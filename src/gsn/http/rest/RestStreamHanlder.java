package gsn.http.rest;

import gsn.DataDistributer;
import gsn.Mappings;
import gsn.beans.VSensorConfig;
import gsn.storage.SQLUtils;
import gsn.storage.SQLValidator;
import gsn.utils.Helpers;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.StringTokenizer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;
import org.eclipse.jetty.continuation.Continuation;
import org.eclipse.jetty.continuation.ContinuationSupport;

public class RestStreamHanlder extends HttpServlet {

	public static final int SUCCESS_200 = 200;

	private static final int _300 = 300;

	private static final String STREAMING = "/streaming/";

	private static transient Logger       logger     = Logger.getLogger ( RestStreamHanlder.class );

	public void doGet ( HttpServletRequest request , HttpServletResponse response ) throws ServletException{

		Object is2ndPass = request.getAttribute("2ndPass");
		Continuation continuation = ContinuationSupport.getContinuation(request);

		if(is2ndPass == null) {
			final DefaultDistributionRequest streamingReq;
			try {
				URLParser parser = new URLParser(request);
				RestDelivery deliverySystem = new RestDelivery(continuation);
				streamingReq = DefaultDistributionRequest.create(deliverySystem, parser.getVSensorConfig(), parser.getQuery(), parser.getStartTime());
				logger.debug("Rest request received: "+streamingReq.toString());
				DataDistributer.getInstance(deliverySystem.getClass()).addListener(streamingReq);
				logger.debug("Streaming request received and registered:"+streamingReq.toString());
				continuation.suspend();
			}catch (Exception e) {
				logger.warn(e.getMessage(),e);
				return ;
			}
		}else {
			try {
				if (!response.getWriter().checkError()) {
					continuation.suspend();	
				}
			}catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	/**
	 * This happens at the server
	 */
	public void doPost ( HttpServletRequest request , HttpServletResponse response ) throws ServletException  {
		try {
			URLParser parser = new URLParser(request);
			Double notificationId = Double.parseDouble(request.getParameter(PushDelivery.NOTIFICATION_ID_KEY));
			String localContactPoint = request.getParameter(PushDelivery.LOCAL_CONTACT_POINT);
			if (localContactPoint == null) {
				logger.warn("Push streaming request received without "+PushDelivery.LOCAL_CONTACT_POINT+" parameter !");
				return;
			}
			//checking to see if there is an already registered notification id, in that case, we ignore (re)registeration.
			PushDelivery delivery = new PushDelivery(localContactPoint,notificationId,response.getWriter());

			boolean isExist = DataDistributer.getInstance(delivery.getClass()).contains(delivery);
			if (isExist) {
				logger.debug("Keep alive request received for the notification-id:"+notificationId);
				response.setStatus(SUCCESS_200);
				delivery.close();
				return;
			}

			DefaultDistributionRequest distributionReq = DefaultDistributionRequest.create(delivery, parser.getVSensorConfig(), parser.getQuery(), parser.getStartTime());
			logger.debug("Rest request received: "+distributionReq.toString());
			DataDistributer.getInstance(delivery.getClass()).addListener(distributionReq);
			logger.debug("Streaming request received and registered:"+distributionReq.toString());
		}catch (Exception e) {
			logger.warn(e.getMessage(),e);
			return ;
		}
	}
	/**
	 * This happens at the client
	 */
	public void doPut( HttpServletRequest request , HttpServletResponse response ) throws ServletException  {
		double notificationId = Double.parseDouble(request.getParameter(PushDelivery.NOTIFICATION_ID_KEY));
		PushRemoteWrapper notification = NotificationRegistry.getInstance().getNotification(notificationId);
		try {
			if (notification!=null) {
				notification.manualDataInsertion(request.getParameter(PushDelivery.DATA));
				response.setStatus(SUCCESS_200);
			}else {
				logger.warn("Received a Http put request for an INVALID notificationId: " + notificationId);
				response.sendError(_300);
			}
		} catch (IOException e) {
			logger.warn("Failed in writing the status code into the connection.\n"+e.getMessage(),e);
		}
	}

	class URLParser{
		private String query,tableName;
		private long startTime;
		private VSensorConfig config;
		public URLParser(HttpServletRequest request) throws UnsupportedEncodingException, Exception {
			String requestURI = request.getRequestURI().substring(request.getRequestURI().toLowerCase().indexOf(STREAMING)+STREAMING.length());
			StringTokenizer tokens = new StringTokenizer(requestURI,"/");
			startTime = System.currentTimeMillis();
			query = tokens.nextToken();
			query = URLDecoder.decode(query,"UTF-8");
			if (tokens.hasMoreTokens()) 
				startTime= Helpers.convertTimeFromIsoToLong(URLDecoder.decode(tokens.nextToken(),"UTF-8"));
			tableName = SQLValidator.getInstance().validateQuery(query);
			if (tableName==null)
				throw new RuntimeException("Bad Table name in the query:"+query);
			/** IMPORTANT: We change the table names to lower-case as some databases (e.g., MySQL on linux) are case sensitive and in 
			 * general we use lower case for table names in GSN. **/
			tableName=tableName.trim();
			query = SQLUtils.newRewrite(query, tableName, tableName.toLowerCase()).toString();
			tableName=tableName.toLowerCase();
			config = Mappings.getConfig(tableName);
		}
		public VSensorConfig getVSensorConfig() {
			return config;
		}
		public String getQuery() {
			return query;
		}
		public long getStartTime() {
			return startTime;
		}

		public String getTableName() {
			return tableName;
		}


	}

}


