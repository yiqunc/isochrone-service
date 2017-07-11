package com.nearbit.common.isochrones;

import org.geotools.graph.structure.Edge;
import org.geotools.graph.structure.Node;

public class IsochronesNode {
	public Node node = null;
	public double traverseDistance = 0.0f;
	public Edge inEdge = null; // the shortest edge that connect to this node from other node
	public boolean isLeaf = false;
	public Integer fromNodeID = -1; //from which node the inEdge is built
}
