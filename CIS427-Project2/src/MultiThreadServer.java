import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.*;
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
            // Initialize database tables and users
            createDBTables();
            initializeUsers();

            while (true) {
                //accept incoming clients
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
        private User loggedInUser = null; // Track the logged-in user

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

        private String processClientMessage(String message) {
            String[] tokens = message.split("\\s+"); // Split by space, but limit to 3 parts in case of data fields containing spaces
            String command = tokens[0].toUpperCase();

            switch (command) {
                case "LOGIN":
                    return handleLogin(tokens);
                case "LOGOUT":
                    return handleLogout(tokens);
                case "BUY":
                    return handleBuy(tokens);
                case "SELL":
                    return handleSell(tokens);
                case "LIST":
                    return handleList(tokens);
                case "DEPOSIT":
                    return handleDeposit(tokens);
                case "BALANCE":
                    return handleBalance(tokens);
                case "WHO":
                    return handleWho(tokens);
                case "LOOKUP":
                    return handleLookup(tokens);
                case "QUIT":
                    return handleQuit(tokens);
                case "SHUTDOWN":
                    return handleShutdown(tokens);
                //TEST
                case "CHECK":
                String dbContents = printDatabase();
                return dbContents + "END OF CHECK TEST\n";
                //end of TEST
                default:
                    return "400 Bad Request\nUnknown command\nEND\n";
            }
        }

// Placeholder methods for handling each command
// You need to implement these methods based on your assignment requirements


        private String handleLogin(String[] tokens) {
            if (tokens.length != 3) return "400 Bad Request\nIncorrect login syntax.\nEND\n";
            String username = tokens[1];
            String password = tokens[2];

            try (Connection conn = MultiThreadServer.getDBConnection();
                 PreparedStatement pstmt = conn.prepareStatement("SELECT * FROM Users WHERE user_name = ? AND password = ?")) {
                pstmt.setString(1, username);
                pstmt.setString(2, password);

                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        this.loggedInUser = new User(rs.getString("ID"), username, password, rs.getDouble("usd_balance")); // Assuming a matching constructor in User class
                        return "200 OK\nUser logged in\nEND\n";
                    }
                }
                return "403 Forbidden\nWrong username or password\nEND\n";
            } catch (SQLException e) {
                e.printStackTrace();
                return "500 Internal Server Error\nDatabase error\nEND\n";
            }
        }

        private String handleLogout(String[] tokens) {
            if (loggedInUser == null) return "403 Forbidden\nNot logged in\nEND\n";
            loggedInUser = null;
            return "200 OK\nUser logged out\nEND\n";
        }

        private void updateStocks(String userId, String stockSymbol, double stockAmount, Connection conn) throws SQLException {
            // Check if the stock record exists
            boolean exists = false;
            try (PreparedStatement checkStmt = conn.prepareStatement("SELECT * FROM Stocks WHERE user_id = ? AND stock_symbol = ?")) {
                checkStmt.setString(1, userId);
                checkStmt.setString(2, stockSymbol);
                try (ResultSet rs = checkStmt.executeQuery()) {
                    exists = rs.next();
                }
            }

            if (exists) {
                // Update existing stock record
                try (PreparedStatement updateStmt = conn.prepareStatement("UPDATE Stocks SET stock_balance = stock_balance + ? WHERE user_id = ? AND stock_symbol = ?")) {
                    updateStmt.setDouble(1, stockAmount);
                    updateStmt.setString(2, userId);
                    updateStmt.setString(3, stockSymbol);
                    updateStmt.executeUpdate();
                }
            } else {
                // Insert new stock record
                try (PreparedStatement insertStmt = conn.prepareStatement("INSERT INTO Stocks (user_id, stock_symbol, stock_balance) VALUES (?, ?, ?)")) {
                    insertStmt.setString(1, userId);
                    insertStmt.setString(2, stockSymbol);
                    insertStmt.setDouble(3, stockAmount);
                    insertStmt.executeUpdate();
                }
            }
        }


        private void updateBalance(String userId, double amount, Connection conn) throws SQLException {
            try (PreparedStatement stmt = conn.prepareStatement("UPDATE Users SET usd_balance = usd_balance + ? WHERE ID = ?")) {
                stmt.setDouble(1, amount);
                stmt.setString(2, userId);
                stmt.executeUpdate();
            }
        }


        private double getCurrentBalance(String userId, Connection conn) throws SQLException {
            double balance = 0.0;
            try (PreparedStatement stmt = conn.prepareStatement("SELECT usd_balance FROM Users WHERE ID = ?")) {
                stmt.setString(1, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        balance = rs.getDouble("usd_balance");
                    }
                }
            }
            return balance;
        }


        private String handleBuy(String[] tokens) {

            if (loggedInUser == null) return "403 Forbidden\nNot logged in\nEND\n";
            // Adjusted for 5 tokens: BUY, stockSymbol, stockAmount, pricePerStock
            if (tokens.length != 5) return "400 Bad Request\nIncorrect syntax for BUY.\nEND\n";
            printDatabase();

            String stockSymbol = tokens[1];
            double stockAmount;
            double pricePerStock;

            try {
                stockAmount = Double.parseDouble(tokens[2]);
                pricePerStock = Double.parseDouble(tokens[3]);
            } catch (NumberFormatException e) {
                return "400 Bad Request\nInvalid stock amount or price.\nEND\n";
            }

            double totalCost = stockAmount * pricePerStock;

            try (Connection conn = MultiThreadServer.getDBConnection()) {
                conn.setAutoCommit(false); // Start transaction

                // Retrieve current balance
                double currentBalance = getCurrentBalance(loggedInUser.userId, conn);
                if (currentBalance < totalCost) {
                    return "400 Bad Request\nNot enough balance.\nEND\n";
                }

                // Update user balance
                updateBalance(loggedInUser.userId, -totalCost, conn);

                // Update or insert stock record
                updateStocks(loggedInUser.userId, stockSymbol, stockAmount, conn);

                conn.commit(); // Commit the transaction
                return String.format("200 OK\nBOUGHT: New balance: %.2f %s. USD balance $%.2f\nEND\n", stockAmount, stockSymbol, currentBalance - totalCost);
            } catch (SQLException e) {
                e.printStackTrace();
                return "500 Internal Server Error\nDatabase error.\nEND\n";
            }
        }



        private String handleSell(String[] tokens) {
            // Perform sell logic
            return "200 OK\nStock sold\nEND\n";
        }
        //Test addition
        private String checkDB(String[] tokens) {
            printDatabase();
            return "200 OK\nStock sold\nEND\n";
        }
        //end Test addition
        private String handleList(String[] tokens) {
            // List stocks for the user or all users if root
            return "200 OK\nStocks listed\nEND\n";
        }

        private String handleDeposit(String[] tokens) {
            // Perform deposit logic
            return "200 OK\nDeposit successful\nEND\n";
        }

        private String handleBalance(String[] tokens) {
            // Retrieve and send balance info
            return "200 OK\nBalance info\nEND\n";
        }

        private String handleWho(String[] tokens) {
            // List all active users
            return "200 OK\nActive users listed\nEND\n";
        }

        private String handleLookup(String[] tokens) {
            // Lookup stock info
            return "200 OK\nStock info\nEND\n";
        }

        private String handleQuit(String[] tokens) {
            // Handle client quit
            return "200 OK\nClient quit\nEND\n";
        }

        private String handleShutdown(String[] tokens) {
            // Shutdown server if user is root
            return "200 OK\nServer shutting down\nEND\n";
        }


        public void run() {
            try {
                while (true) {
                    //read messages from client
                    String clientMessage = fromClient.readUTF();
                    System.out.println("Received from client: " + clientMessage);
                    // Process the message and generate a response
                    String response = processClientMessage(clientMessage);

                    //send the message back to the same client
                    toClient.writeUTF("Server: Received your message - " + response);
                    toClient.flush();
                }
            } catch (IOException e) {
                // Handle client disconnect
                System.out.println("Client disconnected.");
                clients.remove(this);
            }
        }
    }

    public static void createDBTables(){
        Connection c = null;
        Statement stmt = null;
        String sql;

        try {
            Class.forName("org.sqlite.JDBC");
            c = DriverManager.getConnection("jdbc:sqlite:test.db");


            //
            stmt = c.createStatement();
            sql = "CREATE TABLE IF NOT EXISTS Users " +
                    " (ID INTEGER PRIMARY KEY     AUTOINCREMENT," +
                    " first_name     TEXT, " +
                    " last_name      TEXT, " +
                    " user_name      TEXT    NOT NULL, " +
                    " password       TEXT, " +
                    " usd_balance    DOUBLE  NOT NULL )";
            stmt.executeUpdate(sql);
            stmt.close();


            //create new stmt
            stmt = c.createStatement();
            sql = "CREATE TABLE IF NOT EXISTS Stocks " +
                    " (ID INTEGER PRIMARY KEY     AUTOINCREMENT," +
                    " stock_symbol     VARCHAR(4)   NOT NULL, " +
                    " stock_name       VARCHAR(20), " +
                    " stock_balance    DOUBLE, " +
                    " user_id          INTEGER, " +
                    " FOREIGN KEY(user_id) REFERENCES Users(ID) )";
            stmt.executeUpdate(sql);
            stmt.close();

            c.close();
        } catch ( Exception e ) {
            System.err.println(e.getClass().getName() + ": " + e.getMessage());
            System.exit(0);
        }


    }

    public static Connection getDBConnection() {
        Connection connection = null;
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:test.db");
        } catch (Exception e) {
            System.err.println("Cannot connect to database: " + e.getMessage());
        }
        return connection;
    }  // Database connection method

    public static void initializeUsers() {
        User[] users = {
                new User("0","Root", "Root01", 100.0),
                new User("1","Mary", "Mary01", 100.0),
                new User("2","John", "John01", 100.0),
                new User("3","Moe", "Moe01", 100.0)
        };

        Connection connection = null;
        PreparedStatement pstmt = null;
        ResultSet rs = null;

        try {
            connection = getDBConnection();
            for (User user : users) {
                // Check if user already exists
                String query = "SELECT * FROM Users WHERE user_name = ?";
                pstmt = connection.prepareStatement(query);
                pstmt.setString(1, user.userName);
                rs = pstmt.executeQuery();

                if (!rs.next()) { // User does not exist, insert new user
                    String insert = "INSERT INTO Users (user_name, password, usd_balance) VALUES (?, ?, ?)";
                    pstmt = connection.prepareStatement(insert);
                    pstmt.setString(1, user.userName);
                    pstmt.setString(2, user.password);
                    pstmt.setDouble(3, user.balance);
                    pstmt.executeUpdate();
                }
            }
        } catch (Exception e) {
            System.err.println("Error initializing users: " + e.getMessage());
        } finally {
            // Close resources
            try {
                if (rs != null) rs.close();
                if (pstmt != null) pstmt.close();
                if (connection != null) connection.close();
            } catch (Exception e) {
                System.err.println("Error closing resources: " + e.getMessage());
            }
        }
    } //initialize users

    //testing functions
    public static String printDatabase() {
        StringBuilder databaseOutput = new StringBuilder();
        try (Connection conn = getDBConnection()) {
            databaseOutput.append("Contents of Users Table:\n");
            databaseOutput.append(printUsersTable(conn));

            databaseOutput.append("\nContents of Stocks Table:\n");
            databaseOutput.append(printStocksTable(conn));
        } catch (SQLException e) {
            e.printStackTrace();
            databaseOutput.append("Error printing database contents.");
        }
        return databaseOutput.toString();
    }


    private static String printUsersTable(Connection conn) throws SQLException {
        StringBuilder output = new StringBuilder();
        String query = "SELECT * FROM Users";
        try (PreparedStatement pstmt = conn.prepareStatement(query);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                output.append("ID: ").append(rs.getString("ID"))
                        .append(", Name: ").append(rs.getString("user_name"))
                        .append(", Balance: $").append(rs.getDouble("usd_balance"))
                        .append("\n");
            }
        }
        return output.toString();
    }

    private static String printStocksTable(Connection conn) throws SQLException {
        StringBuilder output = new StringBuilder();
        String query = "SELECT * FROM Stocks";
        try (PreparedStatement pstmt = conn.prepareStatement(query);
             ResultSet rs = pstmt.executeQuery()) {
            while (rs.next()) {
                output.append("ID: ").append(rs.getString("ID"))
                        .append(", Symbol: ").append(rs.getString("stock_symbol"))
                        .append(", Balance: ").append(rs.getDouble("stock_balance"))
                        .append(", User ID: ").append(rs.getString("user_id"))
                        .append("\n");
            }
        }
        return output.toString();
    }

    //end of testing functions


}




// Method to initialize predefined users in the database
//test