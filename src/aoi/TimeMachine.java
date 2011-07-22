package aoi;

import java.util.TreeSet;
import java.util.Vector;

import aoi.Logger.Rep;

/**
 * A class for storing the history of the game
 * state as well as predicting the future.
 */ 
public class TimeMachine {

  // Definitive game states that have been received from the server.
  private final TreeSet<GameState> states;

  // Cached future game states
  private final TreeSet<GameState> futureStates;
  
  // Commands by the player.
  private final TreeSet<PlayerControlParameters> ownParams;

  // Number of game states to store in history.
  private final static int STATESSIZE = 5;

  // A client will typically only instantiate one TimeMachine so
  // this is provided for convenience.
  private static TimeMachine current;
  public static void setCurrent(TimeMachine tm) { current = tm; }
  public static TimeMachine current() { return current; }
  
  public TimeMachine() {
    states = new TreeSet<GameState>();
    futureStates = new TreeSet<GameState>();
    ownParams = new TreeSet<PlayerControlParameters>();
  }


  // Call this whenever a new game state is
  // received from the server.
  public synchronized void processStateUpdate(GameState g) {

    states.add(g);
    
    // empty cached future states since we now have better information available.
    // TODO: it might make sense to do this less frequently.
    futureStates.clear();

  }


  // Call this whenever sending new control
  // parameters to the server.
  public void processParamsUpdate(PlayerControlParameters p) {

    synchronized (ownParams) {

      ownParams.remove(p); // replace the old one, if any.
      ownParams.add(p);

    }

  }


  // Return the latest game state received from the server.
  public GameState getAuthorativeState() {

    if (states.size() == 0) return null;

    return states.last();

  }


  // Get the GameState at the given time.
  @SuppressWarnings("unchecked")
  public synchronized GameState getState(int time, MindReader reader) {	

    if (states.size() == 0) return null;

    // Don't extrapolate into the history. Should just store
    // more states if they are needed.
    if (time - states.first().time < 0) return null;


    /* 
     * First figure out if this is:
     *   - a buffered received state
     *   - or cached future state
     *   - or if we need to extrapolate into the future.
     *   
     */

    // last authorative state
    final GameState last = getAuthorativeState();

    // past or current state
    if (time - last.time < 0) return getPastState(time);
    if (time == last.time)    return last;
    
    // look in cached future states
    final GameState cached = getCachedState(time);
    if (cached.time == time) return cached;

    
    /* 
     * Otherwise need to extrapolate into the future.
     */
    if (time - cached.time <= 0) Logger.println(Rep.FEW, time - cached.time);
    assert(time - cached.time > 0);
    
    // The extrapolated states for each player and bullet.
    Ship[] ship = new Ship[SimulationUtils.NUMPLAYERS];
    PlayerState[] players = new PlayerState[SimulationUtils.NUMPLAYERS];
    Vector<Bullet> bullets = (Vector<Bullet>) (cached.bullets.clone());
    PlayerControlParameters[] ctrl = new PlayerControlParameters[SimulationUtils.NUMPLAYERS];
    PlayerControlParameters[] prevCtrl = new PlayerControlParameters[SimulationUtils.NUMPLAYERS];

    // Start from the last known values.
    for (int k=0; k < SimulationUtils.NUMPLAYERS; ++k) {
      players[k] = cached.pStates[k];
      ship[k] = cached.pStates[k].ship;
      ctrl[k] = getParamsAt(k, cached.time-1, reader);
    }
    
    // Predict how the states will evolve during each remaining (time-cached.time) time step
    for (int j=0; j < time - cached.time; ++j) {
      
      // Player states
      for (int k=0; k < SimulationUtils.NUMPLAYERS; ++k) {
        prevCtrl[k] = ctrl[k];
        ctrl[k] = getParamsAt(k, cached.time+j, reader);
        ship[k] = (cached.pStates[k].untilRespawn == 0)? SimulationUtils.nextPosition(ship[k], ctrl[k]) : ship[k];
        
        boolean tookShot = false;
        // Take shots
        if (ctrl[k].shoot && SimulationUtils.canShoot(players[k])) {
          bullets.add(new Bullet(k, ship[k].x(), ship[k].y(), ship[k].dir()));
          tookShot = true;
        }
        
        players[k] = new PlayerState(k, ship[k],
                                      players[k].cropEvents,
                                      players[k].score,
                                      (tookShot)? SimulationUtils.RELOADTIME : Math.max(0, players[k].untilReload-1),
                                      Math.max(0, players[k].untilRespawn-1),
                                      players[k].sinceScoring+1,
                                      players[k].sinceLooping+1,
                                      players[k].sinceCrossing+1,
                                      players[k].sinceHitByBullet+1,
                                      players[k].sincePwned+1,
                                      players[k].tail.addPoint(ship[k].location(), time));
      
      }

      for (int k=bullets.size()-1; k >= 0; --k) {
        Bullet b = bullets.get(k);
        
        if (SimulationUtils.isInArena(b, Arena.current()))
          bullets.set(k, SimulationUtils.nextPosition(b));
        else
          bullets.remove(k);  
      }
      
      futureStates.add(new GameState(cached.time+1+j, players, bullets));
    }

    return futureStates.last();

  }
  
  private synchronized GameState getPastState(int time) {
    // Do a linear search on the history keeping in mind
    // that there may be holes in the history.
    for (GameState g : states) if (time - g.time <= 0) return g;
    return states.last();
  }
  
  /*
   * Return the cached GameState for the given time or, if such
   * a state doesn't exist in the cache, the last cached state.
   */
  private synchronized GameState getCachedState(int time) {
    // Do a linear search in the cache.
    for (GameState g : futureStates) if (g.time == time) return g;
    return (futureStates.isEmpty())? states.last() : futureStates.last();
  }


  // Get the control parameters of player number playerNo at the given time.
  private PlayerControlParameters getParamsAt(int playerNo,
                                              int time,
                                              MindReader reader) {
    if (playerNo == ClientConfiguration.playerNo) return getOwnParamsAt(time);

    PlayerControlParameters params = null;
    if (reader != null) params = reader.paramsAt(time, playerNo);
    if (params != null) return params;

    //Logger.println(Logger.RepetitionRate.FEW, "Couldn't get prediction from MindReader for player " + playerNo + " at t=" + time + ".");

    // Fall back to assuming motion in current direction (unless at standstill).
    assert(states.size() > 0);
    PlayerState last = states.last().pStates[ClientConfiguration.playerNo];
    if (Math.abs(last.ship.vx()) + Math.abs(last.ship.vy()) > SimulationUtils.MAXACC/2)
      return new PlayerControlParameters(time, SimulationUtils.MAXACC, last.ship.dir(), false, false);
    else
      return new PlayerControlParameters(time, 0, last.ship.dir(), false, false);
  }


  // Get the control parameters of the local player at the given time.
  private PlayerControlParameters getOwnParamsAt(int time) {

    synchronized (ownParams) {
      PlayerControlParameters candidate = null;
      for (PlayerControlParameters p : ownParams) {
        if (p.effectiveOn == time) return p;
        if (p.effectiveOn - time > 0) break;
        candidate = p;
      }

      if (candidate != null) return candidate;

      // This should never happen under normal circumstances except when
      // the game is just starting and the player hasn't sent any commands.
      // Fall back to assuming motion in current direction.
      Logger.println(Logger.Rep.FEW, "Couldn't get own parameters at t=" + time + ".");
      assert(states.size() > 0);
      return new PlayerControlParameters(time, 0, states.last().pStates[ClientConfiguration.playerNo].ship.dir(), false, false);

    }
  }

}
