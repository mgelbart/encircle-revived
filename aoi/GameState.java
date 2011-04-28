package aoi;

/*
  A class used for communicating the game state
  from the server to the client.
 */

import java.io.*;

public class GameState implements Cloneable {
    public final int time;
    public final PlayerState[] states;
    public final Bullet[] bullets;

    // This is redundant but it's more convenient than always writing:
    //   (bullets != null) ? bullets.length : 0
    public final int bulletCount;

    public GameState(int time, PlayerState[] states, Bullet[] bullets) {
	assert(states.length == 2);

	this.time = time;
	this.states = states;
	this.bullets = bullets;

	this.bulletCount = (bullets != null) ? bullets.length : 0;
    }

    public GameState clone() {
	PlayerState[] clonedStates = new PlayerState[2];
	Bullet[] clonedBullets = (bullets != null)
	                           ? new Bullet[bulletCount] : null;

	for (int j=0; j<2; ++j)
	    clonedStates[j] = states[j].clone();

	for (int j=0; j<bulletCount; ++j)
	    clonedBullets[j] = bullets[j].clone();

	return new GameState(time, clonedStates, clonedBullets);
    }

    public String toString() {
	StringBuilder s = new StringBuilder();
	
	s.append("time: ");
	s.append(time);
	s.append("\n");

	for (int j=0; j<2; ++j)
	    s.append(states[j].toString());

	for (int j=0; j<bulletCount; ++j)
	    s.append(bullets[j].toString());

	return s.toString();
    }

    public void serialize(DataOutputStream o) {

	try {

	    o.writeBytes("t");
	    o.writeInt(time);

	    for (int j=0; j<2; ++j)
		states[j].serialize(o);

	    o.writeBytes("b");
	    o.writeInt(bulletCount);

	    for (int j=0; j<bulletCount; ++j)
		bullets[j].serialize(o);

	} catch (IOException ioe) {
	    System.out.println(ioe.toString());
	    assert(false);
	}

    }

    public static GameState parse(DataInputStream i) {

	Byte cmd;
	int t, bCount;
	PlayerState states[] = new PlayerState[2];
	Bullet bullets[];

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
	    
	    bullets = (bCount > 0) ? new Bullet[bCount] : null;
	    for (int j=0; j<bCount; ++j)
		bullets[j] = Bullet.parse(i);
	    

	} catch (IOException ioe) {
	    System.out.println(ioe.toString());
	    assert(false);
	    return null;
	}

	return new GameState(t, states, bullets);
    }
}
