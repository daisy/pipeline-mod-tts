package org.daisy.pipeline.tts.sapinative;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import javax.sound.sampled.AudioFormat;

import net.sf.saxon.s9api.XdmNode;

import org.daisy.pipeline.audio.AudioBuffer;
import org.daisy.pipeline.tts.AudioBufferAllocator;
import org.daisy.pipeline.tts.AudioBufferAllocator.MemoryException;
import org.daisy.pipeline.tts.TTSEngine;
import org.daisy.pipeline.tts.TTSRegistry.TTSResource;
import org.daisy.pipeline.tts.TTSService.Mark;
import org.daisy.pipeline.tts.TTSService.SynthesisException;
import org.daisy.pipeline.tts.Voice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SAPIengine extends TTSEngine {

	private Logger Logger = LoggerFactory.getLogger(SAPIengine.class);

	private AudioFormat mAudioFormat;
	private int mOverallPriority;

	private static class ThreadResource extends TTSResource {
		long connection;
	}

	public SAPIengine(SAPIservice service, AudioFormat audioFormat, int priority) {
		super(service);
		mAudioFormat = audioFormat;
		mOverallPriority = priority;
	}

	@Override
	public Collection<AudioBuffer> synthesize(String ssml, XdmNode xmlSSML, Voice voice,
	        TTSResource resource, List<Mark> marks, AudioBufferAllocator bufferAllocator,
	        boolean retry) throws SynthesisException, InterruptedException, MemoryException {

		return speak(ssml, voice, resource, marks, bufferAllocator);
	}

	public Collection<AudioBuffer> speak(String ssml, Voice voice, TTSResource resource,
	        List<Mark> marks, AudioBufferAllocator bufferAllocator) throws SynthesisException,
	        MemoryException {

		ThreadResource tr = (ThreadResource) resource;
		int res = SAPILib.speak(tr.connection, voice.vendor, voice.name, ssml);
		if (res != 0) {
			throw new SynthesisException("SAPI speak error number " + res + " with voice "
			        + voice);
		}

		int size = SAPILib.getStreamSize(tr.connection);

		AudioBuffer result = bufferAllocator.allocateBuffer(size);
		SAPILib.readStream(tr.connection, result.data, 0);

		String[] names = SAPILib.getBookmarkNames(tr.connection);
		long[] pos = SAPILib.getBookmarkPositions(tr.connection);

		float sampleRate = mAudioFormat.getSampleRate();
		int bytesPerSample = mAudioFormat.getSampleSizeInBits() / 8;
		for (int i = 0; i < names.length; ++i) {
			int offset = (int) ((pos[i] * sampleRate * bytesPerSample) / 1000);
			marks.add(new Mark(names[i], offset));
		}

		return Arrays.asList(result);
	}

	@Override
	public TTSResource allocateThreadResources() throws SynthesisException {
		long connection = SAPILib.openConnection();

		if (connection == 0) {
			throw new SynthesisException("could not open SAPI context.");
		}

		ThreadResource tr = new ThreadResource();
		tr.connection = connection;
		return tr;
	}

	@Override
	public Collection<Voice> getAvailableVoices() throws SynthesisException,
	        InterruptedException {
		String[] names = SAPILib.getVoiceNames();
		String[] vendors = SAPILib.getVoiceVendors();
		List<Voice> voices = new ArrayList<Voice>();
		for (int i = 0; i < names.length; ++i) {
			voices.add(new Voice(vendors[i], names[i]));
		}
		return voices;
	}

	@Override
	public AudioFormat getAudioOutputFormat() {
		return mAudioFormat;
	}

	@Override
	public int getOverallPriority() {
		return mOverallPriority;
	}

}
