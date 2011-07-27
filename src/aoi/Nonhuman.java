package aoi;

import java.awt.geom.AffineTransform;
import java.io.FileNotFoundException;
import java.util.Vector;

import aoi.Logger.Rep;

 /**
 *  This class decides what to do based on (the
 *  history of) the game state.
 *
 *  This implementation of Intelligence does
 *  not rely on any human input.
 *
 */
public class Nonhuman extends DefaultIntelligence {

  // We cheat by taking the true values of the clusters
  // in reality, the client should not have these
  // Note that this will cause problems if the
  // Arena changes (i.e., if Arena.current() changes)
  Vector<ClusterServerSide> clusters;  
  
  public Nonhuman() throws FileNotFoundException {
    super();
    
    // Convert the Cluster objects in arena
    // to ClusterServerSide objects so that we know
    // the numbers involved.
    ClusterDatabase db = new ClusterDatabase();
    clusters = new Vector<ClusterServerSide>();
    for (Cluster c : Arena.current().clusters)
      clusters.add(db.copyFromCluster(c)); 
  }
  
  public PlayerControlParameters think(GameState gState) {
    PlayerState pState = gState.pStates[ClientConfiguration.playerNo];
    Ship ship = pState.ship;

    double Fx = 0;
    double Fy = 0;
    double prefactor;
    Angle theta;
    // Compute forces on the ship caused by the clusters
    // The clusters containing the next score send a 
    // perpendicular force encouraging circular motion
    // They also have long-rang attraction.
    // All clusters have short range repuslion.
    
    
    // FIND THE CLUSTER OF INTEREST
    // easier this way
    double minDist = Double.MAX_VALUE;
    ClusterServerSide closestAcceptable = null;
    for (ClusterServerSide css : clusters) {
      if (!css.acceptable(Arena.current().sequence()[pState.score]))
        continue;
      if (css.center().distance(ship.location()) < minDist) {
        minDist = css.center().distance(ship.location());
        closestAcceptable = css;
      }
    }
    if (closestAcceptable == null) return null; // shouldn't happen
    
    
    // FORCES FROM CLOSEST ACCEPTABLE CLUSTER
//      // Rotational force
      prefactor = 50000./minDist;  
      theta = new Angle(
          closestAcceptable.center().x, 
          closestAcceptable.center().y, 
          ship.x(), ship.y());
      Fx += prefactor * theta.sin();
      Fy += prefactor * theta.cos();
      
      // Attraction force
      prefactor = -30000./minDist;
      Fx += prefactor * theta.cos();
      Fy += prefactor * theta.sin();

    Logger.println(Rep.INFREQ, "att |F| = " + Math.sqrt(Fx*Fx+Fy*Fy));
    
    // REPULSION FROM ALL CLUSTERS
    for (Cluster c : clusters) {
      
      prefactor = 30*truncatedExp(c.center().distance(ship.location()), 
          SimulationUtils.ARENASIZE/20);      
      theta = new Angle(c.center().x, c.center().y, ship.x(), ship.y());
      
      Fx += prefactor * theta.cos();
      Fy += prefactor * theta.sin();
    }
    Logger.println(Rep.INFREQ, "rep+att |F| = " + Math.sqrt(Fx*Fx+Fy*Fy));
    
    
//    double bestV, V; 
//    // (above) define a potential function V for decision making
//    PlayerControlParameters bestCtrl, testCtrl;//    
//    
//    bestV = Double.POSITIVE_INFINITY;
//    bestCtrl = null;
//
//    // Try different directions.
//    for (int j=-1; j<=1; ++j) {
//
//      Angle dir = ship.dir().add(j*Math.PI/3);
//
//      // The candidate set of control parameters.
//      testCtrl = new PlayerControlParameters(gState.time, 
//          SimulationUtils.MAXACC, 
//          dir,
//          false, false);
//
//      Ship intPos = SimulationUtils.nextPosition(ship, testCtrl);
//
//      V = Double.NEGATIVE_INFINITY;
//
//      // For this given direction candidate, look ahead 1000 time 
//      // steps and take the maximum of the potential along the path.
//      for (int k=1; k < 1000/SimulationUtils.TIMESTEP; ++k) {
//
//        intPos = SimulationUtils.nextPosition(intPos, testCtrl);
//
//        V = Math.max(V, potentialFunction(intPos));
//      }
//
//      // Prefer going straight
//      V += Math.abs(j) * 0.05;
//
//      if (V < bestV) {
//        bestCtrl = testCtrl;
//        bestV = V;
//      }
//
//    }
//    
//
//    // Use automatic shooter
//    if (Shooter.wouldHit(gState, ClientConfiguration.playerNo))
//      bestCtrl = bestCtrl.shoot();
//
//
//    return bestCtrl;
    

    
    // Set the acceleration based on the force magnitude
    double forceMagnitude = Math.sqrt(Fx*Fx+Fy*Fy);
    double maxForce = 1; // if 0 then always MAXACC
    int acc;
    if (forceMagnitude >= maxForce)
      acc = SimulationUtils.MAXACC;
    else
      acc = (int) (forceMagnitude / maxForce * SimulationUtils.MAXACC);
    
    // Set the direction based on the force direction
    Angle dir = new Angle(Fx, Fy);

    Logger.println(Rep.INFREQ, "Fx = " + Fx + ",  Fy = " + Fy);
    Logger.println(Rep.INFREQ, "|F| = " + forceMagnitude);
    
    Logger.println(Rep.INFREQ, "Acc = " + acc);
    Logger.println(Rep.INFREQ, "Dir = " + dir);
    
    return new PlayerControlParameters(
        gState.time,
        acc,
        dir,
        Shooter.wouldHit(gState, ClientConfiguration.playerNo),
        false);
  }

  /* 
   * Evaluate the desirability of a given position (x, y)
   * on the arena.
  */
  private double potentialFunction(Ship s) {
    double V = 0;

    double range = 500 / SimulationUtils.TIMESTEP
      * Math.sqrt( Math.pow(s.vx(), 2)
                   + Math.pow(s.vy(), 2));

    range = Math.max(range, SimulationUtils.ARENASIZE/20);

    // Repuslive terms pushing you away from the arena boundaries
    V += Math.exp((Math.abs(s.x()) - SimulationUtils.ARENASIZE/2) / 
        (SimulationUtils.ARENASIZE/50));
    V += Math.exp((Math.abs(s.y()) - SimulationUtils.ARENASIZE/2) / 
        (SimulationUtils.ARENASIZE/50));

    // Repulsive terms pushing you away from the clusters
    for (Cluster clust : clusters) {
      V += -truncatedLog(s.location().distance(
          clust.center().x, clust.center().y + clust.height()/2), range);
      V += -truncatedLog(s.location().distance(
          clust.center().x, clust.center().y - clust.height()/2), range);
      V += -truncatedLog(s.location().distance(
          clust.center().x + clust.width()/2, clust.center().y), range);
      V += -truncatedLog(s.location().distance(
          clust.center().x + clust.width()/2, clust.center().y), range);
    }

    return V;
  }

  private double truncatedExp(double x, double range) {
    if (Math.abs(x) > 3*range) return 0.0;

    return Math.exp(-Math.abs(x/range));
  }

  private static double truncatedLog(double x, double range) {
    if (Math.abs(x) > range) return 0.0;

    return Math.log(Math.abs(x/range));
  }

}
