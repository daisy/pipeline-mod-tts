package org.daisy.pipeline.tts;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VoiceInfo {
	private static Pattern localePattern = Pattern
	        .compile("(\\p{Alpha}{2})(?:[-_](\\p{Alpha}{2}))?(?:[-_](\\p{Alnum}{1,8}))*");

	enum Gender {
		MALE_ADULT,
		MALE_CHILD,
		MALE_ELDERY,
		FEMALE_CHILD,
		FEMALE_ADULT,
		FEMALE_ELDERY
	}

	private static Map<String, Gender> strToGender;
	static {
		strToGender = new HashMap<String, Gender>();
		strToGender.put("male", Gender.MALE_ADULT);
		strToGender.put("female", Gender.FEMALE_ADULT);
		strToGender.put("man", Gender.MALE_ADULT);
		strToGender.put("woman", Gender.FEMALE_ADULT);
		strToGender.put("girl", Gender.FEMALE_CHILD);
		strToGender.put("boy", Gender.MALE_CHILD);
		addVariants("male", "child", Gender.MALE_CHILD);
		addVariants("male", "young", Gender.MALE_CHILD);
		addVariants("male", "adult", Gender.MALE_ADULT);
		addVariants("male", "old", Gender.MALE_ELDERY);
		addVariants("man", "old", Gender.MALE_ELDERY);
		addVariants("male", "eldery", Gender.MALE_ELDERY);
		addVariants("man", "eldery", Gender.MALE_ELDERY);
		addVariants("female", "child", Gender.FEMALE_CHILD);
		addVariants("female", "young", Gender.FEMALE_CHILD);
		addVariants("female", "adult", Gender.FEMALE_ADULT);
		addVariants("female", "old", Gender.FEMALE_ELDERY);
		addVariants("woman", "old", Gender.FEMALE_ELDERY);
		addVariants("female", "eldery", Gender.FEMALE_ELDERY);
		addVariants("woman", "eldery", Gender.FEMALE_ELDERY);
	}

	private static void addVariants(String gender, String age, Gender g) {
		strToGender.put(gender + age, g);
		strToGender.put(age + gender, g);
		strToGender.put(age + "-" + gender, g);
		strToGender.put(gender + "-" + age, g);
		strToGender.put(age + "_" + gender, g);
		strToGender.put(gender + "_" + age, g);
	}

	public static Gender gender(String str) {
		if (str == null)
			return null; //unknown gender
		return strToGender.get(str);
	}

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

	VoiceInfo(String voiceVendor, String voiceName, String language, Gender gender,
	        float priority) {
		this.voice = new Voice(voiceVendor, voiceName);
		this.language = tagToLocale(language);
		this.priority = priority;
		this.gender = gender;
	}

	VoiceInfo(Voice v, String language, Gender gender, float priority) {
		this.voice = v;
		this.language = tagToLocale(language);
		this.priority = priority;
		this.gender = gender;
	}

	VoiceInfo(Voice v, Locale language, Gender gender) {
		this.voice = v;
		this.language = language;
		this.gender = gender;
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

	public Gender gender;
	public Voice voice;
	public Locale language;
	float priority;
}
