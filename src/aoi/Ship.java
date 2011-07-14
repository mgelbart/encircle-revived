package aoi;

import java.awt.Point;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;

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
  private final int direction;  // after dividing by INTMAX and multiplying by pi, on [-pi, pi]
  
  private final NSPath path;  // cache the path for intersection checking
  
  public int x() { return x; }
  public int y() { return y; }
  public int vx() { return vx; }
  public int vy() { return vy; }
  public int dir() { return direction; }
  public Point location() { return new Point(x, y); }
  
  public final NSPath getNSPath() { return path; }
  
  public Ship(Ship that) {
    this(that.x, that.y, that.vx, that.vy, that.direction);
  }
  
  public Ship(Point2D p, int vx, int vy, int direction) {
    this((int)p.getX(), (int)p.getY(), vx, vy, direction);
  }
  
  public Ship(int x, int y, int vx, int vy, int direction) {
    this.x = x;
    this.y = y;
    this.vx = vx;
    this.vy = vy;
    this.direction = direction;
    
    path = new NSPath();
    int[][] shape = shape();
    path.addPoint(shape[0][0], shape[1][0]);
    path.addPoint(shape[0][1], shape[1][1]);
    path.addPoint(shape[0][2], shape[1][2]);
  }

  
  public boolean intersects(Line2D line) {
    return path.intersects(line) >= 0;
  }
  public boolean intersects(Bullet b) {
    return intersects(b.getLine());
  }


  public int[][] shape() {
    double cos = Math.cos(Math.PI * (direction / (double) SimulationUtils.INTMAX));
    double sin = Math.sin(Math.PI * (direction / (double) SimulationUtils.INTMAX));

    int deltaX, deltaY;

    int px[] = new int[3];
    int py[] = new int[3];

    for (int j=0; j<3; ++j) {

      // Generate one of the three corners relative to origin.
      deltaX = ((j==1) ? 1:-1) * SHIPLENGTH / 2;
      deltaY = (j-1) * SHIPWIDTH / 2;

      // Rotate each point by direction (multiply by {{cos -sin} {sin cos}})
      // and translate to player location.
      px[j] = x + (int) (cos*deltaX - sin*deltaY);
      py[j] = y + (int) (sin*deltaX + cos*deltaY);

    }

    return new int[][]{px, py};
  }
  
  
}
