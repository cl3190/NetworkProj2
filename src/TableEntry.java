
public class TableEntry {

	private String ipAddress;
	private int port;
	private double cost;
	public TableEntry(String ipAddress, int port, double cost) {
		super();
		this.ipAddress = ipAddress;
		this.port = port;
		this.cost = cost;
	}
	
	public TableEntry(String ipPortPair, double cost){
		this.ipAddress = ipPortPair.split(":")[0];
		this.port = Integer.valueOf(ipPortPair.split(":")[1]);
		this.cost = cost;
		
	}
	public String getIpAddress() {
		return ipAddress;
	}
	public void setIpAddress(String ipAddress) {
		this.ipAddress = ipAddress;
	}
	public int getPort() {
		return port;
	}
	public void setPort(int port) {
		this.port = port;
	}
	public double getCost() {
		return cost;
	}
	public void setCost(double cost) {
		this.cost = cost;
	}
	
	
	
	
	
}
