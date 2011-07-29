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

	GameServerManager gsm;
	Queue<GameClient> clients;

	// This class contains the data for one game client waiting to be matched.
	class GameClient {
		Socket socket;

		GameClient(Socket s) throws IOException {
			socket = s;
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
				servSocket = new ServerSocket(GameServer.TCPPORT);
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
					clients.add(client);
					checkForMatch(client);
				} catch (IOException e) {
					Logger.println(Rep.FEW, "Socket exception");
					e.printStackTrace();
					return;
				}
			}
		}

		void doneWithClient(GameClient c) {
			clients.remove(c);
			closeSocket(c.socket);
		}

		void checkForMatch(GameClient newClient) {

			// Remove clients that have dropped out from the client list.
			// We don't bother checking the new client because they have
			// just connected.
			for (GameClient c : clients) {
				if (c != newClient) {
					if (!acknowledgedMessage(c.socket, Protocol.ping())) {
						doneWithClient(c);
					}
				}
			}

			// Now look for a match.
			for (GameClient c : clients) {
				if (c != newClient) {
					Logger.println(Rep.FREQ, "Found a client in queue to match with: " + c);

					int matchId = randomGenerator.nextInt(Integer.MAX_VALUE);
					
					String server = null;
					boolean success = false;
					while (!success) {
						server = gsm.getNextGameServer();					
						success = gsm.notifyServerOfMatch(server, matchId);
						if (!success) {							
							try {
								Thread.sleep(100);  // wait a while so we don't thrash looking for a server  
							} catch (InterruptedException e) {
								Logger.println(Rep.FEW, "Sleep interrupted");
								e.printStackTrace();
								return;
							}
						}
					}
					
					String matchMadeMessage = Protocol.matchMade(server, matchId);
					success = acknowledgedMessage(c.socket, matchMadeMessage);
					doneWithClient(c);
					
					if (success) {
						success = acknowledgedMessage(newClient.socket, matchMadeMessage);
						if (success) {
							Logger.println(Rep.FREQ, "Successfully matched new client " + newClient + " with queue head client " + c);
						}
						doneWithClient(newClient);					
					}												
				}
			}

			for (GameClient c : clients) {
				Logger.println(Rep.FREQ, "Queue contains: " + c);
			}
		}
	}

	// This class maintains the list of game servers for load balancing.
	class GameServerManager {
		List<String> gameServers;
		int listPosition = 0;

		GameServerManager() {
			readListOfGameServers();
		}
		
		synchronized boolean notifyServerOfMatch(String server, int matchId) {
			boolean success = true;
			Socket s = null;
			
			try {
				s = new Socket(server, GameServer.MATCHMAKERCOMMPORT);			    
				success = acknowledgedMessage(s, Protocol.expectMatch(matchId, SimulationUtils.NUMPLAYERS)); 
			} catch (Exception e) {
				Logger.println(Rep.FEW, "Socket exception for server " + server + " at " + s.getRemoteSocketAddress());
				e.printStackTrace();
			}
			if (s != null)
				closeSocket(s);
			
			return success;			
		}
								
		
		synchronized String getNextGameServer() {
			// Round-robin load balancing
			String server = gameServers.get(listPosition);						
			listPosition++;
			if (listPosition >= gameServers.size()) 
				listPosition = 0;
			
			return server;
		}

		synchronized void readListOfGameServers() {
			// NOTE: In multi-threaded code we must use synchronized keyword
			// when iterating over the list. See
			// http://download.oracle.com/javase/6/docs/api/java/util/Collections.html#synchronizedList(java.util.List)
			gameServers = Collections.synchronizedList(new ArrayList<String>());

			Logger.println(Rep.FEW, "Reading game server list from stdin");
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					System.in));
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
				Logger.println(Rep.FEW, "Game server '" + server
						+ "' found in conf file");
			}
		}

	}
	
	// Send and wait for an "ack". 
	boolean acknowledgedMessage(Socket socket, String message) {
		
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

			// TODO Speed up: c.socket.setSoTimeout(750);
			socket.setSoTimeout(6500);
			String response = Protocol.readNextMsg(in);
			if ((response == null) || !Protocol.isAck(response)) {
				Logger.println(Rep.FEW,
						"Instead of ack, client response was: " + response);
				successful = false;
			}
		} catch (Exception e) {
			successful = false;
			Logger.println(Rep.FEW, "Socket exception for " + socket.getRemoteSocketAddress());
			e.printStackTrace();
		}

		return successful;
	}
	
	void closeSocket(Socket socket) {
		try {
			socket.close();
		} catch (Exception e) {
			Logger.println(Rep.FEW, "Socket exception for " + socket.getRemoteSocketAddress());
			e.printStackTrace();
		}
	}

	Matchmaker() {
		Logger.setVerbosity(Rep.FREQ);
		
		gsm = new GameServerManager();

		clients = new LinkedBlockingQueue<GameClient>();
		GameClientListener listener = new GameClientListener();
		Thread t = new Thread(listener, "GameClientListener");
		t.start();
	}

	public static void main(String argv[]) throws Exception {
		new Matchmaker();
	}
}
