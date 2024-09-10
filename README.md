
# CIS-427-Project2

This project demonstrates a basic client-server architecture in Java, where multiple clients can connect to a server to exchange messages. It includes a `Client` class, a multi-threaded server (`MultiThreadServer`), and a `User` class representing a simple user model.

## Prerequisites

- Java Development Kit (JDK) installed
- A terminal or console environment to run the `make` commands
- SQLite JDBC and SLF4J libraries (already referenced in the classpath in `Makefile`)

### Project Files:

- **User.java**: A class representing a user object, storing the `userId`, `userName`, `password`, and `balance`.
- **Client.java**: The client-side code that communicates with the server using sockets. It prompts for input and handles server responses.
- **MultiThreadServer.java**: A multi-threaded server that listens for connections from clients and processes their requests concurrently.
- **Makefile**: A file for automating the compilation and execution of the server and client.

## Instructions to Run

### Step 1: Compile the Java Files

Ensure you are in the root of the project where the `Makefile` is located. Run the following command to compile all the `.java` files:

```bash
make
```

### Step 2: Running the Server

To start the server, use the following command:

```bash
make run-server
```

This will compile (if necessary) and run the `MultiThreadServer`.

### Step 3: Running the Client

Once the server is running, you can run the client in a separate terminal or console. Use the following command:

```bash
make run-client
```

The client will prompt for an IP address (e.g., `localhost`) and then allow you to send messages to the server.

### Step 4: Clean Up

To remove all compiled `.class` files, run:

```bash
make clean
```

## Notes

- The project uses a Makefile to handle compilation and execution on both Windows and Linux.
- For Linux users, ensure you replace the `;` with `:` in the `CLASSPATH` variable in the `Makefile`.
- You can run multiple clients to connect to the server from different terminals.
- Server and client communications are based on a simple text protocol where clients can send and receive messages.

## Platform-Specific Instructions

### On Windows:
- To clean up compiled files, use:

  ```bash
  del *.class
  ```

### On Linux:
- To clean up compiled files, use:

  ```bash
  rm -f *.class
  ```

## External Libraries

- **SQLite JDBC**: Used for database connection (already included in `CLASSPATH`).
- **SLF4J**: A logging API used in the server (also referenced in `CLASSPATH`).

