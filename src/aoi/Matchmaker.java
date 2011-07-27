package aoi;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.LinkedBlockingQueue;

import aoi.Logger.Rep;


/**
 * The matchmaking server. 
 */
public class Matchmaker {

	List<String> gameServers;
	Queue<GameClient> clients;

	// This class contains the data for one game client waiting to be matched.
	class GameClient {
		Socket socket;
		InputStream in;
		PrintStream out;  
		
		GameClient(Socket s) throws IOException{
			socket = s;
			in = socket.getInputStream();
			out = new PrintStream(socket.getOutputStream());
		}
		
		@Override
		public String toString() {
			return "GameClient " + socket.getRemoteSocketAddress();
		}
	}
	  		

	// This thread loops listening for new game clients, and matches them.
	class GameClientListener implements Runnable {
	    Random randomGenerator = new Random();

	    
		@Override
		public void run() {
			ServerSocket servSocket;
			try {
				servSocket = new ServerSocket(Server.TCPPORT);	
			}
		    catch (IOException e1) {
		        Logger.println(Rep.FEW, "Failed to init server socket.");
		        e1.printStackTrace();
		        return;
		    }
			
			while (true) {
			    try {
			      Socket socket = servSocket.accept();
			      Logger.println(Rep.FREQ, "Connected to client " + socket.getRemoteSocketAddress());
			      GameClient client = new GameClient(socket);
			      clients.add(client);
			      checkForMatch(client);
			    }
			    catch (IOException e) {
			        Logger.println(Rep.FEW, "Socket exception");
			        e.printStackTrace();
			        return;
			    }
			}
		}
		
		void checkForMatch(GameClient client) {

			// Starting from the head of the queue, look for another client
			for (GameClient c : clients) {
				if (c != client) {
					Logger.println(Rep.FREQ, "Found another client in Q: " + c);
					
				    int matchId = randomGenerator.nextInt();
				    
					
					matchMade <hostname> <matchID>\n
				}
			}

		}		
	}
		
	Matchmaker() {			
		Logger.setVerbosity(Rep.FREQ);

		readListOfGameServers();
		clients = new LinkedBlockingQueue<GameClient>();
		
	    GameClientListener listener = new GameClientListener(); 
	    Thread t = new Thread(listener, "GameClientListener");
	    t.start();		
	}

	void readListOfGameServers() {
		// NOTE: In multi-threaded code we must use synchronized keyword when iterating over the list.
		// See http://download.oracle.com/javase/6/docs/api/java/util/Collections.html#synchronizedList(java.util.List)
		gameServers = Collections.synchronizedList(new ArrayList<String>());

		Logger.println(Rep.FEW, "Reading game server list from stdin");
		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
		try {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.length() > 0)
					gameServers.add(line);
			}
		} catch (IOException e1) {
			Logger.println(Rep.FEW, "Couldn't read server conf file.");
			e1.printStackTrace();
			return;
		}	
		
		if (gameServers.size() < 1) {
			throw new RuntimeException("No game server names found");
		}
		
		synchronized(gameServers) {
			for (String server : gameServers) {
				Logger.println(Rep.FEW, "Game server '" + server + "' found in conf file");
			}
		}
	}
	  
	public static void main(String argv[]) throws Exception {
		Matchmaker m = new Matchmaker(); 
	}
}

