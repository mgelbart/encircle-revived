package aoi;

import java.io.*;

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
    GAMESTARTED,
    RECEIVEDARENA,
    GAMEENDED,
    REMATCHREQUESTPENDING
  }

  /**
   * Reads the next line from the stream.
   * 
   * @param in the stream to read from
   * @return the string up to but excluding the next '\n'.
   *          null if the stream was closed gracefully.
   * @throws IOException if reading from the stream fails
   */
  public static String readNextMsg(InputStream in) throws IOException {
    while (true) {
      int b = -2;
      StringBuilder buf = new StringBuilder();
      boolean end = false;
      while (!end) {
        b = in.read();
        end = (b == '\n' || b == -1);
        if (!end) buf.append((char) b);
      }

      if (buf.length() > 0) return buf.toString();
      if (b == -1) return null;
    }
  }
    
  /*
   * General purpose messages
   */
  private static final String ACK = "ack";
  public static String ack() { return ACK+"\n"; }
  public static boolean isAck(String msg) { return msg.matches("\\s*"+ACK+"\\s*"); }
  	 	  
  /*
   * Matchmaking server to Client:
   */
  private static final String MATCHMADE = "matchMade";
  public static String matchMade(String server, long gameId) { return MATCHMADE+" "+server+" "+gameId+"\n"; }
  
  /*
   * Client to Game server:
   */
  private static final String READYFORMATCH = "readyForMatch";
  public static String readyForMatch(long gameId) { return READYFORMATCH+" "+gameId+"\n"; }
  public static Long parseReadyForMatch(String msg)  { return parse1Long(READYFORMATCH, msg); }
  
  private static final String REQUESTREMATCH = "requestRematch";
  public static String requestRematch(boolean request) { return REQUESTREMATCH+" "+((request)? 1:0)+"\n"; }
  public static Boolean parseRequestRematch(String msg) { return parse1Boolean(REQUESTREMATCH, msg); }
  
  /*
   * Game server to Client:
   */
  private static final String MATCHFULL = "matchFull";
  public static String matchFull() { return MATCHFULL+"\n"; }
  public static boolean isMatchFull(String msg) { return msg.matches("\\s*"+MATCHFULL+"\\s*"); }
  
  private static final String MATCHSTARTING = "matchStarting";
  public static String matchStarting(long gameId, long playerId, long uStreamId) { return MATCHSTARTING+" "+gameId+" "+playerId+" "+uStreamId+"\n"; }
  public static long[] parseMatchStarting(String msg)  { return parseNLongs(MATCHSTARTING, msg, 3); }
  
  private static final String SENDINGARENA = "sendingArena";
  public static String sendingArena(String arena) { return SENDINGARENA+" "+arena.replace('\n', ' ')+"\n"; }
  public static String parseSendingArena(String msg) {
    int dataStarts = msg.indexOf(' ');
    if (dataStarts < SENDINGARENA.length()) return null;
    if (!msg.substring(0, dataStarts).equals(SENDINGARENA)) return null;
    final int len = msg.length();
    return msg.substring(dataStarts+1, len - ((msg.charAt(len-1)=='\n')? 1:0));
  }
  
  private static final String MATCHENDED = "matchEnded";
  public static String matchEnded(long gameId) { return MATCHENDED+"\n"; }
  public static boolean isMatchEnded(String msg)  { return msg.matches("\\s*"+MATCHENDED+"\\s*"); }
  
  private static final String DIDOTHERSACCEPTREMATCH = "didOthersAcceptRematch";
  public static String didOthersAcceptRematch(boolean accepted) { return DIDOTHERSACCEPTREMATCH+" "+((accepted)? 1:0)+"\n"; }
  public static Boolean parseDidOthersAcceptRematch(String msg)  { return parse1Boolean(DIDOTHERSACCEPTREMATCH, msg); }
  
  private static Boolean parse1Boolean(String cmd, String msg) {
    Long accepted = parse1Long(cmd, msg);
    if (accepted == null) return null;
    return accepted!=0;
  }
  
  /*
   * Matchmaking server to Game server:
   */
  private static final String POLL = "poll";
  public static String poll() { return POLL+"\n"; }
  public static boolean isPoll(String msg) { return msg.matches("\\s*"+POLL+"\\s*"); }
  
  private static final String EXPECTMATCH = "expectMatch";
  public static String expectMatch(long gameId, int numOfPlayers) { return EXPECTMATCH+" "+gameId+" "+numOfPlayers+"\n";}
  public static long[] parseExpectMatch(String msg)  { return parseNLongs(EXPECTMATCH, msg, 2); }

  
  /*
   * Game server to Matchmaking server:
   */
  private static final String MATCHCOUNT = "matchCount";
  public static String matchCount(int n) { return MATCHCOUNT+" "+n+"\n";}
  public static Long parseMatchCount(String msg)  { return parse1Long(MATCHCOUNT, msg); }

  
  // parse a request with one long argument.
  private static long[] parseNLongs(String cmd, String msg, int n) {
    String[] strParts = parseStrings(cmd, msg);
    if (strParts == null || strParts.length < n+1);
    long[] parts = new long[n];
    for (int j=1; j<1+n; ++j) {
      try { parts[j-1] = Long.parseLong(strParts[j]); }
      catch (NumberFormatException e) { return null; }
    }
    return parts;
  }
  
  // parse a request with one long argument.
  private static Long parse1Long(String cmd, String msg) {
    try { return Long.parseLong(parse1String(cmd, msg)); }
    catch (NumberFormatException e) { return null; }
  }
  
  // parse a request with one string argument.
  private static String parse1String(String cmd, String msg) {
    String[] parts = parseStrings(cmd, msg);
    if (parts == null || parts.length<2) return null;
    return parts[1];
  }
  
  
  // parse a request with one or more string arguments.
  private static String[] parseStrings(String cmd, String msg) {
    // parts[0] should be the command and parts[1] the first arg
    String[] parts = msg.split("[^-\\w]+");
    if (parts.length < 2 || !parts[0].equals(cmd)) return null;
    return parts;
  }
  
  // for testing
  public static void main(String argv[]) throws Exception {
//    assert(isAck(ack()));
//    assert(isAck(" "+ack()+"   "));
//    assert(isPing(ping()));
//    assert(isPing(" "+ping()+"   "));
//    assert("foo".equals(parseRequestMatch(requestMatch("foo"))));
//    assert(null == parseRequestMatch(requestMatch("")));
//    assert(0 == parseMatchMade(matchMade(0)));
    //assert(7 == parseMatchMade(matchMade(7)));
    //assert(123 == parseMatchMade(matchMade(123)));
    //assert(-123 == parseMatchMade(matchMade(-123)));
    
    long[] args = new long[]{ 123l, 4l, 567l};
    long[] argsParsed = parseMatchStarting(matchStarting(args[0], args[1], args[2]));
    assert(args[0] == argsParsed[0]);
    assert(args[1] == argsParsed[1]);
    assert(args[2] == argsParsed[2]);
    
    String arenastr = "<arena> ... </arena>";
    assert(arenastr.equals(parseSendingArena(sendingArena(arenastr))));
    
    Logger.println(Rep.FEW, "tests done.");
  }
}
