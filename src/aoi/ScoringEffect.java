package aoi;

import java.applet.AudioClip;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.BasicStroke;

/**
 *  A GUI element for the special FX shown when you score.
 */
public class ScoringEffect extends DefaultDrawable implements Audible {

  private int prevSinceScoring = -1;
  
  private Tail scoringTail = null;
  private int internalCounter = 0;
  
  // Time duration of the scoring effect in turns.
  private final int SCORINGTIME = 1500 / SimulationUtils.TIMESTEP;
  // The number of pixels the loop is fattened to when scoring.
  private final float SCORINGWIDTH = 10.f;

  public ScoringEffect(int playerNo, Stroke stroke) {
    super(playerNo, stroke);
  }
  
  
  public void prepare(GameState gState) {
    assert(internalCounter >= 0 || internalCounter <= SCORINGTIME);
  }
  public void postpare(GameState gState) { 
    if (internalCounter == 0 && gState.pStates[playerNo].sinceScoring - prevSinceScoring < 0) 
      internalCounter = SCORINGTIME;
    prevSinceScoring = gState.pStates[playerNo].sinceScoring;
  }


  // Return the path to be drawn.
  public Shape[] shapes(GameState gState) {
    final PlayerState s = gState.pStates[playerNo];

//    Logger.println(Logger.RepetitionRate.INFREQUENT, s.sinceScoring); 
    if (s == null) {   // first call
      return null;
    } else if (internalCounter == 0 && s.sinceScoring - prevSinceScoring < 0) {  // you just scored 
      // crop the non-loop part from the scoring effect.
//      Logger.println(Logger.RepetitionRate.INFREQUENT, "Cropping " + s.tailStarted + ":" + s.cropTailFrom);
      scoringTail = scoringTail.crop(s.tailStarted, s.cropTailFrom);
      scoringTail = scoringTail.crop(s.cropTailTo, gState.time + 1);
      
      return new Shape[]{scoringTail.toPath2D()};
    } else if (internalCounter == 0) {  // normal time
      scoringTail = s.tail;
      return null;
    } else {                   // while the effect is showing
      return new Shape[]{scoringTail.toPath2D()};
    }
  }

  // Return the pen stroke used to draw the shape.
  public Stroke[] stroke() {
    if (internalCounter == 0) return new Stroke[]{stroke};
    else {
      internalCounter--;
      return new Stroke[] 
        { new BasicStroke(SCORINGWIDTH * (SCORINGTIME - internalCounter) / (float)SCORINGTIME) };
    }

  }
  

  public AudioClip clip(GameState gState) {
    if (internalCounter == 0 && gState.pStates[playerNo].sinceScoring - prevSinceScoring < 0)
      return StdAudio.winGame;
    else return null;
  }
  

}
