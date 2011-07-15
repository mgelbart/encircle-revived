package aoi;

import java.applet.AudioClip;

public class AudibleShot implements Audible {

  private boolean removed = false;
  
  int[] prevUntilReload;
  
  public AudioClip clip(GameState gState) {
    if (prevUntilReload == null) {
      // Special case for the first call
      prevUntilReload = new int[SimulationUtils.NUMPLAYERS];
      for (int j=0; j<SimulationUtils.NUMPLAYERS; j++)
        prevUntilReload[j] = gState.pStates[j].untilReload;
      return null;
    } else {
      AudioClip clip = null;
      
      for (int j=0; j<SimulationUtils.NUMPLAYERS; j++)
        if (prevUntilReload[j]==0 && gState.pStates[j].untilReload > 0)
          clip = StdAudio.takeShot;
      
      for (int j=0; j<SimulationUtils.NUMPLAYERS; j++)
        prevUntilReload[j] = gState.pStates[j].untilReload;
      
      return clip;
    }
  }

  
  
  public boolean isRemoved() { return removed; }
  public void remove()       { removed = true; }
  

}
