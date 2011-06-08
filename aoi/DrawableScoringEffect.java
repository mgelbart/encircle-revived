package aoi;

import java.awt.geom.Path2D;
import java.awt.Shape;
import java.awt.Color;
import java.awt.Polygon;

/**
 *  A GUI element for the special FX shown when you score.
 */
public class DrawableScoringEffect extends DrawablePath {

  private int playerNo;

  // Time when the current loop was started.
  private int currentLoopStarted = 0;

  // Last known score.
  private int lastScore = 0;

  // Counter for scoring effects.
  private int scored = 0;
  private Path2D scoringLoop = null;

  // Time duration of the scoring effect in turns.
  private final int SCORINGTIME = 1500 / Simulation.TIMESTEP;
  // The factor by which the loop is fattened during the effect.
  private final double SCORINGWIDTH = 8.;


  public DrawableScoringEffect(int playerNo, Color color, double width) {
    super(new Path2D.Double(), color, width);

    this.playerNo = playerNo;
  }

  // Return the path to be drawn.
  public Shape[] shapes(GameState game) {
    final PlayerState s = game.states[playerNo];

    if (s.score > lastScore) {
      scored = SCORINGTIME;
      scoringLoop = path;
    }
    lastScore = s.score;

    if (currentLoopStarted != s.loopStarted) {
      path = new Path2D.Double();
      currentLoopStarted = s.loopStarted;
    }

    if (path.getCurrentPoint() == null) {
      path.moveTo(s.pos.x, s.pos.y);
    } else {
      path.lineTo(s.pos.x, s.pos.y);
    }

    if (scored > 0)
      return new Shape[]{scoringLoop};
    else
      return null;

  }

  // Return the pen width used to draw the shape.
  public double[] penWidth() {

    assert(scored >= 0);

    if (scored == 0) {

      return new double[]{width};

    } else {
      scored--;

      assert(scored < SCORINGTIME);

      return new double[]
        {width*SCORINGWIDTH * (SCORINGTIME - scored) / (double)SCORINGTIME};
    }

  }

}
