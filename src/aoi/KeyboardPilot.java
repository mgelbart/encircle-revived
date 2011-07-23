package aoi;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

/**
 * A simple interface for controlling your ship based on keyboard events.
 */
public class KeyboardPilot implements KeyListener {
  private int acc = 0;
  private boolean left = false;
  private boolean right = false;
  private boolean shoot = false;
  private boolean boost = false;
  
  public boolean stop = false;
  public boolean dontShoot = false;
  
  public PlayerControlParameters getParams(GameState currentGameState) {
    return new PlayerControlParameters(currentGameState.time, acc,
         currentGameState.pStates[ClientConfiguration.playerNo].ship.dir().add(Math.PI/2*( ((left)? 1:0) - ((right)? 1:0) )),
         shoot, boost);
  }
  
  @Override
  public void keyPressed(KeyEvent e) {
    //Logger.println(Logger.RepetitionRate.INFREQUENT, "pressed " + e.getKeyCode());
    switch (e.getKeyCode()) {
    case (KeyEvent.VK_UP):
      acc = SimulationUtils.MAXACC;
      break;
    
    case (KeyEvent.VK_DOWN):
      boost = true;
      break;

    case (KeyEvent.VK_LEFT):
      left = true;
      break;
      
    case (KeyEvent.VK_RIGHT):
      right = true;
      break;
      
    case (KeyEvent.VK_SPACE):
      shoot = true;
      break;
      
    case (KeyEvent.VK_A):
      stop = true;
      break;
      
    case (KeyEvent.VK_F):
      dontShoot = true;
      break;
    }

    e.consume();
  }
  
  @Override
  public void keyReleased(KeyEvent e) {
    //Logger.println(Logger.RepetitionRate.INFREQUENT, "released " + e.getKeyCode());
    
    switch (e.getKeyCode()) {
    case (KeyEvent.VK_UP):
      acc = 0;
      break;
    
    case (KeyEvent.VK_DOWN):
      boost = false;
      break;

    case (KeyEvent.VK_LEFT):
      left = false;
      break;
      
    case (KeyEvent.VK_RIGHT):
      right = false;
      break;
      
    case (KeyEvent.VK_SPACE):
      shoot = false;
      break;
      
    case (KeyEvent.VK_A):
      stop = false;
      break;
      
    case (KeyEvent.VK_F):
      dontShoot = false;
      break;
    }

    e.consume();
  }
  
  @Override
  public void keyTyped(KeyEvent e) { }
  
  
}
