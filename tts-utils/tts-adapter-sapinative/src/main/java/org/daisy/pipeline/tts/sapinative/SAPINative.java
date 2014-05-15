package org.daisy.pipeline.tts.sapinative;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;

import javax.sound.sampled.AudioFormat;

import net.sf.saxon.s9api.XdmNode;

import org.daisy.pipeline.tts.AbstractTTSService;
import org.daisy.pipeline.tts.BasicSSMLAdapter;
import org.daisy.pipeline.tts.SSMLAdapter;
import org.daisy.pipeline.tts.SSMLUtil;
import org.daisy.pipeline.tts.SoundUtil;
import org.daisy.pipeline.tts.TTSRegistry.TTSResource;
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

	public void onBeforeOneExecution() throws SynthesisException {
		if (mFirstInit) {
			System.loadLibrary("sapinative");
			mFirstInit = false;
		}

		mSSMLAdapter = new BasicSSMLAdapter() {
			//note: the <s> element is already provided by the XdmNode
			@Override
			public String getHeader(String voiceName) {
				return "<speak version=\"1.0\" xmlns=\"http://www.w3.org/2001/10/synthesis\">";
			}

			@Override
			public String getFooter() {
				return super.getFooter() + "</speak>";
			}
		};

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
		String text = mSSMLAdapter.getHeader(voice.name) + "text <mark name=\"" + endingMark()
		        + "\"/>" + mSSMLAdapter.getFooter();
		RawAudioBuffer testBuffer = new RawAudioBuffer();
		testBuffer.offsetInOutput = 0;
		testBuffer.output = new byte[16];
		ThreadResource r = (ThreadResource) allocateThreadResources();
		List<Entry<String, Integer>> marks = new ArrayList<Entry<String, Integer>>();
		synthesize(text, voice, testBuffer, r, marks);
		releaseThreadResources(r);
		if (testBuffer.offsetInOutput < 500) {
			throw new SynthesisException(
			        "SAPI's output is too small to be audio when processing " + text);
		}
		if (marks.size() == 0 || !endingMark().equals(marks.get(0).getKey())) {
			throw new SynthesisException(
			        "SAPI's did not output the ending SSML mark as expected.");
		}
	}

	@Override
	public void synthesize(XdmNode ssml, Voice voice, RawAudioBuffer audioBuffer,
	        Object resource, List<Entry<String, Integer>> marks, boolean retry)
	        throws SynthesisException {

		ThreadResource tr = (ThreadResource) resource;
		String text = SSMLUtil.toString(ssml, voice.name, mSSMLAdapter, endingMark());
		synthesize(text, voice, audioBuffer, tr, marks);
	}

	private void synthesize(String ssml, Voice voice, RawAudioBuffer audioBuffer,
	        ThreadResource tr, List<Entry<String, Integer>> marks) throws SynthesisException {

		int res = SAPILib.speak(tr.connection, voice.vendor, voice.name, ssml);
		if (res != 0) {
			throw new SynthesisException("SAPI speak error number " + res + " with voice "
			        + voice);
		}

		int size = SAPILib.getStreamSize(tr.connection);
		if (size > (audioBuffer.output.length - audioBuffer.offsetInOutput)) {
			SoundUtil.realloc(audioBuffer, size);
		}

		audioBuffer.offsetInOutput = SAPILib.readStream(tr.connection, audioBuffer.output,
		        audioBuffer.offsetInOutput);

		String[] names = SAPILib.getBookmarkNames(tr.connection);
		long[] pos = SAPILib.getBookmarkPositions(tr.connection);

		for (int i = 0; i < names.length; ++i) {
			int offset = (int) ((pos[i] * mSampleRate * mBytesPerSample) / 1000);
			marks.add(new AbstractMap.SimpleEntry<String, Integer>(names[i], offset));
		}
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
	public void releaseThreadResources(Object resource) {
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
