package aoi;

import java.io.*;
import java.net.*;

import aoi.Logger.Rep;

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

  public static void main(String argv[]) {

    // Parse input options:
    BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

    boolean generateNewMap = true;
    int maxSegmentNumber = -1;
    int[] numPerNum = null;
    
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

    ServerSocket servSocket;
    UnreliableStreamServer udpServ;
    
    try {
      servSocket = new ServerSocket(TCPPORT);
      udpServ = new UnreliableStreamServer(UDPPORT);
      udpServ.start();
    } catch (IOException e1) {
      Logger.println(Rep.FEW, "Failed to init sockets.");
      e1.printStackTrace();
      return;
    }

	  // (This can be moved later) Initialize ClusterDatabase;
	  ClusterDatabase clusterDbSayWithPET;
    try {
      clusterDbSayWithPET = new ClusterDatabase(CLUSTER_DATASET);
    } catch (FileNotFoundException e1) {
      Logger.println(Rep.FEW, "Failed to init cluster DB.");
      e1.printStackTrace();
      return;
    }

	  int matchId = 0;
    while (true) {

      Arena arena;

      // Generate/load the arena:
      if (generateNewMap) {

        // For now, two of each nSegments from 1 to 7
//        simulation.clusters = clusterDbSayWithPET.generateArenaClusters(numPerNum);
        
        try {
          arena = clusterDbSayWithPET.generateArena(numPerNum);
        } catch (FileNotFoundException e1) {
          Logger.println(Rep.FEW, "Failed to generate map.");
          e1.printStackTrace();
          return;
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

      Simulation simulation = new Simulation(matchId, arena, clusterDbSayWithPET, maxSegmentNumber);

      simulation.initGameState();

      // Establish client connections.
      
      // This helper class sets the Communicator as a receiver of UDP packets as soon
      // as an UnreliableStream to the client is established.
      class SetUDPIO implements UnreliableStreamServer.ConnectionListener {
        Communicator communicator;
        SetUDPIO(Communicator communicator) { this.communicator = communicator; }
        
        @Override
        public void established(UnreliableStream stream) { stream.registerReceiver(communicator); communicator.setUDPOutput(stream); }
      }

      // Prepare to accept a UDP connection.
      for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j) {
        // Assign an ID number for the UDP stream of the client.
        short id = udpServ.expectConnection();
        
        simulation.communicators[j] = new Communicator(j, simulation, id);

        // set the PlayerListener as a receiver of UDP packets and set GameStateSender to
        // use UDP as soon as a connection is established.
        udpServ.registerConnectionListener(id, new SetUDPIO(simulation.communicators[j]));
      }


      // Wait for two clients to connect:
      Logger.println(Logger.Rep.FEW, "Waiting for clients to connect...");
      
      Socket[] sockets = new Socket[SimulationUtils.NUMPLAYERS];
      try {
        for (int j=0; j<SimulationUtils.NUMPLAYERS; ++j) {
          sockets[j] = servSocket.accept();
          Logger.println(Logger.Rep.FEW, "Player " + j + " connected.");
          simulation.communicators[j].setTCPSocket(sockets[j]);
        }
      } catch (IOException e) {
        Logger.println(Rep.FEW, "Failed to accept client connection.");
        e.printStackTrace();
        return;
      }


      // Start simulating the game!
      Thread t = new Thread(simulation, "simulation-"+matchId);
      t.start();

      ++matchId;
    }
  }


}
