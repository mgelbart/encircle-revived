package aoi;

import java.awt.Point;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.Vector;

/** 
 * The tail.
 */
public class Tail {
  
  // Roughly the minimum distance between adjacent points in the trajectory for
  // encircling clusters. See the algorithm to see why this is only 'roughly' so.
  public static final int MINTRAJECTORYSEGMENT = SimulationUtils.ARENASIZE / 200;
  // ------------------------------------------------------------------------ //

  
  // The trajectory in the two space coordinates
  private NSPath trajectory;
  
  // The time coordinate of each point
  private Vector<Integer> times;
  
  public Tail(int x0, int y0, int time) {
    trajectory = new NSPath();
    trajectory.addPoint(x0, y0);
    times = new Vector<Integer>();
    times.add(time);
  }
  
  public double pathLength() { return trajectory.length(); }
  
  private int nPoints() { return trajectory.nPoints(); }
  
  public Path2D intersects(Tail that) {
    return trajectory.intersectsLastElement(that.trajectory);
  }
  
  
  // Create a copy of the Tail with 
  public void addPoint(int x, int y, int time) {

    Point2D curPoint = new Point(x, y);
    Point2D prevPoint = trajectory.lastPoint();
    Point2D prevPrevPoint = trajectory.lastlastPoint();
    Point2D newPoint;

    assert(prevPoint != null);
    
    // if the new point is sufficiently far from the current such that
    // you even care enough to do something about it
    // oh, because of integer resolution
    if (prevPoint.distance(curPoint) > 0.9) {

      // Always add the latest position to the trajectory
      // but combine it with the previous segment if both are short.
      if (prevPrevPoint != null &&
            curPoint.distance(prevPrevPoint) < MINTRAJECTORYSEGMENT) {
        trajectory.removeLast();
        if (times.size() > 1) times.removeElementAt(times.size()-1);
        newPoint = prevPrevPoint;
      } else {
        newPoint = prevPoint;
      }
      trajectory.addLine(new Line2D.Double(newPoint, curPoint));
      times.add(time);
    }  
    
    assert(trajectory.nPoints() == times.size());
  }
  
  
}