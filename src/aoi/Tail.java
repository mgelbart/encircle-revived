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
  
  public Tail() {
    trajectory = new NSPath();
    times = new Vector<Integer>();
  }
  
  public Tail(int x0, int y0, int time) {
    this();
    trajectory.addPoint(x0, y0);
    times.add(time); 
    assert(trajectory.nPoints() == times.size());
  }
  
  private Tail(NSPath trajectory, Vector<Integer> times) {
    this.trajectory = trajectory;
    this.times = times;
    assert(trajectory.nPoints() == times.size());
  }
    
  public double pathLength() { return trajectory.length(); }
  
  public Path2D toPath2D() { return trajectory.toPath2D(); }
  
  public int nPoints() { return trajectory.nPoints(); }
  
  public boolean isEmpty() { return nPoints() == 0; }
  
  public int startTime() {
    if (times.isEmpty()) return -1;
    return times.firstElement();
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
    int k = trajectory.subpath(0, trajectory.nPoints()-excludePoints).intersects(that.trajectory.lastLine());
    if (k < 0) return null; // no intersection
    // Construct an NSPath for the loop (in reality, this is only used if this == that)
    NSPath loop = trajectory.subpath(k, nPoints());
    // Construct the times vector
    Vector<Integer> loopTimes = new Vector<Integer>();
    for (; k < nPoints(); k++)
      loopTimes.add(times.get(k));
  
    return new Tail(loop, loopTimes);
  }

  @SuppressWarnings("unchecked")
  public Tail clone() {
    return new Tail(trajectory.clone(), (Vector<Integer>)times.clone());
  }
  
  
  public Point2D getPointFromTime(int time) {
    for (int i = 0; i < nPoints(); i++)
      if (times.elementAt(i) == time)
        return trajectory.getPoint(i);
    return null;
  }
  
    
  // Finds the angle of the line from i-1 to i
  public Angle tangentAngle(int i) {
    if (i <= 0 || i >= nPoints() || nPoints() < 2) return null;
    return new Angle(trajectory.getPoint(i-1), trajectory.getPoint(i)); 
  }
  public Angle tangentAngleHead() {
    return tangentAngle(nPoints()-1);
  }
  public Angle tangentAngleBase() {
    return tangentAngle(1);
  }
  
  
  
  // Create a copy of the Tail with an added point.
  @SuppressWarnings("unchecked")
  public Tail addPoint(int x, int y, int time) {  
    // trajectory and times for the new tail
    NSPath newTrajectory = trajectory.clone();
    Vector<Integer> newTimes = (Vector<Integer>) (times.clone());
    
    assert(newTrajectory.nPoints() == trajectory.nPoints());
    assert(newTimes.size() == times.size());
    
    assert(trajectory.nPoints() == times.size());
    
    
    // special case: empty
    if (trajectory.isEmpty()) {
      newTrajectory.addPoint(x, y);
      newTimes.add(time);
      return new Tail(newTrajectory, newTimes);
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
      
      //Logger.println(Logger.RepetitionRate.FREQUENT, thatTrajectory.nPoints() + " " + thatTimes.size());
      assert(newTrajectory.nPoints() == newTimes.size());
      
      return new Tail(newTrajectory, newTimes);
      
    } else {
      return this;
    }

  }
  
  
  // Crops part of a tail starting at the beginning and until it has cropped
  // length units off of the tail. if cropping length exactly is not reasonable
  // it will crop slightly more.
  public Tail crop(double length) {
    assert(nPoints() == times.size());
    
    // Fields for new Tail
    NSPath newPath = new NSPath();
    NSPath croppedPath = new NSPath();
    Vector<Integer> newTimes = new Vector<Integer>();

    int i;
    for (i = 0; i < nPoints(); i++) {
      croppedPath.addPoint(trajectory.getPoint(i)); 
      if (croppedPath.length() >= length)
        break;
    }
    for (; i < nPoints(); i++) {
      newPath.addPoint(trajectory.getPoint(i));
      newTimes.add(times.elementAt(i));
    }

    return new Tail(newPath, newTimes);  
  }
  
  // The time of the head, i.e. the last time. Kind of confusing naming, sorry.
  public int headTime() {
    return times.lastElement();
  }
  
  // the first (absolute) time at which the path from base to that time 
  // has at least the specified length
  // note that this does NOT output the time interval length from base to this point
  public int lengthToTime(double length) {
    assert(nPoints() == times.size());
    assert(nPoints() > 0);
    
    NSPath subpath = new NSPath();

    for (int i = 0; i < nPoints(); i++) {
      subpath.addPoint(trajectory.getPoint(i)); 
      if (subpath.length() >= length)
        return times.elementAt(i);
    }
    
    // what to do if the entire path is not long enough
    // must be very careful with what you return in this case
    // here we use lastElement()+1 so that if the whole path
    // is not long enough, you don't crop anything
    // when the output of this function is used as cropTailFrom
    return times.lastElement() + 1;  
  }
  

  // Crops part of a tail given the times "from" and "to".
  // Removes "from" but not "to"
  public Tail crop(int from, int to) {
    assert(nPoints() == times.size());
    
    // Fields for new Tail
    NSPath newPath = new NSPath();
    Vector<Integer> newTimes = new Vector<Integer>();

    for (int i = 0; i < nPoints(); i++) {
      if (times.elementAt(i) - from < 0 || times.elementAt(i) - to >= 0) {
        // If the point is not cropped, add it
        newPath.addPoint(trajectory.getPoint(i));
        newTimes.add(times.elementAt(i));
      }
    }

    return new Tail(newPath, newTimes);  
  }
  
  public String toString() {
    return trajectory.toString();
  }
  
}
