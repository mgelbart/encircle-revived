package aoi;

import java.awt.Polygon;
import org.xml.sax.Attributes;

/**
 * The same as java.awt.Polygon except you can get the list of points out. 
 */
public class NotStupidPolygon extends Polygon {
	private int[] x;
	private int[] y;
	private final int n;
	
  public NotStupidPolygon(int[] x, int[] y) {
  	super(x, y, x.length);
  	assert(x.length == y.length);
  	n = x.length;
  	this.x = x;
  	this.y = y;
  }
  
  // Decode serialized object
  public NotStupidPolygon (Attributes serialized) {
    String[] mainParts = serialized.getValue("d").split(":");
		
		n = Integer.parseInt(mainParts[0]);
		
		String[] coords = mainParts[1].split(",");
		x = new int[n];
		y = new int[n];
		for (int i = 0; i < n; i++) {
		  String[] oneCoord = coords[i].split(" ");
			x[i] = Integer.parseInt(oneCoord[0]);
			y[i] = Integer.parseInt(oneCoord[1]);
		}
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
	public int centerX() {
		return (int) getBounds2D().getCenterX();
	}
	public int centerY() {
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
	public int[][] coordsTranspose() {
	  int[][] coords = new int[n][2];
    for (int i = 0; i < n; i++) {
      coords[i][0] = x[i];
      coords[i][1] = y[i];
    }
    return coords;
	}
	
	public boolean intersects(NotStupidPolygon that) {
		return intersects(that.getBounds2D());
	}
	
	public NotStupidPolygon scaledVersion(int factorX, int factorY) {
	  int[] newX = new int[n];
	  int[] newY = new int[n];
	  for (int i = 0; i < n; i++) {
	    newX[i] = x[i] * factorX;
	    newY[i] = y[i] * factorY;
	  }
	  return new NotStupidPolygon(newX, newY);
	}
	
	public void move(int deltaX, int deltaY) {
		translate(deltaX, deltaY);
		for (int i = 0; i < n; i++) {
			x[i] += deltaX;
			y[i] += deltaY;
		}
	}
	
}