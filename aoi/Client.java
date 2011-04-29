package aoi;

/*
  A class for connecting to the server and parsing the game state
  and sending the desired control parameters.
 */
// testing making a comment... (mike)
import java.io.*;
import java.net.*;
import stdlib.*;

public class Client {

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
	    StdDraw.setXscale(-Simulation.MAPSIZE/2, Simulation.MAPSIZE/2);
	    StdDraw.setYscale(-Simulation.MAPSIZE/2, Simulation.MAPSIZE/2);

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

	    Intelligence intel = new HigherBeing(p);
	    GameState game;

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

    private static GameState parseGameState(DataInputStream i) {
	return GameState.parse(i);
    }

    private static void sendControlParameters(DataOutputStream o, PlayerControlParameters params) {

	if (params == null) return;

	params.serialize(o);

    }

    private static void drawGameState(GameState g) {

	StdDraw.clear(StdDraw.BLACK);
	
	for (int j=0; j<2; ++j)
	    StdDraw.line(g.states[j].x,
			 g.states[j].y,
			 g.states[j].x + 1000 * Math.cos(Math.PI * (g.states[j].direction / (double) Simulation.INTMAX)),
			 g.states[j].y + 1000 * Math.sin(Math.PI * (g.states[j].direction / (double) Simulation.INTMAX)));

	for (int k = (g.bullets==null) ? -1 : g.bullets.length-1; k >= 0; --k)
	    StdDraw.square(g.bullets[k].x, g.bullets[k].y, 100);

    }
}
