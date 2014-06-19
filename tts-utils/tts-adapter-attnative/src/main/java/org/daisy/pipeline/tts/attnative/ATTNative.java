package org.daisy.pipeline.tts.attnative;

import java.nio.ByteBuffer;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import javax.sound.sampled.AudioFormat;

import net.sf.saxon.s9api.XdmNode;

import org.daisy.pipeline.audio.AudioBuffer;
import org.daisy.pipeline.tts.AbstractTTSService;
import org.daisy.pipeline.tts.AudioBufferAllocator;
import org.daisy.pipeline.tts.AudioBufferAllocator.MemoryException;
import org.daisy.pipeline.tts.BasicSSMLAdapter;
import org.daisy.pipeline.tts.LoadBalancer.Host;
import org.daisy.pipeline.tts.RoundRobinLoadBalancer;
import org.daisy.pipeline.tts.SSMLAdapter;
import org.daisy.pipeline.tts.SSMLUtil;
import org.daisy.pipeline.tts.StraightBufferAllocator;
import org.daisy.pipeline.tts.TTSRegistry.TTSResource;
import org.daisy.pipeline.tts.TTSServiceUtil;
import org.daisy.pipeline.tts.TestableTTSService;
import org.daisy.pipeline.tts.Voice;

public class ATTNative extends AbstractTTSService implements ATTLibListener,
        TestableTTSService {

	private AudioFormat mAudioFormat;
	private RoundRobinLoadBalancer mLoadBalancer;
	private boolean mFirstInit = true;

	public static SSMLAdapter mSSMLAdapter = new BasicSSMLAdapter() {
		@Override
		public String getFooter() {
			return "</voice>";
		}

		@Override
		public String getHeader(String voiceName) {
			if (voiceName == null || voiceName.isEmpty()) {
				return "<voice>";
			}
			return "<voice name=\"" + voiceName + "\">";
		}
	};

	private static class ThreadResource extends TTSResource {
		long connection;
		List<AudioBuffer> audioBuffers;
		List<Map.Entry<String, Integer>> marks;
		byte[] utf8text;
		int offset;
		int outOfMemBytes;
		AudioBufferAllocator bufferAllocator;
	}

	public void onBeforeOneExecution() throws SynthesisException, InterruptedException {
		if (mFirstInit) {
			System.loadLibrary("att");
			mFirstInit = false;
		}

		mLoadBalancer = new RoundRobinLoadBalancer(System.getProperty("att.servers",
		        "localhost:8888"), this);

		mAudioFormat = new AudioFormat(16000, 16, 1, true, false);
		ATTLib.setListener(this);

		//Test the synthesizer so that the service won't be active if it fails.
		int workingHosts = 0;
		List<Host> nonWorking = new ArrayList<Host>();
		Throwable lastError = null;
		for (Host h : mLoadBalancer.getAllHosts()) {
			TTSResource r = null;
			Throwable t = null;
			try {
				r = allocateThreadResources(h);
			} catch (SynthesisException | InterruptedException e) {
				t = e;
			}
			if (t == null)
				t = TTSServiceUtil.testTTS(this, "hello world", r);
			if (t == null) {
				++workingHosts;
			} else {
				lastError = t;
				nonWorking.add(h);
			}
		}
		if (workingHosts == 0) {
			throw new SynthesisException("None of the AT&T servers is working. Last error: ",
			        lastError);
		}
		mLoadBalancer.discardAll(nonWorking);
	}

	@Override
	public Collection<AudioBuffer> synthesize(XdmNode ssml, Voice voice, TTSResource resource,
	        List<Entry<String, Integer>> marks, AudioBufferAllocator bufferAllocator,
	        boolean retry) throws SynthesisException, InterruptedException, MemoryException {
		if (retry) {
			//If the synthesis has failed once, it's likely because the connection is dead,
			//therefore we open a new connection.
			ThreadResource old = (ThreadResource) resource;
			releaseThreadResources(resource);
			ThreadResource tr = (ThreadResource) allocateThreadResources();
			old.connection = tr.connection;
		}

		String str = SSMLUtil.toString(ssml, voice.name, mSSMLAdapter, endingMark());
		return synthesize(str, resource, marks, bufferAllocator);
	}

	@Override
	public Collection<AudioBuffer> testSpeak(String ssml, Voice v, TTSResource th,
	        List<Entry<String, Integer>> marks) throws SynthesisException,
	        InterruptedException, MemoryException {
		return synthesize(ssml, th, marks, new StraightBufferAllocator());
	}

	private Collection<AudioBuffer> synthesize(String ssml, TTSResource resource,
	        List<Entry<String, Integer>> marks, AudioBufferAllocator bufferAllocator)
	        throws SynthesisException, MemoryException {

		ThreadResource tr = (ThreadResource) resource;
		tr.audioBuffers = new ArrayList<AudioBuffer>();
		tr.marks = marks;
		tr.offset = 0;
		tr.outOfMemBytes = 0;
		tr.bufferAllocator = bufferAllocator;

		UTF8Converter.UTF8Buffer utf8Buffer = UTF8Converter.convertToUTF8(ssml, tr.utf8text);
		tr.utf8text = utf8Buffer.buffer;

		ATTLib.speak(tr, tr.connection, tr.utf8text);

		if (tr.outOfMemBytes > 0) {
			throw new MemoryException(tr.outOfMemBytes);
		}

		return tr.audioBuffers;
	}

	@Override
	public AudioFormat getAudioOutputFormat() {
		return mAudioFormat;
	}

	@Override
	public String getName() {
		return "att";
	}

	@Override
	public void onRecvAudio(Object handler, ByteBuffer audio, int size) {
		ThreadResource tr = (ThreadResource) handler;
		if (tr.outOfMemBytes == 0) {
			try {
				AudioBuffer buffer = tr.bufferAllocator.allocateBuffer(size);
				audio.get(buffer.data, 0, size);
				tr.audioBuffers.add(buffer);
				tr.offset += size;
			} catch (MemoryException e) {
				tr.outOfMemBytes = size;
			}
		}
	}

	@Override
	public void onRecvMark(Object handler, String name) {
		ThreadResource tr = (ThreadResource) handler;
		tr.marks.add(new AbstractMap.SimpleEntry<String, Integer>(name, tr.offset));
	}

	@Override
	public TTSResource allocateThreadResources() throws SynthesisException,
	        InterruptedException {
		return allocateThreadResources(mLoadBalancer.selectHost());
	}

	private ThreadResource allocateThreadResources(Host h) throws SynthesisException,
	        InterruptedException {
		long connection = ATTLib.openConnection(h.address, h.port, (int) mAudioFormat
		        .getSampleRate(), mAudioFormat.getSampleSizeInBits());
		if (connection == 0) {
			throw new SynthesisException("cannot open connections with ATTServer on " + h,
			        null);
		}

		ThreadResource tr = new ThreadResource();
		tr.connection = connection;
		tr.utf8text = new byte[8];
		return tr;
	}

	@Override
	public void releaseThreadResources(TTSResource resource) {
		ThreadResource tr = (ThreadResource) resource;
		ATTLib.closeConnection(tr.connection);
	}

	@Override
	public String getVersion() {
		return "sdk";
	}

	@Override
	public void onAfterOneExecution() {
		mAudioFormat = null;
		mLoadBalancer = null;
	}

	@Override
	public int getOverallPriority() {
		return Integer.valueOf(System.getProperty("att.native.priority", "10"));
	}

	@Override
	public Collection<Voice> getAvailableVoices() throws SynthesisException,
	        InterruptedException {
		ThreadResource tr = (ThreadResource) allocateThreadResources(mLoadBalancer.getMaster());
		String[] voices = ATTLib.getVoiceNames(tr.connection);
		ATTLib.closeConnection(tr.connection);

		Voice[] result = new Voice[voices.length];
		for (int i = 0; i < voices.length; ++i) {
			result[i] = new Voice(getName(), voices[i]);
		}

		return Arrays.asList(result);
	}

	@Override
	public String endingMark() {
		return "ending-mark";
	}
}
