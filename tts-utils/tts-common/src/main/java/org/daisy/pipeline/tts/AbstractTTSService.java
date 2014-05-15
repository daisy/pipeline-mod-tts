package org.daisy.pipeline.tts;

import org.daisy.pipeline.tts.TTSRegistry.TTSResource;

public abstract class AbstractTTSService implements TTSService {

	@Override
	public TTSResource allocateThreadResources() throws SynthesisException {
		return null;
	}

	@Override
	public void onBeforeOneExecution() throws SynthesisException {
	}

	@Override
	public void onAfterOneExecution() {
	}

	@Override
	public void releaseThreadResources(Object resources) throws SynthesisException {
	}

	@Override
	public String getVersion() {
		return "";
	}

	@Override
	public int getOverallPriority() {
		return 1;
	}

	@Override
	public String endingMark() {
		return null;
	}

	@Override
	public boolean resourcesReleasedASAP() {
		return false;
	}

	@Override
	public void interruptCurrentWork(TTSResource resource) {
	}

}
