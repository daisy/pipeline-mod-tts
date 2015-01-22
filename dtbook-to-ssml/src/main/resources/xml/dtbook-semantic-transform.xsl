<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:ssml="http://www.w3.org/2001/10/synthesis"
    xmlns:dt="http://www.daisy.org/z3986/2005/dtbook/"
    exclude-result-prefixes="#all"
    version="2.0">

  <xsl:template match="@*|node()">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()"/>
    </xsl:copy>
  </xsl:template>

  <!-- Create a new TTS sentence which contains the @alt attribute of the images. -->
  <xsl:template match="dt:img">
    <xsl:copy>
      <xsl:copy-of select="@* except @id"/>
      <xsl:if test="@alt">
	<ssml:s id="{@id}"><xsl:value-of select="@alt"/></ssml:s>
      </xsl:if>
      <xsl:apply-templates select="node()"/>
    </xsl:copy>
  </xsl:template>

  <!-- Add extra white spaces around elements that are sometimes badly juxtaposed. -->
  <xsl:template match="dt:abbr|dt:acronym|dt:br|dt:a">
    <xsl:value-of select="' '"/>
    <xsl:copy>
      <xsl:apply-templates select="@*|node()"/>
    </xsl:copy>
    <xsl:value-of select="' '"/>
  </xsl:template>

  <!-- Commented because it is only relevant forn old DTBooks: -->
  <!-- Wrap links with words in order to make sure they can be matched against lexicon entries. -->
  <!-- (provided that DTBook links cannot contain words, for old versions of the DTBook spec) -->
  <!-- <xsl:template match="dt:a"> -->
  <!--   <xsl:value-of select="' '"/> -->
  <!--   <xsl:copy> -->
  <!--     <xsl:copy-of select="@*"/> -->
  <!--     <xsl:choose> -->
  <!-- 	<xsl:when test="not(ancestor::*[local-name() = 'w'][1])"> -->
  <!-- 	  <dt:w><xsl:apply-templates select="node()"/></dt:w> -->
  <!-- 	</xsl:when> -->
  <!-- 	<xsl:otherwise> -->
  <!-- 	  <xsl:apply-templates select="node()"/> -->
  <!-- 	</xsl:otherwise> -->
  <!--     </xsl:choose> -->
  <!--   </xsl:copy> -->
  <!--   <xsl:value-of select="' '"/> -->
  <!-- </xsl:template> -->


</xsl:stylesheet>
