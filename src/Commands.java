
public enum Commands {
	
	LINKDOWN,LINKUP,SHOWRT,CLOSE,TRANSFER, UNKNOWN;
	
	public static Commands getCommand(String str){
		try{
			return valueOf(str.toUpperCase());
		}catch(Exception ex){
			return UNKNOWN;
		}
	}

}
