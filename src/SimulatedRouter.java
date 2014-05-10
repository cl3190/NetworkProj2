import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.Map.Entry;

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
	private double MAX = 99999.0;

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

	private ArrayList<byte[]> fileReceiveList = new ArrayList<byte[]>();
	private int receiveTime = 0;
	private boolean[] receiveTable = null;

	private Timer sendTableTimer = null;

	/* length is 23 */
	private DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");

	public SimulatedRouter(int port, long timeout, String transportFile,
			int chuckNum, List<String> entryList) throws Exception {
		this.PORT = port;
		this.TIMEOUT = timeout * 1000;
		this.TRANSFILE = transportFile;
		this.CHUCKNUM = chuckNum;

		String ip = InetAddress.getLocalHost().getHostAddress();
		if (ip.substring(0, 3).equals("127")) // some time local address is
												// 127.0.1.1
			ip = "127.0.0.1";
		this.THIS_IP = ip;

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
			case TRANS_PLUS:
				doTransferPlusCommand(params);
				break;
			default:
				System.out.println("Unkown command, please try again.\n");
			}
		}

	}

	private void doLinkDownCommand(String[] params) throws Exception {
		if (params.length != 3) {
			System.out.println("Command format error!");
			return;
		}

		String targetIp = params[1];
		int targetPort = 0;
		try {
			targetPort = Integer.valueOf(params[2]);
		} catch (Exception ex) {
			System.out.println("Command format error!");
			return;
		}

		for (int i = 0; i < neighbours.size(); i++) {
			NetworkNode neighbour = neighbours.get(i);
			if (neighbour.getIpPortPair().equals(targetIp + ":" + targetPort)) {
				neighbours.remove(i);
			}
		}

		updateTableAfterNeighbourDown(targetIp + ":" + targetPort);

		/*
		 * This following code will build the LINKDOWN message the format please
		 * refer to README
		 */

		dpOut.setAddress(InetAddress.getByName(targetIp));
		dpOut.setPort(targetPort);

		String content = "LINKDOWN";

		byte[] byteContent = content.getBytes();

		dpOut.setData(byteContent);
		ds.send(dpOut);
	}

	private void doLinkUpCommand(String[] params) throws Exception {
		if (params.length != 4) {
			System.out.println("Command format error!");
			return;
		}

		String targetIp = params[1];
		int targetPort = 0;
		double cost = 0;
		try {
			targetPort = Integer.valueOf(params[2]);
			cost = Double.valueOf(params[3]);
		} catch (Exception ex) {
			System.out.println("Command format error!");
			return;
		}

		NetworkNode neighbour = new NetworkNode(targetIp, targetPort);
		neighbours.add(neighbour);

		routeTable.put(targetIp + ":" + targetPort, new TableEntry(neighbour,
				neighbour, cost));

		/*
		 * This following code will build the LINKUP message the format please
		 * refer to README
		 */

		dpOut.setAddress(InetAddress.getByName(targetIp));
		dpOut.setPort(targetPort);

		String content = "LINKUP  ";

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		outputStream.write(content.getBytes());
		outputStream.write(Util.toByteArray(cost));

		byte[] byteContent = outputStream.toByteArray();

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

			/* Don't output infinity cost entries */
			if (val.getCost() < MAX) {

				System.out.println("Destination = " + key + ", Cost = "
						+ val.getCost() + ", Link = ("
						+ val.getNextHop().getIpPortPair() + ")");
			}

		}

	}

	private void doTransferCommand(String[] params) throws Exception {
		if (params.length != 3) {
			System.out.println("Command format error!");
			return;
		}
		String targetIp = params[1];
		int targetPort = Integer.valueOf(params[2]);

		if (targetIp.equals(THIS_IP) && targetPort == PORT) {
			System.out.println("Cannot send to yourself!");
			return;
		}
		
		if(TRANSFILE==""){
			System.out.println("Configure file didnot specify a file to send.");
			return;
		}

		for (int i = 0; i < neighbours.size(); i++) {
			if (neighbours.get(i).getIpPortPair()
					.equals(targetIp + ":" + targetPort)) {
				System.out.println("Cannot send to the neighbours!");
				return;
			}
		}

		sendChuckToNextHop(new NetworkNode(targetIp, targetPort), 2);

	}

	private void doTransferPlusCommand(String[] params) throws Exception {
		if (params.length != 4) {
			System.out.println("Command format error!");
			return;
		}
		String targetIp = params[1];
		int targetPort = Integer.valueOf(params[2]);
		int totalChuck = Integer.valueOf(params[3]);

		if (targetIp.equals(THIS_IP) && targetPort == PORT) {
			System.out.println("Cannot send to yourself!");
			return;
		}
		
		if(TRANSFILE==""){
			System.out.println("Configure file didnot specify a file to send.");
			return;
		}

		for (int i = 0; i < neighbours.size(); i++) {
			if (neighbours.get(i).getIpPortPair()
					.equals(targetIp + ":" + targetPort)) {
				System.out.println("Cannot send to the neighbours!");
				return;
			}
		}

		sendChuckToNextHop(new NetworkNode(targetIp, targetPort), totalChuck);

	}

	private void sendChuckToNextHop(NetworkNode destination, int totalChuckCount)
			throws Exception {
		/*
		 * This following code will build the TRANSFER message the format please
		 * refer to README
		 */
		/*
		 * 0-7: "TRANSFER" 8-11: chuck byte length, 12-15: chuck number, 16-19:
		 * total chuck count(how many chucks does this file has) 20-23: numbers
		 * of hops, 24-44: 21 byte for destination, 45 byte on, every 21 byte
		 * is: 111.111.111.111:12345 takes 21 bytes, if not as long as 21 byte,
		 * filled up by "  ", after that comes 23 byte of send time,
		 * yyyy-MM-dd'T'HH:mm:ss.SSS, after that, is the content of the file
		 */

		String targetIp = destination.getIpAddress();
		int targetPort = destination.getPort();

		if (!routeTable.containsKey(targetIp + ":" + targetPort)) {
			System.out.println("Destination cannot be reached!");
			return;
		}

		NetworkNode nextHop = routeTable.get(targetIp + ":" + targetPort)
				.getNextHop();

		dpOut.setAddress(InetAddress.getByName(nextHop.getIpAddress()));
		dpOut.setPort(nextHop.getPort());

		byte[] fileContent = null;
		File file = new File(TRANSFILE);
		try {
			fileContent = Util.fileToByteArray(file);
		} catch (IOException e1) {
			System.out.println("Error Reading The File.");
			e1.printStackTrace();
			return;
		}

		int chuckLength = fileContent.length;
		int chuckNum = CHUCKNUM;

		String com = "TRANSFER";

		String ipPortPair = destination.getIpPortPair();
		int padding = 21 - ipPortPair.length();
		for (int i = 0; i < padding; i++)
			ipPortPair += " ";

		String sendIpPort = THIS_IP + ":" + PORT;
		padding = 21 - sendIpPort.length();
		for (int i = 0; i < padding; i++)
			sendIpPort += " ";

		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		outputStream.write(com.getBytes());
		outputStream.write(Util.intToByteArray(chuckLength));
		outputStream.write(Util.intToByteArray(chuckNum));
		outputStream.write(Util.intToByteArray(totalChuckCount));
		outputStream.write(Util.intToByteArray(1));
		outputStream.write(ipPortPair.getBytes());
		outputStream.write(sendIpPort.getBytes());
		outputStream.write((df.format(new Date()).getBytes()));
		outputStream.write(fileContent);

		// System.out.println(new String(fileContent));

		byte[] byteContent = outputStream.toByteArray();
		// System.out.println("*******************************");

		// System.out.println("|"+new String(Arrays.copyOfRange(byteContent,
		// 24+21+21, 24+21+21+chuckLength))+"|");

		dpOut.setData(byteContent);
		ds.send(dpOut);
	}

	public static void main(String[] args) {

		if (args.length == 0) {
			System.out
					.println("Usage Error, Corret Usage: ./bfClient <config file>");
			System.exit(1);
		}

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
				if (params.length > 2) {
					transportFile = params[2];
					chuckNum = Integer.valueOf(params[3]);
				}
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
		deadTimerTable.get(senderIpPortPair).cancel();
		Timer timer = new Timer();
		timer.schedule(new TimerTaskDeleteNeighbour(senderIpPortPair),
				this.TIMEOUT * 3);
		this.deadTimerTable.put(senderIpPortPair, timer);

		boolean hasUpdate = false;

		for (int i = 0; i < entryCount; i++) {
			String ipPortPair = (new String(inArr, 20 + i * 30, 21)).trim();
			double cost = Util.toDouble(Arrays.copyOfRange(inArr, 42 + i * 30,
					50 + i * 30));
			// System.out.println("###############" + ipPortPair);
			String entryIp = ipPortPair.split(":")[0];
			int entryPort = Integer.valueOf(ipPortPair.split(":")[1]);

			// System.out
			// .println("==================" + ipPortPair + "   " + cost);

			cost += routeTable.get(senderIpPortPair).getCost();

			if (routeTable.containsKey(ipPortPair)) {
				double originCost = routeTable.get(ipPortPair).getCost();

				if (routeTable.get(ipPortPair).getNextHop().getIpPortPair()
						.equals(senderIpPortPair)
						&& originCost < cost) { /*
												 * If it is sent by the nexthop
												 * node, and if the cost becomes
												 * bigger, we should update the
												 * routeTable, only by this way
												 * can we propagate the bad news
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

				if (ipPortPair.equals(THIS_IP + ":" + PORT)) { /*
																 * This is when
																 * receiving the
																 * entry with
																 * the
																 * destination
																 * as this
																 * client, when
																 * this happen,
																 * we update the
																 * destination
																 * to the sender
																 * to the cost
																 * of this entry
																 */

					cost -= routeTable.get(senderIpPortPair).getCost();

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
					if (!ipPortPair.equals(THIS_IP + ":" + PORT)) {
						/* don't add entry to myself */
						routeTable.put(ipPortPair, new TableEntry(
								new NetworkNode(senderIp, senderPort),
								new NetworkNode(ipPortPair), cost));
						hasUpdate = true;
					}
				}
			}
		}

		if (hasUpdate) {
			sendTableToNeibours();
		}
	}

	private void linkdownMessageRespond() throws Exception {
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

	private void TransferMessageRespond() throws Exception {
		/*
		 * 0-7: "TRANSFER" 8-11: chuck byte length, 12-15: chuck number, 16-19:
		 * total chuck count(how many chucks does this file has) 20-23: numbers
		 * of hops, 24-44: 21 byte for destination, 45 byte on, every 21 byte
		 * is: 111.111.111.111:12345 takes 21 bytes, if not as long as 21 byte,
		 * filled up by "  ", after that comes 23 byte of send time,
		 * yyyy-MM-dd'T'HH:mm:ss.SSS, after that, is the content of the file
		 */

		byte[] inArr = dpIn.getData();
		String destIpPort = new String(Arrays.copyOfRange(inArr, 24, 45));
		destIpPort = destIpPort.trim();

		// System.out.println("=======#"+destIpPort+"#"+THIS_IP + ":" +
		// PORT+"#");

		int hopCount = Util.byteArrayToInt(Arrays.copyOfRange(inArr, 20, 24));

		if (destIpPort.equals(THIS_IP + ":" + PORT)) {
			/* This client is the final destination */

			int totalChuckCount = Util.byteArrayToInt(Arrays.copyOfRange(inArr,
					16, 20));
			
			System.out.println("total Chunk count:"+totalChuckCount);
			int chuckNum = Util.byteArrayToInt(Arrays
					.copyOfRange(inArr, 12, 16));
			int length = Util.byteArrayToInt(Arrays.copyOfRange(inArr, 8, 12));
			
			String time= new String(Arrays.copyOfRange(inArr, 24 + (hopCount + 1) * 21, 24
					+23+ (hopCount + 1) * 21 ));

			if (receiveTime == 0) {
				receiveTable = new boolean[totalChuckCount];
				for (int i=0;i<receiveTable.length;i++) {
					receiveTable[i] = false;
				}
				fileReceiveList = new ArrayList<byte[]>();
				for(int i=0;i<totalChuckCount;i++){
					fileReceiveList.add(null);
				}
			}
			receiveTime++;

			receiveTable[chuckNum - 1] = true;
			fileReceiveList.set(chuckNum - 1,
					Arrays.copyOfRange(inArr, 24 +23+ (hopCount + 1) * 21, 24
							+23+ (hopCount + 1) * 21 + length));


			// System.out.println("&&&&&&");
			// System.out.println(new String(Arrays.copyOfRange(inArr,
			// 24+(hopCount+1)*21,24+length+(hopCount+1)*21)));

			boolean allReceived = true;
			for (boolean test : receiveTable) {
				if (!test)
					allReceived = false;
			}

			System.out.println("Chuck " + chuckNum + " has been received.");
			System.out.println("Path:");
			for (int i = 0; i < hopCount; i++) {
				System.out.println((new String(Arrays.copyOfRange(inArr,
						45 + i * 21, 45 + 21 + i * 21))).trim() + "  -> ");
			}
			System.out.println(THIS_IP + ":" + PORT);
			
			System.out.println("Send time:"+time);
			System.out.println("Received time:"+df.format(new Date()));

			if (allReceived) {
				File file = new File("output");
				if (file.exists())
					file.delete();
				file.createNewFile();

				FileOutputStream fos = new FileOutputStream(file);

				for (byte[] cur : fileReceiveList) {
					fos.write(cur);
				}
				fos.flush();
				fos.close();

				System.out
						.println("Chucks has been merged into file \"output\".");
				receiveTable = null;
				fileReceiveList = null;
				receiveTime = 0;
			}

		} else {
			/* send to nexthop */
			NetworkNode nextHop = routeTable.get(destIpPort).getNextHop();

			dpOut.setAddress(InetAddress.getByName(nextHop.getIpAddress()));
			dpOut.setPort(nextHop.getPort());

			int fileLength = Util.byteArrayToInt(Arrays.copyOfRange(inArr, 8,
					12));

			byte[] p1 = Arrays.copyOfRange(inArr, 0, 20);
			byte[] p2 = Util.intToByteArray(hopCount + 1);
			byte[] p3 = Arrays.copyOfRange(inArr, 24, 24 + (hopCount + 1) * 21);
			// System.out.println("p1:"+new String(p1));
			// System.out.println("p2:"+Util.byteArrayToInt(p2));
			// System.out.println("p3:"+new String(p3)+"|");

			String thisIpPort = THIS_IP + ":" + PORT;
			int padding = 21 - thisIpPort.length();
			for (int i = 0; i < padding; i++)
				thisIpPort += " ";

			byte[] p4 = thisIpPort.getBytes();
			// System.out.println("p4:"+new String(p4)+"|");
			byte[] p5 = (df.format(new Date()).getBytes());

			byte[] p6 = Arrays.copyOfRange(inArr, 24 +23+ (hopCount + 1) * 21, 24
					+23+ (hopCount + 1) * 21 + fileLength);

			// System.out.println("p5:"+new String(p5)+"|");

			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			outputStream.write(p1);
			outputStream.write(p2);
			outputStream.write(p3);
			outputStream.write(p4);
			outputStream.write(p5);
			outputStream.write(p6);

			byte[] byteContent = outputStream.toByteArray();

			dpOut.setData(byteContent);
			ds.send(dpOut);
		}

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
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private synchronized void sendTableToNeibours() throws Exception {
		for (NetworkNode neighbour : neighbours) {

			String receiverIp = neighbour.getIpAddress();
			int receiverPort = neighbour.getPort();
			// String receiverIpPort = receiverIp + ":" + receiverPort;
			dpOut.setAddress(InetAddress.getByName(receiverIp));
			dpOut.setPort(receiverPort);

			/*
			 * making the update message, the format please refer to the README
			 * file
			 */
			StringBuffer content = new StringBuffer();
			content.append("UPDATE  ");

			ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
			outputStream.write(content.toString().getBytes());

			int entrySize = routeTable.size();
			// if (routeTable.containsKey(receiverIp + ":" + receiverPort)) {
			// entrySize--;
			// }

			outputStream.write(Util.intToByteArray(entrySize));
			outputStream.write("        ".getBytes());

			Iterator<Entry<String, TableEntry>> it = routeTable.entrySet()
					.iterator();
			while (it.hasNext()) {
				Map.Entry<String, TableEntry> pairs = (Map.Entry<String, TableEntry>) it
						.next();

				String key = pairs.getKey();
				TableEntry val = pairs.getValue();

				int paddingCount = 21 - key.length();

				// if (!val.getDestination().getIpPortPair()
				// .equals(receiverIp + ":" + receiverPort)) {

				outputStream.write(key.getBytes());
				for (int i = 0; i < paddingCount; i++) {
					outputStream.write(" ".getBytes());
				}
				outputStream.write(" ".getBytes());

				if (val.getNextHop().getIpPortPair()
						.equals(receiverIp + ":" + receiverPort)
						&& !key.equals(receiverIp + ":" + receiverPort)) {
					// poison reverse
					outputStream.write(Util.toByteArray(MAX));
					// outputStream.write(Util.toByteArray(val.getCost()));
				} else {
					outputStream.write(Util.toByteArray(val.getCost()));
				}
				// }

			}

			byte[] byteContent = outputStream.toByteArray();

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
				try {

					updateTableAfterNeighbourDown(ipPortPair);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		}
	}

	private void updateTableAfterNeighbourDown(String downNeighbourIpPort)
			throws Exception {
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
		sendTableToNeibours();
	}

}
