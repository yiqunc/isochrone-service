package com.nearbit.common.isochrones.performance;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TestingController {

	static final Logger LOGGER = LoggerFactory.getLogger(TestingController.class);
	
	private List<JSONObject> pointspool = new ArrayList<JSONObject>();

	public TestingController(){
		
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(TestingController.class.getResource("/PERFORMANCE_TEST_MELBUCL_SEEDPOINTS_POOL.json").getPath()));
            String line;
            while ((line = br.readLine()) != null) {
            	pointspool.add(new JSONObject(line));
            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        } finally {
            try {
                if (br != null) {
                    br.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
			
			
	}
	
	public JSONArray getRandomNPoints(int num){
		
		JSONArray points = new JSONArray();
		if(num<=0 || num>pointspool.size()) num = 10;
		try{
			Collections.shuffle(pointspool);
			for(int i=0;i<num;i++){
				points.put(pointspool.get(i));
			}
		}
		catch (Exception e){
			e.printStackTrace();
		}
		return points;
	}
}
