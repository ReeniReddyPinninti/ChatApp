import java.io.*;
import java.net.Socket;
import java.util.*;

public class ClientHandler extends Thread {
    private static final List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());
    private static final Map<String, ClientHandler> clientsMap = Collections.synchronizedMap(new HashMap<>());
    private static final Map<String, List<ClientHandler>> rooms = Collections.synchronizedMap(new HashMap<>());
    private static final Map<String, LinkedList<String>> roomMessages = Collections.synchronizedMap(new HashMap<>());
    private static final Map<String, String> roomPasswords = Collections.synchronizedMap(new HashMap<>());
    private static final Map<String, String> roomAdmins = Collections.synchronizedMap(new HashMap<>());
    private static final Map<String, Map<String, byte[]>> roomFiles = Collections.synchronizedMap(new HashMap<>());
    private static final int MAX_HISTORY = 10;

    private Socket socket;
    private BufferedReader input;
    private PrintWriter output;
    private String clientName;

    private final Set<String> currentRooms = Collections.synchronizedSet(new HashSet<>());
    private String activeRoom = null;

    public ClientHandler(Socket socket) {
        this.socket = socket;
        try {
            input = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            output = new PrintWriter(socket.getOutputStream(), true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void run() {
        try {
            output.println("Enter your name:");
            clientName = input.readLine();

            while (clientsMap.containsKey(clientName)) {
                output.println("❌ Username already taken. Enter a different name:");
                clientName = input.readLine();
            }

            clients.add(this);
            clientsMap.put(clientName, this);
            output.println("📢 Use /join <room> to enter a chat room!");

            String message;
            while ((message = input.readLine()) != null) {
                if (message.startsWith("/msg ")) {
                    String[] split = message.split(" ", 3);
                    if (split.length < 3) {
                        output.println("❌ Usage: /msg <username> <message>");
                    } else {
                        sendPrivateMessage(split[1], split[2]);
                    }

                } else if (message.equals("/users")) {
                    synchronized (clientsMap) {
                        output.println("👥 Online users: " + String.join(", ", clientsMap.keySet()));
                    }

                } else if (message.equals("/exit")) {
                    output.println("👋 Bye " + clientName + "!");
                    break;

                } else if (message.startsWith("/join ")) {
                    String[] parts = message.split(" ", 3);
                    String room = parts[1];
                    String password = parts.length == 3 ? parts[2] : null;
                
                    boolean newRoom = false;
                
                    synchronized (rooms) {
                        // Check if room exists
                        if (rooms.containsKey(room)) {
                            // Room exists, validate password
                            if (roomPasswords.containsKey(room)) {
                                while (!Objects.equals(roomPasswords.get(room), password)) {
                                    output.println("🔒 Incorrect password. Try again:");
                                    try {
                                        password = input.readLine();
                                        if (password == null) return;
                                    } catch (IOException e) {
                                        return;
                                    }
                                }
                            }
                        } else {
                            // Room does not exist, create new room
                            rooms.put(room, Collections.synchronizedList(new ArrayList<>()));
                            if (password != null) {
                                roomPasswords.put(room, password);
                            }
                            roomAdmins.put(room, clientName);
                            output.println("🛡️ You are now the admin of room: " + room);
                            newRoom = true;
                        }
                    }
                
                    // Join the room now
                    joinRoom(room);
                }                
                else if (message.startsWith("/switch ")) {
                    String room = message.split(" ", 2)[1];
                    if (currentRooms.contains(room)) {
                        activeRoom = room;
                        output.println("✅ Switched to room: " + room);
                    } else {
                        output.println("❌ You have not joined this room.");
                    }

                } else if (message.equals("/myrooms")) {
                    output.println("📂 You are in rooms: " + (currentRooms.isEmpty() ? "(none)" : String.join(", ", currentRooms)));

                } else if (message.startsWith("/leave ")) {
                    String room = message.split(" ", 2)[1];
                    leaveRoom(room);

                } else if (message.equals("/room")) {
                    output.println(activeRoom == null ? "📦 No active room." : "🏠 Active room: " + activeRoom);

                } else if (message.equals("/rooms")) {
                    synchronized (rooms) {
                        if (rooms.isEmpty()) {
                            output.println("📁 Available rooms: (none)");
                        } else {
                            output.println("📁 Available rooms:");
                            for (Map.Entry<String, List<ClientHandler>> entry : rooms.entrySet()) {
                                String room = entry.getKey();
                                int count = entry.getValue().size();
                                boolean locked = roomPasswords.containsKey(room);
                                output.println(" - " + room + " (" + count + " users)" + (locked ? " 🔒" : ""));
                            }
                        }
                    }

                } else if (message.equals("/roomusers")) {
                    if (activeRoom == null) {
                        output.println("❌ No active room.");
                    } else {
                        synchronized (rooms) {
                            List<ClientHandler> members = rooms.get(activeRoom);
                            if (members != null && !members.isEmpty()) {
                                List<String> names = new ArrayList<>();
                                for (ClientHandler ch : members) {
                                    names.add(ch.clientName);
                                }
                                output.println("👥 Users in room '" + activeRoom + "': " + String.join(", ", names));
                            } else {
                                output.println("⚠️ No users found in the room.");
                            }
                        }
                    }

                } else if (message.startsWith("/kick ")) {
                    if (!isAdmin(activeRoom, clientName)) {
                        output.println("❌ Only the admin can kick users.");
                    } else {
                        String target = message.split(" ", 2)[1];
                        kickUser(target);
                    }

                } else if (message.startsWith("/lock ")) {
                    if (isAdmin(activeRoom, clientName)) {
                        String pwd = message.split(" ", 2)[1];
                        roomPasswords.put(activeRoom, pwd);
                        output.println("🔒 Room locked with new password.");
                    } else {
                        output.println("❌ Only the admin can lock the room.");
                    }

                } else if (message.equals("/unlock")) {
                    if (isAdmin(activeRoom, clientName)) {
                        roomPasswords.remove(activeRoom);
                        output.println("🔓 Room password removed.");
                    } else {
                        output.println("❌ Only the admin can unlock the room.");
                    }

                } else if (message.startsWith("/promote ")) {
                    if (isAdmin(activeRoom, clientName)) {
                        String newAdmin = message.split(" ", 2)[1];
                        if (clientsMap.containsKey(newAdmin)) {
                            roomAdmins.put(activeRoom, newAdmin);
                            output.println("👑 " + newAdmin + " is now the admin.");
                            broadcastToRoom(activeRoom, "👑 " + newAdmin + " has been promoted to admin.");
                        } else {
                            output.println("❌ User not found.");
                        }
                    } else {
                        output.println("❌ Only the admin can promote.");
                    }

                } else if (message.startsWith("/announce ")) {
                    if (isAdmin(activeRoom, clientName)) {
                        String announcement = message.substring(10).trim();
                        broadcastToRoom(activeRoom, "📣 [ANNOUNCEMENT by " + clientName + "]: " + announcement.toUpperCase());
                    } else {
                        output.println("❌ Only admins can make announcements.");
                    }

                } else if (message.startsWith("/sendfile ")) {
                    if (activeRoom == null) {
                        output.println("❌ Join a room to send files.");
                        continue;
                    }
                
                    String filename = message.split(" ", 2)[1];
                
                    output.println("📤 Send file size in bytes:");
                    output.flush(); // ✅ Important to flush
                    String sizeStr = input.readLine();
                
                    if (sizeStr == null || sizeStr.trim().isEmpty()) {
                        output.println("❌ No file size received.");
                        continue;
                    }
                
                    int size = Integer.parseInt(sizeStr.trim());
                
                    output.println("📤 Send file content (as base64):");
                    output.flush(); // ✅ Important to flush
                
                    String base64Data = input.readLine();
                    if (base64Data == null || base64Data.trim().isEmpty()) {
                        output.println("❌ No file data received.");
                        continue;
                    }
                
                    byte[] fileData = Base64.getDecoder().decode(base64Data);
                
                    roomFiles.putIfAbsent(activeRoom, new HashMap<>());
                    roomFiles.get(activeRoom).put(filename, fileData);
                
                    broadcastToRoom(activeRoom, "📎 File shared in room by " + clientName + ": " + filename);
                    
                } else if (message.equals("/getfiles")) {
                    if (activeRoom == null) {
                        output.println("❌ Join a room first.");
                    } else {
                        Map<String, byte[]> files = roomFiles.get(activeRoom);
                        if (files == null || files.isEmpty()) {
                            output.println("📂 No files shared in this room.");
                        } else {
                            output.println("📂 Files in room:");
                            for (String fname : files.keySet()) {
                                output.println("  - " + fname);
                            }
                        }
                    }

                } else if (message.startsWith("/download ")) {
                    if (activeRoom == null) {
                        output.println("❌ Join a room first.");
                    } else {
                        String filename = message.split(" ", 2)[1];
                        Map<String, byte[]> files = roomFiles.get(activeRoom);
                        if (files == null || !files.containsKey(filename)) {
                            output.println("❌ File not found in room.");
                        } else {
                            byte[] data = files.get(filename);
                            output.println("📥 " + filename + " size: " + data.length);
                            output.println("📥 base64 content:\n" + Base64.getEncoder().encodeToString(data));
                        }
                    }
                    
                } else if (message.startsWith("/global ")) {
                    String globalMsg = message.substring(8).trim();
                    broadcast("🌍 [Global] " + clientName + ": " + globalMsg);

                } else {
                    if (activeRoom == null) {
                        broadcast("🌍 [Global] " + clientName + ": " + message);
                    } else {
                        String time = new java.text.SimpleDateFormat("HH:mm").format(new java.util.Date());
                        broadcastToRoom(activeRoom, "[" + activeRoom + "] " + time + " 💬 " + clientName + ": " + message);
                    }
                }
            }
        } catch (IOException e) {
            // Ignore
        } finally {
            try {
                clients.remove(this);
                clientsMap.remove(clientName);
                for (String room : new HashSet<>(currentRooms)) {
                    leaveRoom(room);
                }
                socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void joinRoom(String room) {
        synchronized (rooms) {
            if (!rooms.containsKey(room)) {
                output.println("❌ Room does not exist.");
                return;
            }
            rooms.get(room).add(this);
        }
    
        currentRooms.add(room);
        activeRoom = room;
        output.println("🏠 You joined room: " + room);
    
        synchronized (roomMessages) {
            List<String> history = roomMessages.get(room);
            if (history != null) {
                output.println("🕘 Recent messages in " + room + ":");
                for (String msg : history) {
                    output.println("  " + msg);
                }
            }
        }
    
        broadcastToRoom(room, "🔔 " + clientName + " joined the room.");
    }    

    private void leaveRoom(String room) {
        if (room == null || !currentRooms.contains(room)) return;

        synchronized (rooms) {
            List<ClientHandler> members = rooms.get(room);
            if (members != null) {
                members.remove(this);
                if (members.isEmpty()) {
                    rooms.remove(room);
                    roomAdmins.remove(room);
                    roomPasswords.remove(room);
                } else if (isAdmin(room, clientName)) {
                    String newAdmin = members.get(0).clientName;
                    roomAdmins.put(room, newAdmin);
                    broadcastToRoom(room, "👑 " + newAdmin + " is now the new admin.");
                }
            }
        }

        broadcastToRoom(room, "❌ " + clientName + " left the room.");
        currentRooms.remove(room);
        if (room.equals(activeRoom)) {
            activeRoom = currentRooms.isEmpty() ? null : currentRooms.iterator().next();
            if (activeRoom != null) {
                output.println("✅ Switched to new active room: " + activeRoom);
            } else {
                output.println("📦 You are now not in any room.");
            }
        }        
        if (room.equals(activeRoom)) activeRoom = null;
    }

    private void broadcastToRoom(String room, String message) {
        synchronized (rooms) {
            List<ClientHandler> members = rooms.get(room);
            if (members != null) {
                for (ClientHandler client : members) {
                    client.output.println(message);
                }
            }
        }

        synchronized (roomMessages) {
            roomMessages.putIfAbsent(room, new LinkedList<>());
            LinkedList<String> history = roomMessages.get(room);
            history.addLast(message);
            if (history.size() > MAX_HISTORY) {
                history.removeFirst();
            }
        }
    }

    private void broadcast(String message) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                client.output.println(message);
            }
        }
    }

    private void sendPrivateMessage(String recipient, String message) {
        ClientHandler target = clientsMap.get(recipient);
        if (target != null) {
            target.output.println("📩 [Private] " + clientName + ": " + message);
            output.println("📤 [Private to " + recipient + "]: " + message);
        } else {
            output.println("❌ User '" + recipient + "' not found.");
        }
    }

    private boolean isAdmin(String room, String name) {
        return name.equals(roomAdmins.get(room));
    }

    private void kickUser(String username) {
        ClientHandler target = clientsMap.get(username);
        if (target != null && target.currentRooms.contains(activeRoom)) {
            target.output.println("⛔ You have been kicked from the room by the admin.");
            target.leaveRoom(activeRoom);
            broadcastToRoom(activeRoom, "🚫 " + username + " was kicked by the admin.");
        } else {
            output.println("❌ User not found in the active room.");
        }
    }
}
