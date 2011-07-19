package aoi;

import java.awt.*;
import java.awt.geom.*;

/**
 *  A GUI element for a tail.
 */
public class DrawableTail extends DefaultDrawable {

  private final static int KNOTSIZE = (int) (SimulationUtils.ARENASIZE * 0.01);

  public DrawableTail(int playerNo, Stroke stroke) {
    super(playerNo, stroke);
  }


  public void prepare(GameState gState) {
    if (gState.pStates[playerNo].tail.isEmpty())
      nShapes = 0;
    else
      nShapes = 1+2*gState.pStates[playerNo].tail.nKnots();
  }
  
  // Return the path to be drawn.
  public Shape[] shapes(GameState gState) {
    if (nShapes == 0) return null;
    Tail tail = gState.pStates[playerNo].tail;
    Shape[] shapes = new Shape[nShapes];
    int j = 0;
    for (Knot k : tail.getKnots()) {
      if (tail != null) {
        Point2D knotPoint = tail.getKnotPoint(k);
        
        AffineTransform at = AffineTransform.getTranslateInstance(knotPoint.getX(), knotPoint.getY());
        
        Angle a = tail.tangentAngle(k.ind + 1);
        if (a == null) a = gState.pStates[playerNo].ship.dir();
        
        at.rotate(a.toDouble());
        
        shapes[j++] = at.createTransformedShape(
            new Line2D.Double(KNOTSIZE/2, -KNOTSIZE/2, -KNOTSIZE/2, KNOTSIZE/2));
        shapes[j++] = at.createTransformedShape(
            new Line2D.Double(KNOTSIZE/2, KNOTSIZE/2, -KNOTSIZE/2, -KNOTSIZE/2));
      } else {  // first step
        shapes[j++] = null;
        shapes[j++] = null;
      }
    }
    shapes[nShapes-1] = tail.toPath2D();
    return shapes;
  }

}
