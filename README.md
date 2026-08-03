# ChatApp

A Java-based multi-client chat application built with sockets and threads. ChatApp supports global chat, private messaging, multi-room chat, room administration, password-protected rooms, and basic file sharing within rooms.

## Features

- Real-time chat over TCP sockets
- Multiple chat rooms
- Create or join rooms with optional passwords
- Switch between joined rooms
- List available rooms and active users
- Private messages between users
- Room admin controls:
  - Kick users
  - Lock or unlock rooms
  - Promote another user to admin
  - Send room announcements
- Global chat broadcast
- Room message history
- File sharing in rooms using Base64 transfer

## Project Structure

- `Server.java` - Starts the chat server on port `1234`
- `Client.java` - Console-based client that connects to the server
- `ClientHandler.java` - Handles each connected client, chat commands, rooms, and file sharing

## Requirements

- Java Development Kit (JDK) 8 or later

## How to Run

### 1. Compile the project

```bash
javac Server.java Client.java ClientHandler.java
```

### 2. Start the server

```bash
java Server
```

### 3. Start one or more clients

Open a new terminal for each client and run:

```bash
java Client
```

## Commands

### User Commands

- `/msg <username> <message>` — send a private message
- `/users` — list online users
- `/global <message>` — send a message to all connected users
- `/rooms` — list available rooms
- `/join <room> [password]` — join or create a room
- `/switch <room>` — switch active room
- `/myrooms` — list rooms you have joined
- `/leave <room>` — leave a room
- `/room` — show current active room
- `/roomusers` — list users in the active room
- `/sendfile <filename>` — share a file in the active room
- `/getfiles` — list shared files in the active room
- `/download <filename>` — download a shared file from the active room
- `/exit` — disconnect from the server

### Admin Commands

- `/kick <username>` — remove a user from the active room
- `/lock <password>` — set or change the room password
- `/unlock` — remove the room password
- `/promote <username>` — promote a user to room admin
- `/announce <message>` — broadcast an announcement to the room

## Notes

- The server runs on `localhost:1234`.
- If a room is created with a password, users must provide the correct password to join.
- Room message history keeps the latest 10 messages.
- File sharing uses Base64 text transfer over the socket connection.

## Example

```bash
# Terminal 1
java Server

# Terminal 2
java Client

# In client
Alice
/join general
Hello everyone!
```

## License

No license has been specified for this project.
