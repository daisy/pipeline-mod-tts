package org.daisy.pipeline.tts;

import net.sf.saxon.s9api.QName;

public class BasicSSMLAdapter implements SSMLAdapter {

	@Override
	public String getHeader(String voiceName) {
		return "";
	}

	@Override
	public String getFooter() {
		//we assume the adapter is called on a single sentence
		return SSMLUtil.getBreakAfterSentence();
	}

	@Override
	public QName adaptElement(QName element) {
		if ("w".equals(element.getLocalName()) || "token".equals(element.getLocalName()))
			return null; //conversion from SSML 1.1 to SSML 1.0
		return new QName(null, element.getLocalName());
	}

	@Override
	public QName adaptAttributeName(QName element, QName attr, String value) {
		if (element.getLocalName().equals("s"))
			return null;
		return attr;
	}

	@Override
	public String adaptAttributeValue(QName element, QName attr, String value) {
		return value;
	}

	@Override
	public String adaptText(String text) {
		/*
		 * replace non-breaking spaces with regular white spaces and other stuff
		 * (should not be necessary for TTS processors in a proper working
		 * conditions).
		 */
		return text.replace(" ", " ").replace("’", "'").replace("”", "\"");
	}
}
