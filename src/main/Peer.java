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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;

import GivenTools.Bencoder2;
import GivenTools.BencodingException;
import GivenTools.TorrentInfo;

public class Peer {
    
    // The IP's we are going to connect to for downloads
    public static final String PEER_IP_TO_MATCH1 = "172.16.97.11";
    public static final String PEER_IP_TO_MATCH2 = "172.16.97.12";
    public static final String PEER_IP_TO_MATCH3 = "172.16.97.13";

    // ArrayList to hold the peerIDs from the tracker
    private static final ArrayList<String> peerIDList = new ArrayList<>();

    // ArrayList to hold all of the peer IPs and ports
    private static ArrayList<String> peers = new ArrayList<>();
        
    // All the peers we are actually going to use
    // We are tasked with downloading only three peers 
    private static ArrayList<String[]> fastPeers = new ArrayList<>();

    // Our own peer ID.
    private static final byte[] pID = Tracker.generatePeerID();

    // The torrent meta-data
    private static TorrentInfo torrent;
    
    // If we are pausing == true
    public static boolean paused;

    // Interval in which to contact the tracker to update the file
    public static int interval;
    
    private static ServerSocket sock;
    private static Socket client;
    	
    
    
    /**
     * The "main" function for this peer class
     * All the peer functions are run through here
     *
     * @param info Metainfo for the torrent file
     * @param response A list of peers returned from the tracker.
     *
     */
    @SuppressWarnings("rawtypes")
    public static byte[] peerMain(TorrentInfo info, byte[] response, boolean resume) throws Exception {    	
       
    	Peer.torrent = info;

        Map oResponse = decodeResponse(response);
        peers = getPeersFromReponse(oResponse);
                        
        System.out.println("\nDownload of " + torrent.file_name + " has started\n");
        System.out.println("This may take several minutes...\n");

        // Keep track of each thread running
        ArrayList<Thread> threads = new ArrayList<>();

        // Keep track of each download occurring in each thread.
        ArrayList<Downloader> downloads = new ArrayList<>();

        // Make sure the place where the file will be stored is initialized
        // If we are resuming, we just need to get the bytes from the file that was created
        if(resume){
        	// Get the bytes from the file
        	Downloader.downloadedPieces = readResumeFile();
        	putBackPiecesDownloadedList();

        	Tracker.updateTracker(torrent, pID, "started", torrent.file_length - Downloader.downloadedPieces.length);
        }else{
        	Downloader.downloadedPieces = new byte[torrent.file_length];	
        	setInitDownloaded();

        	Tracker.updateTracker(torrent, pID, "started", -1);
        }
        
        // Start the uploading
        Uploader up = new Uploader(sock, client, false);
        Thread initialUploader = new Thread(up);
        initialUploader.start();
        
        
        // Add the peers we need to the fast peers list
        for(int i = 0; i < peers.size(); i++){
        	if(verifyIP(i)){
        		fastPeers.add(peers.get(i).split(":"));
        	}
        }
        
        final int piecesPerPeer = torrent.piece_hashes.length / fastPeers.size(); // Number of pieces each peer should download
        final int lastPeer = torrent.piece_hashes.length - piecesPerPeer;
        
        
        // Start and run the threads
        startRunning(piecesPerPeer, lastPeer, threads, downloads);
        
        // Send the tracker updates at each interval
        Timer interval = new Timer();
        interval.schedule(new IntervalTimer(torrent, pID, "", torrent.file_length-Downloader.downloadedPieces.length), 0, (long)Peer.interval);

        // Starts new thread to wait on user input 
        Pause pp = new Pause();
        Thread p = new Thread(pp);
        p.start();

        byte[] combinedPieces = new byte[torrent.file_length];

        // Keep looping until we hit the end
        while(!Pause.getEnd()){
        	
        	Thread.sleep(15000);
	        // If the user paused, write the file and wait on user input to unpause the client 
	        if(Pause.paused){
	        	
	           	// If the pause signal comes through, write the downloaded and verified pieces to the file
	            StringBuffer s = new StringBuffer();
	            for(int i = 0; i < Downloader.piecesDownloaded.size(); i++){
	            	if(Downloader.piecesDownloaded.get(i)){
	            		s.append("1");
	            		s.append(" ");
	            	}
	            	else{
	            		s.append("0");
	            		s.append(" ");
	            	}
	            }
	            
	            FileOutputStream writerArray = new FileOutputStream(Downloader.pauseDownName, false);
	            writerArray.write(s.toString().getBytes());
	            writerArray.close();
	        	FileOutputStream writer = new FileOutputStream(Downloader.pauseDataName, false);
	        	writer.write(Downloader.downloadedPieces);
	        	writer.close();		
	        	
	        	// Update the tracker that we stopped downloading
	        	Tracker.updateTracker(info, pID, "stopped", torrent.file_length - Downloader.downloadedPieces.length);
	        	
	        	System.out.println("The download is now paused.");
	        	System.out.println("To resume the download, type 'resume'.");
	        	System.out.println("To end the client, type 'end'. All progress of the download will be saved.\n");
	
		        // While the program is paused, we loop
		        while(Pause.getPause()){   
		        	
		        	Thread.sleep(15000);
		            // Checks if we are exiting, and if so, exiting before full completion of the file
		            if(RUBTClient.checkIfPaused() && Pause.getPause() && Pause.getEnd()){
		            	Tracker.updateTracker(info, pID, "stopped", torrent.file_length - Downloader.downloadedPieces.length);
		            	p.interrupt();
		            	initialUploader.interrupt();
		            	return null;
		            }
		           
		        }
	        }
	        
	    	// Resuming the download
	        if(Pause.getResume()){
	        	Tracker.updateTracker(info, pID, "started", torrent.file_length - Downloader.downloadedPieces.length);
	        	Pause.resume = false;
	        	startRunning(piecesPerPeer, lastPeer, threads, downloads);
	        }    
	        
	        // If all pieces are downloaded, we can exit the loop, and close the program
	        if(Downloader.checkAllPieces()){
		        p.interrupt();
	        	Pause.setEnd(true);
	        	joinThreads(threads);
	        }

        }
        
        // Return the total file in bytes
        combinedPieces = downloads.get(0).getPieces();
        initialUploader.interrupt();
        return combinedPieces;

    }



    /**
     * Starts the download threads
     * 
     * @param piecesPerPeer
     * @param lastPeer
     * @param threads
     * @param downloads
     */
    private static void startRunning(int piecesPerPeer, int lastPeer, ArrayList<Thread> threads, ArrayList<Downloader> downloads){
	    // Create each downloader and begin the download in a new thread
	    for (int i = 0; i < fastPeers.size(); ++i) {
	    	
	    	final int firstPieceIndex;
	    	final int lastPieceIndex;
	    	
	    	// Checks if the section of pieces we are downloading are at the end.
	    	// If so, we calculate the piece indexes differently
	    	if(i != (fastPeers.size()-1)){
	    		firstPieceIndex = i * piecesPerPeer + i;
	    		lastPieceIndex = firstPieceIndex + piecesPerPeer;
	    	}
	    	else{
	    		firstPieceIndex = lastPeer + 1;
	    		lastPieceIndex = torrent.piece_hashes.length-1;
	    	}
	    	
	    	//Initialize the downloaders and the threads, and start them            
	        Downloader dl = new Downloader(firstPieceIndex, lastPieceIndex, fastPeers.get(i));
	        Thread t = new Thread(dl); 
	        t.start();
	        downloads.add(dl);
	        threads.add(t);
	    }
    }

 
    
    /**
     * Joins the threads in the threads ArrayList
     * 
     * @param threads
     * @throws InterruptedException
     */
    private static void joinThreads(ArrayList<Thread> threads) throws InterruptedException{
	    for(int i = 0; i < threads.size(); i++){
	    	threads.get(i).join();
	    }
    }
   
    

    /**
     * Decodes the response from the tracker
     *
     * @param response A list of peers returned from the tracker.
     * @return nResponse Object
     */
    @SuppressWarnings("rawtypes")
    public static Map decodeResponse(byte[] response) {
        Object oResponse = null;

        try {
            oResponse = Bencoder2.decode(response);
        } catch (BencodingException b) {
            System.out.println("Response from the tracker could not be decoded.");
        }

        return (Map) oResponse;
    }

    
    
    /**
     * Gets the list of peerIDs, peerIPs, and peer ports from the parsed response
     *
     * @param response
     * @return peerURLs
     * @throws UnsupportedEncodingException
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public static ArrayList<String> getPeersFromReponse(Map map) throws UnsupportedEncodingException {

        // Init the byte fields for the peer information
        // Using 50 for the byte size so there is less of a chance of an overflow
        byte[] id = new byte[50];
        byte[] ip = new byte[50];
        int port = 0;

        interval = (int) map.get(ByteBuffer.wrap("interval".getBytes()));
        
        ArrayList<ByteBuffer> peerKey = (ArrayList) map.get(ByteBuffer.wrap("peers".getBytes()));
        ArrayList<String> peerInfo = new ArrayList<>();

        Iterator i = peerKey.iterator();
        Object o;

        // Iterate through the list of dictionaries that hold the peer information
        while (i.hasNext() && (o = i.next()) != null) {
            Map p = (Map) o;            
            
            // Gets the port information
            port = (int) p.get(ByteBuffer.wrap("port".getBytes()));

            // Gets the peer ip information
            Object peersipO = p.get(ByteBuffer.wrap("ip".getBytes()));
            ByteBuffer ipo = (ByteBuffer) peersipO;
            ip = ipo.array();

            // Gets the peer id information
            Object peersidO = p.get(ByteBuffer.wrap("peer id".getBytes("utf8")));
            ByteBuffer ido = (ByteBuffer) peersidO;
            id = ido.array();

            // Add the peer information to the peerInfo ArrayList separated by a ':'
            // Add the peer id to peerID ArrayList
            peerInfo.add(new String(ip) + ":" + port);
            peerIDList.add(new String(id));
        }

        return peerInfo;
    }

    
    
    /**
     * Verify that the peer IP is correct by comparing it to the peer prefix.
     * @param index The index of peer IP to verify.
     * @return true if the peer IP matches any one of the three we will use. False otherwise.
     */
    private static boolean verifyIP(int index) {
        boolean b = false;
    	final String peerID = peers.get(index);
        final String ID = peerID.substring(0, 12);
        
        if(ID.equals(PEER_IP_TO_MATCH1) || ID.equals(PEER_IP_TO_MATCH2) || ID.equals(PEER_IP_TO_MATCH3)){
        	b = true;
        	
        }
        return b;
    }  
    
    
    
    /**
     * Returns the generated peer ID that designates this client to other peers
     * @return the peer ID of this client
     */
    public static byte[] getPID() {
        return pID;
    }

    
    
    /**
     * Returns the torrent meta-data info 
     * @return the torrent meta-data info
     */
    public static TorrentInfo getTorrent() {
        return torrent;
    }
    
    
    
    /**
     * Read the file that was created to set the downloadedPieces
     * @throws Exception 
     */
    private static byte[] readResumeFile() throws Exception{
    	@SuppressWarnings("resource")
		FileInputStream n = new FileInputStream("PD");
    	
    	byte[] resumedBytes = new byte[torrent.file_length];
    	n.read(resumedBytes);
    	
    	return resumedBytes;
    }
    
    
    
    /**
     * Reads the file where the piecesDownloaded ArrayList data is stored
     * and puts the data back into that ArrayList to be used for download resume after it has been paused
     * 
     * @throws Exception
     */
    private static void putBackPiecesDownloadedList() throws Exception{
    	@SuppressWarnings("resource")
		BufferedReader in = new BufferedReader(new FileReader("DPA"));
    	String line;
    	while((line = in.readLine()) != null){
    		StringTokenizer tokens = new StringTokenizer(line, " ");
        	while(tokens.hasMoreElements()){
        		if(tokens.nextToken().contains("1")){
        			Downloader.piecesDownloaded.add(true);
        		}
        		else{
        			Downloader.piecesDownloaded.add(false);
        		}
        		
        	}   
        }

    }
    
    
    
    /**
     * Initializes the piecesDownlaoded ArrayList if there is a fresh, non-paused download
     */
    private static void setInitDownloaded(){
    	for(int i = 0; i < torrent.piece_hashes.length; i++){
    		Downloader.piecesDownloaded.add(i, false);
    	}
    }    
}



class IntervalTimer extends TimerTask{

	private TorrentInfo torrent;
	private byte[] pID;
	private String event;
	private int left;
	
	
	public IntervalTimer(TorrentInfo torrent, byte[] pID, String event, int left){
		this.torrent = torrent;
		this.pID = pID;
		this.event = event;
		this.left = left;
		
	}
	
	
	@Override
	public void run() {
		Tracker.updateTracker(torrent, pID, event, left);
	}
	
}
