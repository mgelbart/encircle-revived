package aoi;

import java.awt.Point;
import java.awt.geom.Path2D;
import java.io.*;
import java.util.Vector;
import java.util.Timer;
import java.util.TimerTask;

import aoi.Logger.Rep;

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
public class Simulation  implements Runnable {

  // a unique identifier for this match
  public final long matchId;
  
  // The current game state.
  private GameState game;

  // Static information about the game state.
  private Arena arena;
  
  private ClusterDatabase clusterDB;
  private int pointsPerRound;
  

  // References to the senders/listeners threads so we can interrupt them.
  // TODO: these should be private
  public final Communicator[] communicators = new Communicator[SimulationUtils.NUMPLAYERS];
  
  // for debugging
  private static boolean VISUALIZE = false;
  private boolean VISUALIZETHIS = VISUALIZE;
  private GUI gui = null;
  private Thread guiThread = null;
  
  // for debugging
  public static final boolean VERIFYSERIALIZATION = true;
  
  /* ----------------------------------------------------------------- */
  
  
  public Simulation(long matchId, Arena arena, ClusterDatabase clusterDB, int pointsPerRound) {
    this.matchId = matchId;
    this.arena = arena;
    this.clusterDB = clusterDB;
    this.pointsPerRound = pointsPerRound;
    
    if (VISUALIZE) {
      VISUALIZE = false; // don't visualize more than one
      VISUALIZETHIS = true;
      // init GUI
      ClientConfiguration.setPlayerNo(1);  // sketch-ball
      Arena.setCurrent(arena);
      DrawUtils.initialize(DrawUtils.createDefaultCanvas());
      gui = new GUI();
      guiThread = new Thread(gui, "gui");
      guiThread.setDaemon(true);
      guiThread.start();
    }
  }
  
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
    PlayerState[] pStates = new PlayerState[SimulationUtils.NUMPLAYERS];

    for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j)
      pStates[j] = initPlayerState(j, true, 0);

    this.game = new GameState(0, pStates, new Vector<Bullet>());
  }

  // Generate a player state at one of the corners.
  // If homeCorner is true, the corner is deterministic.
  // Otherwise it's random.
  private PlayerState initPlayerState(int playerNo,
                                      boolean homeCorner, int time) {
    assert(playerNo >= 0);
    assert(playerNo < SimulationUtils.NUMPLAYERS);

    // Always start from one of the corners of the map.
    final int corner = (homeCorner) ? 2*playerNo : (int) (4 * Math.random());

    final int x = SimulationUtils.ARENASIZE/2 * ((corner==0 || corner==1) ?  1 : -1);
    final int y = SimulationUtils.ARENASIZE/2 * ((corner==0 || corner==3) ? -1 :  1);
    Point p = new Point(x, y);
    Angle dir = new Angle((Math.PI * (1 - 2*Math.random())));

    
    Ship ship = new Ship(p, 0, 0, dir);
    Tail tail = new Tail(time, p);
    
    return new PlayerState(playerNo, ship, tail);
  }

  public void run() {

    // start the client communicators
    for (Communicator c : communicators) c.start();
    
    try {
      Thread.sleep(SimulationUtils.PREPARATIONTIME);
    } catch (InterruptedException e2) {
      Logger.println(Rep.FEW, "interrupted");
    }
    
    // Create temporary variables
    PlayerState[] pStatesTemp = new PlayerState[SimulationUtils.NUMPLAYERS];
    for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j)
      pStatesTemp[j] = game.pStates[j];
    Vector<Bullet> bulletsTemp = new Vector<Bullet>();

    // Set up a timer that wakes up the simulation thread every TIMESTEP
    // milliseconds. Note that, if the computation takes even slightly longer
    // than TIMESTEP, the time step will be doubled.
    Thread[] threads = new Thread[1];
    threads[0] = Thread.currentThread();

    TimerTask interruptTask = new InterruptThreadsTask(threads);
    Timer advanceSimTimer = new Timer("advance-simulation-timer");
    advanceSimTimer.scheduleAtFixedRate(interruptTask, 0, SimulationUtils.TIMESTEP);


    Logger.println(Logger.Rep.FEW, "Game starting.");
    
    // Persistent variables used for respawning
    // (the values are used in subsequent time steps)
    int[] timeOfRespawn = new int[SimulationUtils.NUMPLAYERS]; // the time corresponding to the point 
                           // at which respawning will terminate for a given tail
    double[] lengthUntilRespawn = new double[SimulationUtils.NUMPLAYERS];
    boolean[] spinWhileRespawn = new boolean[SimulationUtils.NUMPLAYERS];
    
    while (true) {

      // Sleep until next time step.
      try {
        Thread.sleep(10000);
      } catch (InterruptedException e) {
        if (game.time % 1000 == 100)
          Logger.println(Rep.INFREQ, "Current local time: " + game.time);

        
        int newTime  = game.time + 1;
        /* TABLE OF CONTENTS
         * 
         * (-1) move bullets
         * for each player
         *   (0) copy previous state parameters
         *   if not respawning
         *     (1) take shots
         *     (2) check if players are hit by bullets
         *     (3) check for crashing into clusters
         *     (4) check for tail crossings and scoring
         *     (5) move the Ship
         *   (6) optimize cropEvents
         *   (7) update Tail
         *   (8) update the temporary player state
         * (9) check if anyone one (temporary)
         * (10) update the real game state
         */
          
        
        
        // (-1) MOVE BULLETS
        for (int k = bulletsTemp.size() - 1; k >= 0; --k) {
          Bullet b = SimulationUtils.nextPosition(bulletsTemp.get(k));
          // Update the bullet position unless it hit something.
          if (SimulationUtils.isInValidLocation(b, arena))
            bulletsTemp.set(k, b);
          else
            bulletsTemp.remove(k);
        }
        
        
        // Copy the parameters to minimize the time difference
        // different players have to update their parameters.
        // Note: don't need to clone (deep copy) or lock because all
        // fields of PlayerControlParameters are final.
        PlayerControlParameters[] params = new PlayerControlParameters[SimulationUtils.NUMPLAYERS];
        for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j)
          params[j] = communicators[j].getCtrlParams(game.time);
        
        boolean[] gotPwned = new boolean[SimulationUtils.NUMPLAYERS];
        for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j)
          gotPwned[j] = false;

        
        for (int playerNo=0; playerNo<SimulationUtils.NUMPLAYERS; ++playerNo) { 
 /* ----------------------  "main" loop for each player  --------------------- */ 
          
          // (0) COPY THE PREVIOUS STATE'S PARAMETERS AND UPDATE BYTE FLAGS
          Ship ship                 = game.pStates[playerNo].ship;
          CropEventGroup cropEvents = game.pStates[playerNo].cropEvents;
          int score                 = game.pStates[playerNo].score;
          int untilReload           = SimulationUtils.enforceByte (game.pStates[playerNo].untilReload      -1);
          int untilRespawn          = SimulationUtils.enforceShort(game.pStates[playerNo].untilRespawn     -1);
          int sinceScoring          = SimulationUtils.enforceByte (game.pStates[playerNo].sinceScoring     +1);
          int sinceLooping          = SimulationUtils.enforceByte (game.pStates[playerNo].sinceLooping     +1);
          int sinceCrossing         = SimulationUtils.enforceByte (game.pStates[playerNo].sinceCrossing    +1);
          int sinceHitByBullet      = SimulationUtils.enforceShort(game.pStates[playerNo].sinceHitByBullet +1);
          int sincePwned            = SimulationUtils.enforceByte (game.pStates[playerNo].sincePwned       +1);
          int sinceCrashed          = SimulationUtils.enforceByte (game.pStates[playerNo].sinceCrashed     +1);
          Tail tail                 = game.pStates[playerNo].tail;
           
          
          // Special case: if you are in the process of respawning
          if (untilRespawn > 0) {
            // Either you were shot or you crashed

            lengthUntilRespawn[playerNo] -= SimulationUtils.UNCOILSPEED;

            Logger.println(Rep.INFREQ, "TOR: " + timeOfRespawn[playerNo]);
            Logger.println(Rep.INFREQ, "untilRespawn: " + untilRespawn);
            Logger.println(Rep.INFREQ, "lengthUR: " + lengthUntilRespawn[playerNo]);
            Logger.println(Rep.INFREQ, "Time now: " + newTime);
            
            if (!tail.isEmpty()) {
              
              // Compute cropFrom
              Integer cropFrom = tail.convertLengthToTime(
                  timeOfRespawn[playerNo], lengthUntilRespawn[playerNo], true);
              assert(cropFrom != null);
                     
              // Get the new location and angle of the ship
              Point newLocation = tail.getPoint(cropFrom);
              Angle newDir;
              if (spinWhileRespawn[playerNo])
                newDir = new Angle(game.time * 0.2);
              else
                newDir = tail.tangentAngleHead();
              // (below) In case the tail only has one point
              if (newLocation == null) newLocation = ship.location(); 
              if (newDir == null) newDir = ship.dir();
              
              // Create new ship with zero velocity
              ship = new Ship(newLocation, 0, 0, newDir);
              
              // Add the crop events
              if (sinceHitByBullet - sinceCrashed <= 0)
                cropFrom++;  // so that you never lose your knot when respawning due to a shot
              cropEvents = cropEvents.addEvent(cropFrom, newTime, newTime, "respawn");
       
              
            } else {
              Logger.println(Rep.FEW, "Empty tail during respawn..."); 
              Logger.println(Rep.FEW, "TOR: " + timeOfRespawn[playerNo]);
              Logger.println(Rep.FEW, "untilRespawn: " + untilRespawn);
              Logger.println(Rep.FEW, "lengthUR: " + lengthUntilRespawn[playerNo]);
              Logger.println(Rep.FEW, "Time now: " + newTime);
            }
     
            
          }
          else {
          // Normal case when not respawning
            
            // (1) TAKE SHOTS
            if (params[playerNo].shoot && 
                SimulationUtils.canShoot(pStatesTemp[playerNo])) {
              
              bulletsTemp.add(SimulationUtils.nextPosition(new Bullet(playerNo, pStatesTemp[playerNo])));
              untilReload = SimulationUtils.RELOADTIME;
              Integer cropTo = tail.convertLengthToTime(tail.baseTime(), SimulationUtils.BULLETCOST, true);
              assert(cropTo != null);
              cropEvents = cropEvents.addEvent(
                  tail.baseTime(),    // from
                  cropTo + 1,         // to
                  game.time + 1,
                  "taking shot");
            }
            
            
            // (2) CHECK IF PLAYER IS HIT BY BULLETS (OR WENT OUT OF BOUNDS)
            for (int k = bulletsTemp.size() - 1; k >= 0; --k) {
              Bullet b = bulletsTemp.get(k);
              if (SimulationUtils.gotShot(pStatesTemp[playerNo], b)) {
                sinceHitByBullet = 0;
                bulletsTemp.remove(k);
              }
            }
            // If you were shot, set the cropEvents and untilRespawn
            // The case where you flew way out of bounds is handled
            // here as well (in the same way)
            if (sinceHitByBullet == 0 ||
                SimulationUtils.isOutOfBounds(ship, arena)) {
              timeOfRespawn[playerNo] = tail.lastKnotTime();
              lengthUntilRespawn[playerNo] = 
                tail.convertTimeToLengthStartingAtHead(timeOfRespawn[playerNo]);
              untilRespawn = 1 + (int) Math.ceil(lengthUntilRespawn[playerNo] / SimulationUtils.UNCOILSPEED);
//              Logger.println(Rep.INFREQ, "Shot respawn. t =" + untilRespawn + " l = " + lengthUntilRespawn[playerNo]);
              spinWhileRespawn[playerNo] = true;
            } 
            
            
            // (3) CHECK IF PLAYER HIT A CLUSTER
            else if (SimulationUtils.isInCluster(ship, arena)) {
              lengthUntilRespawn[playerNo] = SimulationUtils.CRASHUNCOILLENGTH;
              untilRespawn = 1 + (int) Math.ceil(lengthUntilRespawn[playerNo] / SimulationUtils.UNCOILSPEED);
              timeOfRespawn[playerNo] = tail.convertLengthToTime(tail.headTime(), lengthUntilRespawn[playerNo], false);
              Logger.println(Rep.INFREQ, "Crash respawn. t =" + untilRespawn + " l = " + lengthUntilRespawn[playerNo]);
              spinWhileRespawn[playerNo] = false;
            }
            // There still may be problems here if
            // you also cross, score, etc. on the same round...
  
            
//            Logger.println(Rep.INFREQ, (SimulationUtils.isInCluster(ship, arena)));
  
            // (4) CHECK FOR TAIL CROSSINGS AND SCORING          
            for (int thatPlayerNo=0; thatPlayerNo<SimulationUtils.NUMPLAYERS; ++thatPlayerNo) {            
              // (below) See comments in the intersects() function for more detail on this BS
              Tail intersection = pStatesTemp[thatPlayerNo].tail.intersects(tail);
  
              if (intersection != null) {
                if (thatPlayerNo != playerNo) {
                  // If this player crossed the Tail of another player
                  sinceCrossing = 0;
                  cropEvents = cropEvents.addEvent(
                      tail.baseTime(),              // from  
                      tail.cropToForCrossing(),     // to
                      game.time + 1,
                      "crossed path");
                } else {
                  // If this player intersected his own Tail
                  sinceLooping = 0;
                  cropEvents = cropEvents.addEvent(
                      intersection.baseTime()+1,      // from 
                      game.time,                      // to
                      game.time + 1,
                      "made loop");
                  
                  // Check for scoring
                  boolean scored = false;
                  Path2D intersectionPathCache = intersection.toPath2D();
                  for (Cluster cNotSS : arena.clusters) {
                    ClusterServerSide c = (ClusterServerSide) cNotSS;
                    
                    // Check if the center of the cluster is in the loop just made
                    if (c.acceptable(arena.sequence()[score]) && 
                        intersectionPathCache.contains(c.center()) &&
                        sinceHitByBullet > 0) {
                      Logger.println(Logger.Rep.INFREQ, 
                          "Player " + playerNo + " encircled (" + c.center().x + ", " + c.center().y + ").");
                      
                      scored = true;
  
                      // Store the vote in a file for post-processing
                      try {
                        clusterDB.writeData(c.imgName(), score+1, "Mike R");
                      } catch (IOException e1) {
                        Logger.println(Rep.FEW, "Couldn't store vote.");
                        e1.printStackTrace();
                      }
                    }
                  }
                  if (scored) {
                    score++;
                    sinceScoring = 0;
                  }
                  
                  // Check if the other player got pwned.
                  for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j)
                    if (j != playerNo && intersectionPathCache.contains(pStatesTemp[j].ship.location()))
                      gotPwned[j] = true;
                    
                }      // end if (thatPlayerNo !=/== playerNo) 
              }     // end if (intersection != null)
            }     // end for (otherPlayerNo)
            
            
          
            // (5) CALCULATE WHERE PLAYER MOVED AND UPDATE SHIP
              ship = SimulationUtils.nextPosition(ship, params[playerNo]);
            
          }   // end if not respawning
          

          // (6) REMOVE EXPIRED CROP EVENTS AND OPTIMIZE EVENTGROUP
          // (if want speedup, combine these into one function so
          //  that cropEvents doesn't clone twice)
          cropEvents = cropEvents.removeExpired(game.time + 1);
          cropEvents = cropEvents.optimizeEvents(game.time + 1);
          
          
//          if (!cropEvents.isEmpty()) Logger.println(Rep.INFREQ, (game.time+1) + "  " + cropEvents);
          
          
          // (7) UPDATE TAIL BY ADDING NEW POINT AND CROPPING
          tail = Tail.parse(game.pStates[playerNo], ship.location(),
              game.time+1, cropEvents, sinceScoring);
          
          
          //  (8) SET THE PLAYER STATE TO ALL THE NEW PARAMETERS
          //  RESULTING FROM THIS TIME STEP
          pStatesTemp[playerNo] = new PlayerState(
              playerNo, 
              ship,
              cropEvents,
              score,
              untilReload,
              untilRespawn,
              sinceScoring,
              sinceLooping,
              sinceCrossing,
              sinceHitByBullet,
              sincePwned,
              sinceCrashed,
              tail);
        } 
/* ----------------- end of "main" loop for each player --------------------- */
        
        // Needs to be outside main loop because loop on j checks
        // if player j pwned someone else, not if player j got pwned.
        for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j)
          if (gotPwned[j])
            pStatesTemp[j] = pStatesTemp[j].cloneAndPwn();

        // (9) CHECK IF ONE OF THE PLAYERS WON
        // (This is a temporary hack).
        for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j) {
          assert(pStatesTemp[j].score <= pointsPerRound);
          if (pStatesTemp[j].score == pointsPerRound) {
            for (int k=0; k<SimulationUtils.NUMPLAYERS; ++k) {
              pStatesTemp[k] = pStatesTemp[k].cloneAndPwn();
            }
            break;
          }
        }        
        
        
        // (10) UPDATE THE GAME STATE WITH THE NEW PLAYER STATES
        GameState newGState = new GameState(
            game.time + 1, pStatesTemp, bulletsTemp);
        
        if (VERIFYSERIALIZATION) {
          // for debugging
          ByteArrayOutputStream out = new ByteArrayOutputStream();
          try {
            newGState.serialize(out);
          } catch (IOException e1) {
            Logger.println(Rep.FEW, "Couldn't serialize state.");
            e1.printStackTrace();
            assert(false);
          }
          
          ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
          GameState parsedGState = null;
          try {
            parsedGState = GameState.parse(
                new DataInputStream(in), this.game);
          } catch (IOException e1) {
            Logger.println(Rep.FEW, "Couldn't parse state.");
            e1.printStackTrace();
            assert(false);
          }          
          if(!newGState.deepEquals(parsedGState)) {
            Logger.println(Logger.Rep.FEW, "Previous state:");
            Logger.println(Logger.Rep.FEW, this.game);
            assert(false);
          }
        }
        
        // Note: don't need locking since updating a reference is atomic.
        this.game = newGState;

        if (VISUALIZETHIS) {
          // for debugging
          gui.setGameState(this.game);
          guiThread.interrupt();
        }
        
        // end game if both players have disconnected.
        boolean livePlayers = false;
        for (Communicator  c: communicators)
          if (c.isAlive()) { livePlayers = true; break; }
        
        if (!livePlayers) {
          Logger.println(Rep.FEW, "Players disconnected. Stopping simulation.");
          advanceSimTimer.cancel();
          for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j) { communicators[j].stop(); }
          return;
        }
        
        for (Communicator c : communicators)
          c.sendNewState();
        
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

