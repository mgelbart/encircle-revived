package aoi;

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

  
  public int x;
  public int y;
  public int vx;
  public int vy;
  public int direction;
  
  public Ship(int x, int y, int vx, int vy, int direction) {
    this.x = x;
    this.y = y;
    this.vx = vx;
    this.vy = vy;
    this.direction = direction;
  }



  public int[][] shape() {
    double cos = Math.cos(Math.PI * (direction / (double) SimulationUtils.INTMAX));
    double sin = Math.sin(Math.PI * (direction / (double) SimulationUtils.INTMAX));

    int x, y;

    int px[] = new int[3];
    int py[] = new int[3];

    for (int j=0; j<3; ++j) {

      // Generate one of the three corners relative to origin.
      x = ((j==1) ? 1:-1) * SHIPLENGTH / 2;
      y = (j-1) * SHIPWIDTH / 2;

      // Rotate each point by direction (multiply by {{cos -sin} {sin cos}})
      // and translate to player location.
      px[j] = x + (int) (cos*x - sin*y);
      py[j] = y + (int) (sin*x + cos*y);

    }

    return new int[][]{px, py};
  }
  
  
}
