package org.daisy.pipeline.cssinlining;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;

import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmNode;

import org.daisy.pipeline.tts.config.ConfigReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CSSConfigExtension implements ConfigReader.Extension {

	private Logger Logger = LoggerFactory.getLogger(CSSConfigExtension.class);

	@Override
	public boolean parseNode(XdmNode node, URI documentURI) {
		String name = node.getNodeName().getLocalName();
		if ("css".equalsIgnoreCase(name)) {
			String href = node.getAttributeValue(new QName(null, "href"));
			if (href != null) {
				URI uri = null;
				try {
					uri = new URI(href);
				} catch (URISyntaxException e) {
					try {
						uri = new URI("file://" + href);
					} catch (URISyntaxException e1) {
						Logger.error("Invalid URI in config file: " + href);
						return true;
					}
				}
				if (!uri.isAbsolute())
					uri = documentURI.resolve(uri);
				mCSSuris.add(uri);
			} else {
				String content = node.getStringValue();
				if (content != null && !content.isEmpty()) {
					mEmbeddedCSS.add(content);
				}
			}
			return true;
		}
		return false;
	}

	public Collection<URI> getCSSstylesheetURIs() {
		return mCSSuris;
	}

	public Collection<String> getEmbeddedCSS() {
		return mEmbeddedCSS;
	}

	private Collection<URI> mCSSuris = new ArrayList<URI>();
	private Collection<String> mEmbeddedCSS = new ArrayList<String>();

	@Override
	public void setParentReader(ConfigReader cr) {
	}
}
