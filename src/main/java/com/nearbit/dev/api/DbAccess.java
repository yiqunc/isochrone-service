package com.nearbit.dev.api;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.apache.commons.dbcp.BasicDataSource;

public class DbAccess {

	static final Logger LOGGER = LoggerFactory.getLogger(DbAccess.class);
	
	private static Context initCtx = null;
    private static Context envCtx = null;
    private static BasicDataSource pgds = null;
    public static Connection batchConn = null;
    public static PreparedStatement pstTBL_INSERT = null;
    
    /**
     * Init Postgres Data source
     */
	public static void initPostgresDataSource(){
		
		if(pgds!=null) return;
		
		try {
			initCtx = new InitialContext();
			envCtx = (Context) initCtx.lookup("java:comp/env");
			pgds = (BasicDataSource)envCtx.lookup("jdbc/pgds_basic");
		} catch (NamingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void initBatchConn(){
		
		if(batchConn!=null) return;
		
		try {
			batchConn = pgds.getConnection();
			//pstTBL_INSERT = batchConn.prepareStatement("INSERT INTO tbl_route_info(o_fid, d_fid, d_type, trv_dist, euc_dist, job_id, foi_tbl_id) VALUES (?, ?, ?, ?, ?, ?, ?);");

			//pstTBL_INSERT = batchConn.prepareStatement("INSERT INTO tbl_route(the_geom, o_id, d_id, d_type, trv_dist, job_id) VALUES (ST_GeomFromText(?, 28355), ?, ?, ?, ?, ?);");
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public static void closeBatchConn(){
		
		if(batchConn!=null)
			try {
				//pstTBL_INSERT.close();
				batchConn.close();
				batchConn = null;
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		
	}

	/**
	 * Get job(sim) records for specific type of analysis of specific login user 
	 * @param maxNum
	 * @param modelId
	 * @param userId
	 * @return a JSON array containing the results.   
	 */
	public static JSONArray getSimRecords(int maxNum, int modelId, int userId){
		
		//TODO :check options if valid
		if (maxNum<=0) maxNum = 10;
		
		JSONArray results = new JSONArray();
		Connection conn = null;
	    PreparedStatement pst = null;
	    ResultSet rs = null;

		try {
			
			 conn = pgds.getConnection();
			 pst = conn.prepareStatement("SELECT * FROM tbl_sim WHERE modelid = ? and userid = ? ORDER BY simid DESC LIMIT ?;");
			 
			 pst.setInt(1, modelId);
			 pst.setInt(2, userId);
			 pst.setInt(3, maxNum);
			 
			 rs = pst.executeQuery();
			 
			 while(rs!=null && rs.next()){
				 JSONObject sim = new JSONObject();
				 sim.put("simid", rs.getInt("simid"));
				 sim.put("simcode", "job_"+rs.getInt("simid"));
				 sim.put("simpara", new JSONObject(rs.getString("simoptions")));
				 sim.put("simstate", rs.getInt("simstate"));
				 sim.put("data", new JSONObject(rs.getString("simoutputs")));
				 results.put(sim);
			 }
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally{
			
			try {
                if (rs != null) {
                    rs.close();
                }
                if (pst != null) {
                    pst.close();
                }
                if (conn != null) {
                    conn.close();
                }

            } catch (SQLException ex) {
                
            }
		}

		return results;
	}
	
	/**
	 * Get specific output data for a specific simid
	 * @param simId
	 * @param columnName: currently can be either 'simoutputs' or 'simextraoutputs'
	 * @return a JSON object containing the output data
	 */
	public static JSONObject getSimOutputs(int simId, String columnName){
		
		JSONObject result = null;
		Connection conn = null;
	    PreparedStatement pst = null;
	    ResultSet rs = null;

		try {
			
			 conn = pgds.getConnection();
			
			 pst = conn.prepareStatement("SELECT "+columnName+" FROM tbl_sim WHERE simid = ?;");
			 
			 pst.setInt(1, simId);
			 
			 rs = pst.executeQuery();
			 
			 while(rs!=null && rs.next()){

				 result = new JSONObject(rs.getString(columnName));
				
			 }
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally{
			
			try {
                if (rs != null) {
                    rs.close();
                }
                if (pst != null) {
                    pst.close();
                }
                if (conn != null) {
                    conn.close();
                }

            } catch (SQLException ex) {
                
            }
		}

		return result;
	}
	
	/**
	 * Get modelid for a specific simid
	 * @param simId
	 * @return
	 */
	public static int getSimModelId(int simId){
		
		int modelid = -1;
		Connection conn = null;
	    PreparedStatement pst = null;
	    ResultSet rs = null;

		try {
			
			 conn = pgds.getConnection();
			
			 pst = conn.prepareStatement("SELECT modelid FROM tbl_sim WHERE simid = ?;");
			 
			 pst.setInt(1, simId);
			 
			 rs = pst.executeQuery();
			 
			 while(rs!=null && rs.next()){

				 modelid = rs.getInt("modelid");
				
			 }
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally{
			
			try {
                if (rs != null) {
                    rs.close();
                }
                if (pst != null) {
                    pst.close();
                }
                if (conn != null) {
                    conn.close();
                }

            } catch (SQLException ex) {
                
            }
		}

		return modelid;
	}
	/**
	 * Get job(sim) input params for a specific simid
	 * @param simId
	 * @return a JSON object containing the input params data
	 */
	public static JSONObject getSimOptions(int simId){
		
		JSONObject options = null;
		Connection conn = null;
	    PreparedStatement pst = null;
	    ResultSet rs = null;

		try {
			
			 conn = pgds.getConnection();
			
			 pst = conn.prepareStatement("SELECT simoptions FROM tbl_sim WHERE simid = ?;");
			 
			 pst.setInt(1, simId);
			 
			 rs = pst.executeQuery();
			 
			 while(rs!=null && rs.next()){

				 options = new JSONObject(rs.getString("simoptions"));
				
			 }
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally{
			
			try {
                if (rs != null) {
                    rs.close();
                }
                if (pst != null) {
                    pst.close();
                }
                if (conn != null) {
                    conn.close();
                }

            } catch (SQLException ex) {
                
            }
		}

		return options;
	}

	/**
	 * Initialize a job(sim) record based on client defined params  
	 * @param options: client defined params 
	 * @return the newly create job(sim) id.   
	 */
	public static int createSim(JSONObject options){
		
		//TODO :check options if valid
		
		Connection conn = null;
	    PreparedStatement pst = null;
	    ResultSet rs = null;
	    int newSimId =  -1;
		try {
			
			 conn = pgds.getConnection();
			
			 pst = conn.prepareStatement("INSERT INTO tbl_sim(userid, modelid, simoptions) VALUES (?, ?, ?) returning simid;");
			 
			 pst.setInt(1, options.getInt("userid"));
			 pst.setInt(2, options.getInt("modelid"));
			 pst.setString(3, options.getString("options"));
			 rs = pst.executeQuery();
			
			 if(rs!=null && rs.next()){
				 LOGGER.info("==== new inserted simid:{}", rs.getInt("simid"));
				 newSimId = rs.getInt("simid");
			 }
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally{
			
			try {
                if (rs != null) {
                    rs.close();
                }
                if (pst != null) {
                    pst.close();
                }
                if (conn != null) {
                    conn.close();
                }

            } catch (SQLException ex) {
                
            }
		}

		return newSimId;
	}
	
	 public static boolean execSQL(String sql){
		 Connection conn = null;
		    PreparedStatement pst = null;
		    boolean flag= false;
			try {
				
				 conn = pgds.getConnection();
				 
				 Statement st = conn.createStatement();
				 st.execute(sql);
				 flag = true;
				 
			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} finally{
				
				try {
	                if (pst != null) {
	                    pst.close();
	                }
	                if (conn != null) {
	                    conn.close();
	                }
	            } catch (SQLException ex) {
	                
	            }
				finally{
					
				}
			}

			return flag;
		 
	 }
	/**
	 * Update the processing state of a job(sim) for a specific simid
	 * @param simid
	 * @param state
	 * @return success or not
	 */
    public static boolean updateSimState(int simid, int state){
		
		//TODO :check options if valid
		
		Connection conn = null;
	    PreparedStatement pst = null;
	    boolean flag= false;
		try {
			
			 conn = pgds.getConnection();
			
			 pst = conn.prepareStatement("update tbl_sim set simstate=? where simid = ?");
			 
			 pst.setInt(1, state);
			 pst.setInt(2,simid);
			 pst.execute();
			 
			 flag = true;
			 
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally{
			
			try {
                if (pst != null) {
                    pst.close();
                }
                if (conn != null) {
                    conn.close();
                }
            } catch (SQLException ex) {
                
            }
			finally{
				
			}
		}

		return flag;
	}
    
	/**
	 * Update specific output data for a specific simid
	 * @param simid
	 * @param outputs
	 * @param columnName: currently can be either 'simoutputs' or 'simextraoutputs'
	 * @return success or not
	 */
	public static boolean updateSimResult(int simid, Object outputs, String columnName){
		
		//TODO :check options if valid
		
		Connection conn = null;
	    PreparedStatement pst = null;
	    boolean flag= false;
		try {
			
			 conn = pgds.getConnection();
			
			 pst = conn.prepareStatement("update tbl_sim set "+columnName+"=? where simid = ?");
			 
			 pst.setString(1, outputs.toString());
			 pst.setInt(2,simid);
			 pst.execute();
			 
			 flag = true;
			 
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally{
			
			try {
                if (pst != null) {
                    pst.close();
                }
                if (conn != null) {
                    conn.close();
                }
            } catch (SQLException ex) {
                
            }
			finally{
				
			}
		}

		return flag;
	}
	
	/**
	 * Check if user login is successful or not
	 * @param username
	 * @param pswd
	 * @return a JSON object containing user info. 
	 */
	public static JSONObject userLogin(String username, String pswd){
		
		
		JSONObject result = new JSONObject();
		Connection conn = null;
	    PreparedStatement pst = null;
	    ResultSet rs = null;
	    
		try {
			
		    result.put("userid", -1);
		    result.put("username", "");
			
			 conn = pgds.getConnection();
			
			 pst = conn.prepareStatement("SELECT userid, username FROM tbl_user WHERE username = ? and userpswd = ?;");
			 
			 pst.setString(1, username);
			 pst.setString(2, pswd);
			 
			 rs = pst.executeQuery();
			 
			 while(rs!=null && rs.next()){
				 
				 result.put("userid", rs.getInt("userid"));
				 result.put("username", rs.getString("username"));
				
			 }
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally{
			
			try {
                if (rs != null) {
                    rs.close();
                }
                if (pst != null) {
                    pst.close();
                }
                if (conn != null) {
                    conn.close();
                }

            } catch (SQLException ex) {
                
            }
		}

		return result;
	}
	
	
	/**
	 * Initialize a ProcessingLog record  
	 * @param options: processing details
	 * @return the newly create ProcessingLog id.   
	 */
	public static int createProcessingLog(JSONObject options){
		
		//TODO :check options if valid
		
		Connection conn = null;
	    PreparedStatement pst = null;
	    ResultSet rs = null;
	    int newlogid =  -1;
		try {
			
			 conn = pgds.getConnection();
			
			 pst = conn.prepareStatement("INSERT INTO tbl_processing_log(job_id, block_code, block_status, block_tbl_name) VALUES (?, ?, ?, ?) returning logid;");
			 
			 pst.setInt(1, options.getInt("job_id"));
			 pst.setString(2, options.getString("block_code"));
			 pst.setInt(3, options.getInt("block_status"));
			 pst.setString(4, options.getString("block_tbl_name"));
			 rs = pst.executeQuery();
			
			 if(rs!=null && rs.next()){
				 LOGGER.info("==== create processinglog id:{}", rs.getInt("logid"));
				 newlogid = rs.getInt("logid");
			 }
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally{
			
			try {
                if (rs != null) {
                    rs.close();
                }
                if (pst != null) {
                    pst.close();
                }
                if (conn != null) {
                    conn.close();
                }

            } catch (SQLException ex) {
                
            }
		}

		return newlogid;
	}
	
	public static void updateProcessingLog(JSONObject options){
		
		//TODO :check options if valid
		
		Connection conn = null;
	    PreparedStatement pst = null;
	   
		try {
			
			 conn = pgds.getConnection();
			
			 pst = conn.prepareStatement("Update tbl_processing_log set block_status = ?, loginfo = ? where logid = ? ;");
			 
			 pst.setInt(1, options.getInt("block_status"));
			 pst.setString(2, options.getString("loginfo"));
			 pst.setInt(3, options.getInt("logid"));
			 pst.execute();
			
			LOGGER.info("==== update processinglog id:{}, loginfo:{}", options.getInt("logid"), options.getString("loginfo"));
				 

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally{
			
			try {
                
                if (pst != null) {
                    pst.close();
                }
                if (conn != null) {
                    conn.close();
                }

            } catch (SQLException ex) {
                
            }
		}

	}
	
	public static JSONArray getParcels(String strCriteria, String strRegions){
		
		
		
		JSONArray results = new JSONArray();
		Connection conn = null;
	    PreparedStatement pst = null;
	    ResultSet rs = null;

		try {
			
			 conn = pgds.getConnection();
			 pst = conn.prepareStatement("SELECT nb_searchparcels_withregions(?,?) as o_fid;");
			 
			 pst.setString(1, strCriteria);
			 pst.setString(2, strRegions);
			 
			 rs = pst.executeQuery();
			 
			 while(rs!=null && rs.next()){

				 results.put(rs.getInt("o_fid"));
			 }
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}finally{
			
			try {
                if (rs != null) {
                    rs.close();
                }
                if (pst != null) {
                    pst.close();
                }
                if (conn != null) {
                    conn.close();
                }

            } catch (SQLException ex) {
                
            }
		}

		return results;
	}
	
	/**
	 * get a list of suburb which intersects with any unprocessed properties (unprocesssed means these properties are computed for poi route info, whose status = 0)
	 * @return
	 */
	public static JSONArray getIntersectedSuburbs(String travelType){
		
		JSONArray results = new JSONArray();
		Connection conn = null;
	    PreparedStatement pst = null;
	    ResultSet rs = null;

		try {
			
			 conn = pgds.getConnection();
			 pst = conn.prepareStatement("select * from nb_get_intersected_ssc_for_unprocessed_property(?);");

			 pst.setString(1, travelType);
			 
			 rs = pst.executeQuery();
			 
			 while(rs!=null && rs.next()){
				 JSONObject suburb = new JSONObject();
				 suburb.put("ssc_code", rs.getString("ssc_code"));
				 suburb.put("mga_code", rs.getInt("mga_code"));
				 
				 results.put(suburb);
			 }
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally{
			
			try {
                if (rs != null) {
                    rs.close();
                }
                if (pst != null) {
                    pst.close();
                }
                if (conn != null) {
                    conn.close();
                }

            } catch (SQLException ex) {
                
            }
		}

		return results;
	}
	
	/**
	 * get a list of suburb which intersects with any unprocessed properties (unprocesssed means these properties are computed for poi route info, whose status = 0)
	 * @return
	 */
	public static JSONArray getSuburbsByGccCode(String gccCode){
		
		JSONArray results = new JSONArray();
		Connection conn = null;
	    PreparedStatement pst = null;
	    ResultSet rs = null;

		try {
			
			 conn = pgds.getConnection();
			 pst = conn.prepareStatement("select * from nb_get_ssc_by_gcc_code(?);");

			 pst.setString(1, gccCode);
			 
			 rs = pst.executeQuery();
			 
			 while(rs!=null && rs.next()){
				 JSONObject suburb = new JSONObject();
				 suburb.put("ssc_code", rs.getString("ssc_code"));
				 suburb.put("mga_code", rs.getInt("mga_code"));
				 
				 results.put(suburb);
			 }
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally{
			
			try {
                if (rs != null) {
                    rs.close();
                }
                if (pst != null) {
                    pst.close();
                }
                if (conn != null) {
                    conn.close();
                }

            } catch (SQLException ex) {
                
            }
		}

		return results;
	}
	
	/**
	 * get a list of poi layer table info, include tablename, maincat_id)
	 * @return
	 */
	public static JSONArray getPoiLayersInfo(String poiTableNames){
		
		JSONArray results = new JSONArray();
		Connection conn = null;
	    PreparedStatement pst = null;
	    ResultSet rs = null;

		try {
			
			 conn = pgds.getConnection();
			 if(poiTableNames.equalsIgnoreCase("")){
				 pst = conn.prepareStatement("select * from tbl_poi_maincat;");
			 }else
			 {
				 pst = conn.prepareStatement("select * from tbl_poi_maincat where poi_tablename in ("+poiTableNames+");");
			 }
			 rs = pst.executeQuery();
			 
			 while(rs!=null && rs.next()){
				 JSONObject poiInfo = new JSONObject();
				 poiInfo.put("poi_tablename", rs.getString("poi_tablename"));
				 poiInfo.put("maincat_id", rs.getInt("maincat_id"));
				 
				 results.put(poiInfo);
			 }
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally{
			
			try {
                if (rs != null) {
                    rs.close();
                }
                if (pst != null) {
                    pst.close();
                }
                if (conn != null) {
                    conn.close();
                }

            } catch (SQLException ex) {
                
            }
		}

		return results;
	}
	
	public static boolean updatePropertyProcessStatus(int propertyId, int status, String travelType){
		
		//TODO :check options if valid
		
		Connection conn = null;
	    PreparedStatement pst = null;
	    boolean flag= false;
		try {
			
			 conn = pgds.getConnection();
			 if(travelType.equalsIgnoreCase("walk")){
				 pst = conn.prepareStatement("update tbl_property set proc_status_walk=? where property_id = ?");
			 }
			 else{
				 pst = conn.prepareStatement("update tbl_property set proc_status_drive=? where property_id = ?");
			 }
			 pst.setInt(1, propertyId);
			 pst.setInt(2, status);
			 pst.execute();
			 
			 flag = true;
			 
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally{
			
			try {
                if (pst != null) {
                    pst.close();
                }
                if (conn != null) {
                    conn.close();
                }
            } catch (SQLException ex) {
                
            }
			finally{
				
			}
		}

		return flag;
	}
	
	/**
	 * delete RouteInfo Records by suburb code from a specific table : tbl_*_route_info_* 
	 * @param options
	 * @return
	 */
	public static boolean deleteRouteInfoRecords(JSONObject options){
		
		//TODO :check options if valid
		
		Connection conn = null;
	    PreparedStatement pst = null;
	    boolean flag= false;
		try {
			
			String travelType = options.getString("travelType");
			String ssc_code = options.getString("ssc_code");
			String processingType = options.getString("processingType");
			
			 conn = pgds.getConnection();
			 
			 pst = conn.prepareStatement("delete from tbl"+processingType+"route_info_"+travelType+" where ssc_code = ?");
			 
			 pst.setString(1, ssc_code);
			 
			 pst.execute();
			 
			 flag = true;
			 
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally{
			
			try {
                if (pst != null) {
                    pst.close();
                }
                if (conn != null) {
                    conn.close();
                }
            } catch (SQLException ex) {
                
            }
			finally{
				
			}
		}

		return flag;
	}
	
	public static JSONObject getLocationSscCode(String locationcoord, String locationtype){
		
		JSONObject result = null;
		Connection conn = null;
	    PreparedStatement pst = null;
	    ResultSet rs = null;

		try {
			
			 String coord[] = locationcoord.split(",");
			
			 conn = pgds.getConnection();
			 pst = conn.prepareStatement("SELECT nb_get_locationid_ssc_code(?,?,?);");
			 
			 pst.setString(1, locationtype);
			 pst.setDouble(2, Double.parseDouble(coord[0]));
			 pst.setDouble(3, Double.parseDouble(coord[1]));
			 
			 rs = pst.executeQuery();
			 
			 while(rs!=null && rs.next()){
				 result = new JSONObject(rs.getString(1));
			 }
			 
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally{
			
			try {
                if (rs != null) {
                    rs.close();
                }
                if (pst != null) {
                    pst.close();
                }
                if (conn != null) {
                    conn.close();
                }

            } catch (SQLException ex) {
                
            }
		}

		return result;
	}
	
	public static JSONArray getLocationInspectionStatsByPoiSubtypes(JSONObject options, JSONObject poiOptions){
		
		JSONArray results = null;
		Connection conn = null;
	    PreparedStatement pst = null;
	    ResultSet rs = null;

		try {
			
			 conn = pgds.getConnection();
			 pst = conn.prepareStatement("SELECT nb_lis_get_stats_by_poisubtypes(?,?,?,?,?,?);");
			 
			 pst.setString(1, options.getString("locationtype"));
			 pst.setInt(2, options.getInt("locationid"));
			 pst.setString(3, options.getString("traveltype"));
			 pst.setInt(4, options.getInt("poi_search_radius"));
			 pst.setString(5, poiOptions.getString("poi_subtype_ids"));
			 pst.setString(6, poiOptions.getString("poi_subtype_weights"));
			 
			 rs = pst.executeQuery();
			 
			 while(rs!=null && rs.next()){
				 results = new JSONArray(rs.getString(1));
			 }
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally{
			
			try {
                if (rs != null) {
                    rs.close();
                }
                if (pst != null) {
                    pst.close();
                }
                if (conn != null) {
                    conn.close();
                }

            } catch (SQLException ex) {
                
            }
		}

		return results;
	}
	
	
	public static JSONObject verifyAPIAuthorisation(String key, String apiServiceName){
		
		
		JSONObject result = new JSONObject();
		Connection conn = null;
	    PreparedStatement pst = null;
	    ResultSet rs = null;
	    
		try {
			
		    result.put("usr_id", -1);
		    result.put("api_id", -1);
			
			 conn = pgds.getConnection();
			
			 pst = conn.prepareStatement("SELECT usr_id, api_id FROM api_users, api_servicetypes WHERE key = ? and api_name = ?;");
			 
			 pst.setString(1, key);
			 pst.setString(2, apiServiceName);
			 
			 rs = pst.executeQuery();
			 
			 while(rs!=null && rs.next()){
				 
				 result.put("usr_id", rs.getInt("usr_id"));
				 result.put("api_id", rs.getInt("api_id"));
				
			 }
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} finally{
			
			try {
                if (rs != null) {
                    rs.close();
                }
                if (pst != null) {
                    pst.close();
                }
                if (conn != null) {
                    conn.close();
                }

            } catch (SQLException ex) {
                
            }
		}

		return result;
	}
	
	public static JSONObject logAPIRequest(int usr_id, int api_id, String ip_addr, String request_url, String request_params){
		
		
		JSONObject result = new JSONObject();
		Connection conn = null;
	    PreparedStatement pst = null;
	    ResultSet rs = null;
	    
		try {
			
			 conn = pgds.getConnection();
			
			 pst = conn.prepareStatement("INSERT INTO public.api_logs(usr_id, ip_addr, request_url, api_id, request_params)VALUES (?, ?, ?, ?, ?) returning log_id;");
			 
			 pst.setInt(1, usr_id);
			 pst.setString(2, ip_addr);
			 pst.setString(3, request_url);
			 pst.setInt(4, api_id);
			 pst.setString(5, request_params);
			 
			 rs = pst.executeQuery();
			 
			 while(rs!=null && rs.next()){

			 }
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}  finally{
			
			try {
                if (rs != null) {
                    rs.close();
                }
                if (pst != null) {
                    pst.close();
                }
                if (conn != null) {
                    conn.close();
                }

            } catch (SQLException ex) {
                
            }
		}

		return result;
	}
}
