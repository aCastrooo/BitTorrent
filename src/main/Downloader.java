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
import java.net.Socket;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;

import GivenTools.TorrentInfo;

public class Downloader implements Runnable {

	// Our own peer ID.
    private static final byte[] pID = Peer.getPID();
    
    private static final TorrentInfo torrent;

	// Inits the IDs of the different kinds of messages
    private static final byte CHOKE_ID = 0;
    private static final byte UNCHOKE_ID = 1;
    private static final byte INTERESTED_ID = 2;
    private static final byte N_INTERESTED_ID = 3;
    private static final byte HAVE_ID = 4;
    private static final byte BITFIELD_ID = 5;
    private static final byte REQUEST_ID = 6;
    private static final byte PIECE_ID = 7;
    private static final byte CANCEL_ID = 8;
    private static final byte PORT_ID = 9;

    // The protocol that is used in the handshake message
    public static final byte[] BT_PROTOCOL = new byte[] { 'B',
                                                           'i',
                                                           't',
                                                           'T',
                                                           'o',
                                                           'r',
                                                           'r',
                                                           'e',
                                                           'n',
                                                           't',
                                                           ' ',
                                                           'p',
                                                           'r',
                                                           'o',
                                                           't',
                                                           'o',
                                                           'c',
                                                           'o',
                                                           'l' };

    public static byte[] downloadedPieces; // This is what will be returned to Peer
    private String[] peerIP;
    private int firstPieceIndex;
    private int lastPieceIndex;
        
    // The file we will save data to
    public final static String pauseDataName = "PD";
    public final static String pauseDownName = "DPA";
    
    
    // The array list of booleans where each index is a piece
    // Set to 1 if we have that piece, 0 if we still need to download it
    public static ArrayList<Boolean> piecesDownloaded = new ArrayList<Boolean>();
    
    
    static {
        torrent = Peer.getTorrent();
    }
    

    
    public Downloader(int firstPieceIndex, int lastPieceIndex, String[] peerIP) {
        this.firstPieceIndex = firstPieceIndex;
        this.lastPieceIndex = lastPieceIndex;
        this.peerIP = peerIP;
    }


    
	@Override
	public void run() {
		try {
			downloadFromPeer();
			// 15 seconds to wait for other threads to finish
			Thread.sleep(15000);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public final byte[] getPieces() {
		return downloadedPieces;
	}

    /**
     * Downloads the piece from the peer
     *
     * @param peerIP An array of size two where the first element is the peer's IP address
     * and the second element is the peer's port number.
     * @param torrent Metadata for the torrent we are downloading.
     * @return A byte array containing all of the byte sof the downloaded file in correct order.
     * @throws Exception
     */
    public final synchronized void downloadFromPeer() throws Exception {
    	
        // Init the socket
        Socket peerSock = new Socket(peerIP[0], Integer.parseInt(peerIP[1]));
        // This is data to be sent to the peer;
        OutputStream toPeer = peerSock.getOutputStream();
        // This is data to be received from the peer
        InputStream fromPeer = peerSock.getInputStream();
        
        // Create and verify the handshake message
        byte[] message = handshake(torrent);

        // If the handshake is verified, move onto creating messages
        if (verifyHandshake(message, toPeer, fromPeer)) {

            System.out.println("Handshake accepted!");

            // Create and send the interested message
            byte[] interestedMsg = makeMessage(1, 2, 5, -1, -1, -1);
            if (interestedMsg == null) {
                System.err.println("There was a problem in creating the interested message.");
                closeConnections(peerSock, fromPeer, toPeer);

            }

            System.out.println("Sending message to the peer...");

            toPeer.write(interestedMsg);

            // Checks for the unchoke message
            while (!decodeMessage(message).equals("unchoke")) {
                message = getMessage(fromPeer, interestedMsg.length);

            }

            System.out.println("Peer responded with unchoke message!");

            // Set block size to be 16KB
            int block_size = torrent.piece_length / 2;
            // Gets number of pieces to be downloaded
            int numPieces = torrent.piece_length;
            // Number of blocks
            int numBlocksPerPiece = numPieces / block_size;
            // Length of the piece
            int pLength;
            
            // As long as the user has not paused the download, we can continue to download pieces
        
			for (int i = firstPieceIndex; i < lastPieceIndex+1; i++) {
			
			    // If we have downloaded this piece, move on to the next piece
			    if(piecesDownloaded.get(i)){
			    	continue;
			    }
			    else{
				    System.out.println("Downloading Piece: " + (i + 1));
			    }
			    
			    // Check if this is the last piece, and if it is, calculate the new piece length
			    if (i == torrent.piece_hashes.length - 1) {
			        pLength = torrent.file_length - torrent.piece_length * (torrent.piece_hashes.length - 1);
			    }
			    else {
			        pLength = torrent.piece_length;
			    }
			
			    // Send the request message
			    byte[] requestMsg = makeMessage(13, 6, 17, i, 0, pLength);
			    toPeer.write(requestMsg);
			
			    // Get the piece from the peer
			    byte[] newPiece = getPiece(fromPeer, numBlocksPerPiece, pLength);
			    if (newPiece == null) {
			        System.err.println("There was a problem downloading piece: " + (i + 1));
			        break;
			    }
			
			    // Verify that the piece hash matches up with the hash from the torrent file
			    if (verifyPieceHash(newPiece, i, torrent)) {
			        // The have message, telling the peer we now have the piece of the file
			        toPeer.write(makeMessage(5, 4, 9, i, -1, -1));
			        piecesDownloaded.set(i, true);
			
			        // Write the piece to the file bytes
			        System.arraycopy(newPiece,0,downloadedPieces,i*pLength,newPiece.length);
			    }
			    else {
			        System.err.println("Piece hash did not match.");
			            break;
			    }
			 
				if(Pause.getPause()){
			    	return;
			    }
			}
    	
        }    
        else {
            System.err.println("There was a problem contacting the peer.\nHandshake did not match.");
        }

        closeConnections(peerSock, fromPeer, toPeer);
        return;
    }

    

    /**
     * Creates the handshake message
     *
     * @param info
     * @return handshakeMessage
     * @throws IOException
     */
    public static byte[] handshake(TorrentInfo info) throws IOException {

        int i = 0;
        int count;
        byte[] handshakeMessage = new byte[49 + BT_PROTOCOL.length];
        byte[] infoHash = info.info_hash.array();

        // The pstrlen
        handshakeMessage[0] = (byte) 19;

        // Combine the pstr with the pstrlen
        for (i = 1; i <= BT_PROTOCOL.length; i++) {
            handshakeMessage[i] = BT_PROTOCOL[i - 1];
        }
        for (count = 0; count < 8; count++) {
            handshakeMessage[i + count] = (byte) 0;
        }
        i += count;

        // Add info_hash to the end of the array
        for (count = 0; count < infoHash.length; count++) {
            handshakeMessage[i + count] = infoHash[count];
        }
        i += count;

        // Add peer ID to the end of the array
        for (count = 0; count < pID.length; count++) {
            handshakeMessage[i + count] = pID[count];
        }
        i += count;

        return handshakeMessage;
    }

    /**
     * Makes the messages to be sent to the peer
     *
     * @param lengthPrefix
     * @param messageID
     * @param expectedMessageSize
     * @param index
     * @param begin
     * @param length
     * @return
     * @throws Exception
     */
    public static byte[] makeMessage(int lengthPrefix, int messageID, int expectedMessageSize, int index, int begin, int length) throws Exception {
        // The message to send
        byte[] message = new byte[expectedMessageSize];
        int i = 0;

        // Set first three 0s for the len
        for (i = 0; i < 3; i++) {
            message[i] = (byte) 0;
        }
        // Set the last byte of the len
        message[i] = (byte) lengthPrefix;
        i++;
        // Set the ID
        switch (messageID) {
        case 0:
            message[i] = CHOKE_ID;
            break;
        case 1:
            message[i] = UNCHOKE_ID;
            break;
        case 2:
            message[i] = INTERESTED_ID;
            break;
        case 3:
            message[i] = N_INTERESTED_ID;
            break;
        case 4:
            message[i] = HAVE_ID;
            break;
        case 5:
            message[i] = BITFIELD_ID;
            break;
        case 6:
            message[i] = REQUEST_ID;
            break;
        case 7:
            message[i] = PIECE_ID;
            break;
        case 8:
            message[i] = CANCEL_ID;
            break;
        case 9:
            message[i] = PORT_ID;
            break;
        default:
            throw new Exception("Bad message ID.");
        }
        i++;

        if (index >= 0) {
            byte[] temp = ByteBuffer.allocate(4).putInt(index).array();
            System.arraycopy(temp, 0, message, i, temp.length);
            i += 4;
        }

        if (begin >= 0) {
            byte[] temp = ByteBuffer.allocate(4).putInt(begin).array();
            System.arraycopy(temp, 0, message, i, temp.length);
            i += 4;
        }

        if (length >= 0) {
            byte[] temp = ByteBuffer.allocate(4).putInt(length).array();
            System.arraycopy(temp, 0, message, i, temp.length);
        }

        return message;

    }

    

    /**
     * Checks to see if the returned handshake is viable
     *
     * @param message
     * @return boolean
     * @throws IOException
     */
    private static boolean verifyHandshake(byte[] message, OutputStream toPeer, InputStream fromPeer) throws IOException {
        byte[] response = new byte[49 + BT_PROTOCOL.length];

        try {
            toPeer.write(message);
        } catch (IOException e) {
            System.err.println("Could not send message to the peer.");
        }

        fromPeer.read(response);

        // Check if the two byte arrays are the same
        // If they are the same, then the info hash, and the peer IDs will be the same
        if (checkHash(message, response)) {
            return true;
        }
        else {
            return false;
        }
    }

    
    
    /**
     * Checks to see if the two handshake info hashes from the peer and from the user client are the same
     *
     * @param message
     * @param response
     * @return boolean
     */
    private static boolean checkHash(byte[] message, byte[] response) {

        for (int i = 0; i < 20; i++) {
            if (message[i + 28] != response[i + 28]) {
                return false;
            }
        }
        return true;
    }


    
    /**
     * Returns the message the peer sent to us
     *
     * @param fromPeer
     * @param size
     * @return message
     * @throws IOException
     */
    public static byte[] getMessage(InputStream fromPeer, int size) throws IOException {
        int messageLength;

        // While there are still bytes to read, and they don't exceed the size of the needed response,
        // we change the message length
        while ((messageLength = fromPeer.available()) < size) {
            // do nothing
        }

        byte[] message = new byte[messageLength];
        fromPeer.read(message);

        return message;
    }


    
    /**
     * Gets the piece from the peer following the request message
     *
     * @param fromPeer
     * @param blocksPerPiece
     * @param length
     * @return pieceData
     * @throws Exception
     */
    public static byte[] getPiece(InputStream fromPeer, int blocksPerPiece, int length) throws Exception {
        byte[] newPiece = null;
        byte[] pieceData = null;

        // Using 13+length because we are interested in the piece data, not the actual message preceding it
        newPiece = getMessage(fromPeer, 13 + length);

        if (decodeMessage(newPiece).equals("piece")) {
            pieceData = getActualPieceInfo(newPiece, 13);
        }

        return pieceData;
    }

    
    
    /**
     * Separates the message component from the actual piece bytes
     *
     * @param oldPiece
     * @param headerLength
     * @return piece
     */
    private static byte[] getActualPieceInfo(byte[] oldPiece, int headerLength) {
        int pieceLength = oldPiece.length - headerLength;
        byte[] newPiece = new byte[pieceLength];

        System.arraycopy(oldPiece, headerLength, newPiece, 0, pieceLength);
        // System.arraycopy(src, srcPos, dest, destPos, length);
        return newPiece;
    }

    
    
    /**
     * Takes in the message received from the peer and figures out which message the peer sent
     *
     * @param message
     * @return decoded message
     */
    public static String decodeMessage(byte[] message) {

        if (message.length < 4) {
            return null;
        }
        else if (message.length == 4) {
            return "keep-alive";
        }
        else {
            switch (message[4]) {
            case 0:
                return "choke";
            case 1:
                return "unchoke";
            case 2:
                return "interested";
            case 3:
                return "not_interested";
            case 4:
                return "have";
            case 5:
                return "bitfield";
            case 6:
                return "request";
            case 7:
                return "piece";
            case 8:
                return "cancel";
            case 9:
                return "port_id";
            default:
                return "invalid";
            }
        }
    }

    
    
    /**
     * Closes the connections to the peer
     *
     * @param peerSock
     * @param fromPeer
     * @param toPeer
     * @throws IOException
     */
    private static void closeConnections(Socket peerSock, InputStream fromPeer, OutputStream toPeer) throws IOException {
        fromPeer.close();
        toPeer.close();
        peerSock.close();
    }

    
    
    /**
     * Compares the SHA-1 encoded piece from the peer, to the actual piece hash from the torrent file
     *
     * @param index
     * @param torrent
     * @return boolean
     */
    private static boolean verifyPieceHash(byte[] piece, int index, TorrentInfo torrent) {
        byte[] hash = encodeToSHA1(piece);
        byte[] b = torrent.piece_hashes[index].array();

        if (Arrays.equals(hash, b)) {
            return true;
        }
        else {
            return false;
        }
    }


    
    /**
     * Encodes the sent in piece to SHA-1 hash in order to compare it to the hash that is in the torrent file
     *
     * @param toEncode
     * @return sha1Encoded
     */
    private static byte[] encodeToSHA1(byte[] toEncode) {

        byte[] sha1Encoded = null;

        try {
            MessageDigest encoder = MessageDigest.getInstance("SHA-1");
            sha1Encoded = encoder.digest(toEncode);
        } catch (NoSuchAlgorithmException e) {
            System.err.println("SHA-1 failed to encode the piece.");
        }

        return sha1Encoded;

    }



	public static boolean checkAllPieces() {
		for(int i = 0; i < piecesDownloaded.size(); i++){
			if(!piecesDownloaded.get(i)){
				return false;
			}
		}
		
		return true;
	}
}
