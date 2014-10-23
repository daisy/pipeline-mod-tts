package org.daisy.pipeline.tts.config;

import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import javax.xml.transform.sax.SAXSource;

import junit.framework.Assert;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;

import org.daisy.pipeline.tts.VoiceInfo;
import org.junit.Test;
import org.xml.sax.InputSource;

public class ConfigReaderTest {

	static Processor Proc = new Processor(false);
	static String docDirectory = "file:///doc/";

	public static ConfigReader initConfigReader(String xmlstr) throws SaxonApiException {
		DocumentBuilder builder = Proc.newDocumentBuilder();
		SAXSource source = new SAXSource(new InputSource(new StringReader("<config>" + xmlstr
		        + "</config>")));
		source.setSystemId(docDirectory + "uri");
		XdmNode document = builder.build(source);

		return new ConfigReader(document);
	}

	@Test
	public void properties() throws SaxonApiException {
		ConfigReader cr = initConfigReader("<property key=\"key1\" value=\"val1\"/><property key=\"key2\" value=\"val2\"/>");
		Assert.assertEquals("val1", cr.getProperty("key1"));
		Assert.assertEquals("val2", cr.getProperty("key2"));
		Assert.assertEquals(2, cr.getProperties().size());
	}

	@Test
	public void voices() throws SaxonApiException {
		ConfigReader cr = initConfigReader("<voice engine=\"engine\" name=\"voice-name\" gender=\"male\" lang=\"en\" priority=\"42\"/>");

		Collection<VoiceInfo> voices = cr.getVoiceDeclarations();

		Assert.assertFalse(voices.isEmpty());

		VoiceInfo v = voices.iterator().next();
		Assert.assertEquals(new Locale("en"), v.language);
		Assert.assertEquals("voice-name", v.voice.name);
		Assert.assertEquals("engine", v.voice.engine);
		Assert.assertEquals(VoiceInfo.Gender.MALE_ADULT, v.gender);
		Assert.assertEquals(42, (int) v.priority);
	}

	@Test
	public void CSSabsoluteURI() throws SaxonApiException, URISyntaxException {
		ConfigReader cr = initConfigReader("<css href=\"file:///uri1\"/><css href=\"file:///uri2\"/>");
		Set<URI> uris = new HashSet<URI>(cr.getCSSstylesheetURIs());

		Assert.assertEquals(2, uris.size());
		Assert.assertTrue(uris.contains(new URI("file:///uri1")));
		Assert.assertTrue(uris.contains(new URI("file:///uri2")));
	}

	@Test
	public void configDocURI() throws SaxonApiException, URISyntaxException {
		ConfigReader cr = initConfigReader("<css href=\"foo/bar/path.css\"/>");
		Assert.assertTrue(cr.getConfigDocURI() != null);
	}

	@Test
	public void CSSrelativePath() throws SaxonApiException, URISyntaxException {
		ConfigReader cr = initConfigReader("<css href=\"foo/bar/path.css\"/>");

		Collection<URI> res = cr.getCSSstylesheetURIs();
		Assert.assertFalse(res.isEmpty());

		String uri = res.iterator().next().toString();
		Assert.assertEquals(new URI(docDirectory + "foo/bar/path.css"), new URI(uri));
	}

	@Test
	public void embeddedCSS() throws SaxonApiException {
		ConfigReader cr = initConfigReader("<css>css-content1</css><css>css-content2</css>");

		Set<String> contents = new HashSet<String>(cr.getEmbeddedCSS());

		Assert.assertEquals(2, contents.size());
		Assert.assertTrue(contents.contains("css-content1"));
		Assert.assertTrue(contents.contains("css-content2"));
	}
}
