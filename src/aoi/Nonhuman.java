package aoi;

import java.awt.Point;
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
  private Vector<ClusterServerSide> clusters;  
  private ClusterServerSide currentTarget;
  
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
    double distToTarget;
    // Compute forces on the ship caused by the clusters
    // The clusters containing the next score send a 
    // perpendicular force encouraging circular motion
    // They also have long-rang attraction.
    // All clusters have short range repuslion.
    
    
    // FIND THE CLUSTER OF INTEREST
    if (currentTarget == null || 
        !currentTarget.acceptable(
            Arena.current().sequence()[pState.score])) {
      // (above) only search for a new target cluster if 
      // you have never picked a target before, or if your score
      // changed such that currentTarget is no longer an acceptable
      // target
      double minDist = Double.MAX_VALUE;
      for (ClusterServerSide css : clusters) {
        if (!css.acceptable(Arena.current().sequence()[pState.score]))
          continue;
        if (css.center().distance(ship.location()) < minDist) {
          minDist = dist(css, ship);
          currentTarget = css;
        }
      }
      distToTarget = minDist;
      Logger.println(Rep.INFREQ, "Targeting cluster at (" + 
          currentTarget.center().getX()/SimulationUtils.ARENASIZE +
          ", " +
          currentTarget.center().getY()/SimulationUtils.ARENASIZE + ")");
    } else {
      distToTarget = dist(currentTarget, ship);
    }
    if (currentTarget == null) {
      Logger.println(Rep.FEW, "***FAILURE TO FIND TARGET CLUSTER!!!***");
      return null; // shouldn't happen
    }
    
    
    // FORCES FROM CLOSEST ACCEPTABLE CLUSTER
//      // Rotational force
      prefactor = 50000./distToTarget;  
      theta = new Angle(
          currentTarget.center().x, 
          currentTarget.center().y, 
          ship.x(), ship.y());
      Fx += prefactor * -theta.sin();
      Fy += prefactor * theta.cos();
      
      // Attraction force
      prefactor = -30000./distToTarget;
      Fx += prefactor * theta.cos();
      Fy += prefactor * theta.sin();

//    Logger.println(Rep.INFREQ, "att |F| = " + Math.sqrt(Fx*Fx+Fy*Fy));
    
    // REPULSION FROM ALL CLUSTERS
    double repulsionLengthScale = SimulationUtils.ARENASIZE/20.;
    double repulsionStrength = 30;
    
    for (Cluster c : clusters) {
      if (dist(c, ship) > repulsionLengthScale*3) continue;
      // to save time, only take into account fairly nearby clusters
      
      prefactor = 0;
      int[][] coords = c.contour().coords();
      int n = coords.length;
      for (int i = 0; i < n; i++) {
         prefactor += Math.exp(-dist(coords[i][0], coords[i][1], ship) /
             repulsionLengthScale) // length scale of repulsion
             / n * repulsionStrength;  // normalize and multiply by strength
        theta = new Angle(c.center().x, c.center().y, ship.x(), ship.y());
        Fx += prefactor * theta.cos();
        Fy += prefactor * theta.sin();
      }
            
      
    }
//    Logger.println(Rep.INFREQ, "rep+att |F| = " + Math.sqrt(Fx*Fx+Fy*Fy));

    
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

//    Logger.println(Rep.INFREQ, "Fx = " + Fx + ",  Fy = " + Fy);
//    Logger.println(Rep.INFREQ, "|F| = " + forceMagnitude);
//    
//    Logger.println(Rep.INFREQ, "Acc = " + acc);
//    Logger.println(Rep.INFREQ, "Dir = " + dir);
    
    
    // Decide whether or not to shoot
    boolean shoot = Shooter.wouldHit(gState, ClientConfiguration.playerNo); 
    // Eventually, should not shoot if about to score
    
    
    return new PlayerControlParameters(
        gState.time, acc, dir, shoot, false);
  }


  private static double dist(Point p, Ship s) {
    return p.distance(s.location());
  }
  private static double dist(Cluster c, Ship s) {
    return dist(c.center(), s);
  }
  private static double dist(int x, int y, Ship s) {
    return dist(new Point(x, y), s); 
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
