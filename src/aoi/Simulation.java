package aoi;

import java.io.*;
import java.util.Vector;
import java.awt.Rectangle;
import java.awt.geom.Path2D;
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

  // The current game state.
  public GameState game;

  // Private information we want to store about the players
  // but not communicate to the clients.
  private final PlayerStateExtraInfo[] playerStatesInt = new PlayerStateExtraInfo[2];

  // Static information about the game state.
  public Arena arena;

  // This information is redundant because it's already in arena but it's more
  // convenient to check whether something hit a cluster with these rectangles...
  public Vector<ClusterServerSide> clusters = new Vector<ClusterServerSide>();

  // References to the senders/listeners threads so we can interrupt them.
  public final Thread[] senderThreads = new Thread[SimulationUtils.NUMPLAYERS];
  public final Thread[] listenerThreads = new Thread[SimulationUtils.NUMPLAYERS];
  public final GameStateSender[] senders = new GameStateSender[SimulationUtils.NUMPLAYERS];
  public final PlayerListener[] listeners = new PlayerListener[SimulationUtils.NUMPLAYERS];


  // Return the current state of the game.
  // Called by GameStateSender.
  public GameState gameState() {
    return game; // Atomic so no locking necesary.
  }


  // Return the arena used in the game
  public Arena arena() {
    return arena;
  }


  public void initGameState() {
    PlayerState[] states = new PlayerState[SimulationUtils.NUMPLAYERS];

    for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j)
      states[j] = initPlayerState(j, 0, true, 0);

    this.game = new GameState(0, states, new Vector<Bullet>());
  }

  // (Re)generate a player state at one of the corners.
  // If homeCorner is true, the corner is deterministic.
  // Otherwise it's random.
  private PlayerState initPlayerState(int playerNo, int score,
                                      boolean homeCorner, int time) {
    assert(playerNo >= 0);
    assert(playerNo < SimulationUtils.NUMPLAYERS);
    if (score < 0) score = 0;

    // Always start from one of the corners of the map.
    final int corner = (homeCorner) ? 2*playerNo : (int) (4 * Math.random());

    final int x = SimulationUtils.ARENASIZE/2 * ((corner==0 || corner==1) ?  1 : -1);
    final int y = SimulationUtils.ARENASIZE/2 * ((corner==0 || corner==3) ? -1 :  1);

    int dir = (int) (SimulationUtils.INTMAX  * (1 - 2*Math.random()));

    playerStatesInt[playerNo] = new PlayerStateExtraInfo();

    int loopStarted = (game == null) ? 0 : game.time;
    return new PlayerState(playerNo, x, y, 0, 0, dir, 0,
                           loopStarted, loopStarted, loopStarted, score, false,
                           new Tail(x, y, time)); 
  }

  public void startGame(ClusterDatabase clusterDB, int pointsPerRound) throws IOException {

    PlayerState[] states = (PlayerState[]) (game.states.clone());
    Vector<Bullet> bullets = new Vector<Bullet>();


    Rectangle mapedges = new Rectangle(-2*SimulationUtils.ARENASIZE, -2*SimulationUtils.ARENASIZE,
        4*SimulationUtils.ARENASIZE, 4*SimulationUtils.ARENASIZE);


    // Set up a timer that wakes up the simulation thread every TIMESTEP
    // milliseconds. Note that, if the computation takes even slightly longer
    // than TIMESTEP, the time step will be doubled.
    Thread[] threads = new Thread[1+SimulationUtils.NUMPLAYERS];
    threads[0] = Thread.currentThread();
    for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j)
      threads[1+j] = senderThreads[j];


    TimerTask interruptTask = new InterruptThreadsTask(threads);
    Timer timer = new Timer();
    timer.scheduleAtFixedRate(interruptTask, 0, SimulationUtils.TIMESTEP);


    Logger.println(Logger.RepetitionRate.FEW, "Game starting.");

    while (true) {

      // Sleep until next time step.
      try {
        Thread.sleep(10000);
      } catch (InterruptedException e) {

        if (game.time % 100 == 0)
          Logger.println(Logger.RepetitionRate.INFREQUENT, "Current local time: " + game.time);

        // --- Stuff that you do first
        // Check if one of the players won.
        for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j) {
          assert(states[j].score <= pointsPerRound);
          if (states[j].score == pointsPerRound) {
            for (int k=0; k<SimulationUtils.NUMPLAYERS; ++k) {
              playerStatesInt[k].respawnTimeLeft = SimulationUtils.RESPAWNTIME;
              states[k] = states[k].clone("score", 0);
              states[k] = states[k].clone("respawning", 1);
            }
          }
        }
        // Check for players waiting to be respawn:
        for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j) {
          if (playerStatesInt[j].respawnTimeLeft == 1)
            states[j] = initPlayerState(j, states[j].score, false, game.time);
        }
        // First copy the parameters to minimize the time difference
        // different players have to update their parameters.
        // Note: don't need to clone (deep copy) or lock because all
        // fields of PlayerControlParameters are final.
        for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j)
          playerStatesInt[j].params = listeners[j].getCtrlParams(game.time);
        // --- end
        
        
        for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j) { 
          //"main" loop for each player 
          
          // Freeze before respawning.
          if (playerStatesInt[j].respawnTimeLeft > 0) {
            playerStatesInt[j].respawnTimeLeft--;
            continue;
          }

          boolean erasePath = false;
          boolean spendPathOnBullet = false;


          // Take shots first:
          if (playerStatesInt[j].prevParams != playerStatesInt[j].params)
            playerStatesInt[j].shotsTaken = 0;

          // if players have not yet taken enough time to reload
          if (playerStatesInt[j].reloadTimeLeft > 0) {

            playerStatesInt[j].reloadTimeLeft--;

            
          } else if (playerStatesInt[j].shotsTaken < playerStatesInt[j].params.shotsToTake) {
            // if you have reloaded
            
//            if (states[j].tail.pathLength() > SimulationUtils.BULLETCOST) {
              // and if you have enough path to spend
              bullets.add(new Bullet(j,
                  states[j].ship.x(),
                  states[j].ship.y(),
                  states[j].ship.dir()));
  
              playerStatesInt[j].reloadTimeLeft = SimulationUtils.RELOADTIME;
              spendPathOnBullet = true;
//            }
          }

          // Calculate where the player moved.
          Ship ship = SimulationUtils.nextPosition(states[j].ship, playerStatesInt[j].params);

          // Add the new position to the player's tail
          Tail tail = states[j].tail.addPoint(ship.x(), ship.y(), game.time + 1);

          // Check if the paths cross eachother, O(N).
          // Remove all paths that are crossed.
          // Also checks if players scored.
          int score = states[j].score;
          Tail loop = null;
          
          // Check if any player (including j) is crossing j's path
          for (int i=0; i<SimulationUtils.NUMPLAYERS; ++i) {
            //   if (i != j) continue;   // uncomment this to disable path-killing            
            // Check if the last step of player j intersects with the path of player i
            // and if so, return the loop of player j that is now complete
            // (note: the behavior is not really defined when there is an intersection and i!=j,
            // but this is fine because in the i!=j case all we really need out of this function
            // is a boolean and loop==null is that boolean)
            // Note this is not symmetric in i and j.
            Tail intersection = states[i].tail.intersects(tail);

            if (intersection != null) {  // if an intersection occurred

              if (i != j) {
                erasePath = true;
              } else {
                
                loop = intersection;
                
                boolean scored = false;

                assert(score < arena.sequence().length);

                // Check for scoring
                for (ClusterServerSide c : clusters) {
                  
                  // check if the center of the cluster is in the loop just made
                  if (c.acceptable(arena.sequence()[score]) && intersection.toPath2D().contains(c.centerX(), c.centerY())) {
                    Logger.println(Logger.RepetitionRate.INFREQUENT, "Player " + j + " encircled (" + c.centerX() + ", " + c.centerY() + ").");

                    scored = true;

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
                for (int k=0; k<SimulationUtils.NUMPLAYERS; ++k) {
                  if (k==j) continue;

                  if (intersection.toPath2D().contains(states[k].ship.x(), states[k].ship.y())) {

                    states[k] = states[k].clone("score", 0);   // set score to 0 if you are enclosed by the other player!!!!

                  }
                }
              }
            }
          }
          
          int tailStarted, cropFrom, cropTo;
          if (erasePath) {
            // if your path needs to be erased (you got shot, for ex.)
            tail = new Tail(ship.x(), ship.y(), game.time + 1);
            tailStarted = game.time + 1;
            cropFrom = tailStarted;
            cropTo = tailStarted;
          } else if (loop != null) {
            // if you completed a loop
            tailStarted = states[j].tailStarted;
            cropFrom = loop.startTime();
            cropTo = game.time; // probably not game.time + 1, but i didn't think hard
//            Logger.println(Logger.RepetitionRate.INFREQUENT, ""+tail.pathLength());
            tail = tail.crop(cropFrom, cropTo);
//            Logger.println(Logger.RepetitionRate.INFREQUENT, " -> " + tail.pathLength());
//            Logger.println(Logger.RepetitionRate.INFREQUENT, cropFrom + ":" + cropTo);
          } else if (spendPathOnBullet) {
            // if you spend some path on a shot
            tailStarted = states[j].tail.lengthToTime(SimulationUtils.BULLETCOST*states[j].tail.pathLength()) + 1;
            cropFrom    = states[j].tailStarted;
            cropTo      = states[j].tail.lengthToTime(SimulationUtils.BULLETCOST*states[j].tail.pathLength()) + 1; // +1 because cropping excludes the "to"
            Logger.println(Logger.RepetitionRate.INFREQUENT, ""+tail.pathLength());
            tail = tail.crop(cropFrom, cropTo);
            Logger.println(Logger.RepetitionRate.INFREQUENT, " -> " + tail.pathLength());
            Logger.println(Logger.RepetitionRate.INFREQUENT, tailStarted + " / " + cropFrom + " / " + cropTo + " / " + game.time);
          } else {
            // normal time step --
            tailStarted = states[j].tailStarted;
            cropFrom = states[j].cropTailFrom;
            cropTo = states[j].cropTailTo;
          }
          // end dealing with moving around and loops and stuff
          
          
          
          // Calculate the new positions for the bullets and figure out
          // if anyone got hit BY A BULLET.
          for (int k = bullets.size() - 1; k >= 0; --k) {
            Bullet b = bullets.get(k);

            // Whether the bullet hit something.
            boolean remove = false;

            int[] bulletPos = SimulationUtils.nextBulletPosition(b);
            int x = bulletPos[0];
            int y = bulletPos[1];

            // if player j is currently dead 
            if (playerStatesInt[j].respawnTimeLeft > 0) continue;

            // if b does not belong to player j and the players ship contains b
            if (b.playerNo != j && states[j].ship.intersects(b)) {
              remove = true;
               // if you get shot then you lose your tail
              tail = new Tail(states[j].ship.x(), states[j].ship.y(), game.time + 1);
              tailStarted = game.time + 1;
              cropFrom = tailStarted;
              cropTo = tailStarted;
              
            }  // end if

            if (!mapedges.contains(x, y))
              remove = true;

            for (Cluster c : clusters)
              if (c.intersects(b))
                remove = true;

            // Update the bullet position unless it hit something.
            if (!remove)
              bullets.set(k, new Bullet(b.playerNo, x, y, b.direction));
            else
              bullets.remove(k);

          }  // end loop through bullets
          
        
          // Check if anyone went out of bounds or hit a cluster:

          if (playerStatesInt[j].respawnTimeLeft > 0) continue;

          boolean crashed = false;

          if (!mapedges.contains(states[j].ship.x(), states[j].ship.y()))
            crashed = true;

          for (Cluster c : clusters)
            if (c.intersects(states[j].ship))
              crashed = true;
          

          if (crashed) {
            playerStatesInt[j].respawnTimeLeft = SimulationUtils.RESPAWNTIME;
            states[j] = states[j].clone("respawning", 1);
          }
 
          //  SET THE PLAYER STATE TO ALL THE NEW PARAMETERS
          //  RESULTING FROM THIS TIME STEP
          states[j] = new PlayerState(j, ship, 0, 
              tailStarted, cropFrom, cropTo, 
              score, states[j].respawning, tail);

        } // end of "main" loop for each player
        

        // UPDATE THE GAME STATE WITH THE NEW PLAYER STATES
        // Note: don't need locking since updating a reference is atomic.
        this.game = new GameState(game.time + 1, states, bullets);

      }  // end catch
    }  // end infinite while loop
  }  // end this function



  private class PlayerStateExtraInfo {
    /* Values used internally by the simulation. */
    public int shotsTaken = 0;
    public int reloadTimeLeft = 0;

    public int respawnTimeLeft = 0;

    // The last two control parameters.
    public PlayerControlParameters params = null;
    public PlayerControlParameters prevParams = null;
    
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
