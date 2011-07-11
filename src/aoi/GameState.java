package aoi;

import java.io.*;
import java.util.Vector;

/**
 *  A class used for communicating the game state
 *  from the server to the client.
 */
public class GameState implements Comparable<GameState> {
  public final int time;
  public final PlayerState[] states;
  public final Vector<Bullet> bullets;

  @SuppressWarnings("unchecked")
    public GameState(int time, PlayerState[] states, Vector<Bullet> bullets) {
    assert(states.length == SimulationUtils.NUMPLAYERS);

    this.time = time;
    this.states = (PlayerState[]) states.clone();
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

  public String toString() {
    StringBuilder s = new StringBuilder();
	
    s.append("time: ");
    s.append(time);
    s.append("\n");

    for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j)
      s.append(states[j].toString());

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
        states[j].serialize(b);

      b.writeBytes("b");
      b.writeInt(bullets.size());

      for (int j=0; j<bullets.size(); ++j)
        bullets.get(j).serialize(b);

    } catch (IOException ioe) {
      System.err.println(ioe.toString());
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
        states[j] = PlayerState.parse(i, (prevState == null) ? null : prevState.states[j], t);
	    
      cmd = i.readByte();
      assert(cmd == 'b');
      bCount = i.readInt();
      assert(bCount >= 0);
	    
      for (int j=0; j<bCount; ++j)
        bullets.add(Bullet.parse(i));
	    

    } catch (IOException ioe) {
      System.out.println(ioe.toString());
      assert(false);
      return null;
    }

    return new GameState(t, states, bullets);
  }
}
