package org.daisy.pipeline.tts;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;

import net.sf.saxon.s9api.XdmNode;

/**
 * This class is intended to handle ssml:marks with TTS processors that are not
 * designed to deal with them (such as eSpeak). To make it work, the class maps
 * each mark to a subset of the input SSML. Then it runs the processors on each
 * parts separately. Of course, this will damage the prosody. The underlying
 * TTSService is supposed to manage SSML.
 */
public abstract class MarkFreeTTSService extends AbstractTTSService {

	public abstract void synthesize(String ssml, Voice voice, Object threadResources,
	        List<RawAudioBuffer> results) throws SynthesisException, InterruptedException;

	public abstract SSMLAdapter getSSMLAdapter();

	@Override
	public void synthesize(XdmNode ssml, Voice voice, RawAudioBuffer audioBuffer,
	        Object threadResources, List<Entry<String, Integer>> marks, boolean retry)
	        throws SynthesisException, InterruptedException {

		List<String> sortedMarkNames = new ArrayList<String>();
		String[] separated = SSMLUtil.toStringNoMarks(ssml, voice.name, getSSMLAdapter(),
		        sortedMarkNames);
		//note: sortedMarkNames[0] = null

		List<RawAudioBuffer> allBuffers = new ArrayList<RawAudioBuffer>();
		int currentSize = 0;
		for (int i = 0; i < separated.length; ++i) {
			int j = allBuffers.size();
			synthesize(separated[i], voice, threadResources, allBuffers);
			for (; j < allBuffers.size(); ++j) {
				currentSize += allBuffers.get(j).offsetInOutput;
			}
			if (i < (separated.length - 1)) {
				marks.add(new AbstractMap.SimpleEntry<String, Integer>(sortedMarkNames
				        .get(i + 1), currentSize));
			}
		}

		if (currentSize > (audioBuffer.output.length - audioBuffer.offsetInOutput)) {
			SoundUtil.realloc(audioBuffer, currentSize);
		}

		for (RawAudioBuffer buffer : allBuffers) {
			System.arraycopy(buffer.output, 0, audioBuffer.output, audioBuffer.offsetInOutput,
			        buffer.offsetInOutput);
			audioBuffer.offsetInOutput += buffer.offsetInOutput;
		}
	}

	@Override
	public String endingMark() {
		return null;
	}
}
