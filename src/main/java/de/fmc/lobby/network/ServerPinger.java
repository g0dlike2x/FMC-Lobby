package de.fmc.lobby.network;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import de.fmc.lobby.teleporter.ServerStatus;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Minimaler Server-List-Ping (Status-Protokoll seit 1.7). Läuft ausschließlich asynchron.
 */
public final class ServerPinger {

    /** Schutz gegen kaputte Antworten */
    private static final int MAX_RESPONSE = 1 << 20;

    private ServerPinger() {
    }

    /**
     * @return Status mit Online/Max oder {@link ServerStatus#OFFLINE}, wenn der Server nicht antwortet
     */
    public static ServerStatus ping(String host, int port, int timeoutMs) {
        try (Socket socket = new Socket()) {
            socket.setSoTimeout(timeoutMs);
            socket.setTcpNoDelay(true);
            socket.connect(new InetSocketAddress(host, port), timeoutMs);

            DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));

            // Handshake: Paket 0x00, Protokoll -1, Host, Port, nächster Status 1 (Status)
            ByteArrayOutputStream handshakeBytes = new ByteArrayOutputStream();
            DataOutputStream handshake = new DataOutputStream(handshakeBytes);
            writeVarInt(handshake, 0x00);
            writeVarInt(handshake, -1);
            writeString(handshake, host);
            handshake.writeShort(port);
            writeVarInt(handshake, 1);

            writeVarInt(out, handshakeBytes.size());
            out.write(handshakeBytes.toByteArray());

            // Status-Request: Länge 1, Paket 0x00
            writeVarInt(out, 1);
            writeVarInt(out, 0x00);
            out.flush();

            int length = readVarInt(in);
            if (length <= 0 || length > MAX_RESPONSE) {
                return ServerStatus.OFFLINE;
            }
            int packetId = readVarInt(in);
            if (packetId != 0x00) {
                return ServerStatus.OFFLINE;
            }
            int jsonLength = readVarInt(in);
            if (jsonLength <= 0 || jsonLength > MAX_RESPONSE) {
                return ServerStatus.OFFLINE;
            }
            byte[] data = new byte[jsonLength];
            in.readFully(data);

            JsonObject root = JsonParser.parseString(new String(data, StandardCharsets.UTF_8)).getAsJsonObject();
            int online = 0;
            int max = 0;
            JsonElement playersElement = root.get("players");
            if (playersElement != null && playersElement.isJsonObject()) {
                JsonObject players = playersElement.getAsJsonObject();
                online = players.has("online") ? players.get("online").getAsInt() : 0;
                max = players.has("max") ? players.get("max").getAsInt() : 0;
            }
            return new ServerStatus(true, online, max);
        } catch (IOException | RuntimeException e) {
            return ServerStatus.OFFLINE;
        }
    }

    private static void writeVarInt(OutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    private static int readVarInt(InputStream in) throws IOException {
        int value = 0;
        int position = 0;
        while (true) {
            int b = in.read();
            if (b == -1) {
                throw new IOException("Verbindung beendet");
            }
            value |= (b & 0x7F) << position;
            if ((b & 0x80) == 0) {
                return value;
            }
            position += 7;
            if (position >= 32) {
                throw new IOException("VarInt zu lang");
            }
        }
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(out, bytes.length);
        out.write(bytes);
    }
}
