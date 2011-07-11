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
    
    assert(trajectory.nPoints() == times.size());
  }
  
  private Tail(NSPath trajectory, Vector<Integer> times) {
    this.trajectory = trajectory;
    this.times = times;
    
    assert(trajectory.nPoints() == times.size());
  }
  
  public double pathLength() { return trajectory.length(); }
  
  public Path2D toPath2D() { return trajectory.toPath2D(); }
  
//  private int nPoints() { return trajectory.nPoints(); }
  
  public int startTime() {
    if (times.isEmpty()) return -1;
    return times.get(0);
  }
  
  public Path2D intersects(Tail that) {
    return trajectory.intersectsLastElement(that.trajectory);
  }
  
  
  // Create a copy of the Tail with an added point.
  @SuppressWarnings("unchecked")
  public Tail addPoint(int x, int y, int time) {

    // trajectory and times for the new tail
    NSPath thatTrajectory = trajectory.clone();
    Vector<Integer> thatTimes = (Vector<Integer>) (times.clone());
    
    assert(thatTrajectory.nPoints() == trajectory.nPoints());
    assert(thatTimes.size() == times.size());
    
    Point2D curPoint = new Point(x, y);
    Point2D prevPoint = trajectory.lastPoint();
    Point2D prevPrevPoint = trajectory.lastlastPoint();

    assert(prevPoint != null);
    
    // if the new point is sufficiently far from the current such that
    // you even care enough to do something about it
    // oh, because of integer resolution
    if (prevPoint.distance(curPoint) > 0.9) {

      // Always add the latest position to the trajectory
      // but combine it with the previous segment if both are short.
      if (prevPrevPoint != null &&
            curPoint.distance(prevPrevPoint) < MINTRAJECTORYSEGMENT) {

        assert(thatTimes.size() > 1);
        assert(thatTrajectory.nPoints() == thatTimes.size());
        
        thatTrajectory.removeLast();
        thatTimes.removeElementAt(thatTimes.size()-1);
        
      }

      thatTrajectory.addPoint(curPoint);
      thatTimes.add(time);
      
      //System.err.println(thatTrajectory.nPoints() + " " + thatTimes.size());
      assert(thatTrajectory.nPoints() == thatTimes.size());
      
      return new Tail(thatTrajectory, thatTimes);
      
    } else {
      return this;
    }

  }
  
  
}
