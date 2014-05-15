package org.daisy.pipeline.tts;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import net.sf.saxon.s9api.XdmNode;

public abstract class SingleAccessPointTTSService implements TTSService {

	private int nextJobIDstarted;
	private int lastJobIDfinished;
	private byte[] mResult;
	private int mResultSize;
	private List<Entry<String, Integer>> mResultMarks;

	public SingleAccessPointTTSService() {
		nextJobIDstarted = 0;
		lastJobIDfinished = -1;
	}

	@Override
	public void synthesize(XdmNode ssml, Voice voice, RawAudioBuffer audioBuffer,
	        Object threadResources, List<Map.Entry<String, Integer>> marks, boolean retry)
	        throws SynthesisException {

		int myID;
		synchronized (this) {
			myID = nextJobIDstarted++;
			startJob(ssml);
		}

		try {
			synchronized (this) {
				while (lastJobIDfinished != myID) {
					this.wait();
				}
				// here the job is finished
				audioBuffer.offsetInOutput = mResultSize;
				audioBuffer.output = mResult;
				marks.addAll(mResultMarks);
			}
		} catch (InterruptedException e) {
			throw new SynthesisException(e.getMessage(), e.getCause());
		}
	}

	public void endJob(byte[] audioResult, int resultSize, List<Entry<String, Integer>> marks) {
		synchronized (this) {
			mResult = audioResult;
			mResultSize = resultSize;
			mResultMarks = marks;
			++lastJobIDfinished;
			this.notifyAll();
		}
	}

	public abstract void startJob(XdmNode ssml);

}
