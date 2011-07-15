package aoi;

import java.applet.AudioClip;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.BasicStroke;

/**
 *  A GUI element for the special FX shown when you score.
 */
public class ScoringEffect extends DefaultDrawable implements Audible {

  int prevSinceScoring = Integer.MIN_VALUE;
  
  private Tail scoringTail = null;
  private int scored;
  
  // Time duration of the scoring effect in turns.
  private final int SCORINGTIME = 1500 / SimulationUtils.TIMESTEP;
  // The number of pixels the loop is fattened to when scoring.
  private final float SCORINGWIDTH = 10.f;


  public ScoringEffect(int playerNo, Stroke stroke) {
    super(playerNo, stroke);
  }
  
  public void postpare(GameState gState) { 
    prevSinceScoring = gState.pStates[playerNo].sinceScoring;
  }
  
  

  // Return the path to be drawn.
  public Shape[] shapes(GameState gState) {
    final PlayerState s = gState.pStates[playerNo];
    
    if (s.sinceScoring < prevSinceScoring) {  // you just scored
      scored = SCORINGTIME;
      
      // crop the non-loop part from the scoring effect.
      Logger.println(Logger.RepetitionRate.INFREQUENT, "Cropping " + s.tailStarted + ":" + s.cropTailFrom);
      scoringTail = scoringTail.crop(s.tailStarted, s.cropTailFrom);
      
      return new Shape[]{scoringTail.toPath2D()};
    } else if (scored == 0) {    // normal time
      scoringTail = s.tail;
      return null;
    } else {                  // while the effect is showing
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
  

  public AudioClip clip(GameState gState) {
    if (gState.pStates[playerNo].sinceScoring < prevSinceScoring)
      return StdAudio.takeShot;
    else return null;
  }
  

}
