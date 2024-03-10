import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class Client {
    private static DataInputStream fromServer;
    private static DataOutputStream toServer;

    public static void main(String[] args) {
        try {
            Socket socket = new Socket("localhost", 8000);
            fromServer = new DataInputStream(socket.getInputStream());
            toServer = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));

            new Thread(() -> {
                try {
                    while (true) {
                        String serverMessage = fromServer.readUTF();
                        System.out.println("Received from server: " + serverMessage);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }).start();

            Scanner scanner = new Scanner(System.in);

            while (true) {
                System.out.print("Enter a message to send to the server: ");
                String clientMessage = scanner.nextLine();

                toServer.writeUTF(clientMessage);
                toServer.flush();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }
}
