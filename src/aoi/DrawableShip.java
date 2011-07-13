package aoi;

import java.awt.Shape;
import java.awt.Color;
import java.awt.Polygon;
import java.awt.Paint;
import java.awt.Stroke;

/**
 *  A GUI element for a ship.
 */
public class DrawableShip extends DefaultDrawable {

  // Last known score.
//  private int lastScore = 0;
  private PlayerState lastState;
  
  // Counter for pwnage effects.
  private int pwnedCounter = 0;
  
  // If you were shot
  private int shotCounter = 0;

  // Time duration of the pwnage effect in turns.
  private final int PWNAGETIME = 1500 / SimulationUtils.TIMESTEP;
  
  // Time duration of the getting shot effect in turns.
  private final int SHOTPWNTIME = 800 / SimulationUtils.TIMESTEP;
  
  // The color of the ship during the effect.
  private final Color PWNAGECOLOR = Color.GREEN;


  // The color of the outline of the ship.
  private Color color;

  public DrawableShip(int playerNo, Stroke stroke) {
    super(playerNo, stroke);
    
    color = DefaultDrawable.getColor(playerNo);
  }

  // Return the polygon to be drawn.
  public Shape[] shapes(GameState game) {
    PlayerState currentState = game.states[playerNo];
    
    if (lastState != null) {
      if (currentState.score < lastState.score)
        pwnedCounter = PWNAGETIME;
      if (PlayerState.deadOrShot(lastState, currentState.tailStarted, 
          currentState.cropTailFrom, currentState.cropTailTo))
        shotCounter = shotCounter==0?SHOTPWNTIME:shotCounter;
    }
    lastState = currentState;

    int[][] xy = currentState.ship.shape();
    Polygon pol = new Polygon(xy[0], xy[1], xy[0].length);
    return new Shape[]{pol, pol};
  }

  // Return the pen color used to draw the shape.
  public Paint[] paint() {

    assert(pwnedCounter >= 0);
    assert(shotCounter >= 0);

    if (shotCounter == 0 && pwnedCounter == 0) {
      return new Paint[]{color.darker().darker().darker(), color};
    }  else if (pwnedCounter > 0) {
      shotCounter = 0;
      pwnedCounter--;
      return new Paint[]{PWNAGECOLOR, PWNAGECOLOR};
    } else { // if you were shot (but not pwned)
      shotCounter--;
      return new Paint[]{PWNAGECOLOR, PWNAGECOLOR};
    }
  }

  public boolean[] fill() { return new boolean[]{true, false}; }

  public Stroke[] stroke() { return new Stroke[]{stroke, stroke}; }

}
