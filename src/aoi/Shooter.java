package aoi;

import java.awt.geom.Line2D;

import aoi.Logger.Rep;

/**
 * A "passive" shooter module that says whether firing a bullet
 * at a given moment would be a good idea.
 */
public class Shooter {

  /*
   *  Returns true if shooting on the current turn would be likely to hit another player.
   *  
   *  @param now the current game state
   *  @param playerNo the player no assigned to self
   *  @param tm a reference to a time machine that can predict the future
   */
  public static boolean wouldHit(GameState current, int playerNo) {
    final PlayerState pState = current.pStates[playerNo];
    if (!SimulationUtils.canShoot(pState)) return false;
    
    Bullet b = new Bullet(playerNo, pState);
    
    final int RANGE = SimulationUtils.ARENASIZE/3;
    final int RANGEINSTEPS = (int) (RANGE / SimulationUtils.BULLETSPEED);
    
    for (int j=0; j < SimulationUtils.NUMPLAYERS; ++j) {
      if (j==playerNo) continue; // skip self
      
      final Line2D bulletLine = new Line2D.Double(b.location(), b.direction.move(b.location(), SimulationUtils.ARENASIZE));
      final double perpendicularDist = bulletLine.ptSegDist(current.pStates[j].ship.location());
      
      // don't even bother if the other player is in a completely different direction.
      if (perpendicularDist > RANGE/3) continue;
      
      /*
       * do a more thorough calculation
       */
      
      // shoot if the bullet will hit within RANGEINSTEPS time steps.
      Bullet btemp = b;
      for (int k=0; k < RANGEINSTEPS; ++k) {
        btemp = SimulationUtils.nextPosition(btemp);
        if (!SimulationUtils.isInArena(btemp, Arena.current())) continue;
        
        if (SimulationUtils.gotShot(TimeMachine.current().getState(current.time+k, MindReader.current()).pStates[j], btemp))
          return true;
      }
    }
    
    return false;
  }
  
}
