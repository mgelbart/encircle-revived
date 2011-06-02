package aoi;

/*
  A GUI element for a ship.
 */


import java.awt.geom.Path2D;
import java.awt.Shape;
import java.awt.Color;
import java.awt.Polygon;

public class DrawableShip extends DrawablePath {

    private int playerNo;

    // Last known score.
    private int lastScore = 0;

    // Counter for pwnage effects.
    // pwnage == score decreased.
    private int pwned = 0;

    // Time duration of the pwnage effect in turns.
    private final int PWNAGETIME = 1500 / Simulation.TIMESTEP;
    // The color of the ship during the effect.
    private final Color PWNAGECOLOR = stdlib.StdDraw.GREEN;


    public DrawableShip(int playerNo, Color color, double width) {
	super(null, color, width);

	this.playerNo = playerNo;
    }

    // Return the polygon to be drawn.
    public Shape[] shapes(GameState game) {

	if (game.states[playerNo].score < lastScore)
	    pwned = PWNAGETIME;
	lastScore = game.states[playerNo].score;

	int[][] xy = game.states[playerNo].shape();
	Polygon pol = new Polygon(xy[0], xy[1], xy[0].length);

	return new Shape[]{pol};
    }

    // Return the pen color used to draw the shape.
    public Color[] color() {

	assert(pwned >= 0);

	if (pwned == 0)
	    return new Color[]{color};
	else {
	    pwned--;
	    return new Color[]{PWNAGECOLOR};
	}

    }

    public boolean[] fill() { return new boolean[]{true}; }

}
