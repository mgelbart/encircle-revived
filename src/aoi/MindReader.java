package aoi;

import java.util.Vector;

/**
 * A class that figures out what the other
 * player is going to do.
 *
 * This could very much benefit from human
 * input!
 */
public class MindReader {

  // Definitive game states that have been received from the server.
  private final Vector<GameState> states;

  // Number of game states to store in history.
  private final static int STATESSIZE = 3;

  public MindReader() {
    states = new Vector<GameState>(STATESSIZE + 1);
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
      if (i < N && g.time == states.get(i).time) return;

      states.insertElementAt(g, i);

      if (N+1 > STATESSIZE)
        states.removeElementAt(0);
    }

  }


  /*
    Return the best guess at the parameters
    of the player at the given time.
  */
  public PlayerControlParameters paramsAt(int time, int playerNo) {

    assert(playerNo != ClientConfiguration.playerNo); // You don't need to guess your own parameters.

    final int P = playerNo;

    GameState s0, s1;

    synchronized (states) {

      final int N = states.size();

      if (N == 0) return null;

      if (N < 3) return new PlayerControlParameters(time, SimulationUtils.INTMAX, states.get(N-1).states[P].ship.dir(), 0, 0);

      s0 = states.get(N-2);
      s1 = states.get(N-1);

    }

    // Change in velocity PER TIME STEP.
    double dvx = s1.states[P].ship.vx() - s0.states[P].ship.vx() / (double) (s1.time - s0.time);
    double dvy = s1.states[P].ship.vy() - s0.states[P].ship.vy() / (double) (s1.time - s0.time);

    // What we'd expect with zero thrust:
    double dragdvx = -SimulationUtils.DRAG * s1.states[P].ship.vx();
    double dragdvy = -SimulationUtils.DRAG * s1.states[P].ship.vy();

    double a = Math.sqrt(Math.pow(dvx-dragdvx, 2) + Math.pow(dvy-dragdvy, 2));

    return new PlayerControlParameters(time,
                                       (int) (Math.min(SimulationUtils.INTMAX, ((long) SimulationUtils.INTMAX) * (a / SimulationUtils.MAXACC))),
                                       s1.states[P].ship.dir(),
                                       0, 0);
  }

}
