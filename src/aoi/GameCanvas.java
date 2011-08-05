package aoi;

import java.awt.BufferCapabilities;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferStrategy;
import java.io.IOException;

import javax.imageio.ImageIO;

import aoi.Logger.Rep;

public class GameCanvas extends Canvas {
  enum Mode { PREGAME, INGAME, POSTGAME, ERROR };
  
  static Mode mode = null;
  
  static boolean wantsAI = false;
  static boolean wantsAnotherGame = false;
  
  // Double buffered graphics.
  // offscreen stores the buffered image that will be showed when show()
  // is next called.
  private static BufferStrategy strategy;
  private static Graphics2D offscreen = null;
  
  private static String BKGIMGPATH = "guiimg/bkg.png";
  private static Image bkgImg;
  
  private static GameCanvas theCanvas = null;
  
  GameCanvas() {
    assert(theCanvas == null);
    
    // load bkgImg from a file.
    try {
      bkgImg = ImageIO.read(GameCanvas.class.getClassLoader().getResourceAsStream(BKGIMGPATH));
    } catch (IOException e) {
      Logger.println(Rep.FEW, "failed to load the bkg image.");
      e.printStackTrace();
    }

    theCanvas = this;
    toPreGame();
  }
  
  static boolean isInitialized() { return theCanvas != null; }
  
  static void toGameMode() {
    // remove all existing listeners
    for (MouseListener l : theCanvas.getMouseListeners())
      theCanvas.removeMouseListener(l);
    
    theCanvas.setIgnoreRepaint(true);
    theCanvas.createBufferStrategy(2); // double buffering
    strategy = theCanvas.getBufferStrategy();

    initBuffer();
    
    BufferCapabilities cab = strategy.getCapabilities();
    Logger.println(Rep.FEW, "page flipping: " + cab.isPageFlipping());
    Logger.println(Rep.FEW, "HW accelerated front/back buffer: " + cab.getFrontBufferCapabilities().isAccelerated()
                                + " / " + cab.getBackBufferCapabilities().isAccelerated());
    
    mode = Mode.INGAME;
  }
  
  static void toPreGame() {
    mode = Mode.PREGAME;
    theCanvas.setIgnoreRepaint(false);
    wantsAI = false;
    wantsAnotherGame = false;
    
    for (MouseListener l : theCanvas.getMouseListeners())
      theCanvas.removeMouseListener(l);
    
    // add a listener that allows the user to request an AI opponent.
    class PreGameMouseListener implements MouseListener {
      long timeCreated;
      PreGameMouseListener(long timeCreated) { this.timeCreated = timeCreated; }
      
      @Override
      public void mouseClicked(MouseEvent e) {
        // ignore clicks for the first second
        if (System.currentTimeMillis() - timeCreated > 1000) {
          Logger.println(Rep.FEW, "user wants to play against the AI.");
          GameCanvas.wantsAI = true;
          theCanvas.repaint();
        }
        e.consume();
      }
      @Override
      public void mouseEntered(MouseEvent e) {}
      @Override
      public void mouseExited(MouseEvent e) {}
      @Override
      public void mousePressed(MouseEvent e) {}
      @Override
      public void mouseReleased(MouseEvent e) {}
    };
    
    theCanvas.addMouseListener(new PreGameMouseListener(System.currentTimeMillis()));
    theCanvas.repaint();
  }
  
  static void toPostGame() {
    mode = Mode.POSTGAME;
    theCanvas.setIgnoreRepaint(false);
    
    // remove all existing listeners
    for (MouseListener l : theCanvas.getMouseListeners())
      theCanvas.removeMouseListener(l);
    
    // add a listener that allows the user to 
    class PostGameMouseListener implements MouseListener {
      long timeCreated;
      PostGameMouseListener(long timeCreated) { this.timeCreated = timeCreated; }
      
      @Override
      public void mouseClicked(MouseEvent e) {
        // ignore clicks for the first second
        if (System.currentTimeMillis() - timeCreated > 1000) {
          Logger.println(Rep.FEW, "user wants another game.");
          GameCanvas.wantsAnotherGame = true;
          theCanvas.repaint();
        }
        e.consume();
      }
      @Override
      public void mouseEntered(MouseEvent e) {}
      @Override
      public void mouseExited(MouseEvent e) {}
      @Override
      public void mousePressed(MouseEvent e) {}
      @Override
      public void mouseReleased(MouseEvent e) {}
    };
    
    theCanvas.addMouseListener(new PostGameMouseListener(System.currentTimeMillis()));
    theCanvas.repaint();
  }
  
  static void toError() {
    mode = Mode.ERROR;
    theCanvas.setIgnoreRepaint(false);
    
    // remove all existing listeners
    for (MouseListener l : theCanvas.getMouseListeners())
      theCanvas.removeMouseListener(l);

    theCanvas.repaint();
  }
  
  /*
   *  return the current 'offscreen' buffer image
   */
  static Graphics2D offscreen() {
    return offscreen;
  }
  
  static void showBuffered() {
    if (mode != Mode.INGAME) return;
    
    /*
     *  Paint the buffered image
     */
    assert(offscreen != null);
    if (offscreen != null) {
      offscreen.dispose();
      strategy.show();
    }
    
    initBuffer();
  }

  @Override
  public void paint(Graphics g) {
    if (mode == Mode.INGAME) return;
    
    // paint the background image
    final int h = getSize().height;
    final int w = getSize().width;
    final int maxhw = Math.max(h, w);
    
    g.drawImage(bkgImg, 0, 0, maxhw, maxhw, null);
    
    // approximate average pixels per char.
    final double horOffsetPerChar = 3.;
    
    if (mode == Mode.PREGAME) {
      if (!wantsAI) {
        String waiting = "Waiting for other players...";
        String tired = "Tired of waiting?";
        String ai = "Click here to play against the computer";
        
        g.drawString(waiting, (int) (w/2-waiting.length()*horOffsetPerChar), h/2-20);
        g.drawString(tired, (int) (w/2-tired.length()*horOffsetPerChar), h/2);
        g.drawString(ai, (int) (w/2-ai.length()*horOffsetPerChar), h/2+20);
      } else {
        String waiting = "Ok. The game will start in a moment...";
        
        g.drawString(waiting, (int) (w/2-waiting.length()*horOffsetPerChar), h/2);
      }
    }

    
    if (mode == Mode.POSTGAME) {
      String again = (wantsAnotherGame)? "Connecting..." : "Click here to play again.";
      g.drawString(again, (int) (w/2-again.length()*horOffsetPerChar), h/2);
    }

    if (mode == Mode.ERROR) {
      String error = "Oops... An error has occured.";
      String restart = "Reload the page for a new game.";
      
      g.drawString(error, (int) (w/2-error.length()*horOffsetPerChar), h/2-10);
      g.drawString(restart, (int) (w/2-restart.length()*horOffsetPerChar), h/2+10);
    }

  }
  
  /**
   * Get the new offscreen image to buffer to.
   */
  private static void initBuffer() {
    // this cast seems sketchy but it is apparently commonly used.
    offscreen = (Graphics2D) strategy.getDrawGraphics();

    // add antialiasing
    //RenderingHints hints = new RenderingHints(RenderingHints.KEY_ANTIALIASING,
    //                                          RenderingHints.VALUE_ANTIALIAS_ON);
    //hints.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    //offscreen.addRenderingHints(hints);
  }
}
