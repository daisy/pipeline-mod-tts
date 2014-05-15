package org.daisy.pipeline.tts.acapela;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Set;
import java.util.regex.Pattern;

import javax.sound.sampled.AudioFormat;

import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmNode;

import org.daisy.pipeline.tts.AbstractTTSService;
import org.daisy.pipeline.tts.BasicSSMLAdapter;
import org.daisy.pipeline.tts.LoadBalancer.Host;
import org.daisy.pipeline.tts.RoundRobinLoadBalancer;
import org.daisy.pipeline.tts.SSMLAdapter;
import org.daisy.pipeline.tts.SSMLUtil;
import org.daisy.pipeline.tts.SoundUtil;
import org.daisy.pipeline.tts.TTSRegistry.TTSResource;
import org.daisy.pipeline.tts.Voice;
import org.daisy.pipeline.tts.acapela.NscubeLibrary.PNSC_FNSPEECH_DATA;
import org.daisy.pipeline.tts.acapela.NscubeLibrary.PNSC_FNSPEECH_EVENT;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.NativeLongByReference;
import com.sun.jna.ptr.PointerByReference;

/**
 * AcapelaTTS requires libnscube.so to be available in the library path.
 * 
 * Warning: The SSML tags interpreter works only for some languages: • French
 * (fr) • British English (uk) • American English (us) • Spanish (es) • Italian
 * (it) • Brazilian (br) • German (de). For other languages, we can adapt the
 * SSML to the tag language (/mark{...}, /voice{...}) etc.
 */
public class AcapelaTTS extends AbstractTTSService {

	private AudioFormat mAudioFormat;
	private RoundRobinLoadBalancer mLoadBalancer;

	public static class ThreadResources extends TTSResource {
		Pointer dispatcher;
		NativeLong channelId;
		PointerByReference channelLock;
		Pointer server;
		RawAudioBuffer buff;
		List<Entry<String, Integer>> marks;
		NSC_EXEC_DATA execData;
		List<String> idsToMark; //we don't use directly the marks' names because they may contain non-alphanumeric characters
		SSMLAdapter ssmlAdapter; //we use a different adapter for every thread because IdstoMark must not be shared.

		public ThreadResources() {
			idsToMark = new ArrayList<String>();

			ssmlAdapter = new BasicSSMLAdapter() {
				@Override
				public String adaptText(String text) {
					return super.adaptText(SpaceRegex.matcher(text).replaceAll(" "));
				}

				@Override
				public String adaptAttributeValue(QName element, QName attr, String value) {
					if ("mark".equals(element.getLocalName())
					        && "name".equals(attr.getLocalName())) {
						int id = idsToMark.size();
						idsToMark.add(value);
						return String.valueOf(id);
					}
					return super.adaptAttributeValue(element, attr, value);
				}

				@Override
				public String getHeader(String voiceName) {
					if (voiceName != null && !voiceName.isEmpty()) {
						return "\\voice{" + voiceName + "}" + super.getHeader(voiceName);
					}
					return super.getHeader(voiceName);
				}

			};

			execData = new NSC_EXEC_DATA();
			execData.ulEventFilter = new NativeLong(NscubeLibrary.NSC_EVTBIT_TEXT
			        | NscubeLibrary.NSC_EVTBIT_BOOKMARK);
			execData.bEventSynchroReq = 1;
			execData.vsSoundData.uiSize = 0;
			execData.vsSoundData.pSoundBuffer = null;

			execData.pfnSpeechData = new PNSC_FNSPEECH_DATA() {
				@Override
				public int apply(Pointer pData, int cbDataSize, NSC_SOUND_DATA pSoundData,
				        Pointer pAppInstanceData) {
					if (cbDataSize + buff.offsetInOutput > buff.output.length)
						SoundUtil.realloc(buff, cbDataSize);

					System.arraycopy(pData.getByteArray(0, cbDataSize), 0, buff.output,
					        buff.offsetInOutput, cbDataSize);
					buff.offsetInOutput += cbDataSize;

					return cbDataSize;
				}
			};
			execData.pfnSpeechEvent = new PNSC_FNSPEECH_EVENT() {
				@Override
				public int apply(int nEventID, int cbEventDataSize, NSC_EVENT_DATA pEventData,
				        Pointer pAppInstanceData) {

					if (nEventID == NscubeLibrary.NSC_EVID_ENUM.NSC_EVID_BOOKMARK) {
						NSC_EVENT_DATA_Bookmark bookmark = new NSC_EVENT_DATA_Bookmark(
						        pEventData.getPointer());
						bookmark.read();

						String bookmarkName = idsToMark.get(bookmark.uiVal);
						marks.add(new AbstractMap.SimpleEntry<String, Integer>(bookmarkName,
						        bookmark.uiByteCount));
					} else if (nEventID == NscubeLibrary.NSC_EVID_ENUM.NSC_EVID_BOOKMARK_EXT) {
						// This should not happen because the marks are numeric.
						NSC_EVENT_DATA_BookmarkExt bookmark = new NSC_EVENT_DATA_BookmarkExt(
						        pEventData.getPointer());
						bookmark.read();

						String bookmarkName = idsToMark.get(Integer.valueOf(new String(
						        bookmark.szVal).trim()));
						marks.add(new AbstractMap.SimpleEntry<String, Integer>(bookmarkName,
						        bookmark.uiByteCount));
					}

					return 0;
				}
			};
		};
	}

	@Override
	public void onBeforeOneExecution() throws SynthesisException {
		mLoadBalancer = new RoundRobinLoadBalancer(System.getProperty("acapela.servers",
		        "localhost:0"), this); //'0' means that the port is left to Nscube's choice

		int sampleRate = Integer.valueOf(System.getProperty("acapela.samplerate", "22050"));
		mAudioFormat = new AudioFormat((float) sampleRate, 16, 1, true, false);

		//check that all the servers are working
		Throwable lastError = null;
		int workingHosts = 0;
		List<Host> nonWorking = new ArrayList<Host>();
		for (Host h : mLoadBalancer.getAllHosts()) {
			TTSResource resources = null;
			RawAudioBuffer audioBuffer = new RawAudioBuffer(1);
			boolean error = false;
			try {
				resources = allocateThreadResources();
				synthesize("hello world", audioBuffer, (ThreadResources) resources, null);
			} catch (Throwable t) {
				lastError = t;
			} finally {
				if (resources != null)
					releaseThreadResources(resources);
			}
			if (error || audioBuffer.offsetInOutput > 2500) {
				++workingHosts;
			} else {
				nonWorking.add(h);
			}
		}
		if (workingHosts == 0) {
			throw new SynthesisException("None of the ATT servers is working.", lastError);
		}
		mLoadBalancer.discardAll(nonWorking);
	}

	@Override
	public TTSResource allocateThreadResources() throws SynthesisException {
		return allocateThreadResources(mLoadBalancer.selectHost());
	}

	private ThreadResources allocateThreadResources(Host h) throws SynthesisException {
		NscubeLibrary lib = NscubeLibrary.INSTANCE;
		ThreadResources th = new ThreadResources();

		// Acapela's doc says:
		//  "It is possible to create multiple different ServerContext to handle communication with a
		//  single server or multiple servers at the same time."
		// And:
		//   "For a single server context object (created by nscCreateServerContext) the
		//   whole search sequence nscFindFirstVoice, nscFindNextVoice must not be called
		//   from different threads at the same time."

		th.server = createServerContext(h);

		// Acapela's doc says:
		//  "In synchronous mode (channels launched with the nscExecChannel() function), one
		//  dispatcher must be created for each thread because the nscExecChannel() function
		//  internally makes a call to the nscGetandProcess() function."
		// And:
		//  "In any case the application must not use a single dispatcher object (created by
		//   nscCreateDispatcher) from different threads, neither in direct calls (nscProcessEvent,
		//	nscGetandProcess functions) nor in indirect calls (nscExecChannel function)."

		PointerByReference phDispatch = new PointerByReference();
		int ret = lib.nscCreateDispatcher(phDispatch);
		if (ret != NscubeLibrary.NSC_OK) {
			releaseThreadResources(th);
			throw new SynthesisException(
			        "Could not create one Acapela's dispatcher (err code: " + ret + ")");
		}
		th.dispatcher = phDispatch.getValue();

		// Acapela's doc says:
		//   "The capacity of specifying a list of voices for pVoiceList argument is now deprecated and
		//   kept only for compatibility (this functionality will disappear in next version).
		//   To switch to an another language/voice, you can use directly switch tag ( \vox, \voice or
		//   \vce) without the need to load a list of voice."
		// But it seems that a channel must be initialized with a least one valid voice, 
		// i.e. empty string and null string are not accepted.

		PointerByReference voiceEnumerator = new PointerByReference();
		NSC_FINDVOICE_DATA voiceData = new NSC_FINDVOICE_DATA();
		ret = lib.nscFindFirstVoice(th.server, (String) null, (int) mAudioFormat
		        .getSampleRate(), 0, 0, voiceData, voiceEnumerator);
		if (ret != NscubeLibrary.NSC_OK) {
			releaseThreadResources(th);
			throw new SynthesisException(
			        "Could not find any voice to init one Acapela's channel (err code: " + ret
			                + ")");
		}
		lib.nscCloseFindVoice(voiceEnumerator.getValue());

		//Acapela's doc: " If too many threads attempts to run nscInitChannel at the same time, there may be a
		// limitation from the TCP/IP driver. In that case, the function returns the code
		// NSC_ERR_CONNECT. It is up to the application to retry until the function succeeds."

		NativeLongByReference pChId = new NativeLongByReference();
		ret = lib.nscInitChannel(th.server, new String(voiceData.cVoiceName),
		        (int) mAudioFormat.getSampleRate(), 0, th.dispatcher, pChId);
		for (int tries = 0; tries < 4 && ret == NscubeLibrary.NSC_ERR_CONNECT; ++tries) {
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
			}
			ret = lib.nscInitChannel(th.server, new String(voiceData.cVoiceName), 0, 0,
			        th.dispatcher, pChId);
		}
		if (ret != NscubeLibrary.NSC_OK) {
			throw new SynthesisException("Could not init one Acapela's channel (err code: "
			        + ret + ")");
		}

		th.channelId = pChId.getValue();

		return th;
	}

	private Pointer createServerContext(Host h) throws SynthesisException {
		PointerByReference phandler = new PointerByReference();

		int cmdPort = h.port;
		int dataPort = cmdPort == 0 ? 0 : (cmdPort + 1);

		int ret = NscubeLibrary.INSTANCE.nscCreateServerContextEx(
		        NscubeLibrary.NSC_AFTYPE_ENUM.NSC_AF_INET, cmdPort, dataPort, h.address,
		        phandler);
		if (ret != NscubeLibrary.NSC_OK) {
			throw new SynthesisException("could not connect to the Acapela Server (err code: "
			        + ret + ")");
		}
		return phandler.getValue();
	}

	@Override
	public void releaseThreadResources(Object resources) throws SynthesisException {
		ThreadResources th = (ThreadResources) resources;
		if (th.channelId != null && th.server != null)
			NscubeLibrary.INSTANCE.nscCloseChannel(th.server, th.channelId);
		if (th.dispatcher != null)
			NscubeLibrary.INSTANCE.nscDeleteDispatcher(th.dispatcher);
		if (th.server != null) {
			NscubeLibrary.INSTANCE.nscReleaseServerContext(th.server);
		}
	}

	@Override
	public void synthesize(XdmNode ssml, Voice voice, RawAudioBuffer audioBuffer,
	        Object threadResources, List<Entry<String, Integer>> marks, boolean retry)
	        throws SynthesisException {

		ThreadResources th = (ThreadResources) threadResources;
		if (retry) {
			releaseThreadResources(th);
			ThreadResources newth = (ThreadResources) allocateThreadResources();
			th.server = newth.server;
			th.dispatcher = newth.dispatcher;
			th.channelId = newth.channelId;
		}

		th.idsToMark.clear();

		//note: the Acapela's markup for SSML interpretation is active by default.
		synthesize(SSMLUtil.toString(ssml, voice.name, th.ssmlAdapter, endingMark()),
		        audioBuffer, th, marks);
	}

	public void synthesize(String ssml, RawAudioBuffer audioBuffer, ThreadResources th,
	        List<Entry<String, Integer>> marks) throws SynthesisException {
		th.buff = audioBuffer;
		th.marks = marks;
		th.channelLock = new PointerByReference();
		NscubeLibrary lib = NscubeLibrary.INSTANCE;

		int ret = lib.nscLockChannel(th.server, th.channelId, th.dispatcher, th.channelLock);
		if (ret != NscubeLibrary.NSC_OK)
			return;

		ret = lib.nscAddTextUTF8(th.channelLock.getValue(), ssml, null);
		if (ret != NscubeLibrary.NSC_OK) {
			lib.nscUnlockChannel(th.channelLock.getValue());
			return;
		}

		ret = lib.nscExecChannel(th.channelLock.getValue(), th.execData);
		lib.nscUnlockChannel(th.channelLock.getValue());
		if (ret != NscubeLibrary.NSC_OK) {
			throw new SynthesisException("nscExecChannel returned error code:" + ret);
		}
	}

	@Override
	public void interruptCurrentWork(TTSResource resource) {
		ThreadResources th = (ThreadResources) resource;
		NscubeLibrary.INSTANCE.nscExitChannel(th.channelLock.getValue());
	}

	@Override
	public void onAfterOneExecution() {
		mLoadBalancer = null;
		mAudioFormat = null;
	}

	@Override
	public Collection<Voice> getAvailableVoices() throws SynthesisException {
		NscubeLibrary lib = NscubeLibrary.INSTANCE;
		Pointer server = createServerContext(mLoadBalancer.getMaster());

		Set<Voice> result = new HashSet<Voice>();

		//Only the voices with the pre-selected sample rate are kept to prevent the server
		//from producing data using another sample rate without any notification.
		//If there are two versions with the same speaker (for example 8kHz and 22Khz) then
		//the server should choose the voice whose sample rate matches the one provided to
		//the channel initialization. Such situation has not been tested though.
		PointerByReference voiceEnumerator = new PointerByReference();
		NSC_FINDVOICE_DATA voiceData = new NSC_FINDVOICE_DATA();
		int ret = lib.nscFindFirstVoice(server, (String) null, (int) mAudioFormat
		        .getSampleRate(), 0, 0, voiceData, voiceEnumerator);
		while (ret == NscubeLibrary.NSC_OK) {
			if (voiceData.nInitialCoding == NscubeLibrary.NSC_VOICE_ENCODING_PCM) {
				int end = 0;
				for (; end < voiceData.cSpeakerName.length && voiceData.cSpeakerName[end] != 0; ++end);
				//all the names are supposed to be ascii encoded
				result.add(new Voice(getName(), new String(voiceData.cSpeakerName, 0, end)));
			}
			ret = lib.nscFindNextVoice(voiceEnumerator.getValue(), voiceData);
		}

		lib.nscCloseFindVoice(voiceEnumerator.getValue());
		lib.nscReleaseServerContext(server);

		return result;
	}

	@Override
	public int getOverallPriority() {
		return Integer.valueOf(System.getProperty("att.priority", "10"));
	}

	@Override
	public String endingMark() {
		return "ending-mark";
	}

	@Override
	public AudioFormat getAudioOutputFormat() {
		return mAudioFormat;
	}

	@Override
	public String getName() {
		return "acapela";
	}

	@Override
	public String getVersion() {
		return "jna";
	}

	@Override
	public boolean resourcesReleasedASAP() {
		return false;
	}

	//// TODO: use the new unicode class in Java7 or move this somewhere in framework/common

	private static Pattern SpaceRegex = null;
	static {
		char[] SpaceChars = {
		        0x0020, 0x0085, 0x00A0, 0x1680, 0x180E, 0x2028, 0x2029, 0x202F, 0x205F, 0x3000
		};

		String spaces = "";
		for (char spaceChar : SpaceChars) {
			spaces += new Character(spaceChar);
		}
		spaces += new Character((char) 0x0009) + "-" + new Character((char) 0x000D);
		spaces += new Character((char) 0x2000) + "-" + new Character((char) 0x200A);
		SpaceRegex = Pattern.compile("[" + spaces + "]+", Pattern.DOTALL
		        | Pattern.UNICODE_CASE | Pattern.MULTILINE);
	}
}
