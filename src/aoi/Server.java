package aoi;

import java.io.*;
import java.net.*;

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


	  // (This can be moved later) Initialize ClusterDatabase;
	  ClusterDatabase clusterDbSayWithPET = new ClusterDatabase(CLUSTER_DATASET);


    while (true) {

      Simulation simulation = new Simulation();


      // Generate/load the arena:
      if (generateNewMap) {

        // For now, two of each nSegments from 1 to 7
//        simulation.clusters = clusterDbSayWithPET.generateArenaClusters(numPerNum);
        
        simulation.arena = clusterDbSayWithPET.generateArena(numPerNum);

        try {
          FileWriter writer = new FileWriter(Arena.ARENA_METADATA_FILENAME);
          BufferedWriter buf = new BufferedWriter(writer);
          buf.write(simulation.arena.toString());
          buf.close();
        } catch (Exception e) {
          Logger.println(Logger.RepetitionRate.FEW, e.toString());
          assert(false);
        }

      } else {

        try {

          simulation.arena = Arena.parse(new FileInputStream(Arena.ARENA_METADATA_FILENAME));
          for (Cluster c : simulation.arena.clusters) {
            simulation.clusters.add(clusterDbSayWithPET.copyFromCluster(c));
		      }

        } catch (Exception e) {
          Logger.println(Logger.RepetitionRate.FEW, e.toString());
          assert(false);
        }

      }
	    
      Logger.println(Logger.RepetitionRate.FEW, "Arena generated.");


      simulation.initGameState();

      // Establish client connections.


      Socket[] sockets = new Socket[2];

      for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j) {
        simulation.senders[j] = new GameStateSender(j, simulation, null);
        simulation.listeners[j] = new PlayerListener(j, simulation, null);

        // We need a reference to the sender threads to interrupt
        // them when the game sate is updated.
        simulation.senderThreads[j] = new Thread(simulation.senders[j]);
        simulation.listenerThreads[j] = new Thread(simulation.listeners[j]);
      }


      // Wait for two clients to connect:

      Logger.println(Logger.RepetitionRate.FEW, "Waiting for clients to connect...");
      for (int j=0; j<2; ++j) {
        sockets[j] = servSocket.accept();
        Logger.println(Logger.RepetitionRate.FEW, "Player " + j + " connected.");

        // Don't buffer data before sending TCP packets.
        sockets[j].setTcpNoDelay(true);

        simulation.senders[j].setOutput(sockets[j].getOutputStream());
        simulation.listeners[j].setInput(sockets[j].getInputStream());

        // Start sender & listener threads.
        simulation.senderThreads[j].start();
        simulation.listenerThreads[j].start();
      }


      // Start simulating the game!
      simulation.startGame(clusterDbSayWithPET, maxSegmentNumber);

      // TODO: start the while(true) loop from the beginnig
      //       to server multiple games at a time.
      //
      //       However, should first implement a way to
      //       gracefully stop the threads for games that are
      //       finished. Also, some limitations that prevent
      //       overloading the server need to be in place.
      return;
    }
  }


}
