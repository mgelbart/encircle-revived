package aoi;

import java.awt.geom.Path2D;
import java.util.Vector;

/** 
 * The tail.
 */
public class Tail {
  
  // The trajectory in the two space coordinates
  private NSPath trajectory;
  
  // The time coordinate of each point
  private Vector<Integer> times;
  
  public Tail(int x0, int y0, int time) {
    trajectory = new NSPath();
    times = new Vector<Integer>();
    trajectory.addPoint(x0, y0); // Zero length line
    times.add(time);
  }
  
  public double pathLength() { return trajectory.length(); }
  
  public int nPoints() { return trajectory.nPoints(); }
  
  public Path2D intersects(Tail that) {
    return trajectory.intersectsLastElement(that.trajectory);
  }
  
  public void addPoint(int x, int y, int time) {
    trajectory.addPoint(x, y);
    times.add(time);
    
    assert(trajectory.nPoints() == times.size());
  }
  
}