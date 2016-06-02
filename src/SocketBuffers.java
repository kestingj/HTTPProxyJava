import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by josephkesting on 5/31/16.
 */
public class SocketBuffers {
    private static SocketBuffers instance = null;
    private static Map<Integer, BlockingQueue<String>> buffers = null;
    private static final Lock l = new ReentrantLock();
    private static int connectionID;

    private SocketBuffers() {
        buffers = new HashMap<>();
    }

    public static SocketBuffers getInstance() {
        if (instance == null) {
            instance = new SocketBuffers();
        }
        return instance;
    }

    public static int create() {
        int bufferID = getConnectionID();
        buffers.put(bufferID, new LinkedBlockingQueue<>());
        return bufferID;
    }

    public static String take(int id) {
        String retVal = null;
        try {
            retVal = buffers.get(id).take();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return retVal;
    }

    public static void put(int id, String payload) {
        try {
            buffers.get(id).put(payload);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void close(int id) {
        buffers.remove(id);
    }

    private static int getConnectionID() {
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
}
