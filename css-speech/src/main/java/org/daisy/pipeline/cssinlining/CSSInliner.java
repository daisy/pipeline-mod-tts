package org.daisy.pipeline.cssinlining;

import java.net.URI;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import net.sf.saxon.dom.DocumentOverNodeInfo;
import net.sf.saxon.dom.NodeOverNodeInfo;
import net.sf.saxon.om.NameOfNode;
import net.sf.saxon.om.NamespaceBinding;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.tree.util.NamespaceIterator;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.xmlcalabash.util.TreeWriter;

import cz.vutbr.web.css.NodeData;
import cz.vutbr.web.css.Term;
import cz.vutbr.web.domassign.StyleMap;

public class CSSInliner {

	private final static String mStyleNsPrefix = "tmp";
	private boolean mFirst;
	private TreeWriter mTreeWriter;
	private StyleMap mStyleMap;
	private String mStyleNS;

	public XdmNode inline(TreeWriterFactory twFactory, URI docURI, XdmNode root,
	        SpeechSheetAnalyser analyzer, String styleNamespace) {
		// match the document with the style sheet
		DocumentOverNodeInfo doc = (DocumentOverNodeInfo) DocumentOverNodeInfo.wrap(root
		        .getUnderlyingNode());

		Document wrapped = (Document) WrapperOverWrapper.wrap(doc);
		mStyleMap = analyzer.evaluateDOM(wrapped);

		mStyleNS = styleNamespace;
		mFirst = true;
		mTreeWriter = twFactory.newInstance();
		mTreeWriter.startDocument(docURI);
		rebuildRec(wrapped.getDocumentElement());
		mTreeWriter.endDocument();

		XdmNode result = mTreeWriter.getResult();

		//help the GC does its job
		mStyleNS = null;
		mTreeWriter = null;
		mStyleMap = null;

		return result;
	}

	private void rebuildRec(Node node) {
		if (node.getNodeType() == Node.COMMENT_NODE) {
			mTreeWriter.addComment(node.getNodeValue());
		} else if (node.getNodeType() == Node.TEXT_NODE) {
			mTreeWriter.addText(node.getNodeValue());
		} else if (node.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE) {
			mTreeWriter.addPI(node.getLocalName(), node.getNodeValue());
		} else if (node.getNodeType() == Node.ELEMENT_NODE) {

			NamespaceBinding[] bindings = null;
			if (mFirst) {
				List<NamespaceBinding> namespaces = new ArrayList<NamespaceBinding>();
				Iterators.<NamespaceBinding> addAll(namespaces, NamespaceIterator
				        .iterateNamespaces(((NodeOverNodeInfo) node).getUnderlyingNodeInfo()));
				bindings = Iterables.<NamespaceBinding> toArray(namespaces,
				        NamespaceBinding.class);
				mFirst = false;
			} else {
				bindings = ((NodeOverNodeInfo) node).getUnderlyingNodeInfo()
				        .getDeclaredNamespaces(null);
			}

			NodeInfo inode = ((NodeOverNodeInfo) node).getUnderlyingNodeInfo();
			mTreeWriter
			        .addStartElement(new NameOfNode(inode), inode.getSchemaType(), bindings);

			NamedNodeMap attributes = node.getAttributes();
			for (int i = 0; i < attributes.getLength(); i++) {
				Node attr = attributes.item(i);
				if (attr.getPrefix() != null && attr.getPrefix().length() > 0)
					mTreeWriter.addAttribute(new QName(attr.getPrefix(), attr
					        .getNamespaceURI(), attr.getLocalName()), attr.getNodeValue());
				else
					mTreeWriter.addAttribute(new QName(attr.getNamespaceURI(), attr
					        .getLocalName()), attr.getNodeValue());
			}

			// ===== start inlining ===== //
			NodeData nd = mStyleMap.get((Element) node);
			if (nd != null) {
				for (String property : nd.getPropertyNames()) {
					Object val = null;
					Term<?> t = nd.getValue(property, false);
					if (t == null || t.getValue() == null)
						val = nd.getProperty(property, false);
					else
						val = t.getValue();
					if (val != null) {
						String str = null;
						if (val instanceof List<?>) {
							List<?> li = (List<?>) val;
							StringBuilder sb = new StringBuilder();
							Iterator it = li.iterator();
							sb.append(it.next());
							while (it.hasNext()) {
								Term<?> term = (Term<?>) it.next();
								sb.append("," + term.getValue().toString());
							}
							str = sb.toString();
						} else if (property.startsWith("cue")) {
							str = val.toString();
						} else {
							//jStyleParser replaces '-' with '_'. Best workaround so far is to do the opposite:
							//(voice-family and cue aside, there is no property values with '_' in Aural CSS)
							str = val.toString().replace("_", "-").toLowerCase();
						}
						mTreeWriter.addAttribute(
						        new QName(mStyleNsPrefix, mStyleNS, property), str);
					}
				}
			}
			// ===== end inlining ===== //
			for (Node child = node.getFirstChild(); child != null; child = child
			        .getNextSibling())
				rebuildRec(child);

			mTreeWriter.addEndElement();
		}
	}
}
