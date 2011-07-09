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


}
