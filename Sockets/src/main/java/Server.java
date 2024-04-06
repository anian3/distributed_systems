import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.net.ServerSocket;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class Server {
    private final CopyOnWriteArrayList<Socket> clients = new CopyOnWriteArrayList<>();
    private final ArrayList<Thread> client_threads = new ArrayList<>();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final int SERVER_PORT = 9099;
    ServerSocket serverSocket;
    DatagramSocket datagramSocket;

    public static void main(String[] args) throws IOException {
        Server server = new Server();
        server.startServer();
    }

    private void startServer() throws IOException {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Zamykam serwer...");
            stopServer();
        }));
        try {
            serverSocket = new ServerSocket(SERVER_PORT);
            datagramSocket = new DatagramSocket(SERVER_PORT);
            System.out.println("Uruchomiono serwer...");
            while (running.get()) {
                Socket clientSocket = serverSocket.accept();
                clients.add(clientSocket);
                ClientHandler clientHandler = new ClientHandler(clientSocket, clients, running);
                Thread thread = new Thread(clientHandler);
                client_threads.add(thread);
                thread.start();
            }
        } catch (Exception e) {
            if(running.get()) {
                System.out.println("Błąd w serwerze: " + e.getMessage());
            }
        } finally {
            if (serverSocket != null) {
                serverSocket.close();
            }
        }
    }

    private void stopServer(){
        running.set(false);
        try {
            try {
                for(Thread thread: client_threads){
                    thread.join();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("Błąd zamykania: " + e.getMessage());
            }
            if (serverSocket != null) {
                serverSocket.close();
            }
            for (Socket client : clients) {
                if (client != null && !client.isClosed()) {
                    client.close();
                }
            }
        } catch (IOException e) {
            System.out.println("Błąd zamykania serwera: " + e.getMessage());
        }
    }
}

class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final CopyOnWriteArrayList<Socket> clients;
    private final AtomicBoolean running;

    public ClientHandler(Socket clientSocket, CopyOnWriteArrayList<Socket> clients, AtomicBoolean running) {
        this.clientSocket = clientSocket;
        this.clients = clients;
        this.running = running;
    }

    public void run() {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
            String nick = in.readLine();
            System.out.print("Połączył(a) się ");
            System.out.println(nick);
            while (running.get()) {
                String msg = in.readLine();
                if (msg == null) {
                    break;
                }
                for (Socket socket : clients) {
                    if (socket != clientSocket && !socket.isClosed()) {
                        PrintWriter out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
                        out.println(nick + ": " + msg);
                    }
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                clientSocket.close();
                clients.remove(clientSocket);
            } catch (IOException e) {
                System.out.println("Błąd zamykania: " + e.getMessage());
            }
        }
    }
}
