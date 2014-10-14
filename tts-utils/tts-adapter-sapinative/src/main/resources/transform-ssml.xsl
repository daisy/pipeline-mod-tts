<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:ssml="http://www.w3.org/2001/10/synthesis"
    exclude-result-prefixes="#all"
    version="2.0">
    
  <xsl:output omit-xml-declaration="yes"/>

  <xsl:param name="voice" select="''"/>
  <xsl:param name="ending-mark" select="''"/>

  <xsl:template match="*">
  	<ssml:speak version="1.0">
  		<xsl:copy-of select="if (local-name()='speak') then node() else ."/>
    	<ssml:break time="250ms"/>
    	<ssml:mark name="{$ending-mark}"/>
	 </ssml:speak>
  </xsl:template>

</xsl:stylesheet>