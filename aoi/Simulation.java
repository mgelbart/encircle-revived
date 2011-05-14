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
    public static final int TIMESTEP = 40;
 
    // All distances are defined in terms of MAPSIZE.
    // Hence increasing this only increases the resolution.
    public static final int MAPSIZE = 100000;


    // Maximum angular turning rate (i.e. domega/dt).
    // Angle is measured in units of 2*pi / (|INTMAX| + |INTMIN|).
    public static final int OMEGA = (INTMAX/2000) * TIMESTEP;

    // Maximum thrust, i.e. max dx/dt^2.
    public static final int MAXACC = (int) ((MAPSIZE / 1e7) * TIMESTEP * TIMESTEP) * 2;
 
    // Drag coefficient.
    public static final double DRAG = 0.00025 * TIMESTEP * 2.0;

    // Maximum energy.
    public static final int MAXENERGY = 10;

    // Energy recharge period (i.e. how many turns to gain one point).
    public static final int RECHARGEPERIOD = 2000 / TIMESTEP;

    // Energy at the start of the game/after being shot.
    public static final int INITIALENERGY = MAXENERGY;

    // Energy cost per bullet.
    public static final int BULLETCOST = 2;

    // Roughly the minimum distance between adjacent points in the trajectory for
    // encircling clusters. See the algorithm to see why this is only 'roughly' so.
    public static final int MINTRAJECTORYSEGMENT = MAPSIZE / 200;

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
    private Map map;

    // This information is redundant because it's already in map but it's more
    // convenient to check whether something hit a cluster with these rectangles...
    private Rectangle[] clusters;

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

	try {
	    simulation.map = Map.parse(new FileInputStream("map.xml"));
	} catch (Exception e) {
	    System.out.println(e.toString());
	    assert(false);
	}

	simulation.clusters = new Rectangle[simulation.map.clusters.size()];
	for (int j=0; j<simulation.map.clusters.size(); ++j) {
	    Cluster v = simulation.map.clusters.get(j);
	    simulation.clusters[j] = new Rectangle(v.x - v.width/2, v.y - v.height/2,
				      v.width, v.height);
	}


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

	for (int j=0; j<2; ++j)
	    states[j] = initPlayerState(j);

	this.game = new GameState(0, states, new Vector<Bullet>());
    }

    // (Re)generate a player state at a random location.
    private PlayerState initPlayerState(int playerNo) {
	assert(playerNo >= 0);
	assert(playerNo < 2);

	int x = 0;
	int y = 0;
	boolean freespace = false;

	while (!freespace) {

	    x = (int) (MAPSIZE * (0.5 - Math.random()));
	    y = (int) (MAPSIZE * (0.5 - Math.random()));

	    freespace = true;

	    for (Rectangle r : clusters) {
		if (r.contains(x, y))
		    freespace = false;
	    } 
	    
	}

	int dir = (int) (INTMAX  * (1   - 2*Math.random()));

	ctrlParams[playerNo] = new PlayerControlParameters(0, 0, 0, 0);
	playerStatesInt[playerNo] = new PlayerStateInternal(x, y);

	return new PlayerState(playerNo, x, y, dir, INITIALENERGY,
			       (game == null) ? 0 : game.time, 0); 
    }

    @SuppressWarnings("unchecked")
    private void startGame() {

	PlayerState[] states = new PlayerState[2];
	Vector<Bullet> bullets = new Vector<Bullet>();

	// Some temp variables used during calculation
	int dir, x, y, energy, currentLoop, score;
	boolean turnCcw;
	double a;

	Bullet b;
	boolean remove;

	int[][] xy;
	Polygon p;
	boolean crashed;

	int N;
	Point2D curPoint, prevPoint, prevPrevPoint, point;
	Line2D lastSegment;
	Path2D loop;

	Rectangle mapedges = new Rectangle(-MAPSIZE/2, -MAPSIZE/2,
					   MAPSIZE, MAPSIZE);


	// Set up a timer that wakes up the simulation thread every TIMESTEP
	// milliseconds. Note that, if the computation take even slightly longer
	// than TIMESTEP, the time step will be doubled.
	Thread[] threads = new Thread[3];
	threads[0] = Thread.currentThread();
	for (int j=0; j<2; ++j)
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
		for (int j=0; j<2; ++j)
		    playerStatesInt[j].params = this.ctrlParams[j];

		for (int j=0; j<2; ++j) {

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

		    // Calculate the new positions for players.
		    a = Math.max(0.0, MAXACC * (playerStatesInt[j].params.acceleration / (double) INTMAX));

		    playerStatesInt[j].vx = playerStatesInt[j].vx + (int) (a * Math.cos(Math.PI * (game.states[j].direction / (double) INTMAX)));
		    playerStatesInt[j].vy = playerStatesInt[j].vy + (int) (a * Math.sin(Math.PI * (game.states[j].direction / (double) INTMAX)));

		    playerStatesInt[j].vx -= DRAG * playerStatesInt[j].vx;
		    playerStatesInt[j].vy -= DRAG * playerStatesInt[j].vy;

		    x = game.states[j].x + playerStatesInt[j].vx;
		    y = game.states[j].y + playerStatesInt[j].vy;

		    // Figure out whether to turn clockwise or counterclockwise.
		    turnCcw =    Math.abs(game.states[j].direction + OMEGA - playerStatesInt[j].params.targetDirection)
			< Math.abs(game.states[j].direction - OMEGA - playerStatesInt[j].params.targetDirection);
		    dir = game.states[j].direction + ((turnCcw)? 1:-1)*OMEGA;

		    // Don't go past the target direction.
		    if (Integer.signum(game.states[j].direction - playerStatesInt[j].params.targetDirection)
			!= Integer.signum(dir - playerStatesInt[j].params.targetDirection))
			dir = playerStatesInt[j].params.targetDirection;



		    // Check if someone collected a whatever-it-is-we-want-to-call-the-stuff-you-collect

		    currentLoop = game.states[j].currentLoop;
		    score = game.states[j].score;
		    
		    // Add the new position to the trajectory
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
		    
		    // Check if the path crosses itself, O(N).
		    N = playerStatesInt[j].trajectory.size();
		    lastSegment = playerStatesInt[j].trajectory.get(N-1);
		    for (int k=0; k < N-2; ++k) {

			if (lastSegment.intersectsLine(playerStatesInt[j].trajectory.get(k))) {

			    // Construct a Path2D for the loop only.
			    loop = new Path2D.Double();

			    point = playerStatesInt[j].trajectory.get(k).getP2();
			    loop.moveTo(point.getX(), point.getY());

			    for (int l=k+1; l < N; ++l) {
				point = playerStatesInt[j].trajectory.get(l).getP2();
				loop.lineTo(point.getX(), point.getY());
			    }

			    for (Cluster v : map.clusters) {
				if (loop.contains(v.x, v.y)) {
				    System.out.println("Player " + j + " encircled (" + v.x + ", " + v.y + ").");
				    score += 1;
				}
			    }

			    loop = null;

			    playerStatesInt[j].trajectory.clear();
			    playerStatesInt[j].trajectory.add(new Line2D.Double(curPoint, curPoint));

			    currentLoop = game.time;

			    break;

			}

		    }			
		
		    states[j] = new PlayerState(j, x, y, dir, energy, currentLoop, score);
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
		    for (int j=0; j<2; ++j) {
			xy = states[j].shape();
			p = new Polygon(xy[0], xy[1], 4);

			if (b.playerNo != j && p.contains(b.x, b.y)) {
			    states[j] = initPlayerState(j);
			    remove = true;
			}
		    }

		    if (!mapedges.contains(x, y))
			remove = true;

		    for (Rectangle r : clusters) {
			if (r.contains(x, y))
			    remove = true;
		    } 

		    // Update the bullet position unless it hit something.
		    if (!remove)
			bullets.set(k, new Bullet(b.playerNo, x, y, b.direction));
		    else
			bullets.remove(k);

		}

		// Check if anyone went out of bounds or hit a cluster:
		for (int j=0; j<2; ++j) {

		    crashed = false;

		    if (!mapedges.contains(states[j].x, states[j].y))
			crashed = true;

		    for (Rectangle r : clusters) {
			if (r.contains(states[j].x, states[j].y))
			    crashed = true;
		    } 

		    if (crashed) {
			states[j] = initPlayerState(j);
		    }

		}


		// Update the game state.
		// Note: don't need locking since updating a reference is atomic.
		this.game = new GameState(game.time + 1, states, bullets);

	    }
	}
    }


    private class PlayerStateInternal {
	/* Values used internally by the simulation. */
	public int vx = 0;
	public int vy = 0;

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




