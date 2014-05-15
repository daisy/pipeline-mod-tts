package org.daisy.pipeline.tts.synthesize;

import org.daisy.common.xproc.calabash.XProcStepProvider;
import org.daisy.pipeline.audio.AudioEncoder;
import org.daisy.pipeline.audio.AudioServices;
import org.daisy.pipeline.tts.TTSRegistry;

import com.xmlcalabash.core.XProcRuntime;
import com.xmlcalabash.core.XProcStep;
import com.xmlcalabash.runtime.XAtomicStep;

public class SynthesizeProvider implements XProcStepProvider {
	private TTSRegistry mRegistry;
	private AudioServices mAudioServices;

	@Override
	public XProcStep newStep(XProcRuntime runtime, XAtomicStep step) {
		boolean error = false;
		AudioEncoder encoder = null;
		if (mRegistry == null) {
			runtime.error(new RuntimeException("Registry of TTS engines is missing."));
			error = true;
		}

		if (mAudioServices == null) {
			runtime.error(new RuntimeException("Registry of audio encoders is missing."));
			error = true;
		} else {
			// TODO: select an AudioService according to the user's preferences
			encoder = mAudioServices.getEncoder();
			if (encoder == null) {
				runtime.error(new RuntimeException("No audio encoder found."));
				error = true;
			}
		}

		if (error)
			return null;

		//warning: a reference is kept on the audio encoder during all the synthesizing process,
		//even if it is unregistered.

		return new SynthesizeStep(runtime, step, mRegistry, encoder);
	}

	protected void setTTSRegistry(TTSRegistry registry) {
		mRegistry = registry;
	}

	protected void unsetTTSRegistry(TTSRegistry registry) {
		mRegistry = null;
	}

	protected void setAudioServices(AudioServices audioServices) {
		mAudioServices = audioServices;
	}

	protected void unsetAudioServices(AudioServices audioServices) {
		mAudioServices = null;
	}
}
