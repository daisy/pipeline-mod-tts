<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    exclude-result-prefixes="#all"
    version="2.0">

  <!-- This stylesheet removes the characters that should not be
       pronounced by the TTS processors, e.g. bullets or any symbol
       used for enumerations. -->

  <!-- TODO: disable this transformation when CSS speak-punctution is on. -->

  <!-- symbols to not filter (including Braille, special quotes and physical units): -->
  <xsl:variable name="exceptions" select="'[™®©&#10240;-&#10495;&#10075;-&#10078;&#13184;-&#13279;]'"/>
  <xsl:variable name="exception-group" select="concat('(', $exceptions, ')')"/>
  <xsl:variable name="pre-regex" select="$exception-group"/>
  <xsl:variable name="post-regex" select="concat($exception-group, '~')"/>

  <xsl:template match="@*|*">
    <xsl:copy>
      <xsl:apply-templates select="@*|node()"/>
    </xsl:copy>
  </xsl:template>

  <xsl:template match="text()">
    <!-- This 3-stage replacement is performed to tag the characters
         that we don't want to be removed (despite the broad matching
         classes \p{So}) and to remove the tag afterwards. Note: XSLT
         grammar does not yet accept the negative look-ahead
         syntax. -->
    <xsl:value-of
	select="replace(replace(replace(., $pre-regex, '$1~'),
		'[\p{So}+•†∴※*¤§¶¦‡]([^~]|$)', '$1'), $post-regex, '$1')"/>
  </xsl:template>

</xsl:stylesheet>

