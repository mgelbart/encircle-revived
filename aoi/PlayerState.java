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

    public static final int SHIPLENGTH = Simulation.ARENASIZE * 8/200;
    public static final int SHIPWIDTH = Simulation.ARENASIZE * 3/200;

    /* Values transmitted to the clients. */
    public final int playerNo;
    public final int x;
    public final int y;
    public final int vx;
    public final int vy;
    public final int direction;
    public final int energy;

    // The time at which the last loop was completed/current one started.
    public final int currentLoop;

    // How many clusters the player has collected already
    public final int score;


    public PlayerState(int playerNo, int x, int y, int vx, int vy, int direction, int energy, int currentLoop, int score) {
	this.playerNo = playerNo;
	this.x = x;
	this.y = y;
	this.vx = vx;
	this.vy = vy;
	this.direction = direction;
	this.energy = energy;
	this.currentLoop = currentLoop;
	this.score = score;

	assert(playerNo < Simulation.NUMPLAYERS);
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

	    o.writeBytes("u");
	    o.writeInt(vx);

	    o.writeBytes("v");
	    o.writeInt(vy);

	    o.writeBytes("d");
	    o.writeInt(direction);

	    o.writeBytes("e");
	    o.writeInt(energy);

	    o.writeBytes("l");
	    o.writeInt(currentLoop);

	    o.writeBytes("s");
	    o.writeInt(score);

	} catch (IOException ioe) {
	    System.out.println(ioe.toString());
	    assert(false);
	}

    }

    public static PlayerState parse(DataInputStream i) {

	Byte cmd;
	int p, x, y, vx, vy, d, e, l, s;

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
	    assert(cmd == 'u');
	    vx = i.readInt();

	    cmd = i.readByte();
	    assert(cmd == 'v');
	    vy = i.readInt();

	    cmd = i.readByte();
	    assert(cmd == 'd');
	    d = i.readInt();

	    cmd = i.readByte();
	    assert(cmd == 'e');
	    e = i.readInt();

	    cmd = i.readByte();
	    assert(cmd == 'l');
	    l = i.readInt();

	    cmd = i.readByte();
	    assert(cmd == 's');
	    s = i.readInt();

	    
	} catch (IOException ioe) {
	    System.out.println(ioe.toString());
	    assert(false);
	    return null;
	}

	return new PlayerState(p, x, y, vx, vy, d, e, l, s);
    }


    public int[][] shape() {
	assert(playerNo >= 0);
	assert(playerNo < Simulation.NUMPLAYERS);

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

