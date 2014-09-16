package org.daisy.pipeline.tts;

import java.net.URL;

import org.daisy.pipeline.tts.TTSRegistry.TTSResource;
import org.osgi.framework.BundleContext;

public abstract class AbstractTTSService implements TTSService {

	@Override
	public int expectedMillisecPerWord() {
		return 100;
	}

	@Override
	public int reservedThreadNum() {
		return 0;
	}

	@Override
	public TTSResource allocateThreadResources() throws SynthesisException,
	        InterruptedException {
		return null;
	}

	@Override
	public void onBeforeOneExecution() throws SynthesisException, InterruptedException {
	}

	@Override
	public void onAfterOneExecution() {
	}

	@Override
	public void releaseThreadResources(TTSResource resources) throws SynthesisException,
	        InterruptedException {
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
		return null; //marks not handled
	}

	@Override
	public void interruptCurrentWork(TTSResource resource) {
	}

	public void init(BundleContext context) {
		mXSLTresource = context.getBundle().getEntry("/transform-ssml.xsl");
	}

	@Override
	public URL getSSMLxslTransformerURL() {
		return mXSLTresource;
	}

	protected URL mXSLTresource;
}
