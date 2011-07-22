package aoi;

import java.awt.Point;
import java.awt.geom.Point2D;

 /**
 *  This class decides what to do based on (the
 *  history of) the game state.
 *
 *  This implementation of Intelligence does
 *  not rely on any human input.
 *
 */
public class Nonhuman implements Intelligence {

  // A queue of latest game states.
  GameState[] games;

  // The control parameters to be sent to the server.
  PlayerControlParameters ctrl = null;
  PlayerControlParameters prevCtrl = null;

  // Last time ctrl was changed.
  int lastAction = -999999;

  // The main thread that runs think() in an infinite loop.
  Thread intelThread;

  public Nonhuman() {

    Logger.println(Logger.Rep.FEW, "Non-human intelligence for player " + ClientConfiguration.playerNo + " created.");

    games = new GameState[1];

    intelThread = new Thread(this, "intel");
    intelThread.start();
  }

  public void run() {
    think();
  }

  /* Store the latest game state in a queue. */
  public void processGameStateUpdate(GameState game) {
    games[0] = game; /* Our queue consists of one element! */

    intelThread.interrupt();
  }


  public PlayerControlParameters[] getNewControlParameters() {
    if (ctrl != prevCtrl) {
      prevCtrl = ctrl;
      return new PlayerControlParameters[]{ctrl};
    }

    return new PlayerControlParameters[]{};
  }

  public void think() {

    int acc;
    Angle dir;
    boolean shoot, boost;

    acc = SimulationUtils.MAXACC / 2;

    while (true) {
	    
      try {
        Thread.sleep(1000); // Sleep when there's nothing better to do.
      } catch (InterruptedException e) {
			
        // Shoot if appropriate, i.e., if your path is not too long and another player is in range
        double MAX_LOOP_TO_SHOOT = 20000/SimulationUtils.TIMESTEP;
        double MAX_ANGLE_TO_SHOOT = 0.1;
        shoot = false;
        if (Math.abs(games[0].time - games[0].pStates[ClientConfiguration.playerNo].tail.baseTime()) < MAX_LOOP_TO_SHOOT) { 
          if (prevCtrl != null) {
            Ship ComparedSayWithPET = SimulationUtils.nextPosition(
                games[0].pStates[ClientConfiguration.playerNo].ship,
                new PlayerControlParameters(prevCtrl.effectiveOn, 
                    SimulationUtils.MAXACC/10, prevCtrl.targetDirection, false, false));
            
            for (int playerNos = 0; playerNos < SimulationUtils.NUMPLAYERS; playerNos++) {
              if (playerNos == ClientConfiguration.playerNo) continue; // Skip yourself
              
              double theangle = SimulationUtils.angle(
                  games[0].pStates[playerNos].ship.x(), 
                  games[0].pStates[playerNos].ship.y(), 
                  games[0].pStates[ClientConfiguration.playerNo].ship.x(), 
                  games[0].pStates[ClientConfiguration.playerNo].ship.y(), 
                  ComparedSayWithPET.x(), ComparedSayWithPET.y());
              
              // Logger.println(Logger.RepetitionRate.FREQUENT, theangle + " /    " + ComparedSayWithPET[0] + " " + ComparedSayWithPET[1]);
              if (theangle < MAX_ANGLE_TO_SHOOT) {
                shoot = true;
              }
            }
          }
        }
        boost = false;


        // define a potential function V for decision making
        double bestV, V, currentV;
        PlayerControlParameters bestCtrl, testCtrl;


        currentV = potentialFunction(games[0].pStates[ClientConfiguration.playerNo].ship.x(),
                                     games[0].pStates[ClientConfiguration.playerNo].ship.y());

        bestV = Double.POSITIVE_INFINITY;
        bestCtrl = null;

        // Try different directions.
        for (int j=-1; j<=1; ++j) {

          dir = games[0].pStates[ClientConfiguration.playerNo].ship.dir().add(j*Math.PI/3);
        
          // The candidate set of control parameters.
          testCtrl = new PlayerControlParameters(games[0].time, acc, dir,
                                                 shoot, boost);

          Ship intPos = SimulationUtils.nextPosition(
              games[0].pStates[ClientConfiguration.playerNo].ship, testCtrl);

          V = Double.NEGATIVE_INFINITY;

          // For this given direction candidate, look ahead 1000 time 
          // steps and take the maximum of the potential along the path.
          for (int k=1; k < 1000/SimulationUtils.TIMESTEP; ++k) {

            intPos = SimulationUtils.nextPosition(intPos, testCtrl);

            V = Math.max(V, potentialFunction(intPos.x(), intPos.y()));
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
        // if (Math.random() < .9) acc=0;
        //Logger.println(Logger.RepetitionRate.FREQUENT, "acc: " + acc);

        lastAction = games[0].time;

        ctrl = bestCtrl;
      }

    }

  }

  /* Evaluate the desirability of a given position (x, y)
     on the arena.
  */
  private double potentialFunction(int x, int y) {
    double V = 0;

    Point2D loc = new Point(x, y);

    double range = 500 / SimulationUtils.TIMESTEP
      * Math.sqrt( Math.pow(games[0].pStates[ClientConfiguration.playerNo].ship.vx(), 2)
                   + Math.pow(games[0].pStates[ClientConfiguration.playerNo].ship.vy(), 2));

    range = Math.max(range, SimulationUtils.ARENASIZE/20);

    // Repuslive terms pushing you away from the arena boundaries
    V += Math.exp((Math.abs(x) - SimulationUtils.ARENASIZE/2) / (SimulationUtils.ARENASIZE/50));
    V += Math.exp((Math.abs(y) - SimulationUtils.ARENASIZE/2) / (SimulationUtils.ARENASIZE/50));

    for (Cluster clust : Arena.current().clusters) {

      V += -truncatedLog(loc.distance(clust.centerX(), clust.centerY() + clust.height()/2), range);
      V += -truncatedLog(loc.distance(clust.centerX(), clust.centerY() - clust.height()/2), range);
      V += -truncatedLog(loc.distance(clust.centerX() + clust.width()/2, clust.centerY()), range);
      V += -truncatedLog(loc.distance(clust.centerX() + clust.width()/2, clust.centerY()), range);

    }

    return V;
  }

//  private double truncatedExp(double x, double range) {
//    if (Math.abs(x) > 3*range) return 0.0;
//
//    return Math.exp(-Math.abs(x/range));
//  }

  private double truncatedLog(double x, double range) {
    if (Math.abs(x) > range) return 0.0;

    return Math.log(Math.abs(x/range));
  }

}
