package aoi;

import java.awt.Color;
import java.awt.Point;
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
public class HigherBeing extends DefaultIntelligenceWithGUI {

  // Call one of these to get new control parameters.
  private KeyboardPilot keyboardPilot = null;
  private MousePilot mousePilot = null;
  private DrawablePath mousePath;
  
  public HigherBeing() {
    super();
    
    Logger.println(Logger.Rep.FEW, "HigherBeing for player " + ClientConfiguration.playerNo + " created.");  

    mousePilot = new MousePilot();
    DrawUtils.addMouseListener(mousePilot);
    DrawUtils.addMouseMotionListener(mousePilot);
    mousePath = new DrawablePath(ClientConfiguration.playerNo, DrawUtils.DEFAULTSTROKE, Color.GREEN, null);
    gui.addDrawable(mousePath);

    keyboardPilot = new KeyboardPilot();
    DrawUtils.addKeyListener(keyboardPilot);
  }



  public void think() {
    // copy the reference so it doesn't change during an iteration
    GameState gState = latestState(); 

    PlayerControlParameters c;

    if (ClientConfiguration.useMouse) {

      // remove path from tracker if we crashed
      if (gState.pStates[ClientConfiguration.playerNo].untilRespawn > 0) {
        Tracker.setPathToTrack(null);
        mousePath.setPath(null);
      }

      // see if mousepilot has a new trajectory to track.
      Vector<Point> path = mousePilot.trajectory();
      if (path != null)
        Tracker.setPathToTrack(path);


      if (Tracker.pointsLeftToTrack() > 0) {
        // let Tracker decide what to do if it still has points left to track
        c = Tracker.nextParams(gState.time, gState.pStates[ClientConfiguration.playerNo].ship);
        path = Tracker.remainingPath();
        if (path != null) mousePath.setPath(path);
      } else
        // otherwise go towards the cursor.
        c = mousePilot.getParams(gState);

    } else
      // if mouse is disabled
      c = keyboardPilot.getParams(gState);

    // auto-shooter
    if (c != null && !keyboardPilot.dontShoot && Shooter.wouldHit(gState, ClientConfiguration.playerNo)) {
      //Logger.println(Rep.INFREQ, "could shoot");
      c = c.shoot();
    }

    // manual shoot
    if (mousePilot.shoot || keyboardPilot.shoot)
      c = c.shoot();

    // manual stop
    if (keyboardPilot.stop)
      c = c.stop();

    if (c != null) queueParams(c);

   

  }



}
