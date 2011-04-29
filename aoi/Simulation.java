/*
  The main class ran by the server that carries on the
  step by step.
 */

package aoi;

import java.io.*;
import java.net.*;


public class Simulation
{

    public static final int INTMAX = 2147483647;
    public static final int INTMIN = -2147483648;

    // Maximum angular speed for turning the ship (abs. value).
    // Angle is measured in unit of 2*pi / (|INTMAX| + |INTMIN|).
    public static final int OMEGA = 33554432;
 
    public static final int MAPSIZE = 60000;

    // Maximum acceleration.
    // Positions (distances) can range between INTMIN and INTMAX.
    public static final int MAXACC = 10;
 
    // Drag coefficient.
    public static final double DRAG = 0.05;

    // The amount of time the simulation thread sleeps between each
    // step in milliseconds.
    public static final int SLEEPPERSTEP = 50;

    /****
     Public fields used to communicate with PlayerListener
     and GameStateSender threads.
     ****/

    // The parameters received from the clients that tell what
    // the players want to do.
    // Read by the Simulation thread.
    // Written by PlayerListener threads.
    public final PlayerControlParameters[] ctrlParams = new PlayerControlParameters[2];

    // The public game state that gets transmitted to the players.
    // Read/written by the Simulation thread.
    // Read by GameStateSender threads.
    public GameState game;


    // Private information we want to store about the players
    // but not communicate to the clients.
    private final PlayerStateInternal[] playerStatesInt = new PlayerStateInternal[2];

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

	Simulation simulation = new Simulation();

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
		GameStateSender[] senders = new GameStateSender[2];
		PlayerListener[] listeners = new PlayerListener[2];

		for (int j=0; j<2; ++j) {
		    senders[j] = new GameStateSender(j, simulation, new DataOutputStream(senderSockets[j].getOutputStream()));
		    listeners[j] = new PlayerListener(j, simulation, new DataInputStream(listenerSockets[j].getInputStream()));

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
	PlayerState[] states = new PlayerState[2];

	for (int j=0; j<2; ++j) {
	    ctrlParams[j] = new PlayerControlParameters(0, 0, 0, 0);

	    playerStatesInt[j] = new PlayerStateInternal();
	    states[j] = new PlayerState(j, -5000 + j*10000, 0, 0, 0); 
	}

	this.game = new GameState(0, states, null);
    }

    private void startGame() {

	PlayerState[] states = new PlayerState[2];
	Bullet[] bullets = null;
	Bullet[] tempBullets = null;

	PlayerControlParameters[] params = new PlayerControlParameters[2];
	PlayerControlParameters[] prevParams = new PlayerControlParameters[2];

	int shotsTaken[] = new int[2];
	int reloadTimeLeft[] = new int[2];

	int dir, x, y;
	boolean turnCcw;
	double a;

	for (int j=0; j<2; ++j) {
	    shotsTaken[j] = 0;
	    prevParams[j] = null;
	    reloadTimeLeft[j] = 0;
	}

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

	    // First copy the parameters to minimize locking time
	    // Note: don't need to clone (deep copy) or lock because all
	    //   fields of PlayerControlParameters are final.
	    for (int j=0; j<2; ++j)
		params[j] = this.ctrlParams[j];

	    for (int j=0; j<2; ++j) {

		// Take shots first:
		if (prevParams[j] != params[j]) shotsTaken[j] = 0;

		if (reloadTimeLeft[j] > 0) {

		    --reloadTimeLeft[j];

		} else if (shotsTaken[j] < params[j].shotsToTake) {

		    bullets = (bullets == null) ? new Bullet[1]
			                        : java.util.Arrays.copyOf(bullets,
						      bullets.length + 1);

		    bullets[bullets.length-1] = new Bullet(j,
							 game.states[j].x,
							 game.states[j].y,
							 game.states[j].direction);

		    reloadTimeLeft[j] = Bullet.RELOADTIME;

		}

		// Calculate the new positions for players.
		a = Math.max(0.0, MAXACC * (params[j].acceleration / (double) INTMAX));

		playerStatesInt[j].vx = playerStatesInt[j].vx + (int) (a * Math.cos(Math.PI * (game.states[j].direction / (double) INTMAX)));
		playerStatesInt[j].vy = playerStatesInt[j].vy + (int) (a * Math.sin(Math.PI * (game.states[j].direction / (double) INTMAX)));

		playerStatesInt[j].vx -= DRAG * playerStatesInt[j].vx;
		playerStatesInt[j].vy -= DRAG * playerStatesInt[j].vy;

		x = game.states[j].x + playerStatesInt[j].vx;
		y = game.states[j].y + playerStatesInt[j].vy;

		// Figure out whether to turn clockwise or counterclockwise.
		turnCcw =    Math.abs(game.states[j].direction + OMEGA - params[j].targetDirection)
		           < Math.abs(game.states[j].direction - OMEGA - params[j].targetDirection);
		dir = game.states[j].direction + ((turnCcw)? 1:-1)*OMEGA;

		// Don't go past the target direction.
		if (Integer.signum(game.states[j].direction - params[j].targetDirection)
		    != Integer.signum(dir - params[j].targetDirection))
		    dir = params[j].targetDirection;
		

		states[j] = new PlayerState(j, x, y, dir, 0);
	    }

	    // Calculate the new positions for the bullets and figure out
	    // if anyone died.
	    for (int k = (bullets==null) ? -1 : bullets.length-1; k >= 0; --k) {

		x = bullets[k].x + (int) (((double) Bullet.SPEED) * Math.cos(Math.PI * (bullets[k].direction / (double) INTMAX)));
		y = bullets[k].y + (int) (((double) Bullet.SPEED) * Math.sin(Math.PI * (bullets[k].direction / (double) INTMAX)));

		// TODO: This isn't an accurate way of doing this if
		//   the bullets/players move fast.
		for (int j=0; j<2; ++j) {
		    if (bullets[k].playerNo != j
			&& Math.pow(states[j].x - x, 2) + Math.pow(states[j].y - y, 2)
			   < Math.pow(Bullet.RADIUS, 2)) {
			System.out.println("Player " + j + " was hit!");
			assert(false);
		    }
		}

		if (Math.abs(x) > MAPSIZE/2 || Math.abs(y) > MAPSIZE/2) {

		    // Remove old bullets.
		    tempBullets = new Bullet[bullets.length - 1];

		    if (k>0)
			System.arraycopy(bullets, k-1, tempBullets, 0, k);

		    if (k<bullets.length-1)
			System.arraycopy(bullets, k+1, tempBullets,
					 k, bullets.length-1 - k);

		    bullets = tempBullets;
		    tempBullets = null;

		} else {

		    // Otherwise update the location.
		    bullets[k] = new Bullet(bullets[k].playerNo, x, y, bullets[k].direction);

		}
	    }

	    // Update the game state.
	    // Note: don't need locking since updating a reference is atomic.
	    this.game = new GameState(game.time + 1, states, bullets);
	}
    }


    private class PlayerStateInternal {
	/* Values used internally by the simulation. */
	public int vx;
	public int vy;
    }

}




