<?xml version="1.0" encoding="UTF-8"?>
<p:declare-step type="px:zedai-to-ssml" version="1.0"
		xmlns:p="http://www.w3.org/ns/xproc"
		xmlns:c="http://www.w3.org/ns/xproc-step"
		xmlns:px="http://www.daisy.org/ns/pipeline/xproc"
		xmlns:cx="http://xmlcalabash.com/ns/extensions"
		exclude-inline-prefixes="#all"
		name="main">

    <p:documentation>
      <p>Specialization of the SSML generation for Zedai</p>
    </p:documentation>

    <p:input port="fileset.in" sequence="false"/>
    <p:input port="content.in" primary="true" sequence="false"/>

    <p:output port="content.out" sequence="false">
      <p:pipe port="content.out" step="ssml-gen" />
    </p:output>

    <p:output port="ssml.out" primary="true" sequence="true">
      <p:pipe port="ssml.out" step="ssml-gen" />
    </p:output>

    <p:option name="css-sheet-uri" required="false" select="''"/>
    <p:option name="ssml-of-lexicons-uris" required="false" select="''"/>

    <p:import href="http://xmlcalabash.com/extension/steps/library-1.0.xpl"/>
    <p:import href="http://www.daisy.org/pipeline/modules/text-to-ssml/text-to-ssml.xpl" />

    <px:text-to-ssml name="ssml-gen">
      <!-- output ssml.out and content.out -->
      <p:input port="fileset.in">
	<p:pipe port="fileset.in" step="main"/>
      </p:input>
      <p:input port="content.in">
	<p:pipe port="content.in" step="main"/>
      </p:input>
      <p:with-option name="xhtml-link" select="'false'"/>
      <p:with-option name="section-elements" select="'section'"/>
      <p:with-option name="sentence-element" select="'s'"/>
      <p:with-option name="word-element" select="'w'"/>
      <p:with-option name="ssml-of-lexicons-uris" select="$ssml-of-lexicons-uris"/>
      <p:with-option name="aural-sheet-uri" select="$css-sheet-uri"/>
    </px:text-to-ssml>

    <cx:message message="End SSML generation for ZedAI"/><p:sink/>

</p:declare-step>
