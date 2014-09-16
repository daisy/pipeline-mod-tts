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
  		<xsl:apply-templates select="if (local-name() = 'speak') then node() else ." mode="cpy"/>
    	<ssml:break time="250ms"/>
    	<ssml:mark name="{$ending-mark}"/>
	 </ssml:speak>
  </xsl:template>

  <xsl:template match="text()" mode="cpy">
  	<xsl:value-of select="."/>
  </xsl:template>

  <xsl:template match="*" mode="cpy">
  	<xsl:choose>
  		<xsl:when test="$voice != ''">
  			<ssml:voice name="{$voice}">
    			<xsl:copy-of select="."/>
    		</ssml:voice>		
  		</xsl:when>
  		<xsl:otherwise>
  			<xsl:copy-of select="."/>
  		</xsl:otherwise>
  	</xsl:choose>
  </xsl:template>

</xsl:stylesheet>