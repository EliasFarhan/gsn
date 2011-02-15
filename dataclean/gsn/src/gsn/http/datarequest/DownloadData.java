package gsn.http.datarequest;

import gsn.beans.StreamElement;
import gsn.http.MultiDataDownload;
import gsn.storage.DataEnumerator;
import gsn.storage.StorageManager;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

public class DownloadData extends AbstractDataRequest {

	private static transient Logger logger 			= Logger.getLogger(MultiDataDownload.class);

	private static final String PARAM_OUTPUT_TYPE 	= "outputtype";

	public enum AllowedOutputType {
		csv,
		xml
	}

	private AllowedOutputType ot;

	private String csvDelimiter	= ",";

	public DownloadData(Map<String, String[]> requestParameters) throws DataRequestException {
		super(requestParameters);
	}

	@Override
	public void process() throws DataRequestException {
		String outputType = QueriesBuilder.getParameter(requestParameters, PARAM_OUTPUT_TYPE);

		try {
			if (outputType == null) {
				throw new DataRequestException ("The following >" + PARAM_OUTPUT_TYPE + "< parameter is missing in your query.") ;
			}

			ot = AllowedOutputType.valueOf(outputType);

			if (ot == AllowedOutputType.csv) {
				//
				if (QueriesBuilder.getParameter(requestParameters, "delimiter") != null && ! QueriesBuilder.getParameter(requestParameters, "delimiter").equals("")) {
					String reqdelimiter = QueriesBuilder.getParameter(requestParameters, "delimiter");
					if (reqdelimiter.equals("tab")) {
						csvDelimiter = "\t";
					} else if (reqdelimiter.equals("space")){
						csvDelimiter = " ";
					} else if (reqdelimiter.equals("semicolon")){
						csvDelimiter = ";";
					} else if (reqdelimiter.equals("other") && QueriesBuilder.getParameter(requestParameters, "otherdelimiter") != null && ! QueriesBuilder.getParameter(requestParameters, "otherdelimiter").equals("")) {
						csvDelimiter = QueriesBuilder.getParameter(requestParameters, "otherdelimiter");
					}
				}
			}
		}
		catch (IllegalArgumentException e) {
			throw new DataRequestException ("The >" + outputType + "< output type is not supported.") ;
		}
	}

	public String outputResult () {
		ByteArrayOutputStream baos = new ByteArrayOutputStream () ;
		outputResult(baos);
		return baos.toString();
	}

	@Override
	public void outputResult(OutputStream os) {
		PrintWriter respond = new PrintWriter(os);
		Iterator<Entry<String, AbstractQuery>> iter = qbuilder.getSqlQueries().entrySet().iterator();
		Entry<String, AbstractQuery> nextSqlQuery;
		DataEnumerator de;
		if (ot == AllowedOutputType.xml) {
			respond.println("<result>");
		}
		while (iter.hasNext()) {
			nextSqlQuery = iter.next();
			Connection connection = null;
			try {
				connection = StorageManager.getInstance().getConnection();
				de = StorageManager.getInstance().executeQuery(nextSqlQuery.getValue(), false,  connection);
				logger.debug("Data Enumerator: " + de); 
				if (ot == AllowedOutputType.csv) {
					respond.println("##vsname:" + nextSqlQuery.getKey());
					respond.println("##query:" + nextSqlQuery.getValue().getStandardQuery() + (nextSqlQuery.getValue().getLimitCriterion() == null ? "" : "(" + nextSqlQuery.getValue().getLimitCriterion() + ")"));
				}
				else if (ot == AllowedOutputType.xml) {
					respond.println("\t<!-- " + nextSqlQuery.getValue().getStandardQuery() + " -->");
					respond.println("\t<data vsname=\"" + nextSqlQuery.getKey() + "\">");
				}
				FieldsCollection fc = qbuilder.getVsnamesAndStreams().get(nextSqlQuery.getKey());
				//boolean wantTimed = fc != null ? fc.isWantTimed() : false;
				boolean wantTimed = true;
				boolean firstLine = true;
				while (de.hasMoreElements()) {
					if (ot == AllowedOutputType.csv) {
						formatCSVElement(respond, de.nextElement(), wantTimed, csvDelimiter, firstLine);
					}
					else if	(ot == AllowedOutputType.xml) {
						formatXMLElement(respond, de.nextElement(), wantTimed, firstLine);
					}
					firstLine = false;
				}
				if (ot == AllowedOutputType.xml) respond.println("\t</data>");	
			} catch (SQLException e) {
				logger.debug(e.getMessage());
			}finally{
				StorageManager.close(connection);
			}
		}
		if (ot == AllowedOutputType.xml) {
			respond.println("</result>");
		}
		respond.flush();
	}


	private void formatCSVElement (PrintWriter respond, StreamElement se, boolean wantTimed, String cvsDelimiter, boolean firstLine) {
		if (firstLine) {
			respond.print("#");
			for (int i = 0 ; i < se.getData().length ; i++) {
				respond.print(se.getFieldNames()[i]);
				if (i != se.getData().length - 1) respond.print(cvsDelimiter);
			}
			if (wantTimed && se.getData().length != 0) respond.print(cvsDelimiter);
			if (wantTimed) respond.print("timed");
			respond.println();
		}
		for (int i = 0 ; i < se.getData().length ; i++) {
			respond.print(se.getData()[i]);
			if (i != se.getData().length - 1) respond.print(cvsDelimiter); 
		}
		if (wantTimed) {
			if (se.getData().length != 0) respond.print(cvsDelimiter);
			respond.print( qbuilder.getSdf() == null ? se.getTimeStamp() : qbuilder.getSdf().format(new Date(se.getTimeStamp())));
		}
		respond.println();
	}

	private void formatXMLElement (PrintWriter respond, StreamElement se, boolean wantTimed, boolean firstLine) {
		if (firstLine) {
			respond.println("\t\t<header>");
			for (int i = 0 ; i < se.getData().length ; i++) {
				respond.println("\t\t\t<field>" + se.getFieldNames()[i] + "</field>");
			}
			if (wantTimed) respond.println("\t\t\t<field>timed</field>");
			respond.println("\t\t</header>");
		}
		respond.println("\t\t<tuple>");
		for (int i = 0 ; i < se.getData().length ; i++) {
			respond.println("\t\t\t<field>" + se.getData()[i] + "</field>");
		}
		if (wantTimed) respond.println("\t\t\t<field>" + ( qbuilder.getSdf() == null ? se.getTimeStamp() : qbuilder.getSdf().format(new Date(se.getTimeStamp()))) + "</field>");
		respond.println("\t\t</tuple>");
	}

	public AllowedOutputType getOt() {
		return ot;
	}
}