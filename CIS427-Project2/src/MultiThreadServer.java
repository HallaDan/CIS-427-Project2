import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public class MultiThreadServer {
    // list to store connected clients
    private static List<ClientHandler> clients = new ArrayList<>();

    public static void main(String[] args) {
        try {
            //create server socket
            ServerSocket serverSocket = new ServerSocket(8000);
            System.out.println("IndependentChatServer started.");

            while (true) {
                //accept inoming clients
                Socket clientSocket = serverSocket.accept();

                System.out.println("New client connected.");
                //handler for new client and start new thread for it
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                clients.add(clientHandler);
                new Thread(clientHandler).start();
            }
        } catch (IOException ex) {
            System.err.println(ex);
        }
    }

    static class ClientHandler implements Runnable {
        private Socket clientSocket;
        private DataInputStream fromClient;
        private DataOutputStream toClient;

        public ClientHandler(Socket clientSocket) {
            this.clientSocket = clientSocket;
            try {
                //input and output streams for the client
                fromClient = new DataInputStream(clientSocket.getInputStream());
                toClient = new DataOutputStream(
                        new BufferedOutputStream(clientSocket.getOutputStream()));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void run() {
            try {
                while (true) {
                    //read messages from client
                    String clientMessage = fromClient.readUTF();
                    System.out.println("Received from client: " + clientMessage);

                    //send the message back to the same client
                    toClient.writeUTF("Server: Received your message - " + clientMessage);
                    toClient.flush();
                }
            } catch (IOException e) {
                // Handle client disconnect
                System.out.println("Client disconnected.");
                clients.remove(this);
            }
        }
    }
}
