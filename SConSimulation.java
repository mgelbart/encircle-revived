import java.io.*;
import java.net.*;

class SConSimulation
{

    public static final int INTMAX = 2147483647;
    public static final int INTMIN = -2147483648;

    // Maximum angular speed for turning the ship (abs. value).
    // Angle is measured in unit of 2*pi / (|INTMAX| + |INTMIN|).
    public static final int MAXW = 536870662;

    // Maximum acceleration.
    // Positions can range between INTMIN and INTMAX.
    public static final int MAXACC = 10;

    // The amount of time the simulation thread sleeps between each
    // step in milliseconds.
    public static final int SLEEPPERSTEP = 1000;

    /****
     Public fields used to communicate with PlayerActionListener
     and GameStateSender threads.
     ****/
    public SConPlayerControlParameters player1CtrlParams;
    public SConPlayerControlParameters player2CtrlParams;

    public int simulationTime;
    public SConPlayerState player1State;
    public SConPlayerState player2State;

    private SConPlayerStateInternal player1StateInt;
    private SConPlayerStateInternal player2StateInt;



    private Thread player1SenderThread;
    private Thread player2SenderThread;
    private Thread player1ListenerThread;
    private Thread player2ListenerThread;



    public static void main(String argv[]) throws Exception
    {

	ServerSocket player1ListenerServSocket = new ServerSocket(7601);
	ServerSocket player2ListenerServSocket = new ServerSocket(7603);

	ServerSocket player1SenderServSocket = new ServerSocket(7602);
	ServerSocket player2SenderServSocket = new ServerSocket(7603);

	SConSimulation simulation = new SConSimulation();

	while(true)
	    {
		// Wait for clients to connect to all four ports.
		Socket player1ListenerSocket = player1ListenerServSocket.accept();
		Socket player2ListenerSocket = player2ListenerServSocket.accept();
		Socket player1SenderSocket = player1SenderServSocket.accept();
		Socket player2SenderSocket = player2SenderServSocket.accept();


		simulation.initGameState();


		// Create the threads for communication with the clients
		SConGameStateSender player1Sender = new SConGameStateSender();
		player1Sender.setClientStream(new DataOutputStream(player1SenderSocket.getOutputStream()));
		player1Sender.setGame(simulation);

		SConPlayerListener player1Listener = new SConPlayerListener();
		player1Listener.setClientStream(new DataInputStream(player1ListenerSocket.getInputStream()));
		player1Listener.setGame(simulation);
		player1Listener.setPlayerNo(1);

		SConGameStateSender player2Sender = new SConGameStateSender();
		player2Sender.setClientStream(new DataOutputStream(player2SenderSocket.getOutputStream()));
		player2Sender.setGame(simulation);

		SConPlayerListener player2Listener = new SConPlayerListener();
		player2Listener.setClientStream(new DataInputStream(player2ListenerSocket.getInputStream()));
		player2Listener.setGame(simulation);
		player2Listener.setPlayerNo(2);

		// Start the threads

		// We need a reference to the sender threads to interrupt
		// them when the game sate is updated.
		simulation.player1SenderThread = new Thread(player1Sender);
		simulation.player2SenderThread = new Thread(player2Sender);
		simulation.player1SenderThread.start();
		simulation.player2SenderThread.start();

		simulation.player1ListenerThread = new Thread(player1Listener);
		simulation.player2ListenerThread = new Thread(player2Listener);
		simulation.player1ListenerThread.start();
		simulation.player2ListenerThread.start();

		// Start simulating the game!
		simulation.startGame();
	    }
    }

    private void initGameState() {
	player1CtrlParams = new SConPlayerControlParameters();
	player2CtrlParams = new SConPlayerControlParameters();

	player1State = new SConPlayerState();
	player2State = new SConPlayerState();

	player1StateInt = new SConPlayerStateInternal();
	player2StateInt = new SConPlayerStateInternal();

	player1State.x = -10000;
	player2State.x =  10000;

	simulationTime = 0;
    }

    private void startGame() {
	SConPlayerState p1State;
	SConPlayerState p2State;
	SConPlayerControlParameters p1Params;
	SConPlayerControlParameters p2Params;

	int w, dir, a, vx, vy;

	while (true) {

	    // Calculate the new positions of players.
	    try {
		Thread.sleep(SLEEPPERSTEP);
	    } catch (InterruptedException e) {
		return;
	    }


	    synchronized (player1CtrlParams) {
		p1Params = player1CtrlParams.clone();
	    }

	    synchronized (player2CtrlParams) {
		p2Params = player2CtrlParams.clone();
	    }


	    // Don't need locking here because no other thread updates
	    // the game state
	    p1State = player1State.clone();
	    p2State = player2State.clone();


	    // Calculate the new positions.

	    w = Math.max(Math.min(p1Params.angularSpeed, MAXW), -MAXW);
	    dir = p1State.direction + w; // built-in mod (INTMAX-INTMIN)

	    if (Math.abs((long)dir - p1Params.targetDirection) < Math.abs((long)(p1State.direction) - p1Params.targetDirection))
		// only change the direction if it gets closer to the target.
		p1State.direction = dir;

	    a = Math.max(0, MAXACC * (p1Params.acceleration / INTMAX));
	    vx = player1StateInt.vx + a * (int) Math.cos(Math.PI * p1State.direction/INTMAX);
	    vy = player1StateInt.vy + a * (int) Math.sin(Math.PI * p1State.direction/INTMAX);

	    if (Math.abs(Math.sqrt(Math.pow(vx,2) + Math.pow(vy,2)) - p1Params.targetSpeed)
		< Math.abs(Math.sqrt(Math.pow(player1StateInt.vx,2) + Math.pow(player1StateInt.vy,2)) - p1Params.targetSpeed)) {
		player1StateInt.vx = vx; 
		player1StateInt.vy = vy;
	    }

	    p1State.x += player1StateInt.vx;
	    p1State.y += player1StateInt.vy;


	    // Repeat for player 2... (a bit ugly)
	    w = Math.max(Math.min(p2Params.angularSpeed, MAXW), -MAXW);
	    dir = p2State.direction + w; // built-in mod (INTMAX-INTMIN)

	    if (Math.abs((long)dir - p2Params.targetDirection) < Math.abs((long)(p2State.direction) - p2Params.targetDirection))
		// only change the direction if it gets closer to the target.
		p2State.direction = dir;

	    a = Math.max(0, MAXACC * (p2Params.acceleration / INTMAX));
	    vx = player2StateInt.vx + a * (int) Math.cos(Math.PI * p2State.direction/INTMAX);
	    vy = player2StateInt.vy + a * (int) Math.sin(Math.PI * p2State.direction/INTMAX);

	    if (Math.abs(Math.sqrt(Math.pow(vx,2) + Math.pow(vy,2)) - p2Params.targetSpeed)
		< Math.abs(Math.sqrt(Math.pow(player2StateInt.vx,2) + Math.pow(player2StateInt.vy,2)) - p2Params.targetSpeed)) {
		player2StateInt.vx = vx; 
		player2StateInt.vy = vy;
	    }

	    p2State.x += player2StateInt.vx;
	    p2State.y += player2StateInt.vy;


	    // Update the game state.
	    //
	    // We (arbitrarily) choose player1State as the object
	    // to lock when reading the game state data.
	    synchronized (player1State) {
		simulationTime++;
		player1State = p1State;
		player2State = p2State;
	    }

	    // Interrupt the sender threads to let them
	    // know the state has changed.
	    player1SenderThread.interrupt();
	    player2SenderThread.interrupt();
	}
    }
}
