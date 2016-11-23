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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;

import GivenTools.TorrentInfo;

public class Uploader implements Runnable{

	private ServerSocket sock;
	private Socket client;
	
	private boolean innerThread;
	
	// Range of ports 6881 - 6889
	private int port;
	
	private InputStream in;
	private OutputStream out;
	
	// Incoming messages not including the handshake
	private byte[] incomingMessage;
	// Incoming handshake message
	private byte[] incomingHandshake = new byte[49+Downloader.BT_PROTOCOL.length];
	
	private static final TorrentInfo torrent = RUBTClient.info;
	private static ArrayList<Thread> threads = new ArrayList<Thread>();
	
	
	public Uploader(ServerSocket sock, Socket client, boolean innerThread){
		this.sock = sock;
		this.client = client;
		this.innerThread = innerThread;
	}
	
	
	@Override
	public void run() {
		try {
			uploadMain();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
	}
	
	
	
	
	
	
	
	public synchronized void uploadMain() throws IOException{
		
		port = 6881;
		
		while(port < 6890){
			try{
				sock = new ServerSocket(port);
				
				// Client-side socket
				client = sock.accept();
				
				// If this was a thread already spawned from this class, we do not want it to spawn another thread
				// That would be a thread within a thread within a thread, and it will go on forever
				// We only want one layer of threads operating under the initial one that was created
				if(!innerThread){
					makeThread(true);
				}
				
				while(true){
					in = client.getInputStream();
					out = client.getOutputStream();
				
					if(!checkAndSendHandshake(client)){
						break;
					}
					
					if(!checkAndGetInterestedMessage(client)){
						break;
					}
					
					if(!getRequestMessage(client)){
						break;
					}
					
					// If the user closed the client, we must stop all running threads, and close connections
					if(Pause.getEnd()){
						closeInnerThreads(threads);
						return;
					}
					
				}
			}
			catch(IOException e){
				port++;
				continue;
			}

		}
		return;
	}


	
	
	
	private synchronized void makeThread(boolean b) {
		Uploader up = new Uploader(sock, client, b);
		if(b){
			return;
		}
		else{
			Thread t = new Thread(up);
			t.start();
			threads.add(t);
		}
		
	}


	private synchronized void closeInnerThreads(ArrayList<Thread> threads2) {
		for(Thread thread : threads){
			thread.interrupt();
		}
	}


	private synchronized boolean getRequestMessage(Socket client) {
		try {
			in.read(incomingMessage);
			
			if(incomingMessage.length != 17){
				// This is not the request message
				return false;
			}
			else{
				if(Downloader.decodeMessage(incomingMessage).equals("request")){
					// If request is legit, get the piece
					byte[] index = new byte[4];
					byte[] offset = new byte[4];
					byte[] length = new byte[4];
					System.arraycopy(incomingMessage, 5, index, 0, 4);
					System.arraycopy(incomingMessage, 9, offset, 0, 4);
					System.arraycopy(incomingMessage, 13, length, 0, 4);
					int i = ByteBuffer.wrap(index).getInt();
					int o = ByteBuffer.wrap(offset).getInt();
					int pieceLength = ByteBuffer.wrap(length).getInt();
					
					if(getRequestedPiece(i, o, pieceLength)){
						return true;
					}
					else{
						return false;
					}
				}
			}
			
			
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		return false;
	}







	private synchronized boolean getRequestedPiece(int i, int o, int pieceLength){

		// Check if we have that piece before uploading
		if(!Downloader.piecesDownloaded.get(i)){
			return false;
		}
		else{
			try{
				// Make the piece message
				byte[] pieceMessage = Downloader.makeMessage(9+pieceLength, 7, 13+pieceLength, i, o, pieceLength);
				// Send the requested piece
				out.write(pieceMessage);
				return true;
			}catch(Exception e){
				e.printStackTrace();
				return false;
			}
		}
	}



	private synchronized boolean checkAndGetInterestedMessage(Socket client) {
		try{
			in.read(incomingMessage);
			
			if(incomingMessage.length != 5){
				// Not the interested message. Close connections to be careful
				in.close();
				out.close();
				client.close();
				sock.close();
				return false;
			}else{
				
				if(Downloader.decodeMessage(incomingMessage).equals("interested")){
					// Send the unchoke message
					out.write(Downloader.makeMessage(1, 1, 5, -1, -1, -1));
					return true;
				}
				else{
					return false;
				}
			}
						
		}catch(Exception e){
			e.printStackTrace();
			return false;
		}
	}







	private synchronized boolean checkAndSendHandshake(Socket client){
		try{
			// Get the handshake message
			in.read(incomingHandshake);
			
			// Verify handshakes
			if(incomingHandshake.length != (49+Downloader.BT_PROTOCOL.length)){
				// This is not a handshake message
				// To prevent malicious activity, we will close connection to the peer
				in.close();
				out.close();
				client.close();
				sock.close();
				return false;
			}
			else{
				// Verify handshake coming in before sending out response handshake
				byte[] infoHashCheck = new byte[20];
				System.arraycopy(incomingHandshake, (incomingHandshake.length - 1) - 40, infoHashCheck, 0, 20);
				byte[] infoHashTorrent = torrent.info_hash.array();
				// Checks if the info hashes are the one from, the same torrent file
				if(!Arrays.equals(infoHashTorrent, infoHashCheck)){
					// If they don't match, close the connection
					in.close();
					out.close();
					client.close();
					sock.close();
					return false;
				}
				// If they do, send the response handshake message
				else{
					byte[] outHandshake = Downloader.handshake(torrent);
					out.write(outHandshake);
					return true;
				}
				
			}
		}catch(Exception e){
			e.printStackTrace();
			return false;
		}
		
	}
	
}





