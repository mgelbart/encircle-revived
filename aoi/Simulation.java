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
import java.awt.Point;
import java.awt.geom.Point2D;
import java.awt.geom.Path2D;
import java.awt.geom.Line2D;
import java.awt.geom.Area;
import java.util.Timer;
import java.util.TimerTask;

public class Simulation
{

    public static final int INTMAX = 2147483647;
    public static final int INTMIN = -2147483648;

    // Advance the simulation every TIMESTEP milliseconds.
    // This will not be respected if the computation takes longer
    // than TIMESTEP.
    //
    // All other times are defined in terms of TIMESTEP. Hence
    // decreasing this only increases resolution.
    public static final int TIMESTEP = 20;
 
    // All distances are defined in terms of MAPSIZE.
    // Hence increasing this only increases the resolution.
    public static final int MAPSIZE = 100000;


    // Maximum angular turning rate (i.e. domega/dt).
    // Angle is measured in units of 2*pi / (|INTMAX| + |INTMIN|).
    public static final int OMEGA = (INTMAX/1600) * TIMESTEP;

    // Maximum thrust, i.e. max dx/dt^2.
    public static final int MAXACC = (int) ((MAPSIZE / 1e7 * 3) * TIMESTEP * TIMESTEP) ;
 
    // Drag coefficient.
    public static final double DRAG = 0.0013 * TIMESTEP;

    // Maximum energy.
    public static final int MAXENERGY = 10;

    // Energy recharge period (i.e. how many turns to gain one point).
    public static final int RECHARGEPERIOD = 1000 / TIMESTEP;

    // Energy at the start of the game/after being shot.
    public static final int INITIALENERGY = MAXENERGY;

    // Energy cost per bullet.
    public static final int BULLETCOST = 2;

    // Roughly the minimum distance between adjacent points in the trajectory for
    // encircling clusters. See the algorithm to see why this is only 'roughly' so.
    public static final int MINTRAJECTORYSEGMENT = MAPSIZE / 200;

    // The number of players in the game. Currently not adjustable.
    public static final int NUMPLAYERS = 2;

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

    // Static information about the game state.
    private Arena arena;

    // This information is redundant because it's already in arena but it's more
    // convenient to check whether something hit a cluster with these rectangles...
    private Vector<ClusterServerSide> clusters = new Vector<ClusterServerSide>();

    // References to sender/listener threads so we can interrupt them.
    private final Thread[] senderThreads = new Thread[NUMPLAYERS];
    private final Thread[] listenerThreads = new Thread[NUMPLAYERS];


    public static void main(String argv[]) throws Exception {

	ServerSocket[] listenerServSockets = new ServerSocket[NUMPLAYERS];
	ServerSocket[] senderServSockets = new ServerSocket[NUMPLAYERS];

	// I guess these Sockets will not be hardcoded in the future...?
	int[] listenerServSocketNumbers = {7601, 7603};
	int[] senderServSocketNumbers = {7602, 7604};
	
	for (int j = 0; j < NUMPLAYERS; ++j) {
	    listenerServSockets[j] = new ServerSocket(listenerServSocketNumbers[j]);
	    senderServSockets[j] =  new ServerSocket(senderServSocketNumbers[j]);
	}

	Simulation simulation = new Simulation();

	// For now, two of each nSegments from 1 to 7
	simulation.clusters = ClusterServerSide.generateArena(new int[]{2, 2, 2, 2, 2, 2, 2});

	simulation.arena = new Arena(simulation.clusters.toArray(new ClusterServerSide[0]));

	try {
	    FileWriter writer = new FileWriter(Arena.ARENA_METADATA_FILENAME);
	    BufferedWriter buf = new BufferedWriter(writer);
	    buf.write(simulation.arena.toString());
	    buf.close();
	} catch (Exception e) {
	    System.out.println(e.toString());
	    assert(false);
	}

	System.out.println("Arena generated.");


	while(true)
	    {

		Socket[] listenerSockets = new Socket[2];
		Socket[] senderSockets = new Socket[2];

		// Wait for clients to connect to all four ports.
		// Would probably be better to have one thread per client here too...

		System.out.println("Waiting for clients to connect...");
		for (int j=0; j<2; ++j) {
		    listenerSockets[j] = listenerServSockets[j].accept();
		    senderSockets[j] = senderServSockets[j].accept();
		    System.out.println("Player " + j + " connected.");
		}


		simulation.initGameState();


		// Create the threads for communication with the clients
		GameStateSender[] senders = new GameStateSender[NUMPLAYERS];
		PlayerListener[] listeners = new PlayerListener[NUMPLAYERS];

		for (int j=0; j<NUMPLAYERS; ++j) {
		    senders[j] = new GameStateSender(j, simulation, new DataOutputStream(senderSockets[j].getOutputStream()));
		    listeners[j] = new PlayerListener(j, simulation, new DataInputStream(listenerSockets[j].getInputStream()));

		    // We need a reference to the sender threads to interrupt
		    // them when the game sate is updated.
		    simulation.senderThreads[j] = new Thread(senders[j]);
		    simulation.listenerThreads[j] = new Thread(listeners[j]);
		}

		// Start the threads
		for (int j=0; j<NUMPLAYERS; ++j) {
		    simulation.senderThreads[j].start();
		    simulation.listenerThreads[j].start();
		}

		// Start simulating the game!
		simulation.startGame();
	    }
    }

    private void initGameState() {
	PlayerState[] states = new PlayerState[NUMPLAYERS];

	for (int j=0; j<NUMPLAYERS; ++j)
	    states[j] = initPlayerState(j);

	this.game = new GameState(0, states, new Vector<Bullet>());
    }

    // initPlayerState with a score of zero
    private PlayerState initPlayerState(int playerNo) {
	return initPlayerState(playerNo, 0);
    }

    // (Re)generate a player state at a random location.
    private PlayerState initPlayerState(int playerNo, int score) {
	assert(playerNo >= 0);
	assert(playerNo < NUMPLAYERS);
	if (score < 0) score = 0;

	int x = 0;
	int y = 0;
	boolean freespace = false;

	while (!freespace) {

	    x = (int) (MAPSIZE * (0.5 - Math.random()));
	    y = (int) (MAPSIZE * (0.5 - Math.random()));

	    freespace = true;

	    for (Cluster c : clusters) {
		if (c.boundingBox().contains(x, y))
		    freespace = false;
	    } 
	    
	}

	int dir = (int) (INTMAX  * (1   - 2*Math.random()));

	ctrlParams[playerNo] = new PlayerControlParameters(0, 0, 0, 0);
	playerStatesInt[playerNo] = new PlayerStateInternal(x, y);

	return new PlayerState(playerNo, x, y, 0, 0, dir, INITIALENERGY,
			       (game == null) ? 0 : game.time, score); 
    }

    @SuppressWarnings("unchecked")
    private void startGame() {

	PlayerState[] states = new PlayerState[NUMPLAYERS];
	Vector<Bullet> bullets = new Vector<Bullet>();

	// Some temp variables used during calculation
	int[] nextPos;
	int dir, x, y, vx, vy, energy, score, currentLoop;

	Bullet b;
	boolean remove;

	int[][] xy;
	Polygon p;
	boolean crashed;

	Point2D curPoint, prevPoint, prevPrevPoint;
	Path2D loop;

	Rectangle mapedges = new Rectangle(-MAPSIZE/2, -MAPSIZE/2,
					   MAPSIZE, MAPSIZE);


	// Set up a timer that wakes up the simulation thread every TIMESTEP
	// milliseconds. Note that, if the computation takes even slightly longer
	// than TIMESTEP, the time step will be doubled.
	Thread[] threads = new Thread[1+NUMPLAYERS];
	threads[0] = Thread.currentThread();
	for (int j=0; j<NUMPLAYERS; ++j)
	    threads[1+j] = senderThreads[j];


	TimerTask interruptTask = new InterruptThreadsTask(threads);
	Timer timer = new Timer();
	timer.scheduleAtFixedRate(interruptTask, 0, TIMESTEP);


	System.out.println("Game starting.");

	while (true) {

	    // Sleep until next time step.
	    try {
		Thread.sleep(10000);
	    } catch (InterruptedException e) {

		if (game.time % 100 == 0)
		    System.out.println("Current local time: " + game.time);


		// Need to clone the bullet vector because the other threads may
		// still need the old bullet vector (part of the old game state).    
		bullets = (Vector<Bullet>) bullets.clone();


		// First copy the parameters to minimize the time difference
		// different players have to update their parameters.
		// Note: don't need to clone (deep copy) or lock because all
		//   fields of PlayerControlParameters are final.
		for (int j=0; j<NUMPLAYERS; ++j)
		    playerStatesInt[j].params = this.ctrlParams[j];

		for (int j=0; j<NUMPLAYERS; ++j) {

		    // Add one point of energy every RECHARGEPERIOD steps.
		    energy = Math.min(MAXENERGY, game.states[j].energy
				      + ((game.time % RECHARGEPERIOD == 0) ? 1:0));

		    // Take shots first:
		    if (playerStatesInt[j].prevParams != playerStatesInt[j].params)
			playerStatesInt[j].shotsTaken = 0;

		    if (playerStatesInt[j].reloadTimeLeft > 0) {

			playerStatesInt[j].reloadTimeLeft--;

		    } else if (playerStatesInt[j].shotsTaken < playerStatesInt[j].params.shotsToTake
			       && energy >= BULLETCOST) {

			bullets.add(new Bullet(j,
					       game.states[j].x,
					       game.states[j].y,
					       game.states[j].direction));

			playerStatesInt[j].reloadTimeLeft = Bullet.RELOADTIME;
			
			energy -= BULLETCOST;

		    }


		    // Calculate where the player moved.
		    nextPos = SimulationUtils.nextPosition(game.states[j], playerStatesInt[j].params);

		    x = nextPos[0];
		    y = nextPos[1];
		    vx = nextPos[2];
		    vy = nextPos[3];
		    dir = nextPos[4];


		    // Add the new position to the player's trajectory
		    curPoint = new Point(x, y);
		    prevPoint = playerStatesInt[j].trajectory.lastElement().getP2();
		    prevPrevPoint = playerStatesInt[j].trajectory.lastElement().getP1();

		    if (prevPoint.distance(curPoint) > 0.9) {

			// Always add the latest position to the trajectory
			// but combine it with the previous segment if both are short.
			if (curPoint.distance(prevPrevPoint) < MINTRAJECTORYSEGMENT) {
			    playerStatesInt[j].trajectory.remove(playerStatesInt[j].trajectory.size()-1);
			    playerStatesInt[j].trajectory.add(new Line2D.Double(prevPrevPoint, curPoint));
			} else {
			    playerStatesInt[j].trajectory.add(new Line2D.Double(prevPoint, curPoint));
			}
			    
		    }
		
	    
	    	    // Check if the paths cross eachother, O(N).
	            // Remove all paths that are crossed.
	            // Also checks if players scored.
		    score = game.states[j].score;
		    currentLoop = game.states[j].currentLoop;
		    
		    // Check if any player (including j) is crossing j's path
	    	    for (int i=0; i<NUMPLAYERS; ++i) {
	    	     //   if (i != j) continue;   // uncomment this to disable path-killing
		        loop = checkForIntersection(i, j);
		        
		        if (loop != null) {
		            if (checkForScoring(j, loop)) score++;
		        
		            System.out.println("!!!!");
		        
		    	    playerStatesInt[j].trajectory.clear();
			    playerStatesInt[j].trajectory.add(new Line2D.Double(curPoint, curPoint));
			    
			    currentLoop = game.time;
			    // Only let one player break j's loop (not sure if we need this).
			    // break;
		        }
		    }
		    
		    states[j] = new PlayerState(j, x, y, vx, vy, dir, energy, currentLoop, score);
		
		}

		// Calculate the new positions for the bullets and figure out
		// if anyone died.
		for (int k = bullets.size() - 1; k >= 0; --k) {
		    b = bullets.get(k);

		    // Whether the bullet hit something.
		    remove = false;

		    x = b.x + (int) (((double) Bullet.SPEED) * Math.cos(Math.PI * (b.direction / (double) INTMAX)));
		    y = b.y + (int) (((double) Bullet.SPEED) * Math.sin(Math.PI * (b.direction / (double) INTMAX)));

		    // TODO: This isn't an accurate way of doing this if
		    //   the bullets/players move fast.
		    for (int j=0; j<NUMPLAYERS; ++j) {
			xy = states[j].shape();
			p = new Polygon(xy[0], xy[1], 4);

			if (b.playerNo != j && p.contains(b.x, b.y)) {
			    states[j] = initPlayerState(j, states[j].score - 1);
			    remove = true;
			}
		    }

		    if (!mapedges.contains(x, y))
			remove = true;

		    for (Cluster c : clusters) {
			if (c.boundingBox().contains(x, y))
			    remove = true;
		    } 

		    // Update the bullet position unless it hit something.
		    if (!remove)
			bullets.set(k, new Bullet(b.playerNo, x, y, b.direction));
		    else
			bullets.remove(k);

		}

		// Check if anyone went out of bounds or hit a cluster:
		for (int j=0; j<NUMPLAYERS; ++j) {

		    crashed = false;

		    if (!mapedges.contains(states[j].x, states[j].y))
			crashed = true;

		    for (Cluster c : clusters) {
			if (c.boundingBox().contains(states[j].x, states[j].y))
			    crashed = true;
		    } 

		    if (crashed) {
			states[j] = initPlayerState(j, states[j].score);
		    }

		}


		// Update the game state.
		// Note: don't need locking since updating a reference is atomic.
		this.game = new GameState(game.time + 1, states, bullets);

	    }
	}
    }


    // Check if the last step of player i intersects with the path of player j
    // and if so, return the loop of player j that is now broken.
    // Note this is not symmetric in i and j.
    private Path2D checkForIntersection(int i, int j) {
	int Ni, Nj;
	Line2D lastSegment;
	Path2D loop;
	Point2D point;

    	Ni = playerStatesInt[i].trajectory.size();
    	Nj = playerStatesInt[j].trajectory.size();	
	lastSegment = playerStatesInt[i].trajectory.get(Ni-1);
	for (int k=0; k < Nj-2; ++k) {

	    // If there was an intersection, remove the path and check for scoring.
	    if (lastSegment.intersectsLine(playerStatesInt[j].trajectory.get(k))) {

		// Construct a Path2D for the loop only.
		loop = new Path2D.Double();

		point = playerStatesInt[j].trajectory.get(k).getP2();
		loop.moveTo(point.getX(), point.getY());

		for (int l=k+1; l < Nj; ++l) {
		    point = playerStatesInt[j].trajectory.get(l).getP2();
		    loop.lineTo(point.getX(), point.getY());
		}
		return loop;
	    }
	}
	return null;
    }
    
    // Check if the particular loop merits a score up for player j. 
    // If so, return true; else, false.
    private boolean checkForScoring(int j, Path2D loop) {

	for (ClusterServerSide c : clusters) {
	    if (c.nSegments.contains(game.states[j].score+1) && loop.contains(c.x, c.y)) {
		System.out.println("Player " + j + " encircled (" + c.x + ", " + c.y + ").");
		// This is an important point - what to do
		// if someone encircles a cluster. For now
		// write the data in the Database. 
		
		// It maybe be cool to also remove the cluster but right
		// now the Arena is only transmitted to the client at the beginning.
			    
		// Note that determining what nSegments values
		// are acceptable is determined in ProtoCluster.acceptableN()
		
		
		

		return true; // collect only one cluster per loop
	    }
	}
	return false;
    }
    

    private class PlayerStateInternal {
	/* Values used internally by the simulation. */
	public int shotsTaken = 0;
	public int reloadTimeLeft = 0;

	// The path traced by the ship since the last loop completion.
	public Vector<Line2D> trajectory;

	// The last two control parameters.
	public PlayerControlParameters params = null;
	public PlayerControlParameters prevParams = null;


	public PlayerStateInternal(int xInitial, int yInitial) {
	    trajectory = new Vector<Line2D>();
	    trajectory.add(new Line2D.Double(xInitial, yInitial, xInitial, yInitial)); // Zero length line
	}
    }


    private class InterruptThreadsTask extends TimerTask {

	Thread[] threads;
	
	public InterruptThreadsTask(Thread[] threads) {
	    this.threads = threads;
	}

        public void run() {
            for (Thread t : threads)
		t.interrupt();
        }
    }

}




