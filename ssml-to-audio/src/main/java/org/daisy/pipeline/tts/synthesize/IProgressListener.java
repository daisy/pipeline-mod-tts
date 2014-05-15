package org.daisy.pipeline.tts.synthesize;

import org.daisy.pipeline.tts.synthesize.SynthesisWorkerPool.UndispatchableSection;

public interface IProgressListener {
	public void notifyFinished(UndispatchableSection section);
}
