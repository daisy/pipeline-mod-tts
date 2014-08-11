package org.daisy.pipeline.tts.sapinative;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.sound.sampled.AudioFormat;

import net.sf.saxon.s9api.XdmNode;

import org.daisy.pipeline.audio.AudioBuffer;
import org.daisy.pipeline.tts.AbstractTTSService;
import org.daisy.pipeline.tts.AudioBufferAllocator;
import org.daisy.pipeline.tts.AudioBufferAllocator.MemoryException;
import org.daisy.pipeline.tts.SSMLAdapter;
import org.daisy.pipeline.tts.TTSRegistry.TTSResource;
import org.daisy.pipeline.tts.TTSServiceUtil;
import org.daisy.pipeline.tts.Voice;

public class SAPINative extends AbstractTTSService {

	private AudioFormat mAudioFormat;
	private int mSampleRate;
	private int mBytesPerSample;
	private boolean mFirstInit = true;
	private SSMLAdapter mSSMLAdapter;

	private static class ThreadResource extends TTSResource {
		long connection;
	}

	public void onBeforeOneExecution() throws SynthesisException, InterruptedException {
		if (mFirstInit) {
			System.loadLibrary("sapinative");
			mFirstInit = false;
		}

		mSampleRate = Integer.valueOf(System.getProperty("sapi.samplerate", "22050"));
		mBytesPerSample = Integer.valueOf(System.getProperty("sapi.bytespersample", "2"));
		mAudioFormat = new AudioFormat(mSampleRate, 8 * mBytesPerSample, 1, true, false);
		//This audio format is only a preference. We should get the one returned by SAPI if
		//SAPI cannot handle the one above.
		int res = SAPILib.initialize(mSampleRate, 8 * mBytesPerSample);
		if (res != 0) {
			throw new SynthesisException("SAPI initialization failed with error code '" + res
			        + "'");
		}

		//test whether SAPI can synthesize text with marks
		String[] vendors = SAPILib.getVoiceVendors();
		String[] names = SAPILib.getVoiceNames();
		if (vendors.length == 0 || names.length == 0) {
			throw new SynthesisException("Cannot find any SAPI voice");
		}

		Voice voice = new Voice(vendors[0], names[0]);
		String text = mSSMLAdapter.getHeader(voice.name) + "text" + mSSMLAdapter.getFooter();

		Throwable t = TTSServiceUtil.testTTS(this, voice, text);

		if (t != null) {
			throw new SynthesisException(
			        "SAPI's output is too small to be audio when processing " + text, t);
		}
	}

	@Override
	public Collection<AudioBuffer> synthesize(String ssml, XdmNode xmlSSML, Voice voice,
	        TTSResource resource, List<Mark> marks, AudioBufferAllocator bufferAllocator,
	        boolean retry) throws SynthesisException, InterruptedException, MemoryException {

		return speak(ssml, voice, resource, marks, bufferAllocator);
	}

	public Collection<AudioBuffer> speak(String ssml, Voice voice, TTSResource resource,
	        List<Mark> marks, AudioBufferAllocator bufferAllocator) throws SynthesisException,
	        MemoryException {

		ThreadResource tr = (ThreadResource) resource;
		int res = SAPILib.speak(tr.connection, voice.vendor, voice.name, ssml);
		if (res != 0) {
			throw new SynthesisException("SAPI speak error number " + res + " with voice "
			        + voice);
		}

		int size = SAPILib.getStreamSize(tr.connection);

		AudioBuffer result = bufferAllocator.allocateBuffer(size);
		SAPILib.readStream(tr.connection, result.data, result.size);

		String[] names = SAPILib.getBookmarkNames(tr.connection);
		long[] pos = SAPILib.getBookmarkPositions(tr.connection);

		for (int i = 0; i < names.length; ++i) {
			int offset = (int) ((pos[i] * mSampleRate * mBytesPerSample) / 1000);
			marks.add(new Mark(names[i], offset));
		}

		return Arrays.asList(result);
	}

	@Override
	public AudioFormat getAudioOutputFormat() {
		return mAudioFormat;
	}

	@Override
	public String getName() {
		return "sapi";
	}

	@Override
	public TTSResource allocateThreadResources() throws SynthesisException {
		long connection = SAPILib.openConnection();

		if (connection == 0) {
			throw new SynthesisException("cannot initialize voices for SAPI native version");
		}

		ThreadResource tr = new ThreadResource();
		tr.connection = connection;
		return tr;
	}

	@Override
	public void releaseThreadResources(TTSResource resource) {
		ThreadResource tr = (ThreadResource) resource;
		SAPILib.closeConnection(tr.connection);
	}

	@Override
	public String getVersion() {
		return "native";
	}

	@Override
	public void onAfterOneExecution() {
		mAudioFormat = null;
		mSSMLAdapter = null;
	}

	@Override
	public int getOverallPriority() {
		return Integer.valueOf(System.getProperty("sapi.priority", "2"));
	}

	@Override
	public Collection<Voice> getAvailableVoices() throws SynthesisException {
		String[] names = SAPILib.getVoiceNames();
		String[] vendors = SAPILib.getVoiceVendors();
		List<Voice> voices = new ArrayList<Voice>();
		for (int i = 0; i < names.length; ++i) {
			voices.add(new Voice(vendors[i], names[i]));
		}
		return voices;
	}

	public void deactivate() {
		SAPILib.dispose();
	}

	@Override
	public String endingMark() {
		return "ending-mark";
	}
}
