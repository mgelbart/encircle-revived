package aoi;

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
   * Return the angle in radians on the interval -PI...PI.
   */
  public double toDouble() {
    return Math.PI * (dir / (double) Integer.MAX_VALUE);
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
  
  /*
   * The difference between this angle and that angle.
   * Signed. In the interval -Pi...Pi.
   */
  public double dtheta(Angle that) {
    return ((dir - that.dir) / (double) Integer.MAX_VALUE) * Math.PI;
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
