import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    //input stream to receive messages from server
    private static DataInputStream fromServer;
    //output stream to send messages to server
    private static DataOutputStream toServer;
    // flag to control prompt for input
    private static boolean continuePrompting = true;

    public static void main(String[] args) {
        try {
            // connect to server on 'localhost'
            // will change so that the user can
            // enter their own IP

            Scanner scanner = new Scanner(System.in);
            System.out.println("Enter IP Address: ");
            String IP = scanner.nextLine();

            Socket socket = new Socket(IP, 8306);

            //initialize the input and output streams for comms with server
            fromServer = new DataInputStream(socket.getInputStream());
            toServer = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

            //thread to continuously receive messages from the server
            new Thread(() -> {
                try {
                    while (true) {
                        //read message from server
                        String serverMessage = fromServer.readUTF();
                        //System.out.println("\n" + serverMessage);

                        //require no response if server response indicates client should stop prompting
                        if (serverMessage.startsWith("200 OK\nClient quit\nEND\n") || serverMessage.startsWith("200 OK\nServer shutting down\nEND\n")) {
                            System.out.println("\n200 OK\n");
                            continuePrompting = false;
                            break;
                        }

                        if (continuePrompting) {
                            System.out.println("\n" + serverMessage);

                            System.out.print("\nSend to server: ");
                            String clientMessage = scanner.nextLine();

                            // send the message to the server
                            toServer.writeUTF(clientMessage);
                            toServer.flush();
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

            //capture client message
            System.out.print("\nSend to server: ");
            String clientMessage = scanner.nextLine();

            // send the message to the server
            toServer.writeUTF(clientMessage);
            toServer.flush();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}

