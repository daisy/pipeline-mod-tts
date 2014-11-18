package org.daisy.pipeline.tts.config;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XdmSequenceIterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigReader {

	private Logger Logger = LoggerFactory.getLogger(ConfigReader.class);

	public interface Extension {
		/**
		 * @node has a non-null local name
		 * @return false is the parsing can keep going with other extensions
		 *         (most likely if @node is not related to the extension)
		 */
		public boolean parseNode(XdmNode node, URI documentURI);

		public void setParentReader(ConfigReader cr);
	}

	public ConfigReader(XdmNode doc, Extension... extensions) {

		for (Extension ext : extensions) {
			ext.setParentReader(this);
		}

		mDocURI = doc.getDocumentURI();
		XdmSequenceIterator it = doc.axisIterator(Axis.CHILD);
		XdmNode root = doc;
		while (doc.getNodeKind() != XdmNodeKind.ELEMENT && it.hasNext())
			root = (XdmNode) it.next();

		it = root.axisIterator(Axis.CHILD);
		while (it.hasNext()) {
			XdmNode node = (XdmNode) it.next();
			QName qname = node.getNodeName();
			if (qname != null) {
				if ("property".equalsIgnoreCase(qname.getLocalName())) {
					String key = node.getAttributeValue(new QName(null, "key"));
					String value = node.getAttributeValue(new QName(null, "value"));
					if (key == null || value == null) {
						Logger.warn("Missing key or value for config's property "
						        + node.toString());
					} else {
						mProps.put(key, value);
					}
				} else {
					boolean parsed = false;
					for (int k = 0; !parsed && k < extensions.length; ++k) {
						parsed = extensions[k].parseNode(node, mDocURI);
					}
				}
			}
		}
	}

	public URI getConfigDocURI() {
		return mDocURI;
	}

	public Map<String, String> getProperties() {
		return mProps;
	}

	public String getProperty(String key) {
		return mProps.get(key);
	}

	private URI mDocURI;
	private Map<String, String> mProps = new HashMap<String, String>();
}
