/*
  The main class ran by the server that carries on the
  step by step.
 */

package aoi;

import java.io.*;
import java.net.*;
import java.util.Vector;
import java.awt.Polygon;
import java.awt.Rectangle;

public class Simulation
{

    public static final int INTMAX = 2147483647;
    public static final int INTMIN = -2147483648;

    // Maximum angular speed for turning the ship (abs. value).
    // Angle is measured in unit of 2*pi / (|INTMAX| + |INTMIN|).
    public static final int OMEGA = INTMAX / 50;
 
    public static final int MAPSIZE = 60000;

    // Maximum acceleration.
    public static final int MAXACC = MAPSIZE * 15 / 100000;
 
    // Drag coefficient.
    public static final double DRAG = 0.01;

    // The amount of time the simulation thread sleeps between each
    // step in milliseconds.
    public static final int SLEEPPERSTEP = 40;

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

	    states[j] = initPlayerState(j);
	}

	this.game = new GameState(0, states, new Vector<Bullet>());
    }

    // (Re)generate a player state at a random location.
    private static PlayerState initPlayerState(int playerNo) {
	assert(playerNo >= 0);
	assert(playerNo < 2);

	return new PlayerState(playerNo,
			       (int) (MAPSIZE * (0.5 -   Math.random())),
			       (int) (MAPSIZE * (0.5 -   Math.random())),
			       (int) (INTMAX  * (1   - 2*Math.random())),
			       0); 
    }

    @SuppressWarnings("unchecked")
    private void startGame() {

	PlayerState[] states = new PlayerState[2];
	Vector<Bullet> bullets = new Vector<Bullet>();

	PlayerControlParameters[] params = new PlayerControlParameters[2];
	PlayerControlParameters[] prevParams = new PlayerControlParameters[2];

	int shotsTaken[] = new int[2];
	int reloadTimeLeft[] = new int[2];

	// Some temp variables used during calculation
	int dir, x, y;
	boolean turnCcw;
	double a;

	Bullet b;

	int[][] xy;
	Polygon p;

	Rectangle map = new Rectangle(-MAPSIZE/2, -MAPSIZE/2, MAPSIZE, MAPSIZE);

	for (int j=0; j<2; ++j) {
	    shotsTaken[j] = 0;
	    prevParams[j] = null;
	    reloadTimeLeft[j] = 0;
	}

	System.out.println("Game starting.");

	while (true) {

	    if (game.time % 100 == 0)
		System.out.println("Current local time: " + game.time);

	    // Need to clone the bullet vector because the other threads may
	    // still need the old bullet vector (part of the old game state).
	    
	    bullets = (Vector<Bullet>) bullets.clone();

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

		    bullets.add(new Bullet(j,
					   game.states[j].x,
					   game.states[j].y,
					   game.states[j].direction));

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
	    for (int k = bullets.size() - 1; k >= 0; --k) {
		b = bullets.get(k);

		x = b.x + (int) (((double) Bullet.SPEED) * Math.cos(Math.PI * (b.direction / (double) INTMAX)));
		y = b.y + (int) (((double) Bullet.SPEED) * Math.sin(Math.PI * (b.direction / (double) INTMAX)));

		// TODO: This isn't an accurate way of doing this if
		//   the bullets/players move fast.
		for (int j=0; j<2; ++j) {
		    xy = states[j].shape();
		    p = new Polygon(xy[0], xy[1], 4);

		    if (b.playerNo != j && p.contains(b.x, b.y))
			states[j] = initPlayerState(j);
		}

		// Update the bullet position unless it's out of bounds.
		if (Math.abs(x) < MAPSIZE/2 && Math.abs(y) < MAPSIZE/2)
		    bullets.set(k, new Bullet(b.playerNo, x, y, b.direction));
		else
		    bullets.remove(k);

	    }

	    // Finally check if anyone went out of bounds:
	    for (int j=0; j<2; ++j) {
		if (!map.contains(states[j].x, states[j].y))
		    states[j] = initPlayerState(j);
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




