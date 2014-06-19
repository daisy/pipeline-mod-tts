package org.daisy.pipeline.tts;

import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;

import org.daisy.pipeline.audio.AudioBuffer;
import org.daisy.pipeline.tts.AudioBufferAllocator.MemoryException;
import org.daisy.pipeline.tts.TTSRegistry.TTSResource;
import org.daisy.pipeline.tts.TTSService.SynthesisException;

/**
 * TTS processors that implement this interface will be easily testable before
 * being used for multiple sentences. The major difference with the regular
 * interface is that speak() takes as input a String instead of a XdmNode.
 */
public interface TestableTTSService {
	Collection<AudioBuffer> testSpeak(String ssml, Voice v, TTSResource th,
	        List<Entry<String, Integer>> marks) throws SynthesisException,
	        InterruptedException, MemoryException;

}
