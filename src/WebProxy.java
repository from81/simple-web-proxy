import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Scanner;

public class WebProxy implements Runnable {

  private ServerSocket serverSocket;
  private static boolean running = true;
  private final ArrayList<Thread> threads;
  private static final int defaultPort = 3310;
  private static final String defaultBaseURL = "http://bom.gov.au";

  public static void main(String[] args) {
    String baseURL = WebProxy.defaultBaseURL;
    int port = WebProxy.defaultPort;

    if (args.length > 2) {
      System.out.println(
              String.format("Too many arguments.\nFormat:\njava WebProxy <base_url> [<port_number>]"));
      System.exit(-1);
    }

    if (args.length == 1) {
      baseURL = args[0];
    } else if (args.length == 2) {
      baseURL = args[0];
      port = Integer.parseInt(args[1]);
    }
    WebProxy myProxy = new WebProxy(port);
    myProxy.listen(baseURL);
  }

  public WebProxy() {
    this(WebProxy.defaultPort);
  }

  public WebProxy(int port) {
    // Create array list to hold servicing threads
    this.threads = new ArrayList<>();

    try {
      // Start proxy manager and call run()
      new Thread(this).start();

      // Create the Server Socket for the Proxy
      this.serverSocket = new ServerSocket(port);

      // Set the timeout
      // serverSocket.setSoTimeout(100000);	// debug
      WebProxy.running = true;
      System.out.println("Listening on port: " + this.serverSocket.getLocalPort());
    } catch (SocketException e) {
      System.out.println("SocketException => " + e.getMessage());
      e.printStackTrace();
    } catch (SocketTimeoutException e) {
      System.out.println("SocketTimeoutException => " + e.getMessage());
      e.printStackTrace();
    } catch (IOException e) {
      System.out.println("IOException => " + e.getMessage());
      e.printStackTrace();
    }
  }

  /**
   * Listens on specified port. Accept new connections, create a new thread, and let request handler
   * handle the data transfer from the requested path
   */
  public void listen(String baseURL) {
    while (WebProxy.running) {
      try {
        // Blocks execution until new connection request is received by the proxy server
        Socket socket = this.serverSocket.accept();

        // Create new thread, pass the RequestHandler instance to be run, and start running
        Thread thread = new Thread(new RequestHandler(socket, baseURL));
        this.threads.add(thread);
        thread.start();
      } catch (SocketException e) {
        System.out.println("Server closed");
      } catch (IOException e) {
        System.out.println("IOException => " + e.getMessage());
        e.printStackTrace();
      }
    }
  }

  /**
   * Terminate all active threads and close server socket
   */
  private void shutdown() {
    WebProxy.running = false;

    // Terminate active threads
    for (Thread thread : this.threads) {
      if (thread.isAlive()) {
        try {
          thread.join();
        } catch (InterruptedException e) {
          System.out.println("InterruptedException => " + e.getMessage());
          e.printStackTrace();
        }
      }
    }

    // Close Server Socket
    try {
      System.out.println("Terminating server socket");
      this.serverSocket.close();
    } catch (Exception e) {
      System.out.println("Exception => " + e.getMessage());
      e.printStackTrace();
    }
  }

  /**
   * Run proxy server until exit command is entered
   */
  @Override
  public void run() {
    Scanner scanner = new Scanner(System.in);
    String command;
    while (WebProxy.running) {
      System.out.println("Enter 'quit' to close.");
      command = scanner.nextLine();
      if (command.equals("quit")) {
        shutdown();
      }
    }
    scanner.close();
  }
}
