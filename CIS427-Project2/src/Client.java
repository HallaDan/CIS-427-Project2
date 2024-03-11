import java.io.*;
import java.net.Socket;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
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
            Socket socket = new Socket("localhost", 8000);

            //initialize the input and output streams for comms with server
            fromServer = new DataInputStream(socket.getInputStream());
            toServer = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

            //thread to continuously receive messages from the server
            new Thread(() -> {
                try {
                    while (true) {
                        //read message from server
                        String serverMessage = fromServer.readUTF();
                        System.out.println("Received from server: " + serverMessage);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();
            //read input from user keyboard.. (typing message)
            Scanner scanner = new Scanner(System.in);

            while (true) {
                // prompt to send message
                System.out.print("Enter a message to send to the server: ");
                String clientMessage = scanner.nextLine();

                // send the message to the server
                toServer.writeUTF(clientMessage);
                toServer.flush();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}

