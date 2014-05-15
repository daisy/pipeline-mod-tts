<p:declare-step version="1.0" type="px:create-audio-fileset" name="main"
		xmlns:p="http://www.w3.org/ns/xproc"
		xmlns:px="http://www.daisy.org/ns/pipeline/xproc"
		xmlns:cx="http://xmlcalabash.com/ns/extensions"
		xmlns:d="http://www.daisy.org/ns/pipeline/data">

  <p:import href="http://www.daisy.org/pipeline/modules/fileset-utils/library.xpl"/>

  <p:input port="source" primary="true" /> <!-- audio clips -->
  <p:output port="fileset.out" sequence="false" primary="true"/>  <!-- fileset of audio files -->

  <p:option name="output-dir" required="true"/>
  <p:option name="audio-relative-dir" required="true"/>
  <p:variable name="audio-dir" select="concat($output-dir, $audio-relative-dir)">
    <p:empty/>
  </p:variable>

  <!-- Iterate over the resulting SMIL files to add them to a new fileset. -->
  <!-- Add the sound files to the fileset as well. -->
  <p:for-each name="for-each-audio">
    <!-- identical audio files should be adjacent in the clip list -->
    <p:iteration-source select="//d:clip[not(preceding::d:clip[1]) or (@src != preceding::d:clip[1]/@src)]"/>
    <p:variable name="former-src" select="/*/@src">
      <p:pipe port="current" step="for-each-audio"/>
    </p:variable>
    <p:variable name="new-src" select="concat($audio-dir, tokenize($former-src, '[\\/]+')[last()])"/>
    <px:fileset-create/>
    <!-- TODO: deal with other format than audio/mpeg -->
    <px:fileset-add-entry first="true" media-type="audio/mpeg">
      <p:with-option name="href" select="$new-src" />
      <p:with-option name="original-href" select="$former-src" />
    </px:fileset-add-entry>
  </p:for-each>
  <px:fileset-join/>

</p:declare-step>
