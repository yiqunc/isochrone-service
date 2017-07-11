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

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import jsr166y.RecursiveAction;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.graph.structure.Edge;
import org.geotools.graph.structure.Node;
import org.geotools.graph.structure.basic.BasicEdge;
import org.geotools.graph.structure.basic.BasicNode;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.linearref.LengthIndexedLine;

/**
 * A Fork/Join Network service area generator using Bread First graph traversal
 * of a network
 * 
 * @author  Benny Chen
 */
public class IsochronesFJ extends RecursiveAction {

	static final Logger LOGGER = LoggerFactory.getLogger(IsochronesFJ.class);

	public LinkedList <IsochronesNode> isochronesNodeList;
	public Map<Integer, IsochronesNode> visitedIsochronesNodeMap;
	public Map<Integer, Geometry> visitedWholeEdgeBufferMap;
	public Map<Integer, IsochronesEdge> visitedWholeEdgeMap;
	public Map<String, IsochronesEdge> visitedChoppedEdgeMap; //string is the choppededgecode, which is constructed by "choppedEdgeId-fromNodeId"
	public Double distance;

	/**
	 * Intialise inputs
	 * 
	 * @param network
	 *            Network/graph dataset
	 * @param currentPath
	 *            The current path being traversed
	 * @param distance
	 *            The maximum distance to traverse a path
	 * @param nmEdges
	 *            The normal Edges (set of edges)
	 * @param ucEdges
	 * 			  The uncontained-chopped Edges (set of edges)
	 */
	public IsochronesFJ(LinkedList<IsochronesNode> isochronesNodeList, Double distance) {
		this.isochronesNodeList = isochronesNodeList;
		this.distance = distance;
		this.visitedIsochronesNodeMap = new ConcurrentHashMap<Integer, IsochronesNode>();
		this.visitedWholeEdgeBufferMap = new ConcurrentHashMap<Integer, Geometry>();
		this.visitedWholeEdgeMap = new ConcurrentHashMap<Integer, IsochronesEdge>();
		this.visitedChoppedEdgeMap = new ConcurrentHashMap<String, IsochronesEdge>();

	}

	/**
	 * Sets up the ForkJoinPool and then calls invoke to find service area
	 * 
	 */
	public void createBuffer() {
		compute();
	}

	@Override
	protected void compute() {
		
		// if a new leaf node is create, give it a proper node id by subtract leafNodeId value
		int leafNodeId = 999999999;
		boolean isStartNode = true;
		int BFSLoopCount = 0;
		//using breadth first search 
		while(!isochronesNodeList.isEmpty()){
			
			IsochronesNode curIsoNode = isochronesNodeList.removeFirst();
			if(BFSLoopCount == 0)
			{
				isStartNode = true;
			}
			else
			{
				isStartNode = false;
			}
			BFSLoopCount++;
			
			double curTrasverseDistance = curIsoNode.traverseDistance;
			//LOGGER.info("=== visit nodeid {}, {}", curIsoNode.node.getID(), "travdist:"+curIsoNode.traverseDistance);
			visitedIsochronesNodeMap.put(curIsoNode.node.getID(), curIsoNode);
			
			//add all of connected edges to isochronesNodeList 
			List<Edge> graphEdges = (List<Edge>)curIsoNode.node.getEdges();
			
			for (Edge inEdge : graphEdges) {
				
				//get the length of edge
				Geometry edgeGeom = (Geometry)(((SimpleFeature) inEdge.getObject()).getDefaultGeometry());
				double edgeLen = edgeGeom.getLength();
				
				//build a new IsochronesNode object
				IsochronesNode newIsochronesNode = new IsochronesNode();
				
				//check whether a whole edge can be added
				if(edgeLen + curTrasverseDistance <= distance){
					//no edge chopping required. add an existing node to isochronesNodeList when necessary, 
					//check whether this node is already in visitedIsochronesNodeMap, 
					//if no, then add it; 
					//if yes, then check whether the new traverseDistance is smaller than the old one: 
					//					if no, then there is no need to add it; 
					//					if yes, then update the node's inEdge and traverseDistance info in visitedIsochronesNodeMap and then add it again so that this node (and its connected nodes) can be revisited 
					
					Node newNode = null;
					
					//make sure newNode is distant away from curIsoNode.node, it should be either inEdge.getNodeA or inEdge.getNodeB
					Coordinate curIsoNodeCoord = ((Point)curIsoNode.node.getObject()).getCoordinate();
					Coordinate nodeACoord = ((Point)inEdge.getNodeA().getObject()).getCoordinate();
					Coordinate nodeBCoord = ((Point)inEdge.getNodeB().getObject()).getCoordinate();
					if(curIsoNodeCoord.distance(nodeACoord) > curIsoNodeCoord.distance(nodeBCoord)){
						newNode = inEdge.getNodeA();
					}else
					{
						newNode = inEdge.getNodeB();
					}
					/*
					if(curIsoNode.node.equals(inEdge.getNodeB())){
						newNode = inEdge.getNodeA();
					}else
					{
						newNode = inEdge.getNodeB();
					}
					*/
					
					//add whole edge to visitedWholeEdgeMap
					
					//if nodeA is the end, then assign nodeA's coord to toNodeCoordinate
					boolean isLeaf = false;
					if(inEdge.getNodeA().getDegree() == 1 || inEdge.getNodeB().getDegree() == 1)
					{
						//just in case the startnode is a leaf node, the bfs should continue.
						//must not set a startnode as a leaf node, otherwise the search will be terminated incorrectly
						if(!isStartNode) isLeaf = true;
					}
					
					//construct a newEdge with correct NodeA and NodeB and pass it instead of 'inEdge' to newIsoEdge.edge and newIsochronesNode.inEdge, the reason is that 'inEdge' might have wrong NodeA and NodeB order
					Edge newEdge = new BasicEdge(curIsoNode.node, newNode);
					newEdge.setObject(inEdge.getObject());
					newEdge.setID(inEdge.getID());
					
					IsochronesEdge newIsoEdge = new IsochronesEdge();
					newIsoEdge.edge = newEdge;
					newIsoEdge.isLeaf = isLeaf;
					newIsoEdge.traverseDistance = edgeLen + curTrasverseDistance;
					newIsoEdge.fromNodeID = curIsoNode.node.getID();
					newIsoEdge.toNodeID = newNode.getID();
					newIsoEdge.toNodeCoordinate = ((Point)newNode.getObject()).getCoordinate();

					//check if visitedWholeEdgeMap has already contains newIsoEdge
					if(!visitedWholeEdgeMap.containsKey(inEdge.getID())){
						visitedWholeEdgeMap.put(inEdge.getID(), newIsoEdge);
					}
					else
					{
						//if contains, update visitedWholeEdgeMap only when current travdist is smaller than that of existing one
						double existingTravDist = visitedWholeEdgeMap.get(inEdge.getID()).traverseDistance;
						if(newIsoEdge.traverseDistance < existingTravDist){
							visitedWholeEdgeMap.put(inEdge.getID(), newIsoEdge);
						}
					}
					//the buffer whole edge is very valuable to eliminate those chopped edges that are "contained" inside
					//if wholeEdge never be buffered, put its buffer in visitedWholeEdgeBufferMap
					if(!visitedWholeEdgeBufferMap.containsKey(inEdge.getID())){
						visitedWholeEdgeBufferMap.put(inEdge.getID(), edgeGeom.buffer(1));
					}

					newIsochronesNode.node = newNode;
					newIsochronesNode.inEdge = newEdge;
					newIsochronesNode.isLeaf = isLeaf;
					newIsochronesNode.traverseDistance = edgeLen + curTrasverseDistance;
					newIsochronesNode.fromNodeID = curIsoNode.node.getID();
					
					//if this is not in visitedIsochronesNodeMap yet
					if(!visitedIsochronesNodeMap.containsKey(newNode.getID())){
						
						if(!isLeaf){
							//if this is not a leaf, then put it into isochronesNodeList so it can be visited later
							isochronesNodeList.addLast(newIsochronesNode);}
						else{
							//if this is a leaf, then put it in visitedIsochronesNodeMap, and not put it into isochronesNodeList
							//since the outer condition check can ensure it is not in visitedIsochronesNodeMap yet, so we can put it in directly
							visitedIsochronesNodeMap.put(newNode.getID(), newIsochronesNode);
						}
						
					}else //if this is already in visitedIsochronesNodeMap yet
					{
						double oldTraverseDistance = ((IsochronesNode)visitedIsochronesNodeMap.get(newNode.getID())).traverseDistance;
						if(edgeLen + curTrasverseDistance < oldTraverseDistance){
							//update visited node in visitedIsochronesNodeMap with smaller traverseDistance
							visitedIsochronesNodeMap.put(newNode.getID(), newIsochronesNode);
							/* TODO:
							//and add it back to isochronesNodeList if necessary, so its following nodes can be revisited as well.
							//check if the newIsochronesNode with same nodeID has already in isochronesNodeList
							//if no, add it
							//if yes, check if current one has the smaller traverseDistance
							//			if yes, replace existing one with this
							//			if no, do nothing
							*/
							//if this is not a leaf, then put it into isochronesNodeList so it can be visited later
							if(!isLeaf) isochronesNodeList.addLast(newIsochronesNode);
						}else{
							//do nothing
						}
					}
				}
				else{
					//edge chopping required. create a newIsochronesNode (isLeaf=true) and put it into visitedIsochronesNodeMap. 
					//There is no need to add this newIsochronesNode to isochronesNodeList for visiting
					
					//first we need to chop the edge
					double length = distance - (edgeLen + curTrasverseDistance);
					Node node = curIsoNode.node;
					Node newNode = new BasicNode();
					//assign newNode with a system generated id
					newNode.setID(leafNodeId);
					//update leafNodeId for next use
					leafNodeId = leafNodeId - 1;
					
					//build a chopped edge and use it as inEdge for the newIsochronesNode
					Edge newEdge = new BasicEdge(node, newNode);
					
					//give the chopped edge the same id as the whole edge, important!
					newEdge.setID(inEdge.getID()); 

					Geometry lineGeom = ((Geometry) ((SimpleFeature) inEdge.getObject()).getDefaultGeometry());
					
					LengthIndexedLine line = new LengthIndexedLine(lineGeom);
					
					boolean chopEdgeFlagSuccess = true;

					Geometry newLine = null;
					if (node.equals(inEdge.getNodeA())) {
					    newLine = line.extractLine(line.getStartIndex(), length);
						SimpleFeature newFeature = buildFeatureFromGeometry(((SimpleFeature) inEdge.getObject()).getType(), newLine);
						newEdge.setObject(newFeature);

					} else if (node.equals(inEdge.getNodeB())) {
						newLine = line.extractLine(line.getEndIndex(), -length);
						SimpleFeature newFeature = buildFeatureFromGeometry(((SimpleFeature) inEdge.getObject()).getType(), newLine);
						newEdge.setObject(newFeature);

					} else {
						LOGGER.error("Failed To Cut Edge");
						chopEdgeFlagSuccess = false;
					}
					
					//if edge is chopped successfully, add wrap it as an inEdge of newIsochronesNode, then put the newIsochronesNode into visitedIsochronesNodeMap
					if(chopEdgeFlagSuccess){
						boolean ignoreChoppedEdge = false;
						//check if the newLine (chopped edge) is contained in visitedWholeEdgeBufferMap
						if(visitedWholeEdgeBufferMap.containsKey(inEdge.getID()))
						{
							Geometry buff = (Geometry)(visitedWholeEdgeBufferMap.get(inEdge.getID()));
							if(buff.contains(newLine)){
								//if yes, this chopped edge can be ignored
								//do nothing
								ignoreChoppedEdge = true;
							}
						}
						
						//continue check if the chopped edge codestring exists in visitedChoppedEdgeMap
						//if yes, then check if the existing one is shorter than the current one,
						//			if yes, replace it with current one, (set ignoreChoppedEdge = false), this is very important, it ensures that only the longest chopped edge is retained
						//			if no, do nothing (set ignoreChoppedEdge = true)
						//if no, set ignoreChoppedEdge = false
						if(visitedChoppedEdgeMap.containsKey(newEdge.getID()+"-"+node.getID())){
							Edge existingChoppedEdge = visitedChoppedEdgeMap.get(newEdge.getID()+"-"+node.getID()).edge;
							Geometry existingChoppedEdgeGeom = (Geometry)(((SimpleFeature) existingChoppedEdge.getObject()).getDefaultGeometry());
							double existingChoppedEdgeLength = existingChoppedEdgeGeom.getLength();
							
							double newChoppedEdgeLength = newLine.getLength();
							
							if(existingChoppedEdgeLength > newChoppedEdgeLength){
								ignoreChoppedEdge = true;
							}
						}
						
						
						//if cannot ignore the ChoppedEdge, then wrap the chopped edge as an inEdge of newIsochronesNode, 
						// and put the newIsochronesNode into visitedIsochronesNodeMap (there is no need to add it to isochronesNodeList)
						if (!ignoreChoppedEdge){
							newIsochronesNode.node = newNode;
							newIsochronesNode.inEdge = newEdge;
							newIsochronesNode.traverseDistance = distance;
							newIsochronesNode.isLeaf = true;
							newIsochronesNode.fromNodeID = curIsoNode.node.getID();
							visitedIsochronesNodeMap.put(newNode.getID(), newIsochronesNode);
							
							
							IsochronesEdge newIsoEdge = new IsochronesEdge();
							newIsoEdge.edge = newEdge;
							newIsoEdge.isLeaf = true;
							newIsoEdge.isChopped = true;
							newIsoEdge.traverseDistance = distance;
							newIsoEdge.fromNodeID = curIsoNode.node.getID();
							newIsoEdge.toNodeID = newNode.getID();
							
							Coordinate coord = new Coordinate();
							Coordinate edgeFirstCoord = newLine.getCoordinates()[0];
							Coordinate edgeLastCoord = newLine.getCoordinates()[newLine.getCoordinates().length -1];
							Coordinate curIsoNodeCoord = ((Point)curIsoNode.node.getObject()).getCoordinate();
						    if(curIsoNodeCoord.distance(edgeFirstCoord) < curIsoNodeCoord.distance(edgeLastCoord))
						    {
						    	coord = edgeLastCoord;
						    }
						    else
						    {
						    	coord = edgeFirstCoord;
						    }
							newIsoEdge.toNodeCoordinate = coord;
							
							visitedChoppedEdgeMap.put(newEdge.getID()+"-"+node.getID(), newIsoEdge);
						}
					}
					
				}

			}
		}
		
		//LOGGER.info("===== now all we need is ready in visitedIsochronesNodeMap");
		// test for the last time if chopped is within whole edge buffer (since till now all whole edge buffer should exist in the visitedWholeEdgeBufferMap)
		// if yes, its IsochronesNode should be removed from visitedIsochronesNodeMap
		Set<Integer> nodeIDs = visitedIsochronesNodeMap.keySet();
		for (Integer nodeID : nodeIDs) {
			if(visitedIsochronesNodeMap.get(nodeID).isLeaf){
				Integer edgeID = visitedIsochronesNodeMap.get(nodeID).inEdge.getID();
				if(visitedWholeEdgeBufferMap.containsKey(edgeID))
				{
					Geometry choppedEdgeGeom = (Geometry)(((SimpleFeature) visitedIsochronesNodeMap.get(nodeID).inEdge.getObject()).getDefaultGeometry());
					
					Geometry buff = (Geometry)(visitedWholeEdgeBufferMap.get(edgeID));
					if(buff.contains(choppedEdgeGeom)){

						//if yes, first remove the chopped edge from visitedChoppedEdgeMap
						visitedChoppedEdgeMap.remove(visitedIsochronesNodeMap.get(nodeID).inEdge.getID()+"-"+visitedIsochronesNodeMap.get(nodeID).fromNodeID );
						//then this chopped edge can be ignored
						visitedIsochronesNodeMap.remove(nodeID);
					}
				}
			}
		}
		
	}

	
	private static SimpleFeature buildFeatureFromGeometry(SimpleFeatureType featureType, Geometry geom) {

		SimpleFeatureTypeBuilder stb = new SimpleFeatureTypeBuilder();
		stb.init(featureType);
		SimpleFeatureBuilder sfb = new SimpleFeatureBuilder(featureType);
		sfb.add(geom);
	
		return sfb.buildFeature(null);
	}

	
}