package unreliablestream;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;

import unreliablestream.UnreliableStream.Packet;

import aoi.Logger.Rep;

/**
 * Provides the server side implementation of UnreliableStream.
 * 
 * The main task of this class is to act as a multiplexer that
 * listens on a single UDP port and directs the incoming packets
 * to the respective UnreliableStreams depending on the stream id.
 * In other words, while UnreliableStream can only connect to a
 * single server at a time, the UnreliableStreamServer can serve
 * multiple clients at the same time.
 */
public class UnreliableStreamServer implements Runnable {

  // the socket to listen on.
  private final DatagramSocket socket;
  
  // a list of connections based on id.
  // this also serves as a filter for the server (packets with other ids are ignored).
  private final Map<Short, UnreliableStream> connections = new HashMap<Short, UnreliableStream>();

  // a list of registered connection listeners
  private final Map<Short, ConnectionListener> connectionListeners = new HashMap<Short, ConnectionListener>();
  
  private final Thread listener;
  
  // kill the server asap
  private boolean kill = false;
  
  // Wait for people to connect to the specified UDP port.
  public UnreliableStreamServer(int port) throws SocketException {
    socket = new DatagramSocket(port);
    
    try {
      socket.setSoTimeout(UnreliableStream.TIMEOUT/4);
    } catch (SocketException e) {
      e.printStackTrace();
      assert(false);
    }
    
    listener = new Thread(this);
  }
  
  private short getFreeId() {
    Short id = null;
    while (id == null || connections.keySet().contains(id))
        id = (short) (Math.random() * Short.MAX_VALUE);
    return id;
  }
  
  public short expectConnection() {
    synchronized (connections) {
      final short id = getFreeId();
      assert(!connections.containsKey(id));
      connections.put(id, null);
      return id;
    }
  }
  
  public void disconnect(short id) {
    UnreliableStream con;
    synchronized (connections) {
      con = connections.remove(id);
      connectionListeners.remove(id);
    }
    if (con != null) con.disconnect();
  }
  
  // ATM, you can only register one listener per id.
  public void registerConnectionListener(short id, ConnectionListener l) {
    connectionListeners.put(id, l);
  }
  
  // Start listening for connections.
  public void start() {
    assert(!listener.isAlive());
    
    listener.start();
  }
  
  // Stop listening for new packets.
  public void stop() {
    assert(listener.isAlive());
    
    kill = true;
  }
  
  public void run() {
    byte[] buf = new byte[Packet.HEADERSIZE + UnreliableStream.MAXDATASIZE + 1];
    
    while (true) {

      DatagramPacket p = new DatagramPacket(buf, buf.length);

      // stop executing if the kill flag is set
      if (kill) {
        aoi.Logger.println(Rep.FEW, "server killed.");
        synchronized (connections) {
          for (UnreliableStream s : connections.values()) {
            s.disconnect();
          }
          connections.clear();
          connectionListeners.clear();
        }
        return;
      }
      
      // otherwise parse the packet
      try {
        
        try {
          socket.receive(p);
        } catch (SocketTimeoutException ex) {
          continue;
        }
        
        final ByteArrayInputStream in = new ByteArrayInputStream(p.getData());
        final Packet parsed = Packet.parse(new DataInputStream(in), p.getLength());
        
        UnreliableStream con;
        synchronized (connections) { con = connections.get(parsed.id); }
        
        if (con == null) {
          if (!connections.containsKey(parsed.id)) { aoi.Logger.println(Rep.INFREQ, "received unknown id: " + parsed.id + " seq: " + parsed.sequenceNo); continue; }
          
          // someone is trying to establish a new connection.
          con = new UnreliableStream(socket, p.getAddress(), p.getPort(), parsed.id);
          synchronized (connections) { connections.put(parsed.id, con); }
          final ConnectionListener lis = connectionListeners.get(parsed.id);
          if (lis != null) lis.established(con); // call the registered call back method, if any
          con.startToKeepAlive();
          
        } else
          // normal packet
          con.processReceived(parsed);
          
        
        
      } catch (IOException e) {
        e.printStackTrace();
        assert(false);
      }
      
    }
  }

  /**
   * Register a ConnectionEventListener using
   * registerConnectionListener() to receive
   * notification of the establishment of a new
   * connection.
   */
  public interface ConnectionListener {
    // This will block server excecution so should return quickly!
    public void established(UnreliableStream stream);
  }
  
  
  // for testing
  public static void main(String argv[]) throws Exception {
    final int port = aoi.Server.UDPPORT;
    
    UnreliableStreamServer serv = new UnreliableStreamServer(port);
    
    /*
     * Set up the server to echo sent messages from either stream.
     */
    
    final short id1 = serv.expectConnection();
    final short id2 = serv.expectConnection();
    
    ConnectionListener listener = new ConnectionListener() {

      @Override
      public void established(UnreliableStream stream) {
        stream.registerReceiver(new UnreliableStream.Receiver() {

          @Override
          public boolean received(byte[] data, UnreliableStream src) {
            aoi.Logger.println(Rep.FEW, "server received from id " + src.getId() + ": " + new String(data));
            try {
              src.send(("ack: " + new String(data)).getBytes());
            } catch (IOException e) {
              e.printStackTrace();
              assert(false);
            }
            return true;
          }
          
        });
        
      }
      
    };
    
    serv.registerConnectionListener(id1, listener);
    serv.registerConnectionListener(id2, listener);
    
    serv.start();
    
    
    /*
     * Set up two clients that send messages to the server.
     */
 
    UnreliableStream stream1 = new UnreliableStream(new DatagramSocket(), InetAddress.getLocalHost(), port, id1);
    UnreliableStream stream2 = new UnreliableStream(new DatagramSocket(), InetAddress.getLocalHost(), port, id2);
    
    UnreliableStream.Receiver echoer = new UnreliableStream.Receiver() {

      @Override
      public boolean received(byte[] data, UnreliableStream src) {
        aoi.Logger.println(Rep.FEW, "client received from id " + src.getId() + ": " + new String(data));
        return true;
      }
      
    };
    
    stream1.registerReceiver(echoer);
    stream2.registerReceiver(echoer);
    
    class DummySender implements Runnable {
      UnreliableStream s;
      public DummySender(UnreliableStream s) { this.s = s; }
      
      public void run() {
        try {
          s.send(("first client->server using id "+s.getId()+".").getBytes());
        } catch (IOException e1) {
          e1.printStackTrace();
          assert(false);
        }
        
        try {
          Thread.sleep(2000);
        } catch (InterruptedException e) {
          e.printStackTrace();
          assert(false);
        }

        try {
          s.send(("second client->server using id "+s.getId()+".").getBytes());
        } catch (IOException e) {
          e.printStackTrace();
          assert(false);
        }
      }
    }
  
    Runnable sender1 = new DummySender(stream1);
    Runnable sender2 = new DummySender(stream2);
    
    Thread thread1 = new Thread(sender1);
    Thread thread2 = new Thread(sender2);
    
    stream1.connect();
    stream2.connect();
    
    thread1.start();
    thread2.start();

  }
  
}
