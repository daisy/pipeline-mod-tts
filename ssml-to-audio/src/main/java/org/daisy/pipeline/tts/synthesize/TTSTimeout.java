package org.daisy.pipeline.tts.synthesize;

import org.daisy.pipeline.tts.TTSRegistry.TTSResource;
import org.daisy.pipeline.tts.TTSService;
import org.daisy.pipeline.tts.TTSServiceUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TTSTimeout extends Thread {

	private Logger ServerLogger = LoggerFactory.getLogger(SynthesisWorkerThread.class);
	private Thread mJob;
	private int mSeconds;
	private boolean mOk;
	private TTSService mTTS;
	private TTSResource mResource;

	public TTSTimeout(int seconds) {
		mSeconds = seconds;
		mOk = true;
	}

	public void watch(Thread job, TTSService tts, TTSResource resource) {
		mJob = job;
		mTTS = tts;
		mResource = resource;
		start();
	}

	@Override
	public void run() {
		try {
			sleep(mSeconds * 1000);
		} catch (InterruptedException e) {
			//interrupted by the TTS thread with cancel() because the watched job is finished
			return;
		}
		mOk = false;
		mJob.interrupt();

		//in case the thread interruption was not enough (e.g. socket reading interruption):
		try {
			sleep(1000);
		} catch (InterruptedException e) {
			//interrupted by the TTS thread with cancel() because the watched job is finished
			return;
		}
		if (!interrupted()) {
			//not super-safe because the current work can finish at any time
			ServerLogger.warn("Forcing interruption of the current work of TTS "
			        + TTSServiceUtil.displayName(mTTS) + "...");
			mTTS.interruptCurrentWork(mResource);
		}

	}

	public boolean stillTime() {
		return mOk;
	}

	public void cancel() {
		this.interrupt(); //interrupts the sleeps
		try {
			//wait for interruptCurrentWork to finish
			this.join();
		} catch (InterruptedException e) {
			//interruption of cancel()'s caller: should not happen!
		}
	}
}
