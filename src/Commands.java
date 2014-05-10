
public enum Commands {
	
	LINKDOWN,LINKUP,SHOWRT,CLOSE,TRANSFER, UNKNOWN, TRANS_PLUS;
	
	public static Commands getCommand(String str){
		try{
			return valueOf(str.toUpperCase());
		}catch(Exception ex){
			return UNKNOWN;
		}
	}

}
