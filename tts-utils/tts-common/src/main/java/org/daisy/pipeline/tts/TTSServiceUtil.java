package org.daisy.pipeline.tts;

public class TTSServiceUtil {
	public static String displayName(TTSService service) {
		return service.getName() + "-" + service.getVersion();
	}
}
