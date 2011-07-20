package aoi;

import java.io.*;
import java.util.Vector;

/**
 *  A class used for communicating the game state
 *  from the server to the client.
 */
public class GameState implements Comparable<GameState> {
  public final int time;
  public final PlayerState[] pStates;
  public final Vector<Bullet> bullets;

  @SuppressWarnings("unchecked")
    public GameState(int time, PlayerState[] states, Vector<Bullet> bullets) {
    assert(states.length == SimulationUtils.NUMPLAYERS);

    this.time = time;
    this.pStates = (PlayerState[]) states.clone();
    this.bullets = (Vector<Bullet>) bullets.clone();
  }

  /** The 'natural' order is time ordering.
   *
   * Note: Since time is periodic, the ordering is also periodic
   *       and transitivity _is_ violated if the GameStates in
   *       the set of interest differ by more than half the full
   *       time range.
   */
  public int compareTo(GameState that) {
    return this.time - that.time;
  }

  public boolean equals(GameState that) {
    return this.time == that.time;
  }
  
  public void assertDeepEquals(GameState that) {
    assert(this.time == that.time);
    assert(this.pStates.length == that.pStates.length);
    for (int i = 0; i < pStates.length; i++)
      this.pStates[i].assertEquals(that.pStates[i]);
    assert(this.bullets.equals(that.bullets));
  }
  
  public boolean deepEquals(GameState that, boolean log) {
    // Compare times
    if (this.time != that.time) {
      if (log) Logger.println(Logger.RepetitionRate.FEW, "times disagree");
      return false;
    }

    // Compare pStates
    if (this.pStates.length != that.pStates.length) {
        if (log) Logger.println(Logger.RepetitionRate.FEW, 
                 "pStates disagree: different length");
        return false; 
    }
    for (int i = 0; i < pStates.length; i++) {
      if (!this.pStates[i].equals(that.pStates[i])) {
        if (log) Logger.println(Logger.RepetitionRate.FEW, 
                 "pStates disagree: pState " + i + " disagrees");
        if (this.pStates[i].tail.nPoints() != that.pStates[i].tail.nPoints()) {
          if (log) {
            Logger.println(Logger.RepetitionRate.FEW, "the tails disagree in number of points");
            Logger.println(Logger.RepetitionRate.FEW, this.pStates[i].tail.nPoints());
            Logger.println(Logger.RepetitionRate.FEW, that.pStates[i].tail.nPoints());
          }      
        } else if (!this.pStates[i].tail.equals(that.pStates[i].tail)) {
          if (log) {
            Logger.println(Logger.RepetitionRate.FEW, "the tails disagree");
            Logger.println(Logger.RepetitionRate.FEW, this.pStates[i].tail);
            Logger.println(Logger.RepetitionRate.FEW, that.pStates[i].tail);      
          }
        }
        
        return false;
      }
    }

    if (!this.bullets.equals(that.bullets)) {
      if (log) Logger.println(Logger.RepetitionRate.FEW, "bullets disagree");
      return false;
    }
    return true;
  }

  public String toString() {
    StringBuilder s = new StringBuilder();
	
    s.append("time: ");
    s.append(time);
    s.append("\n");

    for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j)
      s.append(pStates[j].toString());

    for (int j=0; j<bullets.size(); ++j)
      s.append(bullets.get(j).toString());

    return s.toString();
  }

  public void serialize(OutputStream o) {

    DataOutputStream b = new DataOutputStream(o);

    try {

      b.writeBytes("t");
      b.writeInt(time);

      for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j)
        pStates[j].serialize(b);

      b.writeBytes("b");
      b.writeInt(bullets.size());

      for (int j=0; j<bullets.size(); ++j)
        bullets.get(j).serialize(b);

    } catch (IOException ioe) {
      Logger.println(Logger.RepetitionRate.FEW, ioe.toString());
      assert(false);
    }

  }

  // Construct the new GameState using the state data received from the server
  // and the previous state (needed to construct things like Tails that are sent incrementally).
  public static GameState parse(DataInputStream i, GameState prevState) {

    Byte cmd;
    int t, bCount;
    PlayerState states[] = new PlayerState[SimulationUtils.NUMPLAYERS];
    Vector<Bullet> bullets = new Vector<Bullet>();

    try {

      cmd = i.readByte();
      assert(cmd == 't');
      t = i.readInt();

      for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j)
        states[j] = PlayerState.parse(i, (prevState == null) ? null : prevState.pStates[j], t);
	    
      cmd = i.readByte();
      assert(cmd == 'b');
      bCount = i.readInt();
      assert(bCount >= 0);
	    
      for (int j=0; j<bCount; ++j)
        bullets.add(Bullet.parse(i));
	    

    } catch (IOException ioe) {
      Logger.println(Logger.RepetitionRate.FEW, ioe.toString());
      assert(false);
      return null;
    }

    return new GameState(t, states, bullets);
  }
}
