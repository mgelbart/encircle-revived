package aoi;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.util.Vector;

import javax.swing.event.*;

/**
 * A simple interface for controlling your ship based on mouse events.
 */
public class MousePilot implements MouseInputListener {
  private Point target;
  public boolean shoot = false;
  private boolean boost = false;
  
  private Vector<Point> trajectory = null;
  private Vector<Point> finishedTrajectory;

  public long lastAction = 0;
  
  public PlayerControlParameters getParams(GameState currentGameState) {
    if (target == null) return null;

    // mouse location
    Point tar = DrawUtils.fromJavaUserSpaceToGameCoordinates(target);
    
    PlayerControlParameters p = Tracker.paramsToTarget(currentGameState.time, currentGameState.pStates[ClientConfiguration.playerNo].ship, tar);
    
    if (shoot) p = p.shoot();
    
    return p;
  }
  
  public Vector<Point> trajectory() {
    Vector<Point> t = finishedTrajectory;
    finishedTrajectory = null;
    return t;
  }

  @Override
  public void mouseMoved(MouseEvent e) {
    target = e.getPoint();
    e.consume();
    lastAction = System.currentTimeMillis();
  }
 

  @Override
  public void mousePressed(MouseEvent e) {
    GameCanvas.requestKeyboardFocus();
    
    if (e.getButton() == MouseEvent.BUTTON1) {
      trajectory = new Vector<Point>();
      trajectory.add(DrawUtils.fromJavaUserSpaceToGameCoordinates(e.getPoint()));
    } else {
      shoot = true;
    }
    e.consume();
    lastAction = System.currentTimeMillis();
  }


  @Override
  public void mouseReleased(MouseEvent e) {
    if (e.getButton() == MouseEvent.BUTTON1) {
      finishedTrajectory = trajectory;
      trajectory = null;
    } else {
      shoot = false;
    }
    e.consume();
    lastAction = System.currentTimeMillis();
  }

  @Override
  public void mouseDragged(MouseEvent e) {
    target = e.getPoint();
    if (trajectory != null) trajectory.add(DrawUtils.fromJavaUserSpaceToGameCoordinates(e.getPoint()));
    e.consume();
    lastAction = System.currentTimeMillis();
  }
  
  @Override
  public void mouseClicked(MouseEvent e) { 
    e.consume();
  }


  @Override
  public void mouseEntered(MouseEvent e) { }


  @Override
  public void mouseExited(MouseEvent e) { }
  
}
