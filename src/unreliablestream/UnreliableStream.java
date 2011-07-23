package unreliablestream;

import java.io.*;
import java.net.*;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;

import aoi.Logger;
import aoi.Logger.Rep;

/**
 * This class provides an unreliable packet "stream" between the client and server.
 *
 * The stream has the following properties.
 * 
 *   - The delivery of packets is _not_ guaranteed.
 *   - The packets are delivered in time order.
 *   - The packet data size is limited to MAXDATASIZE bytes.
 *
 */
public class UnreliableStream {
  
  // Maximum amount of data carried by a single packet, in bytes.
  public static final int MAXDATASIZE = 2048;
  
  // The connection is considered dead if no packets are received in this time.
  // Specified in milliseconds.
  static final int TIMEOUT = 4000;
  
  // An empty packet is sent at least every KEEPALIVEPERIOD ms if there's
  // nothing else to send.
  private static final int KEEPALIVEPERIOD = 150;


  // The socket used to communicate with the remote party.
  private DatagramSocket socket;
  
  // The address and port of the remote party.
  private InetAddress remoteHost;
  private int remotePort;
  
  // The id of this connection.
  private short id;
  
  // a counter for the packet id of own packets
  private Integer lastSentSeq = 0;
  
  // the latest packet received from the remote host
  private Packet lastReceived;
  // if the i'th bit is set --> lastReceivedSeq-i has been received
  private int relativeReceived;
  
  

  // The system time when the last packet was received.
  private long lastReceivedTime = 0;
  private long lastSentTime = 0;
  
  // The seq number and send time of last sent packets that have not been acked yet.
  // Used to estimate lag.
  private TreeMap<Integer, Long> notAcked = new TreeMap<Integer, Long>(); 
  
  // The low pass filtered estimates of the lag.
  private Double lagAverage = null;
  private Double lagPessimistic = null;
  
  // A timer that sends empty packets if there's nothing else to send.
  private Timer keepAliveTimer;
  
  // list of handlers for received packets
  private Vector<Receiver> receivers = new Vector<Receiver>();
  
  public UnreliableStream(DatagramSocket socket, InetAddress remoteHost, int remotePort, short id) {
    
    try {
      socket.setSoTimeout(TIMEOUT/4);
    } catch (SocketException e) {
      e.printStackTrace();
      assert(false);
    }
    
    this.socket = socket;
    this.remoteHost = remoteHost;
    this.remotePort = remotePort;
    this.id = id;
  }
  
  // Connect to the remote host and start listening for incoming packets.
  public void connect() throws IOException {
  
    Thread listenThread = new Thread(new Runnable() {

      @Override
      public void run() {
        byte[] buf = new byte[Packet.HEADERSIZE + UnreliableStream.MAXDATASIZE + 1];
        DatagramPacket p = new DatagramPacket(buf, buf.length);
        
        while (true) {
          
          // wait for an incoming packet to arrive
          try {
            socket.receive(p);
          } catch (IOException e) {
            if (keepAliveTimer == null) { aoi.Logger.println(Rep.INFREQ, "connection closed id: " + id); return; }
            if (lastReceivedTime - System.currentTimeMillis() > TIMEOUT) { aoi.Logger.println(Rep.INFREQ, "timeout on id: " + id); return; }
            continue;
          }
          
          // parse the bytes into a Packet
          final ByteArrayInputStream in = new ByteArrayInputStream(p.getData());
          Packet parsed;
          try {
            parsed = Packet.parse(new DataInputStream(in), p.getLength());
          } catch (IOException e) {
            Logger.println(Rep.FEW, "Failed to parse packet.");
            e.printStackTrace();
            continue;
          }
          
          processReceived(parsed);
        }

      }
      
    }, "ustream-listener");
    
    listenThread.start();
    
    startToKeepAlive();
  }
  
  void startToKeepAlive() throws IOException {
    keepAliveTimer = new Timer();
    lastReceivedTime = System.currentTimeMillis();
    keepAliveTimer.schedule(new KeepAliveTask(this), KEEPALIVEPERIOD, KEEPALIVEPERIOD/3);
    
    // send the first keep alive packet.
    send(new byte[0]);
  }

  // Send the next packet
  public void send(byte[] data) throws IOException {
    if (data.length > MAXDATASIZE) {
      Logger.println(Rep.FEW, "data size=" + data.length + " exceeds MAXDATASIZE=" + MAXDATASIZE);
    }
    assert(data.length <= MAXDATASIZE);
    
    int seq, ack, ackRel;
    synchronized (lastSentSeq) { seq = ++lastSentSeq; }
    if (lastReceived != null) synchronized (lastReceived) { ack = lastReceived.sequenceNo; ackRel = relativeReceived; }
    else { ack = 0; ackRel = 0; }
      
    
    final byte[] all = new Packet(id, seq, ack, ackRel, data).toBytes();
    final DatagramPacket p = new DatagramPacket(all, all.length, remoteHost, remotePort);

    socket.send(p);
    lastSentTime = System.currentTimeMillis();
    notAcked.put(seq, lastSentTime);
    
//    Logger.println(RepetitionRate.INFREQUENT, "sent " + data.length + " bytes. seq: " + seq);
    
  }
  
  // Register a handler for received packets.
  public void registerReceiver(Receiver r) {
    synchronized (receivers) {
      receivers.add(r);
    }
  }
  
  // deal with a parsed incoming packet
  void processReceived(Packet p) {
    if (p.id != id) { aoi.Logger.println(Rep.INFREQ, "received unknown id: " + p.id); return; }
    
    final int diff = (lastReceived == null) ? 1 : p.sequenceNo - lastReceived.sequenceNo;
    if (diff > 0) {
      
      // update the ack fields sent to the remote party
      if (lastReceived == null) { relativeReceived = 0; lastReceived = p; }
      else {
        synchronized (lastReceived) {
          lastReceived = p;
          relativeReceived = relativeReceived<<diff; // shift all previous acks by diff
          relativeReceived = relativeReceived | (1<<(diff-1));  // add the previous lastReceived to the relative acks
        }
      }
      lastReceivedTime = System.currentTimeMillis();
      
      // update the lag estimates
      final Long sentTimeOfAcked = notAcked.remove(p.ack);
      if (sentTimeOfAcked != null) {
        final double lag = lastReceivedTime - sentTimeOfAcked;
        
        if (lagAverage == null || lagPessimistic == null) { lagAverage=lag; lagPessimistic=lag; }
        else {
          lagAverage     = lagAverage     + (lag-lagAverage)     * 0.01;
          // for the pessimistic average, use a different time constant for below/above average lag
          lagPessimistic = lagPessimistic + (lag-lagPessimistic) * ((lagPessimistic > lag)? 0.01:1.);
        }
        
        // ignore skipped packets
        while (!notAcked.isEmpty() && notAcked.firstKey() - p.ack < 0)
          notAcked.remove(notAcked.firstKey());
      } else {
//        Logger.println(Rep.INFREQ, "already acked: " + p.ack);
      }
      
      
//      Logger.println(RepetitionRate.INFREQUENT, "received " + p.data.length + " bytes. seq: " + p.sequenceNo);
      
      synchronized (receivers) {
        for (Receiver l : receivers) {
          // pass the packet to receivers until one of them consumes it
          if (l.received(p.data, this)) return;
        }
      }
      
      return;
    } else {
      aoi.Logger.println(Rep.INFREQ, "out of order packet received. id: " + p.id);
      return;
    }
  }
  
  // return the fraction of packets that made it to the remote party recently.
  public double sendSuccessRate() {
    if (lastReceived == null) return -1;
    
    int ones = 0;
    final int ackRel = lastReceived.relativeAck;
    for (int j=0; j<Packet.ACKBITS; ++j)
      if ((ackRel & 1<<j) != 0) ++ones;
    
    return (1 + ones) / (double) (1 + Packet.ACKBITS);
  }
  
  // return the fraction of packets that made it from the remote party recently.
  public double receiveSuccessRate()  {
    if (lastReceived == null) return -1;
    
    int ones = 0;
    final int ackRel = relativeReceived;
    for (int j=0; j<Packet.ACKBITS; ++j)
      if ((ackRel & 1<<j) != 0) ++ones;
    
    return (1 + ones) / (double) (1 + Packet.ACKBITS);
  }

  /*
   * Returns a low pass filtered average estimate of the lag.
   * This includes both network lag and processing time on the
   * remote side between receiving a packet and sending the next one.
   */
  public double averageLag() { return lagAverage; }
  
  /*
   * Same as averageLag() but the lag estimate is low pass filtered
   * in such a way that it increases rapidly whenever the instantaneous
   * lag is greater than the current estimate.
   */
  public double pessimisticLag() { return lagPessimistic; }
  
  // Returns true iff the connection is considered to be alive.
  public boolean isAlive() {
    return System.currentTimeMillis() - lastReceivedTime < TIMEOUT;
  }

  // Kills the connection
  public void disconnect() {
    assert(keepAliveTimer != null);
    keepAliveTimer.cancel();
    keepAliveTimer = null;
  }
  
  /*
   * Return the id of the current connection.
   */
  public short getId() {
    return id;
  }
  
  
  /**
   * You can register a class that implements this interface as
   * a receiver of incoming data by calling registerReceiver().
   */
  public interface Receiver {
    /*
     * Called whenever a new packet is received.
     * Return true if you want to consume the event.
     * This method blocks the parsing of new packets so it must return quickly.
     * 
     * @param data the received data
     * @param src the source stream
     */
    public boolean received(byte[] data, UnreliableStream src);
  }
  
  
  
  
  static class Packet {
    // The size of the header in bytes.
    static final int HEADERSIZE = 14;
    
    // id number of this connection
    final short id;
    
    // The sequence number of this packet
    final int sequenceNo;
    
    // The greatest sequence number received so far
    final int ack;
    
    // Other sequence numbers received so far, relative to ack.
    // i.e setting relativeAck[i]==true means that (ack-1-i) was
    // received.
    static final int ACKBITS = 32;  // size of the relativeAck field in bits
    final int relativeAck; // used as a bit mask
    
    // the data to be sent/read, make sure to limit this to some reasonable
    // size for a UDP packet.
    final byte[] data;
    
    Packet(short id, int sequenceNo, int ack, int relativeAck, byte[] data) {
      this.id = id;
      this.sequenceNo = sequenceNo;
      this.ack = ack;
      this.relativeAck = relativeAck;
      this.data = data;
    }
    
    /*
     * Whether this Packet acks the given sequence number.
     */
    boolean acks(int seq) {
      if (seq > ack || seq < ack-ACKBITS) return false;
      if (seq == ack) return true;
      
      final int mask = 1<<((ack-1)-seq);
      return (relativeAck & mask) != 0;
    }
    
    /*
     * Return all the sequence numbers acked by this packet.
     */
    Set<Integer> allAcks() {
      TreeSet<Integer> a = new TreeSet<Integer>();
      
      a.add(ack);
      
      for (int j=0; j<ACKBITS; ++j) {
        final int mask = 1<<j;
        if ((relativeAck & mask) != 0) a.add(ack-j);
      }
      
      return a;
    }
    
    static Packet parse(DataInputStream in, int len) throws IOException {
      final short id = in.readShort();
      final int sequenceNo = in.readInt();
      final int ack = in.readInt();
      final int relativeAck = in.readInt(); // the ack bits as int
      
      final int databytes = len-Packet.HEADERSIZE;
      assert(databytes>=0);
      final byte[] data = new byte[databytes];
      if (databytes > 0)
        in.readFully(data, 0, Math.min(databytes, MAXDATASIZE));

      return new Packet(id, sequenceNo, ack, relativeAck, data);
    }

    byte[] toBytes() {
      ByteArrayOutputStream out = new ByteArrayOutputStream(HEADERSIZE + data.length);
      DataOutputStream dout = new DataOutputStream(out);
      
      try {
        dout.writeShort(id);
        dout.writeInt(sequenceNo);
        dout.writeInt(ack);
        dout.writeInt(relativeAck);
        dout.write(data);
      } catch (IOException e) {
        e.printStackTrace();
        assert(false);
      }
      
      return out.toByteArray();
    }

  }
  
  private static class KeepAliveTask extends TimerTask {
    UnreliableStream stream;

    KeepAliveTask(UnreliableStream stream) {
      this.stream = stream;
    }
    
    @Override
    public void run() {
      if (!stream.isAlive()) { this.cancel(); return; };
      if (System.currentTimeMillis() - stream.lastSentTime > KEEPALIVEPERIOD) {
        try {
          stream.send(new byte[0]);
        } catch (IOException e) {
          Logger.println(Rep.FEW, "Failed to send a keep alive.");
          e.printStackTrace();
        }
      }
    }
    
  }
  
}
