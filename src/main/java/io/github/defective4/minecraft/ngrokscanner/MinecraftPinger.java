package io.github.defective4.minecraft.ngrokscanner;

import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Random;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.defective4.minecraft.chatlib.chat.ChatComponent;

public class MinecraftPinger {
    private static final int CONTINUE_BIT = 0x80;

    private static final int SEGMENT_BITS = 0x7F;

    private static final Random UNAME_RANDOM = new Random();

    private MinecraftPinger() {}

    public static PingResponse legacyPing(InetSocketAddress endpoint, int timeout) throws Exception {
        try (Socket socket = new Socket()) {
            socket.setSoTimeout(timeout);
            socket.connect(endpoint);
            OutputStream os = socket.getOutputStream();
            DataInputStream in = new DataInputStream(socket.getInputStream());
            os.write(new byte[] {
                    (byte) 0xfe, 0x01
            });
            int id = in.read();
            if (id != 0xff) throw new IOException("Invalid legacy status response ID: 0x" + Integer.toHexString(id));
            int len = in.readShort();
            if (len <= 0) throw new IOException("Invalid legacy status response length: " + len);

            StringBuilder statusBuilder = new StringBuilder();
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_16BE)) {
                reader.read(new char[3]);
                while (true) {
                    int read = reader.read();
                    if (read == -1) break;
                    char c = (char) read;
                    statusBuilder.append(c);
                }
            }
            String[] args = statusBuilder.toString().split("\0");
            try {
                int protocol = Integer.parseInt(args[0]);
                String version = args[1];
                int online = Integer.parseInt(args[3]);
                int max = Integer.parseInt(args[4]);
                return new PingResponse(version, protocol, online, max, new ChatComponent(args[2]), true);
            } catch (Exception e) {
                throw new IOException("Invalid legacy status response received");
            }
        }
    }

    public static PingResponse ping(InetSocketAddress endpoint, int protocol, int timeout) throws Exception {
        try (Socket socket = new Socket()) {
            socket.setSoTimeout(timeout);
            socket.connect(endpoint);
            OutputStream os = socket.getOutputStream();
            DataInputStream in = new DataInputStream(socket.getInputStream());
            os.write(prepareHandshake(protocol, endpoint.getHostString(), endpoint.getPort(), 1));
            os.write(new byte[] {
                    0x01, 0x00
            });
            int len = readVarInt(in);
            if (len < 2) throw new IOException("Illegal packet length: " + len);

            int id = in.read();
            if (id == -1) throw new IOException("End of stream reached");
            if (id != 0x00) throw new IOException("Illegal packed ID: 0x" + Integer.toHexString(id & 0xff));

            byte[] jsonData = new byte[readVarInt(in)];
            in.readFully(jsonData);
            String json = new String(jsonData, StandardCharsets.UTF_8);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            ChatComponent description = obj.has("description") ? ChatComponent.fromJson(obj.get("description")) : null;
            int prot = -1;
            int online = 0;
            int max = 0;
            String versionName = "Unknown";
            if (obj.has("version")) {
                JsonObject version = obj.getAsJsonObject("version");
                versionName = version.get("name").getAsString();
                prot = version.get("protocol").getAsInt();
            }
            if (obj.has("players")) {
                JsonObject players = obj.getAsJsonObject("players");
                online = players.get("online").getAsInt();
                max = players.get("max").getAsInt();
            }
            return new PingResponse(versionName, protocol, online, max, description, false);
        }
    }

    public static boolean tryJoin(InetSocketAddress endpoint, int timeout) {
        try (Socket socket = new Socket()) {
            socket.setSoTimeout(timeout);
            socket.connect(endpoint);
            OutputStream os = socket.getOutputStream();
            DataInputStream in = new DataInputStream(socket.getInputStream());

            StringBuilder loginUsername = new StringBuilder(8);
            for (int i = 0; i < loginUsername.capacity(); i++) loginUsername.append(UNAME_RANDOM.nextInt(10));

            os.write(prepareHandshake(-1, endpoint.getHostString(), endpoint.getPort(), 2));
            if (readVarInt(in) < 2) throw new IOException("Invalid packet length");
            if (readVarInt(in) != 0) throw new IOException("Invalid login response");

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static byte[] prepareHandshake(int protocol, String host, int port, int state) {
        byte[] rawData;
        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                DataOutputStream wrapper = new DataOutputStream(buffer)) {
            wrapper.write(0);
            writeVarInt(wrapper, protocol);
            byte[] hostData = host.getBytes(StandardCharsets.UTF_8);
            writeVarInt(wrapper, hostData.length);
            wrapper.write(hostData);
            wrapper.writeShort(port);
            wrapper.write(state);
            wrapper.flush();
            rawData = buffer.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        try (ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            writeVarInt(buffer, rawData.length);
            buffer.write(rawData);
            return buffer.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static int readVarInt(DataInput input) throws IOException {
        int value = 0;
        int position = 0;
        byte currentByte;

        while (true) {
            currentByte = input.readByte();
            value |= (currentByte & SEGMENT_BITS) << position;

            if ((currentByte & CONTINUE_BIT) == 0) break;

            position += 7;

            if (position >= 32) throw new RuntimeException("VarInt is too big");
        }

        return value;
    }

    private static void writeVarInt(OutputStream output, int value) throws IOException {
        while (true) {
            if ((value & ~SEGMENT_BITS) == 0) {
                output.write(value);
                return;
            }

            output.write(value & SEGMENT_BITS | CONTINUE_BIT);
            value >>>= 7;
        }
    }
}
