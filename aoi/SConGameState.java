package aoi;

/*
  A class used for communicating the game state
  from the server to the client.
 */

import java.io.*;

public class SConGameState implements Cloneable {
    public final int time;
    public final SConPlayerState[] states;

    public SConGameState(int time, SConPlayerState[] states) {
	assert(states.length == 2);

	this.time = time;
	this.states = states;
    }

    public SConGameState clone() {
	SConPlayerState[] clonedStates = new SConPlayerState[2];

	for (int j=0; j<2; ++j)
	    clonedStates[j] = states[j].clone();

	return new SConGameState(time, clonedStates);
    }

    public String toString() {
	StringBuilder s = new StringBuilder();
	
	s.append("time: ");
	s.append(time);
	s.append("\n");

	for (int j=0; j<2; ++j)
	    s.append(states[j].toString());

	return s.toString();
    }

    public void serialize(DataOutputStream o) {

	try {

	    o.writeBytes("t");
	    o.writeInt(time);

	    for (int j=0; j<2; ++j)
		states[j].serialize(o);

	} catch (IOException ioe) {
	    System.out.println(ioe.toString());
	    assert(false);
	}

    }

    public static SConGameState parse(DataInputStream i) {

	Byte cmd;
	int t;
	SConPlayerState states[] = new SConPlayerState[2];

	try {

	    cmd = i.readByte();
	    assert(cmd == 't');
	    t = i.readInt();

	    for (int j=0; j<2; ++j)
		states[j] = SConPlayerState.parse(i);
	    
	} catch (IOException ioe) {
	    System.out.println(ioe.toString());
	    assert(false);
	    return null;
	}

	return new SConGameState(t, states);
    }
}
