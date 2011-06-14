package aoi;

/**
 * This class contains static functions used by
 * the server to calculate certain quantities.
 *
 * These are provided to the clients so that there
 * is no need to reverse engineer any parameters.
 */
public abstract class SimulationUtils {

  /* Returns an array of {x, y, vx, vy, direction} after
     a single time step given an initial state and
     control parameters.

     Note that this in particular does not tell you
     if you hit something (a boundary, cluster, bullet).
  */
  public static PlayerState.Position nextPosition(PlayerState.Position state,
                                                  PlayerControlParameters params) {
    assert(state != null);
    assert(params != null);

    // Acceleration
    double a = Math.max(0.0, Simulation.MAXACC * (params.acceleration / (double) Simulation.INTMAX));
    
    // Angle the ship is currently pointing, in [-PI, +PI]
    double angleFacing = Math.PI * (state.direction / (double) Simulation.INTMAX);

    // Currently, turning does not "slow you down". This might not be good
    int vx = state.vx + (int) (a * Math.cos(angleFacing));
    int vy = state.vy + (int) (a * Math.sin(angleFacing));
/*
    // Drag increases with the angle between your current direction and the direction you're facing,
    // to represent the fact that you are more aerodynamic facing forwards. This also helps to slow 
    // you down on turns, making it easier to navigate around obstacles.
    // if (a < 1) {
  // the larger this is, the crazier the effect will be 
    double PERSISTENCE_FACTOR = 1.0;
    double drag = Simulation.DRAG * (0.6 + Math.pow(angleDifference(angleFacing, Math.atan2(vy, vx)), PERSISTENCE_FACTOR));  // * scaled by this angle!
    System.out.println("drag factor: " + drag/Simulation.DRAG);
*/
// I ultimately decided the above is stupid because I realized that the special ability to 
// speed boost will create exactly this effect that I want, since it will add some
// fixed factor to your speed. Thus I just need to make the drag bigger to get the effect
// I want. 
    double drag = Simulation.DRAG;
    
    
    vx -= drag * vx;
    vy -= drag * vy;
    
    int x = state.x + vx;
    int y = state.y + vy;

    // Figure out whether to turn clockwise or counterclockwise.
    boolean turnCcw =   Math.abs(state.direction + Simulation.OMEGA - params.targetDirection)
      < Math.abs(state.direction - Simulation.OMEGA - params.targetDirection);
    int dir = state.direction + ((turnCcw)? 1:-1)*Simulation.OMEGA;

    // Don't go past the target direction.
    if (Integer.signum(state.direction - params.targetDirection)
        != Integer.signum(dir - params.targetDirection))
      dir = params.targetDirection;

    return new PlayerState.Position(x, y, vx, vy, dir);
  }


  /* Returns an array of {x, y} after a single time step
     given an initial bullet state.

     Note that this in particular does not tell you
     if the bullet hit something (a boundary, cluster).

     If b is null this return an empty
     int[] of the correct size.

  */
  public static int[] nextBulletPosition(Bullet b) {

    if (b == null) return new int[2];

    int x = b.x + (int) (((double) Bullet.SPEED) * Math.cos(Math.PI * (b.direction / (double) Simulation.INTMAX)));
    int y = b.y + (int) (((double) Bullet.SPEED) * Math.sin(Math.PI * (b.direction / (double) Simulation.INTMAX)));

    return new int[]{x, y};
  }

  // Returns the angle between three points (0-1-2). Angle in [0 pi)
  public static double angle(double x0, double y0, double x1, double y1, double x2, double y2) {
    // System.out.println("** " + x0 + " " + y0 + " " + x1 + " " + y1 + " " + x2 + " " + y2); 
    // Set (x1, y1) to origin
    x0 -= x1;
    y0 -= y1;
    x2 -= x1;
    y2 -= y1;
    // Set vectors 0 and 2 to unit length
    double len0 = Math.sqrt(x0*x0+y0*y0);
    double len2 = Math.sqrt(x2*x2+y2*y2);
    x0 /= len0;
    y0 /= len0;
    x2 /= len2;
    y2 /= len2;
    // Return arc cosine of vector 0 dot vector 2
    return Math.acos(x0*x2+y0*y2);
  }
  public static double angleDifference(double a, double b) {
    return Math.min(Math.abs(a-b),(2.*Math.PI)-Math.abs(a-b));
  }
}
