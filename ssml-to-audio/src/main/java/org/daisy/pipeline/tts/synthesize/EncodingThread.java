package org.daisy.pipeline.tts.synthesize;

import java.util.concurrent.BlockingQueue;

import org.daisy.pipeline.audio.AudioEncoder;
import org.daisy.pipeline.audio.AudioServices;
import org.daisy.pipeline.tts.AudioBufferTracker;

import com.google.common.base.Optional;

/**
 * Consumes a shared queue of PCM packets. PCM packets are then provided to
 * audio encoders. The thread stops when it receives an 'EndOfQueue' marker.
 */
public class EncodingThread {

	private Thread mThread;

	void start(final AudioServices encoderRegistry,
	        final BlockingQueue<ContiguousPCM> inputPCM, final IPipelineLogger logger,
	        final AudioBufferTracker audioBufferTracker) {
		mThread = new Thread() {
			@Override
			public void run() {
				while (!interrupted()) {
					ContiguousPCM job;
					try {
						job = inputPCM.take();
					} catch (InterruptedException e) {
						logger.printInfo(IPipelineLogger.AUDIO_MISSING
						        + ": encoding thread has been interrupted.");
						break;
					}
					if (job.isEndOfQueue()) {
						//nothing to release
						break;
					} else {
						int jobSize = job.sizeInBytes();
						AudioEncoder encoder = encoderRegistry.getEncoder();
						if (encoder == null) {
							job = null;
							audioBufferTracker.releaseEncodersMemory(jobSize);
							logger.printInfo(IPipelineLogger.AUDIO_MISSING
							        + ": No audio encoder found. Encoding thread is stopping...");
							break;
						}
						Optional<String> destURI = encoder.encode(job.getBuffers(), job
						        .getAudioFormat(), job.getDestinationDirectory(), job
						        .getDestinationFilePrefix());
						if (destURI.isPresent()) {
							job.getURIholder().append(destURI.get());
						}
						job = null;
						audioBufferTracker.releaseEncodersMemory(jobSize);
					}
				}
			}
		};
		mThread.start();
	}

	void waitToFinish() {
		try {
			mThread.join();
		} catch (InterruptedException e) {
			//should not happen
		}
	}
}
