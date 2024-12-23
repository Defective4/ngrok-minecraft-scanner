package io.github.defective4.minecraft.ngrokscanner;

import static java.util.Collections.unmodifiableList;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.defective4.minecraft.ngrokscanner.NgrokScanner.HostPortPair;

public class NgrokScanner {
    public static interface AddressResolverCallback {
        void resolved(String host, boolean success, String address);

        void resolving(String host);
    }

    public static class HostPortPair {
        private final String host;
        private final int port;

        public HostPortPair(String host, int port) {
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

    public static interface ScannerCallback {
        void discovered(PingResponse data, String host, int port);

        void scanned(String host, int port);
    }

    private static final int LOWER_PORT = 10000;

    private static final int UPPER_PORT = 20000;

    private NgrokScanner() {}

    public static List<HostPortPair> generateTargetAddresses(Collection<String> hosts) {
        List<HostPortPair> addresses = new ArrayList<>();
        for (String host : hosts) {
            for (int i = LOWER_PORT; i < UPPER_PORT; i++) addresses.add(new HostPortPair(host, i));
        }
        return unmodifiableList(addresses);
    }

    public static Map<String, String> resolveAllNgrokHostnames(String region, AddressResolverCallback cb) {
        if (region == null || "us".equalsIgnoreCase(region)) region = ".";
        else region = "." + region + ".";
        Map<String, String> addresses = new HashMap<>();
        for (int x = 0; x < 10; x++) {
            String host = x + ".tcp" + region + "ngrok.io";
            try {
                if (cb != null) cb.resolving(host);
                String ip = InetAddress.getByName(host).getHostAddress();
                if (!addresses.containsValue(ip)) addresses.put(host, ip);
                if (cb != null) cb.resolved(host, true, ip);
            } catch (UnknownHostException e) {
                e.printStackTrace();
                if (cb != null) cb.resolved(host, false, null);
            }
        }
        return Collections.unmodifiableMap(addresses);
    }

    public static void scan(List<List<HostPortPair>> list, ScannerCallback callback, int legacy, boolean join,
            int timeout) {
        for (List<HostPortPair> pairs : list) {
            new Thread(() -> {
                for (HostPortPair pair : pairs) {
                    try {
                        InetSocketAddress addr = new InetSocketAddress(pair.getHost(), pair.getPort());
                        if (legacy > 1) {
                            PingResponse data = MinecraftPinger.legacyPing(addr, timeout);
                            callback.discovered(data, pair.getHost(), pair.getPort());
                        } else {
                            PingResponse data;
                            try {
                                try {
                                    data = MinecraftPinger.ping(addr, 754, timeout);
                                } catch (Exception e) {
                                    if (legacy != 1) throw e;
                                    data = MinecraftPinger.legacyPing(addr, timeout);
                                }
                            } catch (Exception e) {
                                if (!join) throw e;
                                if (MinecraftPinger.tryJoin(addr, timeout)) data = new PingResponse();
                                else throw e;
                            }
                            callback.discovered(data, pair.getHost(), pair.getPort());
                        }
                    } catch (Exception e) {}
                    callback.scanned(pair.getHost(), pair.getPort());
                }
            }).start();
        }
    }

    public static <T> List<List<T>> split(List<T> list, int parts) {
        List<List<T>> split = new ArrayList<>();
        double partSize = list.size() / (double) parts;
        for (int i = 0; i < list.size(); i++) {
            int part = (int) (i / partSize);
            while (split.size() <= part) split.add(new ArrayList<>());
            split.get(part).add(list.get(i));
        }

        return unmodifiableList(split);
    }
}
