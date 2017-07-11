# isochrone-service

This project aims to provide researchers with a highly-parametric API (Application Programming Interface) for creating isochrones worldwide, meeting various scenario requirements with high accuracy. We start with OpenStreetMap road data that the software cleans by applying a sub-graph algorithm, removing isolated road links. This results in a fully-connected network for isochrones calculation, improving the web API stability. Then, a non-recursive breadth-first-search algorithm runs in parallel to generate isochrone links. The isochrones are then constructed using either link buffers or concave hulls to meet various accuracy requirements. The final outputs, including isochrones polygons, lines, and nodes with traverse distance attributes, can be exported in popular formats like geojson or shpfiles.

The core algorithm implementation locates in package "com.nearbit.common.isochrones", which can be applied to any road network data.

For more information, check out http://nearbit.com/api-isochrones/




If you have any questions or suggestions, please contact:


Dr Yiqun Chen

Centre for Spatial Data Infrastructures & Land Administration

The University of Melbourne

E: yiqun.c@unimelb.edu.au