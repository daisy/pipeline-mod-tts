package org.daisy.pipeline.tts.synthesize;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

public class TTSLogEmpty implements TTSLog {

	private Entry mUselessEntry = new Entry();

	@Override
	public Entry getOrCreateEntry(String id) {
		return mUselessEntry;
	}

	@Override
	public Entry getWritableEntry(String id) {
		return new Entry();
	}

	@Override
	public Set<java.util.Map.Entry<String, Entry>> getEntries() {
		return Collections.EMPTY_SET;
	}

	@Override
	public void addGeneralError(ErrorCode errcode, String message) {
	}

	@Override
	public Collection<Error> readonlyGeneralErrors() {
		return Collections.EMPTY_LIST;
	}

}
