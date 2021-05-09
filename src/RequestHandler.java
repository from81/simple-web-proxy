import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.Socket;
import java.net.URL;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

public class RequestHandler implements Runnable {

  private Socket clientSocket;
  private BufferedReader clientSocketBReader;
  private BufferedWriter clientSocketBWriter;
  private static String baseURL;
  private static final Logger LOG = Logger.getLogger(RequestHandler.class.getName());
  private long threadID;
  private static int urlNumReplaced = 0;
  private static int cityNumReplaced = 0;

  public RequestHandler(Socket clientSocket, String baseURL) {
    this.clientSocket = clientSocket;
    RequestHandler.baseURL = baseURL;

    Thread currThread = Thread.currentThread();
    threadID = currThread.getId();

    try {
      this.clientSocket.setSoTimeout(2000);
      this.clientSocketBReader =
              new BufferedReader(new InputStreamReader(this.clientSocket.getInputStream()));
      this.clientSocketBWriter =
              new BufferedWriter(new OutputStreamWriter(this.clientSocket.getOutputStream()));
    } catch (IOException e) {
      System.out.println("IOException => " + e.getMessage());
      e.printStackTrace();
    }
  }

  /**
   * Reads and clean the request, retrieve and send data to client
   */
  @Override
  public void run() {
    try {
      int idx;

      // Get Request from client
      String request = this.clientSocketBReader.readLine();
      // Uncomment if Requests for images are also needed
      // LOG.info(String.format("\tThread ID: %d\n\tRequest Received: %s\n", this.threadID, request));

      // Parse out URL
      String urlString = request.substring(request.indexOf(' ') + 1);
      urlString = urlString.substring(0, urlString.indexOf(' '));

      // Remove http://www if included
      if (urlString.startsWith("http")) {
        idx = 0;
        String toRemove = "http://www";
        while (toRemove.charAt(idx) == urlString.charAt(idx)) idx++;
        urlString = urlString.substring(idx);
      }

      // Remove leading www.
      if (urlString.startsWith("www")) {
        idx = 0;
        String toRemove = "www.";
        while (toRemove.charAt(idx) == urlString.charAt(idx)) idx++;
        urlString = urlString.substring(idx);
      }

      if (urlString.equals("/")) {
        sendToClient(RequestHandler.baseURL, request);
      } else {
        // Remove leading forward slashes
        idx = 0;
        while (urlString.charAt(idx) == '/') idx++;
        urlString = urlString.substring(idx);

        // Remove local address and port + /
        idx = 0;
        InetAddress address = this.clientSocket.getLocalAddress();
        String serverAddress =
                String.format("%s:%d/", address.getHostAddress(), clientSocket.getLocalPort());
        while (urlString.charAt(idx) == serverAddress.charAt((idx)) && idx < urlString.length())
          idx++;
        urlString = urlString.substring(idx);

        // Remove URL if absolute path is used
        int offset = 0;
        String s = "http://www.";
        while (RequestHandler.baseURL.charAt(offset) == s.charAt(offset)) offset++;
        String domain = RequestHandler.baseURL.substring(offset);
        if (urlString.startsWith(domain)) {
          urlString = urlString.substring(urlString.lastIndexOf(domain) + 1);
        }

        // Remove leading forward slashes
        idx = 0;
        while (urlString.charAt(idx) == '/') idx++;
        urlString = urlString.substring(idx);

        // prepend forward slash /
        urlString = "/" + urlString;

        // prepend BoM base url
        urlString = RequestHandler.baseURL + urlString;

        // System.out.println("Cleaned and parsed URL: " + urlString + "\n");
        sendToClient(urlString, request);
      }
    } catch (IOException e) {
      System.out.println("IOException => " + e.getMessage());
      e.printStackTrace();
    }
  }

  private void sendToClient(String urlString, String request) {
    try {
      // Get file name and type
      String fileType, fileName;
      int idxFileExt = urlString.lastIndexOf(".");
      fileType = urlString.substring(idxFileExt + 1);
      // fileName = urlString.substring(0, idxFileExt);

      if (fileType.equals("png")
              || fileType.equals("jpg")
              || fileType.equals("jpeg")
              || fileType.equals("gif")
              || fileType.equals("ico")) {

        URL remoteURL = new URL(urlString);

        // Read and buffer image
        HttpURLConnection httpConn = (HttpURLConnection) remoteURL.openConnection();
        httpConn.addRequestProperty("User-Agent", "");
        BufferedImage img = ImageIO.read(httpConn.getInputStream());

        String res;
        if (img == null) {
          // Image not found
          res = "HTTP/1.0 404 NOT FOUND\n" + "Proxy-agent: ProxyServer/1.0\n" + "\r\n";
          this.clientSocketBWriter.write(res);
          this.clientSocketBWriter.flush();
        } else {
          // Send response code to client
          res = "HTTP/1.0 200 OK\n" + "Proxy-agent: ProxyServer/1.0\n" + "\r\n";
          this.clientSocketBWriter.write(res);
          this.clientSocketBWriter.flush();

          // Write image data to the client
          ImageIO.write(img, fileType, this.clientSocket.getOutputStream());
        }
      } else {
        URL remoteURL = new URL(urlString);

        // Open connection to remote server
        HttpURLConnection serverConn = (HttpURLConnection) remoteURL.openConnection();

        serverConn.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 6.1; WOW64; X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/89.0.4389.90 Safari/537.36");
        serverConn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        serverConn.setRequestProperty("Content-Language", "en-US");
        serverConn.setDoOutput(true);
        serverConn.setUseCaches(false);

        // Read via connection to remote Server
        BufferedReader serverSocketBReader =
                new BufferedReader(new InputStreamReader(serverConn.getInputStream()));

        // Send success code to client
        this.clientSocketBWriter.write(
                "HTTP/1.0 200 OK\n" + "Proxy-agent: ProxyServer/1.0\n" + "\r\n");

        // Read lines from server and write to client
        String line;
        while ((line = serverSocketBReader.readLine()) != null) {
          LineParser parser = parseLine(line);
          this.clientSocketBWriter.write(parser.getParsedText());
          urlNumReplaced += parser.getAddressNumReplaced();
          cityNumReplaced += parser.getCityNumReplaced();
        }

        // Log URL, status response, and stats
        // Don't log requests for JS and CSS
        if (!urlString.endsWith(".js") && !urlString.endsWith(".css")) {
          String msg =
                  String.format(
                          "\tThread ID: %s\n\tURL: %s\n\tRequest: %s\n\tStatus response: %s\n\tLinks Replaced:\t%d\t\tCity names replaced:\t%d\n",
                          String.valueOf(this.threadID), urlString, request, serverConn.getHeaderField(null), urlNumReplaced, cityNumReplaced);
          LOG.info(msg);
        }

        serverSocketBReader.close();
        this.clientSocketBWriter.flush();
      }
      this.clientSocketBWriter.close();
    } catch (Exception e) {
      System.out.println("Exception => " + e.getMessage());
      e.printStackTrace();
    }
  }

  private LineParser parseLine(String line) {
    int port = this.clientSocket.getLocalPort();
    InetAddress address = this.clientSocket.getLocalAddress();
    String serverAddress = address.getHostAddress();
    LineParser parser = new LineParser(RequestHandler.baseURL, serverAddress, port);
    parser.parseAddress(line);
    parser.swapCity(line);
    return parser;
  }
}

class LineParser {
  private String targetURL, baseURL, rawText, parsedText;
  private int port, addressNumReplaced, cityNumReplaced;

  public String getRawText() {
    return rawText;
  }

  public String getParsedText() {
    return parsedText;
  }

  public int getAddressNumReplaced() {
    return addressNumReplaced;
  }

  public int getCityNumReplaced() {
    return cityNumReplaced;
  }

  public LineParser(String targetURL, String baseURL, int port) {
    this.targetURL = targetURL;
    this.baseURL = baseURL;
    this.port = port;
  }

  public void parseAddress(String line) {
    if (targetURL.startsWith("http://")) {
      targetURL = targetURL.substring(7);
    }

    if (targetURL.startsWith("www.")) {
      targetURL = targetURL.substring(4);
    }

    this.addressNumReplaced = line.split(this.targetURL, -1).length - 1;
    this.rawText = line;

    // Replace base URL with local address and port
    String domain = this.baseURL;
    if (domain.endsWith(String.format(":%d/", this.port))) {
      domain = domain.substring(0, domain.lastIndexOf(":"));
    }

    // Remove hex ipv6 address + port but leave the path
    line = line.replaceAll("(?:[0-9A-Za-z]{0,4}\\:){7}[0-9A-Za-z]{0,4}:[0-9]+/", "/");

    // Remove ipv4 address + port but leave the path
    // hex ipv4
    line = line.replaceAll("(?:[0-9A-Za-z]{0,2}\\:){3}[0-9A-Za-z]{0,2}:[0-9]+/", "/");
    // decimal ipv4
    line = line.replaceAll("(?:[0-9]+\\.){3}[0-9]+:[0-9]+/", "/");

    String regex = "(http)?(?=[^s])(\\:\\/\\/)?(www\\.)?([A-Za-z0-9]+\\.)?(%s)(.*)";
    String regexStr = String.format(regex, this.targetURL);
    domain = String.format("%s:%d", domain, this.port);
    this.parsedText = line.replaceAll(regexStr, String.format("%s/$4$5$6", domain));
  }

  /**
   * Swap city names only if it's a plain text
   */
  public void swapCity(String line) {
    String targetCity = "Sydney";
    String dummyCity = "Tokyo";
    String regex = String.format("( )?(%s)([\\ \\.\\<])(?!s?html)", targetCity);
    this.cityNumReplaced = line.split(targetCity, -1).length - 1;
    this.parsedText = line.replaceAll(regex, "$1" + dummyCity + "$3");
  }
}
