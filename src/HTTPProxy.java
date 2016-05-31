import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;



/**
 * Created by josephkesting on 5/26/16.
 */
public class HTTPProxy {

    Map<Integer, BlockingQueue<String>> buffers;
    private final Lock l = new ReentrantLock();
    private int connectionID = 0;
    private ServerSocket serverSocket;

    public HTTPProxy(int port) {
        buffers = new HashMap<>();

        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            System.out.println(e);
        }
    }

    public void run(){
        System.out.println(1);
        while (true) {
            System.out.println(2);
            PrintWriter clientOut = null;
            BufferedReader clientIn = null;
            String request;
            int clientToHostID = getConnectionID();
            try {
                Socket clientSocket = serverSocket.accept();
                System.out.println(3);
                clientOut =
                        new PrintWriter(clientSocket.getOutputStream(), true);
                clientIn = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream()));
            } catch (IOException e) {
                System.out.println(e);
            }
            request = getModifiedRequest(clientIn);
            System.out.println(4);
            INetAddress address = getAddressFromMessage(request);
            System.out.println(5);

            PrintWriter hostOut = null;
            BufferedReader hostIn = null;
            int hostToClientID = getConnectionID();
            InputStream i = null;
            try {
                Socket hostSocket = new Socket();
                System.out.println(6);
                InetSocketAddress addr = new InetSocketAddress(0);
                hostSocket.bind(addr);
                System.out.println(addr);
                hostSocket.connect(new InetSocketAddress(address.getHost(), address.getPort()));
                System.out.println(hostSocket.isConnected());
                System.out.println(7);
                hostOut =
                        new PrintWriter(hostSocket.getOutputStream(), true);
                hostIn = new BufferedReader(
                        new InputStreamReader(hostSocket.getInputStream()));
                i = hostSocket.getInputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            boolean connect = request.startsWith("CONNECT");

            BlockingQueue<String> clientToHost = new LinkedBlockingQueue<>();
            BlockingQueue<String> hostToClient = new LinkedBlockingQueue<>();
            clientToHost.add(request);
            buffers.put(clientToHostID, clientToHost);
            buffers.put(hostToClientID, hostToClient);

            System.out.println(8);
            Thread clientRead = new readThread(clientIn, clientToHostID, connect);
            Thread clientWrite = new writeThread(clientOut, hostToClientID, connect);
            Thread hostRead = new readThread(hostIn, hostToClientID, connect);
            Thread hostWrite = new writeThread(hostOut, clientToHostID, connect);
            hostWrite.start();
            hostRead.start();
            clientWrite.start();
            clientRead.start();
            System.out.println(9);
        }
    }

    private INetAddress getAddressFromMessage(String message) {
        String host = "";
        int port = -1;
        Scanner s = new Scanner(message);
        String requestLine = "";
        while (s.hasNextLine()) {

            String line = s.nextLine();
            if(requestLine.equals("")) {
                requestLine = line;
            }
            if(line.toLowerCase().startsWith("host")) {
                System.out.println(line);
                String[] splitLine = line.split(":", 2);
                String url = splitLine[1].trim().split(" ")[0];
                String[] split = url.split(":");
                host = split[0];
                if(split.length > 1) {
                    port = Integer.parseInt(split[1].split("/")[0]);
                }


//                try {
//                    URI url = new URI(splitLine[1]);
//                    host = url.getHost();
//                    port = url.getPort();
////                } catch (MalformedURLException e) {
////                    e.printStackTrace();
//                } catch (URISyntaxException e) {
//                    e.printStackTrace();
//                }
            }
        }
        if (port == -1) {
            String[] splitLine = requestLine.split(" ");
//            try {
//                String urlString = splitLine[1];
//                try {
//                    URL url = new URL(urlString);
//                } catch (MalformedURLException e) {
//                    try {
//                        String[] array = urlString.split(":");
//                        if (array.length == 3) {
//                            port = Integer.parseInt(array[2].split("/")[0]);
//                        } else if (array.length == 2) {
//                            if (array[0].equals("http")) {
//                                port = 80;
//                            } else if (array[0].equals("https")) {
//                                port = 443;
//                            } else {
//                                port = Integer.parseInt(array[1].split("/")[0]);
//                            }
//                        } else {
//                            if (requestLine.startsWith("CONNECT")) {
//                                port = 443;
//                            } else {
//                                port = 80;
//                            }
//                        }
//
//                    } catch (ArrayIndexOutOfBoundsException error) {
//                        System.out.println("FUCKED");
//                    }
//                }

            try {
                URI uri = new URI(splitLine[1]);
                port = uri.getPort();
                if (port == -1) {
                    if (splitLine[1].startsWith("https://") || splitLine[0].equals("CONNECT")) {
                        port = 443;
                    } else {
                        port = 80;
                    }
                }
            } catch (URISyntaxException e) {
                System.out.println("FUCKED");
            }

//                port = url.getPort();
//                if (port == -1) {
//                    String protocol = url.getScheme();
//                    if(protocol.equals("https")) {
//                        port = 443;
//                    } else {
//                        port = 80;
//                    }
//                }
//            } catch (MalformedURLException e) {
//                e.printStackTrace();

            }

        if (host.equals("")) {
            System.out.println("STILL NO HOST");
        }
        if (port == -1) {
            System.out.println("STILL NO PORT");
        }

        return new INetAddress(host, port);
    }

    private String getModifiedRequest(BufferedReader in) {
        String request = "";
        System.out.println("PRELOOP");
        while(true) {
            String line = null;
            try {
                line = in.readLine();
            } catch (IOException e) {
                e.printStackTrace();
                break;
            }
            if (line == null || line.equals("")) {
                break;
            }
            if (request.equals("")) {
                line = line.replace("HTTP/1.1", "HTTP/1.0");
            } else if (line.toLowerCase().startsWith("connection") || line.toLowerCase().startsWith("proxy-connection")) {
                line = line.replace("keep-alive", "close");
            }
            request = request + line + "\n";
        }
        System.out.println("POSTLOOP");
        return request;
    }

    private int getConnectionID() {
        l.lock();
        int id = 0;
        try {
            connectionID++;
            id = connectionID;
        } finally {
            l.unlock();
            return id;
        }
    }

    class readThread extends Thread {

        private BufferedReader in;
        private int connectionID;
        private boolean connect;

        readThread(BufferedReader in, int connectionID, boolean connect) {
            this.in = in;
            this.connectionID = connectionID;
            this.connect = connect;
        }

        public void run () {
            try {
                do {
                    System.out.println("IN READ");
                    System.out.println(in.ready());
                    String packet = in.readLine();
                    System.out.println("read:" + packet);
                    buffers.get(connectionID).add(packet);
                } while (connect);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    class writeThread extends Thread {

        private PrintWriter out;
        private int connectionID;
        private boolean connect;

        writeThread(PrintWriter out, int connectionID, boolean connect) {
            this.out = out;
            this.connectionID = connectionID;
            this.connect = connect;
        }

        public void run () {
            try {
                do {
                    String packet = buffers.get(connectionID).take();
                    System.out.println("write:" + packet);
                    out.write(packet);
                    out.flush();
                } while (connect);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

}
