package aoi;

import java.io.*;

public abstract class Logger {

  public enum RepetitionRate {FEW, INFREQUENT, FREQUENT};
  
  // Where log messages are written
  private static PrintStream out = System.err;
  
  // Messages above this repetition rate are discarded
  private static RepetitionRate verbosity = RepetitionRate.INFREQUENT;
  
  // disables all logging at compile time
  private static final boolean DISABLE = false;
  
  
  public static void println(RepetitionRate rate, String msg) {
    if (DISABLE) return;
    
    if (rate.compareTo(verbosity) > 0) return;
    
    printHeader();
    
    out.println(msg);
  }
  
  public static void println(RepetitionRate rate, int msg) {
    if (DISABLE) return;
    
    if (rate.compareTo(verbosity) > 0) return;
    
    printHeader();
    
    out.println(msg);
  }
  
  public static void println(RepetitionRate rate, double msg) {
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
    
    out.print(classname);
    out.print('.');
    out.print(method);
    out.print(':');
    out.print(line);
    out.print(' ');
  }
  
}
