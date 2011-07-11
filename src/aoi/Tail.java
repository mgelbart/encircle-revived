package aoi;

import java.awt.Point;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.util.Vector;

/** 
 * The tail.
 */
public class Tail {
  
  // Roughly the minimum distance between adjacent points in the trajectory for
  // encircling clusters. See the algorithm to see why this is only 'roughly' so.
  public static final int MINTRAJECTORYSEGMENT = SimulationUtils.ARENASIZE / 200;
  // ------------------------------------------------------------------------ //

  // The trajectory in the two space coordinates
  private NSPath trajectory;
  
  // The time coordinate of each point
  private Vector<Integer> times;
  
  public Tail(int x0, int y0, int time) {
    trajectory = new NSPath();
    trajectory.addPoint(x0, y0);
    times = new Vector<Integer>();
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
   * Does this Tail intersect with another Tail?
   * If so, return the loop that is formed as a Tail. If not, return null.
   */
  public Tail intersects(Tail that) {
    if (that.trajectory.nLines() == 0) return null; 
    
    for (int k=0; k < trajectory.nLines()-2; ++k) {
      // for every line in this tail, check if it intersects with the last
      // line of that tail
      if (that.trajectory.lastLine().intersectsLine(trajectory.getLine(k))) {

        // Construct an NSPath for the loop
        // In reality, this is only used if this == that
        NSPath loop = new NSPath();
        Vector<Integer> loopTimes = new Vector<Integer>();

        Point2D point = trajectory.getLine(k).getP2();
//        loop.moveTo(point.getX(), point.getY());
        loop.addPoint(point);
        loopTimes.add(times.elementAt(k+1));  // omg so messy
        // NSPath really needs refactoring to suck less

        for (int l=k+1; l < trajectory.nLines(); ++l) {
          point = trajectory.getLine(l).getP2();
//          loop.lineTo(point.getX(), point.getY());
          loop.addPoint(point);
          loopTimes.add(times.elementAt(l+1));
        }
//        return loop;
        return new Tail(loop, loopTimes);
      }
    }
    return null;
    
    
  }
  
  
  
  
  // Create a copy of the Tail with an added point.
  @SuppressWarnings("unchecked")
  public Tail addPoint(int x, int y, int time) {

    // trajectory and times for the new tail
    NSPath thatTrajectory = trajectory.clone();
    Vector<Integer> thatTimes = (Vector<Integer>) (times.clone());
    
    assert(thatTrajectory.nPoints() == trajectory.nPoints());
    assert(thatTimes.size() == times.size());
    
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

        assert(thatTimes.size() > 1);
        assert(thatTrajectory.nPoints() == thatTimes.size());
        
        thatTrajectory.removeLast();
        thatTimes.removeElementAt(thatTimes.size()-1);
        
      }

      thatTrajectory.addPoint(curPoint);
      thatTimes.add(time);
      
      //Logger.println(Logger.RepetitionRate.FREQUENT, thatTrajectory.nPoints() + " " + thatTimes.size());
      assert(thatTrajectory.nPoints() == thatTimes.size());
      
      return new Tail(thatTrajectory, thatTimes);
      
    } else {
      return this;
    }

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
        // If the point is not cropped, at it
        newPath.addPoint(trajectory.getPoint(i));
        newTimes.add(times.elementAt(i));
      }
    }

    return new Tail(newPath, newTimes);  
  }
  
  
  
}
