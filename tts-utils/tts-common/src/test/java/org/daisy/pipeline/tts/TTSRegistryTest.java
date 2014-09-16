package org.daisy.pipeline.tts;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;

import javax.sound.sampled.AudioFormat;

import junit.framework.Assert;
import net.sf.saxon.Configuration;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.XdmNode;

import org.daisy.pipeline.audio.AudioBuffer;
import org.daisy.pipeline.tts.AudioBufferAllocator.MemoryException;
import org.daisy.pipeline.tts.TTSRegistry.TTSResource;
import org.junit.Before;
import org.junit.Test;

public class TTSRegistryTest {

	private static class SimplifiedProcessor extends AbstractTTSService {

		private Collection<Voice> mVoices;
		private URL mXSLT = null;

		SimplifiedProcessor(String xslt, String... voices) throws MalformedURLException {
			mVoices = new ArrayList<Voice>();
			for (String v : voices) {
				String[] parts = v.split(":");
				mVoices.add(new Voice(parts[0], parts[1]));
			}
			mXSLT = getClass().getResource(xslt);
		}

		@Override
		public Collection<AudioBuffer> synthesize(String sentence, XdmNode xmlSentence,
		        Voice voice, TTSResource threadResources, List<Mark> marks,
		        AudioBufferAllocator bufferAllocator, boolean retry)
		        throws SynthesisException, InterruptedException, MemoryException {
			return null;
		}

		@Override
		public AudioFormat getAudioOutputFormat() {
			return null;
		}

		@Override
		public String getName() {
			return "simplified-processor";
		}

		@Override
		public Collection<Voice> getAvailableVoices() throws SynthesisException,
		        InterruptedException {
			return mVoices;
		}

		@Override
		public URL getSSMLxslTransformerURL() {
			return mXSLT;
		}

		//withPriority ...

	}

	static Configuration Conf = new Processor(false).getUnderlyingConfiguration();

	private static String registerVoice(String vendor, String name, String gender,
	        String lang, int property) {
		System.setProperty("priority." + vendor + "." + name + "." + lang + "." + gender,
		        String.valueOf(property));
		return vendor + ":" + name;
	}

	@Before
	public void eraseProperties() {
		HashSet<Entry<Object, Object>> properties = new HashSet<Entry<Object, Object>>(System
		        .getProperties().entrySet());
		for (Entry<Object, Object> e : properties) {
			String key = e.getKey().toString();
			if (key.startsWith("priority.")) {
				System.clearProperty(key);
			}
		}
	}

	@Test
	public void simpleInit() throws MalformedURLException {
		TTSRegistry registry = new TTSRegistry();
		registry.addTTS(new SimplifiedProcessor("/empty-ssml-adapter.xsl", "acapela:claire"));
		VoiceManager vm = registry.openSynthesizingContext(Conf);

		boolean[] perfectMatch = new boolean[1];
		Voice v = vm.findAvailableVoice("acapela", "claire", null, null, perfectMatch);
		Assert.assertTrue(perfectMatch[0]);
		Assert.assertNotNull(v);
		Assert.assertEquals("acapela", v.vendor);
		Assert.assertEquals("claire", v.name);

		registry.closeSynthesizingContext();
	}

	@Test
	public void buggyStylesheet() throws MalformedURLException {
		TTSRegistry registry = new TTSRegistry();
		registry.addTTS(new SimplifiedProcessor("/buggy-ssml-adapter.xsl", "acapela:claire"));
		registry.addTTS(new SimplifiedProcessor("/empty-ssml-adapter.xsl", "acapela:alice"));
		VoiceManager vm = registry.openSynthesizingContext(Conf);
		boolean[] perfectMatch = new boolean[1];

		Voice v = vm.findAvailableVoice("acapela", "claire", null, null, perfectMatch);
		Assert.assertFalse(perfectMatch[0]);

		v = vm.findAvailableVoice("acapela", "alice", null, null, perfectMatch);
		Assert.assertTrue(perfectMatch[0]);
		Assert.assertNotNull(v);
		Assert.assertEquals("acapela", v.vendor);
		Assert.assertEquals("alice", v.name);

		registry.closeSynthesizingContext();
	}

	@Test
	public void customVoice() throws MalformedURLException {
		String vendor = "vendor";
		String voiceName = "voice1";

		String fullname = registerVoice(vendor, voiceName, "en", "male-adult", 10);

		TTSRegistry registry = new TTSRegistry();
		registry.addTTS(new SimplifiedProcessor("/empty-ssml-adapter.xsl", fullname));
		VoiceManager vm = registry.openSynthesizingContext(Conf);

		boolean[] exactMatch = new boolean[1];
		Voice v = vm.findAvailableVoice(vendor, voiceName, null, null, exactMatch);
		Assert.assertTrue(exactMatch[0]);
		Assert.assertNotNull(v);
		Assert.assertEquals(vendor, v.vendor);
		Assert.assertEquals(voiceName, v.name);

		registry.closeSynthesizingContext();
	}

	@Test
	public void onlyLanguage() throws MalformedURLException {
		String vendor = "vendor";
		String voiceName = "voice1";

		String fullname0 = registerVoice("v2", "wrong-lang1", "fr", "male-adult", 15);
		String fullname1 = registerVoice("v", "low-prio", "en", "male-adult", 5);
		String fullname2 = registerVoice(vendor, voiceName, "en", "male-adult", 10);
		String fullname3 = registerVoice("v2", "wrong-lang2", "fr", "male-adult", 15);

		TTSRegistry registry = new TTSRegistry();
		registry.addTTS(new SimplifiedProcessor("/empty-ssml-adapter.xsl", fullname0,
		        fullname1, fullname2, fullname3));
		VoiceManager vm = registry.openSynthesizingContext(Conf);

		boolean[] exactMatch = new boolean[1];
		Voice v = vm.findAvailableVoice(null, null, "en", null, exactMatch);
		Assert.assertTrue(exactMatch[0]);
		Assert.assertNotNull(v);
		Assert.assertEquals(vendor, v.vendor);
		Assert.assertEquals(voiceName, v.name);

		registry.closeSynthesizingContext();
	}

	@Test
	public void withGenderAndLang() throws MalformedURLException {
		String vendor = "vendor";
		String maleVoice = "male-voice";
		String femaleVoice = "female-voice";
		String fullname1 = registerVoice(vendor, maleVoice, "en", "male-adult", 5);
		String fullname2 = registerVoice(vendor, femaleVoice, "en", "female-adult", 10);
		String fullname3 = registerVoice(vendor, "fr-voice", "fr", "female-adult", 15);
		String fullname4 = registerVoice(vendor, "lowprio1", "en", "female-adult", 5);
		String fullname5 = registerVoice(vendor, "lowprio2", "en", "male-adult", 5);

		TTSRegistry registry = new TTSRegistry();
		registry.addTTS(new SimplifiedProcessor("/empty-ssml-adapter.xsl", fullname1,
		        fullname2, fullname3, fullname4, fullname5));
		VoiceManager vm = registry.openSynthesizingContext(Conf);
		boolean[] exactMatch = new boolean[1];

		Voice v = vm.findAvailableVoice(null, null, "en", "male-adult", exactMatch);
		Assert.assertTrue(exactMatch[0]);
		Assert.assertNotNull(v);
		Assert.assertEquals(maleVoice, v.name);

		v = vm.findAvailableVoice(null, null, "en", "female-adult", exactMatch);
		Assert.assertTrue(exactMatch[0]);
		Assert.assertNotNull(v);
		Assert.assertEquals(femaleVoice, v.name);

		registry.closeSynthesizingContext();
	}

	@Test
	public void withVendorAndLang() throws MalformedURLException {
		String vendor1 = "vendor1";
		String vendor2 = "vendor2";
		String voice1 = "voice1";
		String voice2 = "voice2";
		String fullname1 = registerVoice(vendor1, voice1, "en", "male-adult", 5);
		String fullname2 = registerVoice(vendor2, voice2, "en", "male-adult", 10);
		String fullname3 = registerVoice(vendor1, "voice-fr", "fr", "male-adult", 15);

		TTSRegistry registry = new TTSRegistry();
		registry.addTTS(new SimplifiedProcessor("/empty-ssml-adapter.xsl", fullname1,
		        fullname2, fullname3));
		VoiceManager vm = registry.openSynthesizingContext(Conf);
		boolean[] exactMatch = new boolean[1];

		Voice v = vm.findAvailableVoice(vendor1, null, "en", null, exactMatch);
		Assert.assertTrue(exactMatch[0]);
		Assert.assertNotNull(v);
		Assert.assertEquals(vendor1, v.vendor);
		Assert.assertEquals(voice1, v.name);

		v = vm.findAvailableVoice(vendor2, null, "en", null, exactMatch);
		Assert.assertTrue(exactMatch[0]);
		Assert.assertNotNull(v);
		Assert.assertEquals(vendor2, v.vendor);
		Assert.assertEquals(voice2, v.name);

		registry.closeSynthesizingContext();
	}

	@Test
	public void withVendorAndLangAndGender() throws MalformedURLException {
		String vendor1 = "vendor1";
		String vendor2 = "vendor2";
		String maleVoice = "male-voice";
		String fullname1 = registerVoice(vendor2, maleVoice, "en", "male-adult", 100);
		String fullname2 = registerVoice(vendor1, maleVoice, "en", "male-adult", 10);
		String fullname3 = registerVoice(vendor1, "wrong", "fr", "male-adult", 100);
		String fullname4 = registerVoice(vendor1, "low-prio", "en", "male-adult", 5);

		TTSRegistry registry = new TTSRegistry();
		registry.addTTS(new SimplifiedProcessor("/empty-ssml-adapter.xsl", fullname1,
		        fullname2, fullname3, fullname4));
		VoiceManager vm = registry.openSynthesizingContext(Conf);
		boolean[] exactMatch = new boolean[1];

		Voice v = vm.findAvailableVoice("vendor1", null, "en", "male-adult", exactMatch);
		Assert.assertTrue(exactMatch[0]);
		Assert.assertNotNull(v);
		Assert.assertEquals(vendor1, v.vendor);
		Assert.assertEquals(maleVoice, v.name);

		registry.closeSynthesizingContext();
	}

	@Test
	public void voiceNotFound() throws MalformedURLException {
		String vendor = "vendor";
		String voiceName = "voice1";
		String fullname1 = registerVoice(vendor, voiceName, "en", "male-adult", 10);
		String fullname2 = registerVoice(vendor, "wrongvoice", "fr", "male-adult", 100);

		TTSRegistry registry = new TTSRegistry();
		registry.addTTS(new SimplifiedProcessor("/empty-ssml-adapter.xsl", fullname1,
		        fullname2));
		VoiceManager vm = registry.openSynthesizingContext(Conf);

		boolean[] exactMatch = new boolean[1];
		Voice v = vm.findAvailableVoice("any-vendor", "any-voice", "en", "female-adult",
		        exactMatch);
		Assert.assertFalse(exactMatch[0]);
		Assert.assertNotNull(v);
		Assert.assertEquals(vendor, vendor);
		Assert.assertEquals(voiceName, v.name);

		registry.closeSynthesizingContext();
	}

	@Test
	public void approximateMatch1() throws MalformedURLException {
		String vendor = "vendor";
		String voiceName = "voice1";
		String fullname1 = registerVoice(vendor, voiceName, "en", "male-adult", 10);
		String fullname2 = registerVoice(vendor, "wrongvoice1", "fr", "male-adult", 100);
		String fullname3 = registerVoice("another-vendor", "wrongvoice2", "en", "male-adult",
		        200);

		TTSRegistry registry = new TTSRegistry();
		registry.addTTS(new SimplifiedProcessor("/empty-ssml-adapter.xsl", fullname1,
		        fullname2, fullname3));
		VoiceManager vm = registry.openSynthesizingContext(Conf);

		boolean[] exactMatch = new boolean[1];
		Voice v = vm.findAvailableVoice(vendor, null, "en", "female-adult", exactMatch);
		Assert.assertFalse(exactMatch[0]);
		Assert.assertNotNull(v);
		Assert.assertEquals(vendor, vendor);
		Assert.assertEquals(voiceName, v.name);

		registry.closeSynthesizingContext();
	}

	@Test
	public void approximateMatch2() throws MalformedURLException {
		String vendor = "vendor";
		String voiceName = "voice1";
		String fullname1 = registerVoice(vendor, voiceName, "en", "male-adult", 10);
		String fullname2 = registerVoice(vendor, "wrongvoice", "fr", "male-adult", 100);

		TTSRegistry registry = new TTSRegistry();
		registry.addTTS(new SimplifiedProcessor("/empty-ssml-adapter.xsl", fullname1,
		        fullname2));
		VoiceManager vm = registry.openSynthesizingContext(Conf);

		boolean[] exactMatch = new boolean[1];
		Voice v = vm.findAvailableVoice("wrong-vendor", null, "en", "male-adult", exactMatch);
		Assert.assertFalse(exactMatch[0]);
		Assert.assertNotNull(v);
		Assert.assertEquals(vendor, vendor);
		Assert.assertEquals(voiceName, v.name);

		registry.closeSynthesizingContext();
	}

	@Test
	public void langVariantPriority() throws MalformedURLException {
		String vendor = "vendor";
		String voiceName = "voice1";
		String fullname1 = registerVoice(vendor, "voice-a", "en", "male-adult", 10);
		String fullname2 = registerVoice(vendor, "voice-b", "en", "male-adult", 10);
		String fullname3 = registerVoice(vendor, "voice-c", "en", "male-adult", 10);
		String fullname4 = registerVoice(vendor, voiceName, "en-us", "male-adult", 10);
		String fullname5 = registerVoice(vendor, "voice-d", "en", "male-adult", 10);
		String fullname6 = registerVoice(vendor, "voice-e", "en", "male-adult", 10);

		TTSRegistry registry = new TTSRegistry();
		registry.addTTS(new SimplifiedProcessor("/empty-ssml-adapter.xsl", fullname1,
		        fullname2, fullname3, fullname4, fullname5, fullname6));
		VoiceManager vm = registry.openSynthesizingContext(Conf);

		boolean[] exactMatch = new boolean[1];
		Voice v = vm.findAvailableVoice(null, null, "en-us", "male-adult", exactMatch);
		Assert.assertTrue(exactMatch[0]);
		Assert.assertNotNull(v);
		Assert.assertEquals(voiceName, v.name);

		registry.closeSynthesizingContext();
	}

	@Test
	public void voiceFallback1() throws MalformedURLException {
		String vendor1 = "vendor1";
		String vendor2 = "vendor2";
		String firstChoice = "voice1";
		String secondChoice = "voice2";
		String thirdChoice = "voice3";

		String fullname1 = registerVoice(vendor1, firstChoice, "en", "male-adult", 20);
		String fullname2 = registerVoice(vendor2, "wrong-choice", "en", "male-adult", 5);
		String fullname3 = registerVoice(vendor2, thirdChoice, "en", "female-adult", 19);
		String fullname4 = registerVoice(vendor2, secondChoice, "en", "male-adult", 18);

		TTSRegistry registry = new TTSRegistry();
		registry.addTTS(new SimplifiedProcessor("/empty-ssml-adapter.xsl", fullname1,
		        fullname2, fullname3, fullname4));
		VoiceManager vm = registry.openSynthesizingContext(Conf);

		boolean[] exactMatch = new boolean[1];
		Voice v = vm.findAvailableVoice(null, null, "en-us", "male-adult", exactMatch);
		Assert.assertTrue(exactMatch[0]);
		Assert.assertNotNull(v);
		Assert.assertEquals(firstChoice, v.name);

		v = vm.findSecondaryVoice(v);
		Assert.assertNotNull(v);
		Assert.assertEquals(vendor2, v.vendor);
		Assert.assertTrue(secondChoice.equals(v.name) || thirdChoice.equals(v.name));

		registry.closeSynthesizingContext();
	}

	@Test
	public void voiceFallback2() throws MalformedURLException {
		String vendor = "vendor";
		String wantedVoice = "voice1";
		String availableVoice = "voice2";

		registerVoice(vendor, wantedVoice, "en", "male-adult", 20);
		String fullname1 = registerVoice(vendor, "wrong-voice1", "en", "male-adult", 10);
		String fullname2 = registerVoice(vendor, availableVoice, "en", "male-adult", 20);
		String fullname3 = registerVoice(vendor, "wrong-voice2", "en", "male-adult", 10);
		String fullname4 = registerVoice("another-vendor", "wrong-voice3", "en", "male-adult",
		        50);

		TTSRegistry registry = new TTSRegistry();
		registry.addTTS(new SimplifiedProcessor("/empty-ssml-adapter.xsl", fullname1,
		        fullname2, fullname3, fullname4));
		VoiceManager vm = registry.openSynthesizingContext(Conf);

		boolean[] exactMatch = new boolean[1];
		Voice v = vm.findAvailableVoice(vendor, wantedVoice, null, null, exactMatch);
		Assert.assertFalse(exactMatch[0]);
		Assert.assertNotNull(v);
		Assert.assertEquals(availableVoice, v.name);

		registry.closeSynthesizingContext();
	}
}
