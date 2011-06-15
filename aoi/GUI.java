package aoi;

import java.awt.KeyboardFocusManager;
import java.awt.KeyEventDispatcher;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.awt.Point;
import java.awt.Shape;
import java.awt.Image;
import java.awt.Paint;
import java.awt.Stroke;
import java.awt.Color;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;
import java.io.*;

/**
 *  A class for interacting with a human, i.e. for
 *  drawing the game state and getting inputs.
 *
 *  Designed to be ran in a separate thread such that
 *  frames are skipped in the visualization as needed.
 *
 *  This class should be more or less tightly integrated
 *  with an instance of Intelligence.
*/
public class GUI implements Runnable {

  // The input options received from Client.
  ClientConfiguration config;

  /* Most recent game state. Updated by the Intelligence. */
  private GameState game;

  /* The arena containing the clusters. */
  private Arena arena;

  /* Booleans that describe which of the control
     keys is currently pressed down.
       
     These are read by the Intelligence.
  */
  private TreeSet<Integer> keysPressed = new TreeSet<Integer>();


  // Items to draw on the screen.
  private Vector<Drawable> drawables = new Vector<Drawable>();

  private int[] currentLoopStarted;

  private Vector<Point2D>[] loop;

  // Used to highlight a loop that scored a point:
  private int[] lastScore;
  private int[] lastScoringTime;
  private Vector<Point2D>[] lastScoringLoop;


  @SuppressWarnings("unchecked")
  public GUI(Arena arena, ClientConfiguration config) {

    this.arena = arena;
    this.config = config;
    game = null;

    currentLoopStarted = new int[Simulation.NUMPLAYERS];
    loop = new Vector[Simulation.NUMPLAYERS];

    lastScore = new int[Simulation.NUMPLAYERS];
    lastScoringTime = new int[Simulation.NUMPLAYERS];
    lastScoringLoop = new Vector[Simulation.NUMPLAYERS];

    for (int j=0; j<Simulation.NUMPLAYERS; ++j) {
      currentLoopStarted[j] = -999;
      loop[j] = new Vector<Point2D>();

      lastScore[j] = 0;
      lastScoringTime[j] = -999;
    }


    /* Default Drawable items:
     *
     *   Note that the adding order determines
     *   the rendering order
     */

    for (Cluster c : arena.clusters) {
      // The cluster image
      drawables.add(new DrawableCluster(c));
      // The cluster contour
      drawables.add(new DrawablePath(c.contour().coordsTranspose(), true, Color.GREEN, DrawUtils.DEFAULTSTROKE));
    }

    Color[] colors = new Color[2];
    for (int j=0; j < Simulation.NUMPLAYERS; ++j)
      colors[j] = (j == config.playerNo) ? Color.RED : Color.BLUE;
	

    for (int j=0; j < Simulation.NUMPLAYERS; ++j)
      drawables.add(new DrawableTail(j, colors[j], DrawUtils.DEFAULTSTROKE));

    for (int j=0; j < Simulation.NUMPLAYERS; ++j)
      drawables.add(new DrawableShip(j, colors[j], DrawUtils.DEFAULTSTROKE));

    drawables.add(new DrawableBullets());

    for (int j=0; j < Simulation.NUMPLAYERS; ++j)
      drawables.add(new DrawableScoringEffect(j, colors[j], DrawUtils.DEFAULTSTROKE));

    drawables.add(new DrawableScore(Color.GRAY, colors));


    /* Canvas parameters: */
    DrawUtils.setRenderedGameCoords(0, 0, Simulation.ARENASIZE, Simulation.ARENASIZE);
    DrawUtils.clear(Color.BLACK);
    DrawUtils.show();


    /* Catch keyboard events. */
    KeyboardFocusManager.getCurrentKeyboardFocusManager()
      .addKeyEventDispatcher(new KeyEventDispatcher() {
          @Override
          public boolean dispatchKeyEvent(KeyEvent e) {
            int key = e.getKeyCode();

            if (e.getID() == KeyEvent.KEY_PRESSED) {
              synchronized (keysPressed) {
                keysPressed.add(key);
              }
            } else if (e.getID() == KeyEvent.KEY_RELEASED) {
              synchronized (keysPressed) {
                keysPressed.remove(key);
              }
            }

            e.consume();

            return false;
          }
        });

  }

  public void run() {

    // Sleep until interruption (triggered by Client
    // when game state is updated);
    while (true) {
	    
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {

        drawGameState(arena, game);

      }

    }

  }

  // Set the game state to be drawn next.
  public void setGameState(GameState game) {
    this.game = game;
  }

  // Get the set of currently pressed keys.
  @SuppressWarnings("unchecked")
  public Set<Integer> pressedKeys() {

    // Note that the Integer objects themselves
    // aren't cloned...
    Set<Integer> k;
    synchronized (keysPressed) {
      k = (Set<Integer>) (keysPressed.clone());
    }

    return k;

  }

  // Add a Drawable object to the GUI.
  public void addDrawable(Drawable d) {
    // Note: prepending not thread safe with the
    //       current mechanism of removing items
    //       in drawGameState.
    drawables.add(d);
  }

  @SuppressWarnings("unchecked")
  private void drawGameState(Arena m, GameState g) {

    DrawUtils.clear(Color.BLACK);

    // Set the zoom level
    final double r = Math.max(Simulation.ARENASIZE/2,
                              Math.max(Math.abs(g.states[config.playerNo].pos.x * 1.05),
                                       Math.abs(g.states[config.playerNo].pos.y * 1.05)));

    DrawUtils.setRenderedGameCoords(0, 0, (int) (2*r), (int) (2*r));

    for (int j=0; j<drawables.size(); ++j) {
      Drawable d = drawables.get(j);

      if (!d.isRemoved()) {

        Shape[] s = d.shapes(g);
        Image img = d.img(g);

        if (s != null) {
          boolean[] fill = d.fill();
          Paint[] colors = d.paint();
          Stroke[] strokes = d.stroke();
          for (int k=0; k<s.length; ++k) {
            DrawUtils.setPaint(colors[k]);
            DrawUtils.setStroke(strokes[k]);
            DrawUtils.shape(s[k], d.ingameCoordinates(), fill[k]);
          }
        }

        if (img != null) {
          int[] loc = d.imgLocation();
          DrawUtils.picture(loc[0], loc[1], loc[2], loc[3], img, true);
        }

      } else {
        drawables.removeElementAt(j);
        --j;
      }

    }

    DrawUtils.show();

  }

}
