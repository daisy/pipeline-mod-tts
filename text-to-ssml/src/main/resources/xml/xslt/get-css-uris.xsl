<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
		xmlns:d="http://www.daisy.org/ns/pipeline/data"
		version="2.0" exclude-result-prefixes="#all">

  <xsl:param name="xhtml-link" select="'true'"/>

  <xsl:template match="/">
    <d:root>
      <xsl:for-each select="processing-instruction('xml-stylesheet')">
	<xsl:if test="contains(., 'text/css')">
	  <xsl:analyze-string select="." regex="href=['&quot;]([^'&quot;]*)['&quot;]">
	    <xsl:matching-substring>
	      <d:sheet href="{regex-group(1)}"/>
            </xsl:matching-substring>
	  </xsl:analyze-string>
	</xsl:if>
      </xsl:for-each>
      <xsl:if test="$xhtml-link = 'true'">
	<xsl:for-each select="//*[local-name() = 'link' and @rel='stylesheet' and @href and @type='text/css']">
	  <d:sheet href="{@href}"/>
	</xsl:for-each>
      </xsl:if>
    </d:root>
  </xsl:template>

</xsl:stylesheet>
