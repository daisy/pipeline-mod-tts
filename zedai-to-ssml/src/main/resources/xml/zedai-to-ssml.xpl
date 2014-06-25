<?xml version="1.0" encoding="UTF-8"?>
<p:declare-step type="px:zedai-to-ssml" version="1.0"
		xmlns:p="http://www.w3.org/ns/xproc"
		xmlns:c="http://www.w3.org/ns/xproc-step"
		xmlns:px="http://www.daisy.org/ns/pipeline/xproc"
		exclude-inline-prefixes="#all"
		name="main">

  <p:documentation>
    <p>Specialization of the SSML generation for ZedAI</p>
  </p:documentation>

  <p:input port="fileset.in" sequence="false"/>
  <p:input port="content.in" primary="true" sequence="false"/>
  <p:input port="sentence-ids" sequence="false"/>

  <p:output port="result" primary="true" sequence="true">
    <p:pipe port="result" step="ssml-gen" />
  </p:output>

  <p:option name="css-sheet-uri" required="false" select="''"/>
  <p:option name="ssml-of-lexicons-uris" required="false" select="''"/>
  <p:option name="separate-skippable" required="false" select="'true'"/>

  <p:import href="http://www.daisy.org/pipeline/modules/common-utils/library.xpl"/>
  <p:import href="http://www.daisy.org/pipeline/modules/text-to-ssml/library.xpl" />

  <px:text-to-ssml name="ssml-gen">
    <p:input port="fileset.in">
      <p:pipe port="fileset.in" step="main"/>
    </p:input>
    <p:input port="content.in">
      <p:pipe port="content.in" step="main"/>
    </p:input>
    <p:input port="sentence-ids">
      <p:pipe port="sentence-ids" step="main"/>
    </p:input>
    <p:with-option name="section-elements" select="'section'"/>
    <p:with-option name="word-element" select="'w'"/>
    <p:with-option name="aural-sheet-uri" select="$css-sheet-uri"/>
    <p:with-option name="separate-skippable" select="$separate-skippable"/>
    <p:with-option name="skippable-elements" select="'annoref,noteref,ref,lnum'"/>
    <p:with-option name="ssml-of-lexicons-uris" select="$ssml-of-lexicons-uris"/>
  </px:text-to-ssml>

  <px:message message="End SSML generation for ZedAI"/><p:sink/>

</p:declare-step>
