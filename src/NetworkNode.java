
public class NetworkNode {
	private String ipAddress;
	private int port;
	
	public NetworkNode(String ipAddress, int port){
		this.ipAddress = ipAddress;
		this.port = port;
	}
	
	public NetworkNode(String ipPortPair){
		String[] params = ipPortPair.split(":");
		this.ipAddress = params[0];
		this.port = Integer.valueOf(params[1]);
	}

	public String getIpPortPair(){
		return this.ipAddress+":"+this.port;
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
	
	
	
}
