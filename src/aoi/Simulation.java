package aoi;

import java.awt.geom.Point2D;
import java.io.*;
import java.util.Vector;
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

  // Static information about the game state.
  public Arena arena;

  // References to the senders/listeners threads so we can interrupt them.
  public final Thread[] senderThreads = new Thread[SimulationUtils.NUMPLAYERS];
  public final Thread[] listenerThreads = new Thread[SimulationUtils.NUMPLAYERS];
  public final GameStateSender[] senders = new GameStateSender[SimulationUtils.NUMPLAYERS];
  public final PlayerListener[] listeners = new PlayerListener[SimulationUtils.NUMPLAYERS];

  // Return the current state of the game.
  // Called by GameStateSender.
  public GameState gameState() {
    return game; // Atomic so no locking necessary.
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

    Angle dir = new Angle((Math.PI * (1 - 2*Math.random())));

    int tailStarted = (game == null) ? 0 : game.time;
    return new PlayerState(playerNo, x, y, 0, 0, dir, 0,
                           tailStarted, tailStarted, tailStarted, score,
                           0, 0, // untilReload, untilRespawn
                           255, 255, 255, 255, 255, // sinceScoring, sinceLooping, sinceCrossing, sinceHitByBullet, sincePwned
                           new Tail(x, y, time)); 
  }

  public void startGame(ClusterDatabase clusterDB, int pointsPerRound) throws IOException {

    PlayerState[] states = (PlayerState[]) (game.states.clone());
    Vector<Bullet> bullets = new Vector<Bullet>();


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
        // (This is a temporary hack).
        for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j) {
          assert(states[j].score <= pointsPerRound);
          if (states[j].score == pointsPerRound) {
            for (int k=0; k<SimulationUtils.NUMPLAYERS; ++k) {
              states[k] = states[k].clone("score", 0);
              int TIME_BETWEEN_ROUNDS_JUST_TEMPORARY = 1000 / SimulationUtils.TIMESTEP;
              states[k] = states[k].clone("untilRespawn", TIME_BETWEEN_ROUNDS_JUST_TEMPORARY);
            }
          }
        }
        
        // First copy the parameters to minimize the time difference
        // different players have to update their parameters.
        // Note: don't need to clone (deep copy) or lock because all
        // fields of PlayerControlParameters are final.
        PlayerControlParameters[] params = new PlayerControlParameters[SimulationUtils.NUMPLAYERS];
        for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j)
          params[j] = listeners[j].getCtrlParams(game.time);
        
        boolean[] gotPwned = new boolean[2];
        for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j)
          gotPwned[j] = false;
        // --- end
        
        for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j) { 
 /* ----------------------  "main" loop for each player  --------------------- */ 

          Ship ship;
          Tail tail;
          boolean erasePath = false;
          boolean spendPathOnBullet = false;
          boolean scored = false;
          boolean crossedOtherPath = false;
          boolean formedLoop = false;
          
          // Check for players waiting to respawn:
//          if (states[j].untilRespawn == 1) {  // time to respawn
//            states[j] = initPlayerState(j, states[j].score, false, game.time);
//            continue;
          if (states[j].untilRespawn > 0) {  // still waiting
            int tailStarted, cropFrom, cropTo, untilRespawn;
            
            tailStarted = states[j].tailStarted;
            
            if (!states[j].tail.isEmpty()) { 
              cropFrom = states[j].tail.lengthToTime(states[j].untilRespawn*SimulationUtils.UNCOILSPEED);
              cropTo = game.time + 1; // +1 because crop() does not remove "cropTo itself"
              Point2D newLocation = states[j].tail.getPointFromTime(cropFrom);
              if (newLocation == null) newLocation = states[j].ship.location();
              Angle newDir = states[j].tail.tangentAngle();
              if (newDir == null) newDir = states[j].ship.dir();
              ship = new Ship(newLocation, 0, 0, newDir);
            } else {  // this should never actually happen, but want to guard against weird corner case
              cropFrom = tailStarted;
              cropTo   = tailStarted;
              ship = states[j].ship;
            }
       
            tail = states[j].tail.crop(cropFrom, cropTo);
            
            untilRespawn = states[j].untilRespawn-1;
            
//            Logger.println(RepetitionRate.INFREQUENT, tail);            
//          Logger.println(RepetitionRate.INFREQUENT, "product = " + states[j].untilRespawn*SimulationUtils.UNCOILSPEED);   
//          Logger.println(RepetitionRate.INFREQUENT, "player = " + j);
//          Logger.println(RepetitionRate.INFREQUENT, "head time = " + states[j].tail.headTime());
//          Logger.println(RepetitionRate.INFREQUENT, tailStarted + " " + cropFrom + " " + cropTo);
//            Logger.println(RepetitionRate.INFREQUENT, untilRespawn);
//            Logger.println(RepetitionRate.INFREQUENT, tail.nPoints());    
//            Logger.println(RepetitionRate.INFREQUENT, "-------------");
            
            states[j] = new PlayerState(j, ship, 0, 
                tailStarted, cropFrom, cropTo, states[j].score,
                Math.max(0, states[j].untilReload-1),
                untilRespawn,
                states[j].sinceScoring+1,
                states[j].sinceLooping+1,
                states[j].sinceCrossing+1,
                states[j].sinceHitByBullet+1,
                states[j].sincePwned+1,
                tail);
            continue;
          }
          
          // back to the normal loop for non-respawning players
          
          
          
          // Take shots first:          
          if (params[j].shoot && SimulationUtils.canShoot(states[j])) {
            // if players had enough time to reload and enough path to spend
            bullets.add(new Bullet(j, states[j].ship.x(), states[j].ship.y(), states[j].ship.dir()));
            spendPathOnBullet = true;

          }

          // Calculate where the player moved.
          ship = SimulationUtils.nextPosition(states[j].ship, params[j]);

          // Add the new position to the player's tail
          tail = states[j].tail.addPoint(ship.x(), ship.y(), game.time + 1);

          // Check if the paths cross each other, O(N).
          // Remove all paths that are crossed. Also checks if players scored.          
          Tail loop = null;
          
          // Check if any player (including j) is crossing j's path
          for (int i=0; i<SimulationUtils.NUMPLAYERS; ++i) {            
            // See comments in the intersects() function for more detail on this BS
            Tail intersection = states[i].tail.intersects(tail, (i==j)?2:0);

            if (intersection != null) {  // if an intersection occurred

              if (i != j) {
                erasePath = true;
              } else {
                formedLoop = true;
                loop = intersection;

                assert(states[j].score < arena.sequence().length);

                // Check for scoring
                for (Cluster cNotSS : arena.clusters) {
                  ClusterServerSide c = (ClusterServerSide) cNotSS;
                  
                  // Check if the center of the cluster is in the loop just made
                  if (c.acceptable(arena.sequence()[states[j].score]) && intersection.toPath2D().contains(c.centerX(), c.centerY())) {
                    Logger.println(Logger.RepetitionRate.INFREQUENT, "Player " + j + " encircled (" + c.centerX() + ", " + c.centerY() + ").");

                    scored = true;

                    // Store the vote in a file for post-processing
                    // Currently stores the vote for all clusters in the loop that
                    // *could* have been accepted, since we don't know which cluster in particular
                    // the player was going for.
                    clusterDB.writeData(c.imgName(), states[j].score+1, "Mike R");
                  }
                }

                // Check if the other player got pwned.
                for (int k=0; k<SimulationUtils.NUMPLAYERS; ++k) {
                  if (k==j) continue;

                  if (intersection.toPath2D().contains(states[k].ship.x(), states[k].ship.y())) {
                    gotPwned[j] = true; // set score to 0 if you are enclosed by the other player!!!!
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
            tailStarted = states[j].tail.lengthToTime(SimulationUtils.BULLETCOST) + 1;
            cropFrom    = states[j].tailStarted;
            cropTo      = states[j].tail.lengthToTime(SimulationUtils.BULLETCOST) + 1; // +1 because cropping excludes the "to"
//            Logger.println(Logger.RepetitionRate.INFREQUENT, ""+tail.pathLength());
            tail = tail.crop(cropFrom, cropTo);
//            Logger.println(Logger.RepetitionRate.INFREQUENT, " -> " + tail.pathLength());
//            Logger.println(Logger.RepetitionRate.INFREQUENT, tailStarted + " / " + cropFrom + " / " + cropTo + " / " + game.time);
          } else {
            // normal time step --
            tailStarted = states[j].tailStarted;
            cropFrom = states[j].cropTailFrom;
            cropTo = states[j].cropTailTo;
          }
          // end dealing with moving around and loops and stuff
          
          

          // Calculate the new positions for the bullets and figure out
          // if anyone got hit BY A BULLET.
          boolean gotShot = false;
          for (int k = bullets.size() - 1; k >= 0; --k) {
            Bullet b = SimulationUtils.nextBulletPosition(bullets.get(k));

            // Whether the bullet hit something.
            boolean remove = !SimulationUtils.isInArena(b, arena);

            // if b does not belong to player j and the players ship contains b
            if (SimulationUtils.gotShot(states[j], b)) {
              remove = true;
              gotShot = true;
               // if you get shot then you lose your tail
              tail = new Tail(states[j].ship.x(), states[j].ship.y(), game.time + 1);
              tailStarted = game.time + 1;
              cropFrom = tailStarted;
              cropTo = tailStarted;
              
            }  // end if


            // Update the bullet position unless it hit something.
            if (!remove)
              bullets.set(k, b);
            else
              bullets.remove(k);

          }  // end loop through bullets
          
        
          // Check if anyone went out of bounds or hit a cluster:
          boolean crashed = !SimulationUtils.isInArena(states[j].ship, arena);

          //  SET THE PLAYER STATE TO ALL THE NEW PARAMETERS
          //  RESULTING FROM THIS TIME STEP
          states[j] = new PlayerState(j, ship, 0, 
              tailStarted, cropFrom, cropTo,
              states[j].score + ((scored)? 1:0),
              (spendPathOnBullet)? SimulationUtils.RELOADTIME : Math.max(0, states[j].untilReload-1),
              (crashed)? SimulationUtils.timeUntilRespawn(tail) : 0,
              (scored)? 0 : states[j].sinceScoring+1,
              (formedLoop)? 0 : states[j].sinceLooping+1,
              (crossedOtherPath)? 0 : states[j].sinceCrossing+1,
              (gotShot)? 0 : states[j].sinceHitByBullet+1,
              states[j].sincePwned+1,
              tail);

        } 
/* ----------------- end of "main" loop for each player --------------------- */
        
        // Needs to be outside main loop because loop on j checks
        // if player j pwned someone else, not if player j got pwned.
        for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j) {
          if (gotPwned[j]) {
            states[j] = states[j].clone("score", 0);
            states[j] = states[j].clone("sincePwned", 0);
          }
        }

        // UPDATE THE GAME STATE WITH THE NEW PLAYER STATES
        // Note: don't need locking since updating a reference is atomic.
        this.game = new GameState(game.time + 1, states, bullets);

      }  // end catch
    }  // end infinite while loop
  }  // end this function


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
