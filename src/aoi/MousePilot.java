package aoi;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;

import javax.swing.event.*;

/**
 * A simple interface for controlling your ship based on mouse events.
 */
public class MousePilot implements MouseInputListener {
  private Point target;
  private boolean shoot = false;
  private boolean boost = false;

  
  public PlayerControlParameters getParams(GameState currentGameState) {
    if (target == null) return null;
    
    // ship location
    Ship ship = currentGameState.pStates[ClientConfiguration.playerNo].ship; 
    Point loc = ship.location();
    // mouse location
    Point2D tar = DrawUtils.fromJavaUserSpaceToGameCoordinates(target);
    
    // Set the acceleration based on the distance between the ship and mouse.
    
    // the decay length^2
    final double lsq = Math.pow(SimulationUtils.ARENASIZE / 20, 2);
    
    // use the distance parallel to the direction of the ship only
    final Line2D perpLine = new Line2D.Double(loc, ship.dir().add(Math.PI/2).move(loc, 1000.));
    final double distsq = perpLine.ptLineDistSq(tar);
    
    int acc;
    if (distsq < lsq/2) acc = 0; 
    else              acc = (int) (SimulationUtils.MAXACC * (1.-Math.exp(-distsq/lsq)));
    
    return new PlayerControlParameters(currentGameState.time, acc, new Angle(loc, tar), shoot, boost);
  }

  @Override
  public void mouseMoved(MouseEvent e) {
    target = e.getPoint();
  }
 

  @Override
  public void mousePressed(MouseEvent e) {
    shoot = true;
    e.consume();
  }


  @Override
  public void mouseReleased(MouseEvent e) {
    shoot = false;
    e.consume();
  }
  
  
  @Override
  public void mouseClicked(MouseEvent e) { 
    e.consume();
  }


  @Override
  public void mouseEntered(MouseEvent e) { }


  @Override
  public void mouseExited(MouseEvent e) { }


  @Override
  public void mouseDragged(MouseEvent e) { }
  
}
