package org.daisy.pipeline.tts;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import net.sf.saxon.s9api.Processor;
import net.sf.saxon.s9api.QName;

import org.junit.Assert;
import org.junit.Test;

import com.xmlcalabash.util.TreeWriter;

public class MarkFreeTest {

	static Processor Proc = new Processor(false);
	static SSMLAdapter Adapter = new BasicSSMLAdapter();

	private static TreeWriter newTreeWriter() throws URISyntaxException {
		TreeWriter tw = new TreeWriter(Proc);
		tw.startDocument(new URI("http://test"));
		tw.startContent();
		tw.addStartElement(new QName(null, "root"));
		return tw;
	}

	@Test
	public void zeroMark() throws URISyntaxException {
		TreeWriter tw = newTreeWriter();
		tw.addStartElement(new QName(null, "a"));
		tw.addText("text1");
		tw.addEndElement();
		tw.addStartElement(new QName(null, "b"));
		tw.addText("text2");
		tw.addEndElement();
		tw.addEndElement();

		List<String> names = new ArrayList<String>();
		String[] res = SSMLUtil.toStringNoMarks(tw.getResult(), null, Adapter, names);

		Assert.assertEquals(1, res.length);
		Assert.assertEquals(1, names.size());
		Assert.assertTrue(res[0].contains("<a>text1</a><b>text2</b>"));
	}

	@Test
	public void oneMark() throws URISyntaxException {
		String markName = "TheMark";

		TreeWriter tw = newTreeWriter();
		tw.addStartElement(new QName(null, "a"));
		tw.addStartElement(new QName(null, "a1"));
		tw.addText("text1");
		tw.addEndElement();

		tw.addStartElement(new QName(null, "mark"));
		tw.addAttribute(new QName(null, "name"), markName);
		tw.addEndElement();

		tw.addStartElement(new QName(null, "a2"));
		tw.addText("text2");
		tw.addEndElement();
		tw.addEndElement(); //a
		tw.addStartElement(new QName(null, "b"));
		tw.addText("text3");
		tw.addEndElement();
		tw.addEndElement();

		List<String> names = new ArrayList<String>();
		String[] res = SSMLUtil.toStringNoMarks(tw.getResult(), null, Adapter, names);

		Assert.assertEquals(2, res.length);
		Assert.assertEquals(2, names.size());
		Assert.assertEquals(names.get(1), markName);
		Assert.assertTrue(res[0].contains("<a><a1>text1</a1></a>"));
		Assert.assertTrue(res[1].contains("<a><a2>text2</a2></a><b>text3</b>"));
	}
}
