
public class TableEntry {

	private NetworkNode nextHop;
	private NetworkNode destination;
	private double cost;
	public TableEntry(NetworkNode nextHop, NetworkNode destination, double cost) {
		super();
		this.nextHop = nextHop;
		this.destination = destination;
		this.cost = cost;
	}
	
	public NetworkNode getNextHop() {
		return nextHop;
	}
	public void setNextHop(NetworkNode nextHop) {
		this.nextHop = nextHop;
	}
	public NetworkNode getDestination() {
		return destination;
	}
	public void setDestination(NetworkNode destination) {
		this.destination = destination;
	}
	public double getCost() {
		return cost;
	}
	public void setCost(double cost) {
		this.cost = cost;
	}
	
	
	
	
	
	
}
