package aoi;

import java.awt.Point;
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
  private GameState game;

  // Static information about the game state.
  private Arena arena;

  // References to the senders/listeners threads so we can interrupt them.
  // TODO: these should be private
  public final Thread[] senderThreads = new Thread[SimulationUtils.NUMPLAYERS];
  public final Thread[] listenerThreads = new Thread[SimulationUtils.NUMPLAYERS];
  public final GameStateSender[] senders = new GameStateSender[SimulationUtils.NUMPLAYERS];
  public final PlayerListener[] listeners = new PlayerListener[SimulationUtils.NUMPLAYERS];
  
  // for debugging
  private static final boolean VISUALIZE = false;
  private GUI gui = null;
  private Thread guiThread = null;
  
  // for debugging
  public static final boolean VERIFYSERIALIZATION = true;
  
  
  public Simulation(Arena arena) {
    this.arena = arena;
    if (VISUALIZE) {
      // init GUI
      ClientConfiguration.setPlayerNo(1);  // sketch-ball
      Arena.setCurrent(arena);
      DrawUtils.initialize(DrawUtils.createDefaultCanvas());
      gui = new GUI();
      guiThread = new Thread(gui);
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
    Tail tail = new Tail(p, time);
    
    return new PlayerState(playerNo, ship, tail);
  }

  public void startGame(ClusterDatabase clusterDB, int pointsPerRound) throws IOException {
    // Create temporary variables
    PlayerState[] pStatesTemp = new PlayerState[SimulationUtils.NUMPLAYERS];
    for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j)
      pStatesTemp[j] = game.pStates[j];
    Vector<Bullet> bulletsTemp = new Vector<Bullet>();

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


    Logger.println(Logger.Rep.FEW, "Game starting.");

    while (true) {

      // Sleep until next time step.
      try {
        Thread.sleep(10000);
      } catch (InterruptedException e) {

        if (game.time % 100 == 0)
          Logger.println(Logger.Rep.INFREQ, "Current local time: " + game.time);

        // --- Stuff that you do first
        // Check if one of the players won.
        // (This is a temporary hack).
        for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j) {
          assert(pStatesTemp[j].score <= pointsPerRound);
          if (pStatesTemp[j].score == pointsPerRound) {
            for (int k=0; k<SimulationUtils.NUMPLAYERS; ++k) {
              pStatesTemp[k] = pStatesTemp[k].cloneAndPwn();
            }
          }
        }
        
        // Move bullets first
        for (int k = bulletsTemp.size() - 1; k >= 0; --k) {
          Bullet b = SimulationUtils.nextPosition(bulletsTemp.get(k));

          // Update the bullet position unless it hit something.
          if (SimulationUtils.isInArena(b, arena))
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
          params[j] = listeners[j].getCtrlParams(game.time);
        
        boolean[] gotPwned = new boolean[SimulationUtils.NUMPLAYERS];
        for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j)
          gotPwned[j] = false;

        
        for (int playerNo=0; playerNo<SimulationUtils.NUMPLAYERS; ++playerNo) { 
 /* ----------------------  "main" loop for each player  --------------------- */ 

          /* TABLE OF CONTENTS
           * 
           * (0) copy previous state parameters
           * (1) take shots
           * (2) check for tail crossings and scoring
           * (3) check if players are hit by bullets
           * (4) check for crashing
           * (5) move the Ship
           * (6) optimize cropEvents
           * (7) update Tail
           * (8) update the temporary player state
           * (9) update the real game state
           * Note: steps (1)-(5) skipped for respawning players
           */
          
          
          // (0) COPY THE PREVIOUS STATE'S PARAMETERS AND UPDATE BYTE FLAGS
          Ship ship                 = game.pStates[playerNo].ship;
          CropEventGroup cropEvents = game.pStates[playerNo].cropEvents;
          int score                 = game.pStates[playerNo].score;
          int untilReload           = SimulationUtils.enforceByte(game.pStates[playerNo].untilReload      -1);
          int untilRespawn          = SimulationUtils.enforceByte(game.pStates[playerNo].untilRespawn     -1);
          int sinceScoring          = SimulationUtils.enforceByte(game.pStates[playerNo].sinceScoring     +1);
          int sinceLooping          = SimulationUtils.enforceByte(game.pStates[playerNo].sinceLooping     +1);
          int sinceCrossing         = SimulationUtils.enforceByte(game.pStates[playerNo].sinceCrossing    +1);
          int sinceHitByBullet      = SimulationUtils.enforceByte(game.pStates[playerNo].sinceHitByBullet +1);
          int sincePwned            = SimulationUtils.enforceByte(game.pStates[playerNo].sincePwned       +1);
          Tail tail                 = game.pStates[playerNo].tail;
                    
          // Special case: if you are in the process of respawning
          if (untilRespawn > 0) {
            assert(!tail.isEmpty());
            
            int cropFrom = tail.lengthFromLastKnotToTime(
                           (untilRespawn-1)*SimulationUtils.UNCOILSPEED);
            
            cropEvents = cropEvents.addEvent(cropFrom, game.time+1, game.time+1);
            
            Point newLocation = tail.getPointFromTime(cropFrom);
            Angle newDir      = tail.tangentAngleHead();
            // Create new ship with zero velocity
            ship = new Ship(newLocation, 0, 0, newDir);
          } 
          else {
          // Normal case when not respawning
            
            // (1) TAKE SHOTS
            if (params[playerNo].shoot && 
                SimulationUtils.canShoot(pStatesTemp[playerNo])) {
              
              bulletsTemp.add(SimulationUtils.nextPosition(new Bullet(playerNo, pStatesTemp[playerNo])));
              untilReload = SimulationUtils.RELOADTIME;
              cropEvents = cropEvents.addEvent(
                  tail.baseTime(),                                             // from
                  tail.lengthFromBaseToTime(SimulationUtils.BULLETCOST) + 1,   // to
                  game.time + 1);
            }
  
  
            // (2) CHECK FOR TAIL CROSSINGS AND SCORING          
            for (int thatPlayerNo=0; thatPlayerNo<SimulationUtils.NUMPLAYERS; ++thatPlayerNo) {            
              // (below) See comments in the intersects() function for more detail on this BS
              Tail intersection = pStatesTemp[thatPlayerNo].tail.intersects(tail, (thatPlayerNo==playerNo)?2:0);
  
              if (intersection != null) {
                if (thatPlayerNo != playerNo) {
                  // If this player crossed the Tail of another player
                  sinceCrossing = 0;
                  cropEvents = cropEvents.addEvent(
                      tail.baseTime(),              // from  
                      tail.cropToForCrossing(),     // to
                      game.time + 1);
                } else {
                  // If this player intersected his own Tail
                  sinceLooping = 0;
                  cropEvents = cropEvents.addEvent(
                      intersection.baseTime()+1,      // from 
                      game.time,                      // to
                      game.time + 1);
                  
                  // Check for scoring
                  boolean scored = false;
                  for (Cluster cNotSS : arena.clusters) {
                    ClusterServerSide c = (ClusterServerSide) cNotSS;
                    
                    // Check if the center of the cluster is in the loop just made
                    if (c.acceptable(arena.sequence()[score]) && 
                        intersection.contains(c.centerX(), c.centerY())) {
                      Logger.println(Logger.Rep.INFREQ, 
                          "Player " + playerNo + " encircled (" + c.centerX() + ", " + c.centerY() + ").");
                      
                      scored = true;
  
                      // Store the vote in a file for post-processing
                      clusterDB.writeData(c.imgName(), score+1, "Mike R");
                    }
                  }
                  if (scored) {
                    score++;
                    sinceScoring = 0;
                  }
                  
                  // Check if the other player got pwned.
                  for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j)
                    if (j != playerNo && intersection.contains(pStatesTemp[j].ship.x(), pStatesTemp[j].ship.y()))
                      gotPwned[j] = true;
                    
                }      // end if (thatPlayerNo !=/== playerNo) 
              }     // end if (intersection != null)
            }     // end for (otherPlayerNo)
            
            
            // (3) CHECK IF PLAYER IS HIT BY BULLETS
            for (Bullet b : bulletsTemp) {
              if (SimulationUtils.gotShot(pStatesTemp[playerNo], b)) {
                sinceHitByBullet = 0;
                untilRespawn = SimulationUtils.timeUntilRespawn(tail);
              }
            }
            
            
            // (4) CHECK IF PLAYER CRASHED
            if (!SimulationUtils.isInArena(ship, arena)) {
              untilRespawn = SimulationUtils.timeUntilRespawn(tail);
            }
            
            
            // (5) CALCULATE WHERE PLAYER MOVED AND UPDATE SHIP
            ship = SimulationUtils.nextPosition(ship, params[playerNo]);
          }   // end if not respawning
          

          // (6) REMOVE EXPIRED CROP EVENTS AND OPTIMIZE EVENTGROUP
          // (if want speedup, combine these into one function so
          //  that cropEvents doesn't clone twice)
          cropEvents = cropEvents.removeExpired(game.time + 1);
          cropEvents = cropEvents.optimizeEvents(game.time + 1);
          
          
//          if (!cropEvents.isEmpty()) Logger.println(Logger.RepetitionRate.FREQUENT, (game.time+1) + "  " + cropEvents);
          // (7) UPDATE TAIL BY ADDING NEW POINT AND CROPPING
          tail = Tail.parse(game.pStates[playerNo], ship.location(),
              game.time+1, cropEvents, score);
          
          
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
              tail);
        } 
/* ----------------- end of "main" loop for each player --------------------- */
        
        // Needs to be outside main loop because loop on j checks
        // if player j pwned someone else, not if player j got pwned.
        for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j)
          if (gotPwned[j])
            pStatesTemp[j] = pStatesTemp[j].cloneAndPwn();

        // (9) UPDATE THE GAME STATE WITH THE NEW PLAYER STATES
        GameState newGState = new GameState(
            game.time + 1, pStatesTemp, bulletsTemp);
        
        if (VERIFYSERIALIZATION) {
          // for debugging
          ByteArrayOutputStream out = new ByteArrayOutputStream();
          newGState.serialize(out);
          
          ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
          GameState parsedGState = GameState.parse(
              new DataInputStream(in), this.game);          
          if(!newGState.deepEquals(parsedGState)) {
            Logger.println(Logger.Rep.FEW, "Previous state:");
            Logger.println(Logger.Rep.FEW, this.game);
            assert(false);
          }
        }
        
        // Note: don't need locking since updating a reference is atomic.
        this.game = newGState;

        if (VISUALIZE) {
          // for debugging
          gui.setGameState(this.game);
          guiThread.interrupt();
        }
        
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

