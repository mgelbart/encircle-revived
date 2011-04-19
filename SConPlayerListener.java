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
    private SConSimulation sim;

    public SConPlayerListener(int player, SConSimulation sim, DataInputStream in) {
	this.playerNo = player;
	this.client = in;
	this.sim = sim;

	assert(playerNo < 2);
	assert(playerNo >= 0);
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
	    // Note: don't need locking since updating a reference is atomic.
	    sim.ctrlParams[playerNo] = ctrl;

	}

    }

}
