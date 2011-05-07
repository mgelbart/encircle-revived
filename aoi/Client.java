package aoi;

/*
  A class for connecting to the server and parsing the game state
  and sending the desired control parameters.
 */
// testing making a comment... (mike)
import java.io.*;
import java.net.*;

public class Client {

    /* Temporary hack to tell HigherBeing whether to start the GUI. */
    public static boolean draw;

    public static void main(String argv[]) throws Exception {
	BufferedReader stdin = new BufferedReader( new InputStreamReader(System.in));

	Socket controlSocket;
	Socket stateSocket;

	int p = Integer.parseInt(stdin.readLine());
	draw = Boolean.parseBoolean(stdin.readLine());

	if (p > 1 || p < 0) {
	    System.out.println("Invalid player number.");
	    return;
	}

	if (p == 1) {
	    controlSocket = new Socket("localhost", 7601);
	    stateSocket = new Socket("localhost", 7602);
	} else {
	    controlSocket = new Socket("localhost", 7603);
	    stateSocket = new Socket("localhost", 7604);
	}

	try {

	    DataInputStream stateStream = new DataInputStream(stateSocket.getInputStream());
	    DataOutputStream controlStream = new DataOutputStream(controlSocket.getOutputStream());

	    /* Run the intelligence in a separate thread so that parsing/serializing
	       doesn't block thinking. */
	    Intelligence intel = new HigherBeing(p);
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
