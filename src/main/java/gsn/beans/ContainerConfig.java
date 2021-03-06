/**
* Global Sensor Networks (GSN) Source Code
* Copyright (c) 2006-2014, Ecole Polytechnique Federale de Lausanne (EPFL)
* 
* This file is part of GSN.
* 
* GSN is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
* 
* GSN is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
* 
* You should have received a copy of the GNU General Public License
* along with GSN.  If not, see <http://www.gnu.org/licenses/>.
* 
* File: src/gsn/beans/ContainerConfig.java
*
* @author gsn_devs
* @author Ali Salehi
* @author Behnaz Bostanipour
* @author Timotee Maret
* @author Julien Eberle
*
*/

package gsn.beans;

import gsn.config.GsnConf;
import gsn.utils.KeyValueImp;
import gsn.utils.ValidityTools;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;
import java.util.StringTokenizer;

import org.antlr.stringtemplate.StringTemplate;
import org.antlr.stringtemplate.StringTemplateGroup;
import org.apache.log4j.helpers.OptionConverter;
import org.jibx.runtime.BindingDirectory;
import org.jibx.runtime.IBindingFactory;
import org.jibx.runtime.IUnmarshallingContext;
import org.jibx.runtime.JiBXException;

public class ContainerConfig {
	
	public static final String [ ]      LOGGING_LEVELS                     = { "DEBUG" , "INFO" , "WARN" , "ERROR" };
	public static final String [ ]      JDBC_SYSTEMS                       = { "H2 in Memory" , "H2 in File" , "MySql", "SQL Server" };
	public static final String [ ]      JDBC_URLS                          = new String [ ] { "jdbc:h2:mem:." , "jdbc:h2:file:/path/to/file" , "jdbc:mysql://localhost:3306/gsn", "jdbc:jtds:sqlserver://localhost/gsn" };
	public static final String [ ]      JDBC_DRIVERS                       = new String [ ] { "org.h2.Driver" , "org.h2.Driver" , "com.mysql.jdbc.Driver", "net.sourceforge.jtds.jdbc.Driver" };
	public static final String [ ]      JDBC_URLS_PREFIX                   = new String [ ] { "jdbc:h2:mem:" , "jdbc:h2:file:" , "jdbc:mysql:", "jdbc:jtds:sqlserver:" };

	public static final String            NOT_PROVIDED                     = "Not Provided";
	public static final int               DEFAULT_GSN_PORT                 = 22001;
    public static final int               DEFAULT_SSL_PORT                 = 8443;
	public static final int               DEFAULT_ZMQ_PROXY_PORT           = 22022;
	public static final int               DEFAULT_ZMQ_META_PORT            = 22023;
	public static final boolean           DEFAULT_ZMQ_ENABLED               = false;
	public static final String            DEFAULT_LOGGING_LEVEL            = ContainerConfig.LOGGING_LEVELS[ 3 ];
	public static final String            DEFAULT_WEB_NAME                 = "NoName.";
	public static final String            DEFAULT_WEB_AUTHOR               = "Author not specified.";
	public static final String            DEFAULT_WEB_EMAIL                = "Email not specified.";
	public static final String            DEFAULT_DIRECTORY_LOG_FILENAME   = "gsn-dir.log";
	public static final String            DEFAULT_GSN_LOG_FILENAME         = "gsn.log";
	public static final int               DEFAULT_DIRECTORY_PORT           = 1882;  //really used ??
	/**
	 * One Megabyte;
	 */
	public static final long              DEFAULT_GSN_LOG_SIZE             = 10 * 1024 * 1024;
    private static final String           DEFAULT_SSL_KEYSTORE_PWD         = "changeit";
    private static final String           DEFAULT_SSL_KEY_PWD              = "changeit";
    private static final String           DEFAULT_SSL_KEYSTORE             = "conf/servertestkeystore";
	public static final String            FIELD_NAME_gsnPortNo             = "containerPort";
	public static final String            FIELD_NAME_zmqEnabled            = "zmqEnabled";
	public static final String            FIELD_NAME_zmqProxyPort          = "zmqProxyPort";
	public static final String            FIELD_NAME_zmqMetaPort           = "zmqMetaPort";
	public static final String            FIELD_NAME_webName               = "webName";
	public static final String            FIELD_NAME_webAuthor             = "webAuthor";
	public static final String            FIELD_NAME_webDescription        = "webDescription";
	public static final String            FIELD_NAME_webEmail              = "webEmail";
	public static final String            FIELD_NAME_directoryLoggingLevel = "directoryLoggingLevel";
	public static final String          FIELD_NAME_maxDirectoryLogSizeInMB = "maxDirectoryLogSizeInMB";
	public static final String          FIELD_NAME_gsnLoggingLevel         = "gsnLoggingLevel";
	public static final String          FIELD_NAME_maxGSNLogSizeInMB       = "maxGSNLogSizeInMB";
	public static final String          FIELD_NAME_directoryLogFileName    = "directoryLogFileName";
	public static final String          FIELD_NAME_directoryPortNo         = "directoryPortNo";
	public static final String          FIELD_NAME_gsnLogFileName          = "gsnLogFileName";
	public static final String          FIELD_NAME_directoryServiceHost    = "directoryServiceHost";
	public static final String          FIELD_NAME_databaseSystem          = "databaseSystem";

	
	protected String                      webName                          = DEFAULT_WEB_NAME;
    protected String                      webAuthor                        = DEFAULT_WEB_AUTHOR;
    protected String                      webDescription;
	protected String                      webEmail                         = DEFAULT_WEB_EMAIL;
	protected String                      mailServer;
	protected String                      smsServer;
	protected String                      smsPassword;
	protected int                         containerPort                    = DEFAULT_GSN_PORT;
	protected boolean                     zmqEnabled                       = DEFAULT_ZMQ_ENABLED;
	protected int                         zmqProxyPort                     = DEFAULT_ZMQ_PROXY_PORT;
	protected int                         zmqMetaPort                      = DEFAULT_ZMQ_META_PORT;
	protected String                      containerFileName;
	protected int                         storagePoolSize                  = -1;

	private int                           sslPort                          = -1;
    private boolean                       acEnabled                        = false;
	private String                        sslKeyStorePassword;
	private String                        sslKeyPassword;
	private String						  sslKeyStore                      = DEFAULT_SSL_KEYSTORE;
    private StorageConfig                 storage ;
    private SlidingConfig                 sliding;
	private String                        directoryLoggingLevel            = DEFAULT_LOGGING_LEVEL;
	private long                          maxDirectoryLogSizeInMB          = DEFAULT_GSN_LOG_SIZE;
	private String                        gsnLoggingLevel                  = DEFAULT_LOGGING_LEVEL;
	private long                          maxGSNLogSizeInMB                = DEFAULT_GSN_LOG_SIZE;
	private String                        directoryLogFileName             = DEFAULT_DIRECTORY_LOG_FILENAME;
	private String                        gsnLogFileName                   = DEFAULT_GSN_LOG_FILENAME;
	private String                        gsnLog4jFile;
	private String                        gsnConfigurationFileName;
	private Properties                    gsnLog4JProperties;
	private String                        databaseSystem;
	private boolean                       isdatabaseSystemInitialzied      = false;
	protected String                      timeFormat                       = "";

	public ContainerConfig(){
		
	}
	
	public ContainerConfig(String name,String author,String description,String email,
			int port, String timeFormat, boolean zmqEnabled,int zmqProxyPort,int zmqMetaPort,
			boolean acEnabled,int sslPort, String sslKSPass,String sslKPass, 
			StorageConfig storage,SlidingConfig slide){
		this.webName=name;
		this.webAuthor=author;
		this.webEmail=email;
		this.webDescription=description;
		this.containerPort=port;
		this.timeFormat=timeFormat;
		this.zmqEnabled=zmqEnabled;
		this.zmqProxyPort=zmqProxyPort;
		this.zmqMetaPort=zmqMetaPort;
		this.acEnabled=acEnabled;
		this.sslPort=sslPort;
		this.sslKeyStorePassword=sslKSPass;
		this.sslKeyPassword=sslKPass;
		this.storage=storage;
		this.sliding=slide;				
				
				
	}

    public boolean isAcEnabled() {
        return acEnabled;
    }

    public StorageConfig getStorage() {
        return storage;
    }

    public SlidingConfig getSliding() {
        return sliding;
    }

    public String getContainerFileName ( ) {
		return this.containerFileName;
	}

	public void setContainerConfigurationFileName ( final String containerFileName ) {
		this.containerFileName = containerFileName;
	}

	/**
	 * @return Returns the author.
	 */
	public String getWebAuthor ( ) {
		if ( this.webAuthor == null || this.webAuthor.trim( ).equals( "" ) )
			this.webAuthor = NOT_PROVIDED;
		else
			this.webAuthor = this.webAuthor.trim( );
		return this.webAuthor;
	}

	/**
	 * @return Returns the containerPort.
	 */
	public int getContainerPort ( ) {
		return this.containerPort;
	}
	
	public void setContainerPort ( int newValue ) {
		this.containerPort = newValue;
	}
	
	/**
	 * @return true if the zmq data distribution is enabled.
	 */
	public boolean isZMQEnabled() {
		return this.zmqEnabled;
	}
	
	/**
	 * @return Returns the ZeroMQ stream proxy port.
	 */
	public int getZMQProxyPort ( ) {
		return this.zmqProxyPort;
	}
	
	/**
	 * @return Returns the ZeroMQ meta information port.
	 */
	public int getZMQMetaPort ( ) {
		return this.zmqMetaPort;
	}

	/**
	 * @return Returns the webDescription.
	 */
	public String getWebDescription ( ) {
		if ( this.webDescription == null || this.webDescription.trim( ).equals( "" ) ) this.webDescription = NOT_PROVIDED;
		return this.webDescription.trim( );
	}

	/**
	 * @return Returns the webEmail.
	 */
	public String getWebEmail ( ) {
		if ( this.webEmail == null ) this.webEmail = NOT_PROVIDED;
		return this.webEmail;
	}

	/**
	 * @return Returns the name.
	 */
	public String getWebName ( ) {
		if ( this.webName == null || this.webName.trim( ).equals( "" ) ) this.webName = NOT_PROVIDED;
		this.webName = this.webName.trim( );
		return this.webName;
	}

	public void setWebEmail ( String newValue ) {
		this.webEmail = newValue;
	}

	public void setWebAuthor ( String newValue ) {
		this.webAuthor = newValue;
	}

	public void setWebName ( String newValue ) {
		this.webName = newValue;
	}

	/**
	 * @return Returns the storagePoolSize.
	 */
	public int getStoragePoolSize ( ) {
		return this.storagePoolSize;
	}

	public String toString ( ) {
		return this.getClass().getName() + " class [name=" + this.webName + "]";
	}

	public void setDirectoryLoggingLevel ( String newValue ) {
		this.directoryLoggingLevel = newValue;
	}

	public String getDirectoryLoggingLevel ( ) {
		return this.directoryLoggingLevel;
	}

	public void setMaxDirectoryLogSizeInMB ( long newValue ) {
		this.maxDirectoryLogSizeInMB = newValue;
	}

	public long getMaxDirectoryLogSizeInMB ( ) {
		return this.maxDirectoryLogSizeInMB;
	}

	public void setGsnLoggingLevel ( String newValue ) {
		this.gsnLoggingLevel = newValue;
	}

	public String getGsnLoggingLevel ( ) {
		return this.gsnLoggingLevel;
	}

	public void setMaxGSNLogSizeInMB ( long newValue ) {
		this.maxGSNLogSizeInMB = newValue;
	}

	public long getMaxGSNLogSizeInMB ( ) {
		return this.maxGSNLogSizeInMB;
	}

	public void setDirectoryLogFileName ( String newValue ) {
		this.directoryLogFileName = newValue;
	}

	public String getDirectoryLogFileName ( ) {
		return this.directoryLogFileName;
	}

	public void setGsnLogFileName ( String newValue ) {
		this.gsnLogFileName = newValue;
	}

	public String getGsnLogFileName ( ) {
		return this.gsnLogFileName;
	}

	private static String extractLoggingLevel ( String property , String [ ] setOfPossibleValues , String defaultValue ) {
		String toReturn = defaultValue;
		if ( property == null ) return toReturn;
		StringTokenizer st = new StringTokenizer( property , "," );
		if ( st == null || st.countTokens( ) == 0 ) return toReturn;
		String inputLogLevel = st.nextToken( );
		if ( inputLogLevel == null )
			return toReturn;
		else
			inputLogLevel = inputLogLevel.toUpperCase( ).trim( );
		for ( String level : setOfPossibleValues )
			if ( level.equals( inputLogLevel ) ) {
				toReturn = level;
				break;
			}
		return toReturn;
	}

	public static ContainerConfig getConfigurationFromFile ( String containerConfigurationFileName , String gsnLog4jFile , String dirLog4jFile ) throws JiBXException , FileNotFoundException {
/*		IBindingFactory bfact = BindingDirectory.getFactory( ContainerConfig.class );
		IUnmarshallingContext uctx = bfact.createUnmarshallingContext( );
		ContainerConfig toReturn = ( ContainerConfig ) uctx.unmarshalDocument( new FileInputStream( containerConfigurationFileName ) , null );
*/
		GsnConf gsn=GsnConf.load(containerConfigurationFileName);
		ContainerConfig toReturn = BeansInitializer.container(gsn);
		Properties gsnLog4j = new Properties( );
		try {
			gsnLog4j.load( new FileInputStream( gsnLog4jFile ) );
		} catch ( IOException e ) {
			System.out.println( "Can't read the log4j files, please check the 2nd and 3rd parameters and try again." );
			e.printStackTrace( );
			System.exit( 1 );
		}
		toReturn.initLog4JProperties( gsnLog4j  );
		toReturn.setSourceFiles( containerConfigurationFileName , gsnLog4jFile , dirLog4jFile );
		return toReturn;
	}

	private void initLog4JProperties ( Properties gsnLog4j  ) {
		this.gsnLog4JProperties = gsnLog4j;
		setGsnLoggingLevel( extractLoggingLevel( gsnLog4j.getProperty( "log4j.rootLogger" ) , ContainerConfig.LOGGING_LEVELS , DEFAULT_LOGGING_LEVEL ) );
		setMaxGSNLogSizeInMB( OptionConverter.toFileSize( gsnLog4j.getProperty( "log4j.appender.file.MaxFileSize" ) , ContainerConfig.DEFAULT_GSN_LOG_SIZE ) / ( 1024 * 1024 ) );
	}

	private void setSourceFiles ( String gsnConfigurationFileName , String gsnLog4jFile , String dirLog4jFile ) {
		this.gsnConfigurationFileName = gsnConfigurationFileName;
		this.gsnLog4jFile = gsnLog4jFile;
	}

	public void setdatabaseSystem ( String newValue ) {
		isdatabaseSystemInitialzied = true;
		databaseSystem = newValue;
        storage = new StorageConfig();
        storage.setJdbcDriver(convertToDriver( newValue ));
        if ( newValue == JDBC_SYSTEMS[ 0 ] ) {
			storage.setJdbcPassword("");
            storage.setJdbcUsername("sa");
            storage.setJdbcURL(JDBC_URLS[ 0 ]);
		} else if ( newValue == JDBC_SYSTEMS[ 1 ] ) {
			storage.setJdbcPassword("");
            storage.setJdbcUsername("sa");
            storage.setJdbcURL(JDBC_URLS[ 1 ]);
		} else if ( newValue == JDBC_SYSTEMS[ 2 ] ) {
			storage.setJdbcURL(JDBC_URLS[ 2 ]);
		} else if ( newValue == JDBC_SYSTEMS[ 3 ] ) {
			storage.setJdbcURL(JDBC_URLS[ 3 ]);
		}
	}

	public String getdatabaseSystem ( ) {
		if ( isdatabaseSystemInitialzied == false ) {
			isdatabaseSystemInitialzied = true;

			for ( int i = 0 ; i < JDBC_URLS_PREFIX.length ; i++ )
				if ( storage.getJdbcURL().toLowerCase( ).trim( ).startsWith( JDBC_URLS_PREFIX[ i ] ) ) {
					setdatabaseSystem( JDBC_SYSTEMS[ i ] );
					break;
				}
		}
		return this.databaseSystem;
	}

	private String convertToDriver ( String dbSys ) {
		for ( int i = 0 ; i < JDBC_SYSTEMS.length ; i++ )
			if ( JDBC_SYSTEMS[ i ].equals( dbSys ) ) return JDBC_DRIVERS[ i ];
		return "";
	}

	public void writeConfigurations ( ) throws FileNotFoundException , IOException {
		gsnLog4JProperties.put( "log4j.rootLogger" , getGsnLoggingLevel( ) + ",file" );
		gsnLog4JProperties.put( "log4j.appender.file.MaxFileSize" , getMaxGSNLogSizeInMB( ) + "MB" );
		StringTemplateGroup templateGroup = new StringTemplateGroup( "gsn" );
		StringTemplate st = templateGroup.getInstanceOf( "gsn/gui/templates/templateConf" );
		st.setAttribute( "name" , getWebName( ) );
		st.setAttribute( "author" , getWebAuthor( ) );
		st.setAttribute( "description" , getWebDescription( ) );
		st.setAttribute( "email" , getWebEmail( ) );
		st.setAttribute( "db_user" , storage.getJdbcUsername( ) );
		st.setAttribute( "db_password" , storage.getJdbcPassword( ) );
		st.setAttribute( "db_driver" , storage.getJdbcDriver( ) );
		st.setAttribute( "db_url" , storage.getJdbcURL( ) );
		st.setAttribute( "gsn_port" , getContainerPort( ) );

		gsnLog4JProperties.store( new FileOutputStream( gsnLog4jFile ) , "" );
		FileWriter writer = new FileWriter( gsnConfigurationFileName );
		writer.write( st.toString( ) );
		writer.close( );

	}

	public static String extractDirectoryServiceHost ( String rawValue ) {
		return ValidityTools.getHostName( rawValue );
	}

	public static ContainerConfig getDefaultConfiguration ( ) {
		ContainerConfig bean = new ContainerConfig( );
		bean.storage = new StorageConfig();
        bean.storage.setJdbcDriver( ContainerConfig.JDBC_SYSTEMS[ 0 ] );
		bean.storage.setJdbcPassword( "" );
		bean.storage.setJdbcURL( "sa" );
		bean.storage.setJdbcURL( ContainerConfig.JDBC_URLS[ 0 ] );
		return bean;
	}

	public int getSSLPort(){
		return sslPort;
	}
	public String getSSLKeyStorePassword(){
		return sslKeyStorePassword == null ? DEFAULT_SSL_KEYSTORE_PWD : sslKeyStorePassword;
	}
	public String getSSLKeyPassword(){
		return sslKeyPassword == null ? DEFAULT_SSL_KEY_PWD : sslKeyPassword;
	}
	public String getSSLKeystore(){
		return sslKeyStore;
	}
	
	/**
	 * MSR MAP PART.
	 */
	private ArrayList<KeyValueImp> msrMap ;
	private HashMap<String, String> msrMapCached ;
	public HashMap<String, String> getMsrMap() {
		if (msrMapCached==null) {
			msrMapCached = new HashMap<String, String>();
			if (msrMap==null)
				return msrMapCached;
			for (KeyValueImp kv : msrMap)
				msrMapCached.put(kv.getKey().toLowerCase().trim(), kv.getValue());
		}
		return msrMapCached;
	}

	public String getTimeFormat() {
		return timeFormat;
	}
	
}
