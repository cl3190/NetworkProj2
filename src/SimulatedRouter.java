import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;

import com.sun.org.apache.bcel.internal.generic.NEW;

public class SimulatedRouter {

	private int PORT;
	private long TIMEOUT;
	private String TRANSFILE;
	private int CHUCKNUM;

	private String THIS_IP;

	/*
	 * here we do not use Double.POSITIVE_INFINITY because adding to this value
	 * can cause problem
	 */
	private double MAX = 9999999.0;

	/*
	 * At most 2MB will be a chuck, adding the 20 bytes header so the maximum
	 * bytes of a message will be 2*1024*1024+20 bytes
	 */
	/* For details of the header, please refer to the README file */
	/*
	 * 8 bytes for command, 4 bytes for chucknum, 4 bytes for actual length, 4
	 * bytes for total file chucks count
	 */
	private int MAX_LEN = 2 * 1024 * 1024 + 20;

	private List<NetworkNode> neighbours = new ArrayList<NetworkNode>();
	private HashMap<String, TableEntry> routeTable = new HashMap<String, TableEntry>();
	private HashMap<String, Timer> deadTimerTable = new HashMap<String, Timer>();

	private DatagramSocket ds = null;
	private DatagramPacket dpIn = null;
	private DatagramPacket dpOut = null;

	private byte[] inBuf = new byte[MAX_LEN];
	private byte[] outBuf = new byte[MAX_LEN];

	private Timer sendTableTimer = null;

	private DateFormat df = new SimpleDateFormat("yyyy-MM-dd$hh:mm:ss");

	public SimulatedRouter(int port, long timeout, String transportFile,
			int chuckNum, List<String> entryList) throws Exception {
		this.PORT = port;
		this.TIMEOUT = timeout * 1000;
		this.TRANSFILE = transportFile;
		this.CHUCKNUM = chuckNum;

		this.THIS_IP = InetAddress.getLocalHost().getHostAddress();

		for (String line : entryList) {
			String ipPortPair = line.split("\\s+")[0];
			double cost = Double.valueOf(line.split("\\s+")[1]);

			NetworkNode thisNeighbour = new NetworkNode(ipPortPair);

			TableEntry entry = new TableEntry(thisNeighbour, thisNeighbour,
					cost);
			neighbours.add(thisNeighbour);
			routeTable.put(ipPortPair, entry);

			Timer timer = new Timer();
			timer.schedule(new TimerTaskDeleteNeighbour(ipPortPair),
					this.TIMEOUT * 3);
			this.deadTimerTable.put(ipPortPair, timer);

		}

		ds = new DatagramSocket(PORT); // use the same pork to send or listen

		dpOut = new DatagramPacket(outBuf, outBuf.length);
		dpIn = new DatagramPacket(inBuf, inBuf.length);

		/* This thread will listen on updates on the cost of other neighbours */
		ChangeListener changeListener = new ChangeListener();
		changeListener.start();

		/* start the count down for sending update to other neighbours */
		this.sendTableTimer = new Timer();
		sendTableTimer.schedule(new TimeOutSendRouteTable(), TIMEOUT, TIMEOUT);

		waitForCommands();

	}

	private void waitForCommands() throws Exception {
		System.out
				.println("\nClient is now up, please enter your command below:");

		BufferedReader input = null;

		while (true) {
			input = new BufferedReader(new InputStreamReader(System.in));
			String line = input.readLine().trim();
			String[] params = line.split("\\s+");
			String command = params[0];

			switch (Commands.getCommand(command)) {
			case LINKDOWN:
				doLinkDownCommand(params);
				break;
			case LINKUP:
				doLinkUpCommand(params);
				break;
			case SHOWRT:
				doShowRtCommand();
				break;
			case CLOSE:
				System.out.println("The client is now closed.");
				System.exit(0);
				break;
			case TRANSFER:
				doTransferCommand(params);
				break;
			default:
				System.out.println("Unkown command, please try again.\n");
			}
		}

	}

	private void doLinkDownCommand(String[] params) throws Exception{
		if(params.length!=3){
			System.out.println("Command format error!");
			return;
		}
		
		String targetIp = params[1];
		int targetPort = 0;
		try{
			targetPort = Integer.valueOf(params[2]);
		}catch(Exception ex){
			System.out.println("Command format error!");
			return;
		}
		
		for(int i=0;i<neighbours.size();i++){
			NetworkNode neighbour = neighbours.get(i);
			if(neighbour.getIpPortPair().equals(targetIp+":"+targetPort)){
				neighbours.remove(i);
			}
		}
		
		updateTableAfterNeighbourDown(targetIp+":"+targetPort);
		
		/*This following code will build the LINKDOWN message
		 * the format please refer to README*/
		
		
		dpOut.setAddress(InetAddress.getByName(targetIp));
		dpOut.setPort(targetPort);
		
		String content = "LINKDOWN";
		
		byte[] byteContent = content.getBytes();

		dpOut.setData(byteContent);
		ds.send(dpOut);
	}

	private void doLinkUpCommand(String[] params) {
		if(params.length!=4){
			System.out.println("Command format error!");
			return;
		}
		
		String targetIp = params[1];
		int targetPort = 0;
		double cost = 0;
		try{
			targetPort = Integer.valueOf(params[2]);
			cost = Double.valueOf(params[3]);
		}catch(Exception ex){
			System.out.println("Command format error!");
			return;
		}
		
		NetworkNode neighbour = new NetworkNode(targetIp,targetPort)
		neighbours.add(neighbour);
		
		routeTable.put(targetIp+":"+targetPort, new TableEntry(neighbour, neighbour, cost));
		
		/*This following code will build the LINKUP message
		 * the format please refer to README*/
		
		
		dpOut.setAddress(InetAddress.getByName(targetIp));
		dpOut.setPort(targetPort);
		
		String content = "LINKUP  "+new String(Util.toByteArray(cost));
		
		byte[] byteContent = content.getBytes();

		dpOut.setData(byteContent);
		ds.send(dpOut);
		

	}

	private void doShowRtCommand() {
		System.out.println("<" + df.format(new Date())
				+ ">Distance vector list is: ");
		Iterator<Entry<String, TableEntry>> it = routeTable.entrySet()
				.iterator();
		while (it.hasNext()) {
			Map.Entry<String, TableEntry> pairs = (Map.Entry<String, TableEntry>) it
					.next();

			String key = pairs.getKey();
			TableEntry val = pairs.getValue();

			/*Don't output infinity cost entries*/
			if (val.getCost() < MAX) {

				System.out.println("Destination = " + key + ", Cost = "
						+ val.getCost() + ", Link = ("
						+ val.getNextHop().getIpPortPair() + ")");
			}

		}

	}

	private void doTransferCommand(String[] params) {

	}

	public static void main(String[] args) {

		File conf = new File(args[0]);

		if (!conf.exists()) {
			System.out.println("The specified configure file does not exists!");
			System.exit(1);
		}

		SimulatedRouter router = null;

		try {
			Scanner scanner = new Scanner(conf);
			String line = null;

			int port = 0;
			long timeout = 0;
			String transportFile = "";
			int chuckNum = 0;

			// read in the first line
			if (scanner.hasNextLine()) {
				line = scanner.nextLine();
				String[] params = line.split("\\s+");
				port = Integer.valueOf(params[0]);
				timeout = Long.valueOf(params[1]);
				transportFile = params[2];
				chuckNum = Integer.valueOf(params[3]);
			} else {
				System.out.println("The configure file is empty");
				System.exit(1);
			}

			List<String> entryList = new ArrayList<String>();

			while (scanner.hasNextLine()) {
				line = scanner.nextLine();
				entryList.add(line);

			}

			router = new SimulatedRouter(port, timeout, transportFile,
					chuckNum, entryList);

		} catch (Exception ex) {
			ex.printStackTrace();
		}

	}

	private class ChangeListener extends Thread {
		public void run() {
			while (true) {
				try {
					ds.receive(dpIn);

					String messageType = (new String(dpIn.getData()))
							.substring(0, 8).trim();

					switch (Messages.getCommand(messageType)) {
					case UPDATE:
						updateMessageRespond();
						break;
					case LINKDOWN:
						linkdownMessageRespond();
						break;
					case LINKUP:
						linkupMessageRespond();
						break;
					case TRANSFER:
						TransferMessageRespond();
						break;
					default:
						// receiving an unknown message, ignore it

					}
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		}
	}

	private void updateMessageRespond() throws Exception {
		/*
		 * Update message is: 0-7 byte: "UPDATE  " 8-11 byte: the number of
		 * entries in this message 12-19 byte: nothing, just padding, from 20
		 * byte on, every 30 byte is an entry 111.111.111.111:12345 takes 21
		 * bytes (if port is 4 byte then we add a " " to the end add one " "
		 * then the cost, which is a double, take 8 byte
		 */

		byte[] inArr = dpIn.getData();
		int entryCount = Util.byteArrayToInt(Arrays.copyOfRange(inArr, 8, 12));

		String senderIp = dpIn.getAddress().getHostAddress();
		int senderPort = dpIn.getPort();

		String senderIpPortPair = senderIp + ":" + senderPort;

		/*
		 * Some time if a client is closed, and later on restarted, we need to
		 * add it back to the neighbour list
		 */
		boolean hasThisNeighbour = false;
		for (NetworkNode neighbour : neighbours) {
			if (neighbour.getIpPortPair().equals(senderIpPortPair)) {
				hasThisNeighbour = true;
			}
		}
		if (!hasThisNeighbour) {
			neighbours.add(new NetworkNode(senderIpPortPair));
		}

		/* upon receiving the update message, we reset its dead timer */
		Timer timer = new Timer();
		timer.schedule(new TimerTaskDeleteNeighbour(senderIpPortPair),
				this.TIMEOUT * 3);
		this.deadTimerTable.put(senderIpPortPair, timer);

		boolean hasUpdate = false;

		for (int i = 0; i < entryCount; i++) {
			String ipPortPair = (new String(inArr, 20 + i * 30, 21)).trim();
			double cost = Util.toDouble(Arrays.copyOfRange(inArr, 42 + i * 30,
					50 + i * 30));
			String entryIp = ipPortPair.split(":")[0];
			int entryPort = Integer.valueOf(ipPortPair.split(":")[1]);

			if (routeTable.containsKey(ipPortPair)) {
				double originCost = routeTable.get(ipPortPair).getCost()
						+ routeTable.get(senderIpPortPair).getCost();

				if (routeTable.get(ipPortPair).getNextHop().getIpPortPair()
						.equals(senderIpPortPair)
						&& originCost < cost) {
					/*
					 * If it is sent by the nexthop node, and if the cost
					 * becomes bigger, we should update the routeTable, only by
					 * this way can we propagate the bad news
					 */
					routeTable.get(ipPortPair).setCost(cost);

					hasUpdate = true;

				} else if (originCost > cost) {
					// update table if the cost is smaller
					routeTable.put(ipPortPair, new TableEntry(new NetworkNode(
							senderIp, senderPort), new NetworkNode(ipPortPair),
							cost));
					hasUpdate = true;
				}

			} else {

				if (ipPortPair.equals(THIS_IP + ":" + PORT)) {
					/*
					 * This is when receiving the entry with the destination as
					 * this client, when this happen, we update the destination
					 * to the sender to the cost of this entry
					 */
					if (routeTable.get(senderIpPortPair).getCost() > cost) {
						routeTable.put(senderIpPortPair, new TableEntry(
								new NetworkNode(senderIp, senderPort),
								new NetworkNode(senderIpPortPair), cost));
						hasUpdate = true;
					}
				} else {
					/*
					 * if the routeTable doesn't contain it, then we should add
					 * this one to the route table
					 */
					routeTable.put(ipPortPair, new TableEntry(new NetworkNode(
							senderIp, senderPort), new NetworkNode(ipPortPair),
							cost));
					hasUpdate = true;
				}
			}
		}

		if (hasUpdate) {
			sendTableToNeibours();
		}
	}

	private void linkdownMessageRespond() {
		String senderIp = dpIn.getAddress().getHostAddress();
		int senderPort = dpIn.getPort();

		String senderIpPortPair = senderIp + ":" + senderPort;

		updateTableAfterNeighbourDown(senderIpPortPair);

	}

	private void linkupMessageRespond() {
		/*
		 * With link up, the sender is back into the neighbor table again, yet
		 * the route table is not update immediately if some destination would
		 * be better off using this neighbour as the next hop, only after
		 * timeout and the neighbor send its update message will the route table
		 * be changed
		 */

		/*
		 * Format of the linkup message is: 0-7 byte: "LINKUP  ", 8-15 byte the
		 * cost (double is 8 byte)
		 */

		String senderIp = dpIn.getAddress().getHostAddress();
		int senderPort = dpIn.getPort();

		String senderIpPortPair = senderIp + ":" + senderPort;
		double cost = Util.toDouble(Arrays.copyOfRange(dpIn.getData(), 8, 16));
		NetworkNode linkupNote = new NetworkNode(senderIpPortPair);
		neighbours.add(linkupNote);
		routeTable.put(senderIpPortPair, new TableEntry(linkupNote, linkupNote,
				cost));

	}

	private void TransferMessageRespond() {

	}

	/**
	 * This is to send update messages to other router when one timeout has
	 * passed
	 * 
	 * @author kevin
	 * 
	 */
	class TimeOutSendRouteTable extends TimerTask {
		public void run() {
			try {
				sendTableToNeibours();
				sendTableTimer.schedule(new TimeOutSendRouteTable(), TIMEOUT);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private void sendTableToNeibours() throws Exception {
		for (NetworkNode neighbour : neighbours) {

			String receiverIp = neighbour.getIpAddress();
			int receiverPort = neighbour.getPort();
			// String receiverIpPort = receiverIp + ":" + receiverPort;
			dpOut.setAddress(InetAddress.getByName(neighbour.getIpAddress()));
			dpOut.setPort(receiverPort);

			/*
			 * making the update message, the format please refer to the README
			 * file
			 */
			StringBuffer content = new StringBuffer();
			content.append("UPDATE  ");
			content.append(new String(Util.intToByteArray(routeTable.size())));
			content.append("        ");
			Iterator<Entry<String, TableEntry>> it = routeTable.entrySet()
					.iterator();
			while (it.hasNext()) {
				Map.Entry<String, TableEntry> pairs = (Map.Entry<String, TableEntry>) it
						.next();

				String key = pairs.getKey();
				TableEntry val = pairs.getValue();

				int paddingCount = 21 - key.length();
				content.append(key);
				for (int i = 0; i < paddingCount; i++) {
					content.append(" ");
				}
				content.append(" ");
				content.append(Util.toByteArray(val.getCost()));

			}

			byte[] byteContent = content.toString().getBytes();

			dpOut.setData(byteContent);
			ds.send(dpOut);
		}
	}

	/**
	 * When 3 timeout has passed, marked the corresponding neighbour as dead
	 * 
	 * @author kevin
	 * 
	 */
	class TimerTaskDeleteNeighbour extends TimerTask {
		String ipPortPair;

		public TimerTaskDeleteNeighbour(String ipPortPair) {
			this.ipPortPair = ipPortPair;
		}

		public void run() {
			System.out
					.println("3 timeout passed, deleting record for Neighbor: "
							+ ipPortPair + " ");
			if (routeTable.containsKey(ipPortPair)) {
				for (int i = 0; i < neighbours.size(); i++) {
					if (neighbours.get(i).getIpPortPair().equals(ipPortPair)) {
						// remove from neighbor list
						neighbours.remove(i);
					}
				}

				updateTableAfterNeighbourDown(ipPortPair);
			}
		}
	}

	private void updateTableAfterNeighbourDown(String downNeighbourIpPort) {
		/*
		 * For all destination using this neighbour as the next hop, change the
		 * cost to Infinity because this neighbour is down
		 */
		Iterator<Entry<String, TableEntry>> it = routeTable.entrySet()
				.iterator();
		while (it.hasNext()) {
			Map.Entry<String, TableEntry> pairs = (Map.Entry<String, TableEntry>) it
					.next();

			String key = pairs.getKey();
			TableEntry val = pairs.getValue();

			if (val.getNextHop().getIpPortPair().equals(downNeighbourIpPort)) {
				val.setCost(MAX);
			}

		}
	}

}
