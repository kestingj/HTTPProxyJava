/**
 * Created by josephkesting on 5/26/16.
 */
public class INetAddress {
    private String host;
    private int port;

    public INetAddress(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }
}
