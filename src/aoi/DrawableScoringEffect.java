package aoi;

import java.awt.geom.Path2D;
import java.awt.Shape;
import java.awt.Paint;
import java.awt.Stroke;
import java.awt.BasicStroke;

/**
 *  A GUI element for the special FX shown when you score.
 */
public class DrawableScoringEffect extends DrawablePlayerComponent {

  // Last known score.
  private int lastScore = 0;

  // Counter for scoring effects.
  private int scored = 0;
  private Path2D scoringLoop = null;

  // Time duration of the scoring effect in turns.
  private final int SCORINGTIME = 1500 / SimulationUtils.TIMESTEP;
  // The number of pixels the loop is fattened to when scoring.
  private final float SCORINGWIDTH = (float) 10.;


  public DrawableScoringEffect(int playerNo, Stroke stroke) {
    super(playerNo, stroke);
  }

  // Return the path to be drawn.
  public Shape[] shapes(GameState game) {
    final PlayerState s = game.states[playerNo];

    if (s.score > lastScore) {
      scored = SCORINGTIME;
      scoringLoop = s.tail.toPath2D();
    }
    lastScore = s.score;

    if (scored > 0)
      return new Shape[]{scoringLoop};
    else
      return null;

  }

  // Return the pen stroke used to draw the shape.
  public Stroke[] stroke() {

    assert(scored >= 0);

    if (scored == 0) {

      return new Stroke[]{stroke};

    } else {
      scored--;

      assert(scored < SCORINGTIME);

      return new Stroke[]
        { new BasicStroke(SCORINGWIDTH * (SCORINGTIME - scored) / (float)SCORINGTIME) };
    }

  }

}
