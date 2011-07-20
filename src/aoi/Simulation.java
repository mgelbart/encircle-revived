package aoi;

import java.awt.Point;
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
  private static final boolean VERIFYSERIALIZATION = false;
  
  
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

    int crop = (game == null) ? 0 : game.time;
    return new PlayerState(playerNo, x, y, 0, 0, dir,
                           crop, crop, score,
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
              pStates[k] = pStates[k].clone("score", 0);
              int TIME_BETWEEN_ROUNDS_JUST_TEMPORARY = 1000 / SimulationUtils.TIMESTEP;
              pStates[k] = pStates[k].clone("untilRespawn", TIME_BETWEEN_ROUNDS_JUST_TEMPORARY);
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
        
        for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j) { 
 /* ----------------------  "main" loop for each player  --------------------- */ 

          Ship ship;
          Tail tail;
          boolean spendPathOnBullet = false;
          boolean scored = false;
          boolean crossedOtherPath = false;
          boolean formedLoop = false;
          
          if (pStates[j].untilRespawn > 0) {  // still waiting
            assert(!pStates[j].tail.isEmpty());
            
            int untilRespawn = pStates[j].untilRespawn-1;
            int cropFrom = pStates[j].tail.lengthFromLastKnotToTime(untilRespawn*SimulationUtils.UNCOILSPEED);
            int cropTo = game.time + 1; // +1 because crop() does not remove "cropTo itself"
            
            Point2D newLocation = pStates[j].tail.getPointFromTime(cropFrom);
            if (newLocation == null) newLocation = pStates[j].ship.location();
              Angle newDir = pStates[j].tail.tangentAngleHead();
            if (newDir == null) newDir = pStates[j].ship.dir();
              ship = new Ship(newLocation, 0, 0, newDir);

            tail = pStates[j].tail.crop(cropFrom, cropTo);
            
            

            if (untilRespawn == 0) // already decremented
              tail.addPoint(ship.location(), game.time + 1);
           
            
            pStates[j] = new PlayerState(j, ship, 
                cropFrom, cropTo, pStates[j].score,
                Math.max(0, pStates[j].untilReload-1),
                untilRespawn,
                pStates[j].sinceScoring+1,
                pStates[j].sinceLooping+1,
                pStates[j].sinceCrossing+1,
                pStates[j].sinceHitByBullet+1,
                pStates[j].sincePwned+1,
                pStates[j].sinceKnot+1,
                tail);
            continue;
          }
          
          // back to the normal loop for non-respawning players
          
          
          
          // Take shots first:          
          if (params[j].shoot && SimulationUtils.canShoot(pStates[j])) {
            // if players had enough time to reload and enough path to spend
            bullets.add(new Bullet(j, pStates[j].ship.x(), pStates[j].ship.y(), pStates[j].ship.dir()));
            spendPathOnBullet = true;

          }

          // Calculate where the player moved.
          ship = SimulationUtils.nextPosition(pStates[j].ship, params[j]);

          // Add the new position to the player's tail
          tail = pStates[j].tail.addPoint(ship.location(), game.time + 1);

          // Check if the paths cross each other, O(N).
          // Remove all paths that are crossed. Also checks if players scored.          
          Tail loop = null;
          
          // Check if any player (including j) is crossing j's path
          for (int i=0; i<SimulationUtils.NUMPLAYERS; ++i) {            
            // See comments in the intersects() function for more detail on this BS
            Tail intersection = pStates[i].tail.intersects(tail, (i==j)?2:0);

            if (intersection != null) {  // if an intersection occurred

              if (i != j) {
                crossedOtherPath = true;
                
              } else {
                formedLoop = true;
                loop = intersection;

                assert(pStates[j].score < arena.sequence().length);

                // Check for scoring
                for (Cluster cNotSS : arena.clusters) {
                  ClusterServerSide c = (ClusterServerSide) cNotSS;
                  
                  // Check if the center of the cluster is in the loop just made
                  if (c.acceptable(arena.sequence()[pStates[j].score]) && intersection.contains(c.centerX(), c.centerY())) {
                    Logger.println(Logger.RepetitionRate.INFREQUENT, "Player " + j + " encircled (" + c.centerX() + ", " + c.centerY() + ").");

                    scored = true;

                    // Store the vote in a file for post-processing
                    // Currently stores the vote for all clusters in the loop that
                    // *could* have been accepted, since we don't know which cluster in particular
                    // the player was going for.
                    clusterDB.writeData(c.imgName(), pStates[j].score+1, "Mike R");
                  }
                }

                // Check if the other player got pwned.
                for (int k=0; k<SimulationUtils.NUMPLAYERS; ++k) {
                  if (k==j) continue;

                  if (intersection.contains(pStates[k].ship.x(), pStates[k].ship.y())) {
                    gotPwned[k] = true; // set score to 0 if you are enclosed by the other player!!!!
                  }
                }
              }
            }
          }
          
          int  cropFrom, cropTo;
          if (crossedOtherPath) {
            // if your crossed another player's path
            cropFrom = pStates[j].tail.baseTime();
            cropTo = tail.cropToForCrossing();
            tail = tail.crop(cropFrom, cropTo);
          } else if (loop != null) {
            // if you completed a loop
            cropFrom = loop.baseTime();
            cropTo = game.time + 1; 
            tail = tail.crop(cropFrom, cropTo);
          } else if (spendPathOnBullet) {
            // if you spend some path on a shot
            cropFrom    = pStates[j].tail.baseTime();
            cropTo      = pStates[j].tail.lengthFromBaseToTime(SimulationUtils.BULLETCOST) + 1; // +1 because cropping excludes the "to"
            tail = tail.crop(cropFrom, cropTo);
          } else {
            // normal time step -- send the previous values
            cropFrom = pStates[j].cropTailFrom;
            cropTo = pStates[j].cropTailTo;
          }
          // end dealing with moving around and loops and stuff
          
          if (scored)
            tail = tail.addKnotAtHead();
          

          // Calculate the new positions for the bullets and figure out
          // if anyone got hit BY A BULLET.
          boolean gotShot = false;
          for (int k = bullets.size() - 1; k >= 0; --k) {
            Bullet b = SimulationUtils.nextBulletPosition(bullets.get(k));

            // Whether the bullet hit something.
            boolean remove = !SimulationUtils.isInArena(b, arena);

            // if b does not belong to player j and the players ship contains b
            if (SimulationUtils.gotShot(pStates[j], b)) {
              remove = true;
              gotShot = true;              
            }  // end if


            // Update the bullet position unless it hit something.
            if (!remove)
              bullets.set(k, b);
            else
              bullets.remove(k);

          }  // end loop through bullets
          
        
          // Check if anyone went out of bounds or hit a cluster:
          boolean crashed = !SimulationUtils.isInArena(pStates[j].ship, arena);

          //  SET THE PLAYER STATE TO ALL THE NEW PARAMETERS
          //  RESULTING FROM THIS TIME STEP
          pStates[j] = new PlayerState(j, ship,
              cropFrom, cropTo,
              (scored)? pStates[j].score+1 : pStates[j].score,
              (spendPathOnBullet)? SimulationUtils.RELOADTIME : Math.max(0, pStates[j].untilReload-1),
              (crashed||gotShot)? SimulationUtils.timeUntilRespawn(tail) : 0,
              (scored)? 0 : pStates[j].sinceScoring+1,
              (formedLoop)? 0 : pStates[j].sinceLooping+1,
              (crossedOtherPath)? 0 : pStates[j].sinceCrossing+1,
              (gotShot)? 0 : pStates[j].sinceHitByBullet+1,
              pStates[j].sincePwned+1,
              (scored)? 0 : pStates[j].sinceKnot+1,
              tail);
        } 
/* ----------------- end of "main" loop for each player --------------------- */
        
        // Needs to be outside main loop because loop on j checks
        // if player j pwned someone else, not if player j got pwned.
        for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j) {
          if (gotPwned[j]) {
            pStates[j] = pStates[j].clone("score", 0);
            pStates[j] = pStates[j].clone("sincePwned", 0);
          }
        }

        // UPDATE THE GAME STATE WITH THE NEW PLAYER STATES
        GameState newGState = new GameState(game.time + 1, pStates, bullets);
        
        if (VERIFYSERIALIZATION) {
          // for debugging
          ByteArrayOutputStream out = new ByteArrayOutputStream();
          newGState.serialize(out);
          
          ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
          GameState parsedGState = GameState.parse(new DataInputStream(in), this.game);          
          parsedGState.assertDeepEquals(newGState);
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

