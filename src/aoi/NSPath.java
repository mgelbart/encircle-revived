package aoi;

import java.awt.Point;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.Iterator;
import java.util.Vector;

/**
 * 
 * @author mgelbart
 *
 * Not Stupid Path. Like a Path2D object but gives access to the lines.
 * Also has a minimum trajectory segment length.
 * Cousin of NSPolygon.
 */
public class NSPath implements Iterable<Line2D >{
  
  // Roughly the minimum distance between adjacent points in the trajectory for
  // encircling clusters. See the algorithm to see why this is only 'roughly' so.
  public static final int MINTRAJECTORYSEGMENT = SimulationUtils.ARENASIZE / 200;
  // ------------------------------------------------------------------------ //

  private Vector<Line2D> lines;
  private double length;  // Cache the trajectory length
  
  public NSPath() {
    lines = new Vector<Line2D>();
    length = 0;
  }
  
  public double length() { return length; }
  public int nPoints() { return lines.size(); }
  
  private Line2D getLastElement() {
    return lines.get(nPoints()-1);
  }
  
  public void clear() {
    lines.clear();
    length = 0;
  }
  
  public void addPoint(int x, int y) {
    if (nPoints() == 0) {
      lines.add(new Line2D.Double(x, y, x, y));
      return;
    }
      
    Point2D curPoint = new Point(x, y);
    Point2D prevPoint = lines.lastElement().getP2();
    Point2D prevPrevPoint = lines.lastElement().getP1();
    Point2D newPoint;

    if (prevPoint.distance(curPoint) > 0.9) {

      // Always add the latest position to the trajectory
      // but combine it with the previous segment if both are short.
      if (curPoint.distance(prevPrevPoint) < MINTRAJECTORYSEGMENT) {
        lines.remove(lines.size()-1);
        newPoint = prevPrevPoint;
      } else {
        newPoint = prevPoint;
      }
      lines.add(new Line2D.Double(newPoint, curPoint));
      length += curPoint.distance(newPoint);
    }  
  }
  
  public Path2D intersectsLastElement(NSPath that) {
    return intersects(that.getLastElement());
  }
  
  /**
   * Does this Tail intersect with the input Line2D?
   * If so, return the loop that is formed. If not, return null.
   */
  public Path2D intersects(Line2D last) {
    Path2D loop;  
    
    for (int k=0; k < nPoints()-2; ++k) {

      // Check if i's last step crossed any line segment of player
      // j's path, O(N).
      if (last.intersectsLine(lines.get(k))) {

        // Construct a Path2D for the loop only.
        loop = new Path2D.Double();

        Point2D point = lines.get(k).getP2();
        loop.moveTo(point.getX(), point.getY());


        for (int l=k+1; l < nPoints(); ++l) {
          point = lines.get(l).getP2();
          loop.lineTo(point.getX(), point.getY());
        }
        return loop;
        
      }
    }
    return null;
  }


  public Iterator<Line2D> iterator()  {
      return lines.iterator();
  }
  
}
