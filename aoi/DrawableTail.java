package aoi;

import java.awt.geom.Path2D;
import java.awt.Shape;
import java.awt.Color;
import java.awt.Polygon;

/**
 *  A GUI element for a tail.
 */
public class DrawableTail extends DrawablePath {

  private int playerNo;

  // Time when the current loop was started.
  private int currentLoopStarted = 0;


  public DrawableTail(int playerNo, Color color, double width) {
    super(new Path2D.Double(), color, width);

    this.playerNo = playerNo;
  }

  // Return the path to be drawn.
  public Shape[] shapes(GameState game) {
    final PlayerState s = game.states[playerNo];

    if (currentLoopStarted != s.loopStarted) {
      path = new Path2D.Double();
      currentLoopStarted = s.loopStarted;
    }

    if (path.getCurrentPoint() == null) {
      path.moveTo(s.pos.x, s.pos.y);
    } else {
      path.lineTo(s.pos.x, s.pos.y);
    }

    return new Shape[]{path};
  }

}
