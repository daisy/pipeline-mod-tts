package org.daisy.pipeline.tts.osx;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

import net.sf.saxon.s9api.XdmNode;

import org.daisy.pipeline.audio.AudioBuffer;
import org.daisy.pipeline.tts.AbstractTTSService;
import org.daisy.pipeline.tts.AudioBufferAllocator;
import org.daisy.pipeline.tts.AudioBufferAllocator.MemoryException;
import org.daisy.pipeline.tts.SoundUtil;
import org.daisy.pipeline.tts.TTSRegistry.TTSResource;
import org.daisy.pipeline.tts.TTSServiceUtil;
import org.daisy.pipeline.tts.Voice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This synthesizer uses the OS X "say" command.
 * 
 * This is a naïve implementation which just discards all SSML tagging.
 */
public class OSXSpeechTTS extends AbstractTTSService {

	private Logger mLogger = LoggerFactory.getLogger(OSXSpeechTTS.class);

	private AudioFormat mAudioFormat;
	private String mSayPath = "/usr/bin/say";
	private final static int MIN_CHUNK_SIZE = 2048;
	private final static String SAY_PATH = "tts.osxspeech.path";

	public void onBeforeOneExecution() throws SynthesisException, InterruptedException {
		mSayPath = System.getProperty(SAY_PATH, "/usr/bin/say");
		mLogger.info("Will use 'say' binary: " + mSayPath);

		// Test the synthesizer so that the service won't be active if it fails.
		// It sets mAudioFormat too.
		mAudioFormat = null;
		Throwable t = TTSServiceUtil.testTTS(this, "test");

		if (t != null) {
			throw new SynthesisException("the 'say' command did not output audio.", t);
		}
	}

	@Override
	public AudioFormat getAudioOutputFormat() {
		return mAudioFormat;
	}

	@Override
	public String getName() {
		return "osx-speech";
	}

	@Override
	public String getVersion() {
		return "command-line";
	}

	@Override
	public int getOverallPriority() {
		return Integer.valueOf(System.getProperty("osx-speech.priority", "5"));
	}

	@Override
	public Collection<Voice> getAvailableVoices() throws SynthesisException {
		Collection<Voice> result = new ArrayList<Voice>();
		InputStream is;
		Process proc = null;
		Scanner scanner = null;
		Matcher mr;
		try {
			proc = Runtime.getRuntime().exec(new String[]{
			        mSayPath, "-v", "?"
			});
			is = proc.getInputStream();
			mr = Pattern.compile("(.*?)\\s+\\w{2}_\\w{2}").matcher("");
			scanner = new Scanner(is);
			while (scanner.hasNextLine()) {
				mr.reset(scanner.nextLine());
				mr.find();
				result.add(new Voice(getName(), mr.group(1).trim()));
			}
			is.close();
			proc.waitFor();
		} catch (Exception e) {
			if (proc != null) {
				proc.destroy();
			}
			throw new SynthesisException(e.getMessage(), e.getCause());
		} finally {
			if (scanner != null)
				scanner.close();
		}

		return result;
	}

	@Override
	public Collection<AudioBuffer> synthesize(String text, XdmNode xmlText, Voice voice,
	        TTSResource threadResources, List<Mark> marks,
	        AudioBufferAllocator bufferAllocator, boolean retry) throws SynthesisException,
	        InterruptedException, MemoryException {
		Collection<AudioBuffer> result = new ArrayList<AudioBuffer>();
		Process p = null;
		File waveOut = null;
		try {

			waveOut = File.createTempFile("pipeline", ".wav");
			p = Runtime.getRuntime().exec(new String[]{
			        mSayPath, "--data-format=LEI16@22050", "-o", waveOut.getAbsolutePath()
			});

			// write the sentence
			BufferedOutputStream out = new BufferedOutputStream((p.getOutputStream()));
			out.write(text.getBytes("utf-8"));
			out.close();

			p.waitFor();

			// read the wave on the standard output

			BufferedInputStream in = new BufferedInputStream(new FileInputStream(waveOut));
			AudioInputStream fi = AudioSystem.getAudioInputStream(in);

			if (mAudioFormat == null)
				mAudioFormat = fi.getFormat();

			while (true) {
				AudioBuffer b = bufferAllocator
				        .allocateBuffer(MIN_CHUNK_SIZE + fi.available());
				int ret = fi.read(b.data, 0, b.size);
				if (ret == -1) {
					//note: perhaps it would be better to call allocateBuffer()
					//somewhere else in order to avoid this extra call:
					bufferAllocator.releaseBuffer(b);
					break;
				}
				b.size = ret;
				result.add(b);
			}

			fi.close();
		} catch (MemoryException e) {
			SoundUtil.cancelFootPrint(result, bufferAllocator);
			p.destroy();
			throw e;
		} catch (InterruptedException e) {
			SoundUtil.cancelFootPrint(result, bufferAllocator);
			if (p != null)
				p.destroy();
			throw e;
		} catch (Exception e) {
			SoundUtil.cancelFootPrint(result, bufferAllocator);
			StringWriter sw = new StringWriter();
			e.printStackTrace(new PrintWriter(sw));
			if (p != null)
				p.destroy();
			throw new SynthesisException(e.getMessage() + " text: "
			        + text.substring(0, Math.min(text.length(), 100)) + "...", e);
		} finally {
			if (waveOut != null)
				waveOut.delete();
		}
		return result;
	}
}
