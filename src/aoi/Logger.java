package aoi;

import java.io.*;

public abstract class Logger {

  public enum Rep {FEW, INFREQ, FREQ};
  
  // Where log messages are written
  private static PrintStream out = System.err;
  
  // Messages above this repetition rate are discarded
  private static Rep verbosity = Rep.INFREQ;
  
  // disables all logging at compile time
  private static final boolean DISABLE = false;
  
  
  public static void test() {
    println(Rep.FEW, true);
  }
  
  public static void println(Rep rate, Object msg) {
    if (DISABLE) return;
    
    if (rate.compareTo(verbosity) > 0) return;
    
    printHeader();
    
    out.println(msg);
  }
  
  private static void printHeader() {
    String classname, method;
    int line;
    
    StackTraceElement[] frames = Thread.currentThread().getStackTrace();
    
    if (frames.length > 3) {
      StackTraceElement from = frames[3];
      classname = from.getClassName();
      method = from.getMethodName();
      line = from.getLineNumber();
    } else {
      classname = "?";
      method = "?";
      line = -1;
    }
    
    out.print(System.currentTimeMillis() % 1000000);
    out.print(' ');
    out.print(classname);
    out.print('.');
    out.print(method);
    out.print(':');
    out.print(line);
    out.print(' ');
  }
  
}
