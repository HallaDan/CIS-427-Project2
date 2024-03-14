# Makefile for Java project
# ':' for linux -- ';' for windows

# Set the Java compiler
JAVAC = javac

# Set the Java runtime
JAVA = java

# Set the classpath including external libraries
CLASSPATH = ".;sqlite-jdbc-3.45.1.0.jar;slf4j-api-1.7.36.jar"

# List of Java source files
SOURCES = MultiThreadServer.java Client.java User.java

# Default target to compile the Java files
all: $(SOURCES:.java=.class)

# Rule to compile Java source files to bytecode
%.class: %.java
	$(JAVAC) $<

# Target to run the Server
run-server: all
	$(JAVA) -classpath $(CLASSPATH) MultiThreadServer

# Target to run the Client
run-client: all
	$(JAVA) Client

# Clean up compiled files
clean:
	rm -f *.class

# Default target when 'make' is run without arguments
.DEFAULT_GOAL := all