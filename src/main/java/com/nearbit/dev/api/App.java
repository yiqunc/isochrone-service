package com.nearbit.dev.api;

import static spark.Spark.*;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.nearbit.common.*;

import spark.servlet.*;
import spark.*;

import com.nearbit.common.isochrones.performance.TestingController;
import com.nearbit.dev.api.isochrones.PositionChecker;

public class App implements SparkApplication {

	static final Logger LOGGER = LoggerFactory.getLogger(App.class);
	
	/*
	 * These application exposes its service APIs in two ways: 
	 * (1) /dev/* are for development and test purposes and not ready for general public use.   
	 * (2) /stable/* are for public use and need an API key to access.
	 * */
	
	//config for running Spark on external Web Server, e.g. Jetty (which is not the Spark embedded Jetty version)
	public void init() {
		
		//must have this line, otherwise, the OasAnalysisRiskAra result published in geoserver will get lon-lat reversed, i.e. lat-lon
		//ref: http://docs.geotools.org/latest/userguide/faq.html search 'org.geotools.referencing.forceXY'
		System.setProperty("org.geotools.referencing.forceXY", "true");
		//load application configurations
		AppConfig.loadConfig();
		//GeoServerService.init();
		DbAccess.initPostgresDataSource();
		
		//init position checker
		PositionChecker.init();
	
		//returning version info
		get(new Route("/stable/verinfo") {
			        @Override
			        public Object handle(Request request, Response response) {
			        	
			    		JSONObject info = new JSONObject();
			    		
			    		try {
							info.put("version", AppConfig.getString("ver"));
							info.put("lastupdate", AppConfig.getString("lastupdate"));
						} catch (JSONException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
			    		
			           return info.toString();
			        }
			     });
		

		//this endpoint support both get and post 
		get(new Route("/stable/servicearea") {
	        @Override
	        public Object handle(Request request, Response response) {
	        	
	    		JSONObject info = new JSONObject();
	    		
	    		try {
	    			
	    			info = Controller.runServiceArea(request, response);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					
					try {
						info.put("status", 1);
						info.put("errdesc", e.getMessage());
					} catch (JSONException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					
				}
	    		
	           return info;
	        }
	     });
		
		post(new Route("/stable/servicearea") {
	        @Override
	        public Object handle(Request request, Response response) {
	        	
	    		JSONObject info = new JSONObject();
	    		
	    		try {
	    			
	    			info = Controller.runServiceArea(request, response);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					
					try {
						info.put("status", 1);
						info.put("errdesc", e.getMessage());
					} catch (JSONException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					
				}
	    		
	           return info;
	        }
	     });
		
		
		get(new Route("/stable/performance/randomseeds") {
	        @Override
	        public Object handle(Request request, Response response) {
	        	
	        	
	        	JSONObject info = new JSONObject();
	    		
	    		try {
	    			
	    			int num = 500;
	    			if(request.queryParams("num")!=null){
	    				try{
	    					num = Integer.parseInt(request.queryParams("num"));
	    				}catch (Exception e)
	    				{
	    					num = 500;
	    				}
	    			}
	    			if(num<=0 || num>2000) num=500;
	    			
	    			TestingController performanceTC = new TestingController();
	    			
		    		info.put("data", performanceTC.getRandomNPoints(num));
		    		info.put("status", 0);
		    		
		    		
				} catch (Exception e) {
					// TODO Auto-generated catch block
					
					try {
						info.put("status", 1);
						info.put("errdesc", e.getMessage());
					} catch (JSONException e1) {
						// TODO Auto-generated catch block
						e1.printStackTrace();
					}
					
				}
	    		
	           return info;
	        }
	     });
	}
}