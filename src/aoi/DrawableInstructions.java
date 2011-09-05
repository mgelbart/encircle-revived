package aoi;

/**
 * A GUI element that indicates the next cluster to encircle.
 */
public class DrawableInstructions extends DrawableMessage {

  private int prevScore = 0;
  private long prevChangeAt = System.currentTimeMillis();
  private long minDuration = 2000;
  private long REMINDPERIOD = 30000; // how long to wait between scoring events before reminding what the point is

  // counters that keep track of how many different info messages have been shown.
  private int crossingInfoCount = 0;
  private int scoringInfoCount = 0;
  private int shotInfoCount = 0;
  private int pwnedInfoCount = 0;
  private int shootingInfoCount = 0;
  
  public DrawableInstructions() {
    super();
    setMsg("Encircle a cluster with "+Arena.current().sequence()[0]+" cells.", 5000, 15, false);
  }

  public void prepare(GameState gState) {
    final PlayerState p = gState.pStates[ClientConfiguration.playerNo]; 
    final long time = System.currentTimeMillis(); 
    
    // Don't overwrite messages before 'duration' is over.
    if (time - prevChangeAt < minDuration) return;
    
    if (p.score != prevScore) {
      final int[] seq = Arena.current().sequence();
      String msg;
      int dur;
      int fsize;
      if (p.score >= seq.length) {
        msg = "Awesome! You won!";
        dur = 5000;
        minDuration = dur;
        fsize = 35;
      } else if (p.sincePwned < 20 && p.score == 0) {
        if (pwnedInfoCount < 2)
          msg = "You got encircled! Start from "+seq[0]+" again.";
        else
          msg = "pwned again! Start from "+seq[0]+".";
        dur = 5000;
        minDuration = 3000;
        fsize = 15;
        ++pwnedInfoCount;
      } else {
        if (scoringInfoCount == 0) {
          msg = "Good! Now get a "+seq[p.score]+".";
          dur = 5000;
          minDuration = dur;
          fsize = 20;
          ++scoringInfoCount;
        } else {
          msg = ""+seq[p.score];
          dur = 3000;
          minDuration = dur;
          fsize = 70;
        }
      }
      
      setMsg(msg, dur, fsize, false);
      prevScore = p.score;
      prevChangeAt = time;
      return;
    }
    
    if (p.sinceHitByBullet < 20 && shotInfoCount < 2) {
      minDuration = 4000;
      setMsg("Getting shot takes you back to your last knot.", 5000, 15, false);
      prevChangeAt = time;
      ++shotInfoCount;
      return;
    }
    
    if (p.sinceCrossing < 20 && crossingInfoCount < 2) {
      minDuration = 4000;
      setMsg("You lose part of your tail when you cross someone else's tail.", 5000, 15, false);
      prevChangeAt = time;
      ++crossingInfoCount;
      return;
    }
    
    if (p.untilReload > 0 && shootingInfoCount < 1) {
      minDuration = 4000;
      setMsg("Note that shooting costs some of your tail.", 5000, 15, false);
      prevChangeAt = time;
      ++shootingInfoCount;
      return;
    }

    if (shootingInfoCount == 0 && time - prevChangeAt > REMINDPERIOD/2) {
      minDuration = 4000;
      setMsg("You can also press space to shoot.", 5000, 15, false);
      prevChangeAt = time;
      return;
    }

    if (time - prevChangeAt > REMINDPERIOD*(1+p.score/2)) {
      final int[] seq = Arena.current().sequence();
      if (p.score < seq.length) {
        setMsg("Loop around a cluster with "+seq[p.score]+" cells.", 10000, 15, false);
        minDuration = 1000;
      }
      prevChangeAt = time;
      return;
    }

    // Clear old messages
    if (time - msgSetAt > duration)
      setMsg(null, 0, 0, false);

  }
  
}
