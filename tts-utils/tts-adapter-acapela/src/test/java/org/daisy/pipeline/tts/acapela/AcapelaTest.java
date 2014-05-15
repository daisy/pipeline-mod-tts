package org.daisy.pipeline.tts.acapela;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;

import org.daisy.pipeline.tts.TTSService.RawAudioBuffer;
import org.daisy.pipeline.tts.TTSService.SynthesisException;
import org.daisy.pipeline.tts.Voice;
import org.daisy.pipeline.tts.acapela.AcapelaTTS.ThreadResources;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Before running those tests, you must start an Acapela server and have
 * libscube.so in your library path.
 */
public class AcapelaTest {
	AcapelaTTS tts;

	private String format(String str) {
		return str;
	}

	private String format(String str, String speakerName) {
		return "\\voice{" + speakerName + "}" + str;
	}

	@Before
	public void setUp() throws SynthesisException {
		tts = new AcapelaTTS();
		tts.onBeforeOneExecution();
	}

	@After
	public void shutdown() {
		tts.onAfterOneExecution();
	}

	@Test
	public void simpleConnection() throws SynthesisException {
		Object r = tts.allocateThreadResources();
		tts.releaseThreadResources(r);
	}

	@Test
	public void getVoiceNames() throws SynthesisException {
		Collection<Voice> voices = tts.getAvailableVoices();
		Assert.assertTrue(voices.size() > 0);
	}

	@Test
	public void speakEasy() throws SynthesisException {
		ThreadResources r = (ThreadResources) tts.allocateThreadResources();

		RawAudioBuffer buffer = new RawAudioBuffer(1);
		tts.synthesize(format("this is a test"), buffer, r, null);
		tts.releaseThreadResources(r);

		Assert.assertTrue(buffer.offsetInOutput > 2000);
	}

	@Test
	public void oneBookmark() throws SynthesisException, IOException {
		ThreadResources r = (ThreadResources) tts.allocateThreadResources();
		r.idsToMark = Arrays.asList("bmark1", "bmark2", "bmark3");

		List<Entry<String, Integer>> l = new ArrayList<Entry<String, Integer>>();
		RawAudioBuffer buffer = new RawAudioBuffer(1);

		int bmark = 1;
		String text = "A piece of text long enough.";
		tts.synthesize(format(text + "<mark name=\"" + bmark + "\"/>" + text), buffer, r, l);
		tts.releaseThreadResources(r);

		Assert.assertTrue(buffer.offsetInOutput > 2000);
		Assert.assertTrue(1 == l.size());
		Assert.assertEquals(r.idsToMark.get(bmark), l.get(0).getKey());

		Assert.assertTrue(Math.abs(buffer.offsetInOutput / 2 - l.get(0).getValue()) < 5000); //the mark is around the middle
	}

	@Test
	public void twoBookmarks() throws SynthesisException {
		ThreadResources r = (ThreadResources) tts.allocateThreadResources();
		r.idsToMark = Arrays.asList("bmark1", "bmark2", "bmark3");

		List<Entry<String, Integer>> l = new ArrayList<Entry<String, Integer>>();
		RawAudioBuffer buffer = new RawAudioBuffer(1);
		Integer bmark1 = 1;
		Integer bmark2 = 2;

		tts.synthesize(format("one two three four <mark name=\"" + bmark1
		        + "\"/> five <mark name=\"" + bmark2 + "\"/>"), buffer, r, l);
		tts.releaseThreadResources(r);

		Assert.assertTrue(buffer.offsetInOutput > 200);
		Assert.assertTrue(2 == l.size());
		Assert.assertEquals(r.idsToMark.get(bmark1), l.get(0).getKey());
		Assert.assertEquals(r.idsToMark.get(bmark2), l.get(1).getKey());
		Assert.assertTrue(l.get(1).getValue() - l.get(0).getValue() < l.get(0).getValue());
	}

	private int[] findSize(final String[] sentences, int startShift)
	        throws InterruptedException {

		final int[] foundSize = new int[sentences.length];
		Thread[] threads = new Thread[sentences.length];

		for (int i = 0; i < threads.length; ++i) {
			final int j = i;
			threads[i] = new Thread() {
				public void run() {
					ThreadResources r;
					try {
						r = (ThreadResources) tts.allocateThreadResources();
						RawAudioBuffer buffer = new RawAudioBuffer(1);
						tts.synthesize(format("this is a test"), buffer, r, null);
						foundSize[j] = buffer.offsetInOutput / 4;

						tts.releaseThreadResources(r);
					} catch (SynthesisException e) {
						return;
					}
				}
			};
		}

		for (int i = startShift; i < threads.length; ++i)
			threads[i].start();
		for (int i = 0; i < startShift; ++i)
			threads[i].start();

		for (Thread t : threads)
			t.join();

		return foundSize;
	}

	@Test
	public void multithreadedSpeak() throws InterruptedException {
		final String[] sentences = new String[]{
		        format("short"), format("regular size"), format("a bit longer size"),
		        format("very much longer sentence")
		};

		int[] size1 = findSize(sentences, 0);
		int[] size2 = findSize(sentences, 2);
		for (int i = 0; i < sentences.length; ++i) {
			Assert.assertTrue(0 != size1[i]);
		}

		Assert.assertArrayEquals(size1, size2);
	}

	@Test
	public void multiVoices() throws SynthesisException {
		Collection<Voice> voices = tts.getAvailableVoices();
		Assert.assertTrue(voices.size() > 0);

		Set<Integer> sizes = new HashSet<Integer>();

		ThreadResources r = (ThreadResources) tts.allocateThreadResources();
		for (Voice v : voices) {
			RawAudioBuffer buffer = new RawAudioBuffer(1);
			tts.synthesize(format("this is a test", v.name), buffer, r, null);
			sizes.add(buffer.offsetInOutput / 4);
		}
		tts.releaseThreadResources(r);

		float diversity = Float.valueOf(sizes.size()) / voices.size();
		Assert.assertTrue(diversity >= 0.6);
	}

	@Test
	public void accents() throws SynthesisException, IOException {
		String withAccents = "à Noël, la tèrre est bèlle vûe du cièl";
		String withoutAccents = "a Noel, la terre est belle vue du ciel";

		//When the accents are not properly handled, the processor pronounces the accents separately.
		//As a result, the output audio buffer will be longer than expected. 

		ThreadResources r = (ThreadResources) tts.allocateThreadResources();
		RawAudioBuffer buffer1 = new RawAudioBuffer(1);
		tts.synthesize(format("<s xml:lang=\"fr\">" + withAccents + "</s>"), buffer1, r, null);
		tts.releaseThreadResources(r);

		r = (ThreadResources) tts.allocateThreadResources();
		RawAudioBuffer buffer2 = new RawAudioBuffer(1);
		tts.synthesize(format("<s xml:lang=\"fr\">" + withoutAccents + "</s>"), buffer2, r,
		        null);
		tts.releaseThreadResources(r);

		Assert.assertTrue(buffer1.offsetInOutput > 2000);

		Assert.assertTrue(Math.abs(buffer1.offsetInOutput - buffer2.offsetInOutput) < 2000);
	}

	@Test
	public void utf8chars() throws SynthesisException {
		List<Character> chars = new ArrayList<Character>();
		chars.add("a".charAt(0)); //for the reference test

		//get a list of 'dangerous' characters
		InputStream is = getClass().getResourceAsStream("/decimal_chars.txt");
		BufferedReader br = new BufferedReader(new InputStreamReader(is));
		while (true) {
			try {
				String line = br.readLine();
				if (line == null)
					break;
				int i = Integer.parseInt(line);
				chars.add((char) i);
			} catch (IOException e) {
				e.printStackTrace();
				break;
			}

		}

		//test every character individually
		String begin = "<s>begin ";
		String end = " this is the end of the sentence long enough for tests<mark name=\"0\"></s>";
		Integer refSize = null;

		for (Character c : chars) {
			ThreadResources r = (ThreadResources) tts.allocateThreadResources();
			r.idsToMark = Arrays.asList("end");
			List<Entry<String, Integer>> l = new ArrayList<Entry<String, Integer>>();
			RawAudioBuffer buffer = new RawAudioBuffer(1);

			tts.synthesize(format(begin + c + end, "alice"), buffer, r, l);
			tts.releaseThreadResources(r);

			Assert.assertTrue(1 == l.size());

			if (refSize == null) {
				refSize = new Integer(buffer.offsetInOutput);
			}

			Assert.assertTrue(2 * refSize / 3 - buffer.offsetInOutput < 0);
		}

	}
}
