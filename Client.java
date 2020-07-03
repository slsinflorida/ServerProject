import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    /** Store the server we will connect to */
    private static String host = "139.62.210.153";
//    private static String host = "127.0.0.1";

    /** Store the port we will use to connect */
    private static int port = 0;

    public static void main(String[] args) throws InterruptedException {
        //prompt for port and assign it
        if(args.length < 2){
            System.out.print("Enter port: ");
            Scanner portIn = new Scanner(System.in);
            Client.port = portIn.nextInt();
        } else{
            Client.host = args[0];
            Client.port = Integer.parseInt(args[1]);
        }

        // Only create client once. We don't need the client to every connect to the server,
        // since the connection handlers will do that for us.
        Client client = new Client();
        while (true){
            System.out.println(Client.getMenu()); // Print out main menu.

            Scanner input = new Scanner(System.in);
            int action = 0;
            do {
                System.out.print("Enter # of selection (1-7): ");
                action = input.nextInt();
            } while(action < 1 || action > 7);

            // If we enter 7 as the action, then we should only send one shutdown command to the server,
            // since after the first, the remaining won't finish.
            if(action == 7){
                client.createConnections(1, 7);
                break;
            }

            // keep asking number of clients to create
            int numClients = 0;
            do {
                System.out.print("Enter # of clients (min 1, max 60): ");
                numClients = input.nextInt();
            } while (numClients < 1 || numClients > 60);


            client.createConnections(numClients, action);

            // pause for 1 section, so the client handlers have a chance to finish running
            Thread.sleep(1000);
        } // end of loop
    }


    /**
     * This function handles creating all the clients after we ask the user how many clients, and what action.
     * It prints out an error message if it is unable to connect to the given server. That is the most common
     * error that Socket could throw when a new socket is being created.
     * @param numClients The number of clients to create
     * @param action The action to execute on every client
     */
    public void createConnections(int numClients, int action){
        System.out.println("Connecting to [" + host + ":" + port +"] with " + numClients + " client(s).");


        // For each client we want to make...
        for(int i = 0; i < numClients; i++){
            try {

                // Create a new socket and handler...
                Socket s = new Socket(host, port);
                ClientConnectionHandler cch = new ClientConnectionHandler(s, action);

                // Create a new thread for the handler and run it
                new Thread(cch).start();
            } catch (Exception e) {
                System.out.println("Unable to connect to server " + host + ":" + port);
            }
        }
    }

    /**
     * Construct the menu string and print it out!
     * @return the created menu string
     */
    public static String getMenu(){
        StringBuffer menu = new StringBuffer();
        menu.append("\n\nMenu of Actions\n");
        menu.append("---------------\n");
        menu.append("1. Date and time\n");
        menu.append("2. Uptime\n");
        menu.append("3. Memory use\n");
        menu.append("4. Netstat\n");
        menu.append("5. Current users\n");
        menu.append("6. Running process\n");
        menu.append("7. Quit\n");
        return menu.toString();
    }
}
//inner class to represent client threads
class ClientConnectionHandler implements Runnable {
    private Socket socket;
    private int action;
    private long start, end, total;
    public ClientConnectionHandler(Socket socket, int action) {
        this.socket = socket;
        this.action = action;

    }
    public void run() {
        try{
            //initialize input and output streams
            ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
            ObjectInputStream ois = new ObjectInputStream(socket.getInputStream());

            String act = String.valueOf(action);
            //write request to server across socket
            oos.writeObject(act);

            // ois.readObject is response object
            start = System.currentTimeMillis();
            String messageIn = (String) ois.readObject();
            end = System.currentTimeMillis();

            if(messageIn.equals("Goodbye"))
                return;

            messageIn = messageIn.trim();
            total = end - start; // timer

            System.out.println("Message Received: [\n\n" + messageIn.trim() + "\n\n]. - Total time for this request: " + total + "ms.");

            //clean up: close streams and socket when requests complete
            ois.close();
            oos.close();
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
