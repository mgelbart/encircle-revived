package aoi;

/**
 * This class contains static functions used by
 * the server to calculate certain quantities.
 *
 * These are provided to the clients so that there
 * is no need to reverse engineer any parameters.
 */
public abstract class SimulationUtils {
 
  // The range of representable integer values for various coordinates.
  public static final int INTMAX = Integer.MAX_VALUE;
  public static final int INTMIN = Integer.MIN_VALUE;

  // Advance the simulation every TIMESTEP milliseconds.
  // This will not be respected if the computation takes longer
  // than TIMESTEP.
  //
  // All other times are defined in terms of TIMESTEP. Hence
  // decreasing this only increases resolution.
  public static final int TIMESTEP = 40;
 
  // All distances are defined in terms of ARENASIZE.
  // Hence increasing this only increases the resolution.
  public static final int ARENASIZE = 100000;

  // Maximum angular turning rate (i.e. domega/dt).
  // Angle is measured in units of 2*pi / (|INTMAX| + |INTMIN|).
  public static final int OMEGA = (INTMAX/1000) * TIMESTEP;

  // Maximum thrust, i.e. max dx/dt^2.
  public static final int MAXACC = (int) ((ARENASIZE / 1e7 * 5) * TIMESTEP * TIMESTEP) ;
 
  // Drag coefficient.
  public static final double DRAG = 0.0025 * TIMESTEP;

  /* The bullet speed is a universal constant. */
  public static final int BULLETSPEED = ARENASIZE * TIMESTEP / 1500;

  /* Minimum time between firing two bullets. */
  public static final int RELOADTIME = 200 / TIMESTEP;
  
  /* Length of path required to spend in order to shoot.  */
  public static final double BULLETCOST = ARENASIZE / 20.;
  
  // Time to wait until respawning.
  public static final int RESPAWNTIME = 1000 / TIMESTEP;

  // Energy recharge period (i.e. how many turns to gain one point).
  public static final int RECHARGEPERIOD = 1000 / TIMESTEP;

  // The number of players in the game. Currently not adjustable.
  public static final int NUMPLAYERS = 2;

  /*
   * TODO: Should write a proper constructor and/or accessor
   *       method for Simulation that can be called from
   *       Server instead of using all these public fields.
   */
 
 
  /* Returns an array of {x, y, vx, vy, direction} after
     a single time step given an initial state and
     control parameters.

     Note that this in particular does not tell you
     if you hit something (a boundary, cluster, bullet).
  */
  public static Ship nextPosition(Ship ship, PlayerControlParameters params) {
    assert(ship != null);
    assert(params != null);

    // Acceleration
    double a = Math.max(0.0, MAXACC * (params.acceleration / (double) INTMAX));
    
    // Angle the ship is currently pointing, in [-PI, +PI]
    double angleFacing = Math.PI * (ship.dir() / (double) INTMAX);

    // Currently, turning does not "slow you down". This might not be good
    int vx = ship.vx() + (int) (a * Math.cos(angleFacing));
    int vy = ship.vy() + (int) (a * Math.sin(angleFacing));
/*
    // Drag increases with the angle between your current direction and the direction you're facing,
    // to represent the fact that you are more aerodynamic facing forwards. This also helps to slow 
    // you down on turns, making it easier to navigate around obstacles.
    // if (a < 1) {
  // the larger this is, the crazier the effect will be 
    double PERSISTENCE_FACTOR = 1.0;
    double drag = Simulation.DRAG * (0.6 + Math.pow(angleDifference(angleFacing, Math.atan2(vy, vx)), PERSISTENCE_FACTOR));  // * scaled by this angle!
    Logger.println(Logger.RepetitionRate.FREQUENT, "drag factor: " + drag/Simulation.DRAG);
*/
// I ultimately decided the above is stupid because I realized that the special ability to 
// speed boost will create exactly this effect that I want, since it will add some
// fixed factor to your speed. Thus I just need to make the drag bigger to get the effect
// I want. 
    double drag = DRAG;
    
    
    vx -= drag * vx;
    vy -= drag * vy;
    
    int x = ship.x() + vx;
    int y = ship.y() + vy;

    // Figure out whether to turn clockwise or counterclockwise.
    boolean turnCcw =   Math.abs(ship.dir() + OMEGA - params.targetDirection)
      < Math.abs(ship.dir() - OMEGA - params.targetDirection);
    int dir = ship.dir() + ((turnCcw)? 1:-1)*OMEGA;

    // Don't go past the target direction.
    if (Integer.signum(ship.dir() - params.targetDirection)
        != Integer.signum(dir - params.targetDirection))
      dir = params.targetDirection;

    return new Ship(x, y, vx, vy, dir);
  }

  public static boolean gotShot(PlayerState p, Bullet b) {
    return b.playerNo != p.playerNo && p.ship.intersects(b);
  }
  

  /* Returns the next bullet position after a single time step
     given an initial bullet state.

     Note that this in particular does not tell you
     if the bullet hit something (a boundary, cluster).
  */
  public static Bullet nextBulletPosition(Bullet b) {

    int x = b.x + (int) (((double) BULLETSPEED) * Math.cos(Math.PI * (b.direction / (double) INTMAX)));
    int y = b.y + (int) (((double) BULLETSPEED) * Math.sin(Math.PI * (b.direction / (double) INTMAX)));

    return new Bullet(b.playerNo, x, y, b.direction);
  }

  public static boolean isInArena(Bullet b, Arena a) {
    if (isOutOfBounds(b.x, b.y)) return false;

    // hit a cluster
    for (Cluster c : a.clusters)
      if (c.intersects(b))
        return false;
    
    return true;
  }

  public static boolean isInArena(Ship s, Arena a) {
    if (isOutOfBounds(s.x(), s.y())) return false;

    // hit a cluster
    for (Cluster c : a.clusters)
      if (c.intersects(s))
        return false;
    
    return true;
  }

  public static boolean isInArena(int x, int y, Arena a) { 
    if (isOutOfBounds(x, y)) return false;

    // hit a cluster
    for (Cluster c : a.clusters)
      if (c.contains(x, y))
        return false;

    return true;
  }

  //outside the map edges
  private static boolean isOutOfBounds(int x, int y) {
    return (Math.abs(x) > 2*SimulationUtils.ARENASIZE || Math.abs(y) > 2*SimulationUtils.ARENASIZE);
  }
  
  // Returns the angle between three points (0-1-2). Angle in [0 pi)
  public static double angle(double x0, double y0, double x1, double y1, double x2, double y2) {
    // Logger.println(Logger.RepetitionRate.FREQUENT, "** " + x0 + " " + y0 + " " + x1 + " " + y1 + " " + x2 + " " + y2); 
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
