package com.nearbit.dev.api.isochrones;

import org.geotools.data.simple.SimpleFeatureCollection;
import org.json.JSONObject;
import org.opengis.feature.simple.SimpleFeature;

public class IsochronesOutput {
	public SimpleFeature serviceAreaPolgyon = null;
	public SimpleFeatureCollection serviceAreaLines = null;
	public SimpleFeatureCollection serviceAreaNodes = null;
	public JSONObject performanceStats = new JSONObject(); //performance statistics
}
