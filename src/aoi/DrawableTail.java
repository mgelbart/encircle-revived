package aoi;

import java.awt.*;

/**
 *  A GUI element for a tail.
 */
public class DrawableTail extends DefaultDrawable {


  public DrawableTail(int playerNo, Stroke stroke) {
    super(playerNo, stroke);
  }

  // Return the path to be drawn.
  public Shape[] shapes(GameState gState) {
    Tail tail = gState.pStates[playerNo].tail;
    Shape[] shapes = new Shape[1+tail.nKnots()];
    for (int j = 0; j < tail.nKnots(); j++)
      shapes[j] = null;
    shapes[tail.nKnots()] = tail.toPath2D();
    return shapes;
  }

}
