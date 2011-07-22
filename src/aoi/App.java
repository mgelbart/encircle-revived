package aoi;

import javax.swing.*;

/**
 *  A wrapper class used to run the client as an applet.
 */
public class App extends JApplet {

  private static final long serialVersionUID = -36103157688567383L;
  Client client;
  Thread clientThread;

  public void init() {

    final JButton gui = new JButton();
    gui.setBorder(null);
    setContentPane(gui);
    
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

    client = new Client(gui);
  }

  public void start() {
    clientThread = new Thread(client, "client-main");
    clientThread.start();
  }

}
