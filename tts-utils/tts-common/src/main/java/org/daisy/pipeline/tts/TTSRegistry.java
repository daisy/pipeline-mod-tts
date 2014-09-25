package org.daisy.pipeline.tts;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.xml.transform.URIResolver;

import net.sf.saxon.Configuration;
import net.sf.saxon.s9api.SaxonApiException;

import org.daisy.common.xslt.CompiledStylesheet;
import org.daisy.common.xslt.XslTransformCompiler;
import org.daisy.pipeline.tts.TTSService.SynthesisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TTSRegistry {

	public static class TTSResource {
		public boolean released = false;
	}

	private Logger ServerLogger = LoggerFactory.getLogger(TTSRegistry.class);
	private URIResolver mURIResolver;
	private int mContextOpened = 0;
	private VoiceManager mVoiceManager;
	private List<TTSService> mServices = new CopyOnWriteArrayList<TTSService>(); //List of active services
	//Services and resources used by the current running steps (some of them may not be active anymore):
	private Map<TTSService, List<TTSResource>> mTTSResources = new HashMap<TTSService, List<TTSResource>>();

	public TTSRegistry() {
		VoiceManager.StaticInit();
	}

	/**
	 * Service component callback
	 */
	public void setURIResolver(URIResolver uriResolver) {
		mURIResolver = uriResolver;
	}

	/**
	 * Service component callback
	 */
	public void unsetURIResolver(URIResolver uriResolver) {
		mURIResolver = null;
	}

	/**
	 * Service component callback
	 */
	public void addTTS(TTSService tts) {
		mServices.add(tts);
	}

	/**
	 * Service component callback
	 */
	public void removeTTS(TTSService tts) {
		List<TTSResource> resources = null;
		synchronized (mTTSResources) {
			resources = mTTSResources.get(tts);
		}
		if (resources != null) {
			ServerLogger.info("Stop bundle of " + TTSServiceUtil.displayName(tts)
			        + " during one TTS step");
			for (TTSResource resource : resources) {
				synchronized (resource) {
					//the other synchronized(resource) surrounds the calls to TTSService.synthesize()
					//and closeSynthesisContext()
					try {
						tts.releaseThreadResources(resource);
					} catch (SynthesisException e) {
						ServerLogger.warn("error while releasing resource of "
						        + TTSServiceUtil.displayName(tts) + ": " + getStack(e));
					} catch (InterruptedException e) {
						ServerLogger.warn("timeout while releasing resource of "
						        + TTSServiceUtil.displayName(tts));
					}
					resource.released = true;
				}
			}
		}

		mServices.remove(tts);
	}

	/**
	 * Initialize the TTS services. Combine voices and TTS-services to build
	 * maps that allow the user to quickly access to the best voices or the best
	 * services for a given vendor/language/gender via the VoiceManager.
	 */
	public synchronized VoiceManager openSynthesizingContext(Configuration conf) {
		mContextOpened++;
		if (mContextOpened > 1)
			return mVoiceManager; //context already opened before

		synchronized (mTTSResources) {
			mTTSResources.clear();
		}

		//we check that the SSML adapter do compile but we don't store them because
		//they wouldn't be usable with other XProcruntime anyway
		XslTransformCompiler xslCompiler = new XslTransformCompiler(conf, mURIResolver);
		TTSTimeout timeout = new TTSTimeout();
		for (TTSService tts : mServices) {
			String fullname = TTSServiceUtil.displayName(tts);
			CompiledStylesheet transf = null;
			try {
				transf = xslCompiler.compileStylesheet(tts.getSSMLxslTransformerURL()
				        .openStream());
			} catch (SaxonApiException e) {
				ServerLogger.error(fullname + "'s SSML transformer could not be compiled: "
				        + getStack(e));
			} catch (IOException e1) {
				ServerLogger.error("IO error while loading " + fullname
				        + "'s SSML transformer: " + getStack(e1));
			}

			if (transf != null) {
				try {
					timeout.enableForCurrentThread(3);
					tts.onBeforeOneExecution();
					synchronized (mTTSResources) {
						mTTSResources.put(tts, new ArrayList<TTSResource>());
					}
					ServerLogger.info(fullname + " successfully initialized");
				} catch (InterruptedException e) {
					ServerLogger.error("timeout while initializing " + fullname);
				} catch (Throwable t) {
					ServerLogger.error(fullname + " could not be initialized");
					ServerLogger.debug(fullname + " init error: " + getStack(t));
				} finally {
					timeout.disable();
				}
			}
		}
		timeout.close();

		ServerLogger.info("number of working TTS services: " + mTTSResources.size() + "/"
		        + mServices.size());

		mVoiceManager = new VoiceManager(mTTSResources.keySet());

		return mVoiceManager;
	}

	public synchronized void closeSynthesizingContext() {
		mContextOpened--;
		if (mContextOpened > 0)
			return; //context still used by other steps

		for (TTSService tts : mTTSResources.keySet()) { //no possible concurrent write
			tts.onAfterOneExecution();
		}

		synchronized (mTTSResources) {
			mTTSResources.clear();
		}
		mVoiceManager = null;
	}

	public VoiceManager getCurrentVoiceManager() {
		return mVoiceManager;
	}

	public TTSResource allocateResourceFor(TTSService tts) throws SynthesisException,
	        InterruptedException {
		List<TTSResource> resources = null;
		synchronized (mTTSResources) {
			resources = mTTSResources.get(tts);
		}

		if (resources == null)
			return null; //the OSGi component has been stopped

		TTSResource r = tts.allocateThreadResources();
		if (r == null)
			r = new TTSResource();
		resources.add(r);

		return r;
	}

	private static String getStack(Throwable t) {
		StringWriter writer = new StringWriter();
		PrintWriter printWriter = new PrintWriter(writer);
		t.printStackTrace(printWriter);
		printWriter.flush();
		return writer.toString();
	}

}
