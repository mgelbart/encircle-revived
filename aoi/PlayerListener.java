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

    private PlayerControlParameters ctrl = null;

    public PlayerListener(int player, Simulation sim, InputStream in) {
	this.playerNo = player;
	this.client = new DataInputStream(in);
	this.sim = sim;

	assert(playerNo < Simulation.NUMPLAYERS);
	assert(playerNo >= 0);
    }

    public synchronized void setInput(InputStream in) {
	this.client = new DataInputStream(in);
    }

    // Returns the control parameters for the simulation step time...time+1.
    public PlayerControlParameters getCtrlParams(int time) {
	PlayerControlParameters c = ctrl; // needed for atomicity!
	return ((c != null) ? c : new PlayerControlParameters(0, 0, 0, 0));
    }

    public void run() {

	Byte cmd = 0;

	while (true) {

	    // Note: don't need locking since updating a reference is atomic.
	    ctrl = PlayerControlParameters.parse(client);
	    if (ctrl == null) continue;

	    System.out.print("Received from #");
	    System.out.println(playerNo);
	    System.out.println(ctrl.toString());

	}

    }

}
