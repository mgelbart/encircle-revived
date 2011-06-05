package aoi;

/*
  Interface for object passed to the GUI
  for drawing purposes.
*/

import java.awt.Shape;
import java.awt.Image;
import java.awt.Color;

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

  // Return the pen colors used to draw the shapes.
  public Color[] color();

  // Return the pen widths used to draw the shapes.
  public double[] width();

  // Tells whether the coordinates are to be interpreted
  // as in-game coordinates scaled relative to
  // Simulation.ARENASIZE or as coordinates for on-screen
  // overlays scaled between -1/2...+1/2.
  public boolean ingameCoordinates();

  // Remove the object from the collection of
  // objects to be drawn.
  public void remove();

  // Remove the object from the collection of
  // objects to be drawn.
  public boolean isRemoved();

}
