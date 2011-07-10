package aoi;

import java.awt.Point;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.Line2D.Double;
import java.util.Iterator;
import java.util.Vector;

/**
 * 
 * @author mgelbart
 *
 * Not Stupid Path. Like a Path2D object but gives access to the lines.
 * Cousin of NSPolygon.
 */
public class NSPath implements Iterable<Line2D> {
  
  private Vector<Line2D> lines;
  private double length;  // Cache the trajectory length
  
  public NSPath() {
    lines = new Vector<Line2D>();
    length = 0;    
  }
  
  public double length() { return length; }
  
  // Returns the number of points (not the number of line segments)
  public int nPoints() {
    if (length == 0 && lines.size() == 1)
      return 1;
    return lines.size() + 1;
  }
  
  public void clear() {
    lines.clear();
    length = 0;
  }
  
  public Point2D firstPoint() {
    return lines.firstElement().getP1();
  }
  
  public Point2D lastPoint() {
    return lines.lastElement().getP2();
  }
  
  public Point2D lastlastPoint() {
    return lines.lastElement().getP1();
  }
  
  public void removeLast() {
    length -= lastlastPoint().distance(lastPoint());
    lines.remove(lines.size()-1);
  }
   
  public void addLine(Line2D l) {
    lines.add(l);
    length += l.getP1().distance(l.getP2());
  }
  
  public void addPoint(int x, int y) {
    if (lines.size() == 0) {
      lines.add(new Line2D.Double(x, y, x, y));
      return;
    }
    
    Point newPoint = new Point(x, y);
    
    lines.add(new Line2D.Double(lastPoint(), newPoint));
    length += lastPoint().distance(newPoint);

  }
  
  public boolean intersects(Line2D l) {
    for (Line2D line : this)
      if (line.intersectsLine(l))
        return true;
    return false;
  }
  
  public boolean intersects(NSPath that) {
    for (Line2D line : that)
      if (intersects(line))
        return true;
    return false;
  }
  
  
  /**
   * Does this Tail intersect with the input Line2D?
   * If so, return the loop that is formed. If not, return null.
   */
  public Path2D intersectsLastElement(NSPath that) {

    Path2D loop;  
    Line2D last = that.lines.lastElement();
    
    for (int k=0; k < lines.size()-2; ++k) {

      // Check if i's last step crossed any line segment of player
      // j's path, O(N).
      if (last.intersectsLine(lines.get(k))) {

        // Construct a Path2D for the loop only.
        loop = new Path2D.Double();

        Point2D point = lines.get(k).getP2();
        loop.moveTo(point.getX(), point.getY());


        for (int l=k+1; l < lines.size(); ++l) {
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
  
  // Make a deep copy of the path.
  public synchronized NSPath clone() {
    Vector<Line2D> ls = new Vector<Line2D>(lines.size()+1);
    
    final int N = lines.size();
    for (int i = 0; i<N; i++) {
      ls.set(i, (Line2D) (lines.get(i).clone()));
    }

    NSPath that = new NSPath();
    that.lines = ls;
    that.length = length;
    
    return that;
  }
}
