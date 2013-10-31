<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:ssml="http://www.w3.org/2001/10/synthesis"
    xmlns:tmp="http://www.daisy.org/ns/pipeline/tmp"
    exclude-result-prefixes="#all"
    version="2.0">

  <!--======================================================================= -->
  <!-- Convert the CSS properties into SSML elements and discard any node -->
  <!-- which is not SSML. -->
  <!--======================================================================= -->

  <xsl:function name="tmp:normlist">
    <xsl:param name="li"/>
    <xsl:value-of select="replace(translate($li, ' ',''), '[^-_0-9a-zA-Z]+', '|')"/>
  </xsl:function>

  <xsl:template match="node()|@*">
    <xsl:copy>
      <xsl:apply-templates select="node()|@*"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="ssml:s">
    <xsl:copy>
      <xsl:copy-of select="@xml:lang|@id"/>
      <xsl:if test="@tmp:voice-family">
	<!-- voice-family has the format: voice-vendor|voice-name|attr1|attr2|... where attr is a gender or an age -->
	<xsl:variable name="normalized" select="tmp:normlist(@tmp:voice-family)"/>
	<xsl:if test="contains($normalized, '|')">
	  <xsl:variable name="left" select="substring-before($normalized, '|')"/>
	  <xsl:variable name="right" select="substring-after($normalized, '|')"/>
	  <xsl:attribute name="voice-vendor">
	    <xsl:value-of select="translate(lower-case($left), ' ','')"/>
	  </xsl:attribute>
	  <xsl:variable name="voice-name" select="translate(lower-case(if (contains($right, '|')) then substring-before($right, '|') else $right), ' ','')"/>
	  <xsl:if test="$voice-name">
	    <xsl:attribute name="voice-name">
	      <xsl:value-of select="$voice-name"/>
	    </xsl:attribute>
	  </xsl:if>
	</xsl:if>
      </xsl:if>
      <xsl:apply-templates select="." mode="css1"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="*" mode="css-child">
    <xsl:apply-templates select="." mode="css1"/>
  </xsl:template>

  <xsl:template match="text()" mode="css-child">
    <xsl:value-of select="."/>
  </xsl:template>

  <xsl:template match="*[namespace-uri()='http://www.w3.org/2001/10/synthesis']" mode="css-child">
    <xsl:copy>
      <xsl:copy-of select="@*"/>
      <xsl:apply-templates select="node()" mode="css1"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="*" mode="css1">
    <xsl:apply-templates select="." mode="css2"/>
  </xsl:template>
  <xsl:template match="*[@tmp:speak = 'none']" mode="css1">
  </xsl:template>

  <xsl:template match="*" mode="css2">
    <xsl:apply-templates select="." mode="css3"/>
  </xsl:template>
  <xsl:template match="*[@tmp:volume]" mode="css2">
    <ssml:prosody volume="{@tmp:volume}">
      <xsl:apply-templates select="." mode="css3"/>
    </ssml:prosody>
  </xsl:template>

  <xsl:template match="*" mode="css3">
    <xsl:apply-templates select="." mode="css4"/>
  </xsl:template>
  <xsl:template match="*[@tmp:pitch]" mode="css3">
    <ssml:prosody pitch="{@tmp:pitch}">
      <xsl:apply-templates select="." mode="css4"/>
    </ssml:prosody>
  </xsl:template>

  <xsl:template match="*" mode="css4">
    <xsl:apply-templates select="." mode="css5"/>
  </xsl:template>
  <xsl:template match="*[@tmp:speak = 'spell-out']" mode="css4">
    <ssml:say-as interpret-as="characters">
      <xsl:apply-templates select="." mode="css5"/>
    </ssml:say-as>
  </xsl:template>

  <xsl:template match="*" mode="css5">
    <xsl:apply-templates select="." mode="css6"/>
  </xsl:template>
  <xsl:template match="*[@tmp:speech-rate]" mode="css5">
    <ssml:prosody rate="{@tmp:speech-rate}">
      <xsl:apply-templates select="." mode="css6"/>
    </ssml:prosody>
  </xsl:template>

  <xsl:template match="*" mode="css6">
    <xsl:apply-templates select="." mode="css7"/>
  </xsl:template>
  <xsl:template match="*[@tmp:pitch-range]" mode="css6">
    <ssml:prosody range="{@tmp:pitch-range}">
      <xsl:apply-templates select="." mode="css7"/>
    </ssml:prosody>
  </xsl:template>

  <xsl:template match="*" mode="css7">
    <xsl:apply-templates select="." mode="css8"/>
  </xsl:template>
  <xsl:template match="*[@tmp:speak-numeral = 'digits']" mode="css7">
    <ssml:say-as interpret-as="ordinal">
      <xsl:apply-templates select="." mode="css8"/>
    </ssml:say-as>
  </xsl:template>


  <xsl:template match="*" mode="css8">
    <xsl:apply-templates select="." mode="css-end"/>
  </xsl:template>
  <xsl:template match="*[@tmp:speak-numeral = 'continuous']" mode="css8">
    <ssml:say-as interpret-as="cardinal">
      <xsl:apply-templates select="." mode="css-end"/>
    </ssml:say-as>
  </xsl:template>


  <xsl:template match="*" mode="css-end">
    <xsl:apply-templates select="node()" mode="css-child"/>
  </xsl:template>
  <xsl:template match="*[@tmp:voice-family]" mode="css7">
    <xsl:variable name="voice-info" select="substring-after(tmp:normlist(@tmp:voice-family), '|')"/> <!-- first element is the TTS engine -->
    <xsl:choose>
      <xsl:when test="$voice-info">
	<!-- TODO: move the gender and the age to the same place as voice-vendor and voice-name -->
	<ssml:voice>
	  <xsl:choose>
	    <xsl:when test="contains($voice-info, 'male')">
	      <xsl:attribute name="gender"><xsl:value-of select="'male'"/></xsl:attribute>
	    </xsl:when>
	    <xsl:when test="contains($voice-info, 'female')">
	      <xsl:attribute name="gender"><xsl:value-of select="'female'"/></xsl:attribute>
	    </xsl:when>
	    <xsl:when test="contains($voice-info, 'neutral')">
	      <xsl:attribute name="gender"><xsl:value-of select="'neutral'"/></xsl:attribute>
	    </xsl:when>
	  </xsl:choose>
	  <xsl:variable name="age" select="replace($voice-info, '\|([0-9]+)[\|\$]', '$1')"/>
	  <xsl:if test="$age">
	    <xsl:attribute name="age"><xsl:value-of select="$age"/></xsl:attribute>
	  </xsl:if>
	  <xsl:apply-templates select="node()" mode="css-child"/>
	</ssml:voice>
      </xsl:when>
      <xsl:otherwise>
	<xsl:apply-templates select="node()" mode="css-child"/>
      </xsl:otherwise>
    </xsl:choose>
  </xsl:template>

</xsl:stylesheet>

