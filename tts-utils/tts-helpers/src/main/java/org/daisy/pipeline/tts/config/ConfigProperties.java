package org.daisy.pipeline.tts.config;

import java.util.Map;

public interface ConfigProperties {

	public Map<String, String> getDynamicProperties();

	public Map<String, String> getStaticProperties();

	public Map<String, String> getAllProperties();

}
