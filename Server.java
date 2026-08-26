import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Server {

    private static final String SAMPLE_FILE =
            "data/logfiles/logfiles.log";

    private static final String FRONTEND_PATH =
            "frontend";

    public static void main(String[] args) throws Exception {

        System.out.println("======================================");
        System.out.println("       LOG ANALYSIS WEB SERVER");
        System.out.println("======================================");

        HttpServer server = HttpServer.create(
                new InetSocketAddress(8080),
                0
        );

        // Website
        server.createContext("/", Server::serveFrontend);

        // Analyze the original sample dataset
        server.createContext(
                "/api/analyze",
                Server::analyzeSample
        );

        // Analyze an uploaded dataset
        server.createContext(
                "/api/upload",
                Server::uploadAndAnalyze
        );

        server.start();

        System.out.println();
        System.out.println("Server started successfully!");
        System.out.println();
        System.out.println("Website:");
        System.out.println("http://localhost:8080");
        System.out.println();
        System.out.println("Upload API:");
        System.out.println("http://localhost:8080/api/upload");
        System.out.println();
        System.out.println("Keep this terminal running.");
    }


    // =========================================================
    // SERVE FRONTEND
    // =========================================================

    private static void serveFrontend(
            HttpExchange exchange) {

        try {

            String requestPath =
                    exchange.getRequestURI().getPath();

            if (requestPath.equals("/")) {
                requestPath = "/index.html";
            }

            if (requestPath.contains("..")) {

                sendResponse(
                        exchange,
                        403,
                        "text/plain",
                        "Forbidden"
                );

                return;
            }

            Path filePath = Paths.get(
                    FRONTEND_PATH + requestPath
            );

            if (!Files.exists(filePath)
                    || Files.isDirectory(filePath)) {

                sendResponse(
                        exchange,
                        404,
                        "text/plain",
                        "404 - File Not Found"
                );

                return;
            }

            byte[] content =
                    Files.readAllBytes(filePath);

            String contentType =
                    getContentType(filePath.toString());

            exchange.getResponseHeaders().set(
                    "Content-Type",
                    contentType
            );

            exchange.sendResponseHeaders(
                    200,
                    content.length
            );

            try (OutputStream output =
                         exchange.getResponseBody()) {

                output.write(content);
            }

        } catch (Exception e) {

            try {

                sendResponse(
                        exchange,
                        500,
                        "text/plain",
                        "Server error: "
                                + e.getMessage()
                );

            } catch (Exception ignored) {
            }
        }
    }


    // =========================================================
    // CONTENT TYPE
    // =========================================================

    private static String getContentType(
            String fileName) {

        if (fileName.endsWith(".html")) {
            return "text/html; charset=UTF-8";
        }

        if (fileName.endsWith(".css")) {
            return "text/css; charset=UTF-8";
        }

        if (fileName.endsWith(".js")) {
            return "application/javascript; charset=UTF-8";
        }

        if (fileName.endsWith(".json")) {
            return "application/json; charset=UTF-8";
        }

        if (fileName.endsWith(".png")) {
            return "image/png";
        }

        if (fileName.endsWith(".jpg")
                || fileName.endsWith(".jpeg")) {

            return "image/jpeg";
        }

        if (fileName.endsWith(".svg")) {
            return "image/svg+xml";
        }

        return "application/octet-stream";
    }


    // =========================================================
    // ANALYZE SAMPLE DATASET
    // =========================================================

    private static void analyzeSample(
            HttpExchange exchange) {

        try {

            LogAnalyzer analyzer =
                    new LogAnalyzer();

            int totalLines = 0;

            try (BufferedReader reader =
                         new BufferedReader(
                                 new FileReader(
                                         SAMPLE_FILE))) {

                String line;

                while ((line = reader.readLine())
                        != null) {

                    totalLines++;

                    analyzer.analyzeLine(line);
                }
            }

            String json =
                    createJson(
                            analyzer,
                            totalLines
                    );

            sendResponse(
                    exchange,
                    200,
                    "application/json; charset=UTF-8",
                    json
            );

        } catch (Exception e) {

            sendError(
                    exchange,
                    e.getMessage()
            );
        }
    }


    // =========================================================
    // UPLOAD AND ANALYZE USER DATASET
    // =========================================================

    private static void uploadAndAnalyze(
            HttpExchange exchange) {

        try {

            if (!exchange.getRequestMethod()
                    .equalsIgnoreCase("POST")) {

                sendResponse(
                        exchange,
                        405,
                        "text/plain",
                        "POST method required"
                );

                return;
            }

            String contentType =
                    exchange.getRequestHeaders()
                            .getFirst("Content-Type");

            if (contentType == null
                    || !contentType
                    .startsWith("multipart/form-data")) {

                sendResponse(
                        exchange,
                        400,
                        "application/json; charset=UTF-8",
                        "{\"error\":\"Please upload a file using multipart/form-data.\"}"
                );

                return;
            }

            String boundary =
                    getBoundary(contentType);

            if (boundary == null) {

                sendResponse(
                        exchange,
                        400,
                        "application/json; charset=UTF-8",
                        "{\"error\":\"Upload boundary not found.\"}"
                );

                return;
            }

            byte[] requestData =
                    readAllBytes(
                            exchange.getRequestBody()
                    );

            byte[] fileData =
                    extractUploadedFile(
                            requestData,
                            boundary
                    );

            if (fileData == null
                    || fileData.length == 0) {

                sendResponse(
                        exchange,
                        400,
                        "application/json; charset=UTF-8",
                        "{\"error\":\"No file was uploaded.\"}"
                );

                return;
            }

            LogAnalyzer analyzer =
                    new LogAnalyzer();

            int totalLines = 0;

            /*
             * Process the uploaded file line by line.
             * The complete file is not converted into a
             * giant String for analysis.
             */
            try (BufferedReader reader =
                         new BufferedReader(
                                 new java.io.InputStreamReader(
                                         new java.io.ByteArrayInputStream(
                                                 fileData
                                         ),
                                         StandardCharsets.UTF_8
                                 ))) {

                String line;

                while ((line = reader.readLine())
                        != null) {

                    totalLines++;

                    analyzer.analyzeLine(line);
                }
            }

            String json =
                    createJson(
                            analyzer,
                            totalLines
                    );

            sendResponse(
                    exchange,
                    200,
                    "application/json; charset=UTF-8",
                    json
            );

        } catch (Exception e) {

            e.printStackTrace();

            sendError(
                    exchange,
                    e.getMessage()
            );
        }
    }


    // =========================================================
    // GET MULTIPART BOUNDARY
    // =========================================================

    private static String getBoundary(
            String contentType) {

        String[] parts =
                contentType.split(";");

        for (String part : parts) {

            part = part.trim();

            if (part.startsWith("boundary=")) {

                String boundary =
                        part.substring(
                                "boundary=".length()
                        );

                if (boundary.startsWith("\"")
                        && boundary.endsWith("\"")) {

                    boundary =
                            boundary.substring(
                                    1,
                                    boundary.length() - 1
                            );
                }

                return boundary;
            }
        }

        return null;
    }


    // =========================================================
    // READ REQUEST BODY
    // =========================================================

    private static byte[] readAllBytes(
            InputStream input)
            throws IOException {

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        byte[] buffer =
                new byte[8192];

        int bytesRead;

        while ((bytesRead =
                input.read(buffer)) != -1) {

            output.write(
                    buffer,
                    0,
                    bytesRead
            );
        }

        return output.toByteArray();
    }


    // =========================================================
    // EXTRACT FILE FROM MULTIPART REQUEST
    // =========================================================

    private static byte[] extractUploadedFile(
            byte[] data,
            String boundary) {

        String body =
                new String(
                        data,
                        StandardCharsets.ISO_8859_1
                );

        String marker =
                "--" + boundary;

        int headerStart =
                body.indexOf(marker);

        if (headerStart == -1) {
            return null;
        }

        int headerEnd =
                body.indexOf(
                        "\r\n\r\n",
                        headerStart
                );

        if (headerEnd == -1) {
            return null;
        }

        int fileStart =
                headerEnd + 4;

        int fileEnd =
                body.indexOf(
                        "\r\n" + marker,
                        fileStart
                );

        if (fileEnd == -1) {

            fileEnd =
                    body.indexOf(
                            marker,
                            fileStart
                    );
        }

        if (fileEnd == -1
                || fileEnd <= fileStart) {

            return null;
        }

        return java.util.Arrays.copyOfRange(
                data,
                fileStart,
                fileEnd
        );
    }


    // =========================================================
    // CREATE JSON RESPONSE
    // =========================================================

    private static String createJson(
            LogAnalyzer analyzer,
            int totalLines) {

        return "{"
                + "\"totalLogs\":"
                + totalLines
                + ","
                + "\"info\":"
                + analyzer.getInfoCount()
                + ","
                + "\"warnings\":"
                + analyzer.getWarningCount()
                + ","
                + "\"errors\":"
                + analyzer.getErrorCount()
                + ","
                + "\"critical\":"
                + analyzer.getCriticalCount()
                + ","
                + "\"unknown\":"
                + analyzer.getUnknownCount()
                + ","
                + "\"statusCounts\":"
                + analyzer.getStatusCountsJson()
                + ","
                + "\"errorPatterns\":"
                + analyzer.getErrorPatternsJson()
                + "}";
    }


    // =========================================================
    // SEND RESPONSE
    // =========================================================

    private static void sendResponse(
            HttpExchange exchange,
            int status,
            String contentType,
            String content)
            throws IOException {

        byte[] response =
                content.getBytes(
                        StandardCharsets.UTF_8
                );

        exchange.getResponseHeaders().set(
                "Content-Type",
                contentType
        );

        exchange.getResponseHeaders().set(
                "Access-Control-Allow-Origin",
                "*"
        );

        exchange.sendResponseHeaders(
                status,
                response.length
        );

        try (OutputStream output =
                     exchange.getResponseBody()) {

            output.write(response);
        }
    }


    // =========================================================
    // SEND ERROR
    // =========================================================

    private static void sendError(
            HttpExchange exchange,
            String message) {

        try {

            String error =
                    "{\"error\":\""
                    + escapeJson(message)
                    + "\"}";

            sendResponse(
                    exchange,
                    500,
                    "application/json; charset=UTF-8",
                    error
            );

        } catch (IOException ignored) {
        }
    }


    // =========================================================
    // ESCAPE JSON
    // =========================================================

    private static String escapeJson(
            String text) {

        if (text == null) {
            return "Unknown error";
        }

        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}