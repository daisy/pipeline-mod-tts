<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:ssml="http://www.w3.org/2001/10/synthesis"
    xmlns:xml="http://www.w3.org/XML/1998/namespace"
    xmlns:tmp="http://www.daisy.org/ns/pipeline/tmp"
    exclude-result-prefixes="#all"
    version="2.0">

  <xsl:param name="skippable-elements" />

  <!-- must have the same value as in the Java part -->
  <xsl:variable name="mark-delimiter" select="'___'"/>
  <xsl:variable name="skippable-list" select="concat(',', $skippable-elements, ',')" />

  <xsl:template match="/">
    <xsl:copy>
      <xsl:apply-templates select="node()" mode="skippable-free"/>
    </xsl:copy>
    <xsl:result-document href="skippables.xml">
      <xsl:copy>
	<xsl:apply-templates select="node()" mode="skippable-only"/>
      </xsl:copy>
    </xsl:result-document>
  </xsl:template>


  <!-- === REBUILD THE DOCUMENT WITHOUT SKIPPABLE ELEMENTS -->

  <xsl:template match="@*|node()" priority="1" mode="skippable-free">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()" mode="skippable-free"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="*[@id and contains($skippable-list, concat(',', local-name(), ','))]"
		priority="2" mode="skippable-free">
    <!-- If the skippable is not inside any sentence (i.e. the
         skippable is a sentence itself), there is no need for marks
         because the surrounding content won't be
         synthesized. Besides, the skippable element has already been
         transformed into a ssml:s. -->
    <xsl:variable name="sent" select="ancestor::ssml:s[1]"/>
    <xsl:if test="$sent">
      <xsl:variable name="up" select="ancestor::*"/>
      <xsl:variable name="before" select="preceding::*[@id][1]"/>
      <xsl:variable name="after" select="following::*[@id][1]"/>
      <xsl:variable name="before-parent"
		    select="$before/ancestor-or-self::*[@id and not($up intersect .)][last()]"/>
      <xsl:variable name="after-parent"
		    select="$after/ancestor-or-self::*[@id and not($up intersect .)][last()]"/>
      <xsl:variable name="before-str"
		    select="if (not(contains($skippable-list, concat(',', local-name($before), ','))) and
			    $before-parent/ancestor::*[. = $sent][1])
			    then $before-parent/@id else ''"/>
      <xsl:variable name="after-str"
		    select="if (not(contains($skippable-list, concat(',', local-name($after), ','))) and
			    $after-parent/ancestor::*[. = $sent][1])
			    then $after/@id else ''"/>


      <!-- The skippable element is replaced with a mark delimiting
	   the end of the previous element and the beginning of the
	   next one. The skippable elements are copied to a separate
	   document. -->
      <ssml:mark name="{concat($before-str, $mark-delimiter, $after-str)}"/>
      <!-- The empty marks ($before-str='' and $after-str='') are left
           because we want the TTS to know that in such situations the
           sentence container must not be synthesized (it is very
           likely to be a sentence with non-speakable characters
           anyway, otherwise it means that the NLP failed to split
           around the skippable elements). If the sentence were
           synthesized, an entry will be eventually created in the
           audio-map that would make impossible to create nodes with
           @smilref (or equivalent) inside the sentence. -->
    </xsl:if>
  </xsl:template>


  <!-- === REBUILD THE DOCUMENT WITH SKIPPABLE ELEMENTS ONLY -->

  <xsl:template match="@*|*" priority="1" mode="skippable-only">
    <xsl:copy>
      <xsl:apply-templates select="@*|*" mode="skippable-only"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="text()" mode="skippable-only">
    <!-- Ignored if not in a skippable element. -->
  </xsl:template>

  <xsl:template match="*[@id and contains($skippable-list, concat(',', local-name(), ','))]"
		priority="2" mode="skippable-only">
    <xsl:copy-of select="."/>
  </xsl:template>

</xsl:stylesheet>

