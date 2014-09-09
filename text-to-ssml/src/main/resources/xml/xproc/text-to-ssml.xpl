<p:declare-step type="px:text-to-ssml" version="1.0" name="main"
		xmlns:p="http://www.w3.org/ns/xproc"
		xmlns:px="http://www.daisy.org/ns/pipeline/xproc"
		xmlns:xml="http://www.w3.org/XML/1998/namespace"
		xmlns:ssml="http://www.w3.org/2001/10/synthesis"
		exclude-inline-prefixes="#all">

  <p:documentation>
    Generate the TTS input, as SSML snippets.
  </p:documentation>

  <p:input port="fileset.in" sequence="false"/>
  <p:input port="content.in"  sequence="false" primary="true">
    <p:documentation>The content documents (e.g. a Zedai document, a DTBook)</p:documentation>
  </p:input>
  <p:input port="sentence-ids" sequence="false" >
    <p:documentation>The list of the sentence ids, generated by the lexers.</p:documentation>
  </p:input>

  <p:output port="result" sequence="true" primary="true">
    <p:documentation>The SSML output.</p:documentation>
    <p:pipe port="result" step="skippable-to-ssml"/>
    <p:pipe port="result" step="content-to-ssml"/>
  </p:output>

  <p:option name="section-elements" required="true">
    <p:documentation>Elements used to identify threadable groups,
    together with its attribute 'section-attr'.</p:documentation>
  </p:option>
  <p:option name="section-attr" required="false" select="''">
    <p:documentation>If provided, there should be only one section
    element and the option 'section-attr-val' must be provided
    too.</p:documentation>
  </p:option>
  <p:option name="section-attr-val" required="false" select="''"/>

  <p:option name="word-element" required="true">
    <p:documentation>Element used to identify words within sentences,
    together with its attribute 'word-attr'.</p:documentation>
  </p:option>
  <p:option name="word-attr" required="false" select="''"/>
  <p:option name="word-attr-val" required="false" select="''"/>

  <p:option name="skippable-elements" required="false" select="''">
    <p:documentation>The list of elements that will be synthesized in
    separate sections when the corresponding option is enable.
    </p:documentation>
  </p:option>
  <p:option name="separate-skippable" required="false" select="'false'">
    <p:documentation>Whether or not the skippable elements must be all
    synthesized in separate sections.
    </p:documentation>
  </p:option>

  <p:option name="link-element" required="false" select="'true'">
    <p:documentation>Whether or not an HTML-like 'link' element exists,
    such as in EPUB3 and DTBook. Used for retrieving the CSS
    stylesheets.</p:documentation>
  </p:option>

  <p:option name="ssml-of-lexicons-uris" required="false" select="''">
    <p:documentation>URI of a SSML file containing a list of pointers
    to custom lexicons. The phonemes will owerwrite those defined in
    the builtin lexicons.</p:documentation>
  </p:option>

  <p:import href="http://www.daisy.org/pipeline/modules/common-utils/library.xpl"/>
  <p:import href="http://www.daisy.org/pipeline/modules/fileset-utils/library.xpl"/>
  <p:import href="styled-text-to-ssml.xpl" />
  <p:import href="skippable-to-ssml.xpl" />

  <p:variable name="style-ns" select="'http://www.daisy.org/ns/pipeline/tts'"/>

  <!-- Replace the sentences and the words with their SSML counterpart so that it -->
  <!-- will be much simpler and faster to apply transformations after.  -->
  <p:xslt name="normalize">
    <p:with-param name="word-element" select="$word-element"/>
    <p:with-param name="word-attr" select="$word-attr"/>
    <p:with-param name="word-attr-val" select="$word-attr-val"/>
    <p:input port="source">
      <p:pipe port="content.in" step="main"/>
      <p:pipe port="sentence-ids" step="main"/>
    </p:input>
    <p:input port="stylesheet">
      <p:document href="../xslt/normalize.xsl"/>
    </p:input>
  </p:xslt>
  <px:message message="Lexing information normalized"/>

  <p:xslt name="separate">
    <p:with-param name="skippable-elements" select="$skippable-elements"/>
    <p:input port="stylesheet">
      <p:document href="../xslt/extract-skippable.xsl"/>
    </p:input>
  </p:xslt>

  <px:skippable-to-ssml name="skippable-to-ssml">
    <p:input port="content.in">
      <p:pipe port="secondary" step="separate"/>
    </p:input>
    <p:with-option name="skippable-elements" select="$skippable-elements"/>
    <p:with-option name="style-ns" select="$style-ns"/>
  </px:skippable-to-ssml>

  <!-- Load the SSML file containing user's lexicons -->
  <p:choose name="ssml-of-lexicons-uris">
    <p:xpath-context>
      <p:empty/>
    </p:xpath-context>
    <p:when test="$ssml-of-lexicons-uris != ''">
      <p:output port="result"/>
      <p:load>
	<p:with-option name="href" select="$ssml-of-lexicons-uris"/>
      </p:load>
    </p:when>
    <p:otherwise>
      <p:output port="result"/>
      <p:identity>
	<p:input port="source">
	  <p:empty/>
	</p:input>
      </p:identity>
    </p:otherwise>
  </p:choose>

  <p:identity>
    <p:input port="source">
      <p:pipe port="result" step="separate"/>
    </p:input>
  </p:identity>
  <px:styled-text-to-ssml name="content-to-ssml">
    <p:input port="ssml-of-lexicons-uris">
      <p:pipe port="result" step="ssml-of-lexicons-uris"/>
    </p:input>
    <p:input port="fileset.in">
      <p:pipe port="fileset.in" step="main"/>
    </p:input>
    <p:with-option name="section-elements" select="$section-elements"/>
    <p:with-option name="section-attr" select="$section-attr"/>
    <p:with-option name="section-attr-val" select="$section-attr-val"/>
    <p:with-option name="style-ns" select="$style-ns"/>
  </px:styled-text-to-ssml>

</p:declare-step>
