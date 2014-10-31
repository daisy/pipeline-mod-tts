package org.daisy.pipeline.tts.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XdmSequenceIterator;

import org.daisy.pipeline.tts.VoiceInfo;
import org.daisy.pipeline.tts.VoiceInfo.Gender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConfigReader {

	private Logger Logger = LoggerFactory.getLogger(ConfigReader.class);

	public ConfigReader(XdmNode doc) {
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
				String name = qname.getLocalName();
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
								continue;
							}
						}
						if (!uri.isAbsolute())
							uri = mDocURI.resolve(uri);
						mCSSuris.add(uri);
					} else {
						String content = node.getStringValue();
						if (content != null && !content.isEmpty()) {
							mEmbeddedCSS.add(content);
						}
					}
				} else if ("voice".equalsIgnoreCase(name)) {
					String lang = node.getAttributeValue(new QName(null, "lang"));
					if (lang == null)
						node.getAttributeValue(new QName(
						        "http://www.w3.org/XML/1998/namespace", "lang"));
					String vengine = node.getAttributeValue(new QName(null, "engine"));
					String vname = node.getAttributeValue(new QName(null, "name"));
					String priority = node.getAttributeValue(new QName(null, "priority"));
					Gender gender = Gender.of(node
					        .getAttributeValue(new QName(null, "gender")));
					if (priority == null)
						priority = "5";
					if (lang == null || vengine == null || vname == null || gender == null) {
						Logger.warn("Config file invalid near " + node.toString());
					} else {
						try {
							mVoices.add(new VoiceInfo(vengine, vname, lang, gender, Float
							        .valueOf(priority)));
						} catch (NumberFormatException e) {
							Logger.warn("Error while converting config file's priority "
							        + priority + " to float.");
						}
					}
				} else if ("property".equalsIgnoreCase(name)) {
					String key = node.getAttributeValue(new QName(null, "key"));
					String value = node.getAttributeValue(new QName(null, "value"));
					if (key == null || value == null) {
						Logger.warn("Missing key or value for config's property "
						        + node.toString());
					} else {
						mProps.put(key, value);
					}
				}
			}
		}
	}

	public Collection<URI> getCSSstylesheetURIs() {
		return mCSSuris;
	}

	public Collection<String> getEmbeddedCSS() {
		return mEmbeddedCSS;
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

	public Collection<VoiceInfo> getVoiceDeclarations() {
		return mVoices;
	}

	private URI mDocURI;
	private Collection<URI> mCSSuris = new ArrayList<URI>();
	private Collection<String> mEmbeddedCSS = new ArrayList<String>();
	private Collection<VoiceInfo> mVoices = new ArrayList<VoiceInfo>();
	private Map<String, String> mProps = new HashMap<String, String>();
}
