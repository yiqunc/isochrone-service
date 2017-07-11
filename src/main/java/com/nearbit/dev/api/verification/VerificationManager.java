package com.nearbit.dev.api.verification;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.nearbit.dev.api.Controller;
import com.nearbit.dev.api.DbAccess;
import spark.Request;

public class VerificationManager {
	static final Logger LOGGER = LoggerFactory.getLogger(Controller.class);
	
	
	  static public JSONObject verifyAPIAuthorisation(Request request, String apiServiceName) throws Exception {
			synchronized (request.session()) {
				
				JSONObject output = new JSONObject();
				
				String key  = request.queryParams("key");
				if(key==null){
					output.put("status", 1);
					output.put("errdesc", "API key is missing. ");
					
					return output;
				}
				
				JSONObject check = DbAccess.verifyAPIAuthorisation(key, apiServiceName);
				if(check!=null && check.getInt("usr_id")>0 &&check.getInt("api_id")>0)
				{
					output.put("status", 0);
					output.put("errdesc", "");
					
					//update api_log
					DbAccess.logAPIRequest(check.getInt("usr_id"), check.getInt("api_id"), request.raw().getRemoteAddr(), request.url(), request.raw().getParameterMap().toString());
					
					return output;
				}
				else
				{
					output.put("status", 1);
					output.put("errdesc", "A valid API key is required. ");
					
					return output;
				}
				
				
			}
	  }
}
