package gsn.beans.windowing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import gsn.VirtualSensorInitializationFailedException;
import gsn.VirtualSensorPool;
import gsn.beans.AddressBean;
import gsn.beans.DataField;
import gsn.beans.InputStream;
import gsn.beans.StreamElement;
import gsn.beans.StreamSource;
import gsn.beans.VSensorConfig;
import gsn.storage.DataEnumerator;
import gsn.storage.PoolIsFullException;
import gsn.storage.StorageManager;
import gsn.vsensor.BridgeVirtualSensor;
import gsn.wrappers.AbstractWrapper;

import java.io.Serializable;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestWindowing2 {
	public static class WrapperForTest2 extends AbstractWrapper {

		@Override
		public void finalize() {

		}

		@Override
		public DataField[] getOutputFormat() {
			return new DataField[] {};
		}

		@Override
		public String getWrapperName() {
			return "WrapperForTest2";
		}

		@Override
		public boolean initialize() {
			setUsingRemoteTimestamp(true);
			return true;
		}

		@Override
		public Boolean postStreamElement(StreamElement streamElement) {
			return super.postStreamElement(streamElement);
		}

	}

	private WrapperForTest2 wrapper = new WrapperForTest2();

	private StorageManager sm = StorageManager.getInstance();

	private AddressBean[] addressing = new AddressBean[] { new AddressBean("wrapper-for-test") };

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		initDB(StorageManager.H2_DB);
	}

	private static void initDB(int dbType) throws SQLException {
		if (StorageManager.MYSQL_DB == dbType) {
			DriverManager.registerDriver(new com.mysql.jdbc.Driver());
			StorageManager.getInstance().initialize("com.mysql.jdbc.Driver", "mehdi", "mehdi", "jdbc:mysql://localhost/gsntest");
		} else if (StorageManager.H2_DB == dbType) {
			DriverManager.registerDriver(new org.h2.Driver());
			StorageManager.getInstance().initialize("org.hsqldb.jdbcDriver", "sa", "", "jdbc:hsqldb:mem:.");
		} else {
			DriverManager.registerDriver(new net.sourceforge.jtds.jdbc.Driver());
			StorageManager.getInstance().initialize("net.sourceforge.jtds.jdbc.Driver", "mehdi", "mehdi",
					"jdbc:jtds:sqlserver://172.16.4.121:10101/gsntest;cachemetadata=true;prepareSQL=3");
		}
	}

	@Before
	public void setup() throws SQLException {
		sm.executeCreateTable(wrapper.getDBAliasInStr(), new DataField[] {},true);
		wrapper.setActiveAddressBean(new AddressBean("system-time"));
		assertTrue(wrapper.initialize());
	}

	@After
	public void teardown() throws SQLException {
		sm.executeDropTable(wrapper.getDBAliasInStr());
	}

	/**
	 * Testing time-based slide on each tuple (remote time-based)
	 */
	@Test
	public void testTimeBasedWindow1() throws SQLException, PoolIsFullException, VirtualSensorInitializationFailedException {
		InputStream is = new InputStream();
		is.setQuery("select * from mystream");
		StreamSource ss = new StreamSource().setAlias("mystream").setAddressing(addressing).setSqlQuery("select * from wrapper")
				.setRawHistorySize("2s").setInputStream(is);
		ss.setSamplingRate(1);
		is.setSources(new StreamSource[] { ss });
		assertTrue(ss.validate());
		ss.setWrapper(wrapper);

		VSensorConfig config = new VSensorConfig();
		config.setName("testvs");
		config.setMainClass(new BridgeVirtualSensor().getClass().getName());
		config.setInputStreams(new InputStream[] { is });
		config.setStorageHistorySize("10");
		config.setOutputStructure(new DataField[] {});
		config.setFileName("dummy-vs-file");
		assertTrue(config.validate());

		VirtualSensorPool pool = new VirtualSensorPool(config);
		is.setPool(pool);
		if (sm.tableExists(config.getName()))
			sm.executeDropTable(config.getName());
		sm.executeCreateTable(config.getName(), config.getOutputStructure(),true);
		// Mappings.addVSensorInstance ( pool );
		pool.start();

		assertTrue(is.validate());
		assertTrue(ss.rewrite(is.getQuery()).indexOf(ss.getUIDStr().toString()) > 0);

		assertEquals(ss.getWindowingType(), WindowType.TIME_BASED_SLIDE_ON_EACH_TUPLE);
		assertTrue(SQLViewQueryRewriter.class.isAssignableFrom(ss.getQueryRewriter().getClass()));
		assertTrue(((SQLViewQueryRewriter) ss.getQueryRewriter()).createViewSQL().toString().toLowerCase().indexOf("mod") < 0);
		StringBuilder query = new StringBuilder(((SQLViewQueryRewriter) ss.getQueryRewriter()).createViewSQL());
		print(query.toString());

		long time = System.currentTimeMillis();
		wrapper.postStreamElement(createStreamElement(time));
		ResultSet rs = StorageManager.getInstance().executeQueryWithResultSet(query);
		assertTrue(rs.next());
		assertFalse(rs.next());

		StringBuilder vsQuery = new StringBuilder("select * from ").append(config.getName());
		StringBuilder sb = new StringBuilder("SELECT timed from ").append(SQLViewQueryRewriter.VIEW_HELPER_TABLE).append(" where UID='")
				.append(ss.getUIDStr()).append("'");
		rs = sm.executeQueryWithResultSet(sb);
		assertTrue(rs.next());
		assertEquals(rs.getLong(1), time);

		long time1 = time + 1000;
		wrapper.postStreamElement(createStreamElement(time1));
		long time2 = time + 2500;
		wrapper.postStreamElement(createStreamElement(time2));

		DataEnumerator dm = sm.executeQuery(query, true);
		rs = StorageManager.getInstance().executeQueryWithResultSet(query);
		assertTrue(rs.next());
		assertTrue(rs.next());
		assertFalse(rs.next());

		assertTrue(dm.hasMoreElements());
		assertEquals(dm.nextElement().getTimeStamp(), time2);
		assertTrue(dm.hasMoreElements());
		assertEquals(dm.nextElement().getTimeStamp(), time1);
		assertFalse(dm.hasMoreElements());

		wrapper.removeListener(ss);
	}

	/**
	 * Testing time-based window-slide (remote time-based)
	 */
	@Test
	public void testTimeBasedWindow2() throws SQLException, PoolIsFullException, VirtualSensorInitializationFailedException {
		InputStream is = new InputStream();
		is.setQuery("select * from mystream");
		StreamSource ss = new StreamSource().setAlias("mystream").setAddressing(addressing).setSqlQuery("select * from wrapper")
				.setRawHistorySize("3s").setRawSlideValue("2s").setInputStream(is);
		ss.setSamplingRate(1);
		is.setSources(new StreamSource[] { ss });
		assertTrue(ss.validate());
		ss.setWrapper(wrapper);

		VSensorConfig config = new VSensorConfig();
		config.setName("testvs");
		config.setMainClass(new BridgeVirtualSensor().getClass().getName());
		config.setInputStreams(new InputStream[] { is });
		config.setStorageHistorySize("10");
		config.setOutputStructure(new DataField[] {});
		config.setFileName("dummy-vs-file");
		assertTrue(config.validate());

		VirtualSensorPool pool = new VirtualSensorPool(config);
		is.setPool(pool);
		if (sm.tableExists(config.getName()))
			sm.executeDropTable(config.getName());
		sm.executeCreateTable(config.getName(), config.getOutputStructure(),true);
		// Mappings.addVSensorInstance ( pool );
		pool.start();

		assertTrue(is.validate());
		assertTrue(ss.rewrite(is.getQuery()).indexOf(ss.getUIDStr().toString()) > 0);

		assertEquals(ss.getWindowingType(), WindowType.TIME_BASED);
		assertTrue(SQLViewQueryRewriter.class.isAssignableFrom(ss.getQueryRewriter().getClass()));
		assertTrue(((SQLViewQueryRewriter) ss.getQueryRewriter()).createViewSQL().toString().toLowerCase().indexOf("mod") < 0);
		StringBuilder query = new StringBuilder(((SQLViewQueryRewriter) ss.getQueryRewriter()).createViewSQL());
		print(query.toString());

		long time = System.currentTimeMillis() + 2000;
		wrapper.postStreamElement(createStreamElement(time));
		ResultSet rs = StorageManager.getInstance().executeQueryWithResultSet(query);
		assertFalse(rs.next());

		StringBuilder vsQuery = new StringBuilder("select * from ").append(config.getName());
		StringBuilder sb = new StringBuilder("SELECT timed from ").append(SQLViewQueryRewriter.VIEW_HELPER_TABLE).append(" where UID='")
				.append(ss.getUIDStr()).append("'");
		rs = sm.executeQueryWithResultSet(sb);
		assertTrue(rs.next());
		assertEquals(rs.getLong(1), -1L);

		long time1 = time + 1500;
		wrapper.postStreamElement(createStreamElement(time1));
		long time2 = time + 3800;
		wrapper.postStreamElement(createStreamElement(time2));

		rs = sm.executeQueryWithResultSet(sb);
		assertTrue(rs.next());
		assertEquals(rs.getLong(1), time2);

		long time3 = time + 4200;
		wrapper.postStreamElement(createStreamElement(time3));
		DataEnumerator dm = sm.executeQuery(query, true);
		rs = StorageManager.getInstance().executeQueryWithResultSet(query);
		assertTrue(rs.next());
		assertTrue(rs.next());
		assertFalse(rs.next());

		assertTrue(dm.hasMoreElements());
		assertEquals(dm.nextElement().getTimeStamp(), time2);
		assertTrue(dm.hasMoreElements());
		assertEquals(dm.nextElement().getTimeStamp(), time1);
		assertFalse(dm.hasMoreElements());

		long time4 = time + 5800;
		wrapper.postStreamElement(createStreamElement(time4));
		rs = sm.executeQueryWithResultSet(sb);
		assertTrue(rs.next());
		assertEquals(rs.getLong(1), time4);

		dm = sm.executeQuery(query, true);
		rs = StorageManager.getInstance().executeQueryWithResultSet(query);
		assertTrue(rs.next());
		assertTrue(rs.next());
		assertTrue(rs.next());
		assertFalse(rs.next());

		assertTrue(dm.hasMoreElements());
		assertEquals(dm.nextElement().getTimeStamp(), time4);
		assertTrue(dm.hasMoreElements());
		assertEquals(dm.nextElement().getTimeStamp(), time3);
		assertTrue(dm.hasMoreElements());
		assertEquals(dm.nextElement().getTimeStamp(), time2);
		assertFalse(dm.hasMoreElements());

		wrapper.removeListener(ss);
	}

	/**
	 * Testing tuple-based-win-time-based-slide
	 */
	@Test
	public void testTimeBasedWindow3() throws SQLException, PoolIsFullException, VirtualSensorInitializationFailedException {
		InputStream is = new InputStream();
		is.setQuery("select * from mystream");
		StreamSource ss = new StreamSource().setAlias("mystream").setAddressing(addressing).setSqlQuery("select * from wrapper")
				.setRawHistorySize("2").setRawSlideValue("2s").setInputStream(is);
		ss.setSamplingRate(1);
		is.setSources(new StreamSource[] { ss });
		assertTrue(ss.validate());
		ss.setWrapper(wrapper);

		VSensorConfig config = new VSensorConfig();
		config.setName("testvs");
		config.setMainClass(new BridgeVirtualSensor().getClass().getName());
		config.setInputStreams(new InputStream[] { is });
		config.setStorageHistorySize("10");
		config.setOutputStructure(new DataField[] {});
		config.setFileName("dummy-vs-file");
		assertTrue(config.validate());

		VirtualSensorPool pool = new VirtualSensorPool(config);
		is.setPool(pool);
		if (sm.tableExists(config.getName()))
			sm.executeDropTable(config.getName());
		sm.executeCreateTable(config.getName(), config.getOutputStructure(),true);
		// Mappings.addVSensorInstance ( pool );
		pool.start();

		assertTrue(is.validate());
		assertTrue(ss.rewrite(is.getQuery()).indexOf(ss.getUIDStr().toString()) > 0);

		assertEquals(ss.getWindowingType(), WindowType.TUPLE_BASED_WIN_TIME_BASED_SLIDE);
		assertTrue(SQLViewQueryRewriter.class.isAssignableFrom(ss.getQueryRewriter().getClass()));
		assertTrue(((SQLViewQueryRewriter) ss.getQueryRewriter()).createViewSQL().toString().toLowerCase().indexOf("mod") < 0);
		StringBuilder query = new StringBuilder(((SQLViewQueryRewriter) ss.getQueryRewriter()).createViewSQL());
		print(query.toString());

		long time = System.currentTimeMillis() + 2000;
		wrapper.postStreamElement(createStreamElement(time));
		ResultSet rs = StorageManager.getInstance().executeQueryWithResultSet(query);
		assertFalse(rs.next());

		StringBuilder vsQuery = new StringBuilder("select * from ").append(config.getName());
		StringBuilder sb = new StringBuilder("SELECT timed from ").append(SQLViewQueryRewriter.VIEW_HELPER_TABLE).append(" where UID='")
				.append(ss.getUIDStr()).append("'");
		rs = sm.executeQueryWithResultSet(sb);
		assertTrue(rs.next());
		assertEquals(rs.getLong(1), -1L);

		long time1 = time + 1500;
		wrapper.postStreamElement(createStreamElement(time1));
		long time2 = time + 2500;
		wrapper.postStreamElement(createStreamElement(time2));

		rs = sm.executeQueryWithResultSet(sb);
		assertTrue(rs.next());
		assertEquals(rs.getLong(1), time2);

		long time3 = time + 3500;
		wrapper.postStreamElement(createStreamElement(time3));

		DataEnumerator dm = sm.executeQuery(query, true);
		rs = StorageManager.getInstance().executeQueryWithResultSet(query);
		assertTrue(rs.next());
		assertTrue(rs.next());
		assertFalse(rs.next());

		assertTrue(dm.hasMoreElements());
		assertEquals(dm.nextElement().getTimeStamp(), time2);
		assertTrue(dm.hasMoreElements());
		assertEquals(dm.nextElement().getTimeStamp(), time1);
		assertFalse(dm.hasMoreElements());

		long time4 = time + 4600;
		wrapper.postStreamElement(createStreamElement(time4));
		rs = sm.executeQueryWithResultSet(sb);
		assertTrue(rs.next());
		assertEquals(rs.getLong(1), time4);

		dm = sm.executeQuery(query, true);
		rs = StorageManager.getInstance().executeQueryWithResultSet(query);
		assertTrue(rs.next());
		assertTrue(rs.next());
		assertFalse(rs.next());

		assertTrue(dm.hasMoreElements());
		assertEquals(dm.nextElement().getTimeStamp(), time4);
		assertTrue(dm.hasMoreElements());
		assertEquals(dm.nextElement().getTimeStamp(), time3);
		assertFalse(dm.hasMoreElements());

		wrapper.removeListener(ss);
	}

	private StreamElement createStreamElement(long timed) {
		return new StreamElement(new DataField[] {}, new Serializable[] {}, timed);
	}

	public static void print(String query) {
		System.out.println(query);
	}
}
