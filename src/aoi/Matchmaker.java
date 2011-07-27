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
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import aoi.Logger.Rep;


/**
 * The main class run by the matchmaking server. 
 */
public class Matchmaker {
	

	class GameClient {
		Socket socket; 
		String username;
		Protocol.ClientState state; 
		
		GameClient(Socket s) {
			socket = s;
			state = null;
		}
	}
	
	class GameClientManager implements Runnable {
		GameClient client;
		
		GameClientManager(GameClient c) {
			client = c;
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
		        	}
		        	
		        	out.print(Protocol.ack());
		        }
			} 
		    catch (IOException e) {
		        Logger.println(Rep.FEW, "Socket exception");
		        e.printStackTrace();
		        return;
		    }	        
		}
		
	}

	List<String> gameServers;
	Queue<GameClient> clients;
	
	Matchmaker() {			
		readListOfGameServers();
		clients = new LinkedList<GameClient>();
		Logger.setVerbosity(Rep.FREQ);
		
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
		      GameClientManager manager = new GameClientManager(client); 
		      Thread t = new Thread(manager, "manageGameClient");
		      t.start();
		    }
		    catch (IOException e) {
		        Logger.println(Rep.FEW, "Socket exception");
		        e.printStackTrace();
		        return;
		    }
		}
	    
	}

	void readListOfGameServers() {
		gameServers = new ArrayList<String>();

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
		
		for (String server : gameServers) {
			Logger.println(Rep.FEW, "Game server '" + server + "' found in conf file");
		}
	}
	  
	public static void main(String argv[]) throws Exception {
		Matchmaker m = new Matchmaker(); 
	}
}

