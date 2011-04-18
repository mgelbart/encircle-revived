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
    public static final int SLEEPPERSTEP = 100;

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
	ServerSocket player2SenderServSocket = new ServerSocket(7604);

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
		SConGameStateSender player1Sender = new SConGameStateSender(1, simulation, new DataOutputStream(player1SenderSocket.getOutputStream()));

		SConPlayerListener player1Listener = new SConPlayerListener(1, simulation, new DataInputStream(player1ListenerSocket.getInputStream()));

		SConGameStateSender player2Sender = new SConGameStateSender(2, simulation, new DataOutputStream(player2SenderSocket.getOutputStream()));

		SConPlayerListener player2Listener = new SConPlayerListener(2, simulation, new DataInputStream(player2ListenerSocket.getInputStream()));

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

	player1State = new SConPlayerState(1, -50, 0, 0, 0);
	player2State = new SConPlayerState(2,  50, 0, 0, 0);

	player1StateInt = new SConPlayerStateInternal();
	player2StateInt = new SConPlayerStateInternal();

	simulationTime = 0;
    }

    private void startGame() {
	SConPlayerState p1State;
	SConPlayerState p2State;
	SConPlayerControlParameters p1Params;
	SConPlayerControlParameters p2Params;

	int dir1, dir2, vx, vy, x1, x2, y1, y2;
	double w, a;

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


	    // Calculate the new positions.

	    w = MAXW * (p1Params.angularSpeed / (double) INTMAX);
	    dir1 = player1State.direction + (int) w; // built-in mod (INTMAX-INTMIN)

	    if (Math.abs(dir1 - p1Params.targetDirection) > Math.abs(player1State.direction - p1Params.targetDirection))
		// only change the direction if it gets closer to the target.
		dir1 = player1State.direction;

	    a = Math.max((double) 0.0, MAXACC * (p1Params.acceleration / (double) INTMAX));
	    vx = player1StateInt.vx + (int) (((double) a) * Math.cos(Math.PI * (player1State.direction / (double) INTMAX)));
	    vy = player1StateInt.vy + (int) (((double) a) * Math.sin(Math.PI * (player1State.direction / (double) INTMAX)));

	    // This target speed thing is probably a bit stupid...
	    //if (Math.abs(Math.sqrt(Math.pow(vx,2) + Math.pow(vy,2)) - p1Params.targetSpeed)
	    //  < Math.abs(Math.sqrt(Math.pow(player1StateInt.vx,2) + Math.pow(player1StateInt.vy,2)) - p1Params.targetSpeed)) {
		player1StateInt.vx = vx; 
		player1StateInt.vy = vy;
	    //}

	    x1 = player1State.x + player1StateInt.vx;
	    y1 = player1State.y + player1StateInt.vy;


	    // Repeat for player 2... (a bit ugly)
	    w = MAXW * (p2Params.angularSpeed / (double) INTMAX);
	    dir2 = player2State.direction + (int) w; // built-in mod (INTMAX-INTMIN)

	    if (Math.abs(dir2 - p2Params.targetDirection) > Math.abs(player2State.direction - p2Params.targetDirection))
		// only change the direction if it gets closer to the target.
		dir2 = player2State.direction;

	    a = Math.max((double) 0.0, MAXACC * (p2Params.acceleration / (double) INTMAX));
	    vx = player2StateInt.vx + (int) (((double) a) * Math.cos(Math.PI * (player2State.direction / (double) INTMAX)));
	    vy = player2StateInt.vy + (int) (((double) a) * Math.sin(Math.PI * (player2State.direction / (double) INTMAX)));

	    // This target speed thing is probably a bit stupid...
	    //if (Math.abs(Math.sqrt(Math.pow(vx,2) + Math.pow(vy,2)) - p2Params.targetSpeed)
	    //  < Math.abs(Math.sqrt(Math.pow(player2StateInt.vx,2) + Math.pow(player2StateInt.vy,2)) - p2Params.targetSpeed)) {
		player2StateInt.vx = vx; 
		player2StateInt.vy = vy;
	    //}

	    x2 = player2State.x + player2StateInt.vx;
	    y2 = player2State.y + player2StateInt.vy;



	    p1State = new SConPlayerState(1, x1, y1, dir1, 0);
	    p2State = new SConPlayerState(1, x2, y2, dir2, 0);

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
