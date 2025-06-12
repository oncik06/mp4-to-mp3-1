import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.*;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;

public class App {
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        // Serve HTML form
        server.createContext("/", exchange -> {
            byte[] html = Files.readAllBytes(Paths.get("public/index.html"));
            exchange.getResponseHeaders().add("Content-Type", "text/html");
            exchange.sendResponseHeaders(200, html.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(html);
            }
        });

        // Handle MP4 upload
        server.createContext("/upload", exchange -> {
            if (!exchange.getRequestMethod().equalsIgnoreCase("POST")) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            String boundary = exchange.getRequestHeaders().getFirst("Content-Type").split("boundary=")[1];
            byte[] body = exchange.getRequestBody().readAllBytes();

            int start = indexOf(body, "Content-Type: video/mp4".getBytes()) + 4;
            int headerEnd = indexOf(body, "\r\n\r\n".getBytes(), start) + 4;
            int fileEnd = indexOf(body, ("--" + boundary).getBytes(), headerEnd) - 2;

            byte[] videoData = Arrays.copyOfRange(body, headerEnd, fileEnd);

            Files.createDirectories(Paths.get("uploads"));
            String mp4Path = "uploads/input.mp4";
            String mp3Path = "uploads/output.mp3";

            Files.write(Paths.get(mp4Path), videoData);

            ProcessBuilder pb = new ProcessBuilder("ffmpeg", "-i", mp4Path, "-q:a", "0", "-map", "a", mp3Path);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try {
                p.waitFor();
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

            byte[] mp3 = Files.readAllBytes(Paths.get(mp3Path));
            exchange.getResponseHeaders().add("Content-Disposition", "attachment; filename=\"output.mp3\"");
            exchange.getResponseHeaders().add("Content-Type", "audio/mpeg");
            exchange.sendResponseHeaders(200, mp3.length);

            try (OutputStream os = exchange.getResponseBody()) {
                os.write(mp3);
            }

            Files.deleteIfExists(Paths.get(mp4Path));
            Files.deleteIfExists(Paths.get(mp3Path));
        });

        server.setExecutor(null);
        server.start();
        System.out.println("Server running at http://localhost:8080");
    }

    private static int indexOf(byte[] data, byte[] pattern) {
        return indexOf(data, pattern, 0);
    }

    private static int indexOf(byte[] data, byte[] pattern, int start) {
        outer:
        for (int i = start; i < data.length - pattern.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}
