/*
  A class dedicated to listening and parsing commands from
  a remote player.

  Meant to be run in a separate thread. Once the data for a new
  SConPlayerControlParameters object is parsed it is passed on
  to the simulation via the shared variable game.player1/2CtrlParams.

 */

import java.io.*;


class SConPlayerListener implements Runnable {

    private int playerNo;
    private DataInputStream client;
    private SConSimulation game;

    public SConPlayerListener(int player, SConSimulation sim, DataInputStream in) {
	playerNo = player;
	client = in;
	game = sim;
    }

    public void run() {

	Byte cmd = 0;
	SConPlayerControlParameters ctrl;

	while (true) {

	    ctrl = SConPlayerControlParameters.parse(client);
	    if (ctrl == null) continue;

	    System.out.print("Received from #");
	    System.out.println(playerNo);
	    System.out.println(ctrl.toString());

	    // Give the updated target parameters to the simulator
	    if(playerNo == 1)
		synchronized (game.player1CtrlParams) {
		    game.player1CtrlParams = ctrl;
		}
	    else if(playerNo == 2)
		synchronized (game.player2CtrlParams) {
		    game.player2CtrlParams = ctrl;
		}
	    else {
		assert(false);
		System.out.println("Fail!");
	    }

	}

    }

}
