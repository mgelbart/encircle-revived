package aoi;

import java.applet.AudioClip;

public class AudibleCrash implements Audible {

  private boolean removed = false;
  
  int[] prevUntilRespawn;
  
  public AudioClip clip(GameState gState) {
    if (prevUntilRespawn == null) {
      // Special case for the first call
      prevUntilRespawn = new int[SimulationUtils.NUMPLAYERS];
      return null;
    } else {
      AudioClip clip = null;
      
      for (int j=0; j<SimulationUtils.NUMPLAYERS; j++)
        if (prevUntilRespawn[j] < gState.pStates[j].untilRespawn)
          clip = StdAudio.getShot;
      
      return clip;
    }
  }

  
  
  public boolean isRemoved() { return removed; }
  public void remove()       { removed = true; }
  
  public void prepare(GameState gState) { }
  public void postpare(GameState gState) { 
    for (int j=0; j<SimulationUtils.NUMPLAYERS; j++)
      prevUntilRespawn[j] = gState.pStates[j].untilRespawn;
  }  

}
