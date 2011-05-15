package aoi;

/*
  This class contains static functions used by
  the server to calculate certain quantities.

  These are provided to the clients so that there
  is no need to reverse engineer any parameters.
 */

public class SimulationUtils {

    /* Returns an array of {x, y, vx, vy, direction} after
       a single time step given an initial state and
       control parameters.

       Note that this in particular does not tell you
       if you hit something (a boundary, cluster, bullet).
     */
    public static int[] nextPosition(PlayerState state,
				     PlayerControlParameters params) {

	Double a = Math.max(0.0, Simulation.MAXACC * (params.acceleration / (double) Simulation.INTMAX));

	int vx = state.vx + (int) (a * Math.cos(Math.PI * (state.direction / (double) Simulation.INTMAX)));
	int vy = state.vy + (int) (a * Math.sin(Math.PI * (state.direction / (double) Simulation.INTMAX)));

	vx -= Simulation.DRAG * vx;
	vy -= Simulation.DRAG * vy;

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

	return new int[]{x, y, vx, vy, dir};
    }

    /*
      Returns an array of {x, y, vx, vy, direction} after
      'interpolationTime' time steps given an initial state
      and control parameters.
      
      Note that this function does not tell you
      if you hit something (a boundary, cluster, bullet).
     */
    public static int[] interpolatePosition(PlayerState state, PlayerControlParameters params, int interpolationTime) {

	if (interpolationTime > 0) {
	    int[] pos = nextPosition(state, params);

	    for (int j=1; j<interpolationTime; ++j)
		pos = nextPosition(new PlayerState(0, pos[0], pos[1],
						   pos[2], pos[3],
						   pos[4], 0, 0, 0),
				   params);

	    return pos;

	} else {
	    return new int[]{state.x, state.y, state.vx, state.vy, state.direction};
	}

    }


}