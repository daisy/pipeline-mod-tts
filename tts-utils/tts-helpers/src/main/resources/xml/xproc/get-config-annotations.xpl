<p:declare-step type="px:get-config-annotations" version="1.0" name="main"
		xmlns:p="http://www.w3.org/ns/xproc"
		xmlns:px="http://www.daisy.org/ns/pipeline/xproc"
		xmlns:xml="http://www.w3.org/XML/1998/namespace"
		xmlns:d="http://www.daisy.org/ns/pipeline/data"
		exclude-inline-prefixes="#all">

  <p:input port="config" primary="true"/>
  <p:output port="result" primary="true" sequence="true">
    <p:pipe port="result" step="external-annotations"/>
    <p:pipe port="result" step="embedded-annotations"/>
  </p:output>

  <p:option name="content-type"/>

  <p:import href="http://www.daisy.org/pipeline/modules/common-utils/library.xpl"/>

  <p:for-each name="external-annotations">
    <p:output port="result" primary="true"/>
    <p:iteration-source select="//*[local-name()='annotations' and @href and @type=$content-type]"/>
    <p:variable name="href" select="/*/@href"/>
    <p:load name="load">
      <p:with-option name="href" select="resolve-uri($href, base-uri(/*))"/>
    </p:load>
    <px:message>
      <p:with-option name="message" select="concat($href, ' loaded')"/>
    </px:message>
  </p:for-each>

  <p:for-each name="embedded-annotations">
    <p:output port="result" primary="true"/>
    <p:iteration-source select="//*[local-name()='annotations' and not(@href) and @type=$content-type]/*">
      <p:pipe port="config" step="main"/>
    </p:iteration-source>
    <p:identity/>
  </p:for-each>

</p:declare-step>
