package aoi;

 /**
 *  This class decides what to do based on (the
 *  history of) the game state.
 *
 *  This implementation of Intelligence does
 *  not rely on any human input.
 *
 */
public class Nonhuman extends DefaultIntelligence {

  private int acc = SimulationUtils.MAXACC / 2;
  private Angle dir = new Angle(0);
  
  
  public PlayerControlParameters think(GameState gState) {

    double bestV, V, currentV;     // define a potential function V for decision making
    PlayerControlParameters bestCtrl, testCtrl;
        
    
    
    currentV = potentialFunction(gState.pStates[ClientConfiguration.playerNo].ship);
    bestV = Double.POSITIVE_INFINITY;
    bestCtrl = null;

    // Try different directions.
    for (int j=-1; j<=1; ++j) {

      dir = gState.pStates[ClientConfiguration.playerNo].ship.dir().add(j*Math.PI/3);

      // The candidate set of control parameters.
      testCtrl = new PlayerControlParameters(gState.time, acc, dir,
          false, false);

      Ship intPos = SimulationUtils.nextPosition(
          gState.pStates[ClientConfiguration.playerNo].ship, testCtrl);

      V = Double.NEGATIVE_INFINITY;

      // For this given direction candidate, look ahead 1000 time 
      // steps and take the maximum of the potential along the path.
      for (int k=1; k < 1000/SimulationUtils.TIMESTEP; ++k) {

        intPos = SimulationUtils.nextPosition(intPos, testCtrl);

        V = Math.max(V, potentialFunction(intPos));
      }

      // Prefer going straight
      V += Math.abs(j) * 0.05;

      if (V < bestV) {
        bestCtrl = testCtrl;
        bestV = V;
      }

    }


    if (bestV < currentV)
      acc = SimulationUtils.MAXACC;
    else {
      acc = (int) ((10. - Math.max(1., Math.min(5., bestV))) / 9.
          * SimulationUtils.MAXACC);
      //Logger.println(Logger.RepetitionRate.FREQUENT, bestV + "   " + (int) ((25. - Math.max(5., Math.min(20., bestV))) / 20));
    }


    // Use automatic shooter
    if (Shooter.wouldHit(gState, ClientConfiguration.playerNo))
      bestCtrl = bestCtrl.shoot();


    return bestCtrl;
  }

  /* 
   * Evaluate the desirability of a given position (x, y)
   * on the arena.
  */
  private static double potentialFunction(Ship s) {
    double V = 0;

    double range = 500 / SimulationUtils.TIMESTEP
      * Math.sqrt( Math.pow(s.vx(), 2)
                   + Math.pow(s.vy(), 2));

    range = Math.max(range, SimulationUtils.ARENASIZE/20);

    // Repuslive terms pushing you away from the arena boundaries
    V += Math.exp((Math.abs(s.x()) - SimulationUtils.ARENASIZE/2) / (SimulationUtils.ARENASIZE/50));
    V += Math.exp((Math.abs(s.y()) - SimulationUtils.ARENASIZE/2) / (SimulationUtils.ARENASIZE/50));

    for (Cluster clust : Arena.current().clusters) {
      V += -truncatedLog(s.location().distance(clust.center().x, clust.center().y + clust.height()/2), range);
      V += -truncatedLog(s.location().distance(clust.center().x, clust.center().y - clust.height()/2), range);
      V += -truncatedLog(s.location().distance(clust.center().x + clust.width()/2, clust.center().y), range);
      V += -truncatedLog(s.location().distance(clust.center().x + clust.width()/2, clust.center().y), range);
    }

    return V;
  }

//  private double truncatedExp(double x, double range) {
//    if (Math.abs(x) > 3*range) return 0.0;
//
//    return Math.exp(-Math.abs(x/range));
//  }

  private static double truncatedLog(double x, double range) {
    if (Math.abs(x) > range) return 0.0;

    return Math.log(Math.abs(x/range));
  }

}
