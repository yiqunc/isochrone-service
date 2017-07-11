package com.nearbit.common.isochrones;


/*
 *  Copyright (C) 2016 Benny Chen
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.geotools.data.crs.ForceCoordinateSystemFeatureResults;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.store.ReprojectingFeatureCollection;
import org.geotools.factory.CommonFactoryFinder;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geometry.jts.GeometryBuilder;
import org.geotools.geometry.jts.GeometryCollector;
import org.geotools.geometry.jts.JTS;
import org.geotools.graph.build.feature.FeatureGraphGenerator;
import org.geotools.graph.build.line.LineStringGraphGenerator;
import org.geotools.graph.structure.Graph;
import org.geotools.graph.structure.Node;
import org.geotools.referencing.CRS;
import org.json.JSONObject;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.feature.type.FeatureType;
import org.opengis.filter.Filter;
import org.opengis.filter.FilterFactory2;
import org.opengis.geometry.MismatchedDimensionException;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.opengis.referencing.operation.MathTransform;
import org.opensphere.geometry.algorithm.ConcaveHull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nearbit.dev.api.isochrones.PositionChecker;
import com.nearbit.dev.api.isochrones.IsochronesOutput;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;
import com.vividsolutions.jts.geom.PrecisionModel;
import com.vividsolutions.jts.index.SpatialIndex;
import com.vividsolutions.jts.index.strtree.STRtree;
import com.vividsolutions.jts.linearref.LinearLocation;
import com.vividsolutions.jts.linearref.LocationIndexedLine;

/**
 * Generates Network Buffers, which can be used as service areas
 * @author  Benny Chen
 * 
 */
public final class Isochrones {

	private static final int GEOMETRY_PRECISION = 100;
	private static final int INTERSECTION_THRESHOLD = 3;
	static final Logger LOGGER = LoggerFactory.getLogger(Isochrones.class);
	private static PrecisionModel precision = new PrecisionModel(GEOMETRY_PRECISION);
	private static GeometryFactory geometryFactory = new GeometryFactory(precision);
	private Isochrones() {
	}

	private static Graph createGraph(
			LocationIndexedLine connectedLine, SimpleFeatureCollection networkRegion, Point pointOfInterest) {
		
		Coordinate pt = pointOfInterest.getCoordinate();
		
		//find the projection point on the connected(nearest) line  
		LinearLocation here = connectedLine.project(pt);
		
		Geometry lineA = connectedLine.extractLine(connectedLine.getStartIndex(), here);
		Geometry lineB = connectedLine.extractLine(here, connectedLine.getEndIndex());

		Geometry originalLine = connectedLine.extractLine(connectedLine.getStartIndex(), connectedLine.getEndIndex());

		if (lineB.isEmpty() || lineA.isEmpty() || (lineB.getLength() == 0.0) || (lineA.getLength() == 0.0)) {
			
			FeatureGraphGenerator networkGraphGen = buildFeatureNetwork(networkRegion);
			Graph graph = networkGraphGen.getGraph();

			return graph;
		} else {
			
			networkRegion = removeFeature(networkRegion, originalLine);

			GeometryFactory gf = new GeometryFactory(precision);
			lineA = gf.createLineString(lineA.getCoordinates());
			lineB = gf.createLineString(lineB.getCoordinates());

			SimpleFeatureType edgeType = createEdgeFeatureType(networkRegion.getSchema().getCoordinateReferenceSystem());
			
			SimpleFeature featureB = buildFeatureFromGeometry(edgeType, lineB);
			SimpleFeature featureA = buildFeatureFromGeometry(edgeType, lineA);
			FeatureGraphGenerator networkGraphGen = buildFeatureNetwork(networkRegion);
			networkGraphGen.add(featureA);
			networkGraphGen.add(featureB);

			Graph graph = networkGraphGen.getGraph();

			return graph;

		}
	}

	/**
	 * Constructs a geotools Graph line network from a feature source
	 * 
	 * @param featureCollection
	 *            the network feature collection
	 * @return returns a geotools FeatureGraphGenerator based on the features
	 *         within the region of interest
	 * @throws IOException
	 */
	private static FeatureGraphGenerator buildFeatureNetwork(
			SimpleFeatureCollection featureCollection) {
		// create a linear graph generator
		LineStringGraphGenerator lineStringGen = new LineStringGraphGenerator();

		// wrap it in a feature graph generator
		FeatureGraphGenerator featureGen = new FeatureGraphGenerator(lineStringGen);

		// put all the features into the graph generator
		SimpleFeatureIterator iter = featureCollection.features();

		SimpleFeatureType edgeType = createEdgeFeatureType(featureCollection.getSchema().getCoordinateReferenceSystem());

		GeometryFactory gf = new GeometryFactory(precision);
		try {
			while (iter.hasNext()) {
				SimpleFeature feature = iter.next();
				Geometry mls = (Geometry) feature.getDefaultGeometry();
				for (int i = 0; i < mls.getNumGeometries(); i++) {
					Coordinate[] coords = ((LineString) mls.getGeometryN(i)).getCoordinates();
					LineString lineString = gf.createLineString(coords);
					SimpleFeature segmentFeature = buildFeatureFromGeometry(edgeType, lineString);
					featureGen.add(segmentFeature);
				}

			}
		} finally {
			iter.close();
		}
		return featureGen;
	}

	private static Node findStartNode(Graph graph, Geometry inputPoint) {
		double minDist = 99999999999.0f;
		Node startNode = null;
		for (Node node : (Collection<Node>) graph.getNodes()) {

			Geometry nodeGeom = (Geometry) node.getObject();
			double dist = nodeGeom.distance(inputPoint);
			if(dist<minDist){
				minDist = dist;
				startNode = node;
			}
		}
	
		return startNode;
	}
	
	private static SimpleFeatureCollection removeFeature(SimpleFeatureCollection networkRegion,
			Geometry originalLine) {
		
		DefaultFeatureCollection result = new DefaultFeatureCollection();
		SimpleFeatureIterator features = networkRegion.features();
		//List<SimpleFeature> newFeatures = new ArrayList();
		while (features.hasNext()) {
			SimpleFeature feature = features.next();
			Geometry geom = (Geometry) feature.getDefaultGeometry();
			if (!(geom.equals(originalLine))) {
				//newFeatures.add(feature);
				result.add(feature);
			}
		}
		features.close();
		
		return (SimpleFeatureCollection)result;
	}

	private static Map<Node, List> graphToMap(Graph graph) {
		Map<Node, List> networkMap = new HashMap<Node, List>();
		for (Node node : (Collection<Node>) graph.getNodes()) {
			networkMap.put(node, node.getEdges());
			
		}
		return networkMap;
	}

	private static LocationIndexedLine findNearestEdgeLine(
			SimpleFeatureCollection network, Double roadDistance,
			Double bufferDistance, Point pointOfInterest) throws IOException {
		// Build network Graph - within bounds
		Double maxDistance = roadDistance + bufferDistance;
		SpatialIndex index = createLineStringIndex(network);

		Coordinate pt = pointOfInterest.getCoordinate();
		Envelope search = new Envelope(pt);
		search.expandBy(maxDistance);

		/*
		 * Query the spatial index for objects within the search envelope. Note
		 * that this just compares the point envelope to the line envelopes so
		 * it is possible that the point is actually more distant than
		 * MAX_SEARCH_DISTANCE from a line.
		 */
		List<LocationIndexedLine> lines = index.query(search);

		// Initialize the minimum distance found to our maximum acceptable
		// distance plus a little bit
		double minDist = maxDistance;// + 1.0e-6;
		Coordinate minDistPoint = null;
		LocationIndexedLine connectedLine = null;

		for (LocationIndexedLine line : lines) {

			LinearLocation here = line.project(pt); // What does project do?
			Coordinate point = line.extractPoint(here); // What does extracPoint
			// do?
			double dist = point.distance(pt);
			if (dist <= minDist) {
				minDist = dist;
				minDistPoint = point;
				connectedLine = line;
			}
		}

		if (minDistPoint != null) {
			//LOGGER.info("{} - snapped by moving {}\n", pt.toString(), minDist);
			return connectedLine;
		}
		return null;
	}

	private static SpatialIndex createLineStringIndex(
			SimpleFeatureCollection network) throws IOException {
		SpatialIndex index = new STRtree();

		// Create line string index
		// Just in case: check for null or empty geometry
		SimpleFeatureIterator features = network.features();
		try {
			while (features.hasNext()) {
				SimpleFeature feature = features.next();
				Geometry geom = (Geometry) (feature.getDefaultGeometry());
				if (geom != null) {
					Envelope env = geom.getEnvelopeInternal();
					if (!env.isNull()) {
						index.insert(env, new LocationIndexedLine(geom));
					}
				}
			}
		} finally {
			features.close();
		}

		return index;
	}

	public static IsochronesOutput run(SimpleFeatureSource network, Double reachDistance, Double bufSize, SimpleFeature pointFeature, String id, String polygondetaillevel, int concavehullthreshold) throws Exception {
		
		////////////////////////////////////////part 1
		//create a performanceStats to store all preocessing details
		JSONObject performanceStats = new JSONObject();
		
		DecimalFormat df = new DecimalFormat("#.00");
		long execStart = System.currentTimeMillis();
		long execT1 = System.currentTimeMillis();

		Point pointOfInterestGeo = (Point) pointFeature.getDefaultGeometry();

		CoordinateReferenceSystem geoCRS = CRS.decode("EPSG:4326");
		CoordinateReferenceSystem prjCRS = CRS.decode(PositionChecker.getEPSGCode(pointOfInterestGeo));
		
	    boolean lenient = true; // allow for some error due to different datums
	    MathTransform transformToPrjCRS = CRS.findMathTransform(geoCRS, prjCRS, lenient);
	    MathTransform transformToGeoCRS = CRS.findMathTransform(prjCRS, geoCRS, lenient);
	   
	    Point pointOfInterestPrj = (Point)JTS.transform(pointOfInterestGeo, transformToPrjCRS);
		Geometry pointBufferPrj = pointOfInterestPrj.buffer(reachDistance + bufSize);
		Geometry pointBufferGeo = JTS.transform(pointBufferPrj, transformToGeoCRS);
		
		long execT2 = System.currentTimeMillis();
		performanceStats.put("t_1", (execT2 - execT1) / 1000d);
		LOGGER.info("==== Section1 (create point buffer) Execution time is:{} seconds", df.format((execT2 - execT1) / 1000d));
		execT1 = execT2;
		//query network
		SimpleFeatureCollection networkRegionGeo = featuresInRegion(network, pointBufferGeo);
		if(networkRegionGeo!=null && networkRegionGeo.size()==0){
			LOGGER.error("No network segments found in given radius around the point");
			return null;
		}
		performanceStats.put("num_rawlink", networkRegionGeo.size());
		//convert network into projection
		SimpleFeatureCollection networkRegionPrj = new ForceCoordinateSystemFeatureResults(networkRegionGeo, geoCRS, false);
		networkRegionPrj = new ReprojectingFeatureCollection(networkRegionPrj, prjCRS);
		
		execT2 = System.currentTimeMillis();
		performanceStats.put("t_2", (execT2 - execT1) / 1000d);
		LOGGER.info("==== Section2 (load clipped road network) Execution time is:{} seconds", df.format((execT2 - execT1) / 1000d));
		execT1 = execT2;
		
		LocationIndexedLine nearestLine = findNearestEdgeLine(networkRegionPrj, reachDistance, bufSize, pointOfInterestPrj);
		//LOGGER.info("Found nearest edge line {}", nearestLine);
		
		execT2 = System.currentTimeMillis();
		performanceStats.put("t_3", (execT2 - execT1) / 1000d);
		LOGGER.info("==== Section3 (find nearest edge) Execution time is:{} seconds", df.format((execT2 - execT1) / 1000d));
		execT1 = execT2;
		
		if (nearestLine == null) {
			LOGGER.error("Failed to snap point {} to network",pointFeature.getID());
			return null;
		}

		Graph networkGraph = createGraph(nearestLine, networkRegionPrj, pointOfInterestPrj);
		
		Node startNode = findStartNode(networkGraph,pointOfInterestPrj);
		
		execT2 = System.currentTimeMillis();
		performanceStats.put("t_4", (execT2 - execT1) / 1000d);
		LOGGER.info("==== Section4 (create a graph) Execution time is:{} seconds", df.format((execT2 - execT1) / 1000d));
		execT1 = execT2;
		 
		LinkedList<IsochronesNode> isochronesNodeList = new LinkedList<IsochronesNode>();
		
		IsochronesNode initialNode = new IsochronesNode();
		initialNode.node =  startNode;
		isochronesNodeList.add(initialNode);
		
		IsochronesFJ nbfj = new IsochronesFJ(isochronesNodeList, reachDistance);
		nbfj.createBuffer();
		
		execT2 = System.currentTimeMillis();
		performanceStats.put("t_5", (execT2 - execT1) / 1000d);
		LOGGER.info("==== Section5 (calculate isochrones) Execution time is:{} seconds", df.format((execT2 - execT1) / 1000d));
		execT1 = execT2;
		
		//parse visitedIsochronesNodeMap into edge FeatureCollection
		
		DefaultFeatureCollection isoEdgeFC = new DefaultFeatureCollection();
		DefaultFeatureCollection isoNodeFC = new DefaultFeatureCollection();
		
		Set<Integer> visitedWholeEdgeMapIDs =  nbfj.visitedWholeEdgeMap.keySet();
		Set<String> visitedChoppedEdgeMapIDs =  nbfj.visitedChoppedEdgeMap.keySet();
		
		SimpleFeatureType isoEdgeFeatureType = createIsochronesEdgeFeatureType(prjCRS);
		SimpleFeatureBuilder sfb_isoEdge = new SimpleFeatureBuilder(isoEdgeFeatureType);
		
		SimpleFeatureType isoNodeFeatureType = createIsochronesNodeFeatureType(prjCRS);
		SimpleFeatureBuilder sfb_isoNode = new SimpleFeatureBuilder(isoNodeFeatureType);
		
		//a fast way to union geometry 
		Geometry[] edgeBuffers = new Geometry[visitedWholeEdgeMapIDs.size()+ visitedChoppedEdgeMapIDs.size()]; 
		int count = 0;
		
		//iso leaf Node geometry builder
		GeometryBuilder isoLeafNodeGeomBuilder = new GeometryBuilder(geometryFactory);
		GeometryCollector isoLeafNodeGC = new GeometryCollector();
		int isoLeafNodeCounter = 0;

		//loop visitedWholeEdgeMapIDs
		for (Integer edgeid : visitedWholeEdgeMapIDs) {	
			
			IsochronesEdge isoEdge =  nbfj.visitedWholeEdgeMap.get(edgeid);

			//build up edge featurecollection
			SimpleFeature f_isoEdge = sfb_isoEdge.buildFeature(null);
			LineString edgeGeom = (LineString)(((SimpleFeature)isoEdge.edge.getObject()).getDefaultGeometry());
			f_isoEdge.setDefaultGeometry(edgeGeom);
			f_isoEdge.setAttribute("isleaf", isoEdge.isLeaf);
			f_isoEdge.setAttribute("ischopped", isoEdge.isChopped);
			f_isoEdge.setAttribute("len", edgeGeom.getLength());
			f_isoEdge.setAttribute("travdist", isoEdge.traverseDistance);
			f_isoEdge.setAttribute("radius", reachDistance);
			f_isoEdge.setAttribute("bufsize", bufSize);
			f_isoEdge.setAttribute("seedcoord", pointOfInterestGeo.getY()+","+pointOfInterestGeo.getX());
			isoEdgeFC.add(f_isoEdge);
			
			
			//build up node featurecollection
			SimpleFeature f_isoNode = sfb_isoNode.buildFeature(null);
			Point nodeGeom = isoLeafNodeGeomBuilder.point(isoEdge.toNodeCoordinate.x, isoEdge.toNodeCoordinate.y);
			
			f_isoNode.setDefaultGeometry(nodeGeom);
			f_isoNode.setAttribute("isleaf", isoEdge.isLeaf);
			f_isoNode.setAttribute("travdist", isoEdge.traverseDistance);
			f_isoNode.setAttribute("nodeid", isoEdge.toNodeID);
			f_isoNode.setAttribute("radius", reachDistance);
			f_isoNode.setAttribute("bufsize", bufSize);
			f_isoNode.setAttribute("seedcoord", pointOfInterestGeo.getY()+","+pointOfInterestGeo.getX());
			
			isoNodeFC.add(f_isoNode);

			//build up edgebuffer array
			edgeBuffers[count] = edgeGeom.buffer(bufSize);
			
			if(isoEdge.isLeaf) {
				isoLeafNodeGC.add(nodeGeom);
				isoLeafNodeCounter++;
			}
			
			count++;
		}
		
		//also add startNode into isoNodeFC
		SimpleFeature f_isoStartNode = sfb_isoNode.buildFeature(null);
		
		f_isoStartNode.setDefaultGeometry(pointOfInterestPrj);
		f_isoStartNode.setAttribute("isleaf", false);
		f_isoStartNode.setAttribute("travdist", 0);
		f_isoStartNode.setAttribute("nodeid", 0);
		f_isoStartNode.setAttribute("radius", reachDistance);
		f_isoStartNode.setAttribute("bufsize", bufSize);
		f_isoStartNode.setAttribute("seedcoord", pointOfInterestGeo.getY()+","+pointOfInterestGeo.getX());
		isoNodeFC.add(f_isoStartNode);
		
		//loop visitedChoppedEdgeMapIDs
		for (String edgecode : visitedChoppedEdgeMapIDs) {	
			
			IsochronesEdge isoEdge =  nbfj.visitedChoppedEdgeMap.get(edgecode);
			

			//build up edge featurecollection
			SimpleFeature f_isoEdge = sfb_isoEdge.buildFeature(null);
			LineString edgeGeom = (LineString)(((SimpleFeature)isoEdge.edge.getObject()).getDefaultGeometry());
			f_isoEdge.setDefaultGeometry(edgeGeom);
			f_isoEdge.setAttribute("isleaf", true);
			f_isoEdge.setAttribute("ischopped", true);
			f_isoEdge.setAttribute("len", edgeGeom.getLength());
			f_isoEdge.setAttribute("travdist", isoEdge.traverseDistance);
			f_isoEdge.setAttribute("radius", reachDistance);
			f_isoEdge.setAttribute("bufsize", bufSize);
			f_isoEdge.setAttribute("seedcoord", pointOfInterestGeo.getY()+","+pointOfInterestGeo.getX());
			isoEdgeFC.add(f_isoEdge);
			
			
			//build up node featurecollection
			SimpleFeature f_isoNode = sfb_isoNode.buildFeature(null);
			Point nodeGeom = isoLeafNodeGeomBuilder.point(isoEdge.toNodeCoordinate.x, isoEdge.toNodeCoordinate.y);
			
			f_isoNode.setDefaultGeometry(nodeGeom);
			f_isoNode.setAttribute("isleaf", true);
			f_isoNode.setAttribute("travdist", isoEdge.traverseDistance);
			f_isoNode.setAttribute("nodeid", isoEdge.toNodeID);
			f_isoNode.setAttribute("radius", reachDistance);
			f_isoNode.setAttribute("bufsize", bufSize);
			f_isoNode.setAttribute("seedcoord", pointOfInterestGeo.getY()+","+pointOfInterestGeo.getX());
			isoNodeFC.add(f_isoNode);
			
			//check whether a isoNode feature can be ignored -- there is no need to do this for leaf node
			
			//build up edgebuffer array
			edgeBuffers[count] = edgeGeom.buffer(bufSize);
			
			isoLeafNodeGC.add(nodeGeom);
			isoLeafNodeCounter++;
			count++;
		}
		
		execT2 = System.currentTimeMillis();
		performanceStats.put("t_6", (execT2 - execT1) / 1000d);
		LOGGER.info("==== Section6 (handle isoEdgeFC) Execution time is:{} seconds", df.format((execT2 - execT1) / 1000d));
		execT1 = execT2;
		////////////////////////////////////////part 2
		
		IsochronesOutput sao = new IsochronesOutput();
		//sao.serviceAreaNodes = (SimpleFeatureCollection)nodeFC;
		
	    double roadArea = 0.0;
	    
		//if serviceArea is empty, create a circle for this point
		if(isoEdgeFC==null || isoEdgeFC.isEmpty()){
			Geometry circlePrj = pointOfInterestPrj.buffer(bufSize);
			Geometry circleGeo = JTS.transform(circlePrj, transformToGeoCRS);
			sao.serviceAreaPolgyon = buildIsochronePolygonFeature(pointFeature, circleGeo, id, "failure", roadArea);
			return sao;
		}
		
		Geometry all = null;

		// ref: http://www.rotefabrik.free.fr/concave_hull/
		// http://www.bostongis.com/postgis_concavehull.snippet
		// https://alastaira.wordpress.com/2011/03/22/alpha-shapes-and-concave-hulls/
		GeometryCollection gc = new GeometryCollection(edgeBuffers, ((Geometry)pointFeature.getDefaultGeometry()).getFactory());
		
		if(polygondetaillevel.equalsIgnoreCase("high")){
			all = gc.union();
			execT2 = System.currentTimeMillis();
			performanceStats.put("t_7", (execT2 - execT1) / 1000d);
			LOGGER.info("==== Section7-high (union buffered polygons) Execution time is:{} seconds", df.format((execT2 - execT1) / 1000d));
			execT1 = execT2;
		}else if(polygondetaillevel.equalsIgnoreCase("mid"))
		{
			//use endpoints to generate concavehull, set threshold value with 1/4 of the reachDistance (the reachDistance might be different for one searvice area calculation request if radiusarr is passed in, so this can be self-adaptive)
			ConcaveHull ch = new ConcaveHull(isoLeafNodeGC.collect(), reachDistance/4.0);
			all = ch.getConcaveHull().buffer(bufSize);
			execT2 = System.currentTimeMillis();
			performanceStats.put("t_7", (execT2 - execT1) / 1000d);
			LOGGER.info("==== Section7-mid (create concave hull with dynamic threshold) Execution time is:{} seconds", df.format((execT2 - execT1) / 1000d));
			execT1 = execT2;
		}
		else{
			//use endpoints to generate concavehull, set threshold value with fixed concavehullthreshold
			ConcaveHull ch = new ConcaveHull(isoLeafNodeGC.collect(), concavehullthreshold);
			all = ch.getConcaveHull().buffer(bufSize);
			execT2 = System.currentTimeMillis();
			performanceStats.put("t_7", (execT2 - execT1) / 1000d);
			LOGGER.info("==== Section7-low (create concave hull with fixed threshold) Execution time is:{} seconds", df.format((execT2 - execT1) / 1000d));
			execT1 = execT2;
		}
		
		roadArea = all.getArea();
		
		//the buffered network is in projected crs, need to project it back to geographic crs
	    Geometry allGeo = JTS.transform(all, transformToGeoCRS);
	    
		sao.serviceAreaPolgyon = buildIsochronePolygonFeature(pointFeature, allGeo, id, "success", roadArea);
		
		SimpleFeatureCollection isoEdgeFCGeo = new ForceCoordinateSystemFeatureResults(isoEdgeFC, prjCRS, false);
		isoEdgeFCGeo = new ReprojectingFeatureCollection(isoEdgeFCGeo, geoCRS);
		sao.serviceAreaLines = isoEdgeFCGeo;
		
		SimpleFeatureCollection isoNodeFCGeo = new ForceCoordinateSystemFeatureResults(isoNodeFC, prjCRS, false);
		isoNodeFCGeo = new ReprojectingFeatureCollection(isoNodeFCGeo, geoCRS);
		sao.serviceAreaNodes = isoNodeFCGeo;
		
		execT2 = System.currentTimeMillis();
		performanceStats.put("t_all", (execT2 - execStart) / 1000d);
		LOGGER.info("==== Single Isochrone Execution time is:{} seconds", df.format((execT2 - execStart) / 1000d));
		
		//append other info for performanceStats
		performanceStats.put("num_isolink", isoEdgeFCGeo.size());
		performanceStats.put("num_isonode", isoNodeFCGeo.size());
		performanceStats.put("num_isoleafnode", isoLeafNodeCounter);
		performanceStats.put("para_radius", reachDistance);
		performanceStats.put("para_bufsize", bufSize);
		performanceStats.put("para_seedcoord", pointOfInterestGeo.getY()+","+pointOfInterestGeo.getX());
		sao.performanceStats = performanceStats;

		return sao;
	}
	
	private static SimpleFeature buildIsochronePolygonFeature(
			SimpleFeature sourceFeature, Geometry geom, String id, String status, double roadArea) {
		
		SimpleFeatureTypeBuilder stb = new SimpleFeatureTypeBuilder();
		
		stb.setName("buffnetwork");
		stb.add("the_geom", Polygon.class);
		stb.setDefaultGeometry("the_geom");
		stb.add("seedcoord", String.class);
		stb.add("calcstatus", String.class);
		stb.add("roadarea", Double.class);
		SimpleFeatureType featureType = stb.buildFeatureType();
		SimpleFeatureBuilder sfb = new SimpleFeatureBuilder(featureType);
		SimpleFeature sf = sfb.buildFeature(id);
		
		Point pt = (Point)sourceFeature.getDefaultGeometry();

		sf.setAttribute("the_geom", geom);
		sf.setAttribute("seedcoord", pt.getY()+","+pt.getX());
		sf.setAttribute("calcstatus", status);
		sf.setAttribute("roadarea",roadArea);
		
		return sf;
		
	}

	/**
	 * Creates a line network representation of service area from set of Edges
	 * 
	 * @param serviceArea
	 *            The service area edges
	 * @return The edges as SimpleFeature
	 */
	public static List<SimpleFeature> createLinesFromEdges(Map serviceArea) {
		Set<String> edges = serviceArea.keySet();
		List<SimpleFeature> features = new ArrayList();

		for (String edge : edges) {
			features.add(((SimpleFeature) serviceArea.get(edge)));
		}
		return features;
	}

	/**
	 * Creates a convex hull buffer of service area
	 * 
	 * @param serviceArea
	 *            The set of edges
	 * @param distance
	 *            The distance to buffer the service area
	 * @param type
	 *            The feature type for the resulting SimpleFeature
	 * @return The Convex Hull buffered service area
	 */
	public static SimpleFeature createConvexHullFromEdges(Map serviceArea,
			Double distance, SimpleFeatureType type) {
		Set<String> edges = serviceArea.keySet();
		GeometryCollector gc = new GeometryCollector();
		List<Coordinate> coords = new ArrayList();
		for (String edge : edges) {
			Geometry geom = (Geometry) serviceArea.get(edge);
			gc.add(geom);
			Coordinate coordinate = geom.getCoordinate();
			coords.add(coordinate);
		}
		
		Geometry bufferedConvexHull = gc.collect().convexHull()
				.buffer(distance);
		return buildFeatureFromGeometry(type, bufferedConvexHull);
	}

	private static SimpleFeature buildFeatureFromGeometry(
			SimpleFeatureType featureType, Geometry geom) {
		SimpleFeatureTypeBuilder stb = new SimpleFeatureTypeBuilder();
		stb.init(featureType);
		SimpleFeatureBuilder sfb = new SimpleFeatureBuilder(featureType);
		sfb.add(geom);
		return sfb.buildFeature(null);
	}

	private static SimpleFeatureCollection featuresInRegion(
			SimpleFeatureSource featureSource, Geometry roi) throws IOException, MismatchedDimensionException, NoSuchAuthorityCodeException, FactoryException {
		// Construct a filter which first filters within the bbox of roi and
		// then filters with intersections of roi
		FilterFactory2 ff = CommonFactoryFinder.getFilterFactory2();
		FeatureType schema = featureSource.getSchema();

		String geometryPropertyName = schema.getGeometryDescriptor().getLocalName();

		Filter filter = ff.intersects(ff.property(geometryPropertyName),ff.literal(roi.getEnvelope()));

		SimpleFeatureCollection features = featureSource.getFeatures(filter);
		
		//LOGGER.info("Read {} features from road network source",features.size());
		

		return features;
	}

	private static SimpleFeatureType createEdgeFeatureType(CoordinateReferenceSystem crs) {

		SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
		builder.setName("Edge");
		builder.setCRS(crs); // <- Coordinate reference system

		// add attributes in order
		builder.add("Edge", LineString.class);

		// build the type
		return builder.buildFeatureType();
	}
	
	private static SimpleFeatureType createIsochronesEdgeFeatureType(CoordinateReferenceSystem crs) {

		SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
		builder.setName("IsochronesEdge");
		builder.setCRS(crs);
		// add attributes in order
		builder.add("the_geom", LineString.class);
		builder.setDefaultGeometry("the_geom");
		//the seed point(startnode) coords
		builder.add("seedcoord", String.class);
		//mark if a line seg is chopped or not
		builder.add("ischopped", Boolean.class);
		//mark if a line seg is leaf or not
		builder.add("isleaf", Boolean.class);
		//the length of the edge
		builder.add("len", Double.class);
		//the distance on the path traverse
		builder.add("travdist", Double.class);
		//the radius of search -- this is used to differentiate outputs from different computation settings of the same seed point
		builder.add("radius", Double.class);
		//the bufsize of search -- this is used to differentiate outputs from different computation settings of the same seed point
		builder.add("bufsize", Double.class);
		return builder.buildFeatureType();
	}
	
	private static SimpleFeatureType createIsochronesNodeFeatureType(CoordinateReferenceSystem crs) {

		SimpleFeatureTypeBuilder builder = new SimpleFeatureTypeBuilder();
		builder.setName("IsochronesNode");
		builder.setCRS(crs);
		// add attributes in order
		builder.add("the_geom", Point.class);
		builder.setDefaultGeometry("the_geom");
		//the seed point(startnode) coords
		builder.add("seedcoord", String.class);
		builder.add("nodeid", Integer.class);
		builder.add("isleaf", Boolean.class);
		builder.add("travdist", Double.class);
		//the radius of search -- this is used to differentiate outputs from different computation settings of the same seed point
		builder.add("radius", Double.class);
		//the bufsize of search -- this is used to differentiate outputs from different computation settings of the same seed point
		builder.add("bufsize", Double.class);
		return builder.buildFeatureType();
	}
}