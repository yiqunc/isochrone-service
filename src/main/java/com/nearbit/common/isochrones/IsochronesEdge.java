package com.nearbit.common.isochrones;

import org.geotools.graph.structure.Edge;
import org.geotools.graph.structure.Node;

import com.vividsolutions.jts.geom.Coordinate;

public class IsochronesEdge {
	public double traverseDistance = 0.0f; //from start point to the end node of this edge
	public Edge edge = null;
	public boolean isLeaf = false;
	public boolean isChopped = false;
	public Integer fromNodeID = -1; //from which node the edge is built
	public Coordinate toNodeCoordinate = new Coordinate();
	public Integer toNodeID = -1; //to which node the edge is built
}
