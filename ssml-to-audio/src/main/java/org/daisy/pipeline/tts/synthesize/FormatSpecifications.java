package org.daisy.pipeline.tts.synthesize;

import net.sf.saxon.s9api.QName;

public interface FormatSpecifications {

	static final String AudioNS = "http://www.daisy.org/ns/pipeline/data";
	static final String SSMLNS = "http://www.w3.org/2001/10/synthesis";
	static final String MarkDelimiter = "___";

	public static final QName ClipTag = new QName(AudioNS, "clip");
	public static final QName OutputRootTag = new QName(AudioNS, "audio-clips");

	public static final QName Audio_attr_clipBegin = new QName("", "clipBegin");
	public static final QName Audio_attr_clipEnd = new QName("", "clipEnd");
	public static final QName Audio_attr_src = new QName("", "src");
	public static final QName Audio_attr_id = new QName("", "idref");

	public static final QName SentenceTag = new QName(SSMLNS, "s");
	public static final QName TokenTag = new QName(SSMLNS, "token");
	public static final QName Sentence_attr_id = new QName("", "id");

}
