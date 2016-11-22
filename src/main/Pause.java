/**
 * ****************************************************
 * ****************************************************
 *
 * RUBT - Rutgers BitTorrent Client 
 * Phase 3
 * CS352 - Internet Technology
 *
 * @authors Anthony Castronuovo, Jake Taubner
 *
 * ****************************************************
 * ****************************************************
 */

package main;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

public class Pause extends Thread {

	private static boolean paused = false;
	private static boolean resume = false;
	private static boolean end = false;
	
	private String check = "";
	private BufferedReader in;
	
	@Override
	public void run() {
		try{	
			in = new BufferedReader(new InputStreamReader(System.in));
	    	String inn = "";	    	
	    	
	        while(!check.equalsIgnoreCase("pause") || !check.equalsIgnoreCase("resume") || !check.equalsIgnoreCase("end")){
	        	inn = in.readLine();
	        	if(inn.equalsIgnoreCase("pause")){
	        		paused = true;
	        	}
	        	
	        	else if(inn.equalsIgnoreCase("resume") && paused){
	        		resume = true;
	        		paused = false;
	        	}
	        	
	        	else if(inn.equalsIgnoreCase("end") && paused){
	        		end = true;
	        		break;
	        	}
	        }
	        
		}catch (Exception e){
			e.printStackTrace();
		}
		
	}

	
	public static boolean getPause(){
		return paused;
	}
	
	
	public static boolean getResume(){
		return resume;
	}
	
	
	public static boolean getEnd(){
		return end;
	}
	
	
	@Override
	public void interrupt(){
		try{
			in.close();
		} catch (IOException e) {
			System.out.println("BLAH");
			//super.interrupt();
		}finally{
			System.out.println("Inteerupting the thread");
			super.interrupt();
		}
	}
	
	
	
}
