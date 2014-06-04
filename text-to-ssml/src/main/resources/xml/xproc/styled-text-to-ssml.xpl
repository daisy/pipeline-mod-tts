<p:declare-step type="px:styled-text-to-ssml" version="1.0" name="main"
		xmlns:p="http://www.w3.org/ns/xproc"
		xmlns:px="http://www.daisy.org/ns/pipeline/xproc"
		xmlns:xml="http://www.w3.org/XML/1998/namespace"
		xmlns:ssml="http://www.w3.org/2001/10/synthesis"
		xmlns:tmp="http://www.daisy.org/ns/pipeline/tmp"
		xmlns:pls="http://www.w3.org/2005/01/pronunciation-lexicon"
		exclude-inline-prefixes="#all">

  <p:input port="fileset.in" sequence="false"/>
  <p:input port="content.in" sequence="false" primary="true"/>
  <p:input port="ssml-of-lexicons-uris" sequence="false"/>
  <p:output port="result" sequence="true" primary="true"/>

  <p:option name="section-elements" required="true"/>
  <p:option name="section-attr" required="false" select="''"/>
  <p:option name="section-attr-val" required="false" select="''"/>
  <p:option name="first-sheet-uri" required="false" select="''"/>
  <p:option name="style-ns" required="true"/>

  <p:import href="http://www.daisy.org/pipeline/modules/common-utils/library.xpl"/>

  <!-- Encapsulates the section elements into tmp:group. -->
  <p:xslt name="identify-sections">
    <p:with-param name="section-elements" select="$section-elements"/>
    <p:with-param name="section-attr" select="$section-attr"/>
    <p:with-param name="section-attr-val" select="$section-attr-val"/>
    <p:input port="source">
      <p:pipe port="content.in" step="main"/>
    </p:input>
    <p:input port="stylesheet">
      <p:document href="../xslt/identify-sections.xsl"/>
    </p:input>
  </p:xslt>
  <px:message message="Sections identified"/>

  <!-- Map the content to undispatchable units (i.e. the content can
       be split into smaller objects but not transfered to other
       units. The units will be processed by a single thread. -->
  <p:xslt name="set-thread">
    <p:input port="parameters">
      <p:empty/>
    </p:input>
    <p:input port="stylesheet">
      <p:document href="../xslt/assign-thread-id.xsl"/>
    </p:input>
  </p:xslt>
  <px:message message="ssml assigned to threads"/>

  <!-- Generate the rough skeleton of the SSML document. -->
  <!-- Everything is converted but the content of the sentences.-->
  <p:xslt name="gen-input">
    <p:with-param  name="css-sheet-uri" select="$first-sheet-uri"/>
    <p:with-param  name="style-ns" select="$style-ns"/>
    <p:input port="stylesheet">
      <p:document href="../xslt/generate-tts-input.xsl"/>
    </p:input>
  </p:xslt>
  <px:message message="TTS document input skeletons generated"/>

  <!-- Convert to SSML the own sentences' CSS properties and the CSS
       properties inside them. -->
  <p:xslt name="css-convert">
    <p:input port="parameters">
      <p:empty/>
    </p:input>
    <p:input port="stylesheet">
      <p:document href="../xslt/css-to-ssml.xsl"/>
    </p:input>
  </p:xslt>
  <px:message message="CSS properties converted to SSML"/><p:sink/>

  <!-- ============================================================== -->
  <!-- DO SOME TEXT-TO-SSML CONVERSIONS USING THE LEXICONS -->
  <!-- ============================================================== -->

  <!-- Load the user's lexicons. -->
  <p:for-each name="user-lexicons">
    <p:output port="result" sequence="true"/>
    <p:iteration-source select="//ssml:lexicon[@uri]">
      <p:pipe port="ssml-of-lexicons-uris" step="main"/>
    </p:iteration-source>
    <p:load>
      <p:with-option name="href" select="*/@uri"/>
    </p:load>
  </p:for-each>

  <!-- iterate over the fileset to extract the lexicons URI, then load them -->
  <!-- from the disk -->
  <p:for-each name="doc-lexicons">
    <p:iteration-source select="//*[@media-type = 'application/pls+xml']">
      <p:pipe port="fileset.in" step="main"/>
    </p:iteration-source>
    <p:output port="result" sequence="true"/>
    <p:load>
      <p:with-option name="href" select="/*/@original-href"/>
    </p:load>
  </p:for-each>

  <!-- find all the languages actually used -->
  <p:xslt name="list-lang">
    <p:input port="source">
      <p:pipe port="content.in" step="main"/>
    </p:input>
    <p:input port="parameters">
      <p:empty/>
    </p:input>
    <p:input port="stylesheet">
      <p:inline>
	<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="2.0">
	  <xsl:output method="xml" encoding="UTF-8" />
	  <xsl:template match="/">
	    <root>
	      <xsl:for-each-group select="//node()[@xml:lang]" group-by="@xml:lang">
		<lang><xsl:attribute name="lang">
		  <xsl:value-of select="@xml:lang"/>
		</xsl:attribute></lang>
	      </xsl:for-each-group>
	    </root>
	  </xsl:template>
	</xsl:stylesheet>
      </p:inline>
    </p:input>
  </p:xslt>

  <!-- read the corresponding lexicons from the disk -->
  <p:for-each name="builtin-lexicons">
    <p:output port="result" sequence="true"/>
    <p:iteration-source select="//*[@lang]">
      <p:pipe port="result" step="list-lang"/>
    </p:iteration-source>
    <p:variable name="lang" select="/*/@lang">
      <p:pipe port="current" step="builtin-lexicons"/>
    </p:variable>
    <p:try>
      <p:group>
	<p:load>
	  <p:with-option name="href" select="concat('../lexicons/lexicon_', $lang,'.pls')"/>
	</p:load>
	<px:message>
	  <p:with-option name="message" select="concat('loaded lexicon for language: ', $lang)"/>
	</px:message>
      </p:group>
      <p:catch>
	<p:identity>
	  <p:input port="source">
	    <p:empty/>
	  </p:input>
	</p:identity>
	<px:message>
	  <p:with-option name="message" select="concat('could not find the builtin lexicon for language: ', $lang)"/>
	</px:message>
      </p:catch>
    </p:try>
  </p:for-each>

  <px:message message="lexicons read from the disk"/><p:sink/>

  <p:identity name="empty-lexicon">
    <p:input port="source">
      <p:inline>
	<pls:lexicon version="1.0" xmlns="http://www.w3.org/2005/01/pronunciation-lexicon"/>
      </p:inline>
    </p:input>
  </p:identity>

  <p:xslt name="separate-regex-lexicons">
    <p:input port="source">
      <p:pipe port="result" step="user-lexicons"/>
      <p:pipe port="result" step="doc-lexicons"/>
      <p:pipe port="result" step="builtin-lexicons"/>
      <p:pipe port="result" step="empty-lexicon"/>
    </p:input>
    <p:input port="stylesheet">
      <p:document href="../xslt/reorganize-lexicons.xsl"/>
    </p:input>
    <p:input port="parameters">
      <p:empty/>
    </p:input>
  </p:xslt>

  <px:message message="PLS info separated"/><p:sink/>

  <p:xslt name="pls">
    <p:input port="source">
      <p:pipe port="result" step="css-convert"/>
      <p:pipe port="result" step="separate-regex-lexicons"/>
    </p:input>
    <p:input port="stylesheet">
      <p:document href="../xslt/pls-to-ssml.xsl"/>
    </p:input>
    <p:input port="parameters">
      <p:empty/>
    </p:input>
  </p:xslt>

  <p:xslt name="regex-pls">
    <p:input port="source">
      <p:pipe port="result" step="pls"/>
      <p:pipe port="secondary" step="separate-regex-lexicons"/>
    </p:input>
    <p:input port="stylesheet">
      <p:document href="../xslt/regex-pls-to-ssml.xsl"/>
    </p:input>
    <p:input port="parameters">
      <p:empty/>
    </p:input>
  </p:xslt>
  <px:message message="PLS info converted to SSML"/>

  <p:xslt name="filter">
    <p:input port="stylesheet">
      <p:document href="../xslt/filter-chars.xsl"/>
    </p:input>
    <p:input port="parameters">
      <p:empty/>
    </p:input>
  </p:xslt>
  <px:message message="Unsolicited graphical characters removed."/>

  <!-- split the result to extract the wrapped SSML files -->
  <p:delete match="@tmp:*"/>
  <p:filter name="docs-extract">
    <p:with-option name="select" select="'//ssml:speak'"/>
  </p:filter>

</p:declare-step>
