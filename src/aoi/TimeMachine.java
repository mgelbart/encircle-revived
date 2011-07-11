package aoi;

import java.util.Vector;

/**
 * A class for storing the history of the game
 * state as well as predicting the future.
 */ 
public class TimeMachine {

  // Definitive game states that have been received from the server.
  private final Vector<GameState> states;

  // Commands by the player.
  private final Vector<PlayerControlParameters> ownParams;

  // Number of game states to store in history.
  private final static int STATESSIZE = 5;

  public TimeMachine() {
    states = new Vector<GameState>(STATESSIZE + 1);
    ownParams = new Vector<PlayerControlParameters>();
  }


  // Call this whenever a new game state is
  // received from the server.
  public void processStateUpdate(GameState g) {

    synchronized (states) {
	    
      final int N = states.size();

      int i = 0;
      while (i < N && g.compareTo(states.get(i)) > 0) i++;

      assert(i <= states.size());

      // Don't add duplicates.
      if (i < N && g == states.get(i)) return;

      states.insertElementAt(g, i);

      //System.err.println("New state for t=" + g.time + " inserted at i=" + i);

      if (N+1 > STATESSIZE)
        states.removeElementAt(0);

    }

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
    public GameState getState(int time, MindReader reader) {	

    if (states.size() == 0) return null;

    // Don't extrapolate into the history. Should just store
    // more states if they are needed.
    if (time - states.get(0).time < 0) return null;

    int i = 0;
    GameState last;

    synchronized (states) {

      final int N = states.size();

      while (i < N && time - states.get(i).time > 0) i++;

      assert(i <= N);

      if (i < N-1 || (i == N-1 && states.get(i).time == time)) {
        // TODO: might want to interpolate in
        // case there are holes in the history.
        return states.get(i);
      }

      // Otherwise need to extrapolate into the future.
      // Start from the last authorative state.
      last = states.lastElement();
    }




    // The extrapolated states for each player.
    Ship[] ship = new Ship[SimulationUtils.NUMPLAYERS];
    PlayerState[] players = new PlayerState[SimulationUtils.NUMPLAYERS];


    // Need to predict the future.
    // Note: this _only_ extrapolates position. Doesn't check for crashing
    //       into stuff or scoring. If we want those, we should include
    //       them in SimulationUtils.
    for (int k=0; k < SimulationUtils.NUMPLAYERS; ++k) {

      // Start from the last known state.
      ship[k] = last.states[k].ship;

      // Extrapolate (time-last.time) steps.
      for (int j=0; j < time - last.time; ++j) {
        ship[k] = SimulationUtils.nextPosition(ship[k],
                                              getParamsAt(k, last.time+j, reader));
      }

      players[k] = new PlayerState(k, ship[k],
                                   last.states[k].energy,
                                   last.states[k].tailStarted,
                                   last.states[k].tailStarted,
                                   last.states[k].tailStarted,
                                   last.states[k].score,
                                   last.states[k].respawning,
                                   last.states[k].tail.addPoint(ship[k].x(), ship[k].y(), time)); // TODO: should actually do addPoints() with all the intermediate points
	    
    }


    Vector<Bullet> bullets = (Vector<Bullet>) (last.bullets.clone());
    int[] bulPos = new int[SimulationUtils.nextBulletPosition(null).length];
    Bullet b;
//    int dir;

    for (int k=0; k < bullets.size(); ++k) {
      b = bullets.get(k);

      bulPos = SimulationUtils.nextBulletPosition(b);
//      dir = b.direction;

      for (int j=1; j < time - last.time; ++j) {
        bulPos = SimulationUtils.nextBulletPosition(new Bullet(b.playerNo, bulPos[0], bulPos[1], b.direction));
      }

      bullets.set(k, new Bullet(b.playerNo, bulPos[0], bulPos[1], b.direction));
    }


    return new GameState(time, players, bullets);

  }


  // Get the control parameters of player number playerNo at the given time.
  private PlayerControlParameters getParamsAt(int playerNo,
                                              int time,
                                              MindReader reader) {
    if (playerNo == ClientConfiguration.playerNo) return getOwnParamsAt(time);

    PlayerControlParameters params = null;
    if (reader != null) params = reader.paramsAt(time, playerNo);
    if (params != null) return params;

    //System.err.println("Couldn't get prediction from MindReader for player " + playerNo + " at t=" + time + ".");

    // Fall back to assuming motion in current direction (unless at standstill).
    assert(states.size() > 0);
    PlayerState last = states.lastElement().states[ClientConfiguration.playerNo];
    if (Math.abs(last.ship.vx()) + Math.abs(last.ship.vy()) > SimulationUtils.MAXACC/2)
      return new PlayerControlParameters(time, SimulationUtils.INTMAX, last.ship.dir(), 0, 0);
    else
      return new PlayerControlParameters(time, SimulationUtils.INTMAX, last.ship.dir(), 0, 0);
  }


  // Get the control parameters of the local player at the given time.
  private PlayerControlParameters getOwnParamsAt(int time) {

    synchronized (ownParams) {
      final int N = ownParams.size();

      int i = 0;
      while (i < N && ownParams.get(i).effectiveOn - time <= 0) i++;

      /*
        if (i>0 && ownParams.get(i-1).effectiveOn - time < -2) {
        System.err.println("getOwnParamsAt(" + time + ")");
        System.err.println("effectiveOn: " + ownParams.get(i-1).effectiveOn);
        }
      */

      if (i > 0) return ownParams.get(i-1);

      // This should never happen under normal circumstances except when
      // the game is just starting and the player hasn't sent any commands.
      // Fall back to assuming motion in current direction.
      //System.err.println("Couldn't get own parameters at t=" + time + ".");
      assert(states.size() > 0);
      return new PlayerControlParameters(time, 0, states.lastElement().states[ClientConfiguration.playerNo].ship.dir(), 0, 0);

    }
  }

}
