<p:declare-step type="px:skippable-to-ssml" version="1.0" name="main"
		xmlns:p="http://www.w3.org/ns/xproc"
		xmlns:px="http://www.daisy.org/ns/pipeline/xproc"
		xmlns:xml="http://www.w3.org/XML/1998/namespace"
		xmlns:ssml="http://www.w3.org/2001/10/synthesis"
		xmlns:tmp="http://www.daisy.org/ns/pipeline/tmp"
		exclude-inline-prefixes="#all">

  <p:input port="content.in" sequence="false" primary="true"/>
  <p:output port="result" sequence="true" primary="true"/>

  <p:option name="skippable-elements"/>
  <p:option name="style-ns"/>

  <p:import href="http://www.daisy.org/pipeline/modules/common-utils/library.xpl"/>

  <!-- Generate the rough skeleton of the SSML document. The document
       will group together the skippable elements that share the same
       CSS properties. Everything is converted but the content of the
       skippable-elements (in most cases they are mere numbers).-->
  <p:xslt>
    <p:with-param name="skippable-elements" select="$skippable-elements"/>
    <p:with-param name="style-ns" select="$style-ns"/>
    <p:input port="stylesheet">
      <p:document href="../xslt/skippable-to-ssml.xsl"/>
    </p:input>
  </p:xslt>
  <px:message message="Skippable TTS document input skeletons generated."/>

  <!-- Convert the skippable CSS properties to SSML. -->
  <p:xslt name="css-convert">
    <p:input port="parameters">
      <p:empty/>
    </p:input>
    <p:input port="stylesheet">
      <p:document href="../xslt/css-to-ssml.xsl"/>
    </p:input>
  </p:xslt>
  <px:message message="Skippable elements' inner CSS properties converted to SSML."/>

  <!-- Split the result to extract the wrapped SSML files. -->
  <p:delete match="@tmp:*"/>
  <p:filter name="docs-extract">
    <p:with-option name="select" select="'//ssml:speak'"/>
  </p:filter>

</p:declare-step>
