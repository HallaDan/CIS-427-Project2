import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    //input stream to receive messages from server
    private static DataInputStream fromServer;
    //output stream to send messages to server
    private static DataOutputStream toServer;

    public static void main(String[] args) {
        try {
            // connect to server on 'localhost'
            // will change so that the user can
            // enter their own IP
            Scanner scanner = new Scanner(System.in);
            /*System.out.println("Enter IP Address: ");
            String IP = scanner.nextLine();
            */
            Socket socket = new Socket("localhost", 8306);

            //initialize the input and output streams for comms with server
            fromServer = new DataInputStream(socket.getInputStream());
            toServer = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

            //thread to continuously receive messages from the server
            new Thread(() -> {
                try {
                    while (true) {
                        //read message from server
                        String serverMessage = fromServer.readUTF();
                        System.out.println("\nReceived from server: " + serverMessage);

                        System.out.print("\nSend to server: ");
                        String clientMessage = scanner.nextLine();

                        // send the message to the server
                        toServer.writeUTF(clientMessage);
                        toServer.flush();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

            System.out.print("\nSend to server: ");
            String clientMessage = scanner.nextLine();

            // send the message to the server
            toServer.writeUTF(clientMessage);
            toServer.flush();

            /*
            //read input from user keyboard.. (typing message)
            while (true) {
                // prompt to send message
                System.out.print("\nEnter a message to send to the server: ");
                String clientMessage = scanner.nextLine();

                // send the message to the server
                toServer.writeUTF(clientMessage);
                toServer.flush();
            }*/
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}

