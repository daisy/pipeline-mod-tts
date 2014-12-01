<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:ssml="http://www.w3.org/2001/10/synthesis"
    xmlns:tts="http://www.daisy.org/ns/pipeline/tts"
    xmlns:tmp="http://www.daisy.org/ns/pipeline/tmp"
    exclude-result-prefixes="#all"
    version="2.0">

  <!--======================================================================= -->
  <!-- Create a SSML file for every future TTS thread. The SSML files will -->
  <!-- contain a list of SSML sentences. Each one carries the CSS -->
  <!-- properties inherited from their parents. The CSS cues and pauses are -->
  <!-- all handled by this script, including the ones inside the -->
  <!-- sentences. Aside from the pauses and the cues, the sentences' -->
  <!-- content is left unchanged. -->
  <!-- For now, cues' location are relative to the main CSS sheet location. -->
  <!-- (only one sheet is taken into account) -->
  <!--======================================================================= -->

  <xsl:import href="flatten-css.xsl"/>

  <xsl:param name="lang"/>
  <xsl:param name="style-ns"/>

  <!-- ========= bind every cue and pause to its most relevant sentence ========= -->
  <!-- ========= (document order is kept on purpose)                    ========= -->

  <xsl:key name="bindings" match="*[@sentence]" use="@sentence"/>

  <xsl:variable name="bindings">
    <root>
      <xsl:apply-templates mode="build-bindings" select="/*"/>
    </root>
  </xsl:variable>

  <xsl:template match="*" mode="build-before-binding">
    <xsl:param name="sentence-id"/>
    <xsl:if test="@tts:pause-before or @tts:cue-before">
      <bind sentence="{$sentence-id}">
	<xsl:copy-of select="@tts:pause-before"/>
	<xsl:copy-of select="@tts:cue-before"/>
      </bind>
    </xsl:if>
  </xsl:template>

  <xsl:template match="*" mode="build-after-binding">
    <xsl:param name="sentence-id"/>
    <xsl:if test="@tts:pause-after or @tts:cue-after">
      <bind sentence="{$sentence-id}">
	<xsl:copy-of select="@tts:cue-after"/>
	<xsl:copy-of select="@tts:pause-after"/>
      </bind>
    </xsl:if>
  </xsl:template>

  <xsl:template match="ssml:s" mode="build-bindings" priority="2">
    <xsl:apply-templates select="." mode="build-before-binding">
      <xsl:with-param name="sentence-id" select="@id"/>
    </xsl:apply-templates>
    <content sentence="{@id}"/>
    <xsl:apply-templates select="." mode="build-after-binding">
      <xsl:with-param name="sentence-id" select="@id"/>
    </xsl:apply-templates>
  </xsl:template>

  <xsl:template match="*" mode="build-bindings-no-sentence">
    <xsl:param name="sentence-id"/>
    <xsl:apply-templates select="." mode="build-before-binding">
      <xsl:with-param name="sentence-id" select="$sentence-id"/>
    </xsl:apply-templates>
    <xsl:apply-templates select="*" mode="build-bindings-no-sentence">
      <xsl:with-param name="sentence-id" select="$sentence-id"/>
    </xsl:apply-templates>
    <xsl:apply-templates select="." mode="build-after-binding">
      <xsl:with-param name="sentence-id" select="$sentence-id"/>
    </xsl:apply-templates>
  </xsl:template>

  <xsl:template match="*" mode="build-bindings" priority="1">
    <xsl:choose>
      <xsl:when test="@tts:pause-before or @tts:cue-before or @tts:pause-after or @tts:cue-after">
	<xsl:variable name="descendants" select="descendant::ssml:s" />
	<xsl:choose>
	  <xsl:when test="$descendants">
	    <xsl:apply-templates select="." mode="build-before-binding">
	      <xsl:with-param name="sentence-id" select="$descendants[1]/@id"/>
	    </xsl:apply-templates>
	    <xsl:apply-templates select="*" mode="build-bindings"/>
	    <xsl:apply-templates select="." mode="build-after-binding">
	      <xsl:with-param name="sentence-id" select="$descendants[last()]/@id"/>
	    </xsl:apply-templates>
	  </xsl:when>
	  <xsl:otherwise>
	    <!-- This branch contains no sentence. We take a sentence from the nearest sub-tree -->
	    <xsl:variable name="prev-sentence-ancestor" select="ancestor::*[preceding-sibling::*/descendant-or-self::ssml:s[1]][1]" />
	    <xsl:variable name="next-sentence-ancestor" select="ancestor::*[following-sibling::*/descendant-or-self::ssml:s[1]][1]" />
	    <xsl:apply-templates select="." mode="build-bindings-no-sentence">
	      <!-- Legacy code: -->
	      <!-- <xsl:with-param name="sentence-id" -->
	      <!-- 		      select="if ($prev-sentence-ancestor/ancestor::*[self = $next-sentence-ancestor]) then -->
	      <!-- 			      ($prev-sentence-ancestor/preceding-sibling::*[descendant-or-self::ssml:s[1]][1]/descendant-or-self::ssml:s[last()]/@id) else -->
	      <!-- 			      ($next-sentence-ancestor/following-sibling::*/descendant-or-self::ssml:s[1]/@id)"/> -->

	      <xsl:with-param name="sentence-id"
			      select="if ($prev-sentence-ancestor) then
				      ($prev-sentence-ancestor/preceding-sibling::*[descendant-or-self::ssml:s[1]][1]/descendant-or-self::ssml:s[last()]/@id) else
				      ($next-sentence-ancestor/following-sibling::*[descendant-or-self::ssml:s[1]][1]/descendant-or-self::ssml:s[1]/@id)"/>
	    </xsl:apply-templates>
	  </xsl:otherwise>
	</xsl:choose>
      </xsl:when>
      <xsl:otherwise>
	<xsl:apply-templates select="*" mode="build-bindings"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

  <!-- ========= iterate over the sentences ========= -->
  <xsl:template match="/">
    <tmp:root>
      <xsl:for-each-group select="//ssml:s" group-adjacent="@thread-id">
	<ssml:speak version="1.1"> <!-- version 1.0 has no <ssml:token>, nor <ssml:w>. -->
	  <xsl:for-each select="current-group()">
	    <ssml:s>
	      <xsl:variable name="sentence" select="current()"/>
	      <xsl:copy-of select="$sentence/@*"/>
	      <xsl:apply-templates select="$sentence" mode="flatten-css-properties">
		<xsl:with-param name="style-ns" select="$style-ns"/>
		<xsl:with-param name="lang" select="$lang"/>
	      </xsl:apply-templates>
	      <xsl:for-each select="key('bindings', $sentence/@id, $bindings)">
		<xsl:choose>
		  <xsl:when test="current()/@tts:pause-before or current()/@tts:cue-before or current()/@tts:pause-after or current()/@tts:cue-after">
		    <xsl:apply-templates select="current()/@tts:pause-before|current()/@tts:pause-after" mode="pause"/>
		    <xsl:apply-templates select="current()/@tts:cue-before|current()/@tts:cue-after" mode="cue"/>
		  </xsl:when>
		  <xsl:otherwise>
		    <!-- sentence content -->
		    <xsl:apply-templates select="$sentence/node()" mode="inside-sentence"/>

		    <!-- Capture the next punctuation marks to produce the right prosody. -->
		    <xsl:variable name="next-text-node"
				  select="$sentence/following-sibling::text()[normalize-space(.) != ''][1]"/>

		    <xsl:if test="$next-text-node">
		      <xsl:analyze-string select="$next-text-node" regex="^[\p{{Z}}]*([.!?…])">
			<xsl:matching-substring>
			  <xsl:value-of select="regex-group(1)"/>
			</xsl:matching-substring>
			<xsl:non-matching-substring/>
		      </xsl:analyze-string>
		    </xsl:if>

		    <!-- Legacy code (too CPU-intensive, but more accurate): -->
		    <!-- <xsl:variable name="next-node" select="following-sibling::node()[preceding-sibling::ssml:s = current()]"/> -->
		    <!-- <xsl:variable name="next-node-content" select="if ($next-node/self::text()) -->
		    <!-- 						   then $next-node -->
		    <!-- 						   else string-join($next-node//text(),'')"/> -->

		    <!-- <xsl:value-of select="replace($next-node-content, '[^.!?…]', '')"/> -->


		    <!-- No ssml:break is added here as some TTS
		         processors may already add the silent
		         fragments after the final punctuation
		         marks. -->
		  </xsl:otherwise>
		</xsl:choose>
	      </xsl:for-each>
	    </ssml:s>
	  </xsl:for-each>
	</ssml:speak>
      </xsl:for-each-group>
    </tmp:root>
  </xsl:template>

  <!-- === copy the sentences' content and add the cues and pauses if necessary === -->
  <xsl:template match="*" mode="inside-sentence">
    <xsl:apply-templates select="@tts:pause-before" mode="pause"/>
    <xsl:apply-templates select="@tts:cue-before" mode="cue"/>

    <xsl:element name="{name()}" namespace="{namespace-uri()}">
      <xsl:copy-of select="@*"/>
      <xsl:apply-templates select="node()" mode="inside-sentence"/>
    </xsl:element>

    <xsl:apply-templates select="@tts:cue-after" mode="cue"/>
    <xsl:apply-templates select="@tts:pause-after" mode="pause"/>
  </xsl:template>

  <xsl:template match="text()" mode="inside-sentence">
    <xsl:value-of select="."/>
  </xsl:template>

  <xsl:template match="@*" mode="pause">
    <ssml:break time="{.}"/>
  </xsl:template>

  <xsl:template match="@*" mode="cue">
    <xsl:choose>
      <xsl:when test="starts-with(., 'file:///')">
	<ssml:audio src="{substring-after(., 'file://')}"/>
      </xsl:when>
      <xsl:when test="starts-with(., 'file:/')">
	<ssml:audio src="{substring-after(., 'file:')}"/>
      </xsl:when>
      <xsl:when test="starts-with(., '/')">
	<ssml:audio src="{.}"/>
      </xsl:when>
    </xsl:choose>
  </xsl:template>

</xsl:stylesheet>

