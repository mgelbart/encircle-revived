package aoi;

import java.applet.AudioClip;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.awt.*;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

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

  /* Most recent game state. Updated by the Intelligence. */
  private GameState game;

  /* Booleans that describe which of the control
     keys is currently pressed down.
       
     These are read by the Intelligence.
  */
  private TreeSet<Integer> keysPressed = new TreeSet<Integer>();


  // Items to draw on the screen.
  private Vector<Drawable> drawables = new Vector<Drawable>();

  private Vector<Audible> audibles = new Vector<Audible>();
  
  private int[] currentLoopStarted;

  private Vector<Point2D>[] loop;

  // Used to highlight a loop that scored a point:
  private int[] lastScore;
  private int[] lastScoringTime;
  
  @SuppressWarnings("unchecked")
  public GUI() {
    game = null;

    currentLoopStarted = new int[SimulationUtils.NUMPLAYERS];
    loop = new Vector[SimulationUtils.NUMPLAYERS];

    lastScore = new int[SimulationUtils.NUMPLAYERS];
    lastScoringTime = new int[SimulationUtils.NUMPLAYERS];
    for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j) {
      currentLoopStarted[j] = -999;
      loop[j] = new Vector<Point2D>();

      lastScore[j] = 0;
      lastScoringTime[j] = -999;
    }


    /* Add Drawable/Audible items:
     *
     *   Note that the adding order determines
     *   the rendering order
     */
    for (int j=0; j < SimulationUtils.NUMPLAYERS; ++j)
      drawables.add(new DrawableTail(j, DrawUtils.DEFAULTSTROKE));

    drawables.add(new DrawableBullets());

    for (Cluster c : Arena.current().clusters)
      drawables.add(new DrawableCluster(c));

    for (int j=0; j < SimulationUtils.NUMPLAYERS; ++j) {
      ScoringEffect se = new ScoringEffect(j, DrawUtils.DEFAULTSTROKE);
      drawables.add(se);
      audibles.add(se);
    }
    
    for (int j=0; j < SimulationUtils.NUMPLAYERS; ++j)
      drawables.add(new DrawableShip(j, DrawUtils.DEFAULTSTROKE));

    drawables.add(new DrawableScoreboard(Color.GRAY, Arena.current().sequence()));

    audibles.add(new AudibleShot());
    audibles.add(new AudibleCrash());
    
    

    /* Canvas parameters: */
    DrawUtils.setRenderedGameCoords(0, 0, SimulationUtils.ARENASIZE, SimulationUtils.ARENASIZE);
    DrawUtils.clear(Color.BLACK);
    DrawUtils.show();

  }

  public void run() {

    // Sleep until interruption (triggered by Client
    // when game state is updated);
    while (true) {
	    
      try {
        Thread.sleep(1000);
      } catch (InterruptedException e) {

        drawGameState(game);

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
  // If a Drawable contains both images and shapes, the images are drawn first.
  public void addDrawable(Drawable d) {
    // Note: prepending not thread safe with the
    //       current mechanism of removing items
    //       in drawGameState.
    drawables.add(d);
  }
  
  public void addAudible(Audible a) {
    audibles.add(a);
  }

  private void drawGameState(GameState gState) {
    // --- Clear screen ---
    DrawUtils.clear(Color.BLACK);

    // --- Set the zoom level ---
    final double r = 
      Math.max(SimulationUtils.ARENASIZE/2,
      Math.max(Math.abs(gState.pStates[ClientConfiguration.playerNo].ship.x() * 1.05),
      Math.abs(gState.pStates[ClientConfiguration.playerNo].ship.y() * 1.05)));
    DrawUtils.setRenderedGameCoords(0, 0, (int) (2*r), (int) (2*r));

    
    // --- Prepare and remove drawables and audibles ---
    for (int j=0; j<drawables.size(); ++j) {
      Drawable d = drawables.get(j);
      if (!d.isRemoved())
        d.prepare(gState);
      else
        drawables.removeElementAt(j--);
    }
    for (int j=0; j<audibles.size(); ++j) {
      Audible a = audibles.get(j);
      if (!a.isRemoved())
        a.prepare(gState);
      else
        audibles.removeElementAt(j--);
    }    
    
    
    // --- Process drawables and audibles ---
    for (Drawable d : drawables) {
      Shape[] s = d.shapes(gState);
      Image img = d.img(gState);

      if (img != null) {
        int[] loc = d.imgLocation();
        DrawUtils.picture(loc[0], loc[1], loc[2], loc[3], img, true);
      }
      
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
    }
   for (Audible a : audibles) {
     AudioClip c = a.clip(gState);
     if (c != null) StdAudio.playClip(c);
   }
   
   
   // --- Postpare drawables and audibles ---
   for (Drawable d : drawables)
     d.postpare(gState);
   for (Audible a : audibles)
     a.postpare(gState);

   DrawUtils.show();
  }

}
