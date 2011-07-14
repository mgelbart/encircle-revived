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

  // Stores a (limited) history of the the game states
  // and predicts future states.
  private TimeMachine timemachine;

  // Predicts what other people will do in the future.
  // Used in combination with TimeMachine.
  private MindReader mindreader;

  // The control parameters to be sent to the server.
  private Vector<PlayerControlParameters> ctrl = new Vector<PlayerControlParameters>();

  // The GUI is ran in a separate thread so that drawing
  // doesn't block thinking.
  private GUI gui = null;
  private Thread guiThread = null;

  // The last authorative game state that was used to
  // predict the rendered state.
  private GameState renderedAuthorativeState = null;

  // The last rendered game state.
  private GameState renderedState = null;
	
  // Life time of a rendered frame in ms.
  //
  // If no new state is received within this time
  // client-side prediction will be used to render
  // the next frame.
  //
  // Setting this to > SimulationUtils.TIMESTEP will tend to push
  // the simulated time (== renderedTime - renderedServerTime)
  // towards MINSIMULATEDTIME.
  private int frametime = MINFRAMETIME;

  private static final int MINFRAMETIME = SimulationUtils.TIMESTEP + 1;

  // Maximum number of turns to predict into the future.
//  private static int MAXSIMULATEDSTEPS = 1000/SimulationUtils.TIMESTEP;

  // The timer in charge of updating the GUI.
  private Timer renderTimer;

  // The main thread that runs think() in an infinite loop.
  private Thread intelThread;

  public HigherBeing() {
    
    Logger.println(Logger.RepetitionRate.FEW, "HigherBeing for player " + ClientConfiguration.playerNo + " created.");

    gui = new GUI();
    guiThread = new Thread(gui);
    guiThread.start();

    timemachine = new TimeMachine();

    mindreader = new MindReader();
 
    // Dummy command, just so we don't have to deal with the corner case of no control parameters in the beginning.
    queueParams(new PlayerControlParameters(0, 0, 0, 0, 0));
    
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


  public PlayerControlParameters[] getNewControlParameters() {
    PlayerControlParameters[] p;
    
    synchronized (ctrl) {
      final int N = ctrl.size();
      if (N > 0) {
        p = ctrl.toArray(new PlayerControlParameters[]{});
        ctrl.clear();

        if (N > 2)
          Logger.println(Logger.RepetitionRate.INFREQUENT, "Too many ctrls buffered: " + N);

      } else
        p = new PlayerControlParameters[]{};
    }

    return p;

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
    int shoot = 0;
    int boost = 0;

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
          boost++;
          update = true;
        }


        if (keysPressed.contains(KeyEvent.VK_SPACE)) {
          shoot++;
          update = true;
        }

        if (keysPressed.contains(KeyEvent.VK_LEFT)) {
          dir = renderedState.states[ClientConfiguration.playerNo].ship.dir()
            + SimulationUtils.INTMAX/2;
          update = true;
        } else if (keysPressed.contains(KeyEvent.VK_RIGHT)) {
          dir = renderedState.states[ClientConfiguration.playerNo].ship.dir()
            - SimulationUtils.INTMAX/2;
          update = true;
        } else if (Math.abs(dir - renderedState.states[ClientConfiguration.playerNo].ship.dir()) > 
        SimulationUtils.INTMAX/5) {
          dir = renderedState.states[ClientConfiguration.playerNo].ship.dir();
          update = true;
        }


        if (update)
          queueParams(new PlayerControlParameters(renderedState.time, acc, dir, shoot, boost));

      }

    }

  }



  private class RenderGameTask extends TimerTask {

    public void run() {

      renderedAuthorativeState = timemachine.getAuthorativeState();

      // This can happen before the game actually starts.
      if (renderedAuthorativeState == null) return;

      if (ClientConfiguration.predictFuture > 0) {
        
        final int simulatedSteps = ClientConfiguration.predictFuture/SimulationUtils.TIMESTEP;
      
        // This simply skips frames if the rendered states is less than
        // MINSIMULATEDSTEPS ahead of the authorative state.
        // The opposite should never happen as long as
        // frametime > SimulationUtils.TIMESTEP.
        int newRenderedTime = (renderedState != null) ? renderedState.time + 1 : 0;
        if (newRenderedTime - renderedAuthorativeState.time < simulatedSteps) {
  
          /*
          Logger.println(Logger.RepetitionRate.FREQUENT, "Skipped "
                             + (renderedAuthorativeState.time
                                + MINSIMULATEDSTEPS
                                - newRenderedTime)
                             + " frames.");
  
          Logger.println(Logger.RepetitionRate.FREQUENT, "Authorative time: " + renderedAuthorativeState.time);
          if (renderedState != null) {
            Logger.println(Logger.RepetitionRate.FREQUENT, "Rendered time: " + renderedState.time);
          }
          */
          
          newRenderedTime = renderedAuthorativeState.time + simulatedSteps;
  		
        }
  
        renderedState = timemachine.getState(newRenderedTime, mindreader);
	    
      } else {
        
        renderedState = renderedAuthorativeState;
        
      }
      
      
      assert(renderedState != null);

      gui.setGameState(renderedState);
      guiThread.interrupt();

      intelThread.interrupt();
    }

  }


}
