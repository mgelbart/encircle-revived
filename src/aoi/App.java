package aoi;

import java.awt.Canvas;
import javax.swing.*;

import aoi.Logger.Rep;

/**
 *  A wrapper class used to run the client as an applet.
 */
public class App extends JApplet {

  private static final long serialVersionUID = -36103157688567383L;
  Client client;
  Thread clientThread;

  public void init() {

    final GameCanvas canvas = new GameCanvas();

    /*
     *  From: http://download.oracle.com/javase/tutorial/uiswing/concurrency/initial.html
     *  In an applet, the GUI-creation task must be launched from the init method using
     *  invokeAndWait; otherwise, init may return before the GUI is created, which may
     *  cause problems for a web browser launching an applet.
     */
    try {
      SwingUtilities.invokeAndWait(new Runnable() {
          public void run() {
            add(canvas);
          }
        });
    } catch (Exception e) {
      Logger.println(Rep.FEW, "createGUI didn't complete successfully");
    }

    client = new Client(canvas);
  }

  public void start() {
    clientThread = new Thread(client, "client-main");
    clientThread.start();
  }

}
