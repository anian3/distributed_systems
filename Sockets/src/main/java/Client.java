import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.Thread.sleep;

public class Client {
    private static final String SERVER_ADDRESS = "localhost";
    private static final int SERVER_PORT = 9099;
    private Socket clientSocket;
    private final AtomicBoolean running = new AtomicBoolean(true);
    Thread receiverThread;
    Thread senderThread;

    public static void main(String[] args) throws IOException {
        Client client = new Client();
        client.startClient();
    }

    private void startClient() throws IOException {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Zamykam...");
            try {
                stopClient();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }));
        try {
            clientSocket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true, StandardCharsets.UTF_8);
            System.out.println("Wpisz swój nick:");
            Scanner scanner = new Scanner(System.in, "Cp852");
            String nick = scanner.nextLine();
            MessageReceiver messageReceiver = new MessageReceiver(in, running);
            MessageSender messageSender = new MessageSender(nick, scanner, out, running);
            receiverThread = new Thread(messageReceiver);
            senderThread = new Thread(messageSender);
            senderThread.start();
            receiverThread.start();
            while (running.get()) {
                sleep(1);
            }

        } catch (IOException e) {
            System.out.println("Bład: " + e.getMessage());
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            if (clientSocket != null) {
                clientSocket.close();
            }
        }
    }

    private void stopClient() throws InterruptedException {
        running.set(false);
        try {
            receiverThread.join();
            senderThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.out.println("Błąd zamykania: " + e.getMessage());
        }

        try {
            if (clientSocket != null) {
                clientSocket.close();
            }
        } catch (IOException e) {
            System.out.println("Błąd zamykania: " + e.getMessage());
        }
    }
}

class MessageReceiver implements Runnable {
    private final BufferedReader in;
    private final AtomicBoolean running;


    public MessageReceiver(BufferedReader in, AtomicBoolean running) {
        this.in = in;
        this.running = running;
    }

    public void run() {
        try {
            while (running.get()) {
                String message = in.readLine();
                System.out.println(message);
            }
        } catch (IOException e) {
            if (!running.get()) {
                return;
            }
            throw new RuntimeException(e);
        }
    }
}

class MessageSender implements Runnable {
    String nick;
    Scanner keyboard;
    PrintWriter out;
    private final AtomicBoolean running;

    public MessageSender(String nick, Scanner keyboard, PrintWriter out, AtomicBoolean running) {
        this.nick = nick;
        this.keyboard = keyboard;
        this.out = out;
        this.running = running;
    }

    public void run() {
        out.println(nick);
        try {
            while (running.get()) {
                if (keyboard.hasNextLine()) {
                    String message = keyboard.nextLine();
                    out.println(message);
                }
            }
        } catch (Exception e) {
            if (!running.get()) {
                return;
            }
            throw new RuntimeException(e);
        }
    }
}
