package aoi;

/*
  This class decides what to do based on (the
  history of) the game state.

  This implementation of Intelligence does
  not rely on any human input.

*/

import java.awt.Point;
import java.awt.geom.Point2D;

public class Nonhuman implements Intelligence {

  // The input options received from Client.
  ClientConfiguration config;

  Arena arena;

  // A queue of latest game states.
  GameState[] games;

  // The control parameters to be sent to the server.
  PlayerControlParameters ctrl = null;
  PlayerControlParameters prevCtrl = null;

  // Last time ctrl was changed.
  int lastAction = -999999;

  public Nonhuman(ClientConfiguration config, Arena arena) {

    this.config = config;
    this.arena = arena;

    System.out.println("Non-human intelligence for player " + config.playerNo + " created.");

    games = new GameState[1];

  }

  public void run() {
    think();
  }

  /* Store the latest game state in a queue. */
  public void processGameStateUpdate(GameState game) {
    games[0] = game; /* Our queue consists of one element! */
  }


  public PlayerControlParameters getNewControlParameters() {
    if (ctrl != prevCtrl) {
      prevCtrl = ctrl;
      return ctrl;
    }

    return null;
  }

  public void think() {

    int acc, dir, shots, boosts;
    int[] intPos;
    int[] ComparedSayWithPET;

    // define a potential function V for decision making
    double bestV, V, currentV;
    PlayerControlParameters bestCtrl, testCtrl;

    acc = Simulation.INTMAX / 2;

    while (true) {
	    
      try {
        Thread.sleep(1000); // Sleep when there's nothing better to do.
      } catch (InterruptedException e) {

        // if (Math.abs(games[0].time - lastAction) > 200/Simulation.TIMESTEP) {
			
        // Shoot if appropriate, i.e., if your path is not too long and another player is in range
        double MAX_LOOP_TO_SHOOT = 20000/Simulation.TIMESTEP;
        double MAX_ANGLE_TO_SHOOT = 0.1;
        shots = 0;
        if (Math.abs(games[0].time - games[0].states[config.playerNo].loopStarted) < MAX_LOOP_TO_SHOOT) { 
          if (prevCtrl != null) {
            ComparedSayWithPET = SimulationUtils.nextPosition(games[0].states[config.playerNo],
                                                              new PlayerControlParameters(prevCtrl.effectiveOn, Simulation.INTMAX/10, prevCtrl.targetDirection, 0, 0));
            for (int playerNos = 0; playerNos < Simulation.NUMPLAYERS; playerNos++) {
              if (playerNos == config.playerNo) continue; // Skip yourself
              double theangle = SimulationUtils.angle(games[0].states[playerNos].x, games[0].states[playerNos].y, games[0].states[config.playerNo].x, games[0].states[config.playerNo].y, ComparedSayWithPET[0], ComparedSayWithPET[1]);
              // System.out.println(theangle + " /    " + ComparedSayWithPET[0] + " " + ComparedSayWithPET[1]);
              if (theangle < MAX_ANGLE_TO_SHOOT) {
                shots = 1;
              }
            }
          }
        }
        boosts = 0;

        currentV = potentialFunction(games[0].states[config.playerNo].x,
                                     games[0].states[config.playerNo].y);

        bestV = Double.POSITIVE_INFINITY;
        bestCtrl = null;

        // Try different directions.
        for (int j=-1; j<=1; ++j) {

          dir = games[0].states[config.playerNo].direction
            + j*(Simulation.INTMAX/3);

          // The candidate set of control parameters.
          testCtrl = new PlayerControlParameters(games[0].time, acc, dir, shots, boosts);

          intPos = SimulationUtils.nextPosition(games[0].states[config.playerNo],
                                                testCtrl);

          V = Double.NEGATIVE_INFINITY;

          // For this given direction candidate, look ahead 1000 time 
          // steps and sum the potential function (?)
          for (int k=1; k < 1000/Simulation.TIMESTEP; ++k) {

            intPos = SimulationUtils.nextPosition(
                                                  new PlayerState(0, intPos[0], intPos[1],
                                                                  intPos[2], intPos[3],
                                                                  intPos[4], 0, 0, 0, false),
                                                  testCtrl);

            V = Math.max(V, potentialFunction(intPos[0], intPos[1]));
          }

          // Prefer going straight
          V += Math.abs(j) * 0.05;

          if (V < bestV) {
            bestCtrl = testCtrl;
            bestV = V;
          }

        }


        if (bestV < currentV)
          acc = Simulation.INTMAX;
        else {
          acc = (int) ((10. - Math.max(1., Math.min(5., bestV))) / 9.
                       * Simulation.INTMAX);
          //System.out.println(bestV + "   " + (int) ((25. - Math.max(5., Math.min(20., bestV))) / 20));
        }
        // if (Math.random() < .9) acc=0;
        //System.out.println("acc: " + acc);

        lastAction = games[0].time;

        ctrl = bestCtrl;
      }


      // }

    }

  }

  /* Evaluate the desirability of a given position (x, y)
     on the arena.
  */
  private double potentialFunction(int x, int y) {
    double V = 0;

    Point2D loc = new Point(x, y);

    double range = 500 / Simulation.TIMESTEP
      * Math.sqrt( Math.pow(games[0].states[config.playerNo].vx, 2)
                   + Math.pow(games[0].states[config.playerNo].vy, 2));

    range = Math.max(range, Simulation.ARENASIZE/20);

    // Repuslive terms pushing you away from the arena boundaries
    V += Math.exp((Math.abs(x) - Simulation.ARENASIZE/2) / (Simulation.ARENASIZE/50));
    V += Math.exp((Math.abs(y) - Simulation.ARENASIZE/2) / (Simulation.ARENASIZE/50));

    // Attractive terms for four points around the clusters
    double dist;
    for (Cluster clust : arena.clusters) {

      V += -truncatedLog(loc.distance(clust.x, clust.y + clust.height/2), range);
      V += -truncatedLog(loc.distance(clust.x, clust.y - clust.height/2), range);
      V += -truncatedLog(loc.distance(clust.x + clust.width/2, clust.y), range);
      V += -truncatedLog(loc.distance(clust.x + clust.width/2, clust.y), range);

    }

    return V;
  }

  private double truncatedExp(double x, double range) {
    if (Math.abs(x) > 3*range) return 0.0;

    return Math.exp(-Math.abs(x/range));
  }

  private double truncatedLog(double x, double range) {
    if (Math.abs(x) > range) return 0.0;

    return Math.log(Math.abs(x/range));
  }

}
