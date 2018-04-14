package com.nearbit.dev.api.isochrones;

import java.io.File;
import java.sql.PreparedStatement;
import java.text.DecimalFormat;
import java.util.ArrayList;
import org.apache.commons.io.FileUtils;
import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureImpl;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.WKTReader2;
import org.geotools.referencing.CRS;
import org.geotools.geojson.feature.FeatureJSON;
import org.geotools.geojson.geom.GeometryJSON;
import org.json.JSONArray;
import org.json.JSONObject;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.Name;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Polygon;
import com.nearbit.common.Zip;
import com.nearbit.dev.api.DbAccess;
import com.nearbit.common.PostgresDataStore;
import com.nearbit.common.ShapeFileUtils;

public class IsochronesGenerator {

	static final Logger LOGGER = LoggerFactory.getLogger(IsochronesGenerator.class);

	public static JSONObject exec(JSONObject options) throws Exception {

		DecimalFormat df = new DecimalFormat("#.00");
		long execStart = System.currentTimeMillis();

		JSONObject output = new JSONObject();

		// parameters
		double bufsize = options.getDouble("bufsize");
		String tmpOutputFolderPath = options.getString("tmpOutputFolderPath");
		String tmpOutputUrl = options.getString("tmpOutputUrl");
		String networklayername = options.getString("networklayername");
		String traveltype = options.getString("traveltype").toLowerCase();
		JSONArray coordarr = options.getJSONArray("coordarr");
		JSONArray radiusarr = options.getJSONArray("radiusarr");
		JSONArray coordarrRaw = options.getJSONArray("coordarrraw");
		JSONArray radiusarrRaw = options.getJSONArray("radiusarrraw");
		boolean removeholes = options.getBoolean("removeholes");
		boolean returnline = options.getBoolean("returnline");
		boolean returnpoint = options.getBoolean("returnpoint");
		boolean returnpolygon = options.getBoolean("returnpolygon");
		String combotype = options.getString("combotype").toLowerCase();
		String format = options.getString("format");
		String polygondetaillevel = options.getString("polygondetaillevel");
		int concavehullthreshold = options.getInt("concavehullthreshold");
		
		boolean recperformance = options.getBoolean("recperformance");//record performace

		final SimpleFeatureType PointTYPE = DataUtilities.createType("location", "geom:Point,bufsize:Double,id:String");
		WKTReader2 wkt = new WKTReader2();

		DefaultFeatureCollection pointsDFC = new DefaultFeatureCollection();

		for (int i = 0; i < coordarr.length(); i++) {
			JSONObject p = coordarr.getJSONObject(i);
			String s = "POINT(" + p.getDouble("lng") + " " + p.getDouble("lat") + ")";
			String id = "";
			try{
				id = p.getString("id");
			}
			catch(Exception e){
				
			}
			if(id==null){id = "";}
			
			Geometry g = wkt.read(s);

			pointsDFC.add(SimpleFeatureBuilder.build(PointTYPE, new Object[] { g, bufsize, id}, Integer.toString(i)));
		}

		SimpleFeatureCollection pointsFC = DataUtilities.simple(pointsDFC);

		PostgresDataStore pgDS = new PostgresDataStore();

		// get walk or drive network data
		SimpleFeatureType schemaNetwork = pgDS.getDataStore().getSchema(networklayername);
		SimpleFeatureSource sourceNetwork = pgDS.getFeatureSource(networklayername);
		Name geomNameNetwork = schemaNetwork.getGeometryDescriptor().getName();

		// guid_polygon.shp is created for each type of services, an servicetype
		// column will be created to indicate which service type this buffer
		// geom belongs to
		String guid_servicearea = java.util.UUID.randomUUID().toString().replaceAll("-", "");
		
		File f_netbuf = new File(String.format("%s%s%s", tmpOutputFolderPath, File.separator, guid_servicearea));
		if (f_netbuf.exists()) {
			FileUtils.deleteDirectory(f_netbuf);
		}
		//create shpfile dir only necessary 
		if(format.equalsIgnoreCase("shp")) f_netbuf.mkdirs();
		
		String outputShpFilePath_netbuf = f_netbuf.getAbsolutePath() + File.separator + guid_servicearea+"_polygon.shp";
		
		String guid_roadsegs = java.util.UUID.randomUUID().toString().replaceAll("-", "");;
		File f_roadsegs = new File(String.format("%s%s%s", tmpOutputFolderPath, File.separator, guid_roadsegs));
		if (f_roadsegs.exists()) {
			FileUtils.deleteDirectory(f_roadsegs);
		}
		//create shpfile dir only necessary 
		if(format.equalsIgnoreCase("shp")) f_roadsegs.mkdirs();
		
		String outputShpFilePath_roadsegs = f_roadsegs.getAbsolutePath() + File.separator + guid_roadsegs+"_line.shp";
		
		
		String guid_roadnodes = java.util.UUID.randomUUID().toString().replaceAll("-", "");;
		File f_roadnodes = new File(String.format("%s%s%s", tmpOutputFolderPath, File.separator, guid_roadnodes));
		if (f_roadnodes.exists()) {
			FileUtils.deleteDirectory(f_roadnodes);
		}
		//create shpfile dir only necessary 
		if(format.equalsIgnoreCase("shp")) f_roadnodes.mkdirs();
		
		String outputShpFilePath_roadnodes = f_roadnodes.getAbsolutePath() + File.separator + guid_roadnodes+"_point.shp";

		/////// prepare netbuff output shp file structure
		SimpleFeatureTypeBuilder stb_netbuf = new SimpleFeatureTypeBuilder();

		stb_netbuf.setName("netbuf");
		stb_netbuf.add("the_geom", Polygon.class);
		stb_netbuf.setDefaultGeometry("the_geom");
		stb_netbuf.add("id", String.class);
		stb_netbuf.add("radius", Double.class);
		stb_netbuf.add("bufsize", Double.class);
		stb_netbuf.add("seedcoord", String.class);
		stb_netbuf.add("calcstatus", String.class);
		stb_netbuf.add("roadarea", Double.class);

		// now we have a new SimpleFeatureType which suits for shp file
		// exporting as well
		SimpleFeatureType outputFeatureType_netbuf = stb_netbuf.buildFeatureType();
		// we need create a brand new featurecollection to hold the result
		DefaultFeatureCollection outfc_buffers = new DefaultFeatureCollection();
		// create a FeatureBuilder to build features
		SimpleFeatureBuilder outputFeatureBuilder_netbuf = new SimpleFeatureBuilder(outputFeatureType_netbuf);

		// each point can be assigned with a different buffer size
		ArrayList<Double> distanceArr = new ArrayList<Double>();
		for (int i = 0; i < radiusarr.length(); i++) {
			distanceArr.add(radiusarr.getDouble(i));
		}

		com.nearbit.common.isochrones.IsochronesBatch nbb = new com.nearbit.common.isochrones.IsochronesBatch(
				sourceNetwork, pointsFC, distanceArr, bufsize, polygondetaillevel, concavehullthreshold);

		nbb.createBuffersAdvanced();
		
		SimpleFeatureCollection fcBuffers = nbb.getBuffers();
		SimpleFeatureCollection fcLines = nbb.getRoadLines();
		SimpleFeatureCollection fcNodes = nbb.getRoadNodes();
		JSONArray performanceStatsArr = nbb.getPerformanceStatsArray();
		
		// ref:http://osgeo-org.1560.x6.nabble.com/loss-of-precision-of-features-while-geojson-transformation-td5008928.html
		// give FeatureJSON a property precision to work with
		FeatureJSON fj = new FeatureJSON(new GeometryJSON(6));
		
		if(returnpolygon){
			SimpleFeatureIterator iteratorNB = fcBuffers.features();

			int count = -1;
			while (iteratorNB.hasNext()) {
				count++;

				SimpleFeature outputf_netbuf = outputFeatureBuilder_netbuf.buildFeature(null);

				SimpleFeatureImpl fNB = (SimpleFeatureImpl) iteratorNB.next();

				Polygon gNB = (Polygon) fNB.getDefaultGeometry();
				if (!gNB.isValid()) {
					// skip bad data
					continue;
				}

				if (removeholes) {
					outputf_netbuf.setAttribute("the_geom", gNB.getBoundary().getGeometryN(0));
				} else {
					outputf_netbuf.setAttribute("the_geom", gNB);
				}

				outputf_netbuf.setAttribute("radius", distanceArr.get(count));
				outputf_netbuf.setAttribute("bufsize", bufsize);
				outputf_netbuf.setAttribute("id", fNB.getAttribute("id"));
				outputf_netbuf.setAttribute("seedcoord", fNB.getAttribute("seedcoord"));
				outputf_netbuf.setAttribute("calcstatus", fNB.getAttribute("status"));
				outputf_netbuf.setAttribute("roadarea", df.format(fNB.getAttribute("roadarea")));

				outfc_buffers.add(outputf_netbuf);

			}
			iteratorNB.close();
			
			if(format.equalsIgnoreCase("shp")){
				//create shp file for service area polygon
				File shpFile = new File(outputShpFilePath_netbuf);
				ShapeFileUtils.featuresExportToShapeFile(outputFeatureType_netbuf, outfc_buffers, shpFile, true, CRS.decode("EPSG:4326"));
			 
				String zipfileName = shpFile.getParentFile().getAbsolutePath()+ "_polygon.zip";
				Zip zip = new Zip(zipfileName, shpFile.getParentFile().getAbsolutePath());
				zip.createZip();
				
				//get the url for the zip file
				output.put("shpurl_polygon", tmpOutputUrl+guid_servicearea+"_polygon.zip");
				
				//delete tmp shp folder
				FileUtils.deleteDirectory(shpFile.getParentFile());
			 }
			else{
				output.put("geojson_polygon", new JSONObject(fj.toString(outfc_buffers)));
			 }
		}
		
		
		if(returnline){

			if(format.equalsIgnoreCase("shp")){
				//create shp file for roadsegs
				File shpFile = new File(outputShpFilePath_roadsegs);
				//ShapeFileUtils.featuresExportToShapeFile(outputFeatureType_line, outfc_lines, shpFile, true, CRS.decode("EPSG:4326"));
				ShapeFileUtils.featuresExportToShapeFile(fcLines.getSchema(), fcLines, shpFile, true, CRS.decode("EPSG:4326"));

				String zipfileName = shpFile.getParentFile().getAbsolutePath()+ "_line.zip";
				Zip zip = new Zip(zipfileName, shpFile.getParentFile().getAbsolutePath());
				zip.createZip();
				
				//get the url for the zip file
				output.put("shpurl_line", tmpOutputUrl+guid_roadsegs+"_line.zip");
				
				//delete tmp shp folder
				FileUtils.deleteDirectory(shpFile.getParentFile());
				
			}else
			{
				output.put("geojson_line", new JSONObject(fj.toString(fcLines)));
			}
		}
		
		
		if(returnpoint){
				//output.put("geojson_roadnodes", new JSONObject(fj.toString(fcNodes)));
			if(format.equalsIgnoreCase("shp")){
				//create shp file for roadsegs
				File shpFile = new File(outputShpFilePath_roadnodes);
				//ShapeFileUtils.featuresExportToShapeFile(outputFeatureType_line, outfc_lines, shpFile, true, CRS.decode("EPSG:4326"));
				ShapeFileUtils.featuresExportToShapeFile(fcNodes.getSchema(), fcNodes, shpFile, true, CRS.decode("EPSG:4326"));

				String zipfileName = shpFile.getParentFile().getAbsolutePath()+ "_point.zip";
				Zip zip = new Zip(zipfileName, shpFile.getParentFile().getAbsolutePath());
				zip.createZip();
				
				//get the url for the zip file
				output.put("shpurl_point", tmpOutputUrl+guid_roadnodes+"_point.zip");
				
				//delete tmp shp folder
				FileUtils.deleteDirectory(shpFile.getParentFile());
				
			}else
			{
				output.put("geojson_point", new JSONObject(fj.toString(fcNodes)));
			}
		}
		
		pgDS.dispose();
		
		
		long execEnd = System.currentTimeMillis();
		LOGGER.info("==== API Total Execution time is:{} seconds", df.format((execEnd - execStart) / 1000d));
		
		output.put("coordarr", coordarrRaw);
		output.put("crs", "EPSG:4326");
		output.put("radiusarr", radiusarrRaw);
		output.put("bufsize", bufsize);
		output.put("traveltype", traveltype);
		output.put("removeholes", removeholes);
		output.put("returnline", returnline);
		output.put("returnpoint", returnpoint);
		output.put("returnpolygon", returnpolygon);
		output.put("combotype", combotype);
		output.put("polygondetaillevel", polygondetaillevel);
		output.put("concavehullthreshold", concavehullthreshold);
		output.put("processingtime", df.format((execEnd - execStart) / 1000d));

		//recperformance is for dev debug only, not necessary to expose it
		if(recperformance){
			recPerformanceStats(performanceStatsArr);
		}
		return output;
	}

	public static void recPerformanceStats(JSONArray statsArr) throws Exception {
		
	   	 DbAccess.initBatchConn();
	   	 
	   	 PreparedStatement pst = DbAccess.batchConn.prepareStatement("INSERT INTO api_performance(para_radius, para_bufsize, para_seedcoord, num_rawlink, num_isolink, num_isonode, num_isoleafnode, t_1, t_2, t_3, t_4, t_5, t_6, t_7, t_all) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);");
	   	 
	   	 for(int i=0; i<statsArr.length();i++){
	   		 
	   		 
	   		 double para_radius = statsArr.getJSONObject(i).getDouble("para_radius");
	   		 double para_bufsize = statsArr.getJSONObject(i).getDouble("para_bufsize");
	   		 String para_seedcoord = statsArr.getJSONObject(i).getString("para_seedcoord").trim();
	   		 
	   		 int num_rawlink = statsArr.getJSONObject(i).getInt("num_rawlink");
	   		 int num_isolink = statsArr.getJSONObject(i).getInt("num_isolink");
	   		 int num_isonode = statsArr.getJSONObject(i).getInt("num_isonode");
	   		 int num_isoleafnode = statsArr.getJSONObject(i).getInt("num_isoleafnode");
	   		 
	   		 double t_1 = statsArr.getJSONObject(i).getDouble("t_1"); //BFS processing time
	   		 double t_2 = statsArr.getJSONObject(i).getDouble("t_2"); //BFS processing time
	   		 double t_3 = statsArr.getJSONObject(i).getDouble("t_3"); //BFS processing time
	   		 double t_4 = statsArr.getJSONObject(i).getDouble("t_4"); //construct iso polygon time	   		 
	   		 double t_5 = statsArr.getJSONObject(i).getDouble("t_5"); //BFS processing time
	   		 double t_6 = statsArr.getJSONObject(i).getDouble("t_6"); //BFS processing time
	   		 double t_7 = statsArr.getJSONObject(i).getDouble("t_7"); //construct iso polygon time
	   		 double t_all = statsArr.getJSONObject(i).getDouble("t_all");//iso total calculation time

	   		 pst.setDouble(1, para_radius);
	   		 pst.setDouble(2, para_bufsize);
	   		 pst.setString(3, para_seedcoord);
	   		 pst.setInt(4, num_rawlink);
	   		 pst.setInt(5, num_isolink);
	   		 pst.setInt(6, num_isonode);
	   		 pst.setInt(7, num_isoleafnode);
	   		 pst.setDouble(8, t_1);
	   		 pst.setDouble(9, t_2);
	   		 pst.setDouble(10, t_3);
	   		 pst.setDouble(11, t_4);
	   		 pst.setDouble(12, t_5);
	   		 pst.setDouble(13, t_6);
	   		 pst.setDouble(14, t_7);
	   		 pst.setDouble(15, t_all);
	   		 pst.addBatch();
	   	 }
	   	 
	   	 pst.executeBatch();
	  	 pst.clearBatch();
	  	 pst.close();
	   	 DbAccess.closeBatchConn();
		}
	
}
