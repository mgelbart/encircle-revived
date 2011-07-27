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
import java.util.concurrent.LinkedBlockingQueue;

import aoi.Logger.Rep;


/**
 * The matchmaking server. 
 */
public class Matchmaker {

	List<String> gameServers;
	Queue<GameClient> clients;

	// This class contains the data for one game client.
	class GameClient {
		Socket socket; 
		String username;
		Protocol.ClientState state; 
		
		GameClient(Socket s) {
			socket = s;
			state = null;			
		}
		
		@Override
		public String toString() {
			String s = "GameClient addr " + socket.getRemoteSocketAddress() + " state " + state;
			if (username != null) 
				s += " " + username;
			return s;
		}
	}
	
	// This thread manages interaction with one game client.
	class GameClientManager implements Runnable {
		GameClient client;
		
		GameClientManager(GameClient c) {
			client = c;
		}
		
		void checkForMatch() {
        	Logger.println(Rep.FREQ, "Head of Q: " + clients.peek());

        	// Do we have a match?
        	for (GameClient c: clients) {
        		Logger.println(Rep.FREQ, c);
        		
        		if ((c != client) && (c.state == Protocol.ClientState.REQUESTEDMATCH)) {
        			        			  	
        		}
        	}
        	
		}

		@Override
		public void run() {
			Logger.println(Rep.FREQ, "In thread");
			
			try {			
		        InputStream in = client.socket.getInputStream();
		        PrintStream out = new PrintStream(client.socket.getOutputStream());

		        while (true) {
		        	String s = Protocol.readNextMsg(in);
		        	Logger.println(Rep.FREQ, "Got message: '" + s + "'");
		        	
		        	String username = Protocol.parseRequestMatch(s);
		        	if (username != null) {
		        		Logger.println(Rep.FREQ, "Match requested by username: '" + username + "'");
		        		client.username = username;
		        		client.state = Protocol.ClientState.REQUESTEDMATCH;
		        		
		        		checkForMatch();
		        	}		        	
		        			        	
		        }
			} 
		    catch (IOException e) {
		        Logger.println(Rep.FEW, "Socket exception");
		        e.printStackTrace();
		        return;
		    }	        
		}
		
	}

	// This thread loops listening for new game clients.
	class GameClientListener implements Runnable {
		@Override
		public void run() {
			ServerSocket servSocket;
			try {
				servSocket = new ServerSocket(Server.TCPPORT);	
			}
		    catch (IOException e1) {
		        Logger.println(Rep.FEW, "Failed to init sockets.");
		        e1.printStackTrace();
		        return;
		    }
			
			while (true) {
			    try {
			      Socket socket = servSocket.accept();
			      Logger.println(Rep.FREQ, "Connected to client");
			      GameClient client = new GameClient(socket);
			      clients.add(client);
			      GameClientManager manager = new GameClientManager(client); 
			      Thread t = new Thread(manager, "GameClientManager");
			      t.start();			      			     
			    }
			    catch (IOException e) {
			        Logger.println(Rep.FEW, "Socket exception");
			        e.printStackTrace();
			        return;
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

