package aoi;

/**
 * A class that figures out what the other
 * player is going to do.
 *
 * This could very much benefit from human
 * input!
 */
public class MindReader {

  private final TimeMachine timemachine;
  
  public MindReader(TimeMachine timemachine) {
    this.timemachine = timemachine;
  }


  /*
    Return the best guess at the parameters
    of the player at the given time.
  */
  public PlayerControlParameters paramsAt(int time, int playerNo) {

    assert(playerNo != ClientConfiguration.playerNo); // You don't need to guess your own parameters.

    final int P = playerNo;

    /* Use the latest states received from the server to get a first order approximation for the acceleration. */
    GameState s1 = timemachine.getAuthorativeState();
    GameState s0 = timemachine.getState(s1.time-1, null);

    // special cases for the very beginnin of the game.
    if (s1 == null) return null;
    if (s0 == null) return new PlayerControlParameters(time, SimulationUtils.MAXACC, s1.pStates[P].ship.dir(), false, false);

    // Change in velocity PER TIME STEP.
    double dvx = s1.pStates[P].ship.vx() - s0.pStates[P].ship.vx() / (double) (s1.time - s0.time);
    double dvy = s1.pStates[P].ship.vy() - s0.pStates[P].ship.vy() / (double) (s1.time - s0.time);

    // What we'd expect with zero thrust:
    double dragdvx = -SimulationUtils.DRAG * s1.pStates[P].ship.vx();
    double dragdvy = -SimulationUtils.DRAG * s1.pStates[P].ship.vy();

    double a = Math.sqrt(Math.pow(dvx-dragdvx, 2) + Math.pow(dvy-dragdvy, 2));

    return new PlayerControlParameters(time,
                                       (int) (Math.min(SimulationUtils.MAXACC, a)),
                                       s1.pStates[P].ship.dir(),
                                       false, false);
  }

}
