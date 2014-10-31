package org.daisy.pipeline.tts.synthesize;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TTSLogImpl implements TTSLog {

	public Entry getOrCreateEntry(String id) {
		Entry res = mLog.get(id);
		if (res != null)
			return res;
		res = new Entry();
		mLog.put(id, res);
		return res;
	}

	public Entry getWritableEntry(String id) {
		return mLog.get(id);
	}

	public Set<Map.Entry<String, Entry>> getEntries() {
		return mLog.entrySet();
	}

	public void addGeneralError(ErrorCode errcode, String message) {
		synchronized (generalErrors) {
			generalErrors.add(new Error(errcode, message));
		}
	}

	public Collection<Error> readonlyGeneralErrors() {
		return generalErrors;
	}

	private List<Error> generalErrors = new ArrayList<Error>();
	private Map<String, Entry> mLog = new HashMap<String, Entry>();
}
