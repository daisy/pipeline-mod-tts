package org.daisy.pipeline.tts.synthesize;

import java.util.Map;
import java.util.concurrent.BlockingQueue;

import org.daisy.pipeline.audio.AudioEncoder;
import org.daisy.pipeline.audio.AudioServices;
import org.daisy.pipeline.tts.AudioBufferTracker;
import org.daisy.pipeline.tts.synthesize.TTSLog.ErrorCode;

import com.google.common.base.Optional;

/**
 * Consumes a shared queue of PCM packets. PCM packets are then provided to
 * audio encoders. The thread stops when it receives an 'EndOfQueue' marker.
 */
public class EncodingThread {

	private Thread mThread;

	void start(final AudioServices encoderRegistry,
	        final BlockingQueue<ContiguousPCM> inputPCM, final IPipelineLogger logger,
	        final AudioBufferTracker audioBufferTracker, Map<String, String> TTSproperties,
	        final TTSLog ttslog) {
		mThread = new Thread() {
			@Override
			public void run() {
				while (!interrupted()) {
					ContiguousPCM job;
					try {
						job = inputPCM.take();
					} catch (InterruptedException e) {
						String msg = "encoding thread has been interrupted";
						logger.printInfo(msg);
						ttslog.addGeneralError(ErrorCode.CRITICAL_ERROR, msg);
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
							String msg = "No audio encoder found. Encoding thread is stopping...";
							logger.printInfo(msg);
							ttslog.addGeneralError(ErrorCode.CRITICAL_ERROR, msg);
							break;
						}
						//TODO: pass the properties to the encoder
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
