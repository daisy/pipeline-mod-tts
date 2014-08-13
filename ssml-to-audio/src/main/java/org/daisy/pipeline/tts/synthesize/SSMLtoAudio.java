package org.daisy.pipeline.tts.synthesize;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.PriorityBlockingQueue;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmNode;

import org.daisy.pipeline.audio.AudioServices;
import org.daisy.pipeline.tts.AudioBufferTracker;
import org.daisy.pipeline.tts.DefaultSSMLMarkSplitter;
import org.daisy.pipeline.tts.SSMLMarkSplitter;
import org.daisy.pipeline.tts.TTSRegistry;
import org.daisy.pipeline.tts.TTSService;
import org.daisy.pipeline.tts.TTSService.SynthesisException;
import org.daisy.pipeline.tts.Voice;

import com.google.common.collect.Iterables;

/**
 * SSMLtoAudio splits the input SSML sentences into sections called
 * ContiguousText. Every section contains a list of contiguous sentences that
 * may be eventually stored into different audio files, but it is guaranteed
 * that such audio files won't contain sentences of other sections in such a
 * manner that the list of audio files will mirror the document order. Every
 * sentence has the same single sample rate to prevent us from resampling the
 * audio data before sending them to encoders.
 * 
 * Once all the sentences have been assigned to TTS voices, they are sorted by
 * size and stored in a shared queue of ContiguousText. SSMLtoAudio creates
 * threads to consume this queue.
 * 
 * The TextToPcmThreads send PCM data to EncodingThreads via a queue of
 * ContiguousPCM. These PCM packets are then processed from the longest (with
 * respect to the number of samples) to the shortest in order to make it likely
 * that the threads will finish at the same time. When all the TextToPcmThreads
 * are joined, the pipeline pushes an EndOfQueue marker to every EncodingThreads
 * to notify that they must stop waiting for more PCM packets than the ones
 * already pushed.
 * 
 * The queue of PCM chunks, along with the queues of other TTS steps, share a
 * global max size so as to make sure that they won't grow too much if the
 * encoding is slower than the synthesizing (see AudioBuffersTracker).
 * 
 * If an EncodingThread fails to encode samples, it won't set the URI attribute
 * of the audio chunks. In that way, SSMLtoAudio is informed not to include the
 * corresponding text into the list of audio clips.
 * 
 */
class SSMLtoAudio implements IProgressListener {
	private TTSService mLastTTS; //used if no TTS is found for the current sentence
	private TTSRegistry mTTSRegistry;
	private IPipelineLogger mLogger;
	private ContiguousText mCurrentSection;
	private Voice mPreviousVoice;
	private File mAudioDir; //where all the sound files will be stored
	private long mTotalTextSize; //used for the progress bar
	private long mPrintedProgress;
	private long mProgress;
	private int mDocumentPosition;
	private Map<TTSService, List<ContiguousText>> mOrganizedText;
	private AudioBufferTracker mAudioBufferTracker;
	private Processor mProc;

	SSMLtoAudio(File audioDir, TTSRegistry ttsregistry, IPipelineLogger logger,
	        AudioBufferTracker audioBufferTracker, Processor proc) {
		mTTSRegistry = ttsregistry;
		mLogger = logger;
		mCurrentSection = null;
		mPreviousVoice = null;
		mDocumentPosition = 0;
		mOrganizedText = new HashMap<TTSService, List<ContiguousText>>();
		mAudioBufferTracker = audioBufferTracker;
		mProc = proc;
	}

	/**
	 * The SSML is assumed to be pushed in document order.
	 **/
	void dispatchSSML(XdmNode ssml) throws SynthesisException {
		String voiceVendor = ssml.getAttributeValue(new QName("voice-selector1"));
		String voiceName = ssml.getAttributeValue(new QName("voice-selector2"));
		String lang = ssml.getAttributeValue(new QName("http://www.w3.org/XML/1998/namespace",
		        "lang"));

		Voice voice = mTTSRegistry.findAvailableVoice(voiceVendor, voiceName, lang);
		if (voice == null) {
			mLogger.printInfo("Could not find any installed voice matching "
			        + new Voice(voiceVendor, voiceName) + " or providing the language '"
			        + lang + "'");
			if (mPreviousVoice == null) {
				mLogger.printInfo("The corresponding part of the text won't be synthesized.");
				endSection();
				return;
			} else {
				voice = mPreviousVoice;
				mLogger.printInfo("Voice " + voice + " will be used instead.");
			}
		} else
			mPreviousVoice = voice;

		TTSService newSynth = mTTSRegistry.getTTS(voice);
		if (newSynth == null) {
			/*
			 * Should not happen since findAvailableVoice() returns only a
			 * non-null voice if a TTSService can provide it
			 */
			mLogger.printInfo("Could find any TTS processor for the voice " + voice);
			return;
		}

		/*
		 * If a TTS Service has no reserved threads, its sentences are pushed to
		 * a global list whose key is null. Otherwise the TTS Service is used as
		 * key.
		 */
		TTSService poolkey = null;
		if (newSynth.reservedThreadNum() > 0) {
			poolkey = newSynth;
		}

		if (newSynth != mLastTTS) {
			if (mLastTTS != null
			        && (poolkey != null || mLastTTS.reservedThreadNum() != 0 || mLastTTS
			                .getAudioOutputFormat().equals(newSynth.getAudioOutputFormat())))
				endSection(); // necessary because the same thread wouldn't be able to
				              // concatenate outputs of different formats
			mLastTTS = newSynth;
		}

		if (mCurrentSection == null) {
			//happen the first time and whenever endSection() is called
			mCurrentSection = new ContiguousText(mDocumentPosition++, mAudioDir, newSynth
			        .getAudioOutputFormat());

			List<ContiguousText> listOfSections = mOrganizedText.get(poolkey);
			if (listOfSections == null) {
				listOfSections = new ArrayList<ContiguousText>();
				mOrganizedText.put(poolkey, listOfSections);
			}

			listOfSections.add(mCurrentSection);

		}
		mCurrentSection.sentences.add(new Sentence(newSynth, voice, ssml));
	}

	void endSection() {
		mCurrentSection = null;
	}

	Iterable<SoundFileLink> blockingRun(AudioServices audioServices)
	        throws SynthesisException, InterruptedException {

		//SSML mark splitter shared by the threads:
		SSMLMarkSplitter ssmlSplitter = new DefaultSSMLMarkSplitter(mProc);

		reorganizeSections();
		mProgress = 0;
		mPrintedProgress = 0;

		//threading layout
		int reservedThreadNum = 0;
		for (TTSService tts : mOrganizedText.keySet()) {
			if (tts != null && tts.reservedThreadNum() > 0)
				reservedThreadNum += tts.reservedThreadNum();
		}
		int cores = Runtime.getRuntime().availableProcessors();
		int ttsThreadNum = Integer.valueOf(System.getProperty("tts.threads", String
		        .valueOf(cores)));
		int encodingThreadNum = Integer.valueOf(System.getProperty("tts.encoding.threads",
		        String.valueOf(ttsThreadNum)));
		int regularTTSthreadNum = Integer.valueOf(System.getProperty("tts.speak.threads",
		        String.valueOf(ttsThreadNum)));
		int totalTTSThreads = regularTTSthreadNum + reservedThreadNum;
		int maxMemPerTTSThread = 20 * 1048576; //20MB
		mLogger.printInfo("Number of encoding threads: " + encodingThreadNum);
		mLogger.printInfo("Number of regular text-to-speech threads: " + regularTTSthreadNum);
		mLogger.printInfo("Number of reserved text-to-speech threads: " + reservedThreadNum);
		mLogger.printInfo("Max TTS memory footprint (encoding excluded): "
		        + mAudioBufferTracker.getSpaceForTTS() / 1000000 + "MB");
		mLogger.printInfo("Max encoding memory footprint: "
		        + mAudioBufferTracker.getSpaceForEncoding() / 1000000 + "MB");

		//input queue common to all the threads
		BlockingQueue<ContiguousPCM> pcmQueue = new PriorityBlockingQueue<ContiguousPCM>();

		//start the TTS threads
		TextToPcmThread[] tpt = new TextToPcmThread[totalTTSThreads];
		List<ContiguousText> text = mOrganizedText.get(null);
		if (text == null) {
			text = Collections.EMPTY_LIST;
		}
		ConcurrentLinkedQueue<ContiguousText> stext = new ConcurrentLinkedQueue<ContiguousText>(
		        text);
		int i = 0;
		for (; i < regularTTSthreadNum; ++i) {
			tpt[i] = new TextToPcmThread();
			tpt[i].start(stext, pcmQueue, mTTSRegistry, ssmlSplitter, this, mLogger,
			        mAudioBufferTracker, maxMemPerTTSThread);
		}
		for (Map.Entry<TTSService, List<ContiguousText>> e : mOrganizedText.entrySet()) {
			TTSService tts = e.getKey();
			if (tts != null) { //tts = null is handled by the previous loop
				stext = new ConcurrentLinkedQueue<ContiguousText>(e.getValue());
				for (int j = 0; j < tts.reservedThreadNum(); ++i, ++j) {
					tpt[i] = new TextToPcmThread();
					tpt[i].start(stext, pcmQueue, mTTSRegistry, ssmlSplitter, this, mLogger,
					        mAudioBufferTracker, maxMemPerTTSThread);
				}
			}
		}
		mLogger.printInfo("Text-to-speech threads started.");

		//start the encoding threads
		EncodingThread[] encodingTh = new EncodingThread[encodingThreadNum];
		for (int j = 0; j < encodingTh.length; ++j) {
			encodingTh[j] = new EncodingThread();
			encodingTh[j].start(audioServices, pcmQueue, mLogger, mAudioBufferTracker);
		}
		mLogger.printInfo("Encoding threads started.");

		//collect the sound fragments
		Collection<SoundFileLink>[] fragments = new Collection[tpt.length];
		for (int j = 0; j < tpt.length; ++j) {
			fragments[j] = tpt[j].getSoundFragments();
		}

		//send END notifications and wait for the encoding threads to finish
		mLogger.printInfo("Text-to-speech finished. Waiting for audio encoding to finish...");
		for (int k = 0; k < encodingTh.length; ++k) {
			pcmQueue.add(ContiguousPCM.EndOfQueue);
		}
		for (int j = 0; j < encodingTh.length; ++j)
			encodingTh[j].waitToFinish();

		mLogger.printInfo("Audio encoding finished.");

		return Iterables.concat(fragments);
	}

	@Override
	synchronized public void notifyFinished(ContiguousText section) {
		mProgress += section.getStringSize();
		if (mProgress - mPrintedProgress > mTotalTextSize / 15) {
			int TTSMem = mAudioBufferTracker.getUnreleasedTTSMem() / 1000000;
			int EncodeMem = mAudioBufferTracker.getUnreleasedEncondingMem() / 1000000;
			mLogger.printInfo("progress: " + 100 * mProgress / mTotalTextSize + "%  [TTS: "
			        + TTSMem + "MB encoding: " + EncodeMem + "MB]");
			mPrintedProgress = mProgress;
		}
	}

	private void reorganizeSections() {
		mTotalTextSize = 0;
		int sectionCount = 0;
		for (List<ContiguousText> sections : mOrganizedText.values()) {
			//compute the sections' size: needed for displaying the progress,
			//splitting the sections and sorting them
			for (ContiguousText section : sections)
				section.computeSize();

			for (ContiguousText section : sections) {
				mTotalTextSize += section.getStringSize();
			}
			//split up the sections that are too big
			int maxSize = (int) (mTotalTextSize / 15);
			List<ContiguousText> newSections = new ArrayList<ContiguousText>();
			List<ContiguousText> toRemove = new ArrayList<ContiguousText>();
			for (ContiguousText section : sections) {
				if (section.getStringSize() >= maxSize) {
					toRemove.add(section);
					splitSection(section, maxSize, newSections);
				}
			}

			sections.removeAll(toRemove);
			sections.addAll(newSections);

			//sort the sections according to their size in descending-order
			Collections.sort(sections);

			//keep sorted only the smallest sections (50% of total) so the biggest sections won't
			//necessarily be processed at the same time, as that may consume too much memory.
			Collections.shuffle(sections.subList(0, sections.size() / 2));

			sectionCount += sections.size();
		}
		mLogger.printInfo("Number of TTS sections: " + sectionCount);
	}

	private void splitSection(ContiguousText section, int maxSize,
	        List<ContiguousText> newSections) {
		int left = 0;
		int count = 0;
		ContiguousText currentSection = new ContiguousText(section.getDocumentPosition(),
		        section.getAudioOutputDir(), section.getAudioFormat());
		currentSection.setStringsize(0);
		for (int right = 0; right < section.sentences.size(); ++right) {
			if (currentSection.getStringSize() > maxSize) {
				currentSection.sentences = section.sentences.subList(left, right);
				currentSection.setDocumentSplitPosition(count);
				newSections.add(currentSection);
				currentSection = new ContiguousText(section.getDocumentPosition(), section
				        .getAudioOutputDir(), section.getAudioFormat());
				currentSection.setStringsize(0);
				left = right;
				++count;
			}
			currentSection.setStringsize(currentSection.getStringSize()
			        + section.sentences.get(right).getSize());
		}
		currentSection.sentences = section.sentences.subList(left, section.sentences.size());
		currentSection.setDocumentSplitPosition(count);
		newSections.add(currentSection);
	}

}
