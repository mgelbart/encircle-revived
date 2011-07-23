package aoi;

import java.io.*;
import java.net.*;

import unreliablestream.*;

/**
 * The main class ran by the server that handles
 * connections to the clients and starts the
 * Simulation.
 */
public abstract class Server {

  public static final int TCPPORT = 7601;
  public static final int UDPPORT = 7601;

  private static final String CLUSTER_DATASET = "fly";  // should not be hardcoded...

  public static void main(String argv[]) throws Exception {

    // Parse input options:
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

    reader.readLine();  // dummy line
    final boolean generateNewMap = Boolean.parseBoolean(reader.readLine());

    reader.readLine();  // dummy line
    String[] segmentNos = reader.readLine().split(",");
    final int maxSegmentNumber = segmentNos.length;
    int[] numPerNum = new int[maxSegmentNumber]; 
    for (int i = 0; i < maxSegmentNumber; i++)
      numPerNum[i] = Integer.parseInt(segmentNos[i]);
    // NOTE: THIS WILL MESS UP IF YOU READ A PRE-EXISTING ARENA AND IT DOESN'T MATCH

    ServerSocket servSocket = new ServerSocket(TCPPORT);
    UnreliableStreamServer udpServ = new UnreliableStreamServer(UDPPORT);
    udpServ.start();

	  // (This can be moved later) Initialize ClusterDatabase;
	  ClusterDatabase clusterDbSayWithPET = new ClusterDatabase(CLUSTER_DATASET);

    while (true) {

      Arena arena;

      // Generate/load the arena:
      if (generateNewMap) {

        // For now, two of each nSegments from 1 to 7
//        simulation.clusters = clusterDbSayWithPET.generateArenaClusters(numPerNum);
        
        arena = clusterDbSayWithPET.generateArena(numPerNum);

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

      Simulation simulation = new Simulation(arena);

      simulation.initGameState();

      // Establish client connections.
      
      // This helper class sets the PlayerListener as a receiver of UDP packets as soon
      // as an UnreliableStream to the client is established.
      class SetUDPIO implements UnreliableStreamServer.ConnectionListener {
        PlayerListener listener;
        GameStateSender sender;
        SetUDPIO(PlayerListener listener, GameStateSender sender) { this.listener = listener; this.sender = sender; }
        
        @Override
        public void established(UnreliableStream stream) { stream.registerReceiver(listener); sender.setUDPOutput(stream); }
      }

      for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j) {
        // Assign an ID number for the UDP stream of the client.
        short id = udpServ.expectConnection();
        
        simulation.senders[j] = new GameStateSender(j, simulation, null, id);
        simulation.listeners[j] = new PlayerListener(j, simulation, null);

        // We need a reference to the sender threads to interrupt
        // them when the game sate is updated.
        simulation.senderThreads[j] = new Thread(simulation.senders[j], "sender-"+j);
        simulation.listenerThreads[j] = new Thread(simulation.listeners[j], "listener-"+j);

        // set the PlayerListener as a receiver of UDP packets and set GameStateSender to
        // use UDP as soon as a connection is established.
        udpServ.registerConnectionListener(id, new SetUDPIO(simulation.listeners[j], simulation.senders[j]));
      }


      // Wait for two clients to connect:
      Logger.println(Logger.Rep.FEW, "Waiting for clients to connect...");
      
      Socket[] sockets = new Socket[2];
      for (int j=0; j<2; ++j) {
        sockets[j] = servSocket.accept();
        Logger.println(Logger.Rep.FEW, "Player " + j + " connected.");

        // Don't buffer data before sending TCP packets.
        sockets[j].setTcpNoDelay(true);

        simulation.senders[j].setTCPOutput(sockets[j].getOutputStream());
        simulation.listeners[j].setTCPStream(sockets[j].getInputStream());

        // Start sender & listener threads.
        simulation.senderThreads[j].start();
        simulation.listenerThreads[j].start();
      }


      // Start simulating the game!
      simulation.startGame(clusterDbSayWithPET, maxSegmentNumber);

      // TODO: start the while(true) loop from the beginning
      //       to server multiple games at a time.
      //
      //       However, should first implement a way to
      //       gracefully stop the threads for games that are
      //       finished. Also, some limitations that prevent
      //       overloading the server need to be in place.
      udpServ.stop();
      return;
    }
  }


}
