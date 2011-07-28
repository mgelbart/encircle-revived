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
  private boolean clockwise; // which direction to encircle currentTarget
  private Cluster closest = null;
  private boolean rotClockwise;
  private Vector<Point> repulsionCenters;
  private Vector<Integer> repulsionCenterTimes;
  private int prevSinceCrashed = 0;
  private int prevSinceLooping = 0;
  
  public Nonhuman() throws FileNotFoundException {
    super();
    
    clockwise = true;
    repulsionCenters = new Vector<Point>();
    repulsionCenterTimes = new Vector<Integer>();
    
    // Convert the Cluster objects in arena
    // to ClusterServerSide objects so that we know
    // the numbers involved.
    ClusterDatabase db = new ClusterDatabase();
    clusters = new Vector<ClusterServerSide>();
    for (Cluster c : Arena.current().clusters)
      clusters.add(db.copyFromCluster(c)); 
  }
  
  public PlayerControlParameters think(GameState gState) {
    /* TABLE OF CONTENTS
     * (1) FIND THE CLUSTER TO CURRENTLY TARGET (IF NEEDED)
     * (2) TARGET CLUSTER CAUSES ATTRACTION AND ROTATION
     * (3) ALL CLUSTERS CAUSE (SHORT-RANGE) REPULSION AND ROTATION
     * (4) USE FORCE TO GENERATE ACCELERATION, DIRECTION
     * (5) DECIDE WHETHER OR NOT TO SHOOT
     */
       
    PlayerState pState = gState.pStates[ClientConfiguration.playerNo];
    Ship ship = pState.ship;

    
    double FxAttractive = 0;
    double FyAttractive = 0;
    double FxRepulsive = 0;
    double FyRepulsive = 0;
    double FxRotational = 0;
    double FyRotational = 0;
    double prefactor;
    Angle theta;
    double distToTarget;
    
    Point r = ship.shape()[1]; // the front of the ship
    
    
    
 // if Crashed, add repulsion center
    if (pState.sinceCrashed - prevSinceCrashed < 0 ||
        pState.sinceLooping - prevSinceLooping < 0) {
      repulsionCenters.add(r);
      repulsionCenterTimes.add(gState.time);
      Logger.println(Rep.INFREQ, "Repulsive center placed");
    }
    prevSinceCrashed = pState.sinceCrashed;
    prevSinceLooping = pState.sinceLooping;
    
    // get repelled by these repulsion centers
    for (int i = 0; i < repulsionCenters.size(); i++) {
      Point p = repulsionCenters.get(i);
      int t = repulsionCenterTimes.get(i);
    
      if (dist(p, r) > SimulationUtils.ARENASIZE / 5) continue;
      if (gState.time - t > 100) continue;
      // (above) for speed, only consider nearby centers
      
      prefactor = (100-(gState.time-t))*
      Math.exp(-dist(p, ship.location()) /
          (SimulationUtils.ARENASIZE/30)); // length scale of repulsion
      theta = new Angle(p, ship.location());
      FxRepulsive += prefactor * theta.cos();
      FyRepulsive += prefactor * theta.sin();
    }

    
    // if you JUST won
    if (pState.score >= Arena.current().sequence().length) return null;
    
    
/* (1) FIND THE CLUSTER OF INTEREST */
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
        if (dist(css, ship.location()) < minDist) {
          minDist = dist(css, r);
          currentTarget = css;
        }
      }
      distToTarget = minDist;
      clockwise = Math.random() < 0.5; // pick randomly for now
      
      Logger.println(Rep.INFREQ, "Targeting (" + 
          (clockwise?"CLOCKWISE":"COUNTERCLOCKWISE") +
          " at distance) " + minDist + 
              " cluster at (" + 
          currentTarget.center().getX()/SimulationUtils.ARENASIZE +
          ", " +
          currentTarget.center().getY()/SimulationUtils.ARENASIZE + ")");
    } else {
      distToTarget = dist(currentTarget, ship.location());
    }
    if (currentTarget == null) {
      Logger.println(Rep.FEW, "***FAILURE TO FIND TARGET CLUSTER!!!***");
      return null; // shouldn't happen
    }
    
    
    theta = new Angle(currentTarget.center(), ship.location());
  /* (2) FORCES FROM CLOSEST ACCEPTABLE CLUSTER */
      // Rotational force
      prefactor = 200000./distToTarget;
//      prefactor = 50./(distToTarget*distToTarget);
      FxRotational += prefactor * -theta.sin() * (clockwise?-1:1);
      FyRotational += prefactor *  theta.cos() * (clockwise?-1:1);
    
      // Attraction force
//      prefactor = 10 * distToTarget / SimulationUtils.ARENASIZE;
      prefactor = -4;
      FxAttractive += prefactor * theta.cos();
      FyAttractive += prefactor * theta.sin();
  //    Logger.println(Rep.INFREQ, "att |F| = " + Math.sqrt(Fx*Fx+Fy*Fy));
      
      
      
   // (2.5) Find the closest cluster to you
      // and have a small rotational force
    double minDist = Double.MAX_VALUE;
    Cluster closestTemp = null;
    for (Cluster c : clusters) {
      if (dist(c, r) < minDist) {
        minDist = dist(c, ship.location());
        closestTemp = c;
      }
    }
    if (closestTemp == null) return null;   // this shouldn't happen
    closest = closestTemp;
    // If you have a new closest cluster, pick a new direction for it
    // Otherwise, keep the same one
//    if (closestTemp != closest) {
//      rotClockwise = Math.random() < 0.5;
//      closest = closestTemp;
//    }
//    if (closest == null) return null;  // this shouldn't happen
//    
    rotClockwise = clockwise;
     
    
    // And a small rotational force for the closest cluster
    double rotationStrength = 100.;
    double rotationLengthScale = SimulationUtils.ARENASIZE/30.;
    int[][] coords = closest.contour().coords();
    int n = coords[0].length;
    for (int i = 0; i < n; i++) {
      prefactor = Math.exp(-dist(coords[0][i], coords[1][i], r) /
           rotationLengthScale) // length scale of repulsion
           / n * rotationStrength;  // normalize and multiply by strength
      theta = new Angle(coords[0][i], coords[1][i], ship.x(), ship.y());
      FxRotational += prefactor * -theta.sin() * (rotClockwise?-1:1);
      FyRotational += prefactor *  theta.cos() * (rotClockwise?-1:1);      
    }
    
  
      
    
    
    
    
/* (3) REPULSION FROM ALL CLUSTERS */
    double repulsionLengthScale = SimulationUtils.ARENASIZE/30.;
    double repulsionStrength = 100.;
    
    for (Cluster c : clusters) {
      if (dist(c, r) > repulsionLengthScale*10) continue;
      // (above) for speed, only consider nearby clusters
      
      // Mix things up a bit
//      if (Math.random() < 0.1) continue;
      
      // Repulsion
      coords = c.contour().coords();
      n = coords[0].length;
      for (int i = 0; i < n; i++) {
        prefactor = Math.exp(-dist(coords[0][i], coords[1][i], r) /
             repulsionLengthScale) // length scale of repulsion
             / n * repulsionStrength;  // normalize and multiply by strength
        theta = new Angle(coords[0][i], coords[1][i], ship.x(), ship.y());
        FxRepulsive += prefactor * theta.cos();
        FyRepulsive += prefactor * theta.sin();
      }

    }
//    Logger.println(Rep.INFREQ, "rep+att |F| = " + Math.sqrt(Fx*Fx+Fy*Fy));

    double Fx = FxAttractive + FxRepulsive + FxRotational;
    double Fy = FyAttractive + FyRepulsive + FyRotational;
    double forceMagnitude = Math.sqrt(Fx*Fx+Fy*Fy);
    
    // the point of this is that if there's a lot of attraction going on,
    // you should go fast, and if there's a lot of repulsion, you should go slow
    double FAttractive = Math.sqrt(FxAttractive*FxAttractive+FyAttractive*FyAttractive);
    double FRepulsive = Math.sqrt(FxRepulsive*FxRepulsive+FyRepulsive*FyRepulsive);
    double FRotational = Math.sqrt(FxRotational*FxRotational+FyRotational*FyRotational);
    
    
/* (4) USE FORCE TO GENERATE ACCELERATION, DIRECTION */    

    int acc;
    if (FRepulsive > 5.0)  // only go fast if there's a lot of attraction
      acc = SimulationUtils.MAXACC/3;
    else
      acc = SimulationUtils.MAXACC;
    
    // Set the direction based on the force direction
    if (Fx == 0 && Fy == 0) return null;
    
    Angle dir = new Angle(Fx, Fy);

    // Keep going straight once in a while, to close loops
    if (Math.random() < 0.15)
      return null;
    
//    Logger.println(Rep.INFREQ, "Fx = " + Fx + ",  Fy = " + Fy);
//    Logger.println(Rep.INFREQ, "|F| = " + forceMagnitude);
//    
    if (gState.time % 50 == 0) {
      Logger.println(Rep.INFREQ, "FRepulsive = " + FRepulsive);
      Logger.println(Rep.INFREQ, "FAttractive = " + FAttractive);
      Logger.println(Rep.INFREQ, "FRotational = " + FRotational);
      Logger.println(Rep.INFREQ, "Acc = " + acc);
      Logger.println(Rep.INFREQ, "Dir = " + dir);
    }
    

    
    
    
/* (5) DECIDE WHETHER OR NOT TO SHOOT */
    boolean shoot = Shooter.wouldHit(gState, ClientConfiguration.playerNo); 
    // Eventually, should not shoot if about to score
    
    
    return new PlayerControlParameters(
        gState.time, acc, dir, shoot, false);
  }


  
  
  
  
  
  
  
  
  
  
  
  private static double dist(Point p, Point q) {
    return p.distance(q);
  }
  private static double dist(Cluster c, Point q) {
    return dist(c.center(), q);
  }
  private static double dist(int x, int y, Point q) {
    return dist(new Point(x, y), q); 
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
