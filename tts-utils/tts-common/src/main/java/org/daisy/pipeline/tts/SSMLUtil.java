package org.daisy.pipeline.tts;

import java.util.List;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.s9api.Axis;
import net.sf.saxon.s9api.QName;
import net.sf.saxon.s9api.XdmNode;
import net.sf.saxon.s9api.XdmNodeKind;
import net.sf.saxon.s9api.XdmSequenceIterator;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;

public class SSMLUtil {

	private static void toString(XdmNode ssml, StringBuilder sb, SSMLAdapter adapter,
	        String markName, Multimap<NodeInfo, String> marksScope) {
		if (marksScope != null
		        && !marksScope.containsEntry(ssml.getUnderlyingNode(), markName)) {
			return;
		}
		if (ssml.getNodeKind() == XdmNodeKind.TEXT) {
			sb.append(adapter.adaptText(ssml.getStringValue()));
		} else if (ssml.getNodeKind() == XdmNodeKind.DOCUMENT) {
			XdmSequenceIterator iter = ssml.axisIterator(Axis.CHILD);
			while (iter.hasNext()) {
				toString((XdmNode) iter.next(), sb, adapter, markName, marksScope);
			}
		} else if (ssml.getNodeKind() == XdmNodeKind.ELEMENT
		        && (marksScope == null || !"mark".equals(ssml.getNodeName().getLocalName()))) {
			QName elementName = adapter.adaptElement(ssml.getNodeName());
			if (elementName != null) {
				sb.append("<" + elementName.toString());
				XdmSequenceIterator iter = ssml.axisIterator(Axis.ATTRIBUTE);
				while (iter.hasNext()) {
					sb.append(" ");
					XdmNode node = (XdmNode) iter.next();
					QName attrName = adapter.adaptAttributeName(ssml.getNodeName(), node
					        .getNodeName(), node.getStringValue());
					if (attrName != null)
						sb.append(attrName.toString()
						        + "=\""
						        + adapter.adaptAttributeValue(ssml.getNodeName(), node
						                .getNodeName(), node.getStringValue()) + "\"");
				}
				sb.append(">");
			}

			XdmSequenceIterator iter = ssml.axisIterator(Axis.CHILD);
			while (iter.hasNext()) {
				toString((XdmNode) iter.next(), sb, adapter, markName, marksScope);
			}

			if (elementName != null) {
				sb.append("</" + elementName.toString() + ">");
			}
		}
	}

	private static void findMarks(XdmNode ssml, Multimap<NodeInfo, String> marksScope,
	        List<String> sortedMarkNames) {
		String markName = sortedMarkNames.get(sortedMarkNames.size() - 1);
		marksScope.put(ssml.getUnderlyingNode(), markName);
		XdmSequenceIterator iter = ssml.axisIterator(Axis.ANCESTOR);
		while (iter.hasNext()) {
			XdmNode parent = (XdmNode) iter.next();
			marksScope.put(parent.getUnderlyingNode(), markName);
		}
		if (ssml.getNodeName() != null
		        && markNode.getLocalName().equals(ssml.getNodeName().getLocalName())) {
			sortedMarkNames.add(ssml.getAttributeValue(markNameAttr));
		}
		iter = ssml.axisIterator(Axis.CHILD);
		while (iter.hasNext()) {
			findMarks((XdmNode) iter.next(), marksScope, sortedMarkNames);
		}
	}

	public static String toString(XdmNode ssml, String voiceName, SSMLAdapter adapter,
	        String endingMark) {
		if (adapter == null)
			adapter = new BasicSSMLAdapter();
		StringBuilder sb = new StringBuilder();
		sb.append(adapter.getHeader(voiceName));
		toString(ssml, sb, adapter, null, null);

		if (endingMark != null) {
			sb.append("<mark name=\""
			        + adapter.adaptAttributeValue(markNode, markNameAttr, endingMark) + "\"/>");
		}

		sb.append(adapter.getFooter());
		return sb.toString();
	}

	public static String[] toStringNoMarks(XdmNode ssml, String voiceName,
	        SSMLAdapter adapter, List<String> sortedMarkNames) {
		Multimap<NodeInfo, String> marksScope = HashMultimap.create();
		sortedMarkNames.clear();
		sortedMarkNames.add(null); //null means 'no mark'
		findMarks(ssml, marksScope, sortedMarkNames);
		String[] result = new String[sortedMarkNames.size()];

		if (sortedMarkNames.size() == 1) {
			result[0] = toString(ssml, voiceName, adapter, null);
		} else {
			int i = 0;
			for (String markName : sortedMarkNames) {
				StringBuilder sb = new StringBuilder();
				sb.append(adapter.getHeader(voiceName));
				toString(ssml, sb, adapter, markName, marksScope);
				sb.append(adapter.getFooter());
				result[i++] = sb.toString();
			}
		}

		return result;
	}

	public static String getBreakAfterSentence() {
		return BreakAfterSentence;
	}

	private static final String BreakAfterSentence = "<break time=\""
	        + System.getProperty("tts.pause.after.sentence", "250ms") + "\"/>";

	private static final QName markNode = new QName(null, "mark");
	private static final QName markNameAttr = new QName(null, "name");

}
