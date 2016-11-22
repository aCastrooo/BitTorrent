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

import java.io.DataInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.Random;

import GivenTools.TorrentInfo;

public class Tracker {

    // Start with port number 6881
    private static int portNum = 6881;

    /**
     * HTTP GET Request
     *
     * @param infoFile metadata for the torrent we are downloading.
     * @param peerID the peer ID that we created for our peer.
     * @return response a byte array of all the peers who responded to our GET request.
     */
    public static byte[] getRequest(TorrentInfo infoFile, byte[] peerID) throws Exception {

        /* If port 6881 fails, keep trying new port numbers Once all ports from 6881 to 6889 have
         * been tried, the tracker connection will fail */
        while (portNum < 6890) {
            System.out.println("\nConnecting to the tracker...");

            // Add the info_hash to the URL
            String query = infoFile.announce_url + "?info_hash=";
            query = query + hexToURL(byteToHex(infoFile.info_hash.array()));

            // Add the peer id to the URL
            query = query + "&peer_id=" + new String(peerID);
            // query = query + "&peer_id=" + hexToURL(byteToHex(peerID));

            // Add the port number to the URL
            query = query + "&port=" + portNum;

            // Add the length of the file to the URL
            query = query + "&left=" + infoFile.file_length;

            // Add initial upload and download which is 0
            query = query + "&uploaded=" + 0;
            query = query + "&downloaded=" + 0;

            System.out.println("Attempting on port: " + portNum);

            try {
                // Creates the URL from the query
                URL get = new URL(query);

                System.out.println("\nConnecting with URL: \n" + get.toString() + "\n");

                // Establishes the URL connection
                HttpURLConnection connection = (HttpURLConnection) get.openConnection();

                connection.setRequestMethod("GET");

                // Gets the data from the tracker
                DataInputStream i = new DataInputStream(connection.getInputStream());
                int len = (int) connection.getContentLengthLong();
                byte[] response = new byte[len];
                i.readFully(response);
                i.close();

                return response;

            } catch (MalformedURLException e) {
                System.err.println("There was a Malformed Exception");
                portNum++;
                continue;
            } catch (IOException e) {
                System.err.println("There was an IO Exception");
                portNum++;
                continue;
            }
        }

        System.out.println("All legal ports have been attempted.\nCould not connect to the tracker.");

        return null;
    }

    /**
     * Connects to the tracker and tells it the download has started
     *
     * @param torrent
     * @param pID
     * @param event
     */
    public static void updateTracker(TorrentInfo torrent, byte[] pID, String event, int left) {

        int i = 6881;
        while (i < 6890) {

            String query = torrent.announce_url + "?info_hash=";
            query = query + Tracker.hexToURL(Tracker.byteToHex(torrent.info_hash.array()));

            // Add the peer id to the URL
            query = query + "&peer_id=" + new String();
            // query = query + "&peer_id=" + hexToURL(byteToHex(peerID));

            // Add the port number to the URL
            query = query + "&port=" + i;

            // Add the length of the file to the URL
            if(left != -1){
            	query = query + "&left=" + torrent.file_length;
            }
            else{
            	query = query + "&left=" + left;
            }

            // Add initial upload and download which is 0
            query = query + "&uploaded=" + 0;
            query = query + "&downloaded=" + 0;

            // Add event started to the URL
            if(!event.equals("")){
                query = query + "&event=" + event;
            }

            try {

                URL con = new URL(query);
                HttpURLConnection c = (HttpURLConnection) con.openConnection();
                c.connect();
                return;

            } catch (MalformedURLException e) {
                System.err.println("There was a Malformed Exception");
                i++;
                continue;
            } catch (IOException e) {
                System.err.println("There was an IO Exception");
                i++;
                continue;
            }
        }
    }

    /**
     * Generates a random peer id for use in the URL
     *
     * @return byte[]
     */
    public static byte[] generatePeerID() {
        Random blando = new Random();

        byte[] id = new byte[20];

        // Go through the list to randomly generate the peer id
        for (int i = 0; i < 6; i++) {
            // Generates some numbers
            id[i] = (byte) (blando.nextInt((57 - 48) + 1) + 48);
        }
        for (int i = 6; i < 20; i++) {
            // Generates some letters
            id[i] = (byte) (blando.nextInt((122 - 97) + 1) + 97);
        }

        return id;

    }

    /**
     * Takes string of hex and converts it to the required format for the URL
     *
     * @param hexString
     * @return encodedString
     */
    public static String hexToURL(String hex) {

        int length = hex.length();
        char[] encodedString = new char[length + length / 2];

        for (int i = 0, j = 0; j < length; i++, j++) {
            encodedString[i] = '%';
            i++;
            encodedString[i] = hex.charAt(j);
            i++;
            j++;
            encodedString[i] = hex.charAt(j);
        }

        return new String(encodedString);

    }

    /**
     * Converts byte array to hex
     *
     * @param bytes
     * @return hex as String
     */
    public static String byteToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length);

        for (byte i : bytes) {
            hex.append(String.format("%02X", i));
        }

        return hex.toString();
    }

    /**
     * Converts an integer to a byte to be stored in a byte array
     *
     * @param number
     * @return bye
     */
    @SuppressWarnings("unused")
    private static byte[] intToByte(int number) {
        ByteBuffer bye = ByteBuffer.allocate(4);
        bye.putInt(number);

        return bye.array();
    }

}
