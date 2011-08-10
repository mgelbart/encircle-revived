package aoi;

import java.awt.Point;
import java.awt.geom.Line2D;
import java.util.Vector;

import aoi.Logger.Rep;

/*
 * This class translates a trajectory into control parameters 
 * of the ship.
 */
public abstract class Tracker {
  
  // the distance over which the ship will slow down 
  // from full speed to rest.
  private static final double DAMPINGDIST = 
    SimulationUtils.MAXSPEED / SimulationUtils.DRAG;
  
  // acceptable radius at full speed
  private static final double ACCEPTABLERADIOUS = 
    SimulationUtils.ARENASIZE/15;
  
  private static final double MINDISTANCEBETWEENTARGETS = 
    SimulationUtils.ARENASIZE/40;
  
  private static Vector<Point> path = new Vector<Point>();
  private static boolean pathChanged = false;
  
  public static void setPathToTrack(Vector<Point> pts) {
    pathChanged = (pts==null)? path.size()>0 : true;
    if (!pathChanged) return;
    
    Vector<Point> newPath = new Vector<Point>();
    if (pts == null || pts.size() < 3) { path=newPath; return; }
    
    // This copies the path but makes it sparser.
    newPath.add(pts.firstElement());
    for (Point p : pts.subList(1, pts.size()-1)) {
      // don't add points that are too close to each other.
      if (p.distance(newPath.lastElement()) < MINDISTANCEBETWEENTARGETS)
        continue;
      newPath.add(p);
    }
    newPath.add(pts.lastElement());
    
    path = newPath;
  }
  
  public static int pointsLeftToTrack() {
    return (path == null)? 0:path.size();
  }
  
  /*
   * Return the remaining path if it has changed since 
   * this function was last called.
   */
  public static Vector<Point> remainingPath() {
    if (pathChanged) {
      pathChanged = false;
      return path; // note: doesn't clone.
    }
    return null;
  }
  

  public static PlayerControlParameters nextParams(int time, Ship current) {
    if (path == null) return null;

    final double speed = current.speed();
    final double acceptableRadius = 
      ACCEPTABLERADIOUS * (0.2 + 0.8*Math.pow(
          speed/SimulationUtils.MAXSPEED, 0.4)); 
    // (above) the power here controls how much the ship 
    // is allowed to slide at high speeds
    
    // if we're sufficiently close to the next point then move on the next one.
    while (path.size() > 0 && current.location().distance(path.firstElement()) < acceptableRadius) {
      path.remove(0);
      pathChanged = true;
    }
    
    // done:
    if (path.size() == 0) return null;
    
    final Point target = path.firstElement();
    // almost done:
    if (path.size() == 1) return paramsToTarget(time, current, target);
    
    // otherwise figure out the best parameters based on the remaining trajectory.
    final Angle targetDir = 
      new Angle(current.location(), target);
    final double distToTargetDir = 
      Math.abs(current.dir().add(-targetDir.toDouble()).toDouble());
    final double distToTarget = 
      current.location().distance(target);
    
    double acc;
    
    if (distToTargetDir > Math.PI/2 || distToTargetDir > 0.1*distToTarget/acceptableRadius)
      // if we're facing in the wrong direction altogether.
      acc = 0;
    else {
      
      if (distToTarget > DAMPINGDIST) {
        // don't worry about fine tuning if we're still far
        // away from the target
        acc = SimulationUtils.MAXACC;
      } else {
        
        /*
         * Compute a measure of the curvature of the path to
         * follow so that we start to slow
         * down in time.
         */

        double curvature = 0;
        double totalDist = 0;
        for (int j=1; j<path.size(); ++j) {
          final Point p0 = (j==1) ? current.location() : path.get(j-2);
          final Point p1 = path.get(j-1);
          final Point p2 = path.get(j);
          
          // we only care about the curvature within 
          // damping distance of current location
          final double SLOWDOWNDIST = 1.5*DAMPINGDIST;
          totalDist += p0.distance(p1);
          if (totalDist > SLOWDOWNDIST) break;
          
          // give less weight to the curvature the further it is from the current point.
          final double weight = Math.pow(
              (SLOWDOWNDIST-totalDist)/SLOWDOWNDIST, 0.); 
          // (above) this determines how early you start to
          // slow down before a tight curve
          
          final double angle = Math.abs(new Angle(p0, p1).add(-(new Angle(p1, p2).toDouble())).toDouble());
          curvature = Math.max(angle, weight*angle); // "weighted max"
        }
        
        final double MINSPEED = SimulationUtils.MAXSPEED * 0.4; 
        // (above) minimum speed to maintain
        final double MINACC = (speed > MINSPEED)? 0 : MINSPEED*SimulationUtils.DRAG;
        final double MAXACCTH = Math.PI*0.08;
        final double MINACCTH = Math.PI*0.5;
        if (curvature < MAXACCTH)
          acc = SimulationUtils.MAXACC;
        else if (curvature > MINACCTH)
          acc = MINACC;
        else
          acc = MINACC + Math.pow(
              (curvature-MAXACCTH)/(MINACCTH-MAXACCTH), 2)*
              (SimulationUtils.MAXACC-MINACC);

      }
    }
    
    return new PlayerControlParameters(
        time, (int) acc, targetDir, false, false);
  }

  /*
   * Return the parameters for heading towards a (final) 
   * target given current ship position.
   */
  public static PlayerControlParameters paramsToTarget(
      int time, Ship current, Point target) {
    assert(target != null);
    assert(current != null);
    
    // ship location
    Point loc = current.location();
    
    // Set the acceleration based on the distance between the ship and mouse.
    
    // the decay length^2
    final double lsq = Math.pow(DAMPINGDIST, 2);
    
    // use only the distance parallel to the direction of the ship
    final Line2D perpLine = new Line2D.Double(
        loc, current.dir().add(Math.PI/2).move(loc, 1000.));
    final double distsq = perpLine.ptLineDistSq(target);
    final boolean infront = Line2D.relativeCCW(
        perpLine.getX1(), perpLine.getY1(),
        perpLine.getX2(), perpLine.getY2(), 
        target.getX(), target.getY()) > 0;
    
    int acc;
    if (!infront) acc = 0; 
    else acc = (int) (SimulationUtils.MAXACC * 
        Math.min(1., Math.pow(distsq/lsq, 1.)));
    
    return new PlayerControlParameters(
        time, acc, new Angle(loc, target), false, false);
  }

}
