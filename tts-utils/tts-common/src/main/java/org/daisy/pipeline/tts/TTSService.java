package org.daisy.pipeline.tts;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import javax.sound.sampled.AudioFormat;

import net.sf.saxon.s9api.XdmNode;

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

	public static class RawAudioBuffer {
		public RawAudioBuffer() {

		}

		public RawAudioBuffer(int size) {
			output = new byte[size];
		}

		public byte[] output;
		public int offsetInOutput;
	};

	/**
	 * This method is called by TTSRegistry.openSynthesizingContext() from the
	 * pipeline's step thread. It should test the TTSService, configures it and
	 * allocates temporary resources.
	 */
	void onBeforeOneExecution() throws SynthesisException;

	/**
	 * This method is called by TTSRegistry.closeSynthesisContext() from the
	 * pipeline's step thread after releaseThreadResources(). It releases the
	 * resources allocated by onBeforeOneExecution() if onBeforeOneExecution()
	 * hasn't thrown any exception.
	 */
	public void onAfterOneExecution();

	/**
	 * Allocate new resources (such as TCP connections) unique for each thread.
	 * All the allocation calls are made from the pipeline's step thread before
	 * any sentence is processed.
	 * 
	 * @return the resources. It can be null
	 * @throws SynthesisException
	 */
	public TTSResource allocateThreadResources() throws SynthesisException;

	/**
	 * In regular situations, all the release calls are made in a single thread
	 * after all the sentences have been processed. The method can also be
	 * called when a TTS OSGi component is disable before the end of the
	 * synthesizing process. (e.g. CTRL-C).
	 * 
	 * @param resources is the object returned by allocateThreadResource()
	 */
	void releaseThreadResources(Object resources) throws SynthesisException;

	/**
	 * This function must be thread-safe for there is only one instance of
	 * TTSService for each TTS processors. But @param threadResources is here to
	 * prevent you from locking internal resources.
	 * 
	 * @param ssml is the SSML to synthesize. You may need to convert it to the
	 *            format understandable for the TTS (e.g. SAPI). The SSML code
	 *            must include the <mark> and the <break/> at the end.
	 * @param voice is the voice the synthesizer must use. It is guaranteed to
	 *            be one returned by getAvailableVoices()
	 * @param output is the resulting raw audio data. Ideally the address of the
	 *            buffer is left unchanged, but a new buffer can be allocated
	 *            when the audio data do not fit in the one provided. The new
	 *            buffer must contain the previous data as well.
	 *            SoundUtil.realloc() is designed to help you doing it.
	 * @param threadResources is the object returned by
	 *            allocateThreadResource(). It may contain persistent buffers,
	 *            opened file streams, TCP connections and so on. The boolean
	 *            field 'released' is guaranteed to be false, i.e. the resource
	 *            provided is always valid and will remain so during the call.
	 * @param marks are the returned pairs (markName, offsetInOutput)
	 *            corresponding to the ssml:marks of @param ssml. The order must
	 *            be kept. The provided list is always empty. The offsetInOutput
	 *            are relative to the new data inserted (they start at 0 no
	 *            matter what @param audioBuffer already contains).
	 * @param retry is true when this is not the first time the thread attempts
	 *            to synthesize @param smml. In such cases, you may reinitialize
	 *            the connection to the remote TTS server.
	 */
	void synthesize(XdmNode ssml, Voice voice, RawAudioBuffer audioBuffer,
	        Object threadResources, List<Map.Entry<String, Integer>> marks, boolean retry)
	        throws SynthesisException, InterruptedException;

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
	public Collection<Voice> getAvailableVoices() throws SynthesisException;

	/**
	 * @return the name of the mark that will be added to check whether all the
	 *         SSML have been successfully synthesized. TTS processors that
	 *         cannot handle marks must return null.
	 */
	public String endingMark();

	/**
	 * @return true to recommend the threads to release the resources once there
	 *         is no more text to synthesize. This can speed up the other
	 *         working threads if the resources consume processing units of
	 *         remote servers.
	 */
	public boolean resourcesReleasedASAP();
}
