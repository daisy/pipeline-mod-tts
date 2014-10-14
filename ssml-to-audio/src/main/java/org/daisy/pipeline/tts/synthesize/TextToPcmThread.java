package org.daisy.pipeline.tts.synthesize;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.sound.sampled.AudioFormat;

import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;

import org.daisy.common.xslt.ThreadUnsafeXslTransformer;
import org.daisy.pipeline.audio.AudioBuffer;
import org.daisy.pipeline.tts.AudioBufferAllocator.MemoryException;
import org.daisy.pipeline.tts.AudioBufferTracker;
import org.daisy.pipeline.tts.SSMLMarkSplitter;
import org.daisy.pipeline.tts.SSMLMarkSplitter.Chunk;
import org.daisy.pipeline.tts.SoundUtil;
import org.daisy.pipeline.tts.TTSRegistry;
import org.daisy.pipeline.tts.TTSRegistry.TTSResource;
import org.daisy.pipeline.tts.TTSService;
import org.daisy.pipeline.tts.TTSService.Mark;
import org.daisy.pipeline.tts.TTSService.SynthesisException;
import org.daisy.pipeline.tts.TTSServiceUtil;
import org.daisy.pipeline.tts.TTSTimeout;
import org.daisy.pipeline.tts.TTSTimeout.ThreadFreeInterrupter;
import org.daisy.pipeline.tts.Voice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Iterables;

/**
 * TextToPcmThread consumes text from a shared queue. It produces PCM data as
 * output, which in turn are pushed to another shared queue consumed by the
 * EncodingThreads. PCM is produced by calling TTS processors.
 * 
 * TTS processors may fail for some reasons, e.g. after a timeout or because the
 * ending SSML mark is missing. In such cases, TextToPcmThread will clean up the
 * resources and attempt to synthesize the current sentence with another TTS
 * processor chosen by the TTSRegistry, unless the error is a MemoryException,
 * in which case the thread gives up on the guilty sentence.
 * 
 * The resources of the TTS processors (e.g. sockets) are allocated on-the-fly
 * and are all released at the end of the thread execution.
 * 
 */
public class TextToPcmThread implements FormatSpecifications {
	private Logger ServerLogger = LoggerFactory.getLogger(TextToPcmThread.class);
	private Map<TTSService, TTSResource> mResources = new HashMap<TTSService, TTSResource>();
	private Map<TTSService, ThreadUnsafeXslTransformer> mTransforms = new HashMap<TTSService, ThreadUnsafeXslTransformer>();
	private int mFileNrInSection; //usually = 0, but incremented when a flush occurs within a section
	private List<SoundFileLink> mSoundFileLinks; //result provided back to the SynthesizeStep caller
	private List<SoundFileLink> mLinksOfCurrentFile; //links under construction
	private Iterable<AudioBuffer> mBuffersOfCurrentFile; //buffers under construction
	private int mOffsetInFile; // reset after every flush
	private int mMemFootprint; //reset after every flush
	private Thread mThread;
	private TTSRegistry mTTSRegistry;
	private IPipelineLogger mPipelineLogger;
	private AudioFormat lastFormat; //used for knowing if a flush is necessary
	private AudioBufferTracker mAudioBufferTracker;
	private SSMLMarkSplitter mSSMLSplitter;
	private Map<String, Object> transformParams = new TreeMap<String, Object>();

	void start(final ConcurrentLinkedQueue<ContiguousText> input,
	        final BlockingQueue<ContiguousPCM> pcmOutput, TTSRegistry ttsregistry,
	        SSMLMarkSplitter ssmlSplitter, final IProgressListener progressListener,
	        IPipelineLogger pLogger, AudioBufferTracker AudioBufferTracker,
	        final int maxQueueEltSize) {
		mSSMLSplitter = ssmlSplitter;
		mSoundFileLinks = new ArrayList<SoundFileLink>();
		mTTSRegistry = ttsregistry;
		mPipelineLogger = pLogger;
		mAudioBufferTracker = AudioBufferTracker;
		flush(null, pcmOutput);

		mThread = new Thread() {
			@Override
			public void run() {
				TTSTimeout timeout = new TTSTimeout();

				/* Main loop */
				while (true) {
					ContiguousText section = input.poll();
					if (section == null) { //queue is empty
						break;
					}
					mFileNrInSection = 0;
					boolean breakloop = false;
					for (Sentence sentence : section.sentences) {
						try {
							speak(section, sentence, pcmOutput, timeout, maxQueueEltSize);
						} catch (Throwable t) {
							StringWriter sw = new StringWriter();
							t.printStackTrace(new PrintWriter(sw));
							mPipelineLogger.printInfo("Sentence " + sentence.getID()
							        + " caused the current thread to stop because of error: "
							        + sw.toString());
							breakloop = true;
							break;
						}
					}
					flush(section, pcmOutput);
					progressListener.notifyFinished(section);
					if (breakloop)
						break;
				}
				timeout.close();

				//release the TTS resources
				for (Map.Entry<TTSService, TTSResource> e : mResources.entrySet()) {
					releaseResource(e.getKey(), e.getValue());
				}
			}
		};
		mThread.start();
	}

	Collection<SoundFileLink> getSoundFragments() {
		if (mThread != null) {
			try {
				mThread.join();
			} catch (InterruptedException e) {
				//should not happen
			}
			mThread = null;
		}

		return mSoundFileLinks;
	}

	private void releaseResource(TTSService tts, TTSResource r) {
		if (r == null) {
			return;
		}
		synchronized (r) {
			if (!r.released) {
				try {
					tts.releaseThreadResources(r);
				} catch (Throwable t) {
					StringWriter sw = new StringWriter();
					t.printStackTrace(new PrintWriter(sw));
					ServerLogger.debug("error while releasing resources of "
					        + TTSServiceUtil.displayName(tts) + ": " + sw.toString());
				}
				r.released = true;
			}
		}
	}

	private void flush(ContiguousText section, BlockingQueue<ContiguousPCM> pcmOutput) {
		if (section != null && mLinksOfCurrentFile.size() > 0) {
			String filePrefix = String.format("part%04d_%02d_%03d", section
			        .getDocumentPosition(), section.getDocumentSplitPosition(),
			        mFileNrInSection);

			ContiguousPCM pcm = new ContiguousPCM(section.getAudioFormat(),
			        mBuffersOfCurrentFile, section.getAudioOutputDir(), filePrefix);
			for (SoundFileLink clip : mLinksOfCurrentFile) {
				clip.soundFileURIHolder = pcm.getURIholder();
			}
			try {
				mAudioBufferTracker.transferToEncoding(mMemFootprint, pcm.sizeInBytes());
			} catch (InterruptedException e) {
				// Should never happen since interruptions only occur during calls to TTS processors.
			}
			pcmOutput.add(pcm);
			pcm = null;
			mSoundFileLinks.addAll(mLinksOfCurrentFile);
			++mFileNrInSection;
		}
		mLinksOfCurrentFile = new ArrayList<SoundFileLink>();
		mBuffersOfCurrentFile = new ArrayList<AudioBuffer>();
		mOffsetInFile = 0;
		mMemFootprint = 0;
		lastFormat = null;
	}

	/**
	 * Wrapper around TTSService.synthesize() to transform the SSML into string
	 */
	public Collection<AudioBuffer> synthesizeSSML(TTSService tts, XdmNode ssml, Voice voice,
	        TTSResource threadResources, List<Mark> marks) throws SaxonApiException,
	        SynthesisException, InterruptedException, MemoryException {
		String transformed = transformSSML(ssml, tts, voice);
		return tts.synthesize(transformed, ssml, voice, threadResources, marks,
		        mAudioBufferTracker, false);
	}

	/**
	 * Wrapper around synthesizeSSML() to handle marks
	 */
	public Iterable<AudioBuffer> synthesize(TTSService tts, XdmNode ssml, Voice voice,
	        TTSResource threadResources, List<Mark> marks) throws SaxonApiException,
	        SynthesisException, InterruptedException, MemoryException {
		if (tts.endingMark() != null) //can handle marks
			return synthesizeSSML(tts, ssml, voice, threadResources, marks);
		else {
			Collection<Chunk> chunks = mSSMLSplitter.split(ssml);
			Iterable<AudioBuffer> result = new ArrayList<AudioBuffer>();
			int offset = 0;
			for (Chunk chunk : chunks) {
				Collection<AudioBuffer> buffers = null;
				try {
					buffers = synthesizeSSML(tts, ssml, voice, threadResources, null);
				} catch (MemoryException | SaxonApiException | SynthesisException
				        | InterruptedException e) {
					//TODO: flush the buffers here
					SoundUtil.cancelFootPrint(result, mAudioBufferTracker);
					throw e;
				}

				if (chunk.leftMark() != null) {
					marks.add(new Mark(chunk.leftMark(), offset));
				}
				for (AudioBuffer b : buffers) {
					offset += b.size;
				}
				result = Iterables.concat(result, buffers);
			}

			return result;
		}
	}

	/**
	 * @return null if something went wrong
	 */
	private Iterable<AudioBuffer> speakWithVoice(Sentence sentence, Voice v,
	        final TTSService tts, List<Mark> marks, TTSTimeout timeout) throws MemoryException {
		//allocate a TTS resource if necessary
		TTSResource resource = mResources.get(tts);
		if (resource == null) {
			try {
				timeout.enableForCurrentThread(3); //3 seconds
				resource = mTTSRegistry.allocateResourceFor(tts);
			} catch (SynthesisException e) {
				ServerLogger.warn("error when allocating resources for "
				        + TTSServiceUtil.displayName(tts) + ": " + e);
				return null;
			} catch (InterruptedException e) {
				ServerLogger.warn("Timeout while trying to allocate resources for "
				        + TTSServiceUtil.displayName(tts));
				return null;
			} finally {
				timeout.disable();
			}
			if (resource == null) {
				//TTS not working anymore?
				mPipelineLogger
				        .printInfo("Could not allocate resource for "
				                + TTSServiceUtil.displayName(tts)
				                + " (it has probably been stopped).");
				return null; //it will try with another TTS
			}
			mPipelineLogger.printInfo("Resource allocated for "
			        + TTSServiceUtil.displayName(tts));
			mResources.put(tts, resource);

			if (!mTransforms.containsKey(tts)) {
				mTransforms.put(tts, mTTSRegistry.getSSMLTransformer(tts).newTransformer());
			}
		}

		//convert the input sentence into PCM using the TTS processor
		Iterable<AudioBuffer> pcm = null;
		int timeoutSecs = 1 + 3 * tts.expectedMillisecPerWord() * sentence.getSize()
		        / (6 * 1000); //~6 chars/word
		final TTSResource fresource = resource;
		TTSTimeout.ThreadFreeInterrupter interrupter = new ThreadFreeInterrupter() {
			@Override
			public void threadFreeInterrupt() {
				ServerLogger.warn("Forcing interruption of the current work of "
				        + TTSServiceUtil.displayName(tts) + "...");
				tts.interruptCurrentWork(fresource);
			}
		};
		try {
			timeout.enableForCurrentThread(interrupter, timeoutSecs);
			synchronized (resource) {
				if (resource.released) {
					mPipelineLogger.printInfo("Resource of " + TTSServiceUtil.displayName(tts)
					        + " no longer valid. It has probably been stopped.");
					return null;
				}
				pcm = synthesize(tts, sentence.getText(), v, resource, marks);
			}
		} catch (InterruptedException e) {
			ServerLogger.warn("timeout (" + timeoutSecs
			        + " seconds) fired while speaking with " + TTSServiceUtil.displayName(tts)
			        + " on " + getPrintableContext(sentence, tts, v));
			return null;
		} catch (SynthesisException e) {
			ServerLogger.warn("error while speaking with " + TTSServiceUtil.displayName(tts)
			        + " : " + e);
			return null;
		} catch (MemoryException e) {
			throw e;
		} catch (SaxonApiException e) {
			ServerLogger.warn("error while transforming SSML with the XSLT of "
			        + TTSServiceUtil.displayName(tts) + " : " + e);
		} finally {
			timeout.disable();
		}

		//check validity of the result by using the ending mark
		if (tts.endingMark() != null
		        && !(marks.size() > 0 && tts.endingMark().equals(
		                marks.get(marks.size() - 1).name))) {
			SoundUtil.cancelFootPrint(pcm, mAudioBufferTracker);
			ServerLogger.warn("missing ending mark with " + TTSServiceUtil.displayName(tts)
			        + " for " + getPrintableContext(sentence, tts, v)
			        + ". Number of marks received: " + marks.size());
			return null;
		}
		return pcm;
	}

	private void speak(ContiguousText section, Sentence sentence,
	        BlockingQueue<ContiguousPCM> pcmOutput, TTSTimeout timeout, int maxQueueEltSize) {
		TTSService tts = sentence.getTTSproc();
		Voice originalVoice = sentence.getVoice();
		List<Mark> marks = new ArrayList<Mark>();
		Iterable<AudioBuffer> pcm;
		try {
			pcm = speakWithVoice(sentence, originalVoice, tts, marks, timeout);
		} catch (MemoryException e) {
			flush(section, pcmOutput);
			printMemError(sentence, e);
			return;
		}
		if (pcm == null) {
			//release the resource to make it more likely for the next try to succeed
			releaseResource(tts, mResources.get(tts));
			mResources.remove(tts);

			//Find another TTS vendor for this sentence
			Voice newVoice = mTTSRegistry.findSecondaryVoice(sentence.getVoice());
			if (newVoice == null) {
				mPipelineLogger
				        .printInfo("Something went wrong but no fallback voice can be found for "
				                + originalVoice
				                + ". Sentence with id="
				                + sentence.getID()
				                + " won't be synthesized.");
				return;
			}
			tts = mTTSRegistry.getTTS(newVoice); //cannot return null in this case

			//Try with the new vendor
			marks.clear();
			try {
				pcm = speakWithVoice(sentence, newVoice, tts, marks, timeout);
			} catch (MemoryException e) {
				flush(section, pcmOutput);
				printMemError(sentence, e);
				return;
			}
			if (pcm == null) {
				mPipelineLogger.printInfo("Something went wrong with " + originalVoice
				        + " but fallback voice " + newVoice
				        + " didn't work either. Sentence with id=" + sentence.getID()
				        + " won't be synthesized.");
				return;
			}

			mPipelineLogger.printInfo("Something went wrong with " + originalVoice
			        + ". Used voice " + newVoice + " instead to synthesize sentence with id="
			        + sentence.getID());

			if (!tts.getAudioOutputFormat().equals(lastFormat))
				flush(section, pcmOutput);
		}
		lastFormat = tts.getAudioOutputFormat();

		int begin = mOffsetInFile;
		try {
			addBuffers(pcm);
		} catch (InterruptedException e) {
			// Should never happen since interruptions only occur during calls to TTS processors.
		}

		if (tts.endingMark() != null) {
			marks = marks.subList(0, marks.size() - 1);
		}

		// keep track of where the sound begins and where it ends within the audio buffers
		if (marks.size() == 0) {
			SoundFileLink sf = new SoundFileLink();
			sf.xmlid = sentence.getID();
			sf.clipBegin = convertBytesToSecond(lastFormat, begin);
			sf.clipEnd = convertBytesToSecond(lastFormat, mOffsetInFile);
			mLinksOfCurrentFile.add(sf);
		} else {
			Map<String, Integer> starts = new HashMap<String, Integer>();
			Map<String, Integer> ends = new HashMap<String, Integer>();
			Set<String> all = new HashSet<String>();

			for (Mark m : marks) {
				String[] mark = m.name.split(FormatSpecifications.MarkDelimiter, -1);
				if (!mark[0].isEmpty()) {
					ends.put(mark[0], m.offsetInAudio);
					all.add(mark[0]);
				}
				if (!mark[1].isEmpty()) {
					starts.put(mark[1], m.offsetInAudio);
					all.add(mark[1]);
				}
			}
			for (String id : all) {
				SoundFileLink sf = new SoundFileLink();
				sf.xmlid = id;
				if (starts.containsKey(id))
					sf.clipBegin = convertBytesToSecond(lastFormat, begin + starts.get(id));
				else
					sf.clipBegin = convertBytesToSecond(lastFormat, begin);
				if (ends.containsKey(id))
					sf.clipEnd = convertBytesToSecond(lastFormat, begin + ends.get(id));
				else
					sf.clipEnd = convertBytesToSecond(lastFormat, mOffsetInFile);
				mLinksOfCurrentFile.add(sf);
			}
			/*
			 * note: if marks.size() > 0 but all.size() == 0, it means that no
			 * marks refer to no ID. It should imply that the sentence contains
			 * skippable elements but no text. In such a case, it is important
			 * to let the script NOT add any fragment, not even the sentence's
			 * parent.
			 */
		}

		if (mMemFootprint > maxQueueEltSize) {
			/*
			 * This flush prevents the TTS processors from raising too many
			 * out-of-memory errors and smoothes the transfers of PCM data to
			 * the encoders.
			 */
			flush(section, pcmOutput);
		}
	}

	private void printMemError(Sentence sentence, MemoryException e) {
		mPipelineLogger.printInfo("Out of memory when processing sentence with id="
		        + sentence.getID() + " (" + e + "). It won't be synthesized.");
	}

	private void addBuffers(Iterable<AudioBuffer> toadd) throws InterruptedException {
		for (AudioBuffer b : toadd) {
			mOffsetInFile += b.size;
			mMemFootprint += mAudioBufferTracker.getFootPrint(b);
		}
		mBuffersOfCurrentFile = Iterables.concat(mBuffersOfCurrentFile, toadd);
	}

	private static double convertBytesToSecond(AudioFormat format, int bytes) {
		return (bytes / (format.getFrameRate() * format.getFrameSize()));
	}

	private String transformSSML(XdmNode ssml, TTSService tts, Voice v)
	        throws SaxonApiException {
		transformParams.put("voice", v.name);
		if (tts.endingMark() != null)
			transformParams.put("ending-mark", tts.endingMark());
		return mTransforms.get(tts).transformToString(ssml, transformParams);
	}

	private String getPrintableContext(Sentence sentence, TTSService tts, Voice v) {
		String res = "original SSML: " + getPrintableLongString(sentence.getText().toString());
		try {
			res += ", serialized SSML: "
			        + getPrintableLongString(transformSSML(sentence.getText(), tts, v));
		} catch (SaxonApiException e) {
		}
		return res;
	}

	private static String getPrintableLongString(String str) {
		int maxsize = 500;
		if (str.length() <= maxsize)
			return str;
		else {
			return str.substring(0, maxsize / 2) + "..."
			        + str.substring(str.length() - maxsize / 2);
		}
	}
}
