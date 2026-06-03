package org.fog.test.perfeval;

import org.fog.utils.TimeKeeper;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.apache.commons.math3.random.RandomGenerator;
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
//import org.fog.placement.ModulePlacementEdgewardsOrginal;
import org.fog.placement.ModulePlacementEdgewards;
import org.fog.placement.ModulePlacementMapping;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;

/**
 * Simulation setup for case study 1 - Intelligent Surveillance
 * @author Ryan de Klerk
 * <p>
 * NOTE: This file is a modification of 'LabellingDCNSFog.java' by Saeedeh Baneshi.
 *       The main changes are to easily change parameters such as the
 *       workload intensity (Through 'getWorkloadValue()') and
 *       network latency (through 'getLatencyValue()').
 *
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

public class Case1 {
	static List<FogDevice> fogDevices = new ArrayList<FogDevice>();
	static List<Sensor> sensors = new ArrayList<Sensor>();
	static List<Actuator> actuators = new ArrayList<Actuator>();
	static int numOfAreas = 1;
	public static int numOfCamerasPerArea = 4;


	//private static boolean PROXY = true;
	private static boolean USER_BASED = false;

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
		Log.printLine("Starting Labelling DCNS...");

		try {
			Log.disable();
			int num_user = 1; // number of cloud users
			Calendar calendar = Calendar.getInstance();
			boolean trace_flag = false; // mean trace events

			CloudSim.init(num_user, calendar, trace_flag);

			String appId1 = "dcns"; // identifier of the application
			//String appId2 = "dcns2";

			FogBroker broker = new FogBroker("broker");

			Application application = createApplication(appId1, broker.getId());
			application.setUserId(broker.getId());

			createFogDevices(broker.getId(), appId1);
			Controller controller = null;

			//*Saeedeh*//addModuleToDevice function add device name and module name to a map with name: moduleMapping <deviceName, list<moduleName>>
			ModuleMapping moduleMapping = ModuleMapping.createModuleMapping(); // initializing a module mapping

			moduleMapping.addModuleToDevice("user_interface", "cloud"); // fixing instances of User Interface module in the Cloud

			switch (DEPLOYMENT) {
			case "EDGE":
				for(FogDevice device : fogDevices){
					if(device.getName().startsWith("m")){ // names of all Smart Cameras start with 'm'
						moduleMapping.addModuleToDevice("motion_detector", device.getName());
						moduleMapping.addModuleToDevice("object_detector", device.getName());  // fixing 1 instance of the Motion Detector module to each Smart Camera
						moduleMapping.addModuleToDevice("object_tracker", device.getName());
					}
				}
				USER_BASED= true;
				break;
			case "ROUTER":
				for(FogDevice device : fogDevices){
					if(device.getName().startsWith("m")){ // names of all Smart Cameras start with 'm' 
						moduleMapping.addModuleToDevice("motion_detector", device.getName());  // fixing 1 instance of the Motion Detector module to each Smart Camera
					}
				}
            	moduleMapping.addModuleToDevice("object_detector", "d-0");
                moduleMapping.addModuleToDevice("object_tracker", "d-0");
                USER_BASED= true;
				break;
			case "SERVER":
            	for(FogDevice device : fogDevices){
					if(device.getName().startsWith("m")){ // names of all Smart Cameras start with 'm' 
						moduleMapping.addModuleToDevice("motion_detector", device.getName());  // fixing 1 instance of the Motion Detector module to each Smart Camera
					}
				}
            	moduleMapping.addModuleToDevice("object_detector", "proxy-server");
                moduleMapping.addModuleToDevice("object_tracker", "proxy-server");
                USER_BASED= true;
				break;
			case "CLOUD":
				for(FogDevice device : fogDevices){
					if(device.getName().startsWith("m")){ // names of all Smart Cameras start with 'm' 
						moduleMapping.addModuleToDevice("motion_detector", device.getName());  // fixing 1 instance of the Motion Detector module to each Smart Camera
					}
				}
				// if the mode of deployment is cloud-only
				moduleMapping.addModuleToDevice("object_detector", "cloud"); // placing all instances of Object Detector module in the Cloud
				moduleMapping.addModuleToDevice("object_tracker", "cloud"); // placing all instances of Object Tracker module in the Cloud
				break;
			case "BALANCED": // Similar to the Router_Proxy mode.
            	for(FogDevice device : fogDevices){
					if(device.getName().startsWith("m")){ // names of all Smart Cameras start with 'm' 
						moduleMapping.addModuleToDevice("motion_detector", device.getName());  // fixing 1 instance of the Motion Detector module to each Smart Camera
					}
				}
            	moduleMapping.addModuleToDevice("object_detector", "d-0");
                moduleMapping.addModuleToDevice("object_tracker", "proxy-server");
                USER_BASED= true;
				break;
			}

			controller = new Controller("master-controller", fogDevices, sensors, 
					actuators);
			List<String> n=application.getModuleNames();
			for (String name:n) {
				System.out.println(name);
			}
			System.out.println("\n\n\n\n*****************************\n\n\n");
			controller.submitApplication(
					application,
					new ModulePlacementMapping(fogDevices, application, moduleMapping)
			);

			TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());

			CloudSim.startSimulation();
			CloudSim.stopSimulation();

			Log.printLine("DCNS finished!");
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


	static boolean modified_bw=true;
	static int cloud_up_bw=100;
	static int cloud_down_bw=100000;
	static int proxy_up_bw=100000;
	static int proxy_down_bw=100000;
	//Saeedeh changed this from 10000 to 100000
	static int router_up_bw=100000;//To prevent overflow (10000 will make this link bottleneck when number of cameras increases)
	static int router_down_bw=10000;

	private static void createFogDevices(int userId, String appId1) {

		//*********************Default(Original of iFogSim) for bandwidths****/
		//********************Please do not touch these values****************/
		int c_band_up=100;
		int c_band_down=10000;
		int p_band_up=10000;
		int p_band_down=10000;
		//*********************************************************************/

		if(modified_bw==true){
			c_band_up=cloud_up_bw;
			c_band_down=cloud_down_bw;
			p_band_up=proxy_up_bw;
			p_band_down=proxy_down_bw;
		}

		FogDevice cloud = createFogDevice("cloud", 44800, 40000, c_band_up, c_band_down, 0, 0.01, 16*103, 16*83.25, "Shared",12300,11070);
		//FogDevice cloud = createFogDevice("cloud", 11200, 40000, c_band_up, c_band_down, 0, 0.01, 0.25*16*103, 0.25*16*83.25, "Shared",12300,11070);

		cloud.setParentId(-1);
		cloud.setDeviceType("Shared");// Saeedeh added

		fogDevices.add(cloud);
		FogDevice proxy = createFogDevice("proxy-server", 2800, 4000, p_band_up, p_band_down, 1, 0.0, 107.339, 83.4333, "Shared", 4550, 4095);
		//FogDevice proxy = createFogDevice("proxy-server", 700, 4000, p_band_up, p_band_down, 1, 0.0, 0.25*107.339, 0.25*83.4333, "Shared", 4550, 4095);

		proxy.setParentId(cloud.getId());
		proxy.setUplinkLatency(100); // latency of connection between proxy server and cloud is 100 ms
		proxy.setDeviceType("Shared");// Saeedeh added
		fogDevices.add(proxy);
		for(int i=0;i<numOfAreas;i++){
			addArea(i+"", userId, appId1, proxy.getId());
		}
	}

	private static FogDevice addArea(String id, int userId, String appId1, int parentId){
		int r_band_up=10000;
		int r_band_down=10000;
		if(modified_bw==true){
			r_band_up=router_up_bw;
			r_band_down=router_down_bw;
		}

		FogDevice router = createFogDevice("d-"+id, 2800, 4000, r_band_up, r_band_down, 2, 0.0, 107.339, 83.4333, "Shared", 4550, 4095);
		//FogDevice router = createFogDevice("d-"+id, 700, 4000, r_band_up, r_band_down, 2, 0.0, 0.25*107.339, 0.25*83.4333, "Shared", 4550, 4095);

		fogDevices.add(router);
		router.setUplinkLatency(getLatencyValue()); // latency of connection between router and proxy server is 2 ms
		router.setDeviceType("Shared");// Saeedeh added
		for(int i=0;i<numOfCamerasPerArea;i++){
			String mobileId = id+"-"+i;
			FogDevice camera = addCamera(mobileId, userId, appId1, router.getId()); // adding a smart camera to the physical topology. Smart cameras have been modeled as fog devices as well.
			camera.setUplinkLatency(getLatencyValue()); // latency of connection between camera and router is 2 ms
			fogDevices.add(camera);

		}
		router.setParentId(parentId);
		return router;
	}

	private static FogDevice addCamera(String id, int userId, String appId1, int parentId){
		FogDevice camera = createFogDevice("m-"+id, 500, 100000, 10000, 100, 3, 0, 87.53, 82.44, "CPE", 4.6, 2.8);
		//FogDevice camera = createFogDevice("m-"+id, 125, 100000, 10000, 100, 3, 0, 0.25*87.53, 0.25*82.44, "CPE", 4.6, 2.8);

		camera.setParentId(parentId);
		camera.setDeviceType("CPE");// Saeedeh added

		Sensor sensor = new Sensor("s-"+id, "CAMERA", userId, appId1, new DeterministicDistribution(getWorkloadValue())); // inter-transmission time of camera (sensor) follows a deterministic distribution
		sensors.add(sensor);
		Actuator ptz = new Actuator("ptz-"+id, userId, appId1, "PTZ_CONTROL");
		actuators.add(ptz);
		sensor.setGatewayDeviceId(camera.getId());
		sensor.setLatency(1.0);  // latency of connection between camera (sensor) and the parent Smart Camera is 1 ms
		ptz.setGatewayDeviceId(camera.getId());
		ptz.setLatency(1.0);  // latency of connection between PTZ Control and the parent Smart Camera is 1 ms
		return camera;
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
		//saeedeh//System.out.println("nodeNme :"+nodeName);

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
		/*System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ host info ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
		System.out.println("\n this is host Id : "+host.getId()+"\n");
		System.out.println("this is host number of PEs: "+host.getNumberOfPes()+"\n");
		System.out.println("this is host total MIPS : "+host.getTotalMips()+"\n");
		System.out.println("this is host storage : "+host.getStorage()+"\n");
		System.out.println("this is host utilization of CPU : "+host.getUtilizationOfCpu()+"\n");
		System.out.println("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~ the end ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n");

		System.out.println("this is host list size : "+hostList.size());*/

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
		//Saeedeh: You can change scheduling interval here: 5 argument of FogDevice = 10
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
	 * Function to create the Intelligent Surveillance application in the DDF model. 
	 * @param appId unique identifier of the application
	 * @param userId identifier of the user of the application
	 * @return
	 */
	@SuppressWarnings({"serial" })
	private static Application createApplication(String appId1, int userId){

		Application application = Application.createApplication(appId1, userId);
		/*
		 * Adding modules (vertices) to the application model (directed graph)
		 */
		application.addAppModule("object_detector", 10);
		application.addAppModule("motion_detector", 10);
		application.addAppModule("object_tracker", 10);
		application.addAppModule("user_interface", 10);

		/*
		 * Connecting the application modules (vertices) in the application model (directed graph) with edges
		 */
		application.addAppEdge("CAMERA", "motion_detector", 1*1000, 1*20000, "CAMERA", Tuple.UP, AppEdge.SENSOR); // adding edge from CAMERA (sensor) to Motion Detector module carrying tuples of type CAMERA
		application.addAppEdge("motion_detector", "object_detector", 1*2000, 1*2000, "MOTION_VIDEO_STREAM", Tuple.UP, AppEdge.MODULE); // adding edge from Motion Detector to Object Detector module carrying tuples of type MOTION_VIDEO_STREAM
		application.addAppEdge("object_detector", "user_interface", 1*500, 1*2000, "DETECTED_OBJECT", Tuple.UP, AppEdge.MODULE); // adding edge from Object Detector to User Interface module carrying tuples of type DETECTED_OBJECT
		application.addAppEdge("object_detector", "object_tracker", 1*1000, 1*100, "OBJECT_LOCATION", Tuple.UP, AppEdge.MODULE); // adding edge from Object Detector to Object Tracker module carrying tuples of type OBJECT_LOCATION
		//*Saeedeh*//this edge from obj tracker to ptz control is periodic and third argument is the periodicity= 100 (it means every 10msec)
		application.addAppEdge("object_tracker", "PTZ_CONTROL", 100, 1*28, 1*100, "PTZ_PARAMS", Tuple.DOWN, AppEdge.ACTUATOR); // adding edge from Object Tracker to PTZ CONTROL (actuator) carrying tuples of type PTZ_PARAMS

		/*
		 * Defining the input-output relationships (represented by selectivity) of the application modules. 
		 */
		double UI_Frac=0.05;
		//UI_Frac=0.005;
		application.addTupleMapping("motion_detector", "CAMERA", "MOTION_VIDEO_STREAM", new FractionalSelectivity(1.0)); // 1.0 tuples of type MOTION_VIDEO_STREAM are emitted by Motion Detector module per incoming tuple of type CAMERA
		application.addTupleMapping("object_detector", "MOTION_VIDEO_STREAM", "OBJECT_LOCATION", new FractionalSelectivity(1.0)); // 1.0 tuples of type OBJECT_LOCATION are emitted by Object Detector module per incoming tuple of type MOTION_VIDEO_STREAM
		application.addTupleMapping("object_detector", "MOTION_VIDEO_STREAM", "DETECTED_OBJECT", new FractionalSelectivity(UI_Frac)); // 0.05 tuples of type MOTION_VIDEO_STREAM are emitted by Object Detector module per incoming tuple of type MOTION_VIDEO_STREAM

		/*
		 * Defining application loops (maybe incomplete loops) to monitor the latency of. 
		 * Here, we add two loops for monitoring : Motion Detector -> Object Detector -> Object Tracker and Object Tracker -> PTZ Control
		 */
		final AppLoop loop1 = new AppLoop(new ArrayList<String>(){{add("motion_detector");add("object_detector");add("object_tracker");}});
		final AppLoop loop2 = new AppLoop(new ArrayList<String>(){{add("object_tracker");add("PTZ_CONTROL");}});

		List<AppLoop> loops = new ArrayList<AppLoop>(){{add(loop1);add(loop2);}};

		application.setLoops(loops);
		return application;
	}
}
