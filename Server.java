import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Scanner;

public class Server {
    /** This is the server itself. It is the part of the program that can accept and reject connections */
    private ServerSocket server;

    /** Variable to track if the main loop is running or not */
    private boolean running;

    /** Track total time spent processing each message */
    private long totalMessageTime, totalRoundtripTime;

    /** Track total connections made */
    private int totalConnectionsMade;

    /**
     * Server constructor. We throw IOException so we can not have to worry
     * about exception handling in the exception. We take in the server port and
     * @throws IOException in order to not worry about aceptions with the SocketServer being initialized.
     */
    public Server(int port) throws IOException {
        // use this so we know we are referring to this classes server variable
        this.server = new ServerSocket(port);
        this.running = true;
    }

    /**
     * The "run" function leads to an infinite loop function which waits for a socket connection.
     * Once a socket connection has been received, a new thread is created to handle that connection, as to
     * not block the infinite loop from accepting more connections.
     */
    public void run() {

        // While the program is running
        while (this.running) {
            try {

                // Messages around are so we know exactly when waiting vs processing
                System.out.print("Waiting for client message...");
                Socket socket = server.accept(); // Accept a socket connection
                System.out.println("New connection made!");

                // increment connections made
                this.totalConnectionsMade += 1;

                // Start a new thread of the handler class to deal with the new socket
                new Thread(new ConnectionHandler(socket, this)).start();
            } catch (IOException e) {
                System.out.println("Server shutting down.");
            }
        }

        // If we reach here, the server has shutdown, and we are done accepting connecitons.
        StringBuilder out = new StringBuilder();
        out.append("\n\nServer Running Information\n");
        out.append("==========================\n");

        out.append("Total Connections: ");
        out.append(this.totalConnectionsMade);
        out.append("\n");

        out.append("Total Average Message Response Time: ");
        out.append(this.totalMessageTime / this.totalConnectionsMade);
        out.append("ms\n");


        out.append("Total Round Trip Message Response Time: ");
        out.append(this.totalRoundtripTime / this.totalConnectionsMade);
        out.append("ms\n");

        out.append("Goodbye!");
        System.out.println(out.toString());
    }

    /**
     * addMessageTime will add the long a specific request too for a single client, to the total number
     * of requests made in a single server instance. We make this function synchronized, since many threads
     * many be accessing this function, and we don't want the totalMessageTime variable to be set to an
     * incorrect value because two threads are running this function at the same time. That keywords
     * makes this function blocking until the current thread that is running it, completes running it.
     *
     * addRoundtripTime acts similarly, except waits until we respond to the request as-well.
     * @param time Time amount of time to add to the given variable
     */
    public synchronized void addMessageTime(long time){
        this.totalMessageTime += time;
    }

    public synchronized void addRoundtripTime(long time){
        this.totalRoundtripTime += time;
    }

    /**
     * Tell the server to stop running. Use when we receive action 7.
     */
    public synchronized void stopRunning(){
        try {
            this.running = false;
            this.server.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * The Main Function. This is completely separate from the Server class. We don't call this
     * function. We throw exception in the function so we don't need to worry
     * about exceptions in the main function.
     */
    public static void main(String[] args) throws Exception{
        // If we have arguments, make sure we only have one. If we have more than one,
        // tell the user how to type in the arguments, then quit.
        if (args.length > 1){
            System.out.println("Usage: NetworkTiming <port>");
            System.exit(0);
        }

        // create port variable to pass into the server class
        int port = 0;

        // decide if we should take port from input, or ask the user to type in the port
        if(args.length == 1){
            port = Integer.parseInt(args[0]);
        } else {
            Scanner portIn = new Scanner(System.in);
            System.out.print("Enter port: ");
            port = portIn.nextInt();
            portIn.close(); // we don't need this anymore.
        }
        System.out.println("Starting server on port #" + port);
        Server server = new Server(port); // start the server
        server.run();  // start server loop

    }

}

class ConnectionHandler implements Runnable {
    // both are final because they are never changed
    private final Socket socket; // the socket for the current connection
    private final Server parent; // the parent who created this handler

    /**
     * Set the class's socket to the socket we are handling, and start the thread within this class to handle it.
     * We accept the server object so we can have a single variable which holds the time
     * @param socket The connection to the user that we are handling in this loop
     * @param server The server object which instantiated this instance of ConnectionHandler
     */
    public ConnectionHandler(Socket socket, Server server) {
        this.socket = socket;
        this.parent = server;
    }

    /**
     * Since this class "implements Runnable", we need to define "run". This function will let us
     * use this class in a thread.
     */
    public void run() {
        // Most of this class is surrounded by try-catch since many function that interact with socket
        // will throw an IOException from the
        try {
            // initialize input and output streams so we can send and receive data from the client.
            ObjectInputStream inS = new ObjectInputStream(socket.getInputStream());
            ObjectOutputStream outS = new ObjectOutputStream(socket.getOutputStream());

            //keep reading until choice to exit is received
            long start, end;

            // receive the message
            String messageIn = (String) inS.readObject();

            // get the the start time after we read the object as the user might spend a lot
            // of time deciding what to pick
            start = System.currentTimeMillis();

            int actionValue = Integer.parseInt(messageIn); // Convert response to integer

            // determine which process to run by putting each command in a string array, with
            // the index of each command matching up with the action value for that command.
            String[] commands = {"", "date", "uptime", "free", "netstat", "w", "ps -ag", ""};

            // If we get exit value, then exit.
            if(actionValue == 7){
                outS.writeObject("Goodbye");

                // tell the server to top running
                this.parent.stopRunning();

                // clean up early
                inS.close();
                outS.close();
                socket.close();
                return;
            }

            Process process = Runtime.getRuntime().exec(commands[actionValue]);

            // At this point, we have the message, and will not track how long it took to execute.
            end = System.currentTimeMillis();
            this.parent.addMessageTime(end - start);


            // Make sure process is not null, and then do the conversions once here.
            assert process != null;
            process.waitFor();

            // InputStream variable is unnecessary. We never used it directly, and only pass it into
            // the InputStreamReader.
            BufferedReader stdOut = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder out = new StringBuilder();

            // Convert to for loop for-loop. It's a little more readable, and keeps all the variables together.
            // "For every line in stdOut.readLine, set it equal to S, and as long as S is not equal to null,
            // append S and a newline to the final string, and then get the next line"
            for(String s = stdOut.readLine(); s != null; s = stdOut.readLine()){
                out.append(s);
                out.append("\n");
            }

            process.destroy();

            // Send a response information to the client application
            outS.writeObject("From server: " + out.toString());
            end = System.currentTimeMillis();
            this.parent.addRoundtripTime(end - start);

            //clean up: close streams and socket when requests complete
            inS.close();
            outS.close();
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }



}
