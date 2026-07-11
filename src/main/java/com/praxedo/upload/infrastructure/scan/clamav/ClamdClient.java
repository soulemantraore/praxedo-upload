package com.praxedo.upload.infrastructure.scan.clamav;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Client bas niveau du protocole clamd (commande {@code zINSTREAM}) : une connexion TCP par scan.
 * Pas de dependance externe ; le protocole est simple (chunks longueur-prefixee, reponse null-terminee).
 */
public class ClamdClient {

    private static final int CHUNK_SIZE = 4096;

    private final String host;
    private final int port;
    private final int timeoutMs;

    public ClamdClient(String host, int port) {
        this(host, port, 30_000);
    }

    public ClamdClient(String host, int port, int timeoutMs) {
        this.host = host;
        this.port = port;
        this.timeoutMs = timeoutMs;
    }

    public enum Status { OK, FOUND, ERROR }

    public record Response(Status status, String detail) {
    }

    /** Envoie le contenu a clamd via INSTREAM et renvoie le verdict brut. */
    public Response scan(InputStream data) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            OutputStream out = socket.getOutputStream();
            out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            byte[] buffer = new byte[CHUNK_SIZE];
            int read;
            while ((read = data.read(buffer)) != -1) {
                if (read == 0) {
                    continue;
                }
                out.write(intToNetworkBytes(read));
                out.write(buffer, 0, read);
            }
            out.write(intToNetworkBytes(0)); // chunk de taille 0 = fin du flux
            out.flush();
            return parse(readNullTerminated(socket.getInputStream()));
        }
    }

    /** PING/PONG : verifie que clamd repond (utile pour attendre qu'il soit pret). */
    public boolean ping() {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMs);
            socket.setSoTimeout(timeoutMs);
            socket.getOutputStream().write("zPING\0".getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            return readNullTerminated(socket.getInputStream()).contains("PONG");
        } catch (IOException e) {
            return false;
        }
    }

    private static byte[] intToNetworkBytes(int value) {
        return new byte[]{
            (byte) (value >>> 24), (byte) (value >>> 16), (byte) (value >>> 8), (byte) value
        };
    }

    private static String readNullTerminated(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        int b;
        while ((b = in.read()) != -1) {
            if (b == 0) {
                break;
            }
            sb.append((char) b);
        }
        return sb.toString().trim();
    }

    private static Response parse(String reply) {
        // ex : "stream: OK" / "stream: Eicar-Test-Signature FOUND" / "... ERROR"
        if (reply.endsWith("OK")) {
            return new Response(Status.OK, null);
        }
        if (reply.endsWith("FOUND")) {
            String detail = reply.substring(reply.indexOf(':') + 1, reply.lastIndexOf("FOUND")).trim();
            return new Response(Status.FOUND, detail);
        }
        return new Response(Status.ERROR, reply);
    }
}
