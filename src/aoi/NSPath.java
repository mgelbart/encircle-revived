package aoi;

import java.awt.Point;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.Iterator;
import java.util.Vector;

/**
 *
 * Not Stupid Path. Like a Path2D object but gives access to the lines.
 * Cousin of NSPolygon.
 */
public class NSPath implements Iterable<Line2D> {
  
  private Point2D start;
  private Vector<Line2D> lines; // stores every point twice
  private double length;  // Cache the trajectory length
  
  public NSPath() {
    lines = new Vector<Line2D>();
    length = 0;    
  }
  
  private NSPath(Point2D start, Vector<Line2D> lines, double length) {
    this.start = start;
    this.lines = lines;
    this.length = length;
  }

  // this is pretty ugly- this Class should probably extend
  // Path2D like NSPolygon.
  // or else at least store points instead of lines
  public Path2D toPath2D() {
    if (start == null) return null;
    Path2D path = new Path2D.Double();
    path.moveTo(start.getX(), start.getY());
    for (Line2D line : lines)
      path.lineTo(line.getX1(), line.getY1());
    path.lineTo(lastPoint().getX(), lastPoint().getY());
    return path;
  }
  
  public Point2D getPoint(int i) {
    if (i == 0) return start;
    else if (i > lines.size()) return null;
    else return lines.elementAt(i-1).getP2(); 
  }
  
//  public Vector<Line2D> getLines() { return lines; }
  
  public double length() { return length; }
  
  // Returns the number of points (not the number of line segments)
  public int nPoints() {
    if (start == null) return 0;
    else return lines.size() + 1;
  }
  
  public int nLines() {
    return lines.size();
  }
  
  public Point2D firstPoint() {
    return start;
  }
  
  public Point2D lastPoint() {
    if (lines.isEmpty()) return start;
    return lines.lastElement().getP2();
  }
  
  public Point2D lastlastPoint() {
    if (lines.isEmpty()) return null;
    return lines.lastElement().getP1();
  }
  
  public Line2D lastLine() {
    if (lines.isEmpty()) return null;
    return lines.lastElement();
  }
  public Line2D getLine(int i) {
    return lines.elementAt(i);
  }
  
  public void removeLast() {
    if (lines.isEmpty()) {
      start = null;
      return;
    }
    
    length -= lastlastPoint().distance(lastPoint());
    lines.remove(lines.size()-1);
  }
  
  public void addPoint(int x, int y) {
    addPoint(new Point(x, y));
  }
  
  public void addPoint(Point2D newPoint) {
  
    if (start == null) {
      start = newPoint;
    }
    else if (lines.isEmpty()) {
      length += start.distance(newPoint);
      lines.add(new Line2D.Double(start, newPoint));
    }
    else {
      length += lastPoint().distance(newPoint);
      lines.add(new Line2D.Double(lastPoint(), newPoint));
    }
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
  
  


  public Iterator<Line2D> iterator()  {
      return lines.iterator();
  }
  
  // Make a deep copy of the path.
  public synchronized NSPath clone() {
    Vector<Line2D> ls = new Vector<Line2D>(lines.size()+1);
    
    for (Line2D l : lines)
      ls.add((Line2D) (l.clone()));
    
//    final int N = lines.size();
//    for (int i = 0; i<N; i++) {
//      ls.set(i, (Line2D) (lines.get(i).clone()));
//    }

    return new NSPath(start, ls, length);
  }
}
