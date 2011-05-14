package aoi;

/*
  A class used for storing client options, like
  server address, enabled modules, etc.
 */

import java.io.*;

public class ClientConfiguration {

    // The server address, without port information.
    public final String serverurl;

    // The server port for receiving the game state.
    public final int gameStatePort;

    // The server port for sending commands to control the player.
    public final int controlPort;

    // Player number in the game.
    public final int playerNo;

    // True if the player is controlled entirely by the computer.
    public final boolean nonhuman;


    public ClientConfiguration(String serverurl, int gameStatePort,
			       int controlPort, int playerNo, boolean nonhuman) {
	this.serverurl = serverurl;
	this.gameStatePort = gameStatePort;
	this.controlPort = controlPort;
	this.playerNo = playerNo;
	this.nonhuman = nonhuman;

	assert(playerNo >= 0);
	assert(playerNo < 2);
	assert(controlPort > 1023);
	assert(controlPort < 65536);
	assert(gameStatePort > 1023);
	assert(gameStatePort < 65536);
    }


    public String toString() {
	return "server:\n" + serverurl
	    + "\nstate port:\n" + gameStatePort
	    + "\ncontrol port:\n" + controlPort
	    + "\nplayer no:\n" + playerNo
	    + "\nnon-human:\n" + nonhuman;
    }


    public static ClientConfiguration parse(InputStream in) {
	BufferedReader reader = new BufferedReader(new InputStreamReader(in));

	String serverurl;
	int statePort, controlPort, playerNo;
	boolean nonhuman;

	try {

	    reader.readLine();
	    serverurl = reader.readLine();

	    reader.readLine();
	    statePort = Integer.parseInt(reader.readLine());

	    reader.readLine();
	    controlPort = Integer.parseInt(reader.readLine());

	    reader.readLine();
	    playerNo = Integer.parseInt(reader.readLine());

	    reader.readLine();
	    nonhuman = Boolean.parseBoolean(reader.readLine());

	} catch (Exception e) {
	    System.err.println(e.toString());
	    assert(false);
	    return null;
	}

	return new ClientConfiguration(serverurl, statePort, controlPort,
				       playerNo, nonhuman);
    }

}