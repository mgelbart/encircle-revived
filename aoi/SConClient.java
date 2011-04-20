package aoi;

/*
  A class for connecting to the server and parsing the game state
  and sending the desired control parameters.
 */
// testing making a comment... (mike)
import java.io.*;
import java.net.*;
import stdlib.*;

public class SConClient {

    public static void main(String argv[]) throws Exception
    {
	BufferedReader stdin = new BufferedReader( new InputStreamReader(System.in));

	Socket controlSocket;
	Socket stateSocket;

	int p = Integer.parseInt(stdin.readLine());
	boolean draw = Boolean.parseBoolean(stdin.readLine());

	if (p > 1 || p < 0) {
	    System.out.println("Invalid player number.");
	    return;
	}

	if (draw) {
	    StdDraw.setXscale(-300, 300);
	    StdDraw.setYscale(-300, 300);

	    StdDraw.clear(StdDraw.BLACK);

	    StdDraw.setPenColor(StdDraw.BLUE);
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

	    SConIntelligence intel = new HigherBeing(p);
	    SConGameState game;

	    while (true) {
		game = parseGameState(stateStream);
		intel.processGameStateUpdate(game);

		sendControlParameters(controlStream, intel.getNewControlParameters());

		if (draw) drawGameState(game);
	    }

	} catch (Exception e) {
	    controlSocket.close();
	    stateSocket.close();

	    throw e;
	}

	//controlSocket.close();
	//stateSocket.close();
    }

    private static SConGameState parseGameState(DataInputStream i) {
	return SConGameState.parse(i);
    }

    private static void sendControlParameters(DataOutputStream o, SConPlayerControlParameters params) {

	if (params == null) return;

	params.serialize(o);

    }

    private static void drawGameState(SConGameState g) {
	StdDraw.clear(StdDraw.BLACK);
	
	StdDraw.square(g.states[0].x, g.states[0].y, 2);
	StdDraw.square(g.states[1].x, g.states[1].y, 2);
    }
}