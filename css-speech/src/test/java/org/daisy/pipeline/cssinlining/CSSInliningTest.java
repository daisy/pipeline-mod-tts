package org.daisy.pipeline.cssinlining;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.regex.Pattern;

import javax.xml.transform.sax.SAXSource;

import net.sf.saxon.s9api.DocumentBuilder;
import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.Serializer;
import net.sf.saxon.s9api.Serializer.Property;
import net.sf.saxon.s9api.XdmNode;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.xml.sax.InputSource;

import com.xmlcalabash.util.TreeWriter;

public class CSSInliningTest implements TreeWriterFactory {

	static private Processor Proc;
	static private DocumentBuilder Builder;
	static private Serializer Serializer;
	static private CSSInliner CSSInliner;
	static private SpeechSheetAnalyser SheetAnalyzer;

	@BeforeClass
	static public void setUp() throws URISyntaxException {
		Proc = new Processor(false);
		Builder = Proc.newDocumentBuilder();
		Serializer = Proc.newSerializer();
		Serializer.setOutputProperty(Property.OMIT_XML_DECLARATION, "yes");
		Serializer.setOutputProperty(Property.INDENT, "no");
		CSSInliner = new CSSInliner();
		SheetAnalyzer = new SpeechSheetAnalyser();
	}

	@Override
	public TreeWriter newInstance() {
		return new TreeWriter(Proc);
	}

	private void check(String input, String cssFile, String... expectedParts)
	        throws SaxonApiException, URISyntaxException {

		//build the expected output regex
		StringBuilder expected = new StringBuilder();
		boolean opened = false;
		for (String p : expectedParts) {
			if (p.startsWith("</")) {
				if (opened) {
					expected.append(".*>");
					opened = false;
				}
				expected.append(p);
			} else if (p.startsWith("<")) {
				if (opened) {
					expected.append(".*>");
					opened = false;
				}
				expected.append(p);
				opened = true;
			} else if (p.contains("=")) {
				expected.append(".+" + p);
			} else {
				if (opened) {
					expected.append(".*>");
					opened = false;
				}
				expected.append(p);
			}
		}

		SAXSource source = new SAXSource(new InputSource(new StringReader(input)));
		XdmNode document = Builder.build(source);

		if (cssFile == null) {
			cssFile = "test.css";
		}

		//TODO: in Java7:
		//Paths.get(System.getProperty("user.dir"),"foo", "bar.css"); instead of:
		cssFile = new File(new File(System.getProperty("user.dir"), "src/test/resources/"),
		        cssFile).toURI().toString();

		SheetAnalyzer.analyse(Arrays.asList(cssFile));
		XdmNode tree = CSSInliner.inline(this, new URI("http://doc"), document, SheetAnalyzer,
		        "tmp");

		OutputStream result = new ByteArrayOutputStream();
		Serializer.setOutputStream(result);
		Serializer.serializeNode(tree);

		boolean match = Pattern.matches(expected.toString(), result.toString());

		Assert.assertTrue(match);
	}

	@Test
	public void pauseBefore() throws SaxonApiException, URISyntaxException {
		check("<root><simple>test</simple></root>", null, "<root", "<simple",
		        "tmp:pause-before=\"[0-9.]+\"", "test", "</simple>", "</root>");
	}

	@Test
	public void pauseAfter() throws SaxonApiException, URISyntaxException {
		check("<root><simple>test</simple></root>", null, "<root", "<simple",
		        "tmp:pause-after=\"[0-9.]+\"", "test", "</simple>", "</root>");
	}

	@Test
	public void cueBefore() throws SaxonApiException, URISyntaxException {
		check("<root><simple>test</simple></root>", null, "<root", "<simple",
		        "tmp:cue-before=\"[-_a-z0-9]+\\.mp3\"", "test", "</simple>", "</root>");
	}

	@Test
	public void cueAfter() throws SaxonApiException, URISyntaxException {
		check("<root><simple>test</simple></root>", null, "<root", "<simple",
		        "tmp:cue-after=\"[-_a-z0-9]+\\.mp3\"", "test", "</simple>", "</root>");
	}

	@Test
	public void volume() throws SaxonApiException, URISyntaxException {
		check("<root><simple>test</simple></root>", null, "<root", "<simple",
		        "tmp:volume=\"[a-z.0-9]+\"", "test", "</simple>", "</root>");
	}

	@Test
	public void speechRate() throws SaxonApiException, URISyntaxException {
		check("<root><simple>test</simple></root>", null, "<root", "<simple",
		        "tmp:speech-rate=\"[a-z.0-9]+\"", "test", "</simple>", "</root>");
	}

	@Test
	public void hyphen1() throws SaxonApiException, URISyntaxException {
		check("<root><hyphens>test</hyphens></root>", null, "<root", "<hyphens",
		        "tmp:speech-rate=\"x-slow\"", "test", "</hyphens>", "</root>");
	}

	@Test
	public void hyphen2() throws SaxonApiException, URISyntaxException {
		check("<root><hyphens>test</hyphens></root>", null, "<root", "<hyphens",
		        "tmp:cue-before=\".*a_b-c.*\"", "test", "</hyphens>", "</root>");
	}

	@Test
	public void voiceFamily() throws SaxonApiException, URISyntaxException {
		check("<root><simple>test</simple></root>", null, "<root", "<simple",
		        "tmp:voice-family=\"[a-z0-9]+,[a-z0-9]+\"", "test", "</simple>", "</root>");
	}

	@Test
	public void prefixedAttr() throws SaxonApiException, URISyntaxException {
		check("<root xmlns:epub=\"http://epub\"><n epub:type=\"prefixed\">test</n></root>",
		        null, "<root", "<n", "=\"prefixed\\.mp3", "test", "</n>", "</root>");
	}

	@Test
	public void keepContent() throws SaxonApiException, URISyntaxException {
		check("<root x=\"1\"><n y=\"1\">content1<n z=\"1\">content2</n></n>content3</root>",
		        null, "<root", "x=\"1\"", "<n", "y=\"1\"", "content1", "<n", "z=\"1\"",
		        "content2", "</n>", "</n>", "content3", "</root>");
	}

	@Test
	public void selectors1() throws SaxonApiException, URISyntaxException {
		check("<root><div><b><p>test</p></b></div><div><a>test</a></div></root>", null,
		        "<root", "<div", "<b", "<p", "tmp:speech-rate=\"10[.]?[0]*\"", "test", "</p>",
		        "</b>", "</div>", "<div", "<a", "tmp:speech-rate=\"20[.]?[0]*\"", "test",
		        "</a>", "</div>", "</root>");
	}

	@Test
	public void selectors2() throws SaxonApiException, URISyntaxException {
		check("<root><div><x>test</x></div></root>", null, "<root", "<div", "<x",
		        "tmp:speech-rate=\"30[.]?[0]*\"", "test", "</x>", "</div>", "</root>");
	}

	@Test
	public void selectors3() throws SaxonApiException, URISyntaxException {
		check("<root><div>test1</div><y>test2</y></root>", null, "<root", "<div", "test1",
		        "</div>", "<y", "tmp:speech-rate=\"40[.]?[0]*\"", "test2", "</y>", "</root>");
	}

	@Test
	public void mixedMedia() throws SaxonApiException, URISyntaxException {
		check("<root><simple>test</simple></root>", "mixed.css", "<root", "<simple",
		        "tmp:volume=\"[a-z]+\"", "test", "</simple>", "</root>");
	}
}
