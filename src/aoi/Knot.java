package aoi;

public class Knot {
  
  public static final int DEFAULTNUMLIVES = 1;
  
  public final int ind;
  private int lives;  // The special value of -1 means infinitely many lines
                // or, in other words, that the knot is indestructables
  
  public Knot(int ind, int lives) {
    this.ind = ind;
    this.lives = lives;
  }
  public Knot(int ind, boolean indestructable) {
    this(ind, indestructable?-1:DEFAULTNUMLIVES);
  }
  public Knot(Knot that) {
    this.ind = that.ind;
    this.lives = that.lives;
  }
  
  // Use up one of the knot's lives.
  public void use() {
    if (lives == -1) return;
    lives = Math.max(0, lives-1);
  }
  
  public boolean isAlive() { return lives!=0; }
  public int       lives() { return lives;    }
  public boolean isIndes() { return lives==-1;}
  
}
