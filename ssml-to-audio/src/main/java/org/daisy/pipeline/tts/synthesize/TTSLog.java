package org.daisy.pipeline.tts.synthesize;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sf.saxon.s9api.XdmNode;

import org.daisy.pipeline.tts.Voice;

/**
 * A class to log structured messages in a big list. TTSLog is meant to be
 * allocated for every new Pipeline job and written to an external file after
 * the TTS work of the job is done.
 */
public interface TTSLog {
	enum ErrorCode {
		UNEXPECTED_VOICE,
		AUDIO_MISSING,
		CRITICAL_ERROR,
		WARNING
	}

	public static class Error {
		public Error(ErrorCode key, String message) {
			this.key = key;
			this.message = message;
		}

		public ErrorCode getErrorCode() {
			return key;
		}

		public String getMessage() {
			return message;
		}

		private ErrorCode key;
		private String message;
	}

	//TODO: make this class an interface or make its content accessible only through TTSLog
	public static class Entry {
		List<Error> errors = new ArrayList<Error>();
		public XdmNode ssml; //SSML before being converted to 'ttsinput'
		public String ttsinput = ""; //the actual string provided as input to the TTS processor
		public Voice selectedVoice; //the voice selected by the top-level VoiceManager
		//the actual voice used by the TTS processor (the same as selectedVoice in the general case, but can be different if something went wrong)
		public Voice actualVoice;
		public String soundfile; //wave, mp3 or ogg file
		public double beginInFile; //in seconds
		public double endInFile; //in seconds
	}

	/**
	 * Supposed to be called within a single-threaded context
	 */
	Entry getOrCreateEntry(String id);

	/**
	 * Can be called within a multi-threaded context once all the calls to
	 * getOrCreateEntry() are done.
	 */
	Entry getWritableEntry(String id);

	Set<Map.Entry<String, Entry>> getEntries();

	void addGeneralError(ErrorCode errcode, String message);

	Collection<Error> readonlyGeneralErrors();
}
