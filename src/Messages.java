
public enum Messages {

	
	UPDATE, LINKDOWN, LINKUP, TRANSFER, UNKNOWN;
	
	public static Messages getCommand(String str){
		try{
			return valueOf(str.toUpperCase());
		}catch(Exception ex){
			return UNKNOWN;
		}
	}
}
