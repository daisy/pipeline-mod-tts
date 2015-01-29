<xsl:stylesheet version="2.0"
		xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
		xmlns:tts="http://www.daisy.org/ns/pipeline/tts">

  <xsl:variable name="audio-map" select="collection()[last()]"/>

  <xsl:key name="clips" match="*[@idref]" use="@idref"/>

  <xsl:template match="/">
    <tts:cover>
      <xsl:apply-templates select="*"/>
    </tts:cover>
  </xsl:template>

  <xsl:template match="*[@id]" priority="3">
    <xsl:if test="not(key('clips', @id, $audio-map))">
      <xsl:call-template name="report-missing"/>
      <xsl:apply-templates select="*"/>
    </xsl:if>
  </xsl:template>

  <xsl:template match="*" priority="2">
    <xsl:call-template name="report-missing"/>
    <xsl:apply-templates select="*"/>
  </xsl:template>

  <xsl:template match="node()" priority="1">
    <xsl:apply-templates select="*"/>
  </xsl:template>

  <xsl:template name="report-missing">
    <xsl:variable name="parent" select="local-name(.)"/>
    <xsl:variable name="inside" select="ancestor-or-self::*[@id][1]"/>
    <xsl:for-each select="text()">
      <xsl:variable name="text" select="normalize-space(.)"/>
      <xsl:if test="$text != ''">
	<tts:missing inside-id="{$inside/@id}" inside-node="{local-name($inside)}" parent="{$parent}">
	  <xsl:value-of select="$text"/>
	</tts:missing>
      </xsl:if>
    </xsl:for-each>
  </xsl:template>

</xsl:stylesheet>
