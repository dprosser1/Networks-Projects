import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class tcpcss {
    private static final int DEFAULT_PORT = 12345;
    private static int clientIdCounter = 0;
    private static final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private static final Map<String, String> pendingTransfers = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        ServerSocket serverSocket = new ServerSocket(port);

        System.out.println("Listener on port " + port);
        System.out.println("Waiting for connections...");

        while (true) {
            Socket socket = serverSocket.accept();
            String threadName = "Thread-" + clientIdCounter;
            System.out.println("New connection, thread name is " + threadName + ", ip is: " + socket.getInetAddress().getHostAddress() + ", port: " + socket.getPort());
            ClientHandler handler = new ClientHandler(socket, threadName);
            new Thread(handler, threadName).start();
            clientIdCounter++;
        }
    }

    static class ClientHandler implements Runnable {
        private Socket socket;
        private String name;
        private String threadName;
        private BufferedReader in;
        private PrintWriter out;
        private boolean hasLeft = false;

        public ClientHandler(Socket socket, String threadName) {
            this.socket = socket;
            this.threadName = threadName;
        }

        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                out = new PrintWriter(socket.getOutputStream(), true);

                name = in.readLine();
                clients.put(name, this);
                System.out.println("Adding to list of sockets as " + (clientIdCounter - 1));
                System.out.println("[" + name + "] has joined the chat.");
                broadcast("[" + name + "] has joined the chat.");

                String line;
                while ((line = in.readLine()) != null) {
                    if (line.startsWith("/")) {
                        processCommand(line);
                    } else if (line.startsWith("[" + name + "] ")) {
                        broadcast(line);
                        System.out.println(line);
                    } else {
                        String formatted = "[" + name + "] " + line;
                        broadcast(formatted);
                        System.out.println(formatted);
                    }
                }
            } catch (IOException e) {
                // Ignore
            } finally {
                closeConnection();
            }
        }

        private void processCommand(String line) {
            if (line.startsWith("/sendfile")) {
                String[] parts = line.split(" ", 3);
                if (parts.length == 3) {
                    String to = parts[1];
                    String filename = parts[2];
                    File file = new File(filename);
                    if (!file.exists()) {
                        send("File not found: " + filename);
                        return;
                    }
                    long fileSize = file.length();
                    ClientHandler recipient = clients.get(to);
                    if (recipient != null) {
                        String message = "[File transfer initiated from " + name + " to " + to + " " + filename + " (" + (fileSize / 1024) + " KB)]";
                        recipient.send(message);
                        System.out.println(message);
                        pendingTransfers.put(to, name + ":" + filename);
                    }
                }
            } else if (line.startsWith("/acceptfile")) {
                String[] parts = line.split(" ", 2);
                String sender = parts[1];
                String key = name;
                if (pendingTransfers.containsKey(key)) {
                    String[] info = pendingTransfers.remove(key).split(":");
                    String filename = info[1];
                    int port = new Random().nextInt(10000) + 20000;
                    System.out.println("[File transfer accepted from " + name + " to " + sender + "]");
                    sendTo(sender, "[File transfer accepted from " + name + " to " + sender + "]");
                    sendTo(sender, "/fileport sender " + name + " " + filename + " " + port);
                    sendTo(key, "/fileport receiver " + sender + " " + filename + " " + port);
                    System.out.println("Starting new file transfer thread, thread name is Thread-" + clientIdCounter);
                    System.out.println("[Starting file transfer between " + sender + " and " + name + "]");
                }
            } else if (line.startsWith("/rejectfile")) {
                String[] parts = line.split(" ", 2);
                String sender = parts[1];
                System.out.println("[File transfer rejected by " + name + "]");
                sendTo(sender, "[File transfer rejected by " + name + "]");
            } else if (line.equals("/who")) {
                String userList = "[Online users: " + String.join(", ", clients.keySet()) + "]";
                send(userList);
                System.out.println("[" + name + "] requested online users list.");
                System.out.println(userList);
            } else if (line.equals("/quit")) {
                closeConnection();
            }
        }

        private void broadcast(String msg) {
            for (ClientHandler client : clients.values()) {
                client.send(msg);
            }
        }

        private void sendTo(String user, String msg) {
            ClientHandler client = clients.get(user);
            if (client != null) {
                client.send(msg);
            }
        }

        private void send(String msg) {
            out.println(msg);
        }

        private void closeConnection() {
            if (hasLeft) return;
            hasLeft = true;
            try {
                if (name != null) {
                    clients.remove(name);
                    System.out.println("[" + name + "] has left the chat.");
                    broadcast("[" + name + "] has left the chat.");
                }
                if (socket != null) socket.close();
            } catch (IOException e) {
                // Ignore
            }
        }
    }
}
