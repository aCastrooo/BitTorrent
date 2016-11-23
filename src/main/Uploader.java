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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;

import GivenTools.TorrentInfo;

public class Uploader implements Runnable{

	private ServerSocket sock;
	
	// Range of ports 6881 - 6889
	private int port;
	
	private InputStream in;
	private OutputStream out;
	
	// Incoming messages not including the handshake
	private byte[] incomingMessage;
	// Incoming handshake message
	private byte[] incomingHandshake = new byte[49+Downloader.BT_PROTOCOL.length];
	
	private static final TorrentInfo torrent = Peer.getTorrent();
	
	
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
				Socket client = sock.accept();
				
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
					
				}
			}
			catch(IOException e){
				port++;
				continue;
			}
			
			
		}
		
		return;
	
	}

	
	private synchronized boolean getRequestMessage(Socket client) {
		try {
			in.read(incomingMessage);
			
			if(incomingMessage.length != 13){
				// This is not the request message
				return false;
			}
			else{
				
			}
			
			
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		
		
		
		
		
		return false;
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





