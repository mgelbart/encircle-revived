package aoi;

/*

  A class for interacting with a human, i.e. for
  drawing the game state and getting inputs.

  Designed to be ran in a separate thread such that
  frames are skipped in the visualization as needed.

  This class should be more or less tightly integrated
  with an instance of Intelligence.

 */

import stdlib.*;
import java.awt.KeyboardFocusManager;
import java.awt.KeyEventDispatcher;
import java.awt.event.KeyEvent;
import java.awt.geom.Point2D;
import java.awt.Point;
import java.awt.Shape;
import java.awt.Image;
import java.awt.Color;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;
import java.io.*;

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
	    drawables.add(new DrawableCluster(c));
	}

	Color[] colors = new Color[2];
	for (int j=0; j < Simulation.NUMPLAYERS; ++j)
	    colors[j] = (j == config.playerNo) ? StdDraw.RED : StdDraw.BLUE;
	

	for (int j=0; j < Simulation.NUMPLAYERS; ++j)
	    drawables.add(new DrawableTail(j, colors[j], StdDraw.DEFAULT_PEN_RADIUS));

	for (int j=0; j < Simulation.NUMPLAYERS; ++j)
	    drawables.add(new DrawableShip(j, colors[j], StdDraw.DEFAULT_PEN_RADIUS));

	drawables.add(new DrawableBullets());

	for (int j=0; j < Simulation.NUMPLAYERS; ++j)
	    drawables.add(new DrawableScoringEffect(j, colors[j], StdDraw.DEFAULT_PEN_RADIUS));

	drawables.add(new DrawableScore(StdDraw.GRAY, colors));


	/* Canvas parameters: */

	StdDraw.setCanvasSize(700, 700);

	StdDraw.setXscale(-Simulation.ARENASIZE/2, Simulation.ARENASIZE/2);
	StdDraw.setYscale(-Simulation.ARENASIZE/2, Simulation.ARENASIZE/2);

	StdDraw.setPenColor(StdDraw.BLUE);

	StdDraw.clear(StdDraw.BLACK);

	StdDraw.show(0);

	KeyboardFocusManager.getCurrentKeyboardFocusManager()
	    .addKeyEventDispatcher(new KeyEventDispatcher() {
		    @Override
		    public boolean dispatchKeyEvent(KeyEvent e) {
			int key = e.getKeyCode();

			if (e.getID() == KeyEvent.KEY_PRESSED)
			    keysPressed.add(key);
			else if (e.getID() == KeyEvent.KEY_RELEASED)
			    keysPressed.remove(key);

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
	return (Set<Integer>) (keysPressed.clone());
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

	StdDraw.clear(StdDraw.BLACK);

	// Set the zoom level
	final double r = Math.max(Simulation.ARENASIZE/2,
				  Math.max(Math.abs(g.states[config.playerNo].x * 1.05),
					   Math.abs(g.states[config.playerNo].y * 1.05)));
	StdDraw.setXscale(-r, r);
	StdDraw.setYscale(-r, r);

	for (int j=0; j<drawables.size(); ++j) {
	    Drawable d = drawables.get(j);

	    if (!d.isRemoved()) {

		Shape[] s = d.shapes(g);
		Image img = d.img(g);

		if (s != null) {
		    boolean[] fill = d.fill();
		    Color[] colors = d.color();
		    double[] widths = d.width();
		    for (int k=0; k<s.length; ++k) {
			StdDraw.setPenColor(colors[k]);
			StdDraw.setPenRadius(widths[k]);
			StdDraw.shape(s[k], d.ingameCoordinates(), fill[k]);
		    }
		}

		if (img != null) {
		    // TODO: implement ingameCoordinates == false case
		    assert(d.ingameCoordinates() == true);
		    int[] loc = d.imgLocation();
		    StdDraw.picture(loc[0], loc[1], img, loc[2], loc[3]);
		}

	    } else {
		drawables.removeElementAt(j);
		--j;
	    }

	}

	StdDraw.show(0);

    }

}
