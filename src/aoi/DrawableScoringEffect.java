package aoi;

import java.awt.Shape;
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
  private Tail scoringTail = null;

  // Time duration of the scoring effect in turns.
  private final int SCORINGTIME = 1500 / SimulationUtils.TIMESTEP;
  // The number of pixels the loop is fattened to when scoring.
  private final float SCORINGWIDTH = 10.f;


  public DrawableScoringEffect(int playerNo, Stroke stroke) {
    super(playerNo, stroke);
  }

  // Return the path to be drawn.
  public Shape[] shapes(GameState game) {
    final PlayerState s = game.states[playerNo];
    
    if (s.score > lastScore) {  // you just scored
      scored = SCORINGTIME;
      lastScore = s.score;
      
      // crop the non-loop part from the scoring effect.
      Logger.println(Logger.RepetitionRate.INFREQUENT, "Cropping " + s.tailStarted + ":" + s.cropTailFrom);
      scoringTail = scoringTail.crop(s.tailStarted, s.cropTailFrom);
      
      return new Shape[]{scoringTail.toPath2D()};
    } else if (scored == 0) {    // normal time
      scoringTail = s.tail;
      lastScore = s.score;
      return null;
    } else {                  // while the effect is showing
      lastScore = s.score;
      return new Shape[]{scoringTail.toPath2D()};
    }
    
 
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
