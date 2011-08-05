package aoi;

import java.awt.Point;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

import aoi.Logger.Rep;

/** 
 * The tail. Immutable. Not allowed to be empty, so 
 * I put assert(!isEmpty()) everywhere.
 */
public class Tail {
  
  // Roughly the minimum distance between adjacent points in the trajectory for
  // encircling clusters. See the addPoint() to see why this is only 'roughly' so.
  public static final int MINTRAJECTORYSEGMENT = SimulationUtils.ARENASIZE / 200;
  // ------------------------------------------------------------------------ //

  // A map from times to Points. Each key-value pair
  // in this map corresponds to a "3D point" in the
  // trajectory, the 3rd dimension being time.
  private final TreeMap<Integer, Point> trajectory;
  
  private final TreeSet<Integer> knots; 
  
  // ---------------------- start constructors ----------------------- //
  public Tail(int time, Point p) {
    trajectory = new TreeMap<Integer, Point>();
    knots = new TreeSet<Integer>();    
    trajectory.put(time, p);
    
    knots.add(baseTime());
    
    assert(isValid());
  }
  private Tail(TreeMap<Integer, Point> trajectory, TreeSet<Integer> knots) {
    this.trajectory = trajectory;
    this.knots = knots;

    knots.add(baseTime());

    assert(isValid());
  }
  // ---------------------- end constructors ----------------------- //  

  public boolean equals(Tail client) {
    boolean toReturn = true;
    Tail server = this;
    
    assert(server.isValid());
    assert(client.isValid());
    
    if (!server.knots.equals(client.knots)) {
      if (Simulation.VERIFYSERIALIZATION)
        Logger.println(Logger.Rep.INFREQ, "KnotGroup does not agree");
      toReturn = false;
    }
    if (!server.trajectory.equals(client.trajectory)) {
      if (Simulation.VERIFYSERIALIZATION)
        Logger.println(Logger.Rep.INFREQ, "trajectories (times) do not agree");
      toReturn = false;
    }
    
    return toReturn;  
  }
  
  
  // ------------------- start access functions --------------- //
  public int nPoints() { 
    return trajectory.size();
  }
  public Point getPoint(int time) {
    assert(trajectory.containsKey(time));
    return new Point(trajectory.get(time)); 
  }
  
  @SuppressWarnings("unchecked")
  public TreeSet<Integer> getKnotTimes() { 
    assert(isValid());
    return (TreeSet<Integer>)knots.clone();
    //TreeSet<Integer> toReturn = (TreeSet<Integer>)knots.clone();
    //toReturn.add(baseTime());
    //return toReturn;
  }
  public int lastKnotTime() {
    return getKnotTimes().last();
  }

  // the time of the first (closest to base)
  // knot NOT INCLUDING THE KNOT AT BASE
  // Returns the head time if the knot
  // at the base is the only knot
  public int cropToForCrossing() {
    assert(isValid());
    if (knots.size()==1) return headTime(); 
    // (above) only knot at bases crop everything except the head    
    return knots.higher(knots.first());
    // (above) crop everything including this knot (second from base)
  }

  public Integer headTime() {
    return new Integer(trajectory.lastKey());
  }
  public Integer baseTime() {
    return new Integer(trajectory.firstKey());
  }
  public Integer beforeHeadTime() {
    if (nPoints() == 1) return null;
    return new Integer(trajectory.lowerKey(headTime()));
  }
  public Integer afterBaseTime() {
    if (nPoints() == 1) return null;
    return new Integer(trajectory.higherKey(baseTime()));
  }
  public Point headPoint() {
    return new Point(trajectory.get(headTime()));
  }
  public Point basePoint() {
    return new Point(trajectory.get(baseTime()));
  }
  public Point beforeHeadPoint() {
    if (nPoints() == 1) return null;
    return new Point(trajectory.get(beforeHeadTime()));
  }
  public Point afterBasePoint() {
    if (nPoints() == 1) return null;
    return new Point(trajectory.get(afterBaseTime())); 
  }
  
  // The Path2D returned here is not closed by default
  public Path2D toPath2D() { 
    return toPath2D(trajectory.values());
  }
  private static Path2D toPath2D(Iterable<Point> points) {
    Path2D path = new Path2D.Double();
    
    int i = 0;
    for (Point p : points) {
      if (i++ == 0) path.moveTo(p.x, p.y);
      else path.lineTo(p.x, p.y);
    }
    return path;  
  }

  public double length() {
    double length = 0;
    for (int time : trajectory.headMap(headTime()).keySet())
      length += trajectory.get(time).distance(trajectory.get(trajectory.higherKey(time))); 
    return length;
  }
  
  // the first time at which the path from 
  // "startTime" to that time has at least the specified length
  public Integer convertLengthToTime(int startTime, double minLength, boolean forwardDir) {
    assert(isValid());
    if (trajectory.get(startTime) == null) return null;
    
    if (minLength <= 0) return startTime;
    if (nPoints() == 1) return startTime; // here startTime == baseTime == headTime
    // (above) needed to avoid bug in which the loop below
    // will call higher/lowerKey and hit null pointer when
    // tail has only 1 point
    
    // The order and details of these three statements above is important
    // and bugs will come if they are not very carefully checked.
    
    double length = 0;
    int time = startTime;
    Integer nextTime = forwardDir?trajectory.higherKey(startTime):trajectory.lowerKey(startTime);
    while (nextTime != null) {
      length += trajectory.get(time).distance(trajectory.get(nextTime));
      if (length >= minLength)
        return new Integer(nextTime);
      time = nextTime;
      nextTime = forwardDir?trajectory.higherKey(time):trajectory.lowerKey(time);
    }
    // what to do if the entire path is not long enough
    return null; 
  }
   
//   Returns the length from the head to the first time
//   (counting from the head) that is less than or equal to
//   desiredTime
  public double convertTimeToLengthStartingAtHead(int desiredTime) {
    assert(isValid());
    
    if (desiredTime >= headTime()) return 0.;
    if (desiredTime <= baseTime()) return length();
    
    double length = 0;
    int nextTime;
    for (int time = headTime(); time > desiredTime; time = nextTime) {
      nextTime = trajectory.lowerKey(time);
      length += trajectory.get(time).distance(trajectory.get(nextTime));
    }
    return length;
  }

  // Returns the angle between the previous point before the point at "time"
  // and the point at time
  // Special case, if time == basetime(), then it returns the angle from base point to the next one
  public Angle tangentAngle(int time) {
    assert(isValid());
    if (nPoints() == 1) return null;
    if (time - baseTime() < 0 || time - headTime() > 0) return null;
    
    if (time == headTime()) return new Angle(beforeHeadPoint(), headPoint());

    return new Angle(trajectory.get(time), trajectory.get(trajectory.higherKey(time)));
  }
  public Angle tangentAngleHead() {
    return tangentAngle(headTime());
  }  

  // ------------- end access functions --------------- //
  @SuppressWarnings("unchecked")
  public Tail addPoint(int time, Point p) {  
    assert(isValid());
    
    TreeMap<Integer, Point> newTrajectory = (TreeMap<Integer, Point>) trajectory.clone();
    TreeSet<Integer> newKnots = (TreeSet<Integer>) knots.clone();
    
    // Always add the latest position to the trajectory
    // but combine it with the previous segment if both are short.
    // we need the second part after "||" for a special case when there
    // is exactly one point in the tail and you try to add
    // a point at the same location
    if ((beforeHeadPoint() != null && p.distance(beforeHeadPoint()) < MINTRAJECTORYSEGMENT)
           || (headPoint() != null && p.equals(headPoint()))) {
      if (newKnots.contains(headTime())) {
        newKnots.remove(headTime());
        newKnots.add(time);
      }
      newTrajectory.remove(headTime());
    }
    newTrajectory.put(time, p);
    
    return new Tail(newTrajectory, newKnots);
  }

  // crop the tail using all the CropEvents in
  // the CropEventGroup.
  // Does not allow the returned tail to be empty!
  // so, if the CropEventGroup tries to crop
  // the entire tail, this function keeps a point at the head
  private Tail crop(CropEventGroup ceg) {
    assert(isValid());  
    //if (ceg.isEmpty()) return clone(); 
    // This should never be called on an empty ceg anyway.

    TreeMap<Integer, Point> newTrajectory = new TreeMap<Integer, Point>();
    TreeSet<Integer> newKnots = new TreeSet<Integer>();
    
    for (int time : trajectory.keySet()) {
      if (!ceg.contains(time)) {
        newTrajectory.put(time, trajectory.get(time));
        if (knots.contains(time)) newKnots.add(time);
      }
    }
    
    // Don't allow the new tail to be empty
    if (newTrajectory.isEmpty())
      newTrajectory.put(headTime(), headPoint());
    
    return new Tail(newTrajectory, newKnots);
  }
  
  private Tail crop(CropEvent ce) {
    return crop((new CropEventGroup()).addEvent(ce));
  }
  public Tail crop(int cropFrom, int cropTo) {
    return crop(new CropEvent(cropFrom, cropTo,
        cropTo,  // <-- this doesn't matter either
        "DoesntMatter"));
  }
  // (above) this is a bit slow, but only used by ScoringEffect
  
  
  // Add p, then crop with ceg. Do this all at once
  // To avoid cloning twice per turn in PlayerState parsing
  // This works even if prev.tail is empty
  public static Tail parse(PlayerState prev, Point p, int time, 
      CropEventGroup cropEvents, int sinceScoring) {
    if (prev == null)
      return new Tail(time, p);

    // Add the latest point (this now copies the Tail)
    Tail newTail = prev.tail.addPoint(time, p);

    // Note: we use this instead of checking if the score is 
    // incremented because of a bug with scoring and getting
    // pwned on the same turn
    if ((sinceScoring - prev.sinceScoring < 0 ||
        sinceScoring == 0))
      newTail.knots.add(newTail.headTime());
    // Don't want to use prev.tail.headTime()
	// because newTail might not contain this time anymore,
	// since addPoint might have removed that latest point.
	// The alternative would be to add the knot before adding
	// the point, but that's not good because addPoint copies
	// the Tail and thus should be the first command in this
	// function. Therefore this is the best thing to do.

    
    // Execute all the pending crop events
    // We use hasEvents() which only returns true
    // once on the server side, but many times on
    // the client side. This is a little tricky but it allows
    // the server to not crop things many times.
    // We save time by not copying the Tail again (it's already copied
    // by addPoint()) if there are no pending CropEvents.
    if (cropEvents.hasEvents(time))
      newTail = newTail.crop(cropEvents);  // this copies the Tail
	
  
    // The order here is for a reason. A weird bug can occur crop() is suppsed
    // to crop everything and thus is forced to add an extra point to prevent
    // the tail from being empty. If it does this, it can add a point at game.time
    // and then this will get cropped by the CLIENT at the next step but never by
    // the server since the server only executes each cropEvent once. (This bug
    // (happens when a CropEvent has cropTo exactly equal to timeCreated). But if we 
    // do it this way and call newTail.addPoint BEFORE newTail.crop(), then even
    // if the entire tail gets cropped, the dummy point will get added at headTime
    // which is now NEWTIME (i.e. game.time + 1, not game.time). Then the client
    // will not crop this thing later on, so long as the cropEvents are well-behaved
    // and don't do stupid things like have cropTo strictly greater than timeCreated.
    
	assert(newTail.isValid());
    return newTail;
  }
  
  


  /**
   * Does this Tail intersect with *the last line of* another Tail?
   * If so, return the loop that is formed as a Tail. If not, return null.
   *                      
   * When "this" != "that" and there is an intersection, the content
   * of the returned Tail is undefined but it will not be null.                    
   */
  public Tail intersects(Tail that) {
    assert(this.isValid());
    assert(that.isValid());
    
    // The excludePoints is necessary to avoid spurious two segment loops
    // for example when "this" == "that" and the player is not moving                       
    int excludePoints = this == that?2:0;
    
    if (this.nPoints() < 2+excludePoints) return null;
    if (that.nPoints() < 2) return null; 

    
    int lastTime;
    for (lastTime = headTime(); excludePoints-- > 0; lastTime = trajectory.lowerKey(lastTime));
    
    // Check if trajectory intersects the last line of that.trajectory
    // Excluding the last excludePoints of trajectory
    Point that1 = that.beforeHeadPoint();
    Point that2 = that.headPoint();
    Integer intersectionTime = null;
    for (int time : trajectory.headMap(lastTime).keySet()) {  // excludes lastTime
      Point this1 = trajectory.get(time);
      Point this2 = trajectory.get(trajectory.higherKey(time));
      if (Line2D.linesIntersect(
          this1.x, this1.y, this2.x, this2.y, 
          that1.x, that1.y, that2.x, that2.y)) {
        intersectionTime = time;
        break;
      }
    }
    if (intersectionTime == null) return null; // no intersection

//    Logger.println(Rep.INFREQ, this);
//    Logger.println(Rep.INFREQ, that);
//    Logger.println(Rep.INFREQ, lastTime);
//    Logger.println(Rep.INFREQ, "intersectiontime = " + intersectionTime);

    
    // Include the point right before crossing
    // in the loop, as well as the point right after crossing
    return crop(baseTime(), intersectionTime);
  }

  

  @SuppressWarnings("unchecked")
  public Tail clone() {
    return new Tail((TreeMap<Integer,Point>)trajectory.clone(), 
        (TreeSet<Integer>) knots.clone());
  }
  
  public String toString() { 
    return trajectory + "\n" +
           knots;
  }
  
  public boolean isValid() {
    if (trajectory.isEmpty()) {
	  Logger.println(Rep.INFREQ, this);
      Logger.println(Rep.INFREQ, "Tail is empty");
      return false;
    }
    if (knots.isEmpty()) {
	  Logger.println(Rep.INFREQ, this);
      Logger.println(Rep.INFREQ, "No knot at base");
      return false;
    }
    if (!knots.first().equals(baseTime())) {
	  Logger.println(Rep.INFREQ, this);
      Logger.println(Rep.INFREQ, "First knot not at base");
      Logger.println(Rep.INFREQ, "First knot at " + knots.first());
      Logger.println(Rep.INFREQ, "Basetime = " + baseTime());
      return false;
    }
    for (int knotTime : knots) {
      if (!trajectory.containsKey(knotTime)) {
	    Logger.println(Rep.INFREQ, this);
        Logger.println(Rep.INFREQ, "Knot at " + knotTime + " not in tail");
        return false;
      }
    }
    
    return true;
  }

  
}
