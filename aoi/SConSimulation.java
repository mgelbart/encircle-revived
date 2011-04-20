/*
  The main class ran by the server that carries on the
  step by step.
 */

package aoi;

import java.io.*;
import java.net.*;


public class SConSimulation
{

    public static final int INTMAX = 2147483647;
    public static final int INTMIN = -2147483648;

    // Maximum angular speed for turning the ship (abs. value).
    // Angle is measured in unit of 2*pi / (|INTMAX| + |INTMIN|).
    public static final int OMEGA = 536870662;

    // Maximum acceleration.
    // Positions can range between INTMIN and INTMAX.
    public static final int MAXACC = 10;

    // The amount of time the simulation thread sleeps between each
    // step in milliseconds.
    public static final int SLEEPPERSTEP = 100;

    /****
     Public fields used to communicate with PlayerListener
     and GameStateSender threads.
     ****/

    // The parameters received from the clients that tell what
    // the players want to do.
    // Read by the SConSimulation thread.
    // Written by SConPlayerListener threads.
    public final SConPlayerControlParameters[] ctrlParams = new SConPlayerControlParameters[2];

    // The public game state that gets transmitted to the players.
    // Read/written by the SConSimulation thread.
    // Read by SConGameStateSender threads.
    public SConGameState game;


    // Private information we want to store about the players
    // but not communicate to the clients.
    private final SConPlayerStateInternal[] playerStatesInt = new SConPlayerStateInternal[2];

    // References to sender/listener threads so we can interrupt them.
    private final Thread[] senderThreads = new Thread[2];
    private final Thread[] listenerThreads = new Thread[2];


    public static void main(String argv[]) throws Exception {

	ServerSocket[] listenerServSockets = new ServerSocket[2];
	ServerSocket[] senderServSockets = new ServerSocket[2];

	listenerServSockets[0] = new ServerSocket(7601);
	listenerServSockets[1] = new ServerSocket(7603);
	senderServSockets[0] =  new ServerSocket(7602);
	senderServSockets[1] =  new ServerSocket(7604);

	SConSimulation simulation = new SConSimulation();

	while(true)
	    {

		Socket[] listenerSockets = new Socket[2];
		Socket[] senderSockets = new Socket[2];

		// Wait for clients to connect to all four ports.
		// Would probably be better to have one thread per client here too...
		for (int j=0; j<2; ++j) {
		    listenerSockets[j] = listenerServSockets[j].accept();
		    senderSockets[j] = senderServSockets[j].accept();
		}


		simulation.initGameState();


		// Create the threads for communication with the clients
		SConGameStateSender[] senders = new SConGameStateSender[2];
		SConPlayerListener[] listeners = new SConPlayerListener[2];

		for (int j=0; j<2; ++j) {
		    senders[j] = new SConGameStateSender(j, simulation, new DataOutputStream(senderSockets[j].getOutputStream()));
		    listeners[j] = new SConPlayerListener(j, simulation, new DataInputStream(listenerSockets[j].getInputStream()));

		    // We need a reference to the sender threads to interrupt
		    // them when the game sate is updated.
		    simulation.senderThreads[j] = new Thread(senders[j]);
		    simulation.listenerThreads[j] = new Thread(listeners[j]);
		}

		// Start the threads
		for (int j=0; j<2; ++j) {
		    simulation.senderThreads[j].start();
		    simulation.listenerThreads[j].start();
		}

		// Start simulating the game!
		simulation.startGame();
	    }
    }

    private void initGameState() {
	SConPlayerState[] states = new SConPlayerState[2];

	for (int j=0; j<2; ++j) {
	    ctrlParams[j] = new SConPlayerControlParameters(0, 0, 0, 0, 0, 0, 0);

	    playerStatesInt[j] = new SConPlayerStateInternal();
	    states[j] = new SConPlayerState(j, -50 + j*100, 0, 0, 0); 
	}

	this.game = new SConGameState(0, states);
    }

    private void startGame() {

	SConPlayerState[] states = new SConPlayerState[2];
	SConPlayerControlParameters[] params = new SConPlayerControlParameters[2];

	int dir, x, y;
	boolean turnCcw;
	double a;

	while (true) {

	    // Interrupt the sender threads to let them
	    // know the state has changed.
	    for (int j=0; j<2; ++j)
		senderThreads[j].interrupt();


	    // Sleep for some time between each simulated step.
	    try {
		Thread.sleep(SLEEPPERSTEP);
	    } catch (InterruptedException e) {
		return;
	    }

	    // Calculate the new positions of players.

	    // First copy the parameters to minimize locking time
	    // Note: don't need to clone (deep copy) or lock because all
	    //   fields of SConPlayerControlParameters are final.
	    for (int j=0; j<2; ++j)
		params[j] = this.ctrlParams[j];


	    for (int j=0; j<2; ++j) {

		// Calculate the new positions.

		a = Math.max((double) 0.0, MAXACC * (params[j].acceleration / (double) INTMAX));

		playerStatesInt[j].vx = playerStatesInt[j].vx + (int) (((double) a) * Math.cos(Math.PI * (game.states[j].direction / (double) INTMAX)));
		playerStatesInt[j].vy = playerStatesInt[j].vy + (int) (((double) a) * Math.sin(Math.PI * (game.states[j].direction / (double) INTMAX)));

		x = game.states[j].x + playerStatesInt[j].vx;
		y = game.states[j].y + playerStatesInt[j].vy;

		// Figure out whether to turn clockwise or counterclockwise.
		turnCcw =    Math.abs(game.states[j].direction + OMEGA - params[j].targetDirection)
		           < Math.abs(game.states[j].direction - OMEGA - params[j].targetDirection);
		dir = game.states[j].direction + ((turnCcw)? 1:-1)*OMEGA;

		states[j] = new SConPlayerState(j, x, y, dir, 0);
	    }


	    // Update the game state.
	    // Note: don't need locking since updating a reference is atomic.
	    this.game = new SConGameState(game.time + 1, states);
	}
    }


    private class SConPlayerStateInternal {
	/* Values used internally by the simulation. */
	public int vx;
	public int vy;
    }

}




