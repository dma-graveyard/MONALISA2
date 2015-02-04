package dk.dma.epd;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;


public class Main {
	
	/**
	 * Show a usage page..
	 * For development, you can point the plugin_classpath to ${basedir}/target/classes.
	 * 
	 * @param args
	 */
	public static void main(String[] args) {
		
		
		showUsage();
		System.exit(1);
	}
	
	  private static void showUsage() {
	        InputStream input = Main.class.getResourceAsStream("/README.md");
	        try {
	            int ch;
	            while ((ch = input.read()) != -1) {
	                System.out.print( (char) ch);
	            }
	        } catch (IOException e) {
	            System.err.println("-- could not read usage : " + e.getMessage());
	        } finally {
	            try {
	                input.close();
	            } catch (IOException e) {
	                System.err.println("++ could not release usage file : " + e.getMessage());
	            }
	        }

	    }


}
