<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:ssml="http://www.w3.org/2001/10/synthesis"
    xmlns:xml="http://www.w3.org/XML/1998/namespace"
    xmlns:tmp="http://www.daisy.org/ns/pipeline/tmp"
    exclude-result-prefixes="#all"
    version="2.0">

  <xsl:template match="node()|@*">
    <xsl:copy>
      <xsl:apply-templates select="node()|@*"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="ssml:s">
    <xsl:copy>
      <xsl:variable name="following_section" select="following::tmp:group[1]"/>
      <xsl:variable name="preceding_section" select="preceding::tmp:group[1]"/>
      <xsl:variable name="sectionId1" select="if ($following_section) then generate-id($following_section) else '_0'"/>
      <xsl:variable name="sectionId2" select="if ($preceding_section) then generate-id($preceding_section) else '_0'"/>
      <xsl:attribute name="thread-id">
	<xsl:value-of select="concat($sectionId1, $sectionId2)"/>
      </xsl:attribute>
      <xsl:apply-templates select="node()|@*"/>
    </xsl:copy>
  </xsl:template>

</xsl:stylesheet>

