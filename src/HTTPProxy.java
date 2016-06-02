import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;



/**
 * Created by josephkesting on 5/26/16.
 */
public class HTTPProxy {

    Map<Integer, BlockingQueue<char[]>> buffers;
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
        while (true) {
            try {
                Socket clientSocket = serverSocket.accept();
                Thread setupThread = new SetupThread(clientSocket);
                setupThread.start();
            } catch (IOException e) {
                System.out.println(e);
            }
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
                String[] splitLine = line.split(":", 2);
                String url = splitLine[1].trim().split(" ")[0];
                String[] split = url.split(":");
                host = split[0];
                if(split.length > 1) {
                    port = Integer.parseInt(split[1].split("/")[0]);
                }

            }
        }
        if (port == -1) {
            String[] splitLine = requestLine.split(" ");

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
            } catch (ArrayIndexOutOfBoundsException e) {
                System.out.println("CANT BE SPLIT: ");
                System.out.println(requestLine);
            }

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
            String line;
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
                System.out.println(">>> " + line);
            } else if (line.toLowerCase().startsWith("connection") || line.toLowerCase().startsWith("proxy-connection")) {
                line = line.replace("keep-alive", "close");
            }
            request = request + line + "\r\n";
        }
        request += "\r\n";
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
        private InputStream i;

        readThread(BufferedReader in, int connectionID, boolean connect, InputStream i) {
            System.out.println(in);
            this.in = in;
            this.connectionID = connectionID;
            this.connect = connect;
            this.i = i;
        }

        public void run () {
            System.out.println("read thread: " + connectionID);
            BlockingQueue<char[]> buffer = buffers.get(connectionID);
            System.out.println("BUFFER SIZE: " + buffer.size());
            try {
                do {
                    System.out.println("IN READ");
                    System.out.println(in.ready());
//                    char[] packetBuffer = new char[65000];
                    int counter = 0;
                    char[] packetBuffer = new char[8192];
                    try {
//                        while ((in.read(packetBuffer, 0, 65000)) != -1) {
                        while (in.read(packetBuffer) > 0) {
                            System.out.println(in.ready());
                            //String packet = new String(packetBuffer);
//                            System.out.println("in: " + packet.length());
                            try {
                                buffer.put(packetBuffer);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                            System.out.println("read thread: " + connectionID + "counter: " + counter);
                            counter++;
                        }
                        System.out.println("OUT OF READ LOOP");
                    } catch (SocketException e) {
                        System.out.println("connection closed");
                    }

                } while (connect);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    class writeThread extends Thread {

//        private DataOutputStream out;
        PrintWriter out;
        private int connectionID;
        private boolean connect;

        writeThread(PrintWriter out, int connectionID, boolean connect) {
            System.out.println(out);
            this.out = out;
            this.connectionID = connectionID;
            this.connect = connect;
        }

        public void run () {
            System.out.println("write thread: " + connectionID);
            BlockingQueue<char[]> buffer = buffers.get(connectionID);
            try {
                do {
                    char[] packet;
                    int counter = 0;
                    while ((packet = buffer.take()) != null) {
//                        System.out.println("out: " + packet.length());
//                        try {
                            out.print(packet);
                            out.flush();
//                        } catch (IOException e) {
//                            e.printStackTrace();
//                        }

                        System.out.println("write thread: " + connectionID + "counter: " + counter);
                        counter++;
                    }
                    System.out.println("OUT OF WRITE LOOP");
                } while (connect);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    class SetupThread extends Thread {

        Socket clientSocket;

        public SetupThread(Socket clientSocket) {
            this.clientSocket = clientSocket;
        }

        public void run() {
//            PrintWriter clientOut = null;
            DataOutputStream clientOut = null;
            BufferedReader clientIn = null;
            String request;
            int clientToHostID = getConnectionID();
            try {
                System.out.println(3);
//                clientOut =
//                        new PrintWriter(clientSocket.getOutputStream(), true);
                clientOut = new DataOutputStream(clientSocket.getOutputStream());
                clientIn = new BufferedReader(
                        new InputStreamReader(clientSocket.getInputStream()));
            } catch (IOException e) {
                System.out.println(e);
            }
            request = getModifiedRequest(clientIn);
            System.out.println(4);
            if (request.trim().length() > 0) {
                INetAddress address = getAddressFromMessage(request);
                System.out.println(5);

                PrintWriter hostOut = null;
//                DataOutputStream hostOut = null;
//                BufferedReader hostIn = null;
                InputStream hostIn = null;
                int hostToClientID = getConnectionID();
                InputStream i = null;
                try {
                    Socket hostSocket = new Socket();
                    System.out.println(6);
                    InetSocketAddress addr = new InetSocketAddress(0);
                    hostSocket.bind(addr);
                    hostSocket.connect(new InetSocketAddress(address.getHost(), address.getPort()));
                    System.out.println(7);
//                    hostOut = new DataOutputStream(hostSocket.getOutputStream());
                    hostOut = new PrintWriter(hostSocket.getOutputStream(), true);
//                    hostIn = new BufferedReader(
//                            new InputStreamReader(hostSocket.getInputStream()));
                    hostIn = hostSocket.getInputStream();
                    i = hostSocket.getInputStream();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                boolean connect = request.startsWith("CONNECT");


//                BlockingQueue<char[]> clientToHost = new LinkedBlockingQueue<>();
//                BlockingQueue<char[]> hostToClient = new LinkedBlockingQueue<>();
//                clientToHost.add(request.toCharArray());
//                buffers.put(clientToHostID, clientToHost);
//                buffers.put(hostToClientID, hostToClient);
//
//                System.out.println(8);
//                Thread clientRead = new readThread(clientIn, clientToHostID, connect, i);
//                Thread clientWrite = new writeThread(clientOut, hostToClientID, connect);
//                Thread hostRead = new readThread(hostIn, hostToClientID, connect, i);
//                Thread hostWrite = new writeThread(hostOut, clientToHostID, connect);
//                hostWrite.start();
//                hostRead.start();
//                clientWrite.start();

                ////////////////

                hostOut.print(request);
                hostOut.flush();

                byte[] buffer = new byte[8192];
                int bytesRead = 0;
                try {
                    bytesRead = hostIn.read(buffer);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                while (bytesRead > 0) {
                    try {
                        clientOut.write(buffer, 0, bytesRead);
                        clientOut.flush();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    try {
                        bytesRead = hostIn.read(buffer);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
//                clientRead.start();
//                System.out.println(9);
            }
        }
    }


}
