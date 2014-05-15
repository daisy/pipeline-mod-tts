package org.daisy.pipeline.cssinlining;

import java.util.Arrays;
import java.util.List;

import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.SaxonApiException;
import net.sf.saxon.s9api.XdmNode;

import com.xmlcalabash.core.XProcRuntime;
import com.xmlcalabash.io.ReadablePipe;
import com.xmlcalabash.io.WritablePipe;
import com.xmlcalabash.library.DefaultStep;
import com.xmlcalabash.model.RuntimeValue;
import com.xmlcalabash.runtime.XAtomicStep;
import com.xmlcalabash.util.TreeWriter;

public class InlineCSSStep extends DefaultStep implements TreeWriterFactory {

	private String mStyleNsOption;
	private List<String> mStylesheetURIOption;

	private ReadablePipe mSource = null;
	private WritablePipe mResult = null;
	private XProcRuntime mRuntime;

	public InlineCSSStep(XProcRuntime runtime, XAtomicStep step) {
		super(runtime, step);
		mRuntime = runtime;
	}

	public void setInput(String port, ReadablePipe pipe) {
		mSource = pipe;
	}

	@Override
	public void setOption(QName name, RuntimeValue value) {
		super.setOption(name, value);
		if ("style-ns".equalsIgnoreCase(name.getLocalName())) {
			mStyleNsOption = value.getString();
		} else if ("stylesheet-uri".equalsIgnoreCase(name.getLocalName())) {
			//caution: sometimes the option starts with ','.
			//The blank URI will be replaced in analyzer.analyze
			mStylesheetURIOption = Arrays.asList(value.getString().split(","));
		} else {
			mRuntime.error(new Throwable("unknown option " + name.getLocalName()));
			return;
		}
	}

	public void setOutput(String port, WritablePipe pipe) {
		mResult = pipe;
	}

	public void reset() {
		mSource.resetReader();
		mResult.resetWriter();
	}

	public void run() throws SaxonApiException {
		super.run();

		CSSInliner inliner = new CSSInliner();
		SpeechSheetAnalyser analyzer = new SpeechSheetAnalyser();
		try {
			analyzer.analyse(mStylesheetURIOption);
		} catch (Throwable t) {
			mRuntime.info(null, null, t.toString());
			//copy the input
			while (mSource.moreDocuments()) {
				mResult.write(mSource.read());
			}
			return;
		}

		while (mSource.moreDocuments()) {
			XdmNode source = mSource.read();

			// rebuild the document with the additional style info
			XdmNode rebuilt = inliner.inline(this, source.getDocumentURI(), source, analyzer,
			        mStyleNsOption);

			mResult.write(rebuilt);
		}
	}

	@Override
	public TreeWriter newInstance() {
		return new TreeWriter(mRuntime);
	}
}
