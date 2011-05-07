package aoi;

/* A "read-only" class that describes a player's
   position in phase space.

   Note that ints are used although floats might be
   more natural. This is because ints don't need
   any special serialization/parsing to be transmitted
   over the network.

*/

import java.io.*;

public class PlayerState implements Cloneable {

    public static final int SHIPLENGTH = Simulation.MAPSIZE * 8/200;
    public static final int SHIPWIDTH = Simulation.MAPSIZE * 3/200;

    /* Values transmitted to the clients. */
    public final int playerNo;
    public final int x;
    public final int y;
    public final int direction;
    public final int energy;

    public PlayerState(int playerNo, int x, int y, int direction, int energy) {
	this.playerNo = playerNo;
	this.x = x;
	this.y = y;
	this.direction = direction;
	this.energy = energy;

	assert(playerNo < 2);
	assert(playerNo >= 0);
    }

    public PlayerState clone() {
	try {
	    return (PlayerState) super.clone();
	}
	catch (CloneNotSupportedException e) {
	    return null;
	}
    }

    public String toString() {
	StringBuilder s = new StringBuilder();

	s.append("Player #");
	s.append(playerNo);

	s.append("\nx: ");
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

    public static PlayerState parse(DataInputStream i) {

	Byte cmd;
	int p, x, y, d, e;

	try {

	    cmd = i.readByte();
	    assert(cmd == '#');

	    p = i.readInt();
	    assert(p == 0 || p == 1);

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

	return new PlayerState(p, x, y, d, e);
    }


    public int[][] shape() {
	assert(playerNo >= 0);
	assert(playerNo < 2);

	double cos = Math.cos(Math.PI * (direction / (double) Simulation.INTMAX));
	double sin = Math.sin(Math.PI * (direction / (double) Simulation.INTMAX));

	int x, y;

	int px[] = new int[4];
	int py[] = new int[4];

	for (int j=0; j<4; ++j) {

	    // Generate one of the four corners relative to origin.
	    x = ((j==0 || j==1)?  1:-1) * SHIPLENGTH / 2;
	    y = ((j==0 || j==3)? -1: 1) * SHIPWIDTH  / 2;

	    // Rotate each point by direction (multiply by {{cos -sin} {sin cos}})
	    // and translate to player location.
	    px[j] = this.x + (int) (cos*x - sin*y);
	    py[j] = this.y + (int) (sin*x + cos*y);

	}

	return new int[][]{px, py};
    }

}

