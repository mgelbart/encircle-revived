package aoi;

/*
  A class for connecting to the server and parsing the game state
  and sending the desired control parameters.
 */
// testing making a comment... (mike)
import java.io.*;
import java.net.*;

public class Client {

    public static void main(String argv[]) throws Exception {


	Socket controlSocket;
	Socket stateSocket;

	ClientConfiguration config = ClientConfiguration.parse(System.in);

	System.out.println(config);
	
	// Read input options from stdin.
	controlSocket = new Socket(config.serverurl, config.controlPort);
	stateSocket = new Socket(config.serverurl, config.gameStatePort);

	// Read the Arena from a local file for now...
	Arena arena = null;
	try {
	    arena = Arena.parse(new FileInputStream(Arena.ARENA_METADATA_FILENAME));
	} catch (Exception e) {
	    System.out.println(e.toString());
	    assert(false);
	}


	try {

	    DataInputStream stateStream = new DataInputStream(stateSocket.getInputStream());
	    DataOutputStream controlStream = new DataOutputStream(controlSocket.getOutputStream());

	    /* Run the intelligence in a separate thread so that parsing/serializing
	       doesn't block thinking. */
	    Intelligence intel;
	    if (config.nonhuman)
		intel = new Nonhuman(config, arena);
	    else
		intel = new HigherBeing(config, arena);

	    Thread intelThread = new Thread(intel);
	    intelThread.start();

	    GameState game;

	    System.out.println("Ready...");

	    while (true) {
		game = parseGameState(stateStream);
		intel.processGameStateUpdate(game);
		intelThread.interrupt(); // in case she was sleeping!

		sendControlParameters(controlStream, intel.getNewControlParameters());
	    }

	} catch (Exception e) {
	    controlSocket.close();
	    stateSocket.close();

	    throw e;
	}

	//controlSocket.close();
	//stateSocket.close();
    }

    private static GameState parseGameState(DataInputStream i) {
	return GameState.parse(i);
    }

    private static void sendControlParameters(DataOutputStream o, PlayerControlParameters params) {

	if (params == null) return;

	params.serialize(o);

    }
}
