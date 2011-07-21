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
  private static final boolean VISUALIZE = true;
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
      pStates[j] = initPlayerState(j, 0, true, 0);

    this.game = new GameState(0, pStates, new Vector<Bullet>());
  }

  // Generate a player state at one of the corners.
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

    return new PlayerState(playerNo, x, y, 0, 0, dir,
                           new CropEventGroup(), score,
                           0, 0, // untilReload, untilRespawn
                           255, 255, 255, 255, 255, 255, // sinceScoring, sinceLooping, sinceCrossing, sinceHitByBullet, sincePwned
                           new Tail(new Point(x, y), time)); 
  }

  public void startGame(ClusterDatabase clusterDB, int pointsPerRound) throws IOException {

    PlayerState[] pStates = (PlayerState[]) (game.pStates.clone());
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
          assert(pStates[j].score <= pointsPerRound);
          if (pStates[j].score == pointsPerRound) {
            for (int k=0; k<SimulationUtils.NUMPLAYERS; ++k) {
              pStates[k] = pStates[k].cloneAndPwn();
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
        
        boolean[] gotPwned = new boolean[SimulationUtils.NUMPLAYERS];
        for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j)
          gotPwned[j] = false;
        // --- end
        
        for (int playerNo=0; playerNo<SimulationUtils.NUMPLAYERS; ++playerNo) { 
 /* ----------------------  "main" loop for each player  --------------------- */ 

          // COPY THE PREVIOUS STATE'S PARAMETERS AND UPDATE BYTE FLAGS
          Ship ship                 = pStates[playerNo].ship;
          CropEventGroup cropEvents = pStates[playerNo].cropEvents;
          int score                 = pStates[playerNo].score;
          int untilReload           = SimulationUtils.enforceByte(pStates[playerNo].untilReload      -1);
          int untilRespawn          = SimulationUtils.enforceByte(pStates[playerNo].untilRespawn     -1);
          int sinceScoring          = SimulationUtils.enforceByte(pStates[playerNo].sinceScoring     +1);
          int sinceLooping          = SimulationUtils.enforceByte(pStates[playerNo].sinceLooping     +1);
          int sinceCrossing         = SimulationUtils.enforceByte(pStates[playerNo].sinceCrossing    +1);
          int sinceHitByBullet      = SimulationUtils.enforceByte(pStates[playerNo].sinceHitByBullet +1);
          int sincePwned            = SimulationUtils.enforceByte(pStates[playerNo].sincePwned       +1);
          int sinceKnot             = SimulationUtils.enforceByte(pStates[playerNo].sinceKnot        +1);        
          Tail tail                 = pStates[playerNo].tail;
                    
          // Special case: if you are in the process of respawning
          if (pStates[playerNo].untilRespawn > 0) {
            assert(!pStates[playerNo].tail.isEmpty());
            
            
            int cropFrom = tail.lengthFromLastKnotToTime(
                           untilRespawn*SimulationUtils.UNCOILSPEED);
            
            cropEvents = cropEvents.addEvent(cropFrom, game.time+1);
            
            Point newLocation = tail.getPointFromTime(cropFrom);
            Angle newDir      = tail.tangentAngleHead();
            // Create new ship with zero velocity
            ship = new Ship(newLocation, 0, 0, newDir);
          } 
          else {
          // Normal case when not respawning
            
            // TAKE SHOTS
            if (params[playerNo].shoot && 
                SimulationUtils.canShoot(pStates[playerNo])) {
              
              bullets.add(new Bullet(playerNo, ship.x(), ship.y(), ship.dir()));
              untilReload = SimulationUtils.RELOADTIME;
              cropEvents = cropEvents.addEvent(
                  pStates[playerNo].tail.baseTime(),            // from
                  pStates[playerNo].tail.lengthFromBaseToTime(  // to
                      SimulationUtils.BULLETCOST) + 1);
            }
  
  
            // CHECK FOR TAIL CROSSINGS AND SCORING          
            for (int thatPlayerNo=0; thatPlayerNo<SimulationUtils.NUMPLAYERS; ++thatPlayerNo) {            
              // (below) See comments in the intersects() function for more detail on this BS
              Tail intersection = pStates[thatPlayerNo].tail.intersects(tail, (thatPlayerNo==playerNo)?2:0);
  
              if (intersection != null) {
                if (thatPlayerNo != playerNo) {
                  // If this player crossed the Tail of another player
                  sinceCrossing = 0;
                  cropEvents = cropEvents.addEvent(
                      pStates[playerNo].tail.baseTime(),   // from  
                      tail.cropToForCrossing());           // to
                } else {
                  // If this player intersected his own Tails
                  sinceLooping = 0;
                  cropEvents = cropEvents.addEvent(
                      intersection.baseTime(),            // from 
                      game.time + 1);                     // to
                  
                  // Check for scoring
                  assert(pStates[playerNo].score < arena.sequence().length);
                  boolean scored = false;
                  for (Cluster cNotSS : arena.clusters) {
                    ClusterServerSide c = (ClusterServerSide) cNotSS;
                    
                    // Check if the center of the cluster is in the loop just made
                    if (c.acceptable(arena.sequence()[pStates[playerNo].score]) && intersection.contains(c.centerX(), c.centerY())) {
                      Logger.println(Logger.RepetitionRate.INFREQUENT, "Player " + playerNo + " encircled (" + c.centerX() + ", " + c.centerY() + ").");
                      
                      scored = true;
  
                      // Store the vote in a file for post-processing
                      // Currently stores the vote for all clusters in the loop that
                      // *could* have been accepted, since we don't know which cluster in particular
                      // the player was going for.
                      clusterDB.writeData(c.imgName(), pStates[playerNo].score+1, "Mike R");
                    }
                  }
                  if (scored) {
                    score++;
                    tail = tail.addKnotAtHead();
                  }
                  
                  // Check if the other player got pwned.
                  for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j)
                    if (j != playerNo && intersection.contains(pStates[j].ship.x(), pStates[j].ship.y()))
                      gotPwned[j] = true;
                    
                }      // end if (thatPlayerNo !=/== playerNo) 
              }     // end if (intersection != null)
            }     // end for (otherPlayerNo)
            
            
            // MOVE BULLETS AND CHECK IF PLAYER IS HIT BY BULLET
            boolean gotShot = false;
            for (int k = bullets.size() - 1; k >= 0; --k) {
              Bullet b = SimulationUtils.nextBulletPosition(bullets.get(k));
  
              // Whether the bullet hit something.
              boolean remove = !SimulationUtils.isInArena(b, arena);
  
              if (SimulationUtils.gotShot(pStates[playerNo], b)) {
                remove = true;
                gotShot = true;              
              }
  
              // Update the bullet position unless it hit something.
              if (!remove)
                bullets.set(k, b);
              else
                bullets.remove(k);
            }  // end loop through bullets
            if (gotShot) {
              sinceHitByBullet = 0;
              untilRespawn = SimulationUtils.timeUntilRespawn(tail);
            }
            
            // CHECK IF PLAYER CRASHED
            if (!SimulationUtils.isInArena(pStates[playerNo].ship, arena)) {
              untilRespawn = SimulationUtils.timeUntilRespawn(tail);
            }
            
            // Calculate where the player moved.
            ship = SimulationUtils.nextPosition(pStates[playerNo].ship, params[playerNo]);
          }
          

          // Update the tail by executing the crop events 
          // and adding the latest point
          // This is both for the respawning and normal cases
          tail = tail.parse(ship.location(), game.time+1, cropEvents);

          
          //  SET THE PLAYER STATE TO ALL THE NEW PARAMETERS
          //  RESULTING FROM THIS TIME STEP
          pStates[playerNo] = new PlayerState(
              playerNo, 
              ship,
              cropEvents,
              score,
              untilReload-1,
              untilRespawn-1,
              sinceScoring+1,
              sinceLooping+1,
              sinceCrossing+1,
              sinceHitByBullet+1,
              sincePwned+1,
              sinceKnot+1,
              tail);
        } 
/* ----------------- end of "main" loop for each player --------------------- */
        
        // Needs to be outside main loop because loop on j checks
        // if player j pwned someone else, not if player j got pwned.
        for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j)
          if (gotPwned[j])
            pStates[j] = pStates[j].cloneAndPwn();

        // UPDATE THE GAME STATE WITH THE NEW PLAYER STATES
        GameState newGState = new GameState(game.time + 1, pStates, bullets);
        
        if (VERIFYSERIALIZATION) {
          // for debugging
          ByteArrayOutputStream out = new ByteArrayOutputStream();
          newGState.serialize(out);
          
          ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
          GameState parsedGState = GameState.parse(
              new DataInputStream(in), this.game);          
          if(!newGState.deepEquals(parsedGState)) {
            Logger.println(Logger.RepetitionRate.FEW, "Previous state:");
            Logger.println(Logger.RepetitionRate.FEW, this.game);
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

