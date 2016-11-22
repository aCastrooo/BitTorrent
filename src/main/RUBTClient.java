/**
 * ****************************************************
 * ****************************************************
 *
 * RUBT - Rutgers BitTorrent Client Phase 1 CS352 - Internet Technology
 *
 * @authors Anthony Castronuovo, Jake Taubner
 *
 *          ****************************************************
 *          ****************************************************
 */

package main;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.text.DecimalFormat;
import java.util.Random;
import java.util.Scanner;

import GivenTools.TorrentInfo;



public class RUBTClient {

    // Where all the parsed metadata will be stored
    public static TorrentInfo info;
    public static Random rand = new Random();

    // The boolean that will dictate if the download has been paused or not
    // Initially set to false
    private static boolean resume = false;
    
    
    public static void main(String[] args) throws Exception {

        String torrentFileName = null;
        String fileOutName = null;

        // Checking if there are less than two arguments or more than two arguments
        // If more than or less than two arguments, then the program will terminate after returning
        // an error message
        if (args.length != 2) {
            System.err.println("Invalid number of arguments.");
            System.out.print("\n");
            System.out.println("<.torrent file> <Name of output file>");
            System.exit(-1);

        }
        else {
            // The name of the torrent file being decoded from first argument
            torrentFileName = args[0];
            // The name of the outgoing file that the decoded bytes will go into
            fileOutName = args[1];

            // Extension extracts the file extension name from torrentFile
            String extension = torrentFileName.substring(torrentFileName.lastIndexOf(".") + 1, torrentFileName.length());
            String check = "torrent";

            // Checks if extension, is equal to the torrent file extension
            // If they are not equal, then the program will terminate after returning an error
            // message
            if (!check.equals(extension)) {
                System.out.println("Invalid file type. You must enter a \".torrent\" file.");
                System.exit(-1);
            }
        }

        // This is for interacting with the program. To start, user must type start
    	@SuppressWarnings("resource")
		Scanner s = new Scanner(System.in);
        String userResponse;
    	do{
        	System.out.println("====================================================================");
        	System.out.println("To start the download process of the torrent you specified, \nplease type \"start\" into the command line:");
        	System.out.println("At any time you may type \"pause\" to pause the download. \nProgress will be saved for next time you decide to continue the download.");
        	userResponse = s.nextLine();
        	System.out.println("====================================================================\n");
        	
        }while(!userResponse.equalsIgnoreCase("start"));
    	
    	// Check if we are resuming a download
    	resume = checkIfPaused();
        
    	// Start the time calculation now
    	long start_time = System.nanoTime();
    	
        File torrentFile = new File(torrentFileName);

        // Calls parse method from TorrentParse class to parse the torrent file
        info = TorrentParse.parse(torrentFile);

        // If info returns null, there was a problem, so the program will terminate
        if (info != null) {
            System.out.println("The torrent file was successfully parsed!");
        }
        else {
            System.err.println("There was a problem parsing the torrent file");
            System.exit(-1);
        }

        // Displays the file name being downloaded
        System.out.println("File Name: " + info.file_name);

        // Response is the information that the tracker returns after we send the GET request
        byte[] pID = Peer.getPID();
        byte[] response = Tracker.getRequest(info, pID);
        if (response != null) {
            System.out.println("Tracker connection successful!");
        }
        
    	if(resume){
    		System.out.println("Resuming download of " + info.file_name);
    	}
        
        // Connection to peer, download the file, all peer functions done through here
        byte[] file = Peer.peerMain(info, response, resume);

        if (file == null && !checkIfPaused()) {
            System.err.println("There was an error downloading the file.");
            System.exit(-1);
        }
        else if(file == null && checkIfPaused()){
        	System.out.println("Download is paused. Exiting program. \nYou can return at any time to complete the download.");
        	System.out.println("====================================================================\n");
        	System.exit(0);
        }

        // Create and write the downloaded file
        FileOutputStream writer = new FileOutputStream(fileOutName);
        writer.write(file);
        writer.close();

        // Contact the tracker and tell it that the download has completed
        Tracker.updateTracker(info, pID, "completed", 0);

        // Notify the user the file has been downloaded and created
        System.out.println("\nFile: " + info.file_name + " finished downloaded!");
        
        // Output the total time elapsed
        long end_time = System.nanoTime() - start_time;
        double end_time_seconds = (double)end_time / 1000000000.0;
        double end_time_minutes = (double)end_time / 60000000000.0;
        
        // Clean up for seconds
        String secondsTime = new DecimalFormat("#.##").format(end_time_seconds);
        
        if(end_time_seconds < 60.0){
        	System.out.println("Total time to download: " + secondsTime + " seconds.");
        }
        else{
        	// Format the time
        	String minutesTime = new DecimalFormat("#.##").format(end_time_minutes);
        	
        	//Extract the minutes and the seconds from the formatted string
        	String minutesTimeOut = minutesTime.substring(0, 1);
        	String minutesTimeSeconds = minutesTime.substring(2);
        	
        	int minutesTO = Integer.parseInt(minutesTimeOut);
        	double secondsTO = Double.parseDouble(minutesTimeSeconds);
        	
        	if(secondsTO > 60){
        		secondsTO = secondsTO / 60;
        		String m = new DecimalFormat("#.##").format(secondsTO);
        		int mm = Integer.parseInt(m.substring(0, 1));
        		minutesTO += mm;
        		secondsTO = Double.parseDouble(m.substring(2));
        	}
        			
        	System.out.println("Total time to download: " + minutesTO + " minutes " + (int)secondsTO + " seconds");
        }
        
        System.out.println("Client exiting.");
    	System.out.println("====================================================================\n");
        System.exit(0);
    }
    
    
    
    
    /**
     * Checks if the files for resumed have been created
     * 
     * @return yn Returns true if the files have been made, false otherwise
     */
	public static boolean checkIfPaused(){
    	boolean yn;
    	try{
    		@SuppressWarnings({ "unused", "resource" })
			FileInputStream n = new FileInputStream("PD");
    		@SuppressWarnings({ "unused", "resource" })
			FileInputStream m = new FileInputStream("DPA");
    		yn = true;
    		
    	}catch (FileNotFoundException e){
    		// If there is no file, we are not resuming 
    		yn = false;
    	}
    	return yn;
    }
    

}
