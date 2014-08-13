package org.daisy.pipeline.tts.synthesize;

public interface IPipelineLogger {
	static String AUDIO_MISSING = "AUDIO_MISSING";
	static String UNEXPECTED_VOICE = "UNEXPECTED_VOIDE";

	void printInfo(String message);

	void printDebug(String message);
}
