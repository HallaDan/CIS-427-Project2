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

    private static ServerSocket serverSocket;
    private static List<ClientHandler> clients = new ArrayList<>();

    public static void main(String[] args) {
        try {

            //create server socket
            serverSocket = new ServerSocket(8306);
            System.out.println("Server Started.");
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
        public void closeConnection() {
            try {
                //close the input, output, and client socket
                fromClient.close();
                toClient.close();
                clientSocket.close();
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
                        return "\n200 OK\n";
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
            return "\n200 OK\n";
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

        private double getStockBalance(String userId, String stockSymbol, Connection conn) throws SQLException {
            double stock;
            double stockBalance = 0.0;
            String query = "SELECT stock_balance FROM Stocks WHERE user_id = ? AND stock_symbol = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(query)) {
                pstmt.setString(1, userId);
                pstmt.setString(2, stockSymbol);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        stockBalance = rs.getDouble("stock_balance");
                    }
                }
            }
            return stockBalance;
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
                return "400 Bad Request\nInvalid stock amount or price.\n";
            }

            double totalCost = stockAmount * pricePerStock;

            try (Connection conn = MultiThreadServer.getDBConnection()) {
                conn.setAutoCommit(false); // Start transaction

                // Retrieve current balance
                double currentBalance = getCurrentBalance(loggedInUser.userId, conn);
                if (currentBalance < totalCost) {
                    return "400 Bad Request\nNot enough balance.\n";
                }

                // Update user balance
                updateBalance(loggedInUser.userId, -totalCost, conn);

                // Update or insert stock record
                updateStocks(loggedInUser.userId, stockSymbol, stockAmount, conn);

                conn.commit(); // Commit the transaction
                return String.format("200 OK\nBOUGHT: New balance: %.2f %s. USD balance $%.2f\n", stockAmount, stockSymbol, currentBalance - totalCost);
            } catch (SQLException e) {
                e.printStackTrace();
                return "500 Internal Server Error\nDatabase error.\nEND\n";
            }
        }



        private String handleSell(String[] tokens) {
            if (loggedInUser == null) return "403 Forbidden\nNot logged in\nEND\n";
            // Adjusted for 5 tokens: SELL, stockSymbol, stockAmount, pricePerStock
            if (tokens.length != 5) return "400 Bad Request\nIncorrect syntax for SELL.\nEND\n";

            String stockSymbol = tokens[1];
            double stockAmount;
            double pricePerStock;

            try {
                stockAmount = Double.parseDouble(tokens[2]);
                pricePerStock = Double.parseDouble(tokens[3]);
            } catch (NumberFormatException e) {
                return "400 Bad Request\nInvalid stock amount or price.\nEND\n";
            }

            double totalSaleAmount = stockAmount * pricePerStock;

            try (Connection conn = MultiThreadServer.getDBConnection()) {
                conn.setAutoCommit(false); // Start transaction

                // Check if user has enough stock to sell
                double stockBalance = getStockBalance(loggedInUser.userId, stockSymbol, conn);
                if (stockBalance < stockAmount) {
                    return "400 Bad Request\nNot enough stock to sell.\nEND\n";
                }

                // Update user's stock balance
                updateStocks(loggedInUser.userId, stockSymbol, -stockAmount, conn);

                // Update user's USD balance
                updateBalance(loggedInUser.userId, totalSaleAmount, conn);

                conn.commit(); // Commit the transaction
                return String.format("200 OK\nSOLD: %f of %s. New USD balance: $%.2f\n", stockAmount, stockSymbol, getCurrentBalance(loggedInUser.userId, conn));
            } catch (SQLException e) {
                e.printStackTrace();
                return "500 Internal Server Error\nDatabase error.\nEND\n";
            }
        }

        //Test addition
        private String checkDB(String[] tokens) {
            printDatabase();
            return "200 OK\nStock sold\nEND\n";
        }
        //end Test addition
        private String handleList(String[] tokens) {
            if (loggedInUser == null) return "403 Forbidden\nNot logged in\nEND\n";

            StringBuilder response = new StringBuilder("\n");

            //check if the logged-in user is a root user
            boolean isRoot = loggedInUser.userName.equals("Root");

            try (Connection conn = getDBConnection();
                 PreparedStatement pstmt = conn.prepareStatement(
                         isRoot ? "SELECT s.*, u.user_name FROM Stocks s INNER JOIN Users u ON s.user_id = u.ID" :
                                 "SELECT * FROM Stocks WHERE user_id = ?")) {
                if (!isRoot) {
                    //if not a root user, set the user_id parameter in the query
                    pstmt.setString(1, loggedInUser.userId);
                }
                try (ResultSet rs = pstmt.executeQuery()) {
                    int count = 0;
                    while (rs.next()) {
                        count++;
                        if (isRoot) {
                            // if root user, include the user_name in the response
                            response.append(count).append(" ")
                                    .append(rs.getString("stock_symbol")).append(" ")
                                    .append(rs.getDouble("stock_balance")).append(" ")
                                    .append(rs.getString("user_name")).append("\n");
                        } else {
                            //if not root user, only include ID, stock_symbol, and stock_balance
                            response.append(count).append(" ")
                                    .append(rs.getString("stock_symbol")).append(" ")
                                    .append(rs.getDouble("stock_balance")).append("\n");
                        }
                    }
                    if (count == 0) {
                        return "404 Not Found\nNo records found\nEND\n";
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
                return "500 Internal Server Error\nDatabase error\nEND\n";
            }

            response.append("\n");
            if (!isRoot) {
                //if not a root user, append the user's username and format the response
                response.insert(0, "200 OK\nThe list of records in the Stock database for " + loggedInUser.userName + ":\n");
            } else {
                //if root user format the response accordingly
                response.insert(0, "200 OK\nThe list of records in the Stock database:\n");
            }
            return response.toString();
        }


        private String handleDeposit(String[] tokens) {
            // Perform deposit logic
            //return "200 OK\nDeposit successful\nEND\
            if (loggedInUser == null) return "403 Forbidden\nNot logged in\nEND\n";


            //for holding temp value before passing to DB to update
            double depositAmount;
            try {
                depositAmount = Double.parseDouble(tokens[1]);
            } catch (NumberFormatException e) {
                return "400 Bad Request\nInvalid deposit amount\nEND\n";
            }

            //update the user's balance in the database
            try (Connection conn = MultiThreadServer.getDBConnection();
                 PreparedStatement pstmt = conn.prepareStatement("UPDATE Users SET usd_balance = ? WHERE ID = ?")) {
                loggedInUser.balance = getCurrentBalance(loggedInUser.userId, conn);
                loggedInUser.balance += depositAmount;
                pstmt.setDouble(1, loggedInUser.balance);
                pstmt.setString(2, loggedInUser.userId);
                pstmt.executeUpdate();
            } catch (SQLException e) {
                e.printStackTrace();
                return "500 Internal Server Error\nDatabase error\nEND\n";
            }


            // response to client
            StringBuilder response = new StringBuilder("200 OK\nDeposit successful. New balance $");
            response.append(String.format("%.2f", loggedInUser.balance)).append("\n");

            return response.toString();
        }

        private String handleBalance(String[] tokens) {
            if (loggedInUser == null) return "403 Forbidden\nNot logged in\nEND\n";

            StringBuilder response = new StringBuilder("200 OK\nBalance for user ");
            response.append(loggedInUser.userName).append(": $");

            try (Connection conn = getDBConnection();
                 PreparedStatement pstmt = conn.prepareStatement("SELECT usd_balance FROM Users WHERE ID = ?")) {
                pstmt.setString(1, loggedInUser.userId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        response.append(String.format("%.2f", rs.getDouble("usd_balance")));
                    } else {
                        return "404 Not Found\nUser not found in the database\nEND\n";
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
                return "500 Internal Server Error\nDatabase error\nEND\n";
            }
            response.append("\n");
            return response.toString();
        }

        private String handleWho(String[] tokens) {
            // List all active users
            //return "200 OK\nActive users listed\nEND\n";
            if (loggedInUser != null && loggedInUser.userName.equals("Root")) {
                //make the list of logged in users
                StringBuilder userList = new StringBuilder("\n");

                //loop through clients arraylist to add the active users
                for (ClientHandler clientHandler : clients) {
                    if (clientHandler.loggedInUser != null) {
                        String username = clientHandler.loggedInUser.userName;
                        String ipAddress = clientHandler.clientSocket.getInetAddress().getHostAddress();
                        userList.append(username).append(" ").append(ipAddress).append("\n");
                    }
                }

                //return the full response
                return "200 OK\nThe list of active users:\n" + userList.toString() + "\n";
            } else {
                //if not Root user
                return "403 Forbidden\nUnauthorized to view user list\nEND\n";
            }

        }

        private String handleLookup(String[] tokens) {
            if (loggedInUser == null) return "403 Forbidden\nNot logged in\nEND\n";
            if (tokens.length < 2) return "400 Bad Request\nSyntax: LOOKUP <stock_name>\nEND\n";

            String searchQuery = tokens[1]; // Assuming stock name or symbol is the second token
            StringBuilder response = new StringBuilder();

            try (Connection conn = MultiThreadServer.getDBConnection();
                 PreparedStatement pstmt = conn.prepareStatement(
                         "SELECT * FROM Stocks WHERE user_id = ? AND (stock_symbol LIKE ?)")) {
                pstmt.setString(1, loggedInUser.userId);
                pstmt.setString(2, "%" + searchQuery + "%"); // Allows partial matches

                try (ResultSet rs = pstmt.executeQuery()) {
                    int matchCount = 0;
                    while (rs.next()) {
                        if (matchCount == 0) {
                            response.append("200 OK\n\nFound matches\n");
                        }
                        // Assuming stock_symbol and stock_balance are the required fields to display
                        response.append(rs.getString("stock_symbol")).append(" ").append(rs.getDouble("stock_balance")).append("\n");
                        matchCount++;
                    }

                    if (matchCount == 0) {
                        return "404 Your search did not match any records.\n";
                    } else {
                        response.append("\n");
                        return response.toString();
                    }
                }
            } catch (SQLException e) {
                e.printStackTrace();
                return "500 Internal Server Error\nDatabase error\nEND\n";
            }
        }

        private String handleQuit(String[] tokens) {
            // Handle client quit
            //return "200 OK\nClient quit\nEND\n";

            if (loggedInUser != null) {
                //assuming the username is stored in loggedInUser.userName
                String username = loggedInUser.userName;

                //find the corresponding ClientHandler
                for (ClientHandler clientHandler : clients) {
                    if (clientHandler.loggedInUser != null && clientHandler.loggedInUser.userName.equals(username)) {
                        //send a response indicating quit
                        String response = "200 OK\nClient quit\nEND\n";
                        try {
                            //send the response to the client
                            toClient.writeUTF(response);
                            toClient.flush();
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        //close the connection for this client
                        clientHandler.closeConnection();
                        break;
                    }
                }

                //clear the logged-in user for this ClientHandler
                loggedInUser = null;

                return "200 OK\nClient quit\nEND\n";
            } else {
                return "403 Forbidden\nNot logged in\nEND\n";
            }
        }

        private String handleShutdown(String[] tokens) {
            // Shutdown server if user is root
            //return "200 OK\nServer shutting down\nEND\n";

            //check if the user is Root to shut down the server
            if (loggedInUser != null && loggedInUser.userName.equals("Root")) {
                try {
                    //close all client connections
                    for (ClientHandler clientHandler : clients) {
                        clientHandler.closeConnection();
                    }
                    //clear the list of clients
                    clients.clear();

                    //close the server socket
                    serverSocket.close();

                    return "200 OK\nServer shutting down\nEND\n";
                } catch (IOException e) {
                    e.printStackTrace();
                    return "500 Internal Server Error\nError shutting down the server\n";
                }
            } else {
                return "403 Forbidden\nUnauthorized to shut down the server\n";
            }
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
                    toClient.writeUTF(response);
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


    //testing functions/////////////////////////////////
    public static String printDatabase() {
        StringBuilder databaseOutput = new StringBuilder();
        try (Connection conn = getDBConnection()) {
            databaseOutput.append("\nContents of Users Table:\n");
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

    //end of testing functions///////////////////////////


}




// Method to initialize predefined users in the database
