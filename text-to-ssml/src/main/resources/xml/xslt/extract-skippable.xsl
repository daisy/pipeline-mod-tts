<xsl:stylesheet xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
		xmlns:ssml="http://www.w3.org/2001/10/synthesis"
		exclude-result-prefixes="#all"
		version="2.0">

  <!-- must have the same value as in the Java part -->
  <xsl:variable name="mark-delimiter" select="'___'"/>
  <xsl:variable name="skippable-ids" select="collection()[2]"/>

  <xsl:key name="skippable" match="*[@id]" use="@id"/>

  <xsl:template match="/*" priority="2">
    <!-- Primary output port: -->
    <xsl:copy>
      <xsl:copy-of select="@*"/>
      <xsl:for-each select="/*/ssml:s">
	<xsl:if test="count(*[@id]) &gt; 1 or
		      not(descendant::*[@id and key('skippable', @id, $skippable-ids)])">
	  <!-- Sentences that contains only a standlone skippable element are not copied
	     because it entails creating a floating SSML mark that refers to nothing. Yet
	     it doesn't prevent such a sentence from being synthesized. If this sentence
	     were synthesized, an audio-map entry will be created eventually and that
	     would make it impossible to create nodes with @smilref (or equivalent) inside
	     the sentence. -->
	  <xsl:copy>
	    <xsl:copy-of select="@*"/>
	    <xsl:for-each select="node()">
	      <xsl:variable name="under-sent" select="current()"/>
	      <xsl:variable name="skippable" select="current()/descendant-or-self::*[@id and
						     key('skippable', @id, $skippable-ids)][1]"/>
	      <xsl:choose>
		<xsl:when test="not($skippable)">
		  <xsl:copy-of select="$under-sent"/>
		</xsl:when>
		<xsl:otherwise>
		  <xsl:variable name="clip-before" select="$under-sent/preceding-sibling::*[@id][1]/@id"/>
		  <xsl:variable name="clip-after" select="$under-sent/following-sibling::*[@id][1]/@id"/>
		    <!-- The skippable element is replaced with a mark delimiting the end
		         of the previous element and the beginning of the next one. The
		         skippable elements are copied to a separate document. -->
		    <ssml:mark name="{concat($clip-before, $mark-delimiter, $clip-after)}"/>
		</xsl:otherwise>
	      </xsl:choose>
	    </xsl:for-each>
	  </xsl:copy>
	</xsl:if>
      </xsl:for-each>
    </xsl:copy>

    <!-- Secondary output port: -->
    <xsl:result-document href="skippable-only.xml">
      <ssml:speak version="1.1">
	<xsl:for-each select="$skippable-ids/*/*">
	  <xsl:variable name="in-doc" select="key('skippable', @id, collection()[1])"/>
	  <xsl:variable name="under-sent" select="$in-doc/ancestor-or-self::*[parent::ssml:s][1]"/>
	  <xsl:if test="$under-sent">
	    <ssml:s id="{concat('holder-of-', @id)}">
	      <xsl:copy-of select="$under-sent/../@*[name() != 'id']"/> <!-- including CSS if any -->
	      <xsl:copy-of select="$under-sent"/>
	      <!-- we are copying the whole tree because there might be annotations
	           inserted around the skippable element. -->
	    </ssml:s>
	  </xsl:if>
	</xsl:for-each>
      </ssml:speak>
    </xsl:result-document>
  </xsl:template>

</xsl:stylesheet>

