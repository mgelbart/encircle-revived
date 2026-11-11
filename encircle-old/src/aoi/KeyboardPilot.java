package aoi;

import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;

import aoi.Logger.Rep;

/**
 * A simple interface for controlling your ship based on keyboard events.
 */
public class KeyboardPilot implements KeyListener {

  
  private boolean accelerate = false;
  private long accPressed = 0; // when the key was pressed down
  private long accReleased = 0; // when the key was released
  private boolean left = false;
  private boolean right = false;
  private long turningAt = 0;
  private long turningReleased = 0;
  public boolean shoot = false;
  private boolean boost = false;
  
  public boolean stop = false;
  public boolean dontShoot = false;
  
  public long lastAction = 0;
  
  public PlayerControlParameters getParams(GameState currentGameState) {
    final long time = System.currentTimeMillis();
    final long sinceAcc = time-accPressed;
    final long sinceTurning = time - turningAt;
    final double TIMECONST = 300; // in ms

    final int acc = (accelerate)? (int) Math.min(SimulationUtils.MAXACC,
        SimulationUtils.MAXACC*(0.7+0.3*Math.pow(sinceAcc/TIMECONST,1.))) : 0;

    final Ship ship =  currentGameState.pStates[ClientConfiguration.playerNo].ship;
    final double omega = Math.min(SimulationUtils.OMEGA,
        SimulationUtils.OMEGA*(0.4+0.6*Math.pow(sinceTurning/TIMECONST,1.)));

    //Logger.println(Rep.FEW, omega/SimulationUtils.OMEGA);
    return new PlayerControlParameters(currentGameState.time, acc,
         ship.dir().add(omega*( ((left)? 1:0) - ((right)? 1:0) )),
         shoot, boost);
  }
  
  @Override
  public void keyPressed(KeyEvent e) {
    //Logger.println(Logger.Rep.INFREQ, "pressed " + e.getKeyCode());
    final long MINRELEASETIME = 20; // in ms
    final long time = System.currentTimeMillis();
    switch (e.getKeyCode()) {
    case (KeyEvent.VK_UP):
      accelerate = true;
      if (time - accReleased > MINRELEASETIME)
        // ignore short releases (because some OSs, like Ubuntu by default,
        // send keypressed/released repeatedly when you hold down a button)
        accPressed = time; 
      break;
    
    case (KeyEvent.VK_DOWN):
      boost = true;
      break;

    case (KeyEvent.VK_LEFT):
      left = true;
      if (time - turningReleased > MINRELEASETIME)
        turningAt = time;
      break;
      
    case (KeyEvent.VK_RIGHT):
      right = true;
      if (time - turningReleased > MINRELEASETIME)
        turningAt = time;
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
    if (e.getKeyCode() != KeyEvent.VK_SPACE)
      lastAction = System.currentTimeMillis();
  }
  
  @Override
  public void keyReleased(KeyEvent e) {
    //Logger.println(Logger.RepetitionRate.INFREQUENT, "released " + e.getKeyCode());    
    final long time = System.currentTimeMillis();
    switch (e.getKeyCode()) {
    case (KeyEvent.VK_UP):
      accelerate = false;
      accReleased = time;
      break;
    
    case (KeyEvent.VK_DOWN):
      boost = false;
      break;

    case (KeyEvent.VK_LEFT):
      left = false;
      turningReleased = time;
      break;
      
    case (KeyEvent.VK_RIGHT):
      right = false;
      turningReleased = time;
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
    if (e.getKeyCode() != KeyEvent.VK_SPACE)
      lastAction = System.currentTimeMillis();
  }
  
  @Override
  public void keyTyped(KeyEvent e) { }
  
  
}
