package org.daisy.pipeline.tts;

import java.util.Collection;

import net.sf.saxon.s9api.XdmNode;

public interface SSMLMarkSplitter {

	Collection<Chunk> split(XdmNode xdmNode);

	static class Chunk {
		public Chunk(XdmNode ssml) {
			this.ssml = ssml;
		}

		public Chunk(XdmNode ssml, String leftmark) {
			this.ssml = ssml;
			this.leftmark = leftmark;
		}

		public boolean mostLeftChunk() {
			return leftmark == null;
		}

		public String leftMark() {
			return leftmark;
		}

		public XdmNode ssml() {
			return ssml;
		}

		private String leftmark;
		private XdmNode ssml;
	}

}
