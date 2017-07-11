package com.nearbit.common.isochrones;


/*
 * Copyright (C) 2016 Benny Chen
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
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.feature.DefaultFeatureCollection;
import org.json.JSONArray;
import org.opengis.feature.simple.SimpleFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.nearbit.dev.api.isochrones.IsochronesOutput;

/**
 * Generates network buffers for a set of points, using Fork/Join for
 * concurrency
 * 
 * @author  Benny Chen
 */
public class IsochronesBatch { //extends RecursiveAction {

  // /**
  // * Some nonsense to satisfy sonar+findbugs
  // */
  // private static final long serialVersionUID = 1L;
  static final Logger LOGGER = LoggerFactory.getLogger(IsochronesBatch.class);
  private SimpleFeatureSource network;
  private SimpleFeatureCollection points;
  private DefaultFeatureCollection buffers;
  private DefaultFeatureCollection roadlines;
  private DefaultFeatureCollection roadnodes;
  private DefaultFeatureCollection graphs;
  private JSONArray performanceStatsArray;
  private Double bufferSize;
  private ArrayList<Double> distanceArray;
  private String polygondetaillevel;
  private int concavehullthreshold;

  /**
   * Generates network buffers for a set of points
   * 
   * @param network
   *          The network to use to generate service networks
   * @param points
   *          The set of points of interest
   * @param distanceArray
   *          The distance definition to traverse along the network for each point.
   * @param bufferSize
   *          The length to buffer the service network
   */
  public IsochronesBatch(SimpleFeatureSource network,
	      SimpleFeatureCollection points, ArrayList<Double> distanceArray, Double bufferSize, String polygondetaillevel, int concavehullthreshold) {
	    this.network = network;
	    this.points = points;
	    this.distanceArray = distanceArray;
	    this.bufferSize = bufferSize;
	    this.buffers = new DefaultFeatureCollection();
	    this.roadlines = new DefaultFeatureCollection();
	    this.roadnodes = new DefaultFeatureCollection();
	    this.graphs = new DefaultFeatureCollection();
	    this.performanceStatsArray = new JSONArray();
	    this.polygondetaillevel = polygondetaillevel;
	    this.concavehullthreshold = concavehullthreshold;
	  }
  
  /**
   * 
   * @return A SimpleFeatureCollection of the service area networks for all
   *         points of interest
   */
  public SimpleFeatureCollection getGraphs() {
    return graphs;
  }
  
  public SimpleFeatureCollection getBuffers(){
		  return (SimpleFeatureCollection)buffers;  
  }
  
  public SimpleFeatureCollection getRoadLines(){
	  return (SimpleFeatureCollection)roadlines;  
}
  
  public SimpleFeatureCollection getRoadNodes(){
	  return (SimpleFeatureCollection)roadnodes;  
}
  public JSONArray getPerformanceStatsArray(){
	  return performanceStatsArray;  
}
  
  public boolean createBuffersAdvanced() {
	    ExecutorService executorService = Executors.newFixedThreadPool(Runtime
	        .getRuntime().availableProcessors());
	    
	    try {
		    List<Future> futures = new ArrayList<Future>();
		    int count = 0;
		    SimpleFeatureIterator features = points.features();
		    while (features.hasNext()) {
		        SimpleFeature point = features.next();
		        BuffernatorAdvanced ac = new BuffernatorAdvanced(distanceArray.get(count), point, network);
		        Future future = executorService.submit(ac);
		        futures.add(future);
		        count++;
		    }
		    features.close();

		    for (Future future : futures) {
		    		buffers.add(((IsochronesOutput)future.get()).serviceAreaPolgyon);
		    		roadlines.addAll(((IsochronesOutput)future.get()).serviceAreaLines); 
		    		roadnodes.addAll(((IsochronesOutput)future.get()).serviceAreaNodes);
		    		performanceStatsArray.put(((IsochronesOutput)future.get()).performanceStats);
		    }
		    
		    LOGGER.debug("Completed {} buffers for {} points", buffers.size(), points.size());
		    return true;
	    } catch (Exception e) {
	      LOGGER.error("=== {}", e.getMessage());
	    } finally {
	      executorService.shutdownNow();
	    }
	    
	    return false;
	  }
  
  class BuffernatorAdvanced implements Callable<IsochronesOutput> {
  	private Double reachDistance;
    private SimpleFeature point;
    private SimpleFeatureSource network;

    BuffernatorAdvanced(Double reachDistance, SimpleFeature point, SimpleFeatureSource network) {
      this.reachDistance = reachDistance;
      this.point = point;
      this.network = network;
    }

    public IsochronesOutput call() throws Exception {
    	
      IsochronesOutput sao = Isochrones.run(network, reachDistance, bufferSize, point, String.valueOf(point.getID()), polygondetaillevel, concavehullthreshold);
      
      return sao;
    }
  }
}