package aoi;

public class DefaultIntelligenceWithGUI extends DefaultIntelligence {

  public DefaultIntelligenceWithGUI() {
    super();
   
    gui = new GUI();
    guiThread = new Thread(gui, "gui");
    guiThread.setPriority(Thread.MAX_PRIORITY);
    guiThread.start();
  }
}
