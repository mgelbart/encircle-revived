package aoi;

import java.util.Vector;

/**
 * A class for storing the history of the game
 * state as well as predicting the future.
 */ 
public class TimeMachine {

  // Definitive game states that have been received from the server.
  private final Vector<GameState> states;

  // Cached future game states
  private final Vector<GameState> futureStates;
  
  // Commands by the player.
  private final Vector<PlayerControlParameters> ownParams;

  // Number of game states to store in history.
  private final static int STATESSIZE = 5;

  public TimeMachine() {
    states = new Vector<GameState>(STATESSIZE + 1);
    futureStates = new Vector<GameState>();
    ownParams = new Vector<PlayerControlParameters>();
  }


  // Call this whenever a new game state is
  // received from the server.
  public synchronized void processStateUpdate(GameState g) {

    final int N = states.size();

    int i = 0;
    while (i < N && g.compareTo(states.get(i)) > 0) i++;

    assert(i <= states.size());

    // Don't add duplicates.
    if (i < N && g == states.get(i)) return;

    states.insertElementAt(g, i);

    //Logger.println(Logger.RepetitionRate.FREQUENT, "New state for t=" + g.time + " inserted at i=" + i);

    if (N+1 > STATESSIZE)
      states.removeElementAt(0);

    // empty cached future states since we now have better information available.
    // TODO: it might make sense to do this less frequently.
    futureStates.clear();

  }


  // Call this whenever sending new control
  // parameters to the server.
  public void processParamsUpdate(PlayerControlParameters p) {

    synchronized (ownParams) {

      ownParams.add(p);
      java.util.Collections.sort(ownParams);

      // Prune old control params.
      while (ownParams.size() > 2 && ownParams.get(1).effectiveOn - states.lastElement().time < 0)
        ownParams.removeElementAt(0);

    }

  }


  // Return the latest game state received from the server.
  public GameState getAuthorativeState() {

    if (states.size() == 0) return null;

    return states.lastElement();

  }


  // Get the GameState at the given time.
  @SuppressWarnings("unchecked")
  public synchronized GameState getState(int time, MindReader reader) {	

    if (states.size() == 0) return null;

    // Don't extrapolate into the history. Should just store
    // more states if they are needed.
    if (time - states.get(0).time < 0) return null;


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
    if (time < last.time)  return getPastState(time);
    if (time == last.time) return last;
    
    // look in cached future states
    final GameState cached = getCachedState(time);
    if (cached.time == time) return cached;

    
    /* 
     * Otherwise need to extrapolate into the future.
     */
    
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
    assert(time - cached.time > 0);
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
          bullets.set(k, SimulationUtils.nextBulletPosition(b));
        else
          bullets.remove(k);  
      }
      
      futureStates.add(new GameState(time, players, bullets));
    }


    return futureStates.lastElement();

  }
  
  private synchronized GameState getPastState(int time) {
    // Do a linear search on the history keeping in mind
    // that there may be holes in the history.
    final int N = states.size();

    int i = 0;
    while (i < N && time - states.get(i).time > 0) i++;

    assert(i <= N);
    return states.get(i);
  }
  
  /*
   * Return the cached GameState for the given time or, if such
   * a state doesn't exist in the cache, the last cached state.
   */
  private synchronized GameState getCachedState(int time) {
    // Do a linear search in the cache.
    for (GameState g : futureStates) if (g.time == time) return g;
    return (futureStates.isEmpty())? getAuthorativeState() : futureStates.lastElement();
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
    PlayerState last = states.lastElement().pStates[ClientConfiguration.playerNo];
    if (Math.abs(last.ship.vx()) + Math.abs(last.ship.vy()) > SimulationUtils.MAXACC/2)
      return new PlayerControlParameters(time, SimulationUtils.MAXACC, last.ship.dir(), false, false);
    else
      return new PlayerControlParameters(time, 0, last.ship.dir(), false, false);
  }


  // Get the control parameters of the local player at the given time.
  private PlayerControlParameters getOwnParamsAt(int time) {

    synchronized (ownParams) {
      final int N = ownParams.size();

      int i = 0;
      while (i < N && ownParams.get(i).effectiveOn - time <= 0) i++;

      /*
        if (i>0 && ownParams.get(i-1).effectiveOn - time < -2) {
        Logger.println(Logger.RepetitionRate.FREQUENT, "getOwnParamsAt(" + time + ")");
        Logger.println(Logger.RepetitionRate.FREQUENT, "effectiveOn: " + ownParams.get(i-1).effectiveOn);
        }
      */

      if (i > 0) return ownParams.get(i-1);

      // This should never happen under normal circumstances except when
      // the game is just starting and the player hasn't sent any commands.
      // Fall back to assuming motion in current direction.
      Logger.println(Logger.Rep.FEW, "Couldn't get own parameters at t=" + time + ".");
      assert(states.size() > 0);
      return new PlayerControlParameters(time, 0, states.lastElement().pStates[ClientConfiguration.playerNo].ship.dir(), false, false);

    }
  }

}
