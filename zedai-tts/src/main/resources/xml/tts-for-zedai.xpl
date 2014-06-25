<?xml version="1.0" encoding="UTF-8"?>
<p:declare-step version="1.0" name="main" type="px:tts-for-zedai"
		xmlns:p="http://www.w3.org/ns/xproc"
		xmlns:px="http://www.daisy.org/ns/pipeline/xproc"
		xmlns:d="http://www.daisy.org/ns/pipeline/data"
		exclude-inline-prefixes="#all">

  <p:input port="content.in" primary="true" sequence="true">
    <p:documentation xmlns="http://www.w3.org/1999/xhtml">
      <p>List of documents in memory, including one or more ZedAI
      documents.</p>
    </p:documentation>
  </p:input>

  <p:input port="fileset.in">
    <p:documentation xmlns="http://www.w3.org/1999/xhtml">
       <p>Input fileset including ZedAI documents, lexicons and CSS
       stylesheets.</p>
    </p:documentation>
  </p:input>

  <p:output port="audio-map">
    <p:pipe port="audio-map" step="synthesize"/>
    <p:documentation xmlns="http://www.w3.org/1999/xhtml">
       <p>List of audio clips (see pipeline-mod-tts
       documentation).</p>
    </p:documentation>
  </p:output>

  <p:output port="content.out" primary="true" sequence="true">
    <p:pipe port="content.out" step="synthesize"/>
    <p:documentation xmlns="http://www.w3.org/1999/xhtml">
       <p>Copy of content.in. The included ZedAI documents are
       enriched with ids, words and sentences.</p>
    </p:documentation>
  </p:output>

  <p:output port="sentence-ids" sequence="true">
    <p:pipe port="sentence-ids" step="synthesize"/>
    <p:documentation xmlns="http://www.w3.org/1999/xhtml">
      <p>Every document of this port is a list of nodes whose id
      attribute refers to elements of the 'content.out'
      documents. Grammatically speaking, the referred elements are
      sentences even if the underlying XML elements are not meant to
      be so. Documents are listed in the same order as in
      'content.out'.</p>
    </p:documentation>
  </p:output>

  <p:option name="audio" required="false" px:type="boolean" select="'true'">
    <p:documentation xmlns="http://www.w3.org/1999/xhtml">
      <h2 px:role="name">Enable Text-To-Speech</h2>
      <p px:role="desc">Whether to use a speech synthesizer to produce
      audio files.</p>
    </p:documentation>
  </p:option>

  <!-- Might be useful some day: -->
  <!-- <p:option name="segmentation" required="false" px:type="boolean" select="'true'"> -->
  <!--   <p:documentation xmlns="http://www.w3.org/1999/xhtml"> -->
  <!--     <h2 px:role="name">Enable segmentation</h2> -->
  <!--     <p px:role="desc">Whether to segment the text or not, i.e. word and sentence boundary detection.</p> -->
  <!--   </p:documentation> -->
  <!-- </p:option> -->

  <p:option name="aural-css" required="false" px:type="anyURI" select="''">
    <p:documentation xmlns="http://www.w3.org/1999/xhtml">
      <h2 px:role="name">Aural CSS sheet</h2>
      <p px:role="desc">Path of an additional Aural CSS stylesheet for
      the Text-To-Speech.</p>
    </p:documentation>
  </p:option>

  <p:option name="ssml-of-lexicons-uris" required="false" px:type="anyURI" select="''">
    <p:documentation xmlns="http://www.w3.org/1999/xhtml">
      <h2 px:role="name">Lexicons SSML pointers</h2>
      <p px:role="desc">URI of an SSML file which contains a list of
      lexicon elements with their URI. The lexicons will be provided
      to the Text-To-Speech processors.</p>
    </p:documentation>
  </p:option>

  <p:import href="http://www.daisy.org/pipeline/modules/ssml-to-audio/library.xpl" />
  <p:import href="http://www.daisy.org/pipeline/modules/zedai-to-ssml/library.xpl" />
  <p:import href="http://www.daisy.org/pipeline/modules/zedai-break-detection/library.xpl"/>
  <p:import href="http://www.daisy.org/pipeline/modules/common-utils/library.xpl"/>

  <p:choose name="synthesize">
    <!-- ====== TTS OFF ====== -->
    <p:when test="$audio = 'false'">
      <p:output port="audio-map">
	<p:inline>
	  <d:audio-clips/>
	</p:inline>
      </p:output>
      <p:output port="content.out" primary="true" sequence="true">
	<p:pipe port="content.in" step="main"/>
      </p:output>
      <p:output port="sentence-ids" sequence="true">
	<p:empty/>
      </p:output>
      <p:sink/>
    </p:when>

    <!-- ====== TTS ON ====== -->
    <p:otherwise>
      <p:output port="audio-map">
	<p:pipe port="result" step="to-audio"/>
      </p:output>
      <p:output port="content.out" primary="true" sequence="true">
	<p:pipe port="result" step="lexing"/>
      </p:output>
      <p:output port="sentence-ids" sequence="true">
	<p:pipe port="sentence-ids" step="lexing"/>
      </p:output>

      <!-- LEXING -->
      <p:for-each name="lexing">
	<p:iteration-source>
	  <p:pipe port="content.in" step="main"/>
	</p:iteration-source>
	<p:output port="result" primary="true"/>
	<p:output port="sentence-ids">
	  <p:pipe port="sentence-ids" step="is.zedai"/>
	</p:output>
	<p:choose name="is.zedai">
	  <!-- TODO: use instead the fileset to know whether it's a ZedAI document. -->
	  <p:when test="namespace-uri(/*/*[1]) = 'http://www.daisy.org/ns/z3998/authoring/'">
	    <p:output port="result" primary="true">
	      <p:pipe port="result" step="break"/>
	    </p:output>
	    <p:output port="sentence-ids">
	      <p:pipe port="sentence-ids" step="break"/>
	    </p:output>
	    <px:zedai-break-detect name="break"/>
	  </p:when>
	  <p:otherwise>
	    <p:output port="result" primary="true"/>
	    <p:output port="sentence-ids">
	      <p:inline>
		<d:sentence-ids/>
	      </p:inline>
	    </p:output>
	    <p:identity/>
	  </p:otherwise>
	</p:choose>
      </p:for-each>
      <!-- TTS -->
      <p:for-each name="for-each.content">
	<p:output port="ssml.out" primary="true" sequence="true"/>
	<p:split-sequence name="sentences">
	  <p:input port="source">
	    <p:pipe port="sentence-ids" step="lexing"/>
	  </p:input>
	  <p:with-option name="test" select="concat('position()=', p:iteration-position())"/>
	</p:split-sequence>
	<p:group>
	  <p:variable name="sentence-num" select="count(//*[@id])"/>
	  <p:identity>
	    <p:input port="source">
	      <p:pipe port="current" step="for-each.content"/>
	    </p:input>
	  </p:identity>
	  <px:message>
	    <p:with-option name="message"
			   select="concat('number of sentences for ', base-uri(/*) , ': ', $sentence-num)"/>
	  </px:message>
	  <p:choose>
	    <p:when test="$sentence-num = 0">
	      <p:output port="ssml.out" primary="true" sequence="true">
		<p:empty/>
	      </p:output>
	      <p:sink/>
	    </p:when>
	    <p:otherwise>
	      <p:output port="ssml.out" primary="true" sequence="true">
	  	<p:pipe port="result" step="ssml-gen"/>
	      </p:output>
	      <px:zedai-to-ssml name="ssml-gen">
	  	<p:input port="content.in">
	  	  <p:pipe port="current" step="for-each.content"/>
	  	</p:input>
	  	<p:input port="sentence-ids">
	  	  <p:pipe port="matched" step="sentences"/>
	  	</p:input>
	  	<p:input port="fileset.in">
	  	  <p:pipe port="fileset.in" step="main"/>
	  	</p:input>
	  	<p:with-option name="css-sheet-uri" select="$aural-css"/>
	  	<p:with-option name="ssml-of-lexicons-uris" select="$ssml-of-lexicons-uris"/>
	      </px:zedai-to-ssml>
	    </p:otherwise>
	  </p:choose>
	</p:group>
      </p:for-each>
      <px:ssml-to-audio name="to-audio"/>
    </p:otherwise>
  </p:choose>

</p:declare-step>
