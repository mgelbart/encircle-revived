package aoi;

/*
  A class dedicated to listening and parsing commands from
  a remote player.

  Meant to be run in a separate thread. Once the data for a new
  PlayerControlParameters object is parsed it is passed on
  to the simulation via the shared variable game.player1/2CtrlParams.

 */

import java.io.*;


public class PlayerListener implements Runnable {

    private int playerNo;
    private DataInputStream client;
    private Simulation sim;

    public PlayerListener(int player, Simulation sim, DataInputStream in) {
	this.playerNo = player;
	this.client = in;
	this.sim = sim;

	assert(playerNo < Simulation.NUMPLAYERS);
	assert(playerNo >= 0);
    }

    public void run() {

	Byte cmd = 0;
	PlayerControlParameters ctrl;

	while (true) {

	    ctrl = PlayerControlParameters.parse(client);
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
