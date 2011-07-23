package aoi;

import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

import aoi.Logger.Rep;

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
  
  // The time that was supposed to be rendered last.
  // Could disagree with renderedState.time if timemachine.getState() is slow.
  private int renderedTime = 0;
	
  // The timer in charge of updating the GUI.
  private RenderTimer renderTimer;
  private Thread renderTimerThread;

  // The main thread that runs think() in an infinite loop.
  private Thread intelThread;

  // Call one of these to get new control parameters.
  private KeyboardPilot keyboardPilot = null;
  private MousePilot mousePilot = null;
  
  public HigherBeing() {
    
    Logger.println(Logger.Rep.FEW, "HigherBeing for player " + ClientConfiguration.playerNo + " created.");

    gui = new GUI();
    guiThread = new Thread(gui, "gui");
    guiThread.setPriority(Thread.MAX_PRIORITY);
    guiThread.start();

    timemachine = new TimeMachine();
    TimeMachine.setCurrent(timemachine);

    mindreader = new MindReader(timemachine);
    MindReader.setCurrent(mindreader);

    // Dummy command, just so we don't have to deal with the corner case of no control parameters in the beginning.
    queueParams(new PlayerControlParameters(0, 0, new Angle(0), false, false));
    

    mousePilot = new MousePilot();
    DrawUtils.addMouseListener(mousePilot);
    DrawUtils.addMouseMotionListener(mousePilot);

    keyboardPilot = new KeyboardPilot();
    DrawUtils.addKeyListener(keyboardPilot);
    
    renderTimer = new RenderTimer();
    renderTimerThread = new Thread(renderTimer, "render-timer");
    renderTimerThread.start();

    intelThread = new Thread(this, "intel");
    intelThread.start();
  }

  public void run() {
    think();
  }

  /* Store the latest game state in a queue. */
  public void processGameStateUpdate(GameState game) {
    timemachine.processStateUpdate(game);
  }


  public PlayerControlParameters[] getNewControlParameters() {
    PlayerControlParameters[] p;
    
    synchronized (ctrl) {
      final int N = ctrl.size();
      if (N > 0) {
        p = ctrl.toArray(new PlayerControlParameters[]{});
        ctrl.clear();

        if (N > 2)
          Logger.println(Logger.Rep.INFREQ, "many ctrls buffered: " + N);

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

    while (true) {
	    
      try {
        Thread.sleep(1000); // Sleep when there's nothing better to do.
      } catch (InterruptedException e) {

        // Don't do anything before the game starts.
        if (renderedState == null) continue;
        
        GameState gState = renderedState; // copy the reference so it doesn't change during an iteration

        PlayerControlParameters c;

        if (ClientConfiguration.useMouse) {
          c = mousePilot.getParams(gState);
        } else
          c = keyboardPilot.getParams(gState);

        if (c != null && !keyboardPilot.dontShoot && Shooter.wouldHit(gState, ClientConfiguration.playerNo)) {
          //Logger.println(Rep.INFREQ, "could shoot");
          c = c.shoot();
        }
        
        if (keyboardPilot.stop)
          c = c.stop();
        
        if (c != null) queueParams(c);

      }

    }

  }



  private class RenderTimer implements Runnable {

    // sleep time between frames in ms.
    //
    // This is low pass filtered based on the actual amount of CSP
    // so that on average the number of CSP steps tends to the desired value;
    private double sleeptime = SimulationUtils.TIMESTEP;
    
    public boolean stop = false;
    
    public void run() {
      
      while (true) {
        // don't do anything if the gui hasn't finished rendering the previous frame.
        //if (guiThread.getState() != Thread.State.TIMED_WAITING) return;
  
        renderedAuthorativeState = timemachine.getAuthorativeState();
  
        // This can happen before the game actually starts.
        if (renderedAuthorativeState == null) {
          try {
            Thread.sleep(SimulationUtils.TIMESTEP);
          } catch (InterruptedException e) {
            Logger.println(Rep.FEW, "interrupted");
          }
          continue;
        }
  
        if (ClientConfiguration.predictFuture > 0) {
          
          // in the long term we would like to (on average) be this many steps ahead
          // of the latest state received from the server
          final int targetSimulatedSteps = ClientConfiguration.predictFuture/SimulationUtils.TIMESTEP;
          
          // the next time to render
          renderedTime = (renderedState != null) ? renderedTime + 1 : 0;
          
          // the actual number of CSP frames
          final int simulatedSteps = renderedTime - renderedAuthorativeState.time;
          
          // Adjust the frametime using an exponential moving average (low pass filter)
          sleeptime = sleeptime + 0.2*(simulatedSteps-targetSimulatedSteps);
          
          // skip frames if we are behind the target.
          if (simulatedSteps < targetSimulatedSteps)
            renderedTime = renderedAuthorativeState.time + targetSimulatedSteps;
          
          if (Math.abs(simulatedSteps-targetSimulatedSteps) > 3) Logger.println(Rep.INFREQ, "CSP time " + simulatedSteps*SimulationUtils.TIMESTEP + " ms.");
  
          renderedState = timemachine.getState(renderedTime, mindreader);
  	    
        } else {
          
          renderedState = renderedAuthorativeState;
          
        }
        
        
        assert(renderedState != null);
  
        gui.setGameState(renderedState);
        guiThread.interrupt();
  
        intelThread.interrupt();

        try {
          //Thread.sleep((int) sleeptime, ((int) (1000*sleeptime))%1000);
          //if (renderedTime%100 == 0) Logger.println(Rep.INFREQ, "sleep: " + sleeptime);
          Thread.sleep(Math.round(sleeptime));
        } catch (InterruptedException e) {
          if (stop) return;
        }
      }
    }

  }

}
