package aoi;

import java.applet.AudioClip;

public class AudibleShot implements Audible {

  private boolean removed = false;
  
  int[] prevUntilReload;
  
  public AudioClip clip(GameState gState) {
    if (prevUntilReload == null) {
      // Special case for the first call
      prevUntilReload = new int[SimulationUtils.NUMPLAYERS];
      return null;
    } else {
      AudioClip clip = null;
      
      for (int j=0; j<SimulationUtils.NUMPLAYERS; j++)
        if (prevUntilReload[j] < gState.pStates[j].untilReload)
          clip = StdAudio.takeShot;
      
      return clip;
    }
  }

  
  
  public boolean isRemoved() { return removed; }
  public void remove()       { removed = true; }
  
  public void prepare(GameState gState) { }
  public void postpare(GameState gState) { 
    for (int j=0; j<SimulationUtils.NUMPLAYERS; j++)
      prevUntilReload[j] = gState.pStates[j].untilReload;
  }  

}
