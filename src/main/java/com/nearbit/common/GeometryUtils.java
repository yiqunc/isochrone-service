package com.nearbit.common;

/*
 * Copyright (C) 2014 Benny Chen
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

import java.util.ArrayList;
import java.util.regex.Pattern;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.feature.DefaultFeatureCollection;
import org.geotools.referencing.CRS;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.NoSuchAuthorityCodeException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.vividsolutions.jts.geom.Geometry;

public class GeometryUtils {
	
		static final Logger LOGGER = LoggerFactory.getLogger(GeometryUtils.class);
		
		static final String LatLngStringFormat = "^[-+]?([1-8]?\\d(\\.\\d+)?|90(\\.0+)?),\\s*[-+]?(180(\\.0+)?|((1[0-7]\\d)|([1-9]?\\d))(\\.\\d+)?)$";
		
		static final Pattern LatLngPattern = Pattern.compile(LatLngStringFormat);
		
		/***
		 * Given two feature collections A and B, return a sub feature collection of A which intersect with B
		 * @param A
		 * @param B
		 * @return
		 * @throws Exception
		 */
		public static SimpleFeatureCollection intersection(SimpleFeatureCollection A,
				SimpleFeatureCollection B) throws Exception {

			if (A == null || A.size() == 0) return null;

			//if B is null, it means A has nothing to intersect with B, so we return null
			if (B == null || B.size() == 0) return null;

			DefaultFeatureCollection outputFC =  new DefaultFeatureCollection();

			ArrayList<SimpleFeature> uniqueIntersectedFeatureList = new ArrayList<SimpleFeature>();
			 
			LOGGER.info("A geometry number: {}", A.size());
			LOGGER.info("B geometry number: {}", B.size());
			
			SimpleFeatureIterator BFeatures = B.features();

			while (BFeatures.hasNext()) {
				SimpleFeature featureB = BFeatures.next();
				Geometry geometryB = (Geometry) featureB.getDefaultGeometry();
				
				SimpleFeatureIterator AFeatures = A.features();
				while (AFeatures.hasNext()){
					
					SimpleFeature featureA = AFeatures.next();
					Geometry geometryA = (Geometry) featureA.getDefaultGeometry();
					if(geometryA.intersects(geometryB)){
						if(uniqueIntersectedFeatureList.contains(featureA)){
		            		//skip duplicated ones
		            		LOGGER.info("===== duplicate intersected feature ignored");
		            	}
		            	else
		            	{
		            		uniqueIntersectedFeatureList.add(featureA);
		            		outputFC.add(featureA);
		            	}
					}
				}
				AFeatures.close();
				
			}
			
			BFeatures.close();
			
			return outputFC;
		}
		
		/***
		 * intersect featureCollection with a geometry with overlapThreshold respection
		 * @param A
		 * @param geometryB
		 * @param overlapThreshold
		 * @return
		 * @throws Exception
		 */
		public static SimpleFeatureCollection intersection(SimpleFeatureCollection A,
				Geometry geometryB, double overlapThreshold) throws Exception {
			
			
			if (A == null || A.size() == 0) return null;
			
			//TODO: test geometryB type, must be polygon
			if(overlapThreshold>1.0) overlapThreshold = 1.0;
			
			
			DefaultFeatureCollection outputFC =  new DefaultFeatureCollection();
			 
			//LOGGER.info("A geometry number: {}", A.size());

			SimpleFeatureIterator AFeatures = A.features();
			while (AFeatures.hasNext()){
				SimpleFeature featureA = AFeatures.next();
				Geometry geometryA = (Geometry) featureA.getDefaultGeometry();
				if(geometryA.intersects(geometryB))
				{	
					//if don't need to test the intersection area/length, add id directly
					if(overlapThreshold<0){
	            		outputFC.add(featureA);
					}
					else{
						
						Geometry intersection = geometryA.intersection(geometryB);
						if(geometryA.getArea()>0){
							
							if(intersection.getArea()/geometryA.getArea() > overlapThreshold){
								
								outputFC.add(featureA);
							}
						}
					}
				}
			}
			AFeatures.close();

			return outputFC;
		}
		
		
		public static Geometry union(SimpleFeatureCollection A) throws Exception {
			
			
			Geometry output = null;
			if (A == null || A.size() == 0) return null;
			 
			LOGGER.info("A geometry number: {}", A.size());

			SimpleFeatureIterator AFeatures = A.features();
			while (AFeatures.hasNext()){
				SimpleFeature featureA = AFeatures.next();
				Geometry geometryA = (Geometry)featureA.getDefaultGeometry();
				geometryA = geometryA.union();
				//LOGGER.info("==== ");
				if (output != null) {
					output = output.union().union();
				}
				
				if(output == null){
					output = geometryA;
				}else
				{
					output = output.union(geometryA);
					
				}
			}
			AFeatures.close();

			return output;
		}
		
		/**
		 * Check if input is a valid strLatLngString (e.g., 'lat, lng')
		 * @param strLatLngString
		 * @return
		 * @throws Exception
		 */
		public static boolean checkValidLatLngString(String strLatLngString) throws Exception {
			return (LatLngPattern.matcher(strLatLngString).find());
		}
		
		
		/**
		 * autmatically determine a UTM CRS from a given SimpleFeatureCollection
		 * 
		 * @param fc
		 * @return
		 * @throws NoSuchAuthorityCodeException
		 * @throws FactoryException
		 */
		public static CoordinateReferenceSystem getAutoUTM(SimpleFeatureCollection fc)
				throws NoSuchAuthorityCodeException, FactoryException {

			double lon = (fc.getBounds().getMaxX() + fc.getBounds().getMinX()) / 2.0;
			double lat = (fc.getBounds().getMaxY() + fc.getBounds().getMinY()) / 2.0;

			CoordinateReferenceSystem crs = CRS.decode("AUTO2:42001," + lon + "," + lat);

			return crs;
		}
}
