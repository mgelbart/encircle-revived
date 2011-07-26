package aoi;

import java.io.*;
import java.util.Vector;

import aoi.Logger.Rep;

/**
 * This class defines constants and helper methods used
 * for the communicatio between the matchmaking server (M),
 * a game server (G) and a client (C).
 * 
 * All messages must be terminated by a newline (\n).
 */
public abstract class Protocol {

  /*
   * The client goes through the following state
   * during the matchmaking and game play stages.
   */
  public enum ClientState {
    REQUESTEDMATCH,
    READYFORMATCH,
    RECEIVEDPLAYERNO,
    RECEIVEDARENA,
    GAMESTARTED,
    GAMEENDED,
    REMATCHREQUESTPENDING
  }

  
  public static String readNextMsg(InputStream in) throws IOException {
    while (true) {
      int b;
      StringBuilder buf = new StringBuilder();
      do {
        b = in.read();
        buf.append((char) b);
      } while (b != '\n' && b != -1);
      
      assert(buf.length() > 0);
      buf.deleteCharAt(buf.length()-1);
      if (buf.length() > 0) return buf.toString();
    }
  }
  
  /*
   * General purpose messages.
   */
  private static final String ACK = "ack";
  public static String ack() { return ACK+"\n"; }
  public static boolean isAck(String msg) { return msg.matches("\\s*"+ACK+"\\s*"); }
  
  /*
   * Client to Matchmaking server:
   */
  private static final String REQUESTMATCH = "requestMatch";
  public static String requestMatch(String userid) { return REQUESTMATCH+" "+userid+"\n"; }
  public static String parseRequestMatch(String msg) { return parse1String(REQUESTMATCH, msg); }
  
  /*
   * Matchmaking server to Client:
   */
  private static final String PING = "ping";
  public static String ping() { return PING+"\n"; }
  public static boolean isPing(String msg) { return msg.matches("\\s*"+PING+"\\s*"); }
  
  private static final String MATCHMADE = "matchMade";
  public static String matchMade(long gameId) { return MATCHMADE+" "+gameId+"\n"; }
  public static Long parseMatchMade(String msg)  { return parse1Long(MATCHMADE, msg); }
  
  /*
   * Client to Game server:
   */
  private static final String READYFORMATCH = "readyForMatch";
  public static String readyForMatch(long gameId) { return READYFORMATCH+" "+gameId+"\n"; }
  public static Long parseReadyForMatch(String msg)  { return parse1Long(READYFORMATCH, msg); }
  
  private static final String REMATCHREQUEST = "rematchRequest";
  public static String requestRematch(long gameId) { return REMATCHREQUEST+" "+gameId+"\n"; }
  public static Long parseRequestRematch(String msg)  { return parse1Long(REMATCHREQUEST, msg); }
  
  /*
   * Game server to Client:
   */
  private static final String MATCHSTARTING = "matchStarting";
  public static String matchStarting(long gameId) { return MATCHSTARTING+" "+gameId+"\n"; }
  public static Long parseMatchStarting(String msg)  { return parse1Long(MATCHSTARTING, msg); }
  
  private static final String MATCHENDED = "matchEnded";
  public static String matchEnded(long gameId) { return MATCHENDED+" "+gameId+"\n"; }
  public static Long parseMatchEnded(String msg)  { return parse1Long(MATCHENDED, msg); }
  
  private static final String OTHERPLAYERREQUESTEDREMATCH = "otherPlayerRequestedRematch";
  public static String otherPlayerRequestedRematch(long gameId) { return OTHERPLAYERREQUESTEDREMATCH+" "+gameId+"\n"; }
  public static Long parseOtherPlayerRequestedRematch(String msg)  { return parse1Long(OTHERPLAYERREQUESTEDREMATCH, msg); }
  
  /*
   * Matchmaking server to Game server:
   */
  private static final String POLL = "poll";
  public static String poll() { return POLL+"\n"; }
  public static boolean isPoll(String msg) { return msg.matches("\\s*"+POLL+"\\s*"); }
  
  /*
   * Game server to Matchmaking server:
   */
  private static final String MATCHCOUNT = "matchCount";
  public static String matches(int n) { return MATCHCOUNT+" "+n+"\n";}
  public static Long parseMatchCount(String msg)  { return parse1Long(MATCHCOUNT, msg); }

  
  // parse a request with one long argument.
  private static Long parse1Long(String cmd, String msg) {
    try { return Long.parseLong(parse1String(cmd, msg)); }
    catch (NumberFormatException e) { return null; }
  }
  
  // parse a request with one long argument.
  private static String parse1String(String cmd, String msg) {
    // parts[0] should be the command and parts[1] the first arg
    String[] parts = msg.split("[^-\\w]+", 3);
    if (parts.length < 2) return null;
    if (!parts[0].equals(cmd)) return null;
    if (parts[1].isEmpty()) return null;
    
    return parts[1];
  }
  
  
  // for testing
  public static void main(String argv[]) throws Exception {
    assert(isAck(ack()));
    assert(isAck(" "+ack()+"   "));
    assert(isPing(ping()));
    assert(isPing(" "+ping()+"   "));
    assert("foo".equals(parseRequestMatch(requestMatch("foo"))));
    assert(null == parseRequestMatch(requestMatch("")));
    assert(0 == parseMatchMade(matchMade(0)));
    assert(7 == parseMatchMade(matchMade(7)));
    assert(123 == parseMatchMade(matchMade(123)));
    assert(-123 == parseMatchMade(matchMade(-123)));
    
    Logger.println(Rep.FEW, "tests done.");
  }
}
