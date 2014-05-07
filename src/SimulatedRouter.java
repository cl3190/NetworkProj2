import java.io.File;
import java.io.IOException;
import java.net.*;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;


public class SimulatedRouter {

	private int PORT;
	private double TIMEOUT;
	private String TRANSFILE;
	private int CHUCKNUM;

	private List<TableEntry> neighbours = new ArrayList<TableEntry>();
	private HashMap<String, TableEntry> routeTable = new HashMap<String, TableEntry>();
	private HashMap<String, Timer> deadTimerTable = new HashMap<String, Timer>();

	private DatagramSocket ds = null;
	private DatagramPacket dpIn = null;
	private DatagramPacket dpOut = null;

	// private DateFormat df= new SimpleDateFormat("yyyy-MM-dd$hh:mm:ss");

	public SimulatedRouter(int port, double timeout, String transportFile,
			int chuckNum, List<String> entryList) {
		this.PORT = port;
		this.TIMEOUT = timeout;
		this.TRANSFILE = transportFile;
		this.CHUCKNUM = chuckNum;
		
		for(String line : entryList){
			String ipPortPair = line.split("\\s+")[0];
			double cost = Double.valueOf(line.split("\\s+")[1]);
			
			TableEntry entry = new TableEntry(ipPortPair, cost);
			neighbours.add(entry);
			routeTable.put(ipPortPair, entry);
			
			Timer timer = new Timer();
			timer.schedule(new TimerTaskForDead(name), this.TIMEOUT*3);
			this.deadTimerTable.put(ipPortPair, timer);
			
		}
		
		
		
		
		
		
		ChangeListener changeListener = new ChangeListener();
		
		changeListener.start();

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
			double timeout = 0;
			String transportFile = "";
			int chuckNum = 0;

			// read in the first line
			if (scanner.hasNextLine()) {
				line = scanner.nextLine();
				String[] params = line.split("\\s+");
				port = Integer.valueOf(params[0]);
				timeout = Double.valueOf(params[1]);
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

					String info = new String(dpIn.getData(), 0,
							dpIn.getLength());
					if (info.equals("end"))
						break;
					String type = info.substring(0, 6).trim();
					// System.out.println(type);
					if (type.equals("UPDATE")) {
						responseForUpdate();
					} else if (type.equals("DOWN")) {
						responseForDown();
					} else if (type.equals("UP")) { // for up
						responseForUp();
					}
				} catch (IOException e) {
					// e.printStackTrace();
				}
			}
		}
	}
	
	
	class TimerTaskForDead extends TimerTask{
		String client;
		public TimerTaskForDead(String c){
			this.client = c;
		}
		
		public void run(){
			System.out.println("3 timeout has passed!! Neighbor: "+client+"  is dead");
			if(routeTable.containsKey(deadClient)){
				for(int i=0; i<neibors.size(); i++){
					if(neibors.get(i).getClient().equals(deadClient)){
						neibors.remove(i);  //remove it from neighbor list
					}
				}
				
				//set infinity(MAX)
				routeTable.put(deadClient, new Entry(MAX, new Client(deadClient.split(":")[0], deadClient.split(":")[1])));
			}
		}
	}
	
}
