package com.coinhitchhiker.vbtrader.trader.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class PropertyMapHandler {
	protected static final Logger LOGGER = LoggerFactory.getLogger(PropertyMapHandler.class);

	public String getString(Map<String, String> propertyMap, String key) {
		if (propertyMap.get(key) == null) {
			LOGGER.error("Not found property value by given key [" + key + "]");
			return null;
		}
		return propertyMap.get(key);
	}

	public Map<Object, Object> getMap(Map<String, Map<Object, Object>> propertyMap, String key) {
		if (propertyMap.get(key) == null) {
			LOGGER.error("Not found property value by given key [" + key + "]");
			return null;
		}
		return propertyMap.get(key);
	}

	public List<Object> getList(Map<String, List<Object>> propertyMap, String key) {
		if (propertyMap.get(key) == null) {
			LOGGER.error("Not found property value by given key [" + key + "]");
			return null;
		}
		return propertyMap.get(key);
	}

	public Boolean getBoolean(Map<String, Boolean> propertyMap, String key) {
		if (propertyMap.get(key) == null) {
			LOGGER.error("Not found property value by given key [" + key + "]");
			return null;
		}
		return propertyMap.get(key);
	}

	public Double getDouble(Map<String, Double> propertyMap, String key) {
		if (propertyMap.get(key) == null) {
			LOGGER.error("Not found property value by given key [" + key + "]");
			return null;
		}
		return propertyMap.get(key);
	}

}
