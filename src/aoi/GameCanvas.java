package aoi;

import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.image.BufferStrategy;
import java.awt.image.BufferedImage;
import java.io.IOException;

import javax.imageio.ImageIO;
import javax.swing.JComponent;

import aoi.Logger.Rep;

public class GameCanvas extends JComponent {
  enum Mode { PREGAME, INGAME, POSTGAME, ERROR };
  
  static Mode mode = null;
  
  static boolean wantsAI = false;
  static boolean wantsAnotherGame = false;
  
  // Double buffered graphics
  // Offscreen stores the currently buffered image that
  // methods like shape() and image() draw on.
  // Calling show() switches this buffered image to the screen.
  private static BufferedImage offscreenImage, onscreenImage;
  private static Graphics2D offscreen, onscreen;
  private static GraphicsConfiguration graphicsConfig;
  private static Object switchingBuffersLock = new Object();

  // Set to true if the canvas is resized.
  // Need to reallocate the buffer images when this happens.
  private static boolean canvasResized = false;  

  private static String BKGIMGPATH = "guiimg/bkg.png";
  private static Image bkgImg;
  
  private static GameCanvas theCanvas;

  private static String[] currentMsg;
  
  GameCanvas() {
    assert(theCanvas == null);
    theCanvas = this;
    
    graphicsConfig = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration();
    
    addComponentListener(new ResizeListener());
    
    // load bkgImg from a file.
    try {
      bkgImg = ImageIO.read(GameCanvas.class.getClassLoader().getResourceAsStream(BKGIMGPATH));
    } catch (IOException e) {
      Logger.println(Rep.FEW, "failed to load the bkg image.");
      e.printStackTrace();
    }

    Font f = new Font("Monospaced", Font.BOLD, 18);
    setFont(f);
    
    toPreGame();
  }
  
  static boolean isInitialized() { return theCanvas != null; }
  
  static void toGameMode() {
    // remove all existing listeners
    for (MouseListener l : theCanvas.getMouseListeners())
      theCanvas.removeMouseListener(l);
    
    //theCanvas.setIgnoreRepaint(true);
    //theCanvas.createBufferStrategy(2); // double buffering
    //strategy = theCanvas.getBufferStrategy();

    initBuffer();
    
//    BufferCapabilities cab = strategy.getCapabilities();
//    Logger.println(Rep.FEW, "page flipping: " + cab.isPageFlipping());
//    Logger.println(Rep.FEW, "HW accelerated front/back buffer: " + cab.getFrontBufferCapabilities().isAccelerated()
//                                + " / " + cab.getBackBufferCapabilities().isAccelerated());
    
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
          setCurrentMsg(new String[] { "Asking for a match against the computer..." });
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
    
    setCurrentMsg(new String[] {
        "Waiting for other players...",
        "Tired of waiting?",
        "Click here to play against the computer"});
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
          setCurrentMsg(new String[] { "Connecting..." });
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

    setCurrentMsg(new String[] { "Click here to play again." });
  }
  
  static void toError() {
    mode = Mode.ERROR;
    theCanvas.setIgnoreRepaint(false);
    
    // remove all existing listeners
    for (MouseListener l : theCanvas.getMouseListeners())
      theCanvas.removeMouseListener(l);

    setCurrentMsg(new String[] {
        "Oops... An error has occured.",
        "Reload the page for a new game."});
  }
  
  /*
   *  return the current 'offscreen' buffer image
   */
  static Graphics2D offscreen() {
    return offscreen;
  }
  
  static void showBuffered() {
    if (mode != Mode.INGAME) return;

    synchronized(switchingBuffersLock) {

      if (canvasResized) {
        // reallocate the buffer images and skip frame.
        canvasResized = false;
        initBuffer();
        return;
      }
      
      /*
       *  Paint the buffered image
       */
      assert(offscreen != null);
      if (offscreen != null) {
        Graphics2D t = onscreen;
        BufferedImage tImg = onscreenImage;
        onscreen = offscreen;
        onscreenImage = offscreenImage;
        offscreen = t;
        offscreenImage = tImg;

        // disable anti-aliasing on the new offscreen image
        setAntialiasing(false);
        
        theCanvas.repaint();
      }

    }
  }

  @Override
  public void paintComponent(Graphics g) {
    if (mode == Mode.INGAME) {
      synchronized(switchingBuffersLock) {
        g.drawImage(onscreenImage, 0, 0, null);
      }
      return;
    }
    
    // paint the background image
    final int h = getSize().height;
    final int w = getSize().width;
    final int maxhw = Math.max(h, w);
    
    g.drawImage(bkgImg, 0, 0, maxhw, maxhw, null);
    
    // approximate average pixels per char.
    final double hOffsetPerChar = 5.;
    final int vOffsetPerLine = 30;

    // Copy the reference to make this thread safe.
    String[] msg = currentMsg;
    if (currentMsg != null) {
      int vOffset = -msg.length * vOffsetPerLine/2;
      for (String l : msg) {
        g.drawString(l, (int) (w/2-l.length()*hOffsetPerChar), h/2+vOffset);
        vOffset += vOffsetPerLine;
      }
    }

  }
  
  public static void setCurrentMsg(String[] msg) {
    currentMsg = msg.clone();
    theCanvas.repaint();
  }
  
  public static void setAntialiasing(boolean on) {
    if (on) {
      RenderingHints hints = new RenderingHints(RenderingHints.KEY_ANTIALIASING,
                                                RenderingHints.VALUE_ANTIALIAS_ON);
      //hints.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      offscreen.addRenderingHints(hints);
    } else {
      RenderingHints hints = new RenderingHints(RenderingHints.KEY_ANTIALIASING,
                                                RenderingHints.VALUE_ANTIALIAS_OFF);
      //hints.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      offscreen.addRenderingHints(hints);
    }
  }
  
  /**
   * Get the new offscreen image to buffer to.
   */
  private static void initBuffer() {
    // Initialize the off and onscreen buffers.
    Dimension s = theCanvas.getSize();
    offscreenImage = graphicsConfig.createCompatibleImage(s.width, s.height, Transparency.TRANSLUCENT);
    onscreenImage  = graphicsConfig.createCompatibleImage(s.width, s.height, Transparency.TRANSLUCENT);
    offscreen = offscreenImage.createGraphics();
    onscreen = onscreenImage.createGraphics();

    // add antialiasing
    //RenderingHints hints = new RenderingHints(RenderingHints.KEY_ANTIALIASING,
    //                                          RenderingHints.VALUE_ANTIALIAS_ON);
    //hints.put(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
    //offscreen.addRenderingHints(hints);
    //onscreen.addRenderingHints(hints);
  }
  
  private static class ResizeListener implements ComponentListener {
    
    public void componentResized(ComponentEvent e) {
      canvasResized = true;
    }

    public void componentHidden(ComponentEvent arg0) {}

    public void componentMoved(ComponentEvent arg0) {}

    public void componentShown(ComponentEvent arg0) {}
  }
  
  @Override
  public boolean isFocusTraversable() { return true; }
}
