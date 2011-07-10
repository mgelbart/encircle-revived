package aoi;

import java.awt.event.KeyEvent;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

/**
 *  This class decides what to do based on (the
 *  history of) the game state.
 *
 *  This implementation of Intelligence relies
 *  on a combination of human input and machine
 *  decision making, i.e. it implements an
 *  "augmented intelligence".
 */
public class HigherBeing implements Intelligence {

  // The input options received from Client.
  ClientConfiguration config;

  Arena arena;

  // Stores a (limited) history of the the game states
  // and predicts future states.
  TimeMachine timemachine;

  // Predicts what other people will do in the future.
  // Used in combination with TimeMachine.
  MindReader mindreader;

  // The control parameters to be sent to the server.
  Vector<PlayerControlParameters> ctrl = new Vector<PlayerControlParameters>();

  // Last time ctrl was changed.
  int lastAction = -999999;

  // The GUI is ran in a separate thread so that drawing
  // doesn't block thinking.
  GUI gui = null;
  Thread guiThread = null;

  // The last authorative game state that was used to
  // predict the rendered state.
  GameState renderedAuthorativeState = null;

  // The last rendered game state.
  GameState renderedState = null;
	
  // Life time of a rendered frame in ms.
  //
  // If no new state is received within this time
  // client-side prediction will be used to render
  // the next frame.
  //
  // Setting this to > SimulationUtils.TIMESTEP will tend to push
  // the simulated time (== renderedTime - renderedServerTime)
  // towards MINSIMULATEDTIME.
  public int frametime = MINFRAMETIME;

  private static int MINFRAMETIME = SimulationUtils.TIMESTEP + 1;
	
  // Minimum number of turns to predict into the future.
  private static int MINSIMULATEDSTEPS = 200/SimulationUtils.TIMESTEP;

  // Maximum number of turns to predict into the future.
//  private static int MAXSIMULATEDSTEPS = 1000/SimulationUtils.TIMESTEP;

  // The timer in charge of updating the GUI.
  private Timer renderTimer;

  // The main thread that runs think() in an infinite loop.
  private Thread intelThread;

  public HigherBeing(ClientConfiguration config, Arena arena) {

    this.config = config;
    this.arena = arena;

    System.out.println("HigherBeing for player " + config.playerNo + " created.");

    gui = new GUI(arena, config);
    guiThread = new Thread(gui);
    guiThread.start();

    timemachine = new TimeMachine(config);

    mindreader = new MindReader(config);

    TimerTask renderTask = new RenderGameTask();
    renderTimer = new Timer();
    renderTimer.scheduleAtFixedRate(renderTask, 0, frametime);

    intelThread = new Thread(this);
    intelThread.start();
  }

  public void run() {
    think();
  }

  /* Store the latest game state in a queue. */
  public void processGameStateUpdate(GameState game) {
    timemachine.processStateUpdate(game);
    mindreader.processStateUpdate(game);
  }


  public PlayerControlParameters getNewControlParameters() {
    synchronized (ctrl) {
      if (ctrl.size() > 0) {
        PlayerControlParameters p = ctrl.firstElement();
        ctrl.removeElementAt(0);
        return p;
      }

      assert(ctrl.size() < 20); // Something is wrong if they get piled up.
    }

    return null;

  }

  private void queueParams(PlayerControlParameters params) {

    synchronized (ctrl) {
      ctrl.add(params);
    }

    timemachine.processParamsUpdate(params);

  }

  public void think() {

    boolean update;
    int acc = 0;
    int dir = 0;
    int shots = 0;
    int boosts = 0;

    Set<Integer> keysPressed;

    while (true) {
	    
      try {
        Thread.sleep(1000); // Sleep when there's nothing better to do.
      } catch (InterruptedException e) {

        // Don't do anything before the game starts.
        if (renderedState == null) continue;

        update = false;

        keysPressed = gui.pressedKeys();

        if (keysPressed.contains(KeyEvent.VK_UP)) {
          if (acc != SimulationUtils.INTMAX) {
            acc = SimulationUtils.INTMAX;
            update = true;
          }
        } else if (acc > 0) {
          acc = 0;
          update = true;
        }

        if (keysPressed.contains(KeyEvent.VK_DOWN)) {
          boosts = 10;
          update = true;
        } else if (boosts > 0) {
          boosts = 0;
          update = true;
        }


        if (keysPressed.contains(KeyEvent.VK_SPACE)) {
          shots = 10;
          update = true;
        } else if (shots > 0) {
          shots = 0;
          update = true;
        }

        if (keysPressed.contains(KeyEvent.VK_LEFT)) {
          dir = renderedState.states[config.playerNo].pos.direction
            + SimulationUtils.INTMAX/2;
          update = true;
        } else if (keysPressed.contains(KeyEvent.VK_RIGHT)) {
          dir = renderedState.states[config.playerNo].pos.direction
            - SimulationUtils.INTMAX/2;
          update = true;
        } else if (Math.abs(dir - renderedState.states[config.playerNo].pos.direction) > SimulationUtils.INTMAX/5) {
          dir = renderedState.states[config.playerNo].pos.direction;
          update = true;
        }		    


        if (update)
          queueParams(new PlayerControlParameters(renderedState.time, acc, dir, shots, boosts));

      }

    }

  }



  private class RenderGameTask extends TimerTask {

    public void run() {

      renderedAuthorativeState = timemachine.getAuthorativeState();

      // This can happen before the game actually starts.
      if (renderedAuthorativeState == null) return;

      // This simply skips frames if the rendered states is less than
      // MINSIMULATEDSTEPS ahead of the authorative state.
      // The opposite should never happen as long as
      // frametime > SimulationUtils.TIMESTEP.
      int newRenderedTime = (renderedState != null) ? renderedState.time + 1 : 0;
      if (newRenderedTime - renderedAuthorativeState.time < MINSIMULATEDSTEPS) {

        /*
        System.err.println("Skipped "
                           + (renderedAuthorativeState.time
                              + MINSIMULATEDSTEPS
                              - newRenderedTime)
                           + " frames.");

        System.err.println("Authorative time: " + renderedAuthorativeState.time);
        if (renderedState != null) {
          System.err.println("Rendered time: " + renderedState.time);
        }
        */

        newRenderedTime = renderedAuthorativeState.time
          + MINSIMULATEDSTEPS;

		
      }

      renderedState = timemachine.getState(newRenderedTime, mindreader);
	    
      assert(renderedState != null);

      gui.setGameState(renderedState);
      guiThread.interrupt();

      intelThread.interrupt();
    }

  }


}
