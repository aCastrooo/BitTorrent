/**
 * ****************************************************
 * ****************************************************
 *
 * RUBT - Rutgers BitTorrent Client
 * Phase 1
 * CS352 - Internet Technology
 *
 * @authors Anthony Castronuovo, Jake Taubner
 *
 * ****************************************************
 * ****************************************************
 */


package main;

import java.io.File;
import java.io.FileInputStream;

import GivenTools.*;

public class TorrentParse {

    /**
     * Parses the torrent file
     * @param torrent
     * @return theInfo
     * @throws Exception
     */
    public static TorrentInfo parse(File torrent) throws Exception {

        System.out.print("Attempting to parse the torrent file...\n");

        try {
            FileInputStream in = new FileInputStream(torrent);

            byte[] torrentBytes = new byte[(int)torrent.length()];
            in.read(torrentBytes);
            TorrentInfo theInfo = new TorrentInfo(torrentBytes);

            System.out.print("----------------------------------------\n");

            in.close();

            return theInfo;

        } catch (Exception e) {
            System.out.print("An error has occurred while attempting to parse this torrent file.");
            return null;
        }
    }
}
