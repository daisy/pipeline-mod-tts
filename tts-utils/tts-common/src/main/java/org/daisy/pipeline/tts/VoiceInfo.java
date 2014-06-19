package org.daisy.pipeline.tts;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VoiceInfo {
	private static Pattern localePattern = Pattern
	        .compile("(\\p{Alpha}{2})(?:[-_](\\p{Alpha}{2}))?(?:[-_](\\p{Alnum}{1,8}))*");

	public static Locale tagToLocale(String langtag) {
		//TODO: in Java7 we would use:
		//return Locale.forLanguageTag(lang)
		//=> this works with BCP47 tags, and should work with old tags from RFC 3066
		//TODO: use a common function for pipeline-mod-nlp and pipeline-mod-tts
		Locale locale = null;
		if (langtag != null) {
			Matcher m = localePattern.matcher(langtag.toLowerCase());
			if (m.matches()) {
				locale = new Locale(m.group(1), m.group(2) != null ? m.group(2) : "");
			}
		}
		return locale;
	}

	VoiceInfo(String voiceVendor, String voiceName, String language, float priority) {
		this.voice = new Voice(voiceVendor, voiceName);
		this.language = tagToLocale(language);
		this.priority = priority;
	}

	VoiceInfo(Voice v, String language, float priority) {
		this.voice = v;
		this.language = tagToLocale(language);
		this.priority = priority;
	}

	VoiceInfo(Voice v, Locale language) {
		this.voice = v;
		this.language = language;
		this.priority = -1; //not used in the comparison
	}

	@Override
	public int hashCode() {
		return this.voice.hashCode() ^ this.language.hashCode();
	}

	public boolean equals(Object other) {
		VoiceInfo o = (VoiceInfo) other;
		return voice.equals(o.voice) && language.equals(o.language);
	}

	public Voice voice;
	public Locale language;
	float priority;
}
