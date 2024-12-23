package io.github.defective4.minecraft.ngrokscanner;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import io.github.defective4.minecraft.chatlib.chat.ChatComponent;

public class PingResponse {
    private transient final ChatComponent description;
    private final boolean isUnlisted, isLegacy;
    private final int maxPlayers;
    private final int players;
    private final int protocol;
    private final String version;

    public PingResponse() {
        this("Unknown", 0, 0, 0, null, false, true);
    }

    public PingResponse(String version, int protocol, int players, int maxPlayers, ChatComponent description,
            boolean isLegacy) {
        this(version, protocol, players, maxPlayers, description, isLegacy, false);
    }

    private PingResponse(String version, int protocol, int players, int maxPlayers, ChatComponent description,
            boolean isLegacy, boolean isUnlisted) {
        this.version = version;
        this.protocol = protocol;
        this.players = players;
        this.maxPlayers = maxPlayers;
        this.description = description;
        this.isUnlisted = isUnlisted;
        this.isLegacy = isLegacy;
    }

    public ChatComponent getDescription() {
        return description;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public int getPlayers() {
        return players;
    }

    public int getProtocol() {
        return protocol;
    }

    public String getVersion() {
        return version;
    }

    public boolean isLegacy() {
        return isLegacy;
    }

    public boolean isUnlisted() {
        return isUnlisted;
    }

    public String toJson(String host, int port) {
        JsonObject obj = new Gson().toJsonTree(this).getAsJsonObject();
        obj.addProperty("host", host);
        obj.addProperty("port", port);
        if (description != null) obj.addProperty("description", description.toPlainString());
        return obj.toString();
    }

    @Override
    public String toString() {
        return "PingResponse [version=" + version + ", protocol=" + protocol + ", players=" + players + ", maxPlayers="
                + maxPlayers + "]";
    }

}
