package org.daisy.pipeline.tts.synthesize;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmNode;

import org.daisy.pipeline.audio.AudioEncoder;
import org.daisy.pipeline.tts.TTSRegistry.TTSResource;
import org.daisy.pipeline.tts.TTSService;
import org.daisy.pipeline.tts.TTSService.RawAudioBuffer;
import org.daisy.pipeline.tts.TTSService.SynthesisException;
import org.daisy.pipeline.tts.TTSServiceUtil;
import org.daisy.pipeline.tts.Voice;
import org.daisy.pipeline.tts.synthesize.SynthesisWorkerPool.Speakable;
import org.daisy.pipeline.tts.synthesize.SynthesisWorkerPool.UndispatchableSection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The SynthesisWorkerThread is meant to be used by a SynthesisWorkerPool. It
 * works as follow: when it receives a new piece of SSML, it calls the
 * synthesizer to transform it to raw audio data. The data is added to the
 * thread's internal audio buffer and when the buffer is about to be full, the
 * threads call the encoder to transform the buffer into a compressed audio
 * file.
 */
public class SynthesisWorkerThread extends Thread implements FormatSpecifications {

	private Logger ServerLogger = LoggerFactory.getLogger(SynthesisWorkerThread.class);

	// 20 seconds with 24kHz 16 bits audio
	private static final int MAX_ESTIMATED_SYNTHESIZED_BYTES = 24000 * 2 * 20;

	// 2 minutes, i.e. ~6MBytes
	private static final int AUDIO_BUFFER_BYTES = 24000 * 2 * 60 * 2;

	//number of tries before giving up synthesizing a piece of SSML
	private static final int SYNTHESIS_TRIES = 3;

	private AudioEncoder mEncoder; // must be thread-safe!
	private byte[] mOutput;
	private RawAudioBuffer mAudioBuffer;
	private List<SoundFragment> mGlobalSoundFragments; // must be thread-safe!
	private List<SoundFragment> mCurrentFragments;
	private IPipelineLogger mLogger;
	private IProgressListener mProgressListener;
	private TTSService mLastUsedSynthesizer; // must be thread-safe!
	private ConcurrentLinkedQueue<UndispatchableSection> mSectionsQueue;
	private Map<TTSService, TTSResource> mResources;
	private UndispatchableSection mCurrentSection;
	private int mSectionFiles;

	public void init(AudioEncoder encoder, IPipelineLogger logger,
	        IProgressListener progressListener, List<SoundFragment> allSoundFragments,
	        ConcurrentLinkedQueue<UndispatchableSection> sectionQueue) {
		mOutput = new byte[AUDIO_BUFFER_BYTES];
		mResources = new HashMap<TTSService, TTSResource>();
		mEncoder = encoder;
		mAudioBuffer = new RawAudioBuffer();
		mAudioBuffer.offsetInOutput = 0;
		mAudioBuffer.output = mOutput;

		mCurrentFragments = new LinkedList<SoundFragment>();
		mGlobalSoundFragments = allSoundFragments;
		mLogger = logger;
		mProgressListener = progressListener;
		mSectionsQueue = sectionQueue;
	}

	private void encodeAudio() {
		String filePrefix = String.format("part%04d_%02d_%04d",
		        mCurrentSection.documentPosition, mCurrentSection.documentSubPosition,
		        mSectionFiles);

		String soundFile = mEncoder.encode(mAudioBuffer.output, mAudioBuffer.offsetInOutput,
		        mLastUsedSynthesizer.getAudioOutputFormat(), this,
		        mCurrentSection.audioOutputDir, filePrefix);

		if (soundFile == null) {
			float sec = mAudioBuffer.offsetInOutput
			        / mLastUsedSynthesizer.getAudioOutputFormat().getFrameRate();
			mLogger.printInfo("" + sec + " seconds of audio data could not be encoded!");
		} else {
			for (SoundFragment sf : mCurrentFragments) {
				sf.soundFileURI = soundFile;
			}
			mGlobalSoundFragments.addAll(mCurrentFragments); // must be thread-safe
		}

		// reset the state of the thread
		mAudioBuffer.offsetInOutput = 0;
		mAudioBuffer.output = mOutput;
		mCurrentFragments.clear();
		++mSectionFiles;
	}

	private double convertBytesToSecond(int bytes) {
		return (bytes / (mLastUsedSynthesizer.getAudioOutputFormat().getFrameRate() * mLastUsedSynthesizer
		        .getAudioOutputFormat().getFrameSize()));
	}

	public void assignResource(TTSService s, TTSResource r) {
		mResources.put(s, r);
	}

	private boolean processOneSentenceOneTry(final XdmNode sentence, final Voice voice,
	        final List<Map.Entry<String, Integer>> marks, final TTSResource resource,
	        final boolean firstTry) throws Throwable {

		final Throwable[] exceptions = new SynthesisException[]{
			null
		};
		TTSTimeout timeout = new TTSTimeout(3 * 60);
		Thread synthJob = new Thread() {
			@Override
			public void run() {
				try {
					mLastUsedSynthesizer.synthesize(sentence, voice, mAudioBuffer, resource,
					        marks, !firstTry);
				} catch (InterruptedException e) {
				} catch (Throwable e) {
					exceptions[0] = e;
				}
			}
		};
		synchronized (resource) {
			//The synchronized is necessary because it is possible that the TTSRegistry 
			//is currently releasing the resource. 
			if (resource.released)
				throw new SynthesisException("TTS resource no longer valid.");

			timeout.watch(synthJob, mLastUsedSynthesizer, resource);
			synthJob.start();
			try {
				synthJob.join();
			} catch (InterruptedException e) {
				throw new SynthesisException(e);
			} finally {
				timeout.cancel();
			}
		}
		if (exceptions[0] != null) {
			throw exceptions[0];
		}

		if (!timeout.stillTime()) {
			ServerLogger.warn("timeout with "
			        + TTSServiceUtil.displayName(mLastUsedSynthesizer));
			return false;
		}

		if (mLastUsedSynthesizer.endingMark() != null
		        && !(marks.size() > 0 && mLastUsedSynthesizer.endingMark().equals(
		                marks.get(marks.size() - 1).getKey()))) {
			String ssml = sentence.toString();
			ServerLogger.warn("missing ending mark with "
			        + TTSServiceUtil.displayName(mLastUsedSynthesizer) + " for SSML: "
			        + ssml.substring(0, Math.min(500, ssml.length())) + "...");

			return false;
		}

		return true;
	}

	private void processOneSentence(final XdmNode sentence, final Voice voice)
	        throws Throwable {
		if (mAudioBuffer.offsetInOutput + MAX_ESTIMATED_SYNTHESIZED_BYTES > mAudioBuffer.output.length) {
			encodeAudio();
		}
		//warning: the resource may be already released if mLastUsedSynthesizer has been stopped during the synthesis
		final TTSResource resource = mResources.get(mLastUsedSynthesizer);
		List<Map.Entry<String, Integer>> marks = new ArrayList<Map.Entry<String, Integer>>();

		//try to synthesize the SSML using an ending mark to know whether if anything went wrong
		int tries;
		boolean valid = false;
		int begin = mAudioBuffer.offsetInOutput;
		for (tries = 0; !valid && tries < SYNTHESIS_TRIES; ++tries) {
			mAudioBuffer.offsetInOutput = begin;
			marks.clear();
			valid = processOneSentenceOneTry(sentence, voice, marks, resource, (tries > 0));
			if (!valid) {
				try {
					Thread.sleep(2000);
				} catch (InterruptedException e) {
				}
			}
		}
		if (!valid) {
			mAudioBuffer.offsetInOutput = begin;
			mLogger.printInfo("Something went wrong: text with id="
			        + sentence.getAttributeValue(new QName(null, "id")) + " not synthesized");

			//The error is not critical: keep going.
			//For critical errors, exceptions are thrown.
			return;
		}
		if (mLastUsedSynthesizer.endingMark() != null) {
			marks = marks.subList(0, marks.size() - 1);
		}

		// keep track of where the sound begins and where it ends in the audio buffer
		if (marks.size() == 0) {
			SoundFragment sf = new SoundFragment();
			sf.id = sentence.getAttributeValue(Sentence_attr_id);
			sf.clipBegin = convertBytesToSecond(begin);
			sf.clipEnd = convertBytesToSecond(mAudioBuffer.offsetInOutput);
			mCurrentFragments.add(sf);
		} else {
			Map<String, Integer> starts = new HashMap<String, Integer>();
			Map<String, Integer> ends = new HashMap<String, Integer>();
			Set<String> all = new HashSet<String>();

			for (Map.Entry<String, Integer> e : marks) {
				String[] mark = e.getKey().split(FormatSpecifications.MarkDelimiter, -1);
				if (!mark[0].isEmpty()) {
					ends.put(mark[0], e.getValue());
					all.add(mark[0]);
				}
				if (!mark[1].isEmpty()) {
					starts.put(mark[1], e.getValue());
					all.add(mark[1]);
				}
			}
			for (String id : all) {
				SoundFragment sf = new SoundFragment();
				sf.id = id;
				if (starts.containsKey(id))
					sf.clipBegin = convertBytesToSecond(begin + starts.get(id));
				else
					sf.clipBegin = convertBytesToSecond(begin);
				if (ends.containsKey(id))
					sf.clipEnd = convertBytesToSecond(begin + ends.get(id));
				else
					sf.clipEnd = convertBytesToSecond(mAudioBuffer.offsetInOutput);
				mCurrentFragments.add(sf);
			}
			//note, if marks.size() > 0 but all.size() == 0, it means that no marks refer to
			//no ID. It should imply that the sentence contains skippable elements but no text.
			//In such case, it is important to let the script NOT add any fragment, not even the
			//sentence parent.
		}
	}

	@Override
	public void run() {
		while (true) {
			UndispatchableSection section = mSectionsQueue.poll();
			if (section == null) {
				break;
			}
			try {
				mCurrentSection = section;
				mSectionFiles = 0;
				mLastUsedSynthesizer = section.synthesizer;
				for (Speakable speakable : section.speakables) {
					processOneSentence(speakable.sentence, speakable.voice);
				}
				if (mAudioBuffer.offsetInOutput > 0)
					encodeAudio();
			} catch (Throwable e) {
				StringWriter sw = new StringWriter();
				e.printStackTrace(new PrintWriter(sw));
				String synthInfo = (mLastUsedSynthesizer == null) ? "(synthesizer missing)"
				        : " with synthesizer "
				                + TTSServiceUtil.displayName(mLastUsedSynthesizer)
				                + " (audio-format set:"
				                + (mLastUsedSynthesizer.getAudioOutputFormat() != null) + ")";
				mLogger.printInfo("error in TTS thread " + synthInfo + ": " + sw.toString());
				break;
			}
			mProgressListener.notifyFinished(section);
		}

		for (Map.Entry<TTSService, TTSResource> e : mResources.entrySet()) {
			if (e.getKey().resourcesReleasedASAP()) {
				TTSResource r = e.getValue();
				synchronized (r) {
					if (!r.released) {
						try {
							e.getKey().releaseThreadResources(r);
						} catch (Throwable t) {
							StringWriter sw = new StringWriter();
							t.printStackTrace(new PrintWriter(sw));
							mLogger.printInfo("error while releasing resources of "
							        + TTSServiceUtil.displayName(e.getKey()) + ": "
							        + sw.toString());
						}
						r.released = true;
					}
				}
			}
		}

	}
}
