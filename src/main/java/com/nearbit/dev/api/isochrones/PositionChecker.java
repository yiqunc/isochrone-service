package com.nearbit.dev.api.isochrones;
import java.io.IOException;
import org.geotools.data.DataSourceException;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureImpl;
import org.geotools.geometry.jts.WKTReader2;
import org.json.JSONException;
import org.json.JSONObject;
import org.opengis.feature.simple.SimpleFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.io.ParseException;
import com.nearbit.common.AppConfig;
import com.nearbit.common.PostgresDataStore;

	
public class PositionChecker {
	static final Logger LOGGER = LoggerFactory.getLogger(PositionChecker.class);
	
	private static DefaultFeatureCollection prj_epsg_codes;
	
	public static void init(){
		
		
		if(prj_epsg_codes==null){
			
			prj_epsg_codes =  new DefaultFeatureCollection();
			
			PostgresDataStore pgDS = new PostgresDataStore();
			try {

				SimpleFeatureSource sourceEPSG = pgDS.getFeatureSource(AppConfig
						.getString("constantLAYERNAME_PROJECTION_EPSG_CODE"));
				
				SimpleFeatureCollection fc = sourceEPSG.getFeatures();
				 
				SimpleFeatureIterator iterator = fc.features();
				
				while (iterator.hasNext()) 
				{
					prj_epsg_codes.add((SimpleFeature)iterator.next());
				}
				iterator.close();
				
			} catch (DataSourceException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} finally{
				pgDS.dispose();
			}
			
		}
	}
	
	public static JSONObject check(Geometry point){
		return check(point.getCoordinate().y, point.getCoordinate().x);
	}
	
	public static JSONObject check(double lat, double lng){
		
		JSONObject output=new JSONObject();
		boolean containFlag = false; 
		//test if input location is within any existing network coverage.
		Geometry point = null;
		
		WKTReader2 wkt = new WKTReader2();
		String s ="POINT("+lng+" "+lat+")";
		try {
			point = wkt.read(s);
		} catch (ParseException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		} 
		
		SimpleFeatureIterator iterator = prj_epsg_codes.features();
		
		while (iterator.hasNext()) 
		{

			SimpleFeatureImpl SFIbbox =  (SimpleFeatureImpl)iterator.next();
			
			Polygon bbox = (Polygon) SFIbbox.getDefaultGeometry();
		    
			if(bbox.intersects(point)){
				containFlag = true;
				try {
					output.put("status",  0);
					output.put("errdesc", "");
					output.put("epsg_code", SFIbbox.getAttribute("epsg_code"));
					output.put("epsg_num", SFIbbox.getAttribute("epsg_num"));
					output.put("country_code", SFIbbox.getAttribute("country_code"));
					
				} catch (JSONException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				break;
			}
		
		}
		iterator.close();
		
		if(!containFlag){
			try {
				output.put("status",  1);
				output.put("errdesc", "this location is not currently supported for service area generation");
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		return output;
	}
	
	public static String getEPSGCode (Geometry point){
		
		JSONObject result = check(point);
		
		try {
			if(result.getInt("status")==0){
				
				return result.getString("epsg_code");
			}
				
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return "";
	}
	
	public static String getCountryCode (Geometry point){
		
		JSONObject result = check(point);
		
		try {
			if(result.getInt("status")==0){
				
				return result.getString("country_code");
			}
				
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return "";
	}
	
	public static String getCountryCode (double lat, double lng){
		
		JSONObject result = check(lat, lng);
		
		try {
			if(result.getInt("status")==0){
				
				return result.getString("country_code");
			}
				
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return "";
	}
}
