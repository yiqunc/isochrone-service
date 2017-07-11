package com.nearbit.common;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import org.json.simple.parser.*;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AppConfig {
	static final Logger LOGGER = LoggerFactory.getLogger(AppConfig.class);
	private static JSONObject appconfig;
	
	public static void loadConfig() {
		JSONParser p = new JSONParser();
		try {
			
			LOGGER.info("AppConfigFilePath: {}",AppConfig.class.getResource("/connections.properties").getPath());
			appconfig = (JSONObject)p.parse(new FileReader(AppConfig.class.getResource("/connections.properties").getPath()));

			} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static String getString(String key){
		
		return appconfig.get(key).toString();
		
	}
	
}
