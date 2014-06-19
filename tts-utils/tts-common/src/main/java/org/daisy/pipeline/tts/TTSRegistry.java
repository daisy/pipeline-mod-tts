package org.daisy.pipeline.tts;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.daisy.pipeline.tts.TTSService.SynthesisException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO: mark the voices as Male or Female and use this information when
// choosing a voice
public class TTSRegistry {
	private Logger ServerLogger = LoggerFactory.getLogger(TTSRegistry.class);

	public static class TTSResource {
		public boolean released = false;
	}

	/// ==========================================================================================
	/// The following methods manage resources shared between the service-component callbacks
	/// and the synthesis step.
	/// ==========================================================================================

	//List of active services
	private List<TTSService> mServices = new CopyOnWriteArrayList<TTSService>();

	//List of services used by the current running steps (some of them may not be active anymore)
	private Map<TTSService, List<TTSResource>> mTTSResources = new HashMap<TTSService, List<TTSResource>>();

	private int mContextOpened = 0;

	/**
	 * Service component callback
	 * 
	 * @return
	 */
	public void addTTS(TTSService tts) {
		mServices.add(tts);
	}

	/**
	 * Service component callback
	 */
	public void removeTTS(TTSService tts) {
		List<TTSResource> resources = null;
		synchronized (mTTSResources) {
			resources = mTTSResources.get(tts);
		}
		if (resources != null) {
			ServerLogger.info("Stop bundle of " + TTSServiceUtil.displayName(tts)
			        + " during one TTS step");
			for (TTSResource resource : resources) {
				synchronized (resource) {
					//the other synchronized(resource) surrounds the calls to TTSService.synthesize()
					//and closeSynthesisContext()
					try {
						tts.releaseThreadResources(resource);
					} catch (SynthesisException e) {
						ServerLogger.warn("error while releasing resource of "
						        + TTSServiceUtil.displayName(tts) + ": " + getStack(e));
					} catch (InterruptedException e) {
						ServerLogger.warn("timeout while releasing resource of "
						        + TTSServiceUtil.displayName(tts));
					}
					resource.released = true;
				}
			}
		}

		mServices.remove(tts);
	}

	/**
	 * Initialize the TTS services. Combine voices and TTS-services to build
	 * maps that allow the user to quickly access to the best voices or the best
	 * services for a given vendor/language/gender.
	 */
	public void openSynthesizingContext() {
		synchronized (this) {
			mContextOpened++;
			if (mContextOpened > 1)
				return; //context already opened before
		}

		synchronized (mTTSResources) {
			mTTSResources.clear();
		}
		TTSTimeout timeout = new TTSTimeout();
		for (TTSService tts : mServices) {
			String fullname = TTSServiceUtil.displayName(tts);
			try {
				timeout.enableForCurrentThread(3);
				tts.onBeforeOneExecution();
				synchronized (mTTSResources) {
					mTTSResources.put(tts, new ArrayList<TTSResource>());
				}
				ServerLogger.info(fullname + " successfully initialized");
			} catch (InterruptedException e) {
				ServerLogger.error("timeout while initializing " + fullname);
			} catch (Throwable t) {
				ServerLogger.error(fullname + " could not be initialized");
				ServerLogger.debug(fullname + " init error: " + getStack(t));
			} finally {
				timeout.disable();
			}
		}
		timeout.close();

		ServerLogger.info("number of working TTS services: " + mTTSResources.size() + "/"
		        + mServices.size());

		mContext = new TTSContext();

		//Create a map of the best services for each available voice, given that two different
		//services can serve the same voice. 
		timeout = new TTSTimeout();
		for (TTSService tts : mTTSResources.keySet()) { //no possible concurrent write
			try {
				timeout.enableForCurrentThread(3);
				Collection<Voice> voices = tts.getAvailableVoices();
				if (voices != null)
					for (Voice v : voices) {
						TTSService competitor = mContext.bestServices.get(v);
						if (competitor == null
						        || competitor.getOverallPriority() < tts.getOverallPriority()) {
							mContext.bestServices.put(v, tts);
						}
					}
			} catch (SynthesisException e) {
				ServerLogger.error("error while retrieving the voices of "
				        + TTSServiceUtil.displayName(tts));
				ServerLogger.debug(TTSServiceUtil.displayName(tts)
				        + " getAvailableVoices error: " + getStack(e));
			} catch (InterruptedException e) {
				ServerLogger.error("timeout while retrieving the voices of "
				        + TTSServiceUtil.displayName(tts));
			} finally {
				timeout.disable();
			}
		}
		timeout.close();

		//Create a map of the best voices for each language
		for (VoiceInfo voiceInfo : VoicePriorities) {
			if (!mContext.bestVoices.containsKey(voiceInfo.language)
			        && mContext.bestServices.containsKey(voiceInfo.voice)) {
				mContext.bestVoices.put(voiceInfo.language, voiceInfo.voice);
			}
		}

		//Create a map of the best fallback voices for each language
		for (Map.Entry<Locale, Voice> e : mContext.bestVoices.entrySet()) {
			Locale lang = e.getKey();
			Voice bestVoice = e.getValue();
			VoiceInfo vi = new VoiceInfo(bestVoice, lang);
			Iterator<VoiceInfo> it = VoicePriorities.listIterator(VoicePriorities.indexOf(vi));
			while (it.hasNext()) {
				vi = it.next();
				if (vi.language.equals(lang) && !vi.voice.vendor.equals(bestVoice.vendor)
				        && mContext.bestServices.containsKey(vi.voice)) {
					mContext.secondVoices.put(bestVoice, vi.voice);
					break;
				}
			}
		}

		//TODO: create a map of the best voices for each pair of (gender, language) and another
		//for (vendor, gender, language)

		//Create a map of the best voices when only the vendor's name and languages are provided
		for (VoiceInfo voiceInfo : VoicePriorities) {
			Entry<String, Locale> entry = new AbstractMap.SimpleEntry<String, Locale>(
			        voiceInfo.voice.vendor, voiceInfo.language);
			if (!mContext.mBestVendorVoices.containsKey(entry)
			        && mContext.bestServices.containsKey(voiceInfo.voice)) {
				mContext.mBestVendorVoices.put(entry, voiceInfo.voice);
			}
		}

		//log the available voices
		StringBuilder sb = new StringBuilder("Available voices:");
		for (Entry<Voice, TTSService> e : mContext.bestServices.entrySet()) {
			sb.append("\n* " + e.getKey() + " by " + TTSServiceUtil.displayName(e.getValue()));
		}
		ServerLogger.info(sb.toString());
		sb = new StringBuilder("Fallback voices:");
		for (Entry<Locale, Voice> e : mContext.bestVoices.entrySet()) {
			sb.append("\n* " + e.getKey() + ": " + e.getValue());
		}
		ServerLogger.info(sb.toString());
	}

	public synchronized void closeSynthesizingContext() {
		synchronized (this) {
			mContextOpened--;
			if (mContextOpened > 0)
				return; //context still used by other steps
		}

		for (TTSService tts : mTTSResources.keySet()) { //no possible concurrent write
			tts.onAfterOneExecution();
		}

		synchronized (mTTSResources) {
			mTTSResources.clear();
		}
		mContext = null;
	}

	public TTSResource allocateResourceFor(TTSService tts) throws SynthesisException,
	        InterruptedException {
		List<TTSResource> resources = null;
		synchronized (mTTSResources) {
			resources = mTTSResources.get(tts);
		}

		if (resources == null)
			return null; //the OSGi component has been stopped

		TTSResource r = tts.allocateThreadResources();
		if (r == null)
			r = new TTSResource();
		resources.add(r);

		return r;
	}

	/// ================================================================================
	/// The following methods are used by the synthesizing step only.
	/// They are called after openSynthesizingContext() and before closeSynthesizingContext().
	/// ================================================================================

	static private class TTSContext {
		private Map<Map.Entry<String, Locale>, Voice> mBestVendorVoices = new HashMap<Map.Entry<String, Locale>, Voice>();
		private Map<Voice, TTSService> bestServices = new HashMap<Voice, TTSService>();
		private Map<Locale, Voice> bestVoices = new HashMap<Locale, Voice>();
		private Map<Voice, Voice> secondVoices = new HashMap<Voice, Voice>();
	};

	private TTSContext mContext = null;
	private static List<VoiceInfo> VoicePriorities;

	/**
	 * @return the list of all the voices that can be automatically chosen by
	 *         the pipeline. It can help TTS adapters to build their list of
	 *         available voices.
	 */
	public static Collection<VoiceInfo> getAllPossibleVoices() {
		return VoicePriorities;
	}

	/**
	 * @return null if no voice is available for the given parameters.
	 */
	public Voice findAvailableVoice(String voiceVendor, String voiceName, String lang) {
		if (voiceVendor != null && !voiceVendor.isEmpty() && voiceName != null
		        && !voiceName.isEmpty()) {
			Voice preferredVoice = new Voice(voiceVendor, voiceName);
			if (mContext.bestServices.containsKey(preferredVoice)) {
				return preferredVoice;
			}
		}

		Locale loc = VoiceInfo.tagToLocale(lang);
		if (loc == null)
			return null;

		Locale shortLoc = new Locale(loc.getLanguage());
		Voice result;

		if (voiceVendor != null && !voiceVendor.isEmpty()) {
			Entry<String, Locale> entry = new AbstractMap.SimpleEntry<String, Locale>(
			        voiceVendor, loc);
			result = mContext.mBestVendorVoices.get(entry);
			if (result != null)
				return result;
			//try with a more generic voice
			if (!loc.equals(shortLoc)) {
				entry.setValue(shortLoc);
				result = mContext.mBestVendorVoices.get(entry);
				if (result != null)
					return result;
			}
		}

		result = mContext.bestVoices.get(loc);
		if (result != null)
			return result;
		return mContext.bestVoices.get(shortLoc);
	}

	public Voice findSecondaryVoice(Voice v) {
		return mContext.secondVoices.get(v);
	}

	/**
	 * @param voice is an available voice.
	 * @return the best TTS Service for @param voice. It can return an OSGi
	 *         which is no longer enable.
	 */
	public TTSService getTTS(Voice voice) {
		return mContext.bestServices.get(voice);
	}

	private static String getStack(Throwable t) {
		StringWriter writer = new StringWriter();
		PrintWriter printWriter = new PrintWriter(writer);
		t.printStackTrace(printWriter);
		printWriter.flush();
		return writer.toString();
	}

	static {
		final float priorityVariantPenalty = 0.1f;
		final String eSpeak = "espeak";
		final String att = "att";
		final String microsoft = "Microsoft";
		final String acapela = "acapela";
		final String osx = "osx-speech";
		final int eSpeakPriority = 1;
		final int ATT16Priority = 10;
		final int ATT8Priority = 7;
		final int AcapelaPriority = 12;
		final int OSXSpeechPriority = 5;

		//TODO: move this array to an external XML file (ideally a file editable by users).

		VoiceInfo[] info = {
		        //eSpeak:
		        new VoiceInfo(eSpeak, "french", "fr", eSpeakPriority),
		        new VoiceInfo(eSpeak, "danish", "da", eSpeakPriority),
		        new VoiceInfo(eSpeak, "german", "de", eSpeakPriority),
		        new VoiceInfo(eSpeak, "greek", "el", eSpeakPriority),
		        new VoiceInfo(eSpeak, "hindi", "hi", eSpeakPriority),
		        new VoiceInfo(eSpeak, "hungarian", "hu", eSpeakPriority),
		        new VoiceInfo(eSpeak, "finnish", "fi", eSpeakPriority),
		        new VoiceInfo(eSpeak, "italian", "it", eSpeakPriority),
		        new VoiceInfo(eSpeak, "latin", "la", eSpeakPriority),
		        new VoiceInfo(eSpeak, "norwegian", "no", eSpeakPriority),
		        new VoiceInfo(eSpeak, "polish", "pl", eSpeakPriority),
		        new VoiceInfo(eSpeak, "portugal", "pt", eSpeakPriority),
		        new VoiceInfo(eSpeak, "russian_test", "ru", eSpeakPriority),
		        new VoiceInfo(eSpeak, "swedish", "sv", eSpeakPriority),
		        new VoiceInfo(eSpeak, "mandarin", "zh", eSpeakPriority),
		        new VoiceInfo(eSpeak, "turkish", "tr", eSpeakPriority),
		        new VoiceInfo(eSpeak, "afrikaans", "af", eSpeakPriority),
		        new VoiceInfo(eSpeak, "spanish", "es", eSpeakPriority),
		        new VoiceInfo(eSpeak, "spanish-latin-american", "es-la", eSpeakPriority),
		        new VoiceInfo(eSpeak, "english-us", "en", eSpeakPriority),
		        new VoiceInfo(eSpeak, "english-us", "en-us", eSpeakPriority),
		        new VoiceInfo(eSpeak, "english", "en-uk", eSpeakPriority),
		        //AT&T:
		        new VoiceInfo(att, "alain16", "fr", ATT16Priority),
		        new VoiceInfo(att, "alain8", "fr", ATT8Priority),
		        new VoiceInfo(att, "juliette16", "fr", ATT16Priority),
		        new VoiceInfo(att, "juliette8", "fr", ATT8Priority),
		        new VoiceInfo(att, "arnaud16", "fr-ca", ATT16Priority),
		        new VoiceInfo(att, "arnaud8", "fr-ca", ATT8Priority),
		        new VoiceInfo(att, "klara16", "de", ATT16Priority),
		        new VoiceInfo(att, "klara8", "de", ATT8Priority),
		        new VoiceInfo(att, "reiner16", "de", ATT16Priority),
		        new VoiceInfo(att, "reiner8", "de", ATT8Priority),
		        new VoiceInfo(att, "anjali16", "en-uk", ATT16Priority),
		        new VoiceInfo(att, "anjali8", "en-uk", ATT8Priority),
		        new VoiceInfo(att, "audrey16", "en-uk", ATT16Priority),
		        new VoiceInfo(att, "audrey8", "en-uk", ATT8Priority),
		        new VoiceInfo(att, "charles16", "en-uk", ATT16Priority),
		        new VoiceInfo(att, "charles8", "en-uk", ATT8Priority),
		        new VoiceInfo(att, "mike16", "en-us", ATT16Priority),
		        new VoiceInfo(att, "mike8", "en-us", ATT8Priority),
		        new VoiceInfo(att, "claire16", "en-us", ATT16Priority),
		        new VoiceInfo(att, "claire8", "en-us", ATT8Priority),
		        new VoiceInfo(att, "crystal16", "en-us", ATT16Priority),
		        new VoiceInfo(att, "crystal8", "en-us", ATT8Priority),
		        new VoiceInfo(att, "julia16", "en-us", ATT16Priority),
		        new VoiceInfo(att, "julia8", "en-us", ATT8Priority),
		        new VoiceInfo(att, "lauren16", "en-us", ATT16Priority),
		        new VoiceInfo(att, "lauren8", "en-us", ATT8Priority),
		        new VoiceInfo(att, "mel16", "en-us", ATT16Priority),
		        new VoiceInfo(att, "mel8", "en-us", ATT8Priority),
		        new VoiceInfo(att, "ray16", "en-us", ATT16Priority),
		        new VoiceInfo(att, "ray8", "en-us", ATT8Priority),
		        new VoiceInfo(att, "rich16", "en-us", ATT16Priority),
		        new VoiceInfo(att, "rich8", "en-us", ATT8Priority),
		        new VoiceInfo(att, "alberto16", "es-us", ATT16Priority),
		        new VoiceInfo(att, "alberto8", "es-us", ATT8Priority),
		        new VoiceInfo(att, "rosa16", "es-us", ATT16Priority),
		        new VoiceInfo(att, "rosa8", "es-us", ATT8Priority),
		        new VoiceInfo(att, "francesca16", "it", ATT16Priority),
		        new VoiceInfo(att, "francesca8", "it", ATT8Priority),
		        new VoiceInfo(att, "giovanni16", "it", ATT16Priority),
		        new VoiceInfo(att, "giovanni8", "it", ATT8Priority),
		        new VoiceInfo(att, "marina16", "pt-br", ATT16Priority),
		        new VoiceInfo(att, "marina8", "pt-br", ATT8Priority),
		        new VoiceInfo(att, "marina16", "pt-br", ATT16Priority),
		        new VoiceInfo(att, "marina8", "pt-br", ATT8Priority),
		        new VoiceInfo(att, "tiago16", "pt-br", ATT16Priority),
		        new VoiceInfo(att, "tiago8", "pt-br", ATT8Priority),
		        //Microsoft:
		        new VoiceInfo(microsoft, "Microsoft Anna", "en-us", 4),
		        new VoiceInfo(microsoft, "Microsoft David", "en-us", 6),
		        new VoiceInfo(microsoft, "Microsoft Zira", "en-us", 6),
		        new VoiceInfo(microsoft, "Microsoft Hazel", "en-uk", 6),
		        new VoiceInfo(microsoft, "Microsoft Mike", "en-us", 2),
		        new VoiceInfo(microsoft, "Microsoft Lili", "zh", 3),
		        new VoiceInfo(microsoft, "Microsoft Simplified Chinese", "zh", 2),
		        //Acapela (those are the speaker's names, not the voice names):
		        new VoiceInfo(acapela, "heather", "en-us", AcapelaPriority),
		        new VoiceInfo(acapela, "will", "en-us", AcapelaPriority),
		        new VoiceInfo(acapela, "tracy", "en-us", AcapelaPriority),
		        new VoiceInfo(acapela, "antoine", "fr", AcapelaPriority),
		        new VoiceInfo(acapela, "claire", "fr", AcapelaPriority),
		        new VoiceInfo(acapela, "alice", "fr", AcapelaPriority),
		        new VoiceInfo(acapela, "bruno", "fr", AcapelaPriority),
		        // OS X
		        new VoiceInfo(osx, "Alex", "en-us", OSXSpeechPriority),
		        new VoiceInfo(osx, "Thomas", "fr-fr", OSXSpeechPriority)

		};
		Set<VoiceInfo> priorities = new HashSet<VoiceInfo>(Arrays.asList(info));

		//Override and add some priorities from the system properties.
		//format: priority.vendor_name.voice_name.language = priorityVal
		Properties props = System.getProperties();
		for (String key : props.stringPropertyNames()) {
			String[] parts = key.split("\\.");
			if (parts.length == 4 && "priority".equalsIgnoreCase(parts[0])) {
				float priority;
				try {
					priority = Float.valueOf(props.getProperty(key));
				} catch (NumberFormatException e) {
					continue; //priority ignored
				}
				//'_' are replaced with ' ', but sometimes '_' really means '_'
				//so we must deal with all the possibilities.
				//Another way would be to move the pair {vendor, name} from the key
				//to the value.
				Locale locale = VoiceInfo.tagToLocale(parts[3]);
				for (String lang : new String[]{
				        parts[3], locale != null ? locale.getLanguage() : parts[3]
				}) {
					if (!lang.equals(parts[3]))
						priority -= priorityVariantPenalty;

					for (String vendor : new String[]{
					        parts[1], parts[1].replace("_", " ")
					})
						for (String name : new String[]{
						        parts[2], parts[2].replace("_", " ")
						}) {
							VoiceInfo vi = new VoiceInfo(vendor, name, lang, priority);
							if (!priorities.add(vi)) {
								priorities.remove(vi);
								priorities.add(vi);
							}
						}
				}

			}
		}

		//copy the priorities with language variants removed
		VoicePriorities = new ArrayList<VoiceInfo>(priorities);
		for (VoiceInfo vinfo : priorities) {
			String shortLang = vinfo.language.getLanguage();
			if (!vinfo.language.equals(new Locale(shortLang))) {
				VoicePriorities.add(new VoiceInfo(vinfo.voice, shortLang, vinfo.priority
				        - priorityVariantPenalty));
			}
		}

		Comparator<VoiceInfo> reverseComp = new Comparator<VoiceInfo>() {
			@Override
			public int compare(VoiceInfo v1, VoiceInfo v2) {
				return Float.valueOf(v2.priority).compareTo(Float.valueOf(v1.priority));
			}
		};

		Collections.sort(VoicePriorities, reverseComp);
	}
}
