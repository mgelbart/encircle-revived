package aoi;

import java.io.*;
import java.net.*;
import java.util.TreeMap;

import aoi.Logger.Rep;

import unreliablestream.*;

/**
 * The main class ran by the server that handles
 * connections to the clients and starts the
 * Simulation.
 */
public abstract class GameServer {
  
  public static final int MATCHMAKERCOMMPORT = 23099;
  
  private static final TreeMap<Long, Simulation> matches = new TreeMap<Long, Simulation>();
  
  private static boolean generateNewMap = true;
  private static int[] numPerNum = null;
  private static ClusterDatabase clusterDbSayWithPET = null;
  private static UnreliableStreamServer udpServ;
  
  public static void main(String argv[]) {

    // Parse input options:
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

    int maxSegmentNumber = -1;
    try {
      reader.readLine(); // dummy line
      generateNewMap = Boolean.parseBoolean(reader.readLine());

      reader.readLine();  // dummy line
      String[] segmentNos = reader.readLine().split(",");
      
      maxSegmentNumber = segmentNos.length;
      numPerNum = new int[maxSegmentNumber]; 
      for (int i = 0; i < maxSegmentNumber; i++)
        numPerNum[i] = Integer.parseInt(segmentNos[i]);
      // NOTE: THIS WILL MESS UP IF YOU READ A PRE-EXISTING ARENA AND IT DOESN'T MATCH
    } catch (IOException e1) {
      Logger.println(Rep.FEW, "Couldn't read server conf file.");
      e1.printStackTrace();
      return;
    }

    
    // Contact the matchmaking server so that it can tell us when to expect clients to
    // connect and what match ids they'll use.
    Thread matchMakerListener = new Thread(new Runnable() {
      @Override
      public void run() {
        ServerSocket listener;
        try {
          listener = new ServerSocket(MATCHMAKERCOMMPORT);
        } catch (IOException e1) {
          Logger.println(Rep.FEW, "failed to bind the matchmaker communication port.");
          e1.printStackTrace();
          return;
        }
        
        while (true) {
          // wait for the matchmaker to connect.
          try {
            Socket socket = listener.accept();
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();
            
            String msg = Protocol.readNextMsg(in);
            
            synchronized (matches) {
              
              if (Protocol.isPoll(msg)) {
                
                // respond with the number of matches running or expected to start
                out.write(Protocol.matchCount(matches.size()).getBytes());
                
              } else {
                
                // otherwise this should be an expectMatch
                long[] matchDetails = Protocol.parseExpectMatch(msg);
                if (matchDetails == null) {
                  Logger.println(Rep.FEW, "unknown msg from matchmaker: "+msg);
                  continue;
                }
                
                // TODO: reject the new match if the server is already overloaded.
                
                final long matchId = matchDetails[0];
                final long numOfPlayers = matchDetails[1];
                if (matches.containsKey(matchId)) {
                  Logger.println(Rep.FEW, "matchId already in use: "+matchId);
                  continue;
                }
                
                // add the match to the list so we expect clients to connect using the id
                matches.put(matchId, createFreshSimulation(matchId));
                Logger.println(Rep.FEW, "expecting match: "+matchId);
                
                // tell the matchmaker we're ready to accept clients
                out.write(Protocol.ack().getBytes());
              }
              
            } // end synchronized (matches)
            
          } catch (IOException e) {
            e.printStackTrace();
          }

        }
      }
    }, "matchmaker-listener");
    matchMakerListener.setDaemon(true);
    matchMakerListener.start();
    
    
    // start a task that starts matches if people don't connect on time
    // or removes them if nobody connected.
    Thread cleanerThread = new Thread(new Runnable() {
      @SuppressWarnings("unchecked")
      @Override
      public void run() {
        while (true) {
          long sleeptime = SimulationUtils.TIMETOCONNECTTOGAMESERV;
          
          synchronized (matches) {
            for (Long m : ((TreeMap<Long, Simulation>) matches.clone()).keySet()) {
              Simulation s = matches.get(m);
              
              final long untilTimeout = SimulationUtils.TIMETOCONNECTTOGAMESERV
                                        -(System.currentTimeMillis() - s.created);
              
              if (untilTimeout < 0) {
                if (s.numConnectedPlayers == 0) {
                  // nobody ever connected
                  matches.remove(m);
                  continue;
                } else {
                  // TODO: do something reasonable
                  //       if too few people connected (like 1)
                  
                  // Start simulating the game with the connected people
                  Thread t = new Thread(s, "simulation-"+s.matchId);
                  t.start();
                  matches.remove(m);
                }
              } else {
                sleeptime = Math.min(sleeptime, untilTimeout+30);
              }
            }
          } // end synchronized (matches)
          
          // sleep until next timeout
          try {
            Thread.sleep(sleeptime);
          } catch (InterruptedException e) {
            Logger.println(Rep.FEW, "interrupted.");
          }
        }
      }
    }, "match-cleaner");
    cleanerThread.setDaemon(true);
    cleanerThread.start();
    
    ServerSocket servSocket;
    
    try {
      servSocket = new ServerSocket(Protocol.GAMESERVERTCPPORT);
      udpServ = new UnreliableStreamServer(Protocol.GAMESERVERUDPPORT);
      udpServ.start();
    } catch (IOException e1) {
      Logger.println(Rep.FEW, "Failed to init sockets.");
      e1.printStackTrace();
      return;
    }

	  // (This can be moved later) Initialize ClusterDatabase;
    try {
      clusterDbSayWithPET = new ClusterDatabase();
    } catch (FileNotFoundException e1) {
      Logger.println(Rep.FEW, "Failed to init cluster DB.");
      e1.printStackTrace();
      return;
    }


    Logger.println(Logger.Rep.FEW, "Waiting for clients to connect...");
    
    while (true) {
      Simulation simulation = null;
      Socket socket = null;
      
      try {        
        socket = servSocket.accept(); // Wait for a client to connect
      } catch (IOException e) {
        Logger.println(Rep.FEW, "Failed to accept a new client connection.");
        e.printStackTrace();
        continue;
      }
      
      Long matchId;
      
      try {
        // figure out which simulation the client is trying to connect
        final int oldTimeout = socket.getSoTimeout();
        socket.setSoTimeout(Protocol.GAMESERVERMATCHREQUESTTIMEOUT);
        String msg = Protocol.readNextMsg(socket.getInputStream());
        socket.setSoTimeout(oldTimeout);
        
        matchId = Protocol.parseReadyForMatch(msg);
        if (matchId == null) {
          Logger.println(Rep.FEW, "Client sent garbage: "+msg);
          socket.close();
          continue;
        }
      } catch (IOException e) {
        Logger.println(Rep.FEW, "Failed to read readyForMatch from "
            + ((socket==null)? null:socket.getInetAddress()));
        e.printStackTrace();
        continue;
      }
      
      synchronized (matches) {
        simulation = matches.get(matchId);

        if (simulation == null || simulation.started != null
            || simulation.numConnectedPlayers == SimulationUtils.NUMPLAYERS) {
          // the match cannot be joined.
          // tell the client to go back to matchmaker
          try {              
            OutputStream out = socket.getOutputStream();
            out.write(Protocol.matchFull().getBytes());
          } catch (IOException e) {
            Logger.println(Rep.FEW, "Failed to send matchFull to "
                + ((socket==null)? null:socket.getInetAddress()));
            e.printStackTrace();
            continue;
          }
        }
  
        
        try {
          simulation.communicators[simulation.numConnectedPlayers].setTCPSocket(socket);
        } catch (IOException e) {
          Logger.println(Rep.FEW, "Failed to set communicator TCP socket for "
              + ((socket==null)? null:socket.getInetAddress()));
          e.printStackTrace();
          continue;
        }
        Logger.println(Logger.Rep.FEW, "Player " + simulation.numConnectedPlayers
            + " connected to match " + simulation.matchId);
        simulation.numConnectedPlayers++;
      }
      
      if (simulation.numConnectedPlayers == SimulationUtils.NUMPLAYERS) {
        synchronized (matches) {
          // Start simulating the game if everyone has connected!
          Thread t = new Thread(simulation, "simulation-"+simulation.matchId);
          t.start();
          matches.remove(simulation.matchId);
        }
      }
    }
  }
  
  public static void expectRematch(long matchId) {
    synchronized (matches) {
      matches.remove(matchId); // make sure the old game isn't there
      matches.put(matchId, createFreshSimulation(matchId));
    }
  }

  private static Simulation createFreshSimulation(long matchId) {
    Arena arena;

    // Generate/load the arena:
    if (generateNewMap) {

      // For now, two of each nSegments from 1 to 7
//      simulation.clusters = clusterDbSayWithPET.generateArenaClusters(numPerNum);
      
      try {
        arena = clusterDbSayWithPET.generateArena(numPerNum);
      } catch (FileNotFoundException e1) {
        Logger.println(Rep.FEW, "Failed to generate map.");
        e1.printStackTrace();
        return null;
      }

      try {
        FileWriter writer = new FileWriter(Arena.ARENA_METADATA_FILENAME);
        BufferedWriter buf = new BufferedWriter(writer);
        buf.write(arena.toString());
        buf.close();
      } catch (Exception e) {
        Logger.println(Logger.Rep.FEW, e.toString());
        assert(false);
      }

    } else {

      try {

        arena = Arena.parse(new FileInputStream(Arena.ARENA_METADATA_FILENAME));

      } catch (Exception e) {
        Logger.println(Logger.Rep.FEW, e.toString());
        assert(false);
        arena = null;
      }

    }
    
    Logger.println(Logger.Rep.FEW, "Arena generated.");

    Simulation simulation = new Simulation(matchId, arena, clusterDbSayWithPET);

    simulation.initGameState();
    
    
    // This helper class sets the Communicator as a receiver of UDP packets as soon
    // as an UnreliableStream to the client is established.
    class SetUDPIO implements UnreliableStreamServer.ConnectionListener {
      Communicator communicator;
      SetUDPIO(Communicator communicator) { this.communicator = communicator; }
      
      @Override
      public void established(UnreliableStream stream) { stream.registerReceiver(communicator); communicator.setUDPOutput(stream); }
    }

    // Prepare each communicator to accept a UDP connection.
    for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j) {
      // Assign an ID number for the UDP stream of the client.
      short id = udpServ.expectConnection();
      
      simulation.communicators[j] = new Communicator(j, simulation, id);

      // set the PlayerListener as a receiver of UDP packets and set GameStateSender to
      // use UDP as soon as a connection is established.
      udpServ.registerConnectionListener(id, new SetUDPIO(simulation.communicators[j]));
    }
    
    
    return simulation;
  }

}
