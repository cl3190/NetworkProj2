import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.*;


public class Util {
	public static int byteArrayToInt(byte[] b) 
	{
	    return   b[3] & 0xFF |
	            (b[2] & 0xFF) << 8 |
	            (b[1] & 0xFF) << 16 |
	            (b[0] & 0xFF) << 24;
	}

	public static byte[] intToByteArray(int a)
	{
	    return new byte[] {
	        (byte) ((a >> 24) & 0xFF),
	        (byte) ((a >> 16) & 0xFF),   
	        (byte) ((a >> 8) & 0xFF),   
	        (byte) (a & 0xFF)
	    };
	}
	
	
	public static byte[] toByteArray(double value) {
	    byte[] bytes = new byte[8];
	    ByteBuffer.wrap(bytes).putDouble(value);
	    return bytes;
	}

	public static double toDouble(byte[] bytes) {
	    return ByteBuffer.wrap(bytes).getDouble();
	}
	
	public static byte[] fileToByteArray(File file) throws IOException{
		byte[] b = new byte[(int) file.length()];
        try {
              FileInputStream fileInputStream = new FileInputStream(file);
              fileInputStream.read(b);
              //for (int i = 0; i < b.length; i++) {
              //            System.out.print((char)b[i]);
               //}
              fileInputStream.close();
         } catch (FileNotFoundException e) {
                     System.out.println("File Not Found.");
                     e.printStackTrace();
         }
         catch (IOException e1) {
                  System.out.println("Error Reading The File.");
                   e1.printStackTrace();
         }
        
        return b;
	}
	
	public static void main(String[] args){
		byte[] test = Util.toByteArray(1.0309635106057484E-71);
		System.out.println(test);
	}
	
	
}
