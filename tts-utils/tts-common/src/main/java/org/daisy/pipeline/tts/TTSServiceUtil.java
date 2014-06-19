package org.daisy.pipeline.tts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;

import org.daisy.pipeline.audio.AudioBuffer;
import org.daisy.pipeline.tts.TTSRegistry.TTSResource;
import org.daisy.pipeline.tts.TTSService.SynthesisException;

public class TTSServiceUtil {
	public static String displayName(TTSService service) {
		return service.getName() + "-" + service.getVersion();
	}

	public static Throwable testTTS(TTSService tts, String testStr)
	        throws InterruptedException {
		return testTTS(tts, null, testStr, null, null);
	}

	public static Throwable testTTS(TTSService tts, String testStr, String ssmlMark)
	        throws InterruptedException {
		return testTTS(tts, null, testStr, ssmlMark, null);
	}

	public static Throwable testTTS(TTSService tts, Voice v, String testStr)
	        throws InterruptedException {
		return testTTS(tts, v, testStr, null, null);
	}

	public static Throwable testTTS(TTSService tts, String testStr, TTSResource resource)
	        throws InterruptedException {
		return testTTS(tts, null, testStr, null, resource);
	}

	public static Throwable testTTS(TTSService tts, String testStr, String ssmlMark,
	        TTSResource resource) throws InterruptedException {
		return testTTS(tts, null, testStr, ssmlMark, resource);
	}

	public static Throwable testTTS(TTSService tts, Voice v, String testStr,
	        TTSResource resource) throws InterruptedException {
		return testTTS(tts, v, testStr, null, resource);
	}

	/**
	 * @return an error if anything went wrong
	 */
	public static Throwable testTTS(TTSService tts, Voice v, String testStr, String ssmlMark,
	        TTSResource resources) throws InterruptedException {

		//can throw invalid cast exception
		TestableTTSService strTTS = (TestableTTSService) tts;

		if (tts.endingMark() != null) {
			if (ssmlMark == null)
				ssmlMark = "<mark name=\"" + tts.endingMark() + "\"/>";
			testStr += ssmlMark;
		}
		Collection<AudioBuffer> audioBuffers = null;
		List<Entry<String, Integer>> marks = new ArrayList<Entry<String, Integer>>();
		try {
			if (resources == null)
				resources = tts.allocateThreadResources();
			audioBuffers = strTTS.testSpeak(testStr, v, resources, marks);
		} catch (InterruptedException e) {
			throw e;
		} catch (Throwable t) {
			return t;
		} finally {
			if (resources != null)
				try {
					tts.releaseThreadResources(resources);
				} catch (SynthesisException e) {
					return e;
				}
		}

		//check that the output buffer is big enough
		int size = 0;
		for (AudioBuffer buff : audioBuffers)
			size += buff.size;
		if (size < 2500) {
			return new TTSService.SynthesisException("not enough output audio for string \""
			        + testStr + "\"");
		}

		//check the ending mark
		if (tts.endingMark() != null) {
			if (marks.size() != 1) {
				return new TTSService.SynthesisException(
				        "one bookmark events expected. Received " + marks.size() + " instead.");
			}
			String markName = marks.get(0).getKey();
			int markOffset = marks.get(0).getValue();
			if (!tts.endingMark().equals(markName)) {
				return new TTSService.SynthesisException("expecting ending mark "
				        + tts.endingMark() + ". Got " + markName + " instead ");
			}
			if (markOffset < 2500) {
				return new TTSService.SynthesisException(
				        "expecting ending mark offset to be bigger. Got " + markOffset
				                + " as offset");
			}
		}

		return null;
	}
}
