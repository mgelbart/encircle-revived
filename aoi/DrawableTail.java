package aoi;

import java.awt.geom.Path2D;
import java.awt.*;

/**
 *  A GUI element for a tail.
 */
public class DrawableTail extends DrawablePath {

  private int playerNo;

  // Time when the current loop was started.
  private int currentLoopStarted = 0;


  public DrawableTail(int playerNo, Paint paint, Stroke stroke) {
    super(new Path2D.Double(), paint, stroke);

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
