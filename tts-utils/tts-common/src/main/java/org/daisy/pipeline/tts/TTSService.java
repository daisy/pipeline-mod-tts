package org.daisy.pipeline.tts;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.sound.sampled.AudioFormat;

import net.sf.saxon.s9api.XdmNode;

import org.daisy.pipeline.audio.AudioBuffer;
import org.daisy.pipeline.tts.AudioBufferAllocator.MemoryException;
import org.daisy.pipeline.tts.TTSRegistry.TTSResource;

public interface TTSService {

	class SynthesisException extends Exception {
		public SynthesisException(String message, Throwable cause) {
			super(message, cause);
			if (cause != null) {
				setStackTrace(cause.getStackTrace());
			}

		}

		public SynthesisException(Throwable t) {
			super(t);
		}

		public SynthesisException(String message) {
			super(message);
		}
	}

	/**
	 * This method is called by TTSRegistry.openSynthesizingContext() from the
	 * pipeline's step thread. It should test the TTSService, configures it and
	 * allocates temporary resources. It must not catch InterruptuedExceptions.
	 */
	void onBeforeOneExecution() throws SynthesisException, InterruptedException;

	/**
	 * This method is called by TTSRegistry.closeSynthesisContext() from the
	 * pipeline's step thread after all the text has been synthesized. It
	 * releases the resources allocated by onBeforeOneExecution() if
	 * onBeforeOneExecution() hasn't thrown any exception.
	 */
	public void onAfterOneExecution();

	/**
	 * Allocate new resources (such as TCP connections) unique for each thread.
	 * All the allocation calls are made from the pipeline's step thread before
	 * any sentence is processed. It must not catch InterruptuedExceptions.
	 * 
	 * @return the resources, null if the TTS needs no resource.
	 * @throws SynthesisException
	 */
	public TTSResource allocateThreadResources() throws SynthesisException,
	        InterruptedException;

	/**
	 * In regular situations, this method will be called by the TTS threads
	 * after all the sentences have been processed. In some situations, the
	 * method can be called by the TTSRegistry when a TTS OSGi component is
	 * disable before the expected end of the synthesizing process.
	 * 
	 * @param resources is the object returned by allocateThreadResource()
	 */
	void releaseThreadResources(TTSResource resources) throws SynthesisException,
	        InterruptedException;

	/**
	 * This method must be thread-safe for there is only one instance of
	 * TTSService for each TTS processors. But @param threadResources is here to
	 * prevent you from locking internal resources.
	 * 
	 * @param ssml is the SSML to synthesize. You may need to convert it to the
	 *            format understandable for the TTS service (e.g. Apple's ESC).
	 *            The SSML code must include the <mark> and the <break/> at the
	 *            end.
	 * @param voice is the voice the synthesizer must use. It is guaranteed to
	 *            be one of those returned by getAvailableVoices()
	 * @param threadResources is the object returned by
	 *            allocateThreadResource(). It may contain small persistent
	 *            buffers, opened file streams, TCP connections and so on. The
	 *            boolean field 'released' is guaranteed to be false, i.e. the
	 *            resource provided is always valid and will remain so during
	 *            the call.
	 * @param marks are the returned pairs (markName, offset-in-output)
	 *            corresponding to the ssml:marks of @param ssml. The order must
	 *            be kept. The provided list is always empty. The
	 *            offsets-in-output are relative to the new data. That is, they
	 *            start at 0.
	 * @param bufferAllocator is the object that the TTS Service must use to
	 *            allocate new audio buffers.
	 * @param retry is true when this is not the first time the thread attempts
	 *            to synthesize @param smml. In such cases, you may reinitialize
	 *            the connection to remote TTS servers if there are any.
	 * 
	 * 
	 * @return a list of adjacent PCM chunks produced by the TTS processor.
	 */
	public Collection<AudioBuffer> synthesize(XdmNode ssml, Voice voice,
	        TTSResource threadResources, List<Map.Entry<String, Integer>> marks,
	        AudioBufferAllocator bufferAllocator, boolean retry) throws SynthesisException,
	        InterruptedException, MemoryException;

	/**
	 * Force interruption of the execution of synthesize() when the thread-level
	 * interruption is not enough to make synthesize() finish.
	 * 
	 * @param resource is the same as the one provided to synthesize()
	 */
	public void interruptCurrentWork(TTSResource resource);

	/**
	 * @return the audio format (sample rate etc...) of the data produced by
	 *         synthesize(). The synthesizer is assumed to use the same audio
	 *         format every time. Can be called from any thread context.
	 */
	public AudioFormat getAudioOutputFormat();

	/**
	 * @return the same name as in the CSS voice-family property. If several TTS
	 *         services share the same name, then the one with the highest
	 *         priority will be chosen.
	 */
	public String getName();

	/**
	 * @return the version or type (binary, in-memory) of the TTS service. Used
	 *         only for printing information.
	 */
	public String getVersion();

	/**
	 * Called from the pipeline's step thread.
	 */
	public int getOverallPriority();

	/**
	 * Called from the pipeline's step thread.
	 */
	public Collection<Voice> getAvailableVoices() throws SynthesisException,
	        InterruptedException;

	/**
	 * @return the name of the mark that will be added to check whether all the
	 *         SSML have been successfully synthesized. TTS processors that
	 *         cannot handle marks must return null.
	 */
	public String endingMark();

	/**
	 * @return the number of text-to-pcm threads reserved for this TTS
	 *         processor. Different values than zero make only sense if the TTS
	 *         speed is limited by the number of opened resources rather than by
	 *         the number of cores. In such cases, we want to maximize the time
	 *         spent on using the TTS resources by avoiding using threads for
	 *         something else (e.g audio encoding or calling other TTS
	 *         processors).
	 */
	public int reservedThreadNum();

	/**
	 * @return the number of expected milliseconds the TTS will take to process
	 *         a word. It is used for computing dynamic timeout durations.
	 */
	public int expectedMillisecPerWord();
}
