package aoi;

import java.awt.*;
import java.awt.font.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;

/**
 * A simple GUI element that displays the current frames-per-second rate.
 *
 */
public class DrawableFPS extends DefaultDrawable {

  int count = -1; // number of frames drawn since startTime was set
  long startTime = 0;
  
  double fps = 0.;
  
  // helper variables needed to generate the text shapes
  final FontRenderContext context;
  final Font font;
  final AffineTransform at;
  Shape untransformedText = null;
  
  protected DrawableFPS(Stroke stroke) {
    super(0, stroke);
    paint = Color.GRAY;
    fill = true;

    font = new Font("Monospaced", Font.PLAIN, 10);
    
    // This is a bit hackz but we need to create an
    // actual graphics object in order to get the FontMetrics object.
    final Graphics2D graphics = (new BufferedImage(1, 1,
        BufferedImage.TYPE_INT_ARGB))
.createGraphics();
    context = graphics.getFontMetrics(font).getFontRenderContext();
    
    GlyphVector text = font.createGlyphVector(context, ""+fps);
    final double textHeight = text.getOutline().getBounds2D().getHeight();
    final double scaleFact = 10. / textHeight;  // make the letters 10 px high
    
    // Precalculate everything except the translation for the coordinate transformation.
    // The amount of translation depends on the canvas size which may change.
    at = new AffineTransform();
    at.scale(scaleFact, scaleFact);
    at.scale(1, -1); // Java Graphics use a left-handed coordinate system.
  }

  public void prepare(GameState gState) {
    if (count == -1) startTime = System.currentTimeMillis();
    ++count;
    
    final int FRAMESTOCOUNT = 25;
    if (count == FRAMESTOCOUNT) {
      fps = FRAMESTOCOUNT * 1000 / (System.currentTimeMillis()-startTime);
      count = -1;

      assert(context != null);
      untransformedText = font.createGlyphVector(context, ""+fps).getOutline();
      assert(untransformedText != null);
    }
  }

  public Shape[] shapes(GameState gState) {
    if (untransformedText == null) return new Shape[0];
    return new Shape[] { toOverlayCoords().createTransformedShape(untransformedText) };
  }

  public boolean ingameCoordinates() { return false; }
  
  
  private AffineTransform toOverlayCoords() {
    AffineTransform t =  AffineTransform.getTranslateInstance(DrawUtils.canvasWidth()/2 - 80, -DrawUtils.canvasHeight()/2 + 20); // Move to the top
    t.concatenate(at);
    return t;
  }
}
