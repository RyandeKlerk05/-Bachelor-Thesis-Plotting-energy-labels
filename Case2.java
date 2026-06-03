package org.fog.test.perfeval;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.entities.Actuator;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacementEdgewards;
import org.fog.placement.ModulePlacementMapping;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;

/**
 * Simulation setup for case study 2 - EEG Beam Tractor Game / Online VR game
 * @author Ryan de Klerk
 * <p>
 * NOTE: This file is a modification of 'VRGameFog.java' by Harshit Gupta.
 *       The main changes are to easily change parameters such as the
 *       workload intensity (Through 'getWorkloadValue()') and
 *       network latenct (through 'getLatencyValue()').
 * <p>
 *  Possible input variables:
 *   DEPLOYMENT:
 *    - EDGE
 *    - ROUTER
 *    - SERVER
 *    - CLOUD
 *    - BALANCED
 *   WORKLOAD:
 *    - LOW
 *    - MEDIUM
 *    - HIGH
 *   LATENCY:
 *    - LOW
 *    - HIGH
 */

public class Case2 {
	static List<FogDevice> fogDevices = new ArrayList<FogDevice>();
	static List<Sensor> sensors = new ArrayList<Sensor>();
	static List<Actuator> actuators = new ArrayList<Actuator>();
	static double throughput;

	static int numOfDepts = 1;
	static int numOfMobilesPerDept = 4;
	static double EEG_TRANSMISSION_TIME = 10;

	// Change these variables to modify the configuration!
	static String DEPLOYMENT = "EDGE";
	static String WORKLOAD = "LOW";
	static String LATENCY = "LOW";

	// Returns the workload level based on the input settings.
	private static double getWorkloadValue() {
		return switch (WORKLOAD) {
			case "LOW" -> 25;
			case "MEDIUM" -> 20;
			case "HIGH" -> 15;
			default -> 20;
		};
	}

	// Returns the latency level based on the input settings.
	private static double getLatencyValue() {
		return switch (LATENCY) {
			case "LOW" -> 10;
			case "HIGH" -> 50;
			default -> 10;
		};
	}

	public static void main(String[] args) {

		Log.printLine("Starting VRGame...");

		try {
			Log.disable();
			int num_user = 1; // number of cloud users
			Calendar calendar = Calendar.getInstance();
			boolean trace_flag = false; // mean trace events

			CloudSim.init(num_user, calendar, trace_flag);

			String appId = "vr_game"; // identifier of the application

			FogBroker broker = new FogBroker("broker");

			Application application = createApplication(appId, broker.getId());
			application.setUserId(broker.getId());

			createFogDevices(broker.getId(), appId);

			ModuleMapping moduleMapping = ModuleMapping.createModuleMapping(); // initializing a module mapping

			// Sets the deployment configuration based on the input settings.
			switch (DEPLOYMENT) {
				case "EDGE":
					for(FogDevice device : fogDevices){
						if(device.getName().startsWith("m")){
							moduleMapping.addModuleToDevice("connector", device.getName());
							moduleMapping.addModuleToDevice("concentration_calculator", device.getName());
							moduleMapping.addModuleToDevice("client", device.getName());
						}
					}
					break;
				case "ROUTER":
					moduleMapping.addModuleToDevice("connector", "d-0");
					moduleMapping.addModuleToDevice("concentration_calculator", "d-0");
					for(FogDevice device : fogDevices){
						if(device.getName().startsWith("m")){
							moduleMapping.addModuleToDevice("client", device.getName());
						}
					}
					break;
				case "SERVER":
					moduleMapping.addModuleToDevice("connector", "proxy-server");
					moduleMapping.addModuleToDevice("concentration_calculator", "proxy-server");
					for(FogDevice device : fogDevices){
						if(device.getName().startsWith("m")){
							moduleMapping.addModuleToDevice("client", device.getName());
						}
					}
					break;
				case "CLOUD":
					moduleMapping.addModuleToDevice("connector", "cloud");
					moduleMapping.addModuleToDevice("concentration_calculator", "cloud");
					for(FogDevice device : fogDevices){
						if(device.getName().startsWith("m")){
							moduleMapping.addModuleToDevice("client", device.getName());
						}
					}
					break;
				case "BALANCED": // Similar to the Router_Proxy mode.
					moduleMapping.addModuleToDevice("connector", "d-0");
					moduleMapping.addModuleToDevice("concentration_calculator", "proxy-server");
					for(FogDevice device : fogDevices){
						if(device.getName().startsWith("m")){
							moduleMapping.addModuleToDevice("client", device.getName());
						}
					}
					break;
			}

			Controller controller = new Controller("master-controller", fogDevices, sensors, 
					actuators);

			controller.submitApplication(
					application,
					new ModulePlacementMapping(fogDevices, application, moduleMapping)
			);

			TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());

			// Calculates the throughput.
			double totalSensors = numOfDepts * numOfMobilesPerDept;
			double tupleRatePerSensor = 1.0 / getWorkloadValue();
			throughput = (totalSensors * tupleRatePerSensor * 1000);
			System.out.println("Throughput (FPS): " + throughput);

			CloudSim.startSimulation();
			CloudSim.stopSimulation();

			Log.printLine("VRGame finished!");
		} catch (Exception e) {
			e.printStackTrace();
			Log.printLine("Unwanted errors happen");
		}
	}

	/**
	 * Creates the fog devices in the physical topology of the simulation.
	 * @param userId
	 * @param appId
	 */
	private static void createFogDevices(int userId, String appId) {
		FogDevice cloud = createFogDevice("cloud", 44800, 40000, 100, 100000, 0, 0.01, 16*103, 16*83.25, "Shared", 12300, 11070); // creates the fog device Cloud at the apex of the hierarchy with level=0
		cloud.setParentId(-1);
		cloud.setDeviceType("Shared");// Saeedeh added
		FogDevice proxy = createFogDevice("proxy-server", 2800, 4000, 100000, 100000, 1, 0.0, 107.339, 83.4333, "Shared", 4550, 4095); // creates the fog device Proxy Server (level=1)
		proxy.setParentId(cloud.getId()); // setting Cloud as parent of the Proxy Server
		proxy.setUplinkLatency(100); // latency of connection from Proxy Server to the Cloud is 100 ms
		proxy.setDeviceType("Shared");// Saeedeh added


		fogDevices.add(cloud);
		fogDevices.add(proxy);

		for(int i=0;i<numOfDepts;i++){
			addGw(i+"", userId, appId, proxy.getId()); // adding a fog device for every Gateway in physical topology. The parent of each gateway is the Proxy Server
		}

	}

	private static FogDevice addGw(String id, int userId, String appId, int parentId){
		FogDevice dept = createFogDevice("d-"+id, 2800, 4000, 100000, 100000, 1, 0.0, 107.339, 83.4333, "Shared", 4550, 4095);
		fogDevices.add(dept);
		dept.setParentId(parentId);
		dept.setUplinkLatency(getLatencyValue()); // latency of connection between gateways and proxy server is 4 ms
		dept.setDeviceType("Shared"); // Saeedeh added
		for(int i=0;i<numOfMobilesPerDept;i++){
			String mobileId = id+"-"+i;
			FogDevice mobile = addMobile(mobileId, userId, appId, dept.getId()); // adding mobiles to the physical topology. Smartphones have been modeled as fog devices as well.
			mobile.setUplinkLatency(getLatencyValue()); // latency of connection between the smartphone and proxy server is 2 ms
			fogDevices.add(mobile);
		}
		return dept;
	}

	private static FogDevice addMobile(String id, int userId, String appId, int parentId){
		FogDevice mobile = createFogDevice("m-"+id, 1000, 1000, 10000, 270, 3, 0, 87.53, 82.44, "CPE", 4.6, 2.8);
		mobile.setParentId(parentId);
		mobile.setDeviceType("CPE");// Saeedeh added
		Sensor eegSensor = new Sensor("s-"+id, "EEG", userId, appId, new DeterministicDistribution(getWorkloadValue() / 2)); // inter-transmission time of EEG sensor follows a deterministic distribution
		sensors.add(eegSensor);
		Actuator display = new Actuator("a-"+id, userId, appId, "DISPLAY");
		actuators.add(display);
		eegSensor.setGatewayDeviceId(mobile.getId());
		eegSensor.setLatency(6.0);  // latency of connection between EEG sensors and the parent Smartphone is 6 ms
		display.setGatewayDeviceId(mobile.getId());
		display.setLatency(1.0);  // latency of connection between Display actuator and the parent Smartphone is 1 ms
		return mobile;
	}

	/**
	 * Creates a vanilla fog device
	 * @param nodeName name of the device to be used in simulation
	 * @param mips MIPS
	 * @param ram RAM
	 * @param upBw uplink bandwidth
	 * @param downBw downlink bandwidth
	 * @param level hierarchy level of the device
	 * @param ratePerMips cost rate per MIPS used
	 * @param busyPower
	 * @param idlePower
	 * @return
	 */
	private static FogDevice createFogDevice(String nodeName, long mips,
			int ram, long upBw, long downBw, int level, double ratePerMips, double busyPower, double idlePower, String deviceType, double networkingMaxPower, double networkingIdlePower) {

		List<Pe> peList = new ArrayList<Pe>();

		// 3. Create PEs and add these into a list.
		peList.add(new Pe(0, new PeProvisionerOverbooking(mips))); // need to store Pe id and MIPS Rating

		int hostId = FogUtils.generateEntityId();
		long storage = 1000000; // host storage
		int bw = 10000;

		PowerHost host = new PowerHost(
				hostId,
				new RamProvisionerSimple(ram),
				new BwProvisionerOverbooking(bw),
				storage,
				peList,
				new StreamOperatorScheduler(peList),
				new FogLinearPowerModel(busyPower, idlePower)
			);

		List<Host> hostList = new ArrayList<Host>();
		hostList.add(host);

		String arch = "x86"; // system architecture
		String os = "Linux"; // operating system
		String vmm = "Xen";
		double time_zone = 10.0; // time zone this resource located
		double cost = 3.0; // the cost of using processing in this resource
		double costPerMem = 0.05; // the cost of using memory in this resource
		double costPerStorage = 0.001; // the cost of using storage in this
										// resource
		double costPerBw = 0.0; // the cost of using bw in this resource
		LinkedList<Storage> storageList = new LinkedList<Storage>(); // we are not adding SAN
													// devices by now

		FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
				arch, os, vmm, host, time_zone, cost, costPerMem,
				costPerStorage, costPerBw);

		FogDevice fogdevice = null;
		try {
			fogdevice = new FogDevice(nodeName, characteristics,
					new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 0, ratePerMips, deviceType, networkingMaxPower, networkingIdlePower);
		} catch (Exception e) {
			e.printStackTrace();
		}

		fogdevice.setLevel(level);
		return fogdevice;
	}

	/**
	 * Function to create the EEG Tractor Beam game application in the DDF model. 
	 * @param appId unique identifier of the application
	 * @param userId identifier of the user of the application
	 * @return
	 */
	@SuppressWarnings({"serial" })
	private static Application createApplication(String appId, int userId){

		Application application = Application.createApplication(appId, userId); // creates an empty application model (empty directed graph)

		/*
		 * Adding modules (vertices) to the application model (directed graph)
		 */
		application.addAppModule("client", 10); // adding module Client to the application model
		application.addAppModule("concentration_calculator", 10); // adding module Concentration Calculator to the application model
		application.addAppModule("connector", 10); // adding module Connector to the application model

		/*
		 * Connecting the application modules (vertices) in the application model (directed graph) with edges
		 */
		if(EEG_TRANSMISSION_TIME==10)
			application.addAppEdge("EEG", "client", 2000, 500, "EEG", Tuple.UP, AppEdge.SENSOR); // adding edge from EEG (sensor) to Client module carrying tuples of type EEG
		else
			application.addAppEdge("EEG", "client", 2500, 500, "EEG", Tuple.UP, AppEdge.SENSOR);
		application.addAppEdge("client", "concentration_calculator", 3500, 500, "_SENSOR", Tuple.UP, AppEdge.MODULE); // adding edge from Client to Concentration Calculator module carrying tuples of type _SENSOR
		application.addAppEdge("concentration_calculator", "connector", 100, 1000, 1000, "PLAYER_GAME_STATE", Tuple.UP, AppEdge.MODULE); // adding periodic edge (period=1000ms) from Concentration Calculator to Connector module carrying tuples of type PLAYER_GAME_STATE
		application.addAppEdge("concentration_calculator", "client", 14, 500, "CONCENTRATION", Tuple.DOWN, AppEdge.MODULE);  // adding edge from Concentration Calculator to Client module carrying tuples of type CONCENTRATION
		application.addAppEdge("connector", "client", 100, 28, 1000, "GLOBAL_GAME_STATE", Tuple.DOWN, AppEdge.MODULE); // adding periodic edge (period=1000ms) from Connector to Client module carrying tuples of type GLOBAL_GAME_STATE
		application.addAppEdge("client", "DISPLAY", 1000, 500, "SELF_STATE_UPDATE", Tuple.DOWN, AppEdge.ACTUATOR);  // adding edge from Client module to Display (actuator) carrying tuples of type SELF_STATE_UPDATE
		application.addAppEdge("client", "DISPLAY", 1000, 500, "GLOBAL_STATE_UPDATE", Tuple.DOWN, AppEdge.ACTUATOR);  // adding edge from Client module to Display (actuator) carrying tuples of type GLOBAL_STATE_UPDATE

		/*
		 * Defining the input-output relationships (represented by selectivity) of the application modules.
		 */
		application.addTupleMapping("client", "EEG", "_SENSOR", new FractionalSelectivity(0.9)); // 0.9 tuples of type _SENSOR are emitted by Client module per incoming tuple of type EEG 
		application.addTupleMapping("client", "CONCENTRATION", "SELF_STATE_UPDATE", new FractionalSelectivity(1.0)); // 1.0 tuples of type SELF_STATE_UPDATE are emitted by Client module per incoming tuple of type CONCENTRATION 
		application.addTupleMapping("concentration_calculator", "_SENSOR", "CONCENTRATION", new FractionalSelectivity(1.0)); // 1.0 tuples of type CONCENTRATION are emitted by Concentration Calculator module per incoming tuple of type _SENSOR 
		application.addTupleMapping("client", "GLOBAL_GAME_STATE", "GLOBAL_STATE_UPDATE", new FractionalSelectivity(1.0)); // 1.0 tuples of type GLOBAL_STATE_UPDATE are emitted by Client module per incoming tuple of type GLOBAL_GAME_STATE

		/*
		 * Defining application loops to monitor the latency of.
		 * Here, we add only one loop for monitoring : EEG(sensor) -> Client -> Concentration Calculator -> Client -> DISPLAY (actuator)
		 */
		final AppLoop loop1 = new AppLoop(new ArrayList<String>(){{add("EEG");add("client");add("concentration_calculator");add("client");add("DISPLAY");}});
		List<AppLoop> loops = new ArrayList<AppLoop>(){{add(loop1);}};
		application.setLoops(loops);

		return application;
	}
}