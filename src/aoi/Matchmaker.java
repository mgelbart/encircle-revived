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
import java.net.SocketException;
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
		OutputStream out;  
		
		GameClient(Socket s) throws IOException{
			socket = s;
			in = socket.getInputStream();
			out = socket.getOutputStream();
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
			      socket.setTcpNoDelay(true);
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
					
			for (GameClient c : clients) {
				if (c != client) {
					Logger.println(Rep.FREQ, "Found a client in queue to match with: " + c);

				    int matchId = randomGenerator.nextInt(Integer.MAX_VALUE);				    
					String msg = Protocol.matchMade("server", matchId);					
					boolean matchMade = true;
					
					try {

						Logger.println(Rep.FREQ, "Before writing to " + c);
						Logger.println(Rep.FREQ, "bound " + c.socket.isBound());
						Logger.println(Rep.FREQ, "Closed " + c.socket.isClosed());
						Logger.println(Rep.FREQ, "Connected " + c.socket.isConnected());
						Logger.println(Rep.FREQ, "InputShutdown " + c.socket.isInputShutdown());
						Logger.println(Rep.FREQ, "OutputShutdown " + c.socket.isOutputShutdown());
						
						c.out.write(msg.getBytes());
						c.out.flush();


						TODO TODO TODO 
						/* 
						 * 
						 
						  
						 There are two cases if the client has closed already:
						 1) Graceful close (like the nc (netcat) command on Ubuntu) - we 
						    have received a FIN after the write() above, so now when we
						    read, read will return -1
						 2) Ungraceful close (like the OpenBSD nc ported to Cygwin) - the write() above
						    will return a RESET, so read will throw a  
						    java.net.SocketException: Software caused connection abort: recv failed
						    
						Without an "ack",  
						we cannot distinguish 1) due to the client giving up and exiting a long time ago, from 
						1) due to the client closing communication with us due to the match being made. 
						So we wait for an "ack" from the client, with a timeout in case the client 
						has given up and exited earlier.
												
            c.socket.setSoTimeout(500);
            try {
              boolean success = c.in.read() == -1;
              TODO TODO TODO
            } catch (IOException ioe) {
              // unexpected
            }
						*/
						
						Logger.println(Rep.FREQ, "After writing");
						Logger.println(Rep.FREQ, "bound " + c.socket.isBound());
						Logger.println(Rep.FREQ, "Closed " + c.socket.isClosed());
						Logger.println(Rep.FREQ, "Connected " + c.socket.isConnected());
						Logger.println(Rep.FREQ, "InputShutdown " + c.socket.isInputShutdown());
						Logger.println(Rep.FREQ, "OutputShutdown " + c.socket.isOutputShutdown());
						
						Logger.println(Rep.FREQ, "Now trying a read");
						int i = c.in.read();
						Logger.println(Rep.FREQ, "i = " + i);
						
					} catch (Exception e) {
				        Logger.println(Rep.FEW, "Removing client from queue due to exception " + c);
				        e.printStackTrace();							
				        clients.remove(c);
				        matchMade = false;
					}
					
					// TODO - temporarily put a sleep here so we can test "client" dropping
					// a connection after we match it to "c" 
					
					if (matchMade) {
						try {
							client.out.write(msg.getBytes());
							client.out.flush();						
						} catch (Exception e) {
					        Logger.println(Rep.FEW, "That's odd - socket exception writing to a newly connected client " + c);
					        e.printStackTrace();
					        clients.remove(client);
					        matchMade = false;
						}
					}
					
					if (matchMade) {
					    Logger.println(Rep.FREQ, "Successfully matched new client " + client + " with queue head client " + c);
	
					    // Forget about these clients now that they are matched.
					    clients.remove(client);
					    clients.remove(c);
					}
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

