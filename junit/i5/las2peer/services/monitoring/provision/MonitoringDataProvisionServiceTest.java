package i5.las2peer.services.monitoring.provision;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import i5.las2peer.httpConnector.HttpConnector;
import i5.las2peer.httpConnector.client.Client;
import i5.las2peer.p2p.LocalNode;
import i5.las2peer.security.ServiceAgent;
import i5.las2peer.security.UserAgent;
import i5.las2peer.testing.MockAgentFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * 
 * Tests for the Monitoring Data Provision Service.
 * Please note, that this tests will only work with a valid database entry
 * that contains some data. At least one node has to be stored in the database.
 * 
 * @author Peter de Lange
 *
 */
public class MonitoringDataProvisionServiceTest {
	
	private static final String HTTP_ADDRESS = "localhost";
	private static final int HTTP_PORT = HttpConnector.DEFAULT_HTTP_CONNECTOR_PORT;
	
	private LocalNode node;
	private HttpConnector connector;
	private ByteArrayOutputStream logStream;
	private UserAgent adam = null;
	
	private static final String adamsPass = "adamspass";
	private static final String testServiceClass = "i5.las2peer.services.monitoring.provision.MonitoringDataProvisionService";
	
	
	@Before
	public void startServer() throws Exception {
		// start Node
		node = LocalNode.newNode();
		
		adam = MockAgentFactory.getAdam();
		
		node.storeAgent(adam);
		
		node.launch();
		
		ServiceAgent testService = ServiceAgent.generateNewAgent(
				testServiceClass, "a pass");
		testService.unlockPrivateKey("a pass");
		
		node.registerReceiver(testService);

		// start connector
		logStream = new ByteArrayOutputStream();
		connector = new HttpConnector();
		connector.setSocketTimeout(10000);
		connector.setLogStream(new PrintStream(logStream));
		connector.start(node);
	}
	
	
	@After
	public void shutDownServer() throws Exception {
		connector.stop();
		node.shutDown();
		
		connector = null;
		node = null;
		
		LocalNode.reset();
		
		System.out.println("Connector-Log:");
		System.out.println("--------------");

		System.out.println(logStream.toString());
	}
	
	
	@Test
	public void getNamesOfMeasuresServicesAndNodes() {
		
		Client c = new Client(HTTP_ADDRESS, HTTP_PORT, adam.getLoginName(), adamsPass);
		
		try {
			//Login
			c.connect();
			
			
			Object result = c.invoke(testServiceClass, "getMeasureNames", true);
			String[] resultArray = (String[]) result;
			assertEquals(3, resultArray.length);
			//Since they are in an ordered map, this should work
			assertTrue(resultArray[0].equals("ChartMeasure"));
			assertTrue(resultArray[1].equals("KPIMeasure"));
			assertTrue(resultArray[2].equals("ValueMeasure"));
			
			
			result = c.invoke(testServiceClass, "getNodes");
			assertTrue(result instanceof String[]);
			resultArray = (String[]) result;
			for(String node : resultArray)
				System.out.println("Result of asking for all nodes: " + node);
			
			result = c.invoke(testServiceClass, "getServices");
			assertTrue(result instanceof String[]);
			resultArray = (String[]) result;
			for(String service : resultArray)
				System.out.println("Result of asking for all services: " + service);
			
			
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception: " + e);
		}
		
		try {
		
		//Logout
		c.disconnect();
		
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception: " + e);
		}
	}
	
	
	@Test
	public void getMeasures() {
		
		Client c = new Client(HTTP_ADDRESS, HTTP_PORT, adam.getLoginName(), adamsPass);
		
		try {
			//Login
			c.connect();
			Object result = c.invoke(testServiceClass, "getNodes");
			String knownNode = ((String[]) result)[0];
			

			System.out.println("Calling Measure Visualizations with node " + knownNode);
			result = c.invoke(testServiceClass, "visualizeNodeMeasure", "KPIMeasure", knownNode);
			
			Double resultDouble = Double.parseDouble((String) result);
			System.out.println("KPIMeasure Result: " + resultDouble);
			
			result = c.invoke(testServiceClass, "visualizeMeasure", "ValueMeasure");
			assertTrue(result instanceof String);
			System.out.println("ValueMeasure Result: " + result);
			
			result = c.invoke(testServiceClass, "visualizeNodeMeasure", "ChartMeasure", knownNode);
			assertTrue(result instanceof String);
			System.out.println("ChartMeasure Result: " + result);
			
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception: " + e);
		}
		
		try {
		
		//Logout
		c.disconnect();
		
		} catch (Exception e) {
			e.printStackTrace();
			fail("Exception: " + e);
		}
	}
	
}
