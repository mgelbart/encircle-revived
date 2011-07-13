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
  
  private int nPoints() { return trajectory.nPoints(); }
  
  public int startTime() {
    if (times.isEmpty()) return -1;
    return times.firstElement();
  }
  
  /**
   * Does this Tail intersect with *the last line of* another Tail?
   * If so, return the loop that is formed as a Tail. If not, return null.
   */
  public Tail intersects(Tail that) {
    if (that.trajectory.lastLine() == null) return null; 
        
    // need the -1 because when this==that, you will have an intersection
    // between the the last line of this (which is the 2nd last line of that)
    // and the last line of that, since they share a point
    int k = trajectory.subpath(0, trajectory.nPoints()-1).intersects(that.trajectory.lastLine());
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
    
    // if the new point is sufficiently far from the current such that
    // you even care enough to do something about it
    // oh, because of integer resolution
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
  
  
  // the first (absolute) time at which the path from start to that time 
  // has at least the specified length
  // note that this does NOT output the time interval length from start to this point
  public int lengthToTime(double length) {
    assert(nPoints() == times.size());
    
    NSPath subpath = new NSPath();

    for (int i = 0; i < nPoints(); i++) {
      subpath.addPoint(trajectory.getPoint(i)); 
      if (subpath.length() >= length)
        return times.elementAt(i);
    }
    return times.elementAt(0);  // entire path is not long enough
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
  
  
  
}
