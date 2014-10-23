package org.daisy.pipeline.tts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.daisy.pipeline.audio.AudioBuffer;
import org.daisy.pipeline.tts.TTSRegistry.TTSResource;
import org.daisy.pipeline.tts.TTSService.Mark;
import org.daisy.pipeline.tts.TTSService.SynthesisException;

public class TTSServiceUtil {
	public static String displayName(TTSService service) {
		return service.getName() + "-" + service.getVersion();
	}

	public static Throwable testTTS(TTSEngine tts, String testStr) throws InterruptedException {
		return testTTS(tts, null, testStr, null, null);
	}

	public static Throwable testTTS(TTSEngine tts, String testStr, String ssmlMark)
	        throws InterruptedException {
		return testTTS(tts, null, testStr, ssmlMark, null);
	}

	public static Throwable testTTS(TTSEngine tts, Voice v, String testStr)
	        throws InterruptedException {
		return testTTS(tts, v, testStr, null, null);
	}

	public static Throwable testTTS(TTSEngine tts, String testStr, TTSResource resource)
	        throws InterruptedException {
		return testTTS(tts, null, testStr, null, resource);
	}

	public static Throwable testTTS(TTSEngine tts, String testStr, String ssmlMark,
	        TTSResource resource) throws InterruptedException {
		return testTTS(tts, null, testStr, ssmlMark, resource);
	}

	public static Throwable testTTS(TTSEngine tts, Voice v, String testStr,
	        TTSResource resource) throws InterruptedException {
		return testTTS(tts, v, testStr, null, resource);
	}

	/**
	 * @return an error if anything went wrong
	 */
	public static Throwable testTTS(TTSEngine tts, Voice v, String testStr, String ssmlMark,
	        TTSResource resources) throws InterruptedException {

		if (tts.endingMark() != null) {
			if (ssmlMark == null)
				ssmlMark = "<mark name=\"" + tts.endingMark() + "\"/>";
			testStr += ssmlMark;
		}
		Collection<AudioBuffer> audioBuffers = null;
		List<Mark> marks = new ArrayList<Mark>();
		try {
			if (resources == null)
				resources = tts.allocateThreadResources();
			audioBuffers = tts.synthesize(testStr, null, v, resources, marks,
			        new StraightBufferAllocator(), false);
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
			        + testStr + "\".");
		}

		//check the ending mark
		if (tts.endingMark() != null) {
			if (marks.size() != 1) {
				return new TTSService.SynthesisException(
				        "one bookmark events expected. Received " + marks.size() + " instead.");
			}
			Mark mark = marks.get(0);
			if (!tts.getProvider().equals(mark.name)) {
				return new TTSService.SynthesisException("expecting ending mark "
				        + tts.endingMark() + ". Got " + mark.name + " instead ");
			}
			if (mark.offsetInAudio < 2500) {
				return new TTSService.SynthesisException(
				        "expecting ending mark offset to be bigger. Got " + mark.offsetInAudio
				                + " as offset.");
			}
		}

		return null;
	}
}
