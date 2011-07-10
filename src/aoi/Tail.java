package aoi;

import java.awt.geom.Path2D;

/** 
 * The tail.
 */
public class Tail {
  
  private NSPath trajectory;
  
  
  public Tail(int x0, int y0) {
    trajectory = new NSPath();
    trajectory.addPoint(x0, y0); // Zero length line
  }
  
  public double pathLength() { return trajectory.length(); }
  
  public int nPoints() { return trajectory.nPoints(); }
  
  public Path2D intersects(Tail that) {
    return trajectory.intersects(that.trajectory);
  }
  
  public void addPoint(int x, int y) {
    trajectory.addPoint(x, y);
  }
  
}