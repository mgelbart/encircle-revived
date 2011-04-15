/*
  A class for connecting to the server and parsing the game state
  and sending the desired control parameters.
 */

import java.io.*;
import java.net.*;

class SConClient {

    public static void main(String argv[]) throws Exception
    {
	BufferedReader stdin = new BufferedReader( new InputStreamReader(System.in));

	Socket controlSocket;
	Socket stateSocket;

	int p = Integer.parseInt(stdin.readLine());

	if (p > 2 || p < 1) return;

	if (p == 1) {
	    controlSocket = new Socket("localhost", 6701);
	    stateSocket = new Socket("localhost", 6702);
	} else {
	    controlSocket = new Socket("localhost", 6703);
	    stateSocket = new Socket("localhost", 6704);
	}

	try {

	    DataInputStream stateStream = new DataInputStream(stateSocket.getInputStream());
	    DataOutputStream controlStream = new DataOutputStream(controlSocket.getOutputStream());

	    SConIntelligence intel = new HigherBeing(p);
	    
	    while (true) {
		intel.processGameStateUpdate(parseGameState(stateStream));

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

    private static SConParsedGameState parseGameState(DataInputStream i) {

	Byte cmd;
	
	SConParsedGameState game = new SConParsedGameState();
	SConPlayerState[] states = new SConPlayerState[2];

	try {

	    cmd = i.readByte();
	    assert(cmd == 't');
	    game.time = i.readInt();

	    for (int j = 0; j<2; ++j) {
		states[j] = SConPlayerState.parse(i);
	    }

	} catch (IOException ioe) {
	    assert(false);
	}

	game.states = states;

	return game;
    }

    private static void sendControlParameters(DataOutputStream o, SConPlayerControlParameters params) {

	if (params == null) return;

	params.serialize(o);

    }
}