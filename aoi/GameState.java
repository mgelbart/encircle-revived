package aoi;

/*
  A class used for communicating the game state
  from the server to the client.
 */

import java.io.*;
import java.util.Vector;

public class GameState implements Cloneable {
    public final int time;
    public final PlayerState[] states;
    public final Vector<Bullet> bullets;

    public GameState(int time, PlayerState[] states, Vector<Bullet> bullets) {
	assert(states.length == 2);

	this.time = time;
	this.states = states;
	this.bullets = bullets;
    }

    public GameState clone() {
	PlayerState[] clonedStates = new PlayerState[2];
	Vector<Bullet> clonedBullets = new Vector<Bullet>();

	for (int j=0; j<2; ++j)
	    clonedStates[j] = states[j].clone();

	for (int j=0; j<bullets.size(); ++j)
	    clonedBullets.add(bullets.get(j).clone());

	return new GameState(time, clonedStates, clonedBullets);
    }

    public String toString() {
	StringBuilder s = new StringBuilder();
	
	s.append("time: ");
	s.append(time);
	s.append("\n");

	for (int j=0; j<2; ++j)
	    s.append(states[j].toString());

	for (int j=0; j<bullets.size(); ++j)
	    s.append(bullets.get(j).toString());

	return s.toString();
    }

    public void serialize(DataOutputStream o) {

	try {

	    o.writeBytes("t");
	    o.writeInt(time);

	    for (int j=0; j<2; ++j)
		states[j].serialize(o);

	    o.writeBytes("b");
	    o.writeInt(bullets.size());

	    for (int j=0; j<bullets.size(); ++j)
		bullets.get(j).serialize(o);

	} catch (IOException ioe) {
	    System.out.println(ioe.toString());
	    assert(false);
	}

    }

    public static GameState parse(DataInputStream i) {

	Byte cmd;
	int t, bCount;
	PlayerState states[] = new PlayerState[2];
	Vector<Bullet> bullets = new Vector<Bullet>();

	try {

	    cmd = i.readByte();
	    assert(cmd == 't');
	    t = i.readInt();

	    for (int j=0; j<2; ++j)
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
