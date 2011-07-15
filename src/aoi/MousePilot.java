package aoi;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;

import javax.swing.event.*;

/**
 * A simple interface for controlling your ship based on mouse events.
 */
public class MousePilot implements MouseInputListener {
  private int acc = 0;
  private Point target;
  private boolean shoot = false;
  private boolean boost = false;

  
  public PlayerControlParameters getParams(GameState currentGameState) {
    if (target == null) return null;
    
    // ship location
    Point loc = currentGameState.pStates[ClientConfiguration.playerNo].ship.location();
    // mouse location
    Point2D tar = DrawUtils.fromJavaUserSpaceToGameCoordinates(target);
    
    // Set the acceleration based on the distance between the ship and mouse.
    final double l = SimulationUtils.ARENASIZE / 50;
    acc = (int) (SimulationUtils.MAXACC * (1.-Math.exp(-loc.distanceSq(tar)/l)));
    
    return new PlayerControlParameters(currentGameState.time, acc, new Angle(loc, tar), shoot, boost);
  }

  @Override
  public void mouseMoved(MouseEvent e) {
    target = e.getPoint();
  }
 

  @Override
  public void mousePressed(MouseEvent e) {
    shoot = true;
  }


  @Override
  public void mouseReleased(MouseEvent e) {
    shoot = false;
  }
  
  
  @Override
  public void mouseClicked(MouseEvent e) { }


  @Override
  public void mouseEntered(MouseEvent e) { }


  @Override
  public void mouseExited(MouseEvent e) { }


  @Override
  public void mouseDragged(MouseEvent e) { }
  
}
