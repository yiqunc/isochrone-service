package com.nearbit.common;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.geotools.data.DataSourceException;
import org.geotools.data.DataStore;
import org.geotools.data.DataStoreFinder;
import org.geotools.data.simple.SimpleFeatureSource;

public class PostgresDataStore {

	private DataStore dataStore = null;

	public SimpleFeatureSource getFeatureSource(String layerName)throws IOException {
		
		return getDataStore().getFeatureSource(layerName);
	}

	public DataStore getDataStore() throws IOException, DataSourceException {

		if (dataStore == null) {
			Map<String, Object> params = new HashMap<String, Object>();

			params.put( "dbtype", "postgis");
			params.put( "host", AppConfig.getString("postgresDB_IP"));
			params.put( "port", AppConfig.getString("postgresDB_PORT"));
			params.put( "schema", AppConfig.getString("postgresDB_SCHEMA"));
			params.put( "database", AppConfig.getString("postgresDB_NAME"));
			params.put( "user", AppConfig.getString("postgresDB_USER"));
			params.put( "passwd",AppConfig.getString("postgresDB_PASSWD"));
  
			dataStore = DataStoreFinder.getDataStore(params);
			
		}
		return dataStore;
	}
	
	public void dispose(){
		
		if(dataStore != null){
			
			dataStore.dispose();
			dataStore = null;
		}
	}


}
