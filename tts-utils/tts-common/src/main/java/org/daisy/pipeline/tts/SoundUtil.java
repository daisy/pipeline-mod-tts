package org.daisy.pipeline.tts;

import java.io.File;
import java.io.IOException;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.daisy.pipeline.tts.TTSService.RawAudioBuffer;

public class SoundUtil {
	private static int MinRiffHeaderSize = 44;

	static public void readWave(File soundFile, RawAudioBuffer audioBuffer)
	        throws UnsupportedAudioFileException, IOException {
		int maxLength = (int) (soundFile.length() - MinRiffHeaderSize);
		if (maxLength < 0) {
			throw new IOException(soundFile.getAbsolutePath()
			        + " is too small to be a WAV file");
		}
		
		if (maxLength > (audioBuffer.output.length - audioBuffer.offsetInOutput)) {
			realloc(audioBuffer, maxLength);
		}

		AudioInputStream fi = AudioSystem.getAudioInputStream(soundFile);
		int read = 0;
		while (audioBuffer.offsetInOutput + read != audioBuffer.output.length && read != -1) {
			audioBuffer.offsetInOutput += read;
			read = fi.read(audioBuffer.output, audioBuffer.offsetInOutput,
			        audioBuffer.output.length - audioBuffer.offsetInOutput);
		}
		fi.close();
	}

	static public void realloc(RawAudioBuffer buffer, int extra) {
		byte[] newBuffer = new byte[buffer.offsetInOutput + extra];
		System.arraycopy(buffer.output, 0, newBuffer, 0, buffer.offsetInOutput);
		buffer.output = newBuffer;
	}
}
