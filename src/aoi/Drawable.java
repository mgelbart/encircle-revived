package aoi;

import java.awt.*;

/**
 *  Interface for object passed to the GUI
 *  for drawing purposes.
 */
interface Drawable {

  // Returns the image to be drawn, if any.
  // May depend on the current game state.
  public Image img(GameState state);

  // Return {centerx, centery, width, height} of
  // the image, if any.
  public int[] imgLocation();

  // Returns the shapes to be drawn, if any.
  // May depend on the current game state.
  public Shape[] shapes(GameState state);

  // Determines whether the shapes are to be filled
  // or only the outlines drawn.
  public boolean[] fill();

  // Return the Paint objects used to draw the shapes.
  // In particular determines the color.
  public Paint[] paint();

  // Return the stroke object used to draw the shape outlines.
  public Stroke[] stroke();

  // Tells whether the coordinates are to be interpreted
  // as in-game coordinates scaled relative to
  // SimulationUtils.ARENASIZE or as coordinates for on-screen
  // overlays scaled between -1/2...+1/2.
  public boolean ingameCoordinates();

  // Remove the object from the collection of
  // objects to be drawn.
  public void remove();

  // Remove the object from the collection of
  // objects to be drawn.
  public boolean isRemoved();
  
  // All the stuff you want to do before returning shapes, etc.
  public void prepare(GameState gState);
  
//All the stuff you want to do after returning shapes, etc.
  public void postpare(GameState gState);

}
