package com.nearbit.common;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.Map;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.DefaultTransaction;
import org.geotools.data.Transaction;
import org.geotools.data.shapefile.ShapefileDataStore;
import org.geotools.data.shapefile.ShapefileDataStoreFactory;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureStore;
import org.geotools.feature.FeatureCollection;
import org.geotools.geojson.feature.FeatureJSON;
import org.opengis.feature.simple.SimpleFeatureType;
import org.opengis.referencing.FactoryException;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ShapeFileUtils {

	private ShapeFileUtils() {
	}

	static final Logger LOGGER = LoggerFactory.getLogger(ShapeFileUtils.class);

	public static String clapFieldName(String name){
		
		if(name.length()>10) return name.substring(0, 10);
		else return name;
	}
	
	public static String clapFieldAgePopName(String name){
		name = name.replaceAll("age_yr_", "");
		name = name.replaceAll("over_", "");
		if(name.length()>10) return name.substring(0, 10);
		else return name;
	}
	
	public static void featuresExportToShapeFile(SimpleFeatureType type,
			SimpleFeatureCollection simpleFeatureCollection, File newFile,
			boolean createSchema,  CoordinateReferenceSystem targetCRS) throws IOException, FactoryException {

		if (!newFile.exists()) {
			newFile.createNewFile();
		}
		ShapefileDataStoreFactory dataStoreFactory = new ShapefileDataStoreFactory();

		Map<String, Serializable> params = new HashMap<String, Serializable>();
		params.put("url", newFile.toURI().toURL());
		params.put("create spatial index", Boolean.TRUE);

		ShapefileDataStore newDataStore = (ShapefileDataStore) dataStoreFactory
				.createNewDataStore(params);
		if (createSchema) {
			newDataStore.createSchema(type);
		}
		
		newDataStore.forceSchemaCRS(targetCRS);
		Transaction transaction = new DefaultTransaction("create");
		String typeName = newDataStore.getTypeNames()[0];
		SimpleFeatureSource featureSource = newDataStore.getFeatureSource(typeName);

		if (featureSource instanceof SimpleFeatureStore) {
			SimpleFeatureStore featureStore = (SimpleFeatureStore) featureSource;
			featureStore.setTransaction(transaction);
			try {
				featureStore.addFeatures(simpleFeatureCollection);
				transaction.commit();
				//LOGGER.info("commited to feature store, bbox:{}", simpleFeatureCollection.getBounds());

			} catch (Exception problem) {
				//LOGGER.error("exception, rolling back transaction in feature store");
				transaction.rollback();
			} finally {
				transaction.close();
			}
		} else {
			LOGGER.info(typeName + " does not support read/write access");
		}

	}

	public static FeatureCollection readFeaturesFromJson(File file) {
		FeatureCollection features = null;
		FeatureJSON fjson = new FeatureJSON();
		InputStream is;
		try {
			is = new FileInputStream(file);
			features = fjson.readFeatureCollection(is);
			try {
				if (features.getSchema().getCoordinateReferenceSystem() != null) {
					//LOGGER.info("Encoding CRS?");
					fjson.setEncodeFeatureCollectionBounds(true);
					fjson.setEncodeFeatureCollectionCRS(true);
				} else {
					LOGGER.info("CRS is null");
				}
			} finally {
				is.close();
			}
		} catch (FileNotFoundException e1) {
			LOGGER.error("Failed to write feature collection "
					+ e1.getMessage());
		} catch (IOException e) {
			LOGGER.error("Failed to write feature collection " + e.getMessage());
		}
		return features;
	}
	
	public static SimpleFeatureCollection readFeaturesFromShp(File file, String typename) {
		SimpleFeatureCollection features = null;
	
		Map map = new HashMap();
		try {
			map.put("url", file.toURI().toURL());
			DataStore dataStore = DataStoreFinder.getDataStore(map);

			SimpleFeatureSource featureSource = dataStore.getFeatureSource(typename);
			features = featureSource.getFeatures();
			
		} catch (MalformedURLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		return features;
	}
}
