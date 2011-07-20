package aoi;

import java.awt.Point;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
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
  private NSPath trajectory;
  
  // The time coordinate of each point
  private Vector<Integer> times;
  
  private KnotGroup knots; 
  
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
  
  
  // --------- ---------- start small cute functions --------------- //
  public int nPoints() {
    assert(trajectory.nPoints() == times.size());
    return Math.random()<0.5?trajectory.nPoints():times.size();
  }
  
  public int     nKnots  ()      { return knots.size();           }
  public double  length  ()      { return trajectory.length();    }
  public Path2D  toPath2D()      { return trajectory.toPath2D();  }
  public boolean isEmpty ()      { return nPoints() == 0;         }
  public Point getPoint(int i)   { return trajectory.getPoint(i); }

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
  
  public Point2D headPoint() {
    assert(!isEmpty());
    return getPoint(nPoints()-1);
  }
  public Point2D basePoint() {
    assert(!isEmpty());
    return getPoint(0);
  }
  
  // The *time* of the last (closest to head) knot
  // This function contains the logic that
  // There is always a knot at the back
  public int lastKnotTime() {
    assert(!isEmpty());
    return times.get(knots.last());
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
  // the last knot to that time 
  // has at least the specified length
  // note that this does NOT output the time interval length from base to this point
  public int lengthFromBaseToTime(double length) {
    assert(nPoints() > 0);
    
    NSPath subpath = new NSPath();

    for (int i = 0; i < nPoints(); i++) {
      subpath.addPoint(getPoint(i)); 
      if (subpath.length() >= length)
        return times.get(i);
    }
    
    // what to do if the entire path is not long enough
    // must be very careful with what you return in this case
    // here we use lastElement()+1 so that if the whole path
    // is not long enough, you don't crop anything
    // when the output of this function is used as cropTailFrom
    return headTime() + 1;  
  }
  
  
  // the first (absolute) time at which the path from 
  // the last knot to that time 
  // has at least the specified length
  // note that this does NOT output the time interval length from base to this point
  public int lengthFromLastKnotToTime(double length) {
    assert(nPoints() > 0);
    
    NSPath subpath = new NSPath();

    for (int i = knots.last(); i < nPoints(); i++) {
      subpath.addPoint(getPoint(i)); 
      if (subpath.length() >= length)
        return times.get(i);
    }
    
    // what to do if the entire path is not long enough
    // must be very careful with what you return in this case
    // here we use lastElement()+1 so that if the whole path
    // is not long enough, you don't crop anything
    // when the output of this function is used as cropTailFrom
    return headTime() + 1;  
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
  public Angle tangentAngleBase() {
    return tangentAngle(1);
  }
  
  public void addKnotAtHead() {
    knots.add(nPoints()-1);
  }
  // ---------------------- end small cute functions --------------- //
  
  // ---------- start big mean complicated functions --------------- //
  // Create a copy of the Tail with an added point.
  @SuppressWarnings("unchecked")
  public Tail addPoint(Point curPoint, int time) {  
    if (isEmpty())
      return new Tail(curPoint, time);
    // (above): this might happen if for example you just shot
    // and now have absolutely no tail at all
    
    
    // trajectory and times for the new tail
    NSPath newTrajectory = trajectory.clone();
    Vector<Integer> newTimes = (Vector<Integer>) (times.clone());
    KnotGroup newKnots = (KnotGroup) knots.clone();
    
    assert(newTrajectory.nPoints() == trajectory.nPoints());
    assert(newTimes.size() == times.size());    
    assert(trajectory.nPoints() == times.size());
    
    Point2D prevPoint = trajectory.lastPoint();
    Point2D prevPrevPoint = trajectory.lastlastPoint();

    assert(prevPoint != null);
    
    // if the current point is not equal to the previous point
    // but == is sketchy for double so we use this
    if (prevPoint.distance(curPoint) > 0.9) {

      // Always add the latest position to the trajectory
      // but combine it with the previous segment if both are short.
      if (prevPrevPoint != null &&
            curPoint.distance(prevPrevPoint) < MINTRAJECTORYSEGMENT) {

        assert(newTimes.size() > 1);
        assert(newTrajectory.nPoints() == newTimes.size());
        
        newTrajectory.removeLast();
        newTimes.removeElementAt(newTimes.size()-1);
        
      }

      newTrajectory.addPoint(curPoint);
      newTimes.add(time);

      //Logger.println(Logger.RepetitionRate.FREQUENT, thatTrajectory.nPoints() + " " + thatTimes.size());
      assert(newTrajectory.nPoints() == newTimes.size());
      
      return new Tail(newTrajectory, newTimes, newKnots);
      
    } else {
      return this.clone();
    }

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
  // ------------ end big mean complicated functions --------------- //

  @SuppressWarnings("unchecked")
  public Tail clone() {
    return new Tail(trajectory.clone(), 
        (Vector<Integer>)times.clone(),
        (KnotGroup) knots.clone());
  }
  
  public String toString() { return trajectory.toString(); }
   
  
}
