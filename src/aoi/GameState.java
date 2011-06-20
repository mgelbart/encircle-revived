package aoi;

import java.io.*;
import java.util.Vector;

/**
 *  A class used for communicating the game state
 *  from the server to the client.
 */
public class GameState implements Comparable {
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
  public int compareTo(Object o) {
    return this.time - ((GameState) o).time;
  }

  public boolean equals(Object o) {
    return this.time == ((GameState) o).time;
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

  public static GameState parse(DataInputStream i) {

    Byte cmd;
    int t, bCount;
    PlayerState states[] = new PlayerState[SimulationUtils.NUMPLAYERS];
    Vector<Bullet> bullets = new Vector<Bullet>();

    try {

      cmd = i.readByte();
      assert(cmd == 't');
      t = i.readInt();

      for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j)
        states[j] = PlayerState.parse(i);
	    
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
