package aoi;

import java.awt.Point;
import java.awt.geom.Line2D;

/** 
 * A "read-only" class that describes a player's
 * position in phase space.
 *
 * Note that ints are used although floats might be
 * more natural. This is because ints don't need
 * any special serialization/parsing to be transmitted
 * over the network.
*/
public class Ship {

  public static final int SHIPLENGTH = SimulationUtils.ARENASIZE * 7/200;
  public static final int SHIPWIDTH = SimulationUtils.ARENASIZE * 4/200;

  private final int x;
  private final int y;
  private final int vx;
  private final int vy;
  private final Angle direction;
  
  private final NSPath path;  // cache the path for intersection checking
  
  
  public Ship(Point p, int vx, int vy, Angle direction) {
    assert(p != null);
    assert(direction != null);
    
    this.x = p.x;
    this.y = p.y;
    this.vx = vx;
    this.vy = vy;
    this.direction = direction;
    
    path = new NSPath();
    path.addPoints(shape());
    path.close();
  }

  
  
  public int x() { return x; }
  public int y() { return y; }
  public int vx() { return vx; }
  public int vy() { return vy; }
  public Angle dir() { return direction; }
  public Point location() { return new Point(x, y); }
  public double speed() { return Math.pow(Math.pow(vx, 2) + Math.pow(vy, 2), 0.5); }
  
  public final NSPath getNSPath() { return path; }
  
  public void assertEquals(Ship that) {
    assert(this.x == that.x);
    assert(this.y == that.y);
    assert(this.vx == that.vx);
    assert(this.vy == that.vy);
    assert(this.direction.equals(that.direction));
    assert(this.path.equals(that.path));
  }
  public boolean equals(Ship that) {
    if (this.x != that.x) return false;
    if (this.y != that.y) return false;
    if (this.vx != that.vx) return false;
    if (this.vy != that.vy) return false;
    if (!this.direction.equals(that.direction)) return false;
    if (!this.path.equals(that.path)) return false;
    return true;
  }

  public boolean intersects(Line2D line) {
    return path.intersects(line) >= 0;
  }
  public boolean intersects(Bullet b) {
    return intersects(b.getLine());
  }


  private Point[] shape() {
    double cos = Math.cos(direction.toDouble());
    double sin = Math.sin(direction.toDouble());

    int deltaX, deltaY;
    Point[] p = new Point[3];

    for (int j=0; j<p.length; ++j) {
      // Generate one of the three corners relative to origin.
      deltaX = ((j==1) ? 1:-1) * SHIPLENGTH / 2;
      deltaY = (j-1) * SHIPWIDTH / 2;

      // Rotate each point by direction (multiply by {{cos -sin} {sin cos}})
      // and translate to player location.
      p[j] = new Point(x + (int) (cos*deltaX - sin*deltaY),
                       y + (int) (sin*deltaX + cos*deltaY));
    }
    return p;
  }
  
  
}
