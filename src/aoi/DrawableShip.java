package aoi;

import java.awt.Shape;
import java.awt.Color;
import java.awt.Polygon;
import java.awt.Paint;
import java.awt.Stroke;

/**
 *  A GUI element for a ship.
 */
public class DrawableShip extends DrawablePlayerComponent {

  // Last known score.
  private int lastScore = 0;

  // Counter for pwnage effects.
  // pwnage == score decreased.
  private int pwned = 0;

  // Time duration of the pwnage effect in turns.
  private final int PWNAGETIME = 1500 / SimulationUtils.TIMESTEP;
  // The color of the ship during the effect.
  private final Color PWNAGECOLOR = Color.GREEN;


  // The color of the outline of the ship.
  private Color color;

  public DrawableShip(int playerNo, Stroke stroke) {
    super(playerNo, stroke);
    
    color = DrawablePlayerComponent.getColor(playerNo);
  }

  // Return the polygon to be drawn.
  public Shape[] shapes(GameState game) {

    if (game.states[playerNo].score < lastScore)
      pwned = PWNAGETIME;
    lastScore = game.states[playerNo].score;

    int[][] xy = game.states[playerNo].ship.shape();
    Polygon pol = new Polygon(xy[0], xy[1], xy[0].length);

    return new Shape[]{pol, pol};
  }

  // Return the pen color used to draw the shape.
  public Paint[] paint() {

    assert(pwned >= 0);

    if (pwned == 0)
      return new Paint[]{color.darker().darker().darker(), color};
    else {
      pwned--;
      return new Paint[]{PWNAGECOLOR, PWNAGECOLOR};
    }

  }

  public boolean[] fill() { return new boolean[]{true, false}; }

  public Stroke[] stroke() { return new Stroke[]{stroke, stroke}; }

}
