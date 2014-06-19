package org.daisy.pipeline.tts;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;

import net.sf.saxon.s9api.XdmNode;

import org.daisy.pipeline.audio.AudioBuffer;
import org.daisy.pipeline.tts.AudioBufferAllocator.MemoryException;
import org.daisy.pipeline.tts.TTSRegistry.TTSResource;

/**
 * This class is intended to handle ssml:marks with TTS processors that are not
 * designed to deal with them (such as eSpeak). To make it work, the class maps
 * each mark to a subset of the input SSML. Then it runs the processors on each
 * parts separately. Of course, this will damage the prosody. The underlying
 * TTSService is supposed to manage SSML.
 */
public abstract class MarkFreeTTSService extends AbstractTTSService implements
        TestableTTSService {

	public abstract void synthesize(String ssml, Voice voice, TTSResource threadResources,
	        List<AudioBuffer> results, AudioBufferAllocator bufferAllocator)
	        throws SynthesisException, InterruptedException, MemoryException;

	public abstract SSMLAdapter getSSMLAdapter();

	@Override
	public Collection<AudioBuffer> synthesize(XdmNode ssml, Voice voice,
	        TTSResource threadResources, List<Entry<String, Integer>> marks,
	        AudioBufferAllocator bufferAllocator, boolean retry) throws SynthesisException,
	        InterruptedException, MemoryException {

		List<String> sortedMarkNames = new ArrayList<String>();
		String[] separated = SSMLUtil.toStringNoMarks(ssml, voice.name, getSSMLAdapter(),
		        sortedMarkNames);
		//note: sortedMarkNames[0] = null

		List<AudioBuffer> allBuffers = new ArrayList<AudioBuffer>();
		int currentSize = 0;
		for (int i = 0; i < separated.length; ++i) {
			int j = allBuffers.size();
			synthesize(separated[i], voice, threadResources, allBuffers, bufferAllocator);
			for (; j < allBuffers.size(); ++j) {
				currentSize += allBuffers.get(j).size;
			}
			if (i < (separated.length - 1)) {
				marks.add(new AbstractMap.SimpleEntry<String, Integer>(sortedMarkNames
				        .get(i + 1), currentSize));
			}
		}

		return allBuffers;
	}

	@Override
	public String endingMark() {
		return null;
	}

	@Override
	public Collection<AudioBuffer> testSpeak(String ssml, Voice v, TTSResource th,
	        List<Entry<String, Integer>> marks) throws SynthesisException,
	        InterruptedException, MemoryException {
		List<AudioBuffer> output = new ArrayList<AudioBuffer>();
		synthesize(ssml, null, th, output, new StraightBufferAllocator());
		return output;
	}
}
