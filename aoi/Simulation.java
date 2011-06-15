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

/**
 *  The main class ran by the server that carries on the
 *  game step by step by calculating new positions for
 *  players, checking for scoring, etc.
 *
 *  Currently this class also handles connections to
 *  clients but it would be cleaner to have a separate
 *  Server class for that (like Client and Intelligence are
 *  separated on the client side).
 */
public class Simulation {

  public static final int INTMAX = 2147483647;
  public static final int INTMIN = -2147483648;

  // Advance the simulation every TIMESTEP milliseconds.
  // This will not be respected if the computation takes longer
  // than TIMESTEP.
  //
  // All other times are defined in terms of TIMESTEP. Hence
  // decreasing this only increases resolution.
  public static final int TIMESTEP = 40;
 
  // All distances are defined in terms of ARENASIZE.
  // Hence increasing this only increases the resolution.
  public static final int ARENASIZE = 100000;

  // Maximum angular turning rate (i.e. domega/dt).
  // Angle is measured in units of 2*pi / (|INTMAX| + |INTMIN|).
  public static final int OMEGA = (INTMAX/1000) * TIMESTEP;

  // Maximum thrust, i.e. max dx/dt^2.
  public static final int MAXACC = (int) ((ARENASIZE / 1e7 * 5) * TIMESTEP * TIMESTEP) ;
 
  // Drag coefficient.
  public static final double DRAG = 0.0025 * TIMESTEP;

  // Time to wait until respawning.
  public static final int RESPAWNTIME = 1000 / TIMESTEP;

  // Energy recharge period (i.e. how many turns to gain one point).
  public static final int RECHARGEPERIOD = 1000 / TIMESTEP;

  // The number of players in the game. Currently not adjustable.
  public static final int NUMPLAYERS = 2;

  /*
   * TODO: Should write a proper constructor and/or accessor
   *       method for Simulation that can be called from
   *       Server instead of using all these public fields.
   */

  // The current game state.
  public GameState game;

  // Private information we want to store about the players
  // but not communicate to the clients.
  private final PlayerStateInternal[] playerStatesInt = new PlayerStateInternal[2];

  // Static information about the game state.
  public Arena arena;

  // This information is redundant because it's already in arena but it's more
  // convenient to check whether something hit a cluster with these rectangles...
  public Vector<ClusterServerSide> clusters = new Vector<ClusterServerSide>();

  // References to the senders/listeners threads so we can interrupt them.
  public final Thread[] senderThreads = new Thread[NUMPLAYERS];
  public final Thread[] listenerThreads = new Thread[NUMPLAYERS];
  public final GameStateSender[] senders = new GameStateSender[NUMPLAYERS];
  public final PlayerListener[] listeners = new PlayerListener[NUMPLAYERS];


  // Return the current state of the game.
  // Called by GameStateSender.
  public GameState gameState() {
    return game; // Atomic so no locking necesary.
  }


  public void initGameState() {
    PlayerState[] states = new PlayerState[NUMPLAYERS];

    for (int j=0; j<NUMPLAYERS; ++j)
      states[j] = initPlayerState(j, 0, true);

    this.game = new GameState(0, states, new Vector<Bullet>());
  }

  // (Re)generate a player state at one of the corners.
  // If homeCorner is true, the corner is deterministic.
  // Otherwise it's random.
  private PlayerState initPlayerState(int playerNo, int score,
                                      boolean homeCorner) {
    assert(playerNo >= 0);
    assert(playerNo < NUMPLAYERS);
    if (score < 0) score = 0;

    // Always start from one of the corners of the map.
    final int corner = (homeCorner) ? 2*playerNo : (int) (4 * Math.random());

    final int x = ARENASIZE/2 * ((corner==0 || corner==1) ?  1 : -1);
    final int y = ARENASIZE/2 * ((corner==0 || corner==3) ? -1 :  1);

    int dir = (int) (INTMAX  * (1   - 2*Math.random()));

    playerStatesInt[playerNo] = new PlayerStateInternal(x, y);

    return new PlayerState(playerNo, x, y, 0, 0, dir, 0,
                           (game == null) ? 0 : game.time, score, false); 
  }

  @SuppressWarnings("unchecked")
    public void startGame(ClusterDatabase clusterDB, int pointsPerRound) throws IOException {

    PlayerState[] states = (PlayerState[]) (game.states.clone());
    Vector<Bullet> bullets = new Vector<Bullet>();


    Rectangle mapedges = new Rectangle(-2*ARENASIZE, -2*ARENASIZE,
                                       4*ARENASIZE, 4*ARENASIZE);


    // Set up a timer that wakes up the simulation thread every TIMESTEP
    // milliseconds. Note that, if the computation takes even slightly longer
    // than TIMESTEP, the time step will be doubled.
    Thread[] threads = new Thread[1+NUMPLAYERS];
    threads[0] = Thread.currentThread();
    for (int j=0; j<NUMPLAYERS; ++j)
      threads[1+j] = senderThreads[j];


    TimerTask interruptTask = new InterruptThreadsTask(threads);
    Timer timer = new Timer();
    timer.schedule(interruptTask, 0, TIMESTEP);


    System.out.println("Game starting.");

    while (true) {

      // Sleep until next time step.
      try {
        Thread.sleep(10000);
      } catch (InterruptedException e) {

        if (game.time % 100 == 0)
          System.out.println("Current local time: " + game.time);

        // Check if one of the players won.
        for (int j=0; j<NUMPLAYERS; ++j) {

          assert(states[j].score <= pointsPerRound);

          if (states[j].score == pointsPerRound) {
            for (int k=0; k<NUMPLAYERS; ++k) {
              playerStatesInt[k].respawnTimeLeft = RESPAWNTIME;
              states[k] = states[k].clone("score", 0);
              states[k] = states[k].clone("respawning", true);
            }
          }

        }

        // Check for players waiting to be respawn:
        for (int j=0; j<NUMPLAYERS; ++j) {
          if (playerStatesInt[j].respawnTimeLeft == 1)
            states[j] = initPlayerState(j, states[j].score, false);
        }


        // First copy the parameters to minimize the time difference
        // different players have to update their parameters.
        // Note: don't need to clone (deep copy) or lock because all
        // fields of PlayerControlParameters are final.
        for (int j=0; j<NUMPLAYERS; ++j)
          playerStatesInt[j].params = listeners[j].getCtrlParams(game.time);

        for (int j=0; j<NUMPLAYERS; ++j) {

          // Freeze before respawning.
          if (playerStatesInt[j].respawnTimeLeft > 0) {
            playerStatesInt[j].respawnTimeLeft--;
            continue;
          }

          boolean erasePath = false;


          // Take shots first:
          if (playerStatesInt[j].prevParams != playerStatesInt[j].params)
            playerStatesInt[j].shotsTaken = 0;

          // if players have not yet taken enough time to reload
          if (playerStatesInt[j].reloadTimeLeft > 0) {

            playerStatesInt[j].reloadTimeLeft--;

          } else if (playerStatesInt[j].shotsTaken < playerStatesInt[j].params.shotsToTake) {

            bullets.add(new Bullet(j,
                                   states[j].pos.x,
                                   states[j].pos.y,
                                   states[j].pos.direction));

            playerStatesInt[j].reloadTimeLeft = Bullet.RELOADTIME;
            erasePath = true;
			
          }

          // Calculate where the player moved.
          PlayerState.Position pos = SimulationUtils.nextPosition(states[j].pos, playerStatesInt[j].params);

          // Add the new position to the player's tail
          playerStatesInt[j].tail.addPoint(pos.x, pos.y);
	    
          // Check if the paths cross eachother, O(N).
          // Remove all paths that are crossed.
          // Also checks if players scored.
          int score = states[j].score;
		    
          // Check if any player (including j) is crossing j's path
          for (int i=0; i<NUMPLAYERS; ++i) {
            //   if (i != j) continue;   // uncomment this to disable path-killing            
            // Check if the last step of player j intersects with the path of player i
            // and if so, return the loop of player j that is now complete
            // (note: the behavior is not really defined when there is an intersection and i!=j,
            // but this is fine because in the i!=j case all we really need out of this function
            // is a boolean and loop==null is that boolean)
            // Note this is not symmetric in i and j.
            Path2D loop = playerStatesInt[i].tail.intersects(playerStatesInt[j].tail.getLastSegment());

            if (loop != null) {  // if an intersection occurred
              erasePath = true;

              if (i == j) {   
                
                boolean scored = false;
                // Check for scoring
                for (ClusterServerSide c : clusters) {
                  if (c.acceptable(game.states[j].score+1) && loop.contains(c.centerX(), c.centerY())) {
                    System.out.println("Player " + j + " encircled (" + c.centerX() + ", " + c.centerY() + ").");
                    
                    // Store the vote in a file for post-processing
                    // Currently stores the vote for all clusters in the loop that
                    // *could* have been accepted, since we don't know which cluster in particular
                    // the player was going for.
                    clusterDB.writeData(c.imgName(), score+1, "Mike R");

                    // It maybe be cool to also remove the cluster but right
                    // now the Arena is only transmitted to the client at the beginning.
                  }
                }
                if (scored) score++;


                // Check if the other player got pwned.
                for (int k=0; k<NUMPLAYERS; ++k) {
                  if (k==j) continue;

                  if (loop.contains(states[k].pos.x, states[k].pos.y)) {

                    states[k] = states[k].clone("score", 0);   // set score to 0 if you are enclosed by the other player!!!!

                  }
                }
              }
            }
          }

          int loopStarted;
          if (erasePath) {
            playerStatesInt[j].tail = new Tail(pos.x, pos.y);
            loopStarted = game.time;
          } else {
            loopStarted = states[j].loopStarted;
          }

          states[j] = new PlayerState(j, pos, 0, loopStarted, score, states[j].respawning);
		
        }

        // Calculate the new positions for the bullets and figure out
        // if anyone died.
        for (int k = bullets.size() - 1; k >= 0; --k) {
          Bullet b = bullets.get(k);

          // Whether the bullet hit something.
          boolean remove = false;

          int[] bulletPos = SimulationUtils.nextBulletPosition(b);
          int x = bulletPos[0];
          int y = bulletPos[1];

          // TODO: This isn't an accurate way of doing this if
          //   the bullets/players move fast.
          for (int j=0; j<NUMPLAYERS; ++j) {

            if (playerStatesInt[j].respawnTimeLeft > 0) continue;

            int[][] xy = states[j].shape();
            Polygon p = new Polygon(xy[0], xy[1], xy[0].length);

            if (b.playerNo != j && p.contains(b.x, b.y)) {
			    
              remove = true;

			        playerStatesInt[j].tail = new Tail(states[j].pos.x, states[j].pos.y);
			    
              states[j] = states[j].clone("loopStarted", game.time);

            }
          }

          if (!mapedges.contains(x, y))
            remove = true;

          for (Cluster c : clusters) {
            if (c.contains(x, y))
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

          if (playerStatesInt[j].respawnTimeLeft > 0) continue;

          boolean crashed = false;

          if (!mapedges.contains(states[j].pos.x, states[j].pos.y))
            crashed = true;

          for (Cluster c : clusters) {
            // if (c.boundingBox().contains(states[j].pos.x, states[j].pos.y))
		    if (c.contains(states[j].pos.x, states[j].pos.y))
              crashed = true;
          } 

          if (crashed) {

            playerStatesInt[j].respawnTimeLeft = RESPAWNTIME;
            states[j] = states[j].clone("respawning", true);

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
    public int shotsTaken = 0;
    public int reloadTimeLeft = 0;

    public int respawnTimeLeft = 0;

    // The path traced by the ship since the last loop completion.
    public Tail tail;

    // The last two control parameters.
    public PlayerControlParameters params = null;
    public PlayerControlParameters prevParams = null;


    public PlayerStateInternal(int xInitial, int yInitial) {
      tail = new Tail(xInitial, yInitial);
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
