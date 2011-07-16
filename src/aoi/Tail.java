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

  
  // This data should actually be best implemented as a 
  // Map<Integer, Point>
  // this totally undermines NSPath though...
  
  // The trajectory in the two space coordinates
  private NSPath trajectory;
  
  // The time coordinate of each point
  private Vector<Integer> times;
  
  // The indices of the knots
  // so for example getPoint(knotInds.get(1)) gives the
  // location of knot 1
  private Vector<Integer> knotInds;
  // Doesn't explicity store the base as a knot, 
  // (this would amount to just having 0 always be in knotInds)
  // but this is implied in that the lastKnot() function
  // returns baseTime() if no other knots exist
  
  // ---------------------- start constructors ----------------------- //
  public Tail() {
    trajectory = new NSPath();
    times = new Vector<Integer>();
    knotInds = new Vector<Integer>();
  }
  public Tail(Point2D p, int time) {
    this();
    trajectory.addPoint(p);
    times.add(time);
    assert(trajectory.nPoints() == times.size());
  }
  private Tail(NSPath trajectory, Vector<Integer> times, Vector<Integer> knotInds) {
    this.trajectory = trajectory;
    this.times = times;
    this.knotInds = knotInds;
    assert(trajectory.nPoints() == times.size());
  }
  // ---------------------- end constructors ----------------------- //  
  // --------- ---------- start small cute functions --------------- //
  public int nPoints() {
    assert(trajectory.nPoints() == times.size());
    return Math.random()<0.5?trajectory.nPoints():times.size();
  }
  
  public int     nKnots  ()      { return knotInds.size();        }
  public double  length  ()      { return trajectory.length();    }
  public Path2D  toPath2D()      { return trajectory.toPath2D();  }
  public boolean isEmpty ()      { return nPoints() == 0;         }
  public int     getTime (int i) { return times.elementAt(i);     }
  public Point2D getPoint(int i) { return trajectory.getPoint(i); }
  
  public Point2D getPointFromTime(int time) {
    for (int i = 0; i < nPoints(); i++)
      if (getTime(i) == time)
        return getPoint(i);
    return null;
  }
  
  // The time of the head, i.e. the last time. Kind of confusing naming, sorry.
  public int headTime() {
    assert(!isEmpty());
    return getTime(nPoints()-1);
  }
  public int baseTime() {
    assert(!isEmpty());
    return getTime(0);
  }
  
  // The *time* of the last (closest to head) knot
  public int lastKnot() {
    assert(!isEmpty());
    if (nKnots() == 0) return baseTime();
    else return getTime(knotInds.lastElement());
  }
  
  // the first (absolute) time at which the path from base to that time 
  // has at least the specified length
  // note that this does NOT output the time interval length from base to this point
  public int lengthToTime(double length) {
    assert(nPoints() > 0);
    
    NSPath subpath = new NSPath();

    for (int i = 0; i < nPoints(); i++) {
      subpath.addPoint(getPoint(i)); 
      if (subpath.length() >= length)
        return getTime(i);
    }
    
    // what to do if the entire path is not long enough
    // must be very careful with what you return in this case
    // here we use lastElement()+1 so that if the whole path
    // is not long enough, you don't crop anything
    // when the output of this function is used as cropTailFrom
    return headTime() + 1;  
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
  // ---------------------- end small cute functions --------------- //
  
  // ---------- start big mean complicated functions --------------- //
  public Tail addPoint(Point p, int time, boolean isKnot) {
    return addPoint(p.x, p.y, time, isKnot);
  }
  // Create a copy of the Tail with an added point.
  @SuppressWarnings("unchecked")
  public Tail addPoint(int x, int y, int time, boolean isKnot) {  
    // trajectory and times for the new tail
    NSPath newTrajectory = trajectory.clone();
    Vector<Integer> newTimes = (Vector<Integer>) (times.clone());
    Vector<Integer> newKnotInds = (Vector<Integer>) (knotInds.clone());
    
    assert(newTrajectory.nPoints() == trajectory.nPoints());
    assert(newTimes.size() == times.size());    
    assert(trajectory.nPoints() == times.size());
    
   
    // special case: empty
    if (isEmpty()) {
      newTrajectory.addPoint(x, y);
      newTimes.add(time);
      if (isKnot) newKnotInds.add(newTrajectory.nPoints());
      return new Tail(newTrajectory, newTimes, newKnotInds);
    }
    

    Point2D curPoint = new Point(x, y);
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
      if (isKnot) newKnotInds.add(newTrajectory.nPoints());
      
      //Logger.println(Logger.RepetitionRate.FREQUENT, thatTrajectory.nPoints() + " " + thatTimes.size());
      assert(newTrajectory.nPoints() == newTimes.size());
      
      return new Tail(newTrajectory, newTimes, newKnotInds);
      
    } else {
      return this;
    }

  }
  
  
  // Crops part of a tail starting at the beginning and until it has cropped
  // length units off of the tail. if cropping length exactly is not reasonable
  // it will crop slightly more.
  public Tail crop(double length) {
    // Fields for new Tail
    NSPath newPath = new NSPath();
    Vector<Integer> newTimes = new Vector<Integer>();
    Vector<Integer> newKnotInds = new Vector<Integer>();

    // just for keeping track of the length
    NSPath croppedPath = new NSPath();
    
    int i;
    for (i = 0; i < nPoints(); i++) {
      croppedPath.addPoint(getPoint(i)); 
      if (croppedPath.length() >= length)
        break;
    }
    for (; i < nPoints(); i++) {
      newPath.addPoint(getPoint(i));
      newTimes.add(getTime(i));
      if (knotInds.contains(i)) newKnotInds.add(i);
    }

    return new Tail(newPath, newTimes, newKnotInds);  
  }

  // Crops part of a tail given the times "from" and "to".
  // Removes "from" but not "to"
  public Tail crop(int from, int to) {
   
    // Fields for new Tail
    NSPath newPath = new NSPath();
    Vector<Integer> newTimes = new Vector<Integer>();
    Vector<Integer> newKnotInds = new Vector<Integer>();
    
    for (int i = 0; i < nPoints(); i++) {
      if (getTime(i) - from < 0 || getTime(i) - to >= 0) {
        // If the point is not cropped, add it
        newPath.addPoint(getPoint(i));
        newTimes.add(getTime(i));
        if (knotInds.contains(i)) newKnotInds.add(i);
      }
    }

    return new Tail(newPath, newTimes, newKnotInds);
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
    int k = trajectory.subpath(0, nPoints()-excludePoints).intersects(that.trajectory.lastLine());
    if (k < 0) return null; // no intersection
    // Construct an NSPath for the loop (in reality, this is only used if this == that)
    NSPath loop = trajectory.subpath(k, nPoints());
    // Construct the times vector
    Vector<Integer> loopTimes = new Vector<Integer>();
    Vector<Integer> loopKnotInds = new Vector<Integer>();
    for (; k < nPoints(); k++) {
      loopTimes.add(getTime(k));
      if (knotInds.contains(k)) loopKnotInds.add(k);
    }
  
    return new Tail(loop, loopTimes, loopKnotInds);
  }
  // ------------ end big mean complicated functions --------------- //

  @SuppressWarnings("unchecked")
  public Tail clone() {
    return new Tail(trajectory.clone(), 
        (Vector<Integer>)times.clone(),
        (Vector<Integer>)knotInds.clone());
  }
  
  public String toString() { return trajectory.toString(); }
  
}
