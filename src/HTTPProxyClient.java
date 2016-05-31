/**
 * Created by josephkesting on 5/29/16.
 */
public class HTTPProxyClient {
    public static void main(String[] args) {
        int port = Integer.parseInt(args[0]);
        System.out.println(port);
        HTTPProxy proxy = new HTTPProxy(port);
        proxy.run();
    }
}
