<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xs="http://www.w3.org/2001/XMLSchema"
    xmlns:ssml="http://www.w3.org/2001/10/synthesis"
    version="2.0">
    
    <!--  the SSML needs to be serialized because of the starting \voice command -->
    <xsl:output method="text" omit-xml-declaration="yes" indent="no"/> 
    
    <xsl:param name="voice" select="''"/>
    <xsl:param name="ending-mark"/>
    
    <xsl:variable name="end">
    	<ssml:break time="250ms"/>
    	<ssml:mark name="{$ending-mark}"/>
    </xsl:variable>
    
    <xsl:template match="*">
    	<xsl:if test="$voice != ''">
    		<xsl:value-of select="concat('\voice{', $voice, '}')"/>
    	</xsl:if>
    	<xsl:apply-templates mode="serialize" select="."/>
    	<xsl:apply-templates mode="serialize" select="$end"/>
  	</xsl:template>

	<xsl:template match="text()" mode="serialize">
		<xsl:value-of select="."/>
	</xsl:template>
	
	<xsl:template match="ssml:token" mode="serialize" priority="3">
		<xsl:apply-templates mode="serialize" select="node()"/>
	</xsl:template>
	
	<xsl:template match="ssml:mark" mode="serialize" priority="3">
		<!--  This ugly hack is necessary because Acapela can't handle '_' in mark names -->
		<xsl:variable name="new-name" select="replace(@name, '_', 'ZZZ')"/>
		<xsl:value-of select="concat('&lt;mark name=&quot;', $new-name, '&quot;/>')"/>
	</xsl:template>
	
	<xsl:template match="ssml:*" mode="serialize" priority="2">
		<xsl:value-of select="concat('&lt;', local-name())"/>
		<xsl:apply-templates select="@*" mode="serialize"/>
		<xsl:value-of select="'>'"/>
		<xsl:apply-templates mode="serialize" select="node()"/>
		<xsl:value-of select="concat('&lt;/', local-name(), '>')"/>
	</xsl:template>
	
	<xsl:template match="*" mode="serialize" priority="1">
		<xsl:apply-templates mode="serialize" select="node()"/>
	</xsl:template>
	
	<xsl:template match="@*" mode="serialize">
		<xsl:value-of select="concat(' ', local-name(), '=&quot;', ., '&quot;')"/>
	</xsl:template>

</xsl:stylesheet>