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

    // The input options received from Client.
    ClientConfiguration config;

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

	    // Store the previous loop if the score increased
	    if (p.score > lastScore[j]) {
		lastScore[j] = p.score;
		lastScoringTime[j] = g.time;
		lastScoringLoop[j] = (Vector<Point2D>) loop[j].clone();
	    } else if (p.score < lastScore[j]) {
		// Don't want to highlight the path if the score decreased!
		lastScore[j] = p.score;
	    }

	    // Draw the ship

	    xy = p.shape();

	    // Have to cast these things by hand because you can't cast
	    // a whole array at once (I think).
	    for (int k=0; k<2; ++k) {
		for (int l=0; l<4; ++l)
		    xydbl[k][l] = xy[k][l];
	    }

	    if (j == config.playerNo)
		StdDraw.setPenColor(StdDraw.RED);
	    else
		StdDraw.setPenColor(StdDraw.BLUE);


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


	    drawLoop(loop[j]);


	    // Draw the scoring loop if recent:
	    if (lastScoringLoop[j] != null
		&& g.time - lastScoringTime[j] < 1500/Simulation.TIMESTEP) {

		StdDraw.setPenRadius((g.time - lastScoringTime[j])
				     * StdDraw.DEFAULT_PEN_RADIUS/4);

		drawLoop(lastScoringLoop[j]);

		StdDraw.setPenRadius(StdDraw.DEFAULT_PEN_RADIUS);

	    }


	    StdDraw.setPenColor(StdDraw.GREEN);

	    // Draw the score:
	    StdDraw.text((2*j - 1)*Simulation.ARENASIZE*2/5,
			 Simulation.ARENASIZE*19/40,
			 "P" + j + ": " + p.score);

	}

	StdDraw.setPenColor(StdDraw.GREEN);
	for (Bullet b : g.bullets)
	    StdDraw.square(b.x, b.y, Simulation.ARENASIZE / 500);

	StdDraw.show(0);

    }

    private static void drawLoop(Vector<Point2D> loop) {
	for (int k=0; k < loop.size()-1; ++k)
	    StdDraw.line(loop.get(k  ).getX(), loop.get(k  ).getY(),
			 loop.get(k+1).getX(), loop.get(k+1).getY());

    }

}
