package aoi;

import java.awt.Point;
import java.awt.Shape;
import java.util.TreeSet;
import java.util.Vector;

/** 
 * The tail.
 */
public class Tail {
  
  // Roughly the minimum distance between adjacent points in the trajectory for
  // encircling clusters. See the addPoint() to see why this is only 'roughly' so.
  public static final int MINTRAJECTORYSEGMENT = SimulationUtils.ARENASIZE / 200;
  // ------------------------------------------------------------------------ //

  // The trajectory in the two space coordinates
  private final NSPath trajectory;
  
  // The time coordinate of each point
  private final Vector<Integer> times;
  
  private final KnotGroup knots; 
  
  // ---------------------- start constructors ----------------------- //
  public Tail(Point p, int time) {
    trajectory = new NSPath();
    times = new Vector<Integer>();
    knots = new KnotGroup();
    
    trajectory.addPoint(p);
    times.add(time);
    
    assert(trajectory.nPoints() == times.size());
  }
  private Tail(NSPath trajectory, Vector<Integer> times, KnotGroup knots) {
    this.trajectory = trajectory;
    this.times = times;
    this.knots = knots;
    
    assert(trajectory.nPoints() == times.size());
  }
  // ---------------------- end constructors ----------------------- //  
 
  public boolean equals(Tail that) {
    if (!this.trajectory.equals(that.trajectory)) return false;
    if (!this.times.equals(that.times)) return false;
    if (!this.knots.equals(that.knots)) return false;
    return true;
  }
  public void assertEquals(Tail that) {
    assert(this.nPoints() == that.nPoints());
    assert(this.nKnots() == that.nKnots());
    assert(this.knots.equals(that.knots));
    assert(this.times.equals(that.times));
    assert(this.trajectory.equals(that.trajectory));
  }
  
  
  // ------------------- start access functions --------------- //
  public int nPoints() {
    assert(trajectory.nPoints() == times.size());
    return Math.random()<0.5?trajectory.nPoints():times.size();
  }
  
  public int     nKnots  ()      { return knots.size();           }
  public double  length  ()      { return trajectory.length();    }
  public Shape  pathShape()      { return trajectory.clone();     }
  public boolean isEmpty ()      { return nPoints() == 0;         }
  public Point getPoint(int i)   { return trajectory.getPoint(i); }

  public boolean contains(double x, double y) {
    return trajectory.contains(x, y);
  }
  
  @SuppressWarnings("unchecked")
  public Iterable<Integer> getKnots() { 
    return (Iterable<Integer>)knots.clone(); 
  }
  
  public Point getPointFromTime(int time) {
    for (int i = 0; i < nPoints(); i++)
      if (times.get(i) == time)
        return getPoint(i);
    return null;
  }

  public Integer headTime() {
    if (isEmpty()) return null;
    else return times.lastElement();
  }
  public Integer baseTime() {
    if (isEmpty()) return null;
    else return times.firstElement();
  }

  // the time of the first (closest to base)
  // knot NOT INCLUDING THE KNOT AT BASE
  // Returns the head time if the knot
  // at the base is the only knot
  public int cropToForCrossing() {
    assert(!isEmpty());
    assert(nKnots() > 0);
    if (nKnots() == 1) return headTime(); 
    // (above) only knot at bases crop everything except the head    
    return times.get(knots.firstNotBase())+1;
    // (above) crop everything including this knot
  }

  // the first (absolute) time at which the path from 
  // index to that time has at least the specified length
  private int lengthFromIndexToTime(int index, double length) {
    assert(!isEmpty());
    NSPath subpath = new NSPath();

    for (int i = index; i < nPoints(); i++) {
      subpath.addPoint(getPoint(i)); 
      if (subpath.length() >= length)
        return times.get(i);
    }
    // what to do if the entire path is not long enough
    // here we use lastElement()+1 so that if the whole path
    // is not long enough, you don't crop anything
    // when the output of this function is used as cropTailFrom
    return headTime() + 1; 
  }
  public int lengthFromBaseToTime(double length) {
    return lengthFromIndexToTime(0, length);
  }
  public int lengthFromLastKnotToTime(double length) {
    return lengthFromIndexToTime(knots.last(), length);  
  }
  
  public double lengthFromLastKnotToHead() {
    assert(nKnots() > 0);
    double length = 0;
    for (int i = knots.last(); i < nPoints()-1; i++)
      length += getPoint(i).distance(getPoint(i+1));
    return length;
  }
  
  // Finds the angle of the line from i-1 to i
  public Angle tangentAngle(int i) {
    if (i <= 0 || i >= nPoints() || nPoints() < 2) return null;
    return new Angle(getPoint(i-1), getPoint(i)); 
  }
  public Angle tangentAngleHead() {
    return tangentAngle(nPoints()-1);
  }  

  // ------------- end access functions --------------- //
  
  
  
  // ------ start functions that (clone and) modify tail -------- //
  public Tail addKnotAtHead() {
    Tail newTail = clone();    
    newTail.knots.add(nPoints()-1);
    
    return newTail;
  }
  
  // Create a copy of the Tail with an added point.
  public Tail addPoint(Point curPoint, int time) {  
    if (isEmpty()) return new Tail(curPoint, time);
    
    Tail newTail = clone();
    
    Point prevPoint = newTail.trajectory.lastPoint();
    Point prevPrevPoint = newTail.trajectory.lastlastPoint();
    assert(prevPoint != null);
    
    if (!prevPoint.equals(curPoint)) {
      // Always add the latest position to the trajectory
      // but combine it with the previous segment if both are short.
      if (prevPrevPoint != null &&
            curPoint.distance(prevPrevPoint) < MINTRAJECTORYSEGMENT) {        
        newTail.trajectory.removeLast();
        newTail.times.remove(newTail.times.size()-1);
      }
      newTail.trajectory.addPoint(curPoint);
      newTail.times.add(time);
    }
    assert(newTail.trajectory.nPoints() == newTail.times.size());
    return newTail;
  }

  // Crops part of a tail given the times "from" and "to".
  // Removes "from" but not "to"
  public Tail crop(int from, int to) {
   
    // Fields for new Tail
    NSPath newPath = new NSPath();
    Vector<Integer> newTimes = new Vector<Integer>();
    KnotGroup newKnots = new KnotGroup();
    
    for (int i = 0; i < nPoints(); i++) {
      if (times.get(i) - from < 0 || times.get(i) - to >= 0) {
        // If the point is not cropped, add it
        newPath.addPoint(getPoint(i));
        newTimes.add(times.get(i));
        if (knots.contains(i)) newKnots.add(newTimes.size()-1);
      }
    }
    
    return new Tail(newPath, newTimes, newKnots);
  }
  

  /**
   * Does this Tail intersect with *the last line of* another Tail?
   * If so, return the loop that is formed as a Tail. If not, return null.
   * 
   * @param excludePoints Number of points to exclude from the end of 'this'.
   *                      Useful is you want to check for loops (this==that).
   *                      
   *   // Check if the last step of player j intersects with the path of player i
   *   // and if so, return the loop of player j that is now complete
   *   // (note: the behavior is not really defined when there is an intersection and i!=j,
   *   // but this is fine because in the i!=j case all we really need out of this function
   *   // is a boolean and loop==null is that boolean)
   *   // Note this is not symmetric in i and j.
   *                       
   *  The excludePoints is necessary to avoid spurious two segment loops                       
   */
  public Tail intersects(Tail that, int excludePoints) {
    if (that.trajectory.lastLine() == null) return null; 

    // need the -1 because when this==that, you will have an intersection
    // between the the last line of this (which is the 2nd last line of that)
    // and the last line of that, since they share a point.
    // in fact, need -2 when the player is standing still.
    int i = trajectory.subpath(0, nPoints()-excludePoints).intersects(that.trajectory.lastLine());
    if (i < 0) return null; // no intersection
    // Construct an NSPath for the loop (in reality, this is only used if this == that)
    NSPath loop = trajectory.subpath(i, nPoints());
    // Construct the times vector
    Vector<Integer> loopTimes = new Vector<Integer>();
    KnotGroup loopKnots = new KnotGroup();
    for (; i < nPoints(); i++) {
      loopTimes.add(times.get(i));
      if (knots.contains(i)) loopKnots.add(loopTimes.size()-1);
    }
  
    return new Tail(loop, loopTimes, loopKnots);
  }
  // ------ end functions that (clone and) modify tail -------- //

  @SuppressWarnings("unchecked")
  public Tail clone() {
    return new Tail(trajectory.clone(), 
        (Vector<Integer>)times.clone(),
        (KnotGroup) knots.clone());
  }
  
  public String toString() { return trajectory.toString(); }
   
  

  @SuppressWarnings("serial")
  public class KnotGroup extends TreeSet<Integer> {
      
    public KnotGroup() {
      super();
      add(0);
    }  

    public Integer firstNotBase() {
      return higher(0);
    }
  }
  
}
