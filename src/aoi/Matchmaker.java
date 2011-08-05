package aoi;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;

import aoi.Logger.Rep;
import aoi.Protocol.ClientState;

/**
 * The matchmaking server.
 */
public class Matchmaker {

  private static final String GAMESERVERLIST = "gameservers.txt";

  // A special port that AI clients connect to.
  // They get put into a special queue from which they are taken
  // only when a normal client requests to play against an AI.
  public static final int AIPORT = 6937;
  
	GameServerManager gsm;
	Queue<GameClient> clients = new LinkedBlockingQueue<GameClient>();
	Queue<GameClient> ais = new LinkedBlockingQueue<GameClient>();
	
	Random randomGenerator = new Random();


  public static void main(String argv[]) throws Exception {
    new Matchmaker();
  }

  
  Matchmaker() {
    Logger.setVerbosity(Rep.FREQ);
    
    gsm = new GameServerManager();
    long lastServListRead = System.currentTimeMillis();

    // Start listening for connections from clients
    GameClientListener clientListener = new GameClientListener(Protocol.MATCHMAKERTCPPORT, clients);
    Thread clientListenerThread = new Thread(clientListener, "client-listener");
    clientListenerThread.start();
    
    // and from AIs.
    GameClientListener aiListener = new GameClientListener(AIPORT, ais);
    Thread aiListenerThread = new Thread(aiListener, "ai-listener");
    aiListenerThread.start();

    
    while (true) {
      // Keep processing incoming messages from the clients,
      // dropping dead client connection, and starting new matches.
      try {
        final int PROCESSPERIOD = 1000;
        Thread.sleep(PROCESSPERIOD);
      } catch (InterruptedException e) { Logger.println(Rep.FEW, "Interrupted."); }
      
      synchronized (clients) {
        // iterate over both clients and ais
        boolean doingAIs = false;
        for (Iterator<GameClient> i = clients.iterator(); i.hasNext() || !doingAIs;) {
          if (!doingAIs && !i.hasNext()) {
            // move onto the AI list.
            doingAIs = true;
            i = ais.iterator();
            if (!i.hasNext()) break; // empty ais
          }
          GameClient c = i.next();

          try {
            c.processPendingMessages();
          } catch (IOException e) {
            Logger.println(Rep.FEW, "Failed to process messages for client "+c);
            e.printStackTrace();
            clients.remove(c);
          }
          
          // remove dead clients.
          if (!c.isAlive()) {
            Logger.println(Rep.INFREQ, "Removing dead client: " + c);
            closeSocket(c.socket);
            i.remove();
          }
        }
      } // end synchronized (clients)

      // start matches that are already full
      checkForMatch();
      
      // also occasionally reread the server list
      final int SERVERLISTREADPERIOD = 20000;
      if (System.currentTimeMillis() - lastServListRead > SERVERLISTREADPERIOD) {
        gsm.readListOfGameServers();
        lastServListRead = System.currentTimeMillis();
      }
    }
  }

  
  void checkForMatch() {

    GameClient[] matchedClients = null;

    synchronized (clients) {
      // start a match if enough clients have connected
      boolean startMatch = (clients.size() >= SimulationUtils.NUMPLAYERS);
      
      // or someone wants to play against an AI.
      if (!startMatch)
        for (GameClient c : clients)
          if (c.wantsAI) startMatch = true;
      
      if (startMatch && clients.size()+ais.size() >= SimulationUtils.NUMPLAYERS) {
        // take the first NUMPLAYERS players from the queue.
        matchedClients = new GameClient[SimulationUtils.NUMPLAYERS];
        for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j) {
          matchedClients[j] = (!clients.isEmpty())?
              clients.remove() : ais.remove();
        }
      }
      
      // Print out remaining queue
      if (!clients.isEmpty())
        Logger.println(Rep.FREQ, "Queue contains:\n" + join(clients, "\n"));
    } // end synchronized (clients)
    
    if (matchedClients != null) {
      Logger.println(Rep.FREQ, "Matching first " + matchedClients.length + " clients.");

      // TODO: generate a long id.
      int matchId = randomGenerator.nextInt(Integer.MAX_VALUE);
      
      // find a working server
      String server = null;
      boolean success = false;
      while (!success) {
        server = gsm.getNextGameServer();   
        Logger.println(Rep.FREQ, "contacting game server: "+server+" "+matchId);
        success = gsm.notifyServerOfMatch(server, matchId);
        if (!success) {             
          try {
            Thread.sleep(700);  // wait a while so we don't thrash looking for a server  
          } catch (InterruptedException e) {
            Logger.println(Rep.FEW, "Sleep interrupted");
            e.printStackTrace();
            return;
          }
        }
      }
      
      String matchMadeMessage = Protocol.matchMade(server, matchId);
      
      // send matchMade to all clients
      for (GameClient c : matchedClients) {
        try {
          c.socket.getOutputStream().write(matchMadeMessage.getBytes());
        } catch (IOException e) {
          Logger.println(Rep.FEW, "Failed to write matchMade to client "+c);
          e.printStackTrace();
        }
      }
      
      // check whether everyone acked the match
      final long started = System.currentTimeMillis();
      final int ACKTIMEOUT = 2000;
      while (System.currentTimeMillis() - started < ACKTIMEOUT) {
        for (GameClient c : matchedClients) {
          try { c.processPendingMessages(); }
          catch (IOException e) { continue; }
        }

        boolean waitingForAck = false;
        for (GameClient c : matchedClients)
          if (c.state != ClientState.READYFORMATCH)
            waitingForAck = true;
        
        if (!waitingForAck) break;
        
        try { Thread.sleep(300); }
        catch (InterruptedException e) { Logger.println(Rep.FEW, "interrupted"); }
      }

      for (GameClient c : matchedClients)
        if (c.state != ClientState.READYFORMATCH)
          Logger.println(Rep.FEW, "did not get ack for matchMade from client "+c);

      // close matched client connections
      for (GameClient c : matchedClients) closeSocket(c.socket);
    }

  }
	
	// Send and wait for an "ack". 
	boolean acknowledgedMessage(Socket socket, String message, int timeout) {
		
		boolean successful = true;

		try {

			InputStream in = socket.getInputStream();
			OutputStream out = socket.getOutputStream();

			out.write(message.getBytes());
			out.flush();

			/*
			 * 
			 * Before we decide that we've successfully communicated with c,
			 * let's make sure that c didn't drop out earlier because the
			 * user got tired of waiting. We'll do a read() of an "ack" to
			 * make sure.
			 * 
			 * There are three cases if the client has closed already: (1)
			 * Graceful close (like the nc (netcat) command on Ubuntu) - we
			 * have received a FIN, so now when we read, read will return -1
			 * (2) Ungraceful close (like the OpenBSD nc ported to Cygwin) -
			 * the write() above will result in a RST being sent back, so
			 * read will throw a java.net.SocketException: Software caused
			 * connection abort: recv failed (3) Close by turning client
			 * machine physically off. The read will timeout.
			 * 
			 * Without an "ack", we cannot distinguish between (1) due to
			 * the client giving up and exiting a long time ago, from (1)
			 * due to the client closing communication with us due to the
			 * message we just sent us (which could happen if the message is
			 * a match setup). So we wait for an "ack" from the client.
			 */

			final int oldTimeout = socket.getSoTimeout();
			socket.setSoTimeout(timeout);
			String response = Protocol.readNextMsg(in);
			if ((response == null) || !Protocol.isAck(response)) {
				Logger.println(Rep.FEW,
						"Instead of ack, response was: " + response);
				successful = false;
			}
			socket.setSoTimeout(oldTimeout);
		} catch (Exception e) {
			successful = false;
			Logger.println(Rep.FEW, "Socket exception for " + socket.getRemoteSocketAddress());
			e.printStackTrace();
		}

		return successful;
	}

	/*
	 * Close the given connection.
	 * Log errors but ignore them otherwise.
	 */
	void closeSocket(Socket socket) {
		try {
			socket.close();
		} catch (Exception e) {
			Logger.println(Rep.FEW, "Socket exception for " + socket.getRemoteSocketAddress());
			e.printStackTrace();
		}
	}
	
	private static String join(Collection<?> set, String delimiter) {
	  if (set.size() == 0) return "(empty)";
    StringBuilder s = new StringBuilder();
    for (Iterator<?> i=set.iterator(); i.hasNext();) {
      s.append(i.next());
      if (i.hasNext()) s.append(delimiter);
    }
    return s.toString();
	}
	
	

  // This class contains the data for one game client waiting to be matched.
  class GameClient {
    // The client is expected to keep sending keepAlives until
    // a match is made.
    static final int TIMEOUT = 4000;
    
    Socket socket;
    long lastReceived; // time of last message
    ClientState state;
    boolean wantsAI = false;

    GameClient(Socket s) throws IOException {
      socket = s;
      lastReceived = System.currentTimeMillis();
      state = ClientState.REQUESTEDMATCH;
    }

    @Override
    public String toString() {
      return "GameClient " + socket.getRemoteSocketAddress()
              + " (state: " + state + ")";
    }
    
    boolean isAlive() {
      return !socket.isClosed() && System.currentTimeMillis() - lastReceived < TIMEOUT;
    }
    
    /**
     * Read and process any pending messages from the client.
     * @throws IOException 
     */
    synchronized void processPendingMessages() throws IOException {
      InputStream in = socket.getInputStream(); 
      if (in.available() == 0) return;
      
      final int timeout = 10;
      final int oldTimeout = socket.getSoTimeout();
      socket.setSoTimeout(timeout);

      String msg;
      while (in.available() > 0) {
        msg = Protocol.readNextMsg(socket.getInputStream());
        lastReceived = System.currentTimeMillis();
        
        if (msg != null) {
          if (state == ClientState.REQUESTEDMATCH && Protocol.isKeepAlive(msg))
            // the client is waiting to be matched:
            continue;
  
          if (state == ClientState.REQUESTEDMATCH && Protocol.isAck(msg)) {
            // the client acknowledged the sent matchMade
            state = ClientState.READYFORMATCH;
            continue;
          }
          
          if (state == ClientState.REQUESTEDMATCH && Protocol.isRequestAI(msg)) {
            wantsAI = true;
          }
        }
        
        // unknown msg:
        Logger.println(Rep.FEW, "unexpected msg: "+msg
            +"\nfrom: "+this);
      }
      
      socket.setSoTimeout(oldTimeout);
      return;
    }
  }

  // This thread loops listening for new game clients on 'port'
  // and adds the connected client to the Collection 'queue'.
  class GameClientListener implements Runnable {
    int port;
    Collection<GameClient> queue; 
    GameClientListener(int port, Collection<GameClient> queue) {
      this.port = port;
      this.queue = queue;
    }
    
    @Override
    public void run() {
      ServerSocket servSocket;
      try {
        servSocket = new ServerSocket(port);
      } catch (IOException e1) {
        Logger.println(Rep.FEW, "Failed to init server socket.");
        e1.printStackTrace();
        return;
      }

      while (true) {
        try {
          Socket socket = servSocket.accept();
          socket.setTcpNoDelay(true);
          Logger.println(
              Rep.FREQ,
              "Connected to client "
                  + socket.getRemoteSocketAddress());
          GameClient client = new GameClient(socket);
          synchronized (queue) { queue.add(client); }
          checkForMatch();
        } catch (IOException e) {
          Logger.println(Rep.FEW, "Socket exception");
          e.printStackTrace();
          return;
        }
      }
    }

  }

  
  // This class maintains the list of game servers for load balancing.
  class GameServerManager {
    List<String> gameServers = new ArrayList<String>();
    int listPosition = 0;

    GameServerManager() {
      readListOfGameServers();
    }

    boolean notifyServerOfMatch(String server, int matchId) {
      boolean success = true;
      Socket s = null;
      
      try {
        s = new Socket(server, GameServer.MATCHMAKERCOMMPORT);          
        success = acknowledgedMessage(s, Protocol.expectMatch(matchId, SimulationUtils.NUMPLAYERS), 3000); 
      } catch (Exception e) {
        Logger.println(Rep.FEW, "Socket exception for server " + server + " at " + ((s!=null)? s.getRemoteSocketAddress():null));
        e.printStackTrace();
        success = false;
      }
      if (s != null)
        closeSocket(s);
      
      return success;     
    }
                
    
    String getNextGameServer() {
      // Round-robin load balancing
      synchronized (gameServers) {
        // note: this needs to be within the same synchronized block
        //       and before the gameServers.get(listPosition) call
        //       since gameServers could otherwise be reread in the
        //       meanwhile.
        if (listPosition >= gameServers.size()) listPosition = 0;
        
        if (!gameServers.isEmpty()) {
          final String server = gameServers.get(listPosition);            
          listPosition++;
          return server;
        } else {
          Logger.println(Rep.FEW, "gameServers is empty!");
          return "localhost";
        }
      }
    }

    void readListOfGameServers() {
      if (gameServers.isEmpty())
        Logger.println(Rep.FEW, "Reading game server list from " + GAMESERVERLIST);

      BufferedReader reader = new BufferedReader(new InputStreamReader(
          Matchmaker.class.getClassLoader().getResourceAsStream(GAMESERVERLIST)));

      /*
       *  first read the whole file to make sure it contains the start and end tags
       */
      final String STARTTAG = "start-of-list";
      final String ENDTAG = "end-of-list";
      
      boolean listStarted = false;
      boolean listEnded = false;
      ArrayList<String> newServers = new ArrayList<String>();
      try {
        String line;
        while ((line = reader.readLine()) != null) {
          //Logger.println(Rep.FEW, line + " " + listStarted + " " + listEnded);
          if (line.startsWith(ENDTAG)) {
            listEnded = true;
            break;
          } else if (line.startsWith(STARTTAG))
            listStarted = true;
          else if (!line.isEmpty() && listStarted && !listEnded)
            newServers.add(line);
        }
        reader.close();
      } catch (IOException e1) {
        Logger.println(Rep.FEW, "Couldn't read server conf file.");
        e1.printStackTrace();
        return;
      }
      
      if (!listStarted) {
        Logger.println(Rep.FEW, GAMESERVERLIST + " did not contain " + STARTTAG);
        return;
      }
      
      if (!listEnded) {
        Logger.println(Rep.FEW, GAMESERVERLIST + " did not contain " + ENDTAG);
        return;
      }

      if (newServers.isEmpty()) {
        Logger.println(Rep.FEW, GAMESERVERLIST + " did not contain any servers.");
        return;
      }
      
      /*
       * Now update the actual gameServers list in use.
       */
      synchronized (gameServers) {
        boolean listChanged = false;
        if (gameServers.size() != newServers.size())
          listChanged = true;
        else {
          for (int j=0; j<newServers.size(); ++j)
            if (!newServers.get(j).equals(gameServers.get(j)))
              listChanged = true;
        }
        
        gameServers = newServers;
  
        if (listChanged)
          Logger.println(Rep.FEW, "Game servers updated:\n"
              + join(gameServers, "\n"));
      }
    }

  }

  
}
