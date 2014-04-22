<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:ssml="http://www.w3.org/2001/10/synthesis"
    xmlns:xml="http://www.w3.org/XML/1998/namespace"
    xmlns:d="http://www.daisy.org/ns/pipeline/data"
    exclude-result-prefixes="#all"
    version="2.0">

  <xsl:param name="word-element" />
  <xsl:param name="word-attr" />
  <xsl:param name="word-attr-val" />

  <xsl:key name="sentences" match="*[@id]" use="@id"/>

  <xsl:template match="@*|node()" priority="1">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="@*|node()" priority="1" mode="inside-sentence">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()" mode="inside-sentence"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="*[key('sentences', @id, collection()[/d:sentences]) and not(ancestor-or-self::ssml:s)]" priority="3">
    <ssml:s>
      <xsl:apply-templates select="@*|node()" mode="inside-sentence"/>
    </ssml:s>
  </xsl:template>

  <xsl:template match="*[local-name()=$word-element and string(@*[local-name()=$word-attr]) = $word-attr-val]" priority="2" mode="inside-sentence">
    <ssml:token>
      <xsl:apply-templates select="@*|node()" mode="inside-sentence"/>
    </ssml:token>
  </xsl:template>

</xsl:stylesheet>
