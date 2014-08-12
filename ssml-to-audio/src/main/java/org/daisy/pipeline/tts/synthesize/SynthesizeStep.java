package org.daisy.pipeline.tts.synthesize;

import java.io.File;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.Semaphore;

import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmSequenceIterator;

import org.daisy.pipeline.audio.AudioServices;
import org.daisy.pipeline.tts.AudioBufferTracker;
import org.daisy.pipeline.tts.TTSRegistry;
import org.daisy.pipeline.tts.TTSService.SynthesisException;

import com.google.common.collect.Iterables;
import com.xmlcalabash.core.XProcRuntime;
import com.xmlcalabash.io.ReadablePipe;
import com.xmlcalabash.io.WritablePipe;
import com.xmlcalabash.library.DefaultStep;
import com.xmlcalabash.model.RuntimeValue;
import com.xmlcalabash.runtime.XAtomicStep;
import com.xmlcalabash.util.TreeWriter;

public class SynthesizeStep extends DefaultStep implements FormatSpecifications,
        IPipelineLogger {

	private ReadablePipe source = null;
	private WritablePipe result = null;
	private XProcRuntime mRuntime;
	private TTSRegistry mTTSRegistry;
	private Random mRandGenerator;
	private String mTempDirectory;
	private AudioServices mAudioServices;
	private Semaphore mStartSemaphore;
	private AudioBufferTracker mAudioBufferTracker;

	private static String convertSecondToString(double seconds) {
		int iseconds = (int) (Math.floor(seconds));
		int milliseconds = (int) (Math.floor(1000 * (seconds - iseconds)));
		return String.format("%d:%02d:%02d.%03d", iseconds / 3600, (iseconds / 60) % 60,
		        (iseconds % 60), milliseconds);
	}

	public static XdmNode getFirstChild(XdmNode node) {
		XdmSequenceIterator iter = node.axisIterator(Axis.CHILD);
		if (iter.hasNext()) {
			return (XdmNode) iter.next();
		} else {
			return null;
		}
	}

	public SynthesizeStep(XProcRuntime runtime, XAtomicStep step, TTSRegistry ttsRegistry,
	        AudioServices audioServices, Semaphore startSemaphore,
	        AudioBufferTracker audioBufferTracker) {
		super(runtime, step);
		mStartSemaphore = startSemaphore;
		mAudioBufferTracker = audioBufferTracker;
		mAudioServices = audioServices;
		mRuntime = runtime;
		mTTSRegistry = ttsRegistry;
		mRandGenerator = new Random();
		mTempDirectory = System.getProperty("audio.tmpdir");
		if (mTempDirectory == null)
			mTempDirectory = System.getProperty("java.io.tmpdir");
	}

	@Override
	synchronized public void printInfo(String message) {
		mRuntime.info(this, null, message);
	}

	@Override
	synchronized public void printDebug(String message) {
		if (mRuntime.getDebug()) {
			mRuntime.info(this, null, message);
		}
	}

	public void setInput(String port, ReadablePipe pipe) {
		if ("source".equals(port)) {
			source = pipe;
		}
	}

	public void setOutput(String port, WritablePipe pipe) {
		result = pipe;
	}

	@Override
	public void setOption(QName name, RuntimeValue value) {
		super.setOption(name, value);
	}

	public void reset() {
		source.resetReader();
		result.resetWriter();
	}

	public void traverse(XdmNode node, SSMLtoAudio pool) throws SynthesisException {
		if (SentenceTag.equals(node.getNodeName())) {
			pool.dispatchSSML(node);
		} else {
			XdmSequenceIterator iter = node.axisIterator(Axis.CHILD);
			while (iter.hasNext()) {
				traverse((XdmNode) iter.next(), pool);
			}
		}
	}

	public void run() throws SaxonApiException {
		super.run();

		try {
			mStartSemaphore.acquire();
		} catch (InterruptedException e) {
			mRuntime.error(e);
			return;
		}

		mTTSRegistry.openSynthesizingContext(mRuntime.getProcessor()
		        .getUnderlyingConfiguration());

		File audioOutputDir = null;
		do {
			String audioDir = mTempDirectory + "/";
			for (int k = 0; k < 2; ++k)
				audioDir += Long.toString(mRandGenerator.nextLong(), 32);
			audioOutputDir = new File(audioDir);
		} while (audioOutputDir.exists());
		audioOutputDir.mkdir();

		SSMLtoAudio ssmltoaudio = new SSMLtoAudio(audioOutputDir, mTTSRegistry, this,
		        mAudioBufferTracker, mRuntime.getProcessor());

		Iterable<SoundFileLink> soundFragments = Collections.EMPTY_LIST;
		try {
			while (source.moreDocuments()) {
				traverse(getFirstChild(source.read()), ssmltoaudio);
				ssmltoaudio.endSection();
			}
			Iterable<SoundFileLink> newfrags = ssmltoaudio.blockingRun(mAudioServices);
			soundFragments = Iterables.concat(soundFragments, newfrags);
		} catch (SynthesisException e) {
			mRuntime.error(e);
			return;
		} catch (InterruptedException e) {
			mRuntime.error(e);
			return;
		} finally {
			mTTSRegistry.closeSynthesizingContext();
			mStartSemaphore.release();
		}

		TreeWriter tw = new TreeWriter(runtime);
		tw.startDocument(runtime.getStaticBaseURI());
		tw.addStartElement(OutputRootTag);

		int num = 0;
		for (SoundFileLink sf : soundFragments) {
			String soundFileURI = sf.soundFileURIHolder.toString();
			if (!soundFileURI.isEmpty()) {
				tw.addStartElement(ClipTag);
				tw.addAttribute(Audio_attr_id, sf.xmlid);
				tw.addAttribute(Audio_attr_clipBegin, convertSecondToString(sf.clipBegin));
				tw.addAttribute(Audio_attr_clipEnd, convertSecondToString(sf.clipEnd));
				tw.addAttribute(Audio_attr_src, soundFileURI);
				tw.addEndElement();
				++num;
			} else {
				printInfo("error: text with id=" + sf.xmlid
				        + " has not been fully synthesized or encoded.");
			}
		}
		tw.addEndElement();
		tw.endDocument();

		printInfo("number of synthesized sound fragments: " + num);
		printInfo("audio encoding unreleased bytes : "
		        + mAudioBufferTracker.getUnreleasedEncondingMem());
		printInfo("TTS unreleased bytes: " + mAudioBufferTracker.getUnreleasedTTSMem());

		result.write(tw.getResult());
	}
}
