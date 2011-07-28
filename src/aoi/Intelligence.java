package aoi;

public interface Intelligence extends Runnable {
  
  /**
   *  The client calls this whenever it receives a game state update from the server.
   */
  public void processGameStateUpdate(GameState game);
  
  /**
   * The client sends all control parameters returned by this function to the server.
   */
  public PlayerControlParameters[] getNewControlParameters();
  
  /**
   * This function should gracefully terminate all threads associated with this
   * Intelligence.
   */
  public void stop();
}
