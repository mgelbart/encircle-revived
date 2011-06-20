package aoi;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.awt.geom.Path2D;
import java.awt.geom.Line2D;
import java.util.Vector;

/** 
 * The tail.
 */
public class Tail {
  
  // Roughly the minimum distance between adjacent points in the trajectory for
  // encircling clusters. See the algorithm to see why this is only 'roughly' so.
  public static final int MINTRAJECTORYSEGMENT = SimulationUtils.ARENASIZE / 200;
  // ------------------------------------------------------------------------ //
  
  private Vector<Line2D> trajectory;
  
  // Cache the trajectory length
  private double length; 
  
  public Tail(int x0, int y0) {
    trajectory = new Vector<Line2D>();
    trajectory.add(new Line2D.Double(x0, y0, x0, y0)); // Zero length line
    length = 0;
  }

  // // TODO!!!! // 
  // public DrawableTail getDrawable() {
  //   return null;
  // }
  
  public double pathLength() {
    return length;
  }
  
  public int nPoints() {
    return trajectory.size();
  }
  
  public Line2D getLastSegment() {
    return trajectory.get(nPoints()-1);
  }
  
  /**
   * Does this Tail intersect with the input Line2D?
   * If so, return the loop that is formed. If not, return null.
   */
  public Path2D intersects(Line2D line) {
    Path2D loop;

    for (int k=0; k < nPoints()-2; ++k) {

      // Check if i's last step crossed any line segment of player
      // j's path, O(N).
      if (line.intersectsLine(trajectory.get(k))) {

        // Construct a Path2D for the loop only.
        loop = new Path2D.Double();

        Point2D point = trajectory.get(k).getP2();
        loop.moveTo(point.getX(), point.getY());


        for (int l=k+1; l < nPoints(); ++l) {
          point = trajectory.get(l).getP2();
          loop.lineTo(point.getX(), point.getY());
        }
        return loop;
		    
      }
    }
    return null;
  }
  
  public void addPoint(int x, int y) {
    
    Point2D curPoint = new Point(x, y);
    Point2D prevPoint = trajectory.lastElement().getP2();
    Point2D prevPrevPoint = trajectory.lastElement().getP1();
    Point2D newPoint;

    if (prevPoint.distance(curPoint) > 0.9) {

      // Always add the latest position to the trajectory
      // but combine it with the previous segment if both are short.
      if (curPoint.distance(prevPrevPoint) < MINTRAJECTORYSEGMENT) {
        trajectory.remove(trajectory.size()-1);
        newPoint = prevPrevPoint;
      } else {
        newPoint = prevPoint;
      }
      trajectory.add(new Line2D.Double(newPoint, curPoint));
      length += curPoint.distance(newPoint);
    }  
  }
  
  public void clear() {
    trajectory.clear();
    length = 0;
  }
  
  
}