package aoi;

import java.awt.*;

/**
 *  A GUI element for a tail.
 */
public class DrawableTail extends DrawablePlayerComponent {


  public DrawableTail(int playerNo, Stroke stroke) {
    super(playerNo, stroke);
  }

  // Return the path to be drawn.
  public Shape[] shapes(GameState game) { 
    return new Shape[]{game.states[playerNo].tail.toPath2D()};
  }

}
