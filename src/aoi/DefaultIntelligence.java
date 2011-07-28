package aoi;

import java.util.Vector;
import aoi.Logger.Rep;

public class DefaultIntelligence implements Intelligence {

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
  // There is no GUI in this default intelligence,
  // but a default GUI is provided.
  protected GUI gui = null;
  protected Thread guiThread = null;
  
  // The last authoritative game state that was used to
  // predict the latest state.
  private GameState latestAuthoritativeState = null;

  // The last rendered game state.
  private GameState latestState = null;
  
  // The time that was supposed to be rendered last.
  // Could disagree with renderedState.time if timemachine.getState() is slow.
  private int clientTime = 0;
  
  // The timer in charge of CSP
  private CspTimer cspTimer;
  private Thread cspTimerThread;

  // The main thread that runs think() in an infinite loop.
  private Thread intelThread;
  
  public DefaultIntelligence() {

    timemachine = new TimeMachine();
    TimeMachine.setCurrent(timemachine);

    mindreader = new MindReader(timemachine);
    MindReader.setCurrent(mindreader);

    // Dummy command, just so we don't have to deal with the
    // corner case of no control parameters in the beginning.
    queueParams(new PlayerControlParameters(0, 0, new Angle(0), false, false));
        
    cspTimer = new CspTimer();
    cspTimerThread = new Thread(cspTimer, "csp-timer");
    cspTimerThread.start();

    intelThread = new Thread(this, "intel");
    intelThread.start();
  }

  public void run() {
    while (true) {
      try {
        Thread.sleep(1000); // Sleep when there's nothing better to do.
      } catch (InterruptedException e) {
        // Don't do anything before the game starts.
        if (latestState == null) continue;
        
        queueParams(think(latestState));
      }
    }
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
    if (params == null) return;
    
    synchronized (ctrl) {
      ctrl.add(params);
    }
    
    timemachine.processParamsUpdate(params);

  }

  public PlayerControlParameters think(GameState gState) {        
    return new PlayerControlParameters(
      clientTime, 
      SimulationUtils.MAXACC, 
      new Angle((Math.random()-0.5)*2*Math.PI),
      false, false);
  } 



  private class CspTimer implements Runnable {

    // sleep time between frames in ms.
    //
    // This is low pass filtered based on the actual amount of CSP
    // so that on average the number of CSP steps tends to the desired value;
    private double sleeptime = SimulationUtils.TIMESTEP;
    
    public boolean stop = false;
    
    // only print out every SKIPWARNS warning about large CSP.
    private static final int SKIPWARNS = 5;
    private int warnCtr = 1;
    
    public void run() {
      
      while (true) {
        latestAuthoritativeState = timemachine.getAuthorativeState();
  
        // This can happen before the game actually starts.
        if (latestAuthoritativeState == null) {
          try {
            Thread.sleep(SimulationUtils.TIMESTEP);
          } catch (InterruptedException e) {
            Logger.println(Rep.FEW, "interrupted");
          }
          continue;
        }
  
        if (ClientConfiguration.predictFuture > 0) {
          
          Integer lagEstimate = Client.lagEstimate();
          if (lagEstimate == null) lagEstimate = 0;
          // in the long term we would like to (on average) be this many steps ahead
          // of the latest state received from the server
          final int targetSimulatedSteps = (int) Math.round(
              Math.max(ClientConfiguration.predictFuture, 
                  100 + lagEstimate) / (double) SimulationUtils.TIMESTEP);
          
          // the next time to process
          clientTime = (latestState != null) ? clientTime + 1 : 0;
          
          // the actual number of CSP frames
          final int simulatedSteps = clientTime - latestAuthoritativeState.time;
          
          // Adjust the sleep time using an exponential moving average (low pass filter).
          // The idea is that sleeptime plus the time it takes to run this function should
          // be equal (on average) to the _server_ TIMESTEP (measured using server's clock).
          sleeptime = sleeptime + 0.1*Math.signum(simulatedSteps-targetSimulatedSteps);
          
          // The sleep time cannot be much more than TIMESTEP
          final double MAXSLEEPTIME = 1.3*SimulationUtils.TIMESTEP;
          sleeptime = Math.min(MAXSLEEPTIME, sleeptime);
          
          // skip frames if we are significantly behind the target.
          if (simulatedSteps < targetSimulatedSteps-1)
            clientTime = latestAuthoritativeState.time + targetSimulatedSteps;
          
          if (Math.abs(simulatedSteps-targetSimulatedSteps) > 3 && --warnCtr == 0) {
            Logger.println(Rep.INFREQ, "CSP time " + 
                simulatedSteps*SimulationUtils.TIMESTEP + " ms.");
            warnCtr = SKIPWARNS;
          }
  
          latestState = timemachine.getState(clientTime, mindreader);
          
          if (clientTime%2000 == 200) {
            //Logger.println(Rep.INFREQ, "sleep: " + sleeptime + " ms");
            Logger.println(Rep.INFREQ, "avg. lag: " + lagEstimate
                            + "  target/current CSP: " + 
                            targetSimulatedSteps*SimulationUtils.TIMESTEP
                            + " / " + 
                            simulatedSteps*SimulationUtils.TIMESTEP + " ms");
          }
        
        } else {
          
          latestState = latestAuthoritativeState;
          
        }
        
        assert(latestState != null);
        
        if (gui != null) gui.setGameState(latestState);
        if (guiThread != null) guiThread.interrupt();
  
        intelThread.interrupt();

        try {
          //Thread.sleep((int) sleeptime, ((int) (1000*sleeptime))%1000);
          Thread.sleep(Math.round(sleeptime));
        } catch (InterruptedException e) {
          if (stop) return;
        }
      }
    }

  }

}
