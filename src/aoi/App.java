package aoi;

import java.awt.Canvas;
import javax.swing.*;

/**
 *  A wrapper class used to run the client as an applet.
 */
public class App extends JApplet {

  private static final long serialVersionUID = -36103157688567383L;
  Client client;
  Thread clientThread;

  public void init() {

    final GameCanvas canvas = new GameCanvas();
    //canvas.setBorder(null);
    add(canvas);
    
    /*
    try {
      SwingUtilities.invokeAndWait(new Runnable() {
          public void run() {
            add(gui);
          }
        });
    } catch (Exception e) {
      Logger.println(Logger.RepetitionRate.FEW, "createGUI didn't complete successfully");
    }
    */

    client = new Client(canvas);
  }

  public void start() {
    clientThread = new Thread(client, "client-main");
    clientThread.start();
  }

}
