package org.daisy.pipeline.tts;

import java.net.URL;
import java.util.Collection;
import java.util.List;

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

	class Mark {
		public Mark(String name, int offset) {
			this.offsetInAudio = offset;
			this.name = name;
		}

		public int offsetInAudio; //in bytes
		public String name;
	}

	/**
	 * Need not to be thread-safe. This method is called by
	 * TTSRegistry.openSynthesizingContext() from the main thread. It should
	 * test the TTSService, configures it and allocates temporary resources. It
	 * must not catch InterruptuedExceptions.
	 */
	void onBeforeOneExecution() throws SynthesisException, InterruptedException;

	/**
	 * Need not to be thread-safe. This method is called by
	 * TTSRegistry.closeSynthesisContext() from the main thread after all the
	 * text has been synthesized. It releases the resources allocated by
	 * onBeforeOneExecution() if onBeforeOneExecution() hasn't thrown any
	 * exception.
	 */
	public void onAfterOneExecution();

	/**
	 * This method must be thread-safe. It allocates new resources (such as TCP
	 * connections) unique for each thread. Allocations can be made on-the-fly
	 * from different threads. It must not catch InterruptuedExceptions.
	 * 
	 * @return the resources, null if the TTS needs no resource.
	 * @throws SynthesisException
	 */
	public TTSResource allocateThreadResources() throws SynthesisException,
	        InterruptedException;

	/**
	 * This method must be thread-safe. Deallocations may be performed from
	 * different threads but are always performed in the same thread as the one
	 * exploiting @param resources.
	 * 
	 * @param resources is the object returned by allocateThreadResource()
	 */
	void releaseThreadResources(TTSResource resources) throws SynthesisException,
	        InterruptedException;

	/**
	 * This method must be thread-safe. But @param threadResources is here to
	 * prevent the service from locking internal resources.
	 * 
	 * @param sentence is the sentence to synthesize. It is the serialized
	 *            result of the SSML conversion performed by the XSLT whose URL
	 *            is returned by getSSMLxslTransformerURL().
	 * @param xmlSentence is the XML version of @param sentence. It should not
	 *            be used unless one needs information that has been lost during
	 *            the serialization process (e.g. marks or CSS)
	 * @param voice is the voice the synthesizer must use. It is guaranteed to
	 *            be one of those returned by getAvailableVoices()
	 * @param threadResources is the object returned by
	 *            allocateThreadResource(). It may contain small persistent
	 *            buffers, opened file streams, TCP connections and so on. The
	 *            boolean field 'released' is guaranteed to be false, i.e. the
	 *            resource provided is always valid and will remain so during
	 *            the call.
	 * @param marks are the returned pairs (markName, offset-in-output)
	 *            corresponding to the ssml:marks of @param sentence. The order
	 *            must be kept. The provided list is always empty. The
	 *            offset-in-outputs are relative to the new buffers returned by
	 *            synthesize(). That is, they start at 0. If the service doesn't
	 *            handle SSML marks (i.e. endingmark() returns null), this
	 *            parameter may be set to null.
	 * @param bufferAllocator is the object that the TTS Service must use to
	 *            allocate new audio buffers.
	 * @param retry is true when this is not the first time the thread attempts
	 *            to synthesize @param sentence. In such cases, you may
	 *            reinitialize the connection to remote TTS servers if there are
	 *            any.
	 * 
	 * 
	 * @return a list of adjacent PCM chunks produced by the TTS processor.
	 */
	public Collection<AudioBuffer> synthesize(String sentence, XdmNode xmlSentence,
	        Voice voice, TTSResource threadResources, List<Mark> marks,
	        AudioBufferAllocator bufferAllocator, boolean retry) throws SynthesisException,
	        InterruptedException, MemoryException;

	/**
	 * Force interruption of the execution of synthesize() when the thread-level
	 * interruption is not enough to make synthesize() finish. Must be
	 * thread-safe, although the method must not wait for locks.
	 * 
	 * @param resource is the same as the one provided to synthesize()
	 */
	public void interruptCurrentWork(TTSResource resource);

	/**
	 * @return the audio format (sample rate etc.) of the data produced by
	 *         synthesize(). The synthesizer is assumed to use the same audio
	 *         format every time. Must be thread-safe.
	 */
	public AudioFormat getAudioOutputFormat();

	/**
	 * @return the same name as in the CSS voice-family property. If several TTS
	 *         services share the same name, then the one with the highest
	 *         priority will be chosen. Must be thread-safe.
	 */
	public String getName();

	/**
	 * @return the version or type (binary, in-memory) of the TTS service. Used
	 *         only for printing information. Must be thread-safe.
	 */
	public String getVersion();

	/**
	 * Need not to be thread-safe. This method is called from the main thread.
	 */
	public int getOverallPriority();

	/**
	 * Need not to be thread-safe. This method is called from the main thread.
	 */
	public Collection<Voice> getAvailableVoices() throws SynthesisException,
	        InterruptedException;

	/**
	 * @return the name of the mark that will be added to check whether all the
	 *         SSML have been successfully synthesized. TTS processors that
	 *         cannot handle marks must return null. Must be thread-safe.
	 */
	public String endingMark();

	/**
	 * @return the number of text-to-pcm threads reserved for this TTS
	 *         processor. Different values than zero make only sense if the TTS
	 *         speed is limited by the number of opened resources rather than by
	 *         the number of cores. In such cases, we want to maximize the time
	 *         spent on using the TTS resources by avoiding using threads for
	 *         something else (e.g. audio encoding or calling other TTS
	 *         processors).
	 */
	public int reservedThreadNum();

	/**
	 * @return the number of expected milliseconds the TTS will take to process
	 *         a word. It is used for computing dynamic timeout durations. Must
	 *         be thread-safe.
	 */
	public int expectedMillisecPerWord();

	/**
	 * Must be thread safe.
	 * 
	 * @return the URL of the XSLT stylesheet used for transforming SSML into
	 *         whatever language the TTS processor takes as input. If the TTS
	 *         processor does not add any extra ending pauses, the XSLT might
	 *         produce a silent break at the end (around 250ms). It should also
	 *         add an ending SSML mark if it can handle it.
	 */
	public URL getSSMLxslTransformerURL();

}
