package aoi;

/*
  The same as java.awt.Polygon except you can get the list of points out.
 */

import java.awt.Polygon;

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

	public int width() {
		return (int) getBounds().getWidth();
	}
	public int height() {
		return (int) getBounds().getHeight();
	}
	public int centerX() {
		return (int) getBounds().getX() + width()/2;
	}
	public int centerY() {
		return (int) getBounds().getY() + height()/2; //make sure should be minus
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
	public boolean intersects(NotStupidPolygon that) {
		return intersects(that.getBounds2D());
	}
	public void move(int deltaX, int deltaY) {
		translate(deltaX, deltaY);
		for (int i = 0; i < n; i++) {
			x[i] += deltaX;
			y[i] += deltaY;
		}
	}

}