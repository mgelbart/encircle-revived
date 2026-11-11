package aoi;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.io.*;

/**
 * This class is used to represent an angle between -PI and PI.
 */
public class Angle {
  
  final int dir; // after dividing by INTMAX and multiplying by pi, this gives the angle on [-pi, pi]
  
  /* Initialize given an angle in radians.
   * 
   * @param theta the angle in radians
   */
  public Angle(double theta) {
    dir = (int) ( Math.IEEEremainder(theta/Math.PI, 2) * Integer.MAX_VALUE );
  }

  private Angle(int dir) {
    this.dir = dir;
  }
  
  /*
   * The polar angle of the vector from point p1 to p2
   */
  public Angle(double x1, double y1, double x2, double y2) {
    this(Math.atan2(y2-y1, x2-x1));
  }
  public Angle(Point2D p1, Point2D p2) {
    this(p1.getX(), p1.getY(), p2.getX(), p2.getY()); 
  }
  public Angle (Point2D p) {
    this(p.getX(), p.getY());
  }
  public Angle(double x, double y) {
    this(0, 0, x, y);
  }
  
  public boolean equals(Angle that) {
    return this.dir == that.dir;
  }
  
  /* 
   * Return the angle in radians on the interval -PI...PI.
   */
  public double toDouble() {
    return Math.PI * (dir / (double) Integer.MAX_VALUE);
  }
  
  public double cos() {
    return Math.cos(toDouble());
  }
  
  public double sin() {
    return Math.sin(toDouble());
  }
  
  public static Angle uniformRandom() {
    return new Angle((Math.random()-0.5)*2*Math.PI);
  }
  
  public String toString() {
    return String.format("%f", this.toDouble());
  }
  
  /*
   * Return a new Angle with dtheta added to the current angle.
   * 
   * @param dtheta the angle to add.
   */
  public Angle add(double dtheta) {
    return new Angle(this.toDouble() + dtheta);
  }
  
  public Angle add(Angle that) {
    return new Angle(this.toDouble() + that.toDouble());
  }
  public Angle subtract(Angle that) {
    return new Angle(this.toDouble() - that.toDouble());
  }
  
  /*
   * The difference between this angle and that angle.
   * Signed. In the interval -Pi...Pi.
   */
  public double dtheta(Angle that) {
    return ((dir - that.dir) / (double) Integer.MAX_VALUE) * Math.PI;
  }
  
  /*
   * Move the source point in the direction specified by
   * this angle.
   */
  public Point move(Point src, double dist) {
    Point p = new Point(src.x, src.y);
    p.translate((int) (dist*cos()), (int) (dist*sin()));
    return p;
  }
  
  public void serialize(OutputStream out) throws IOException {
    DataOutputStream dout = new DataOutputStream(out);
    dout.writeInt(dir);
  }
  
  public static Angle parse(InputStream in) throws IOException {
    DataInputStream din = new DataInputStream(in);
    return new Angle(din.readInt());
  }
  
}
