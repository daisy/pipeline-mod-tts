package org.daisy.pipeline.tts.synthesize;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.sound.sampled.AudioFormat;

/**
 * List of sentences adjacent to each other in the original document. Positions
 * in the document are kept so as to adequately name the sound files.
 */
class ContiguousText implements Comparable<ContiguousText> {

	ContiguousText(int documentPosition, File audioOutputDir, AudioFormat audioFormat) {
		mDocumentPosition = documentPosition;
		mAudioOutputDir = audioOutputDir;
		mAudioFormat = audioFormat;
		mDocumentSplitPosition = 0;
		mAudioFormat = audioFormat;
		sentences = new ArrayList<Sentence>();
	}

	void computeSize() {
		mSize = 0;
		for (Sentence speakable : sentences) {
			mSize += speakable.getSize();
		}
	}

	@Override
	public int compareTo(ContiguousText other) {
		return (other.mSize - mSize);
	}

	public void setDocumentSplitPosition(int pos) {
		mDocumentSplitPosition = pos;
	}

	public int getDocumentSplitPosition() {
		return mDocumentSplitPosition;
	}

	public int getDocumentPosition() {
		return mDocumentPosition;
	}

	public int getStringSize() {
		return mSize;
	}

	public void setStringsize(int size) {
		mSize = size;
	}

	public AudioFormat getAudioFormat() {
		return mAudioFormat;
	}

	public File getAudioOutputDir() {
		return mAudioOutputDir;
	}

	private File mAudioOutputDir;
	private AudioFormat mAudioFormat;
	private int mDocumentSplitPosition;
	private int mDocumentPosition;
	private int mSize; //used for sorting
	List<Sentence> sentences;
}
