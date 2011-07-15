package aoi;

import java.applet.AudioClip;

/**
 *  Interface for object passed to the GUI
 *  for sound-playing purposes.
 */
interface Audible {

  // Returns the image to be drawn, if any.
  // May depend on the current game state.
  public AudioClip clip(GameState gState);

  // Remove the object from the collection of
  // objects to be drawn.
  public void remove();

  // Remove the object from the collection of
  // objects to be drawn.
  public boolean isRemoved();

  public void prepare(GameState gState);
  public void postpare(GameState gState);
  
}
