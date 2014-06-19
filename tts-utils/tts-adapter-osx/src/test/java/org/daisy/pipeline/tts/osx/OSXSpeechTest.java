package org.daisy.pipeline.tts.osx;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.daisy.pipeline.audio.AudioBuffer;
import org.daisy.pipeline.tts.AudioBufferAllocator;
import org.daisy.pipeline.tts.AudioBufferAllocator.MemoryException;
import org.daisy.pipeline.tts.StraightBufferAllocator;
import org.daisy.pipeline.tts.TTSRegistry.TTSResource;
import org.daisy.pipeline.tts.TTSService;
import org.daisy.pipeline.tts.TTSService.SynthesisException;
import org.daisy.pipeline.tts.Voice;
import org.junit.Assert;
import org.junit.Test;

public class OSXSpeechTest {

	private static AudioBufferAllocator BufferAllocator = new StraightBufferAllocator();

	private static int getSize(Collection<AudioBuffer> buffers) {
		int res = 0;
		for (AudioBuffer buf : buffers) {
			res += buf.size;
		}
		return res;
	}

	@Test
	public void getVoiceInfo() throws SynthesisException, InterruptedException {
		TTSService service = new OSXSpeechTTS();
		service.onBeforeOneExecution();
		Collection<Voice> voices = service.getAvailableVoices();
		Assert.assertTrue(voices.size() > 5);
	}

	@Test
	public void speakEasy() throws SynthesisException, InterruptedException, MemoryException {
		OSXSpeechTTS service = new OSXSpeechTTS();
		service.onBeforeOneExecution();

		ArrayList<AudioBuffer> li = new ArrayList<AudioBuffer>();

		TTSResource resource = service.allocateThreadResources();
		service.synthesize("<s>this is a test</s>", null, resource, li, BufferAllocator);
		service.releaseThreadResources(resource);

		Assert.assertTrue(getSize(li) > 2000);
	}

	@Test
	public void speakWithVoices() throws SynthesisException, InterruptedException,
	        MemoryException {
		OSXSpeechTTS service = new OSXSpeechTTS();
		service.onBeforeOneExecution();
		TTSResource resource = service.allocateThreadResources();

		ArrayList<AudioBuffer> li = new ArrayList<AudioBuffer>();

		Set<Integer> sizes = new HashSet<Integer>();
		int totalVoices = 0;
		Iterator<Voice> ite = service.getAvailableVoices().iterator();
		while (ite.hasNext()) {
			Voice v = ite.next();
			li.clear();
			service.synthesize("<s><voice name=\"" + v.name + "\">small test</voice></s>",
			        null, resource, li, BufferAllocator);
			sizes.add(getSize(li) / 4); //div 4 helps being more robust to tiny differences
			totalVoices++;
		}
		service.releaseThreadResources(resource);

		//this number will be very low if the voice names are not properly retrieved
		float diversity = Float.valueOf(sizes.size()) / totalVoices;

		Assert.assertTrue(diversity > 0.4);
	}

	@Test
	public void speakUnicode() throws SynthesisException, InterruptedException,
	        MemoryException {
		OSXSpeechTTS service = new OSXSpeechTTS();
		service.onBeforeOneExecution();

		ArrayList<AudioBuffer> li = new ArrayList<AudioBuffer>();

		TTSResource resource = service.allocateThreadResources();
		service.synthesize("<s>𝄞𝄞𝄞𝄞 水水水水水 𝄞水𝄞水𝄞水𝄞水 test 国Ø家Ť标准 ĜæŘ ß ŒÞ ๕</s>", null,
		        resource, li, BufferAllocator);
		service.releaseThreadResources(resource);

		Assert.assertTrue(getSize(li) > 2000);
	}

	@Test
	public void multiSpeak() throws SynthesisException, InterruptedException {
		final OSXSpeechTTS service = new OSXSpeechTTS();
		service.onBeforeOneExecution();

		final int[] sizes = new int[16];
		Thread[] threads = new Thread[sizes.length];
		for (int i = 0; i < threads.length; ++i) {
			final int j = i;
			threads[i] = new Thread() {
				public void run() {
					ArrayList<AudioBuffer> li = new ArrayList<AudioBuffer>();
					TTSResource resource = null;
					try {
						resource = service.allocateThreadResources();
					} catch (SynthesisException | InterruptedException e) {
						return;
					}

					for (int k = 0; k < 16; ++k) {
						li.clear();
						try {
							service.synthesize("<s>small test</s>", null, resource, li,
							        BufferAllocator);
						} catch (SynthesisException | InterruptedException | MemoryException e) {
							e.printStackTrace();
							break;
						}
						sizes[j] += getSize(li);
					}
					try {
						service.releaseThreadResources(resource);
					} catch (SynthesisException | InterruptedException e) {
					}
				}
			};
		}

		for (Thread th : threads)
			th.start();

		for (Thread th : threads)
			th.join();

		for (int size : sizes) {
			Assert.assertEquals(sizes[0], size);
		}
	}
}
