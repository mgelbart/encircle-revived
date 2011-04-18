/* Helper class used to tell the simulator what the
   player wants to do.

   Note that ints are used although floats might be
   more natural. This is because ints don't need
   any special serialization/parsing to be transmitted
   over the network.

*/

import java.io.*;

class SConPlayerState implements Cloneable {
    /* Values transmitted to the clients. */
    public final int playerNo;
    public final int x;
    public final int y;
    public final int direction;
    public final int energy;

    public SConPlayerState(int playerNo, int x, int y, int direction, int energy) {
	this.playerNo = playerNo;
	this.x = x;
	this.y = y;
	this.direction = direction;
	this.energy = energy;
    }

    public SConPlayerState clone() {
	try {
	    return (SConPlayerState) super.clone();
	}
	catch (CloneNotSupportedException e) {
	    return null;
	}
    }

    public String toString() {
	StringBuilder s = new StringBuilder();

	s.append("x: ");
	s.append(x);

	s.append("\ny: ");
	s.append(y);

	s.append("\ndirection: ");
	s.append(direction);

	s.append("\n");

	return s.toString();
    }

    public void serialize(DataOutputStream o) {

	try {

	    o.writeBytes("#");
	    o.writeInt(playerNo);

	    o.writeBytes("x");
	    o.writeInt(x);

	    o.writeBytes("y");
	    o.writeInt(y);

	    o.writeBytes("d");
	    o.writeInt(direction);

	    o.writeBytes("e");
	    o.writeInt(energy);

	} catch (IOException ioe) {
	    System.out.println(ioe.toString());
	    assert(false);
	}

    }

    public static SConPlayerState parse(DataInputStream i) {

	Byte cmd;
	int p, x, y, d, e;

	try {

	    cmd = i.readByte();
	    assert(cmd == '#');

	    p = i.readInt();
	    assert(p == 1 || p == 2);

	    cmd = i.readByte();
	    assert(cmd == 'x');
	    x = i.readInt();

	    cmd = i.readByte();
	    assert(cmd == 'y');
	    y = i.readInt();

	    cmd = i.readByte();
	    assert(cmd == 'd');
	    d = i.readInt();

	    cmd = i.readByte();
	    assert(cmd == 'e');
	    e = i.readInt();

	    
	} catch (IOException ioe) {
	    System.out.println(ioe.toString());
	    assert(false);
	    return null;
	}

	return new SConPlayerState(p, x, y, d, e);
    }
}


class SConPlayerStateInternal {
    /* Values used internally by the simulation. */
    public int vx;
    public int vy;
}

