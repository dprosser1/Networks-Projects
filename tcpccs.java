import java.io.*;
import java.net.*;
import java.util.*;

public class tcpccs {
    public static void main(String[] args) throws IOException {
        if (args.length != 2) {
            System.out.println("Usage: ./tcpccs <server_hostname> <username>");
            return;
        }

        String host = args[0];
        String username = args[1];

        Socket socket = new Socket(host, 12345);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        BufferedReader userInput = new BufferedReader(new InputStreamReader(System.in));

        out.println(username);
        System.out.println("Connected to the server. You can start sending messages.");

        Thread listener = new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    if (line.startsWith("/fileport")) {
                        String[] parts = line.split(" ", 5);
                        String role = parts[1];
                        String peer = parts[2];
                        String filename = parts[3];
                        int port = Integer.parseInt(parts[4]);

                        if (role.equals("sender")) {
                            new Thread(() -> sendFile(filename, port)).start();
                        } else if (role.equals("receiver")) {
                            new Thread(() -> receiveFile(peer, filename, host, port)).start();
                        }
                    } else if (!line.startsWith("[" + username + "] ")) {
                        System.out.println(line);
                    }
                }
            } catch (IOException e) {
                // Ignore
            }
        });
        listener.start();

        String input;
        while ((input = userInput.readLine()) != null) {
            out.println(input);
            if (input.equals("/quit")) {
                break;
            }
        }

        socket.close();
        System.exit(0);
    }

    private static void sendFile(String filename, int port) {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            Socket clientSocket = serverSocket.accept();
            try (FileInputStream fis = new FileInputStream(filename);
                 OutputStream out = clientSocket.getOutputStream()) {

                byte[] buffer = new byte[4096];
                int count;
                while ((count = fis.read(buffer)) > 0) {
                    out.write(buffer, 0, count);
                }

                out.flush();
            }
            clientSocket.close();
        } catch (IOException e) {
            System.out.println("[File transfer failed while sending: " + filename + "]");
        }
    }

    private static void receiveFile(String sender, String filename, String host, int port) {
        try (Socket fileSocket = new Socket(host, port);
             InputStream in = fileSocket.getInputStream();
             FileOutputStream fos = new FileOutputStream("received_" + filename)) {

            byte[] buffer = new byte[4096];
            int count;
            while ((count = in.read(buffer)) > 0) {
                fos.write(buffer, 0, count);
            }

            System.out.println("[File transfer complete from " + sender + " " + filename + "]");
        } catch (IOException e) {
            System.out.println("[File transfer failed from " + sender + "]");
        }
    }
}