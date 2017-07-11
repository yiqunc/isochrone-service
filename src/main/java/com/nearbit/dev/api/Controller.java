package com.nearbit.dev.api;


import spark.*;

import java.io.File;
import org.json.JSONObject;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.nearbit.common.AppConfig;
import com.nearbit.common.GeometryUtils;
import com.nearbit.dev.api.isochrones.PositionChecker;
import com.nearbit.dev.api.isochrones.IsochronesGenerator;
import com.nearbit.dev.api.verification.VerificationManager;

public class Controller {
		
	static final Logger LOGGER = LoggerFactory.getLogger(Controller.class);

    static public JSONObject runServiceArea(Request request, Response response) throws Exception {
		synchronized (request.session()) {
			
			JSONObject output = new JSONObject();
			JSONObject options = new JSONObject();
			
			
			JSONObject apiCheck = VerificationManager.verifyAPIAuthorisation(request, "ServiceArea");
			if(apiCheck == null || apiCheck.getInt("status") > 0)
			{
				return apiCheck;
			}
			
			//use which way to compute service area between coordarr and radiusarr
			//if set combotype='simple', each seed point will be assigned with one corresponding radius value defined the radiusarr
			//if set combotype='composite', each seed point will be assigned with all radius values defined the radiusarr
			
			String combotype = "simple";
			if(request.queryParams("combotype")!=null && !request.queryParams("combotype").equalsIgnoreCase("simple")){
				combotype = "composite";
			}
			
			JSONArray coordarr = new JSONArray();
			JSONArray coordarrRaw = null;
			boolean coordsValidFlag = true;
			try
			{
				coordarrRaw = new JSONArray(request.queryParams("coordarr").trim());
			}catch(Exception e)
			{
				coordsValidFlag = false;
			}
			
			if(coordarrRaw!=null && coordarrRaw.length()==0){
				coordsValidFlag = false;
			}else
			{
				//check if all elements in coords are valid
				try{
					for(int i=0;i<coordarrRaw.length();i++){
						JSONObject coord = coordarrRaw.getJSONObject(i);
						String strLatLngString = coord.getDouble("lat")+","+coord.getDouble("lng");
						if(!GeometryUtils.checkValidLatLngString(strLatLngString)){
							coordsValidFlag = false;
							break;
						}
					}
				}
				catch(Exception e){
					coordsValidFlag = false;
				}
			}
			
			if(!coordsValidFlag){
				output.put("status", 1);
				output.put("errdesc", "parameter coordarr is missing or invalid. coordarr is a JSON array contains valid points coordinates. e.g. [{\"lat\":-37.0124, \"lng\":145.1244},{\"lat\":-37.1025, \"lng\":143.2288}]");
				return output;
			}
			
			//make sure all coords are with the same country, not necessarily with the same ESPG code 
			//TODO
			coordsValidFlag = true;
			String country_code = "";
			for(int i=0;i<coordarrRaw.length();i++){
				JSONObject coord = coordarrRaw.getJSONObject(i);
				if(i==0) {
					country_code = PositionChecker.getCountryCode(coord.getDouble("lat"), coord.getDouble("lng"));
				}
				else
				{
					if(!country_code.equalsIgnoreCase(PositionChecker.getCountryCode(coord.getDouble("lat"), coord.getDouble("lng")))){
						coordsValidFlag = false;
						break;
					}
				}
			}
			
			if(!coordsValidFlag){
				output.put("status", 1);
				output.put("errdesc", "all coordinates need to be in the same country.");
				return output;
			}
			
			if(country_code == ""){
				output.put("status", 1);
				output.put("errdesc", "country_code cannot be decided for the given coordinates.");
				return output;
			}
			
			String country_code_prefix = ""; //empty string stands for australia, others are like nz_ cn_
			if(!country_code.equalsIgnoreCase("au")){
				country_code_prefix = country_code+"_";
			}
			
			int maxRadius = 10000;
			int minRadius = 10;
			int defaultRadius = 500;
			//check if an array of radius is provided
			JSONArray radiusarr = new JSONArray();
			JSONArray radiusarrRaw = null;
			try
			{
				radiusarrRaw = new JSONArray(request.queryParams("radiusarr").trim());
				if(radiusarrRaw != null && radiusarrRaw.length() > 0){
					
					if(combotype == "simple")
					{
						//for 'simple' combotype, we need to enforce the length of radiusarrRaw equals to the lengths of coords
						if(radiusarrRaw.length()<coordarrRaw.length()){
							
							//assign the rest missing radius values with the last radius value 
							double lastRadius = defaultRadius; 
							try{		
								lastRadius = radiusarrRaw.getDouble(radiusarrRaw.length()-1);
							}catch(Exception ex){
								lastRadius = defaultRadius; 
							}
							
							int rawArrLength = radiusarrRaw.length();
							for(int i=0;i<coordarrRaw.length()-rawArrLength;i++){
								radiusarrRaw.put(lastRadius);
							}
						}
						
						//populate radiusarr
						//make sure radiusarrRaw elements are in fair range
						for(int i=0;i<radiusarrRaw.length();i++){
							double v = defaultRadius; 
							try{		
								v = radiusarrRaw.getDouble(i);
							}catch(Exception ex){
								v = defaultRadius;
							}
							if (v>maxRadius || v<minRadius) v = defaultRadius;		
							radiusarr.put(v);
						}
						
						//populate coordsarr 
						for(int i=0;i<coordarrRaw.length();i++){
							coordarr.put(coordarrRaw.getJSONObject(i));
						}
					}
					else{
						//for 'composite' combotype,
						int radiusRawLength = radiusarrRaw.length();
						int coordsRawLength = coordarrRaw.length();
						//populate coordsarr 
						for(int i = 0;i<radiusRawLength;i++){
							for(int j=0;j<coordsRawLength;j++){
								coordarr.put(coordarrRaw.getJSONObject(j));
							}
						}
						//populate radiusarr 
						//make sure radiusarrRaw elements are in fair range
						for(int i = 0;i<radiusRawLength;i++){
							for(int j=0;j<coordsRawLength;j++){
								
								double v = defaultRadius; 
								try{		
									v = radiusarrRaw.getDouble(i);
								}catch(Exception ex){
									v = defaultRadius;
								}
								if (v>maxRadius || v<minRadius) v = defaultRadius;		
								radiusarr.put(v);
								
							}
						}
					}
					
				}
				else{
					LOGGER.info("=== radiusarr is not provided, defaultRadius applied");
				}
			}catch(Exception e)
			{
				LOGGER.info("=== radiusarr is not provided, defaultRadius applied");
			}
			
			
			//if radiusarr is not provided, fill it with duplicated radius values and enforce combotype = simple
			if(radiusarr.length()==0){
				combotype = "simple";
				coordarr = new JSONArray();
				radiusarrRaw = new JSONArray();
				for(int i=0;i<coordarrRaw.length();i++){
					coordarr.put(coordarrRaw.getJSONObject(i));
					radiusarr.put(defaultRadius);
					radiusarrRaw.put(defaultRadius);
				}
			}

			int bufsize = 50;
			if(request.queryParams("bufsize")!=null){
				try{
					bufsize = Integer.parseInt(request.queryParams("bufsize"));
				}catch (Exception e)
				{
					bufsize = 50;
				}
			}
			
			if (bufsize>200 || bufsize<5) bufsize = 50;
			
			String traveltype = "walk";
			if(request.queryParams("traveltype")!=null && !request.queryParams("traveltype").equalsIgnoreCase("walk")){
				traveltype = "drive";
			}
			
			boolean removeholes = true;
			if(request.queryParams("removeholes")!=null && !request.queryParams("removeholes").equalsIgnoreCase("true")){
				removeholes = false;
			}
			
			
			boolean returnline = false;
			if(request.queryParams("returnline")!=null && !request.queryParams("returnline").equalsIgnoreCase("false")){
				returnline = true;
			}
			
			boolean returnpoint = false;
			if(request.queryParams("returnpoint")!=null && !request.queryParams("returnpoint").equalsIgnoreCase("false")){
				returnpoint = true;
			}
			
			boolean returnpolygon = true;
			if(request.queryParams("returnpolygon")!=null && !request.queryParams("returnpolygon").equalsIgnoreCase("true")){
				returnpolygon = false;
			}
			
			String polygondetaillevel = "high";
			if(request.queryParams("polygondetaillevel")!=null ){
				polygondetaillevel = request.queryParams("polygondetaillevel").toLowerCase();
			}
			
			if(!polygondetaillevel.equalsIgnoreCase("mid") && !polygondetaillevel.equalsIgnoreCase("low"))
			{
				polygondetaillevel = "high";
			}
			
			//the threshold value for concavehull method, it is only used when 'polygondetaillevel'=low 
			int concavehullthreshold = 200;
			if(request.queryParams("concavehullthreshold")!=null){
				try{
					concavehullthreshold = Integer.parseInt(request.queryParams("concavehullthreshold"));
				}catch (Exception e)
				{
					concavehullthreshold = 200;
				}
			}
			
			if (concavehullthreshold>500 || concavehullthreshold<50) concavehullthreshold = 200;
			
			
			boolean recperformance = false;
			if(request.queryParams("recperformance")!=null && !request.queryParams("recperformance").equalsIgnoreCase("false")){
				recperformance = true;
			}
			
			String format = "geojson";
			if(request.queryParams("format")!=null && !request.queryParams("format").equalsIgnoreCase("geojson")){
				format = "shp";
			}
			
			String networklayername = country_code_prefix+AppConfig.getString("constantLAYERNAME_OSM_NETWORK_"+traveltype);
			
			options.put("networklayername", networklayername);
			options.put("radiusarr", radiusarr);
			options.put("radiusarrraw", radiusarrRaw);
			options.put("bufsize", bufsize);
			options.put("traveltype", traveltype);
			options.put("coordarr", coordarr);
			options.put("coordarrraw", coordarrRaw);
			options.put("removeholes", removeholes);
			options.put("returnline", returnline);
			options.put("returnpoint", returnpoint);
			options.put("returnpolygon", returnpolygon);
			options.put("recperformance", recperformance);
			options.put("combotype", combotype);
			options.put("format", format);
			options.put("polygondetaillevel", polygondetaillevel);
			options.put("concavehullthreshold", concavehullthreshold);
			options.put("tmpOutputFolderPath", getOutputFolderPath("tmpout",request));
			options.put("tmpOutputUrl", getBaseUrl(request)+"/tmpout/");
			
			output.put("status", 0);
			output.put("errdesc", "");
			output.put("version", AppConfig.getString("ver"));
			output.put("lastupdate", AppConfig.getString("lastupdate"));
			output.put("data", IsochronesGenerator.exec(options));

			return output;
		}
	}
	    /**
	     * Get output folder path string if target output folder doesn't exist, create a new one
	     * @param dirname
	     * @param request
	     * @return output folder path string for current login user
	     */
	public static String getOutputFolderPath(String dirname, Request request){
	    	
	    	File f = new File(request.raw().getServletContext().getRealPath("/")+"/"+dirname);
        	if(!f.exists()){
        		boolean flag = f.mkdirs();
        	}
        	
        	return f.getAbsolutePath();
	    }
	
	public static String getBaseUrl(Request request) {
	    String scheme = request.raw().getScheme() + "://";
	    String serverName = request.raw().getServerName();
	    String serverPort = (request.raw().getServerPort() == 80) ? "" : ":" + request.raw().getServerPort();
	    String contextPath = request.raw().getContextPath();
	    return scheme + serverName + serverPort + contextPath;
	  }
   
}
