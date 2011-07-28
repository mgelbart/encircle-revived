package aoi;

import java.awt.Point;
import java.awt.Polygon;
import java.awt.geom.Line2D;
import java.util.Iterator;
import java.util.NoSuchElementException;

import org.xml.sax.Attributes;

/**
 * The same as java.awt.Polygon except you can get the list of points out.
 * The list of points does not wrap around, i.e. x[0] not equal x[end]. 
 */
@SuppressWarnings("serial")
public class NSPolygon extends Polygon implements Iterable<Line2D> {
  private final int[] x;
  private final int[] y;
  private final int n;
	
  public NSPolygon(int[] x, int[] y) {
    super(x, y, x.length);
    assert(x.length == y.length);
    n = x.length;
    this.x = x;
    this.y = y;
  }
  public NSPolygon(int[][] coords) {
    this(coords[0], coords[1]);
  }

  // Decode serialized object
  public static NSPolygon parse(Attributes serialized) {
    String[] mainParts = serialized.getValue("d").split(":");
		
    int n = Integer.parseInt(mainParts[0]);
		
    String[] coordsString = mainParts[1].split(",");
    int[][] coords = new int[2][n];
    for (int i = 0; i < n; i++) {
      String[] oneCoord = coordsString[i].split(" ");
      coords[0][i] = Integer.parseInt(oneCoord[0]);
      coords[1][i] = Integer.parseInt(oneCoord[1]);
    }

    return new NSPolygon(coords);
  }

  // Serialize (encode) object
  public String toString() {
    String s = "";
    s += " d=\"" + n + ":";
    for (int i = 0; i < n; i++)
      s += x[i] + " " + y[i] + ",";
    s += "\"";
    return s;	
  }
	
  

  public int width() {
    return (int) getBounds2D().getWidth();
  }

  public int height() {
    return (int) getBounds2D().getHeight();
  }

  public Point center() {
    return new Point(centerX(), centerY());
  }
 
  private int centerX() {
    return (int) getBounds2D().getCenterX();
  }
  private int centerY() {
    return (int) getBounds2D().getCenterY();
  }

  public int n() {
    return n;
  }

  public int[][] coords() {
    int[][] coords = new int[2][n];
    coords[0] = x;
    coords[1] = y;
    return coords;
  }
  
  private Line2D getLine(int i) {
    if (i < 0 || i >= n) return null;
    else if (i == n-1) return new Line2D.Double(x[n-1], y[n-1], x[0], y[0]); 
    else return new Line2D.Double(x[i], y[i], x[i+1], y[i+1]);
  }
  
  public boolean intersects(Line2D l) {
    for (int i = 0; i < n(); i++) 
      if (getLine(i).intersectsLine(l))
        return true;
    return false;
  }
  
  // Actually intersects or contains.
  // If I just wanted intersects, I would use XOR below
//  public boolean intersects(Line2D line) {
////    Logger.println(Logger.RepetitionRate.FREQUENT, line.getP1() + " " + line.getP2());
//    return contains(line.getP1()) || contains(line.getP2());
//  }
  
  public boolean intersects(NSPolygon that) {
    // To speed up, first check the bounding box of that
    if (!intersects(that.getBounds2D())) return false;
    for (Line2D l : that)
      if (intersects(l))
        return true;
    return false;
  }
  
  public boolean intersects(NSPath path) {
   for (Line2D l : path.getLines()) {
//      Logger.println(Logger.RepetitionRate.FREQUENT, l);
      if (intersects(l))
        return true;
    }
    return false;
  }
  
	
  public NSPolygon scaledVersion(int factorX, int factorY) {
    int[] newX = new int[n];
    int[] newY = new int[n];
    for (int i = 0; i < n; i++) {
      newX[i] = x[i] * factorX;
      newY[i] = y[i] * factorY;
    }
    return new NSPolygon(newX, newY);
  }
	
  public NSPolygon move(int deltaX, int deltaY) {
    int[] newX = new int[n];
    int[] newY = new int[n];
    for (int i = 0; i < n; i++) {
      newX[i] = x[i] + deltaX;
      newY[i] = y[i] + deltaY;
    }
    return new NSPolygon(newX, newY);
  }
  
  public Iterator<Line2D> iterator() {
    return new NSPathIterator();
  }
  // an iterator, doesn't implement remove() since it's optional
  private class NSPathIterator implements Iterator<Line2D> {
    private int i = 0;  // the next element to show (i to i+1)

    public boolean hasNext()  { return i < n-1; }
    public void remove()      { throw new UnsupportedOperationException();  }

    public Line2D next() {
      if (!hasNext()) throw new NoSuchElementException();
      i++;
      return new Line2D.Double(new Point(x[i-1],y[i-1]), new Point(x[i], y[i]));
    }
  }
  
  
  
	
}
