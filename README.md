# Simple Web Proxy

Simple multithreaded web proxy written in Java for COMP6331 class at Australian National University.

## How it works

1. `WebProxy` class listens on the specified port for requests.
2. When a request is received, `WebProxy` creates a new thread, creates an instance of `RequestHandler` class, and passes the instance to the thread.
3. Both `WebProxy` and `RequestHandler` implements `Runnable` so that listening for "quit" command in the console, listening to requests, and handling requests can all be done separately without blocking other tasks. How it works is when the `Thread()` is created and `.start(obj)` is called, `obj.run()` will be called in a separate thread.
4. All instances of `Sydney` in text will be replaced by `Tokyo` as long as it doesn't break URL and paths or modify the HTML visually.
5. Original URL will also be replaced by the proxy URL (including the port number). 

## Work to be done:

1. Consolidate string cleaning (for URL and HTML body) into a separate Parser class
2. Sometimes, for example, the class will rewrite links in the requested HTML page to something like "0.0.0.0:3301" but the client browser rewrites that to "localhost:3301", or displayed as a different format. This can mess with the URL rewrite. 

## How to run

Format:

`javac *.java; java WebProxy <baseURL> [<port>]`

Example:

- `javac *.java; java WebProxy http://bom.gov.au`

- `javac *.java; java WebProxy http://bom.gov.au 3310`

To close the application, enter "quit" in the command line / terminal.
