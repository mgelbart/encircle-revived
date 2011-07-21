package aoi;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.PathIterator;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.Iterator;
import java.util.Vector;

/**
 *
 * Not Stupid Path. Like a Path2D object but gives access to the lines.
 * Cousin of NSPolygon.
 */
public class NSPath implements Iterable<Point>, Shape {
  
  private Vector<Point> points;
  private double length;  // Cache the trajectory length
  
  public NSPath() {
    points = new Vector<Point>();
    length = 0;    
  }
  
  private NSPath(Vector<Point> points, double length) {
    this.points = points;
    this.length = length;
  }

  public boolean equals(NSPath that) {
    if (this.length != that.length) return false;
    if (!this.points.equals(that.points)) return false;
    return true;
  }
  
  public Path2D toPath2D() {
    Path2D path = new Path2D.Double();
    if (points.isEmpty()) return path;
    
    path.moveTo(firstPoint().getX(), firstPoint().getY());
    for (int i = 1; i < nPoints(); i++)
      path.lineTo(getPoint(i).getX(), getPoint(i).getY());
    return path;
  }
    
  public double length()         { return length; }
  public boolean isEmpty()       { return points.isEmpty(); }
  public int nPoints()           { return points.size(); }
  public Point firstPoint()    { return getPoint(0); }
  public Point getPoint(int i) { return points.elementAt(i); }
  public Point lastPoint()     { return isEmpty()?null:points.lastElement(); } 
  public Point lastlastPoint() { return (nPoints()<2)?null:points.elementAt(nPoints()-2); }  
  
  public Line2D[] getLines() {
    Line2D[] lines = new Line2D[nPoints()-1];
    for (int i = 0; i < nPoints()-1; i++)
      lines[i] = getLine(i);
    return lines;
  }
  
  public Line2D getLine(int i) {
    if (i < 0 || i >= nPoints()-1) return null;
    else return new Line2D.Double(getPoint(i), getPoint(i+1));
  }
  
  public Line2D lastLine() {
    if (nPoints() < 2) return null;
    else return new Line2D.Double(lastlastPoint(), lastPoint());
  }
  
  public void removeLast() {
    if (points.isEmpty())
      return;
    
    length -= lastlastPoint().distance(lastPoint());
    points.remove(nPoints()-1);
  }
  

  public void addPoint(int x, int y) {
    addPoint(new Point(x, y));
  }
  public void addPoint(Point newPoint) {
    if (!isEmpty()) 
      length += lastPoint().distance(newPoint);
    
    points.add(newPoint);
  }
  public void addPoints(Point[] newPoints) {
    for (int i = 0; i < newPoints.length; i++)
      addPoint(newPoints[i]);
  }
  
  public int intersects(Line2D l) {
    if (nPoints() < 2) return -1;
    if (l == null) return -1;
    for (int i = 0; i < nPoints()-1; i++)
      if (getLine(i).intersectsLine(l))
        return i;
    return -1;
  }
  
  public void close() {
    addPoint(firstPoint());
  }
  
  public int intersects(NSPath that) {
    if (that.nPoints() < 2) return -1;
    for (int i = 0; i < that.nPoints()-1; i++)
      if (intersects(that.getLine(i)) >= 0)
        return i;
    return -1;
  }
  
  // including from and not including to
  public NSPath subpath(int from, int to) {
    NSPath subpath = new NSPath();
    for (int i = 0; i < nPoints(); i++)
      if (i >= from && i < to)
        subpath.addPoint(getPoint(i));
    return subpath;
  }
  
  // crops out from, but does not crop out to
  public NSPath croppath(int from, int to) {
    NSPath croppath = new NSPath();
    for (int i = 0; i < nPoints(); i++)
      if (i < from || i >= to)
        croppath.addPoint(getPoint(i));
    return croppath;
  }
  
  public static String pointToString(Point2D p) {
    return "(" + p.getX() + ", " + p.getY() + ")  "; 
  }
  public static String lineToString(Line2D l) {
    return "from " + pointToString(l.getP1()) + " to " + pointToString(l.getP2());
  }
  
  public String toString() {
    String s = "";
    for (int i = 0; i < nPoints(); i++)
      s += pointToString(getPoint(i));
    return s;
  }
  
  public Iterator<Point> iterator()  {
      return points.iterator();
  }
  
  // Make a deep copy of the path.
  @SuppressWarnings("unchecked")
  public synchronized NSPath clone() {
    return new NSPath((Vector<Point>)points.clone(), length);
  }

  
  // stuff for implementing shape
  // not sure if this is good or sketch-ball
  
  @Override
  public boolean contains(Point2D p) {
    return toPath2D().contains(p);
  }
  @Override
  public boolean contains(Rectangle2D r) {
    return toPath2D().contains(r);
  }
  @Override
  public boolean contains(double x, double y) {
    return toPath2D().contains(x, y);
  }
  @Override
  public boolean contains(double x, double y, double w, double h) {
    return toPath2D().contains(x, y, w, h);
  }
  @Override
  public Rectangle getBounds() {
    return toPath2D().getBounds();
  }
  @Override
  public Rectangle2D getBounds2D() {
    return toPath2D().getBounds2D();
  }
  public PathIterator getPathIterator(AffineTransform at) {
    return toPath2D().getPathIterator(at);
  }
  @Override
  public PathIterator getPathIterator(AffineTransform at, double flatness) {
    return toPath2D().getPathIterator(at, flatness);
  }
  @Override
  public boolean intersects(Rectangle2D r) {
    return toPath2D().intersects(r);
  }
  @Override
  public boolean intersects(double x, double y, double w, double h) {
    return toPath2D().intersects(x, y, w, h);
  }
}
