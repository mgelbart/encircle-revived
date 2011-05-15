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
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;
import java.io.*;

public class GUI implements Runnable {

    /* Most recent game state. Updated by the Intelligence. */
    public GameState game;

    /* The arena containing the clusters. */
    public Arena arena;

    /* Booleans that describe which of the control
       keys is currently pressed down.
       
       These are read by the Intelligence.
    */
    public Set<Integer> keysPressed = new TreeSet<Integer>();


    private int[] currentLoopStarted;
    private Vector<Point2D>[] loop;

    @SuppressWarnings("unchecked")
    public GUI(Arena arena) {

	this.arena = arena;
	game = null;

	currentLoopStarted = new int[2];
	loop = new Vector[2];
	for (int j=0; j<2; ++j) {
	    currentLoopStarted[j] = -999;
	    loop[j] = new Vector<Point2D>();
	}

	StdDraw.setCanvasSize(700, 700);

	StdDraw.setXscale(-Simulation.MAPSIZE/2, Simulation.MAPSIZE/2);
	StdDraw.setYscale(-Simulation.MAPSIZE/2, Simulation.MAPSIZE/2);

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


    private void drawGameState(Arena m, GameState g) {

	StdDraw.clear(StdDraw.BLACK);

	int xy[][];
	double xydbl[][] = new double[2][4];

	Point point;

	for (Cluster v : m.clusters)
	    StdDraw.picture(v.x, v.y, v.img, v.width, v.height);
	
	PlayerState p;
	for (int j=0; j<2; ++j) {
	    p = g.states[j];

	    // Draw the ship

	    xy = p.shape();

	    // Have to cast these things by hand because you can't cast
	    // a whole array at once (I think).
	    for (int k=0; k<2; ++k) {
		for (int l=0; l<4; ++l)
		    xydbl[k][l] = xy[k][l];
	    }

	    StdDraw.polygon(xydbl[0], xydbl[1]);

	    //  Draw the loop

	    if (currentLoopStarted[j] == p.currentLoop) {

		point = new Point(p.x, p.y);
		if (loop[j].size() == 0 || !loop[j].lastElement().equals(point))
		    loop[j].add(point);

	    } else {

		currentLoopStarted[j] = p.currentLoop;
		loop[j].clear();

	    }

	    for (int k=0; k < loop[j].size()-1; ++k)
		StdDraw.line(loop[j].get(k  ).getX(), loop[j].get(k  ).getY(),
			     loop[j].get(k+1).getX(), loop[j].get(k+1).getY());

	    // Draw the score:
	    StdDraw.text((2*j - 1)*Simulation.MAPSIZE*2/5,
			 Simulation.MAPSIZE*19/40,
			 "P" + j + ": " + p.score);

	}

	for (Bullet b : g.bullets)
	    StdDraw.square(b.x, b.y, Simulation.MAPSIZE / 500);

	StdDraw.show(0);

    }

}
