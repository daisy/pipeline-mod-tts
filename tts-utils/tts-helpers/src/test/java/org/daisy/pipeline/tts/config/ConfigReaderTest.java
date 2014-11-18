package org.daisy.pipeline.tts.config;

import java.io.StringReader;
import java.net.URISyntaxException;

import javax.xml.transform.sax.SAXSource;

import junit.framework.Assert;
import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;

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
	public void configDocURI() throws SaxonApiException, URISyntaxException {
		ConfigReader cr = initConfigReader("<css href=\"foo/bar/path.css\"/>");
		Assert.assertTrue(cr.getConfigDocURI() != null);
	}
}
