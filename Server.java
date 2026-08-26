import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Server {

    private static final int DEFAULT_PORT = 10000;

    private static final String[] FRONTEND_PATHS = {
        "../frontend/index.html",
        "frontend/index.html",
        "./frontend/index.html",
        "/app/frontend/index.html",
        "index.html",
        "./index.html"
    };

    private static final String[] SAMPLE_LOG_PATHS = {
        "logfiles.log",
        "./logfiles.log",
        "data/logfiles.log",
        "./data/logfiles.log",
        "../data/logfiles.log"
    };

    public static void main(String[] args) {

        try {

            int port = getPort();

            System.out.println("======================================");
            System.out.println("       LOG ANALYSIS WEB SERVER");
            System.out.println("======================================");

            HttpServer server =
                HttpServer.create(
                    new InetSocketAddress("0.0.0.0", port),
                    0
                );

            server.createContext("/", new FrontendHandler());
            server.createContext("/api/analyze", new AnalyzeHandler());
            server.createContext("/api/upload", new UploadHandler());

            server.setExecutor(
                Executors.newCachedThreadPool()
            );

            server.start();

            System.out.println("Server started successfully!");
            System.out.println("Port: " + port);
            System.out.println("Website: http://localhost:" + port);
            System.out.println("Upload API: /api/upload");
            System.out.println("Analysis API: /api/analyze");
            System.out.println("======================================");

        } catch (Exception e) {

            System.err.println("Unable to start server.");
            e.printStackTrace();
        }
    }


    // ============================================================
    // PORT
    // ============================================================

    private static int getPort() {

        String port =
            System.getenv("PORT");

        if (port != null &&
            !port.trim().isEmpty()) {

            try {

                return Integer.parseInt(
                    port.trim()
                );

            } catch (Exception ignored) {
            }
        }

        return DEFAULT_PORT;
    }


    // ============================================================
    // FRONTEND
    // ============================================================

    static class FrontendHandler
        implements HttpHandler {

        @Override
        public void handle(
            HttpExchange exchange
        ) throws IOException {

            String method =
                exchange.getRequestMethod()
                    .toUpperCase();

            String path =
                exchange.getRequestURI()
                    .getPath();

            System.out.println(
                "Requested file: " + path +
                " [" + method + "]"
            );

            if (!path.equals("/") &&
                !path.equals("/index.html")) {

                sendText(
                    exchange,
                    404,
                    "404 - Page Not Found",
                    "text/plain; charset=UTF-8"
                );

                return;
            }

            if (!method.equals("GET") &&
                !method.equals("HEAD")) {

                sendText(
                    exchange,
                    405,
                    "Method Not Allowed",
                    "text/plain; charset=UTF-8"
                );

                return;
            }

            Path frontend =
                findFrontendFile();

            if (frontend == null) {

                sendText(
                    exchange,
                    500,
                    "Frontend file not found.",
                    "text/plain; charset=UTF-8"
                );

                return;
            }

            byte[] content =
                Files.readAllBytes(frontend);

            Headers headers =
                exchange.getResponseHeaders();

            headers.set(
                "Content-Type",
                "text/html; charset=UTF-8"
            );

            headers.set(
                "Cache-Control",
                "no-cache, no-store, must-revalidate"
            );

            headers.set(
                "Access-Control-Allow-Origin",
                "*"
            );

            /*
             * IMPORTANT:
             * HEAD must NOT write a response body.
             */

            if (method.equals("HEAD")) {

                exchange.sendResponseHeaders(
                    200,
                    -1
                );

                exchange.close();

                return;
            }

            exchange.sendResponseHeaders(
                200,
                content.length
            );

            try (OutputStream output =
                    exchange.getResponseBody()) {

                output.write(content);
                output.flush();
            }
        }
    }


    // ============================================================
    // SAMPLE ANALYSIS
    // ============================================================

    static class AnalyzeHandler
        implements HttpHandler {

        @Override
        public void handle(
            HttpExchange exchange
        ) throws IOException {

            String method =
                exchange.getRequestMethod()
                    .toUpperCase();

            System.out.println(
                "API request: /api/analyze [" +
                method +
                "]"
            );

            if (method.equals("OPTIONS")) {

                sendJson(
                    exchange,
                    200,
                    "{}"
                );

                return;
            }

            if (!method.equals("GET") &&
                !method.equals("HEAD")) {

                sendJson(
                    exchange,
                    405,
                    "{\"error\":\"Method Not Allowed\"}"
                );

                return;
            }

            try {

                Path logFile =
                    findSampleLogFile();

                String content = "";

                if (logFile != null) {

                    System.out.println(
                        "Sample log file: " +
                        logFile.toAbsolutePath()
                    );

                    content =
                        readFile(logFile);

                } else {

                    System.out.println(
                        "Sample log file not found."
                    );
                }

                Map<String, Object> result =
                    analyzeLogs(content);

                String json =
                    resultToJson(result);

                System.out.println(
                    "Sample analysis complete. " +
                    "Total logs = " +
                    result.get("totalLogs")
                );

                sendJson(
                    exchange,
                    200,
                    json
                );

            } catch (Exception e) {

                e.printStackTrace();

                sendJson(
                    exchange,
                    500,
                    "{\"error\":\"Unable to analyze sample dataset\"}"
                );
            }
        }
    }


    // ============================================================
    // UPLOAD
    // ============================================================

    static class UploadHandler
        implements HttpHandler {

        @Override
        public void handle(
            HttpExchange exchange
        ) throws IOException {

            String method =
                exchange.getRequestMethod()
                    .toUpperCase();

            System.out.println(
                "======================================"
            );

            System.out.println(
                "UPLOAD API REQUEST: " +
                method
            );

            System.out.println(
                "Content-Type: " +
                exchange.getRequestHeaders()
                    .getFirst("Content-Type")
            );

            System.out.println(
                "Content-Length: " +
                exchange.getRequestHeaders()
                    .getFirst("Content-Length")
            );

            System.out.println(
                "======================================"
            );

            if (method.equals("OPTIONS")) {

                sendJson(
                    exchange,
                    200,
                    "{}"
                );

                return;
            }

            if (!method.equals("POST")) {

                sendJson(
                    exchange,
                    405,
                    "{\"error\":\"Only POST is allowed\"}"
                );

                return;
            }

            try {

                String contentType =
                    exchange.getRequestHeaders()
                        .getFirst("Content-Type");

                /*
                 * IMPORTANT FIX:
                 *
                 * The current index.html sends:
                 *
                 * text/plain; charset=UTF-8
                 *
                 * Therefore we directly read the request body
                 * as the uploaded log file.
                 */

                if (contentType == null ||
                    !contentType
                        .toLowerCase()
                        .startsWith("text/plain")) {

                    System.out.println(
                        "Unexpected Content-Type: " +
                        contentType
                    );

                    sendJson(
                        exchange,
                        400,
                        "{\"error\":\"Expected text/plain request body\"}"
                    );

                    return;
                }

                String uploadedText =
                    readRequestBodyAsText(
                        exchange.getRequestBody()
                    );

                System.out.println(
                    "Received uploaded text."
                );

                System.out.println(
                    "Characters received: " +
                    uploadedText.length()
                );

                if (uploadedText.trim().isEmpty()) {

                    sendJson(
                        exchange,
                        400,
                        "{\"error\":\"Uploaded file is empty\"}"
                    );

                    return;
                }

                Map<String, Object> result =
                    analyzeLogs(uploadedText);

                String json =
                    resultToJson(result);

                System.out.println(
                    "UPLOAD ANALYSIS COMPLETE"
                );

                System.out.println(
                    "Total logs: " +
                    result.get("totalLogs")
                );

                System.out.println(
                    "Info: " +
                    result.get("info")
                );

                System.out.println(
                    "Warnings: " +
                    result.get("warnings")
                );

                System.out.println(
                    "Errors: " +
                    result.get("errors")
                );

                System.out.println(
                    "Critical: " +
                    result.get("critical")
                );

                sendJson(
                    exchange,
                    200,
                    json
                );

            } catch (Exception e) {

                System.err.println(
                    "UPLOAD ERROR:"
                );

                e.printStackTrace();

                sendJson(
                    exchange,
                    500,
                    "{\"error\":\"Unable to process uploaded file\"}"
                );
            }
        }
    }


    // ============================================================
    // FILE HELPERS
    // ============================================================

    private static Path findFrontendFile() {

        for (String location :
            FRONTEND_PATHS) {

            try {

                Path path =
                    Paths.get(location);

                if (Files.exists(path) &&
                    Files.isRegularFile(path)) {

                    return path;
                }

            } catch (Exception ignored) {
            }
        }

        return null;
    }


    private static Path findSampleLogFile() {

        for (String location :
            SAMPLE_LOG_PATHS) {

            try {

                Path path =
                    Paths.get(location);

                if (Files.exists(path) &&
                    Files.isRegularFile(path)) {

                    return path;
                }

            } catch (Exception ignored) {
            }
        }

        return null;
    }


    private static String readFile(
        Path path
    ) throws IOException {

        byte[] data =
            Files.readAllBytes(path);

        return new String(
            data,
            StandardCharsets.UTF_8
        );
    }


    private static String readRequestBodyAsText(
        InputStream input
    ) throws IOException {

        StringBuilder result =
            new StringBuilder();

        byte[] buffer =
            new byte[8192];

        int length;

        while (
            (length = input.read(buffer)) != -1
        ) {

            result.append(
                new String(
                    buffer,
                    0,
                    length,
                    StandardCharsets.UTF_8
                )
            );
        }

        return result.toString();
    }


    // ============================================================
    // LOG ANALYSIS
    // ============================================================

    private static Map<String, Object>
    analyzeLogs(
        String content
    ) {

        Map<String, Object> result =
            new LinkedHashMap<>();

        int totalLogs = 0;
        int info = 0;
        int warnings = 0;
        int errors = 0;
        int critical = 0;

        Map<String, Integer> statusCounts =
            new LinkedHashMap<>();

        Map<String, Integer> errorPatterns =
            new LinkedHashMap<>();

        if (content == null ||
            content.trim().isEmpty()) {

            result.put(
                "totalLogs",
                0
            );

            result.put(
                "info",
                0
            );

            result.put(
                "warnings",
                0
            );

            result.put(
                "errors",
                0
            );

            result.put(
                "critical",
                0
            );

            result.put(
                "statusCounts",
                statusCounts
            );

            result.put(
                "errorPatterns",
                errorPatterns
            );

            return result;
        }

        String[] lines =
            content.split("\\r?\\n");

        Pattern httpPattern =
            Pattern.compile(
                "\\b([1-5][0-9]{2})\\b"
            );

        for (String line :
            lines) {

            if (line == null ||
                line.trim().isEmpty()) {

                continue;
            }

            totalLogs++;

            String upper =
                line.toUpperCase();

            if (
                containsWord(
                    upper,
                    "CRITICAL"
                ) ||
                containsWord(
                    upper,
                    "FATAL"
                )
            ) {

                critical++;

            } else if (
                containsWord(
                    upper,
                    "ERROR"
                ) ||
                containsWord(
                    upper,
                    "ERR"
                )
            ) {

                errors++;

            } else if (
                containsWord(
                    upper,
                    "WARNING"
                ) ||
                containsWord(
                    upper,
                    "WARN"
                )
            ) {

                warnings++;

            } else {

                info++;
            }

            Matcher matcher =
                httpPattern.matcher(line);

            while (matcher.find()) {

                String status =
                    matcher.group(1);

                int code =
                    Integer.parseInt(status);

                if (
                    code >= 100 &&
                    code <= 599
                ) {

                    Integer old =
                        statusCounts.get(status);

                    statusCounts.put(
                        status,
                        old == null
                            ? 1
                            : old + 1
                    );

                    if (code >= 400) {

                        Integer oldError =
                            errorPatterns.get(
                                status
                            );

                        errorPatterns.put(
                            status,
                            oldError == null
                                ? 1
                                : oldError + 1
                        );
                    }
                }
            }
        }

        result.put(
            "totalLogs",
            totalLogs
        );

        result.put(
            "info",
            info
        );

        result.put(
            "warnings",
            warnings
        );

        result.put(
            "errors",
            errors
        );

        result.put(
            "critical",
            critical
        );

        result.put(
            "statusCounts",
            sortMapByValueDescending(
                statusCounts
            )
        );

        result.put(
            "errorPatterns",
            sortMapByValueDescending(
                errorPatterns
            )
        );

        return result;
    }


    private static boolean containsWord(
        String text,
        String word
    ) {

        return Pattern
            .compile(
                "\\b" +
                Pattern.quote(word) +
                "\\b"
            )
            .matcher(text)
            .find();
    }


    private static Map<String, Integer>
    sortMapByValueDescending(
        Map<String, Integer> input
    ) {

        List<Map.Entry<String, Integer>>
            entries =
                new ArrayList<>(
                    input.entrySet()
                );

        entries.sort(
            (a, b) ->
                Integer.compare(
                    b.getValue(),
                    a.getValue()
                )
        );

        Map<String, Integer> sorted =
            new LinkedHashMap<>();

        for (
            Map.Entry<String, Integer> entry :
            entries
        ) {

            sorted.put(
                entry.getKey(),
                entry.getValue()
            );
        }

        return sorted;
    }


    // ============================================================
    // JSON
    // ============================================================

    private static String resultToJson(
        Map<String, Object> result
    ) {

        StringBuilder json =
            new StringBuilder();

        json.append("{");

        json.append("\"totalLogs\":")
            .append(
                result.get("totalLogs")
            )
            .append(",");

        json.append("\"info\":")
            .append(
                result.get("info")
            )
            .append(",");

        json.append("\"warnings\":")
            .append(
                result.get("warnings")
            )
            .append(",");

        json.append("\"errors\":")
            .append(
                result.get("errors")
            )
            .append(",");

        json.append("\"critical\":")
            .append(
                result.get("critical")
            )
            .append(",");

        json.append("\"statusCounts\":")
            .append(
                mapToJson(
                    (Map<String, Integer>)
                        result.get(
                            "statusCounts"
                        )
                )
            )
            .append(",");

        json.append("\"errorPatterns\":")
            .append(
                errorPatternsToJson(
                    (Map<String, Integer>)
                        result.get(
                            "errorPatterns"
                        )
                )
            );

        json.append("}");

        return json.toString();
    }


    private static String mapToJson(
        Map<String, Integer> map
    ) {

        StringBuilder json =
            new StringBuilder();

        json.append("{");

        boolean first = true;

        for (
            Map.Entry<String, Integer> entry :
            map.entrySet()
        ) {

            if (!first) {
                json.append(",");
            }

            first = false;

            json.append("\"")
                .append(
                    jsonEscape(
                        entry.getKey()
                    )
                )
                .append("\":")
                .append(
                    entry.getValue()
                );
        }

        json.append("}");

        return json.toString();
    }


    private static String
    errorPatternsToJson(
        Map<String, Integer> map
    ) {

        StringBuilder json =
            new StringBuilder();

        json.append("{");

        boolean first = true;

        for (
            Map.Entry<String, Integer> entry :
            map.entrySet()
        ) {

            if (!first) {
                json.append(",");
            }

            first = false;

            int status =
                Integer.parseInt(
                    entry.getKey()
                );

            String description =
                getStatusDescription(
                    status
                );

            json.append("\"")
                .append(
                    jsonEscape(
                        entry.getKey()
                    )
                )
                .append("\":{");

            json.append("\"count\":")
                .append(
                    entry.getValue()
                )
                .append(",");

            json.append(
                "\"description\":\""
            )
                .append(
                    jsonEscape(
                        description
                    )
                )
                .append("\"}");
        }

        json.append("}");

        return json.toString();
    }


    // ============================================================
    // HTTP STATUS DESCRIPTION
    // ============================================================

    private static String
    getStatusDescription(
        int status
    ) {

        switch (status) {

            case 200:
                return "OK";

            case 201:
                return "Created";

            case 202:
                return "Accepted";

            case 204:
                return "No Content";

            case 301:
                return "Moved Permanently";

            case 302:
                return "Found";

            case 303:
                return "See Other";

            case 304:
                return "Not Modified";

            case 400:
                return "Bad Request";

            case 401:
                return "Unauthorized";

            case 403:
                return "Forbidden / Access Denied";

            case 404:
                return "Resource Not Found";

            case 405:
                return "Method Not Allowed";

            case 408:
                return "Request Timeout";

            case 409:
                return "Conflict";

            case 429:
                return "Too Many Requests";

            case 500:
                return "Internal Server Error";

            case 501:
                return "Not Implemented";

            case 502:
                return "Bad Gateway";

            case 503:
                return "Service Unavailable";

            case 504:
                return "Gateway Timeout";

            default:
                return "HTTP Status";
        }
    }


    // ============================================================
    // RESPONSE HELPERS
    // ============================================================

    private static void sendJson(
        HttpExchange exchange,
        int status,
        String json
    ) throws IOException {

        byte[] data =
            json.getBytes(
                StandardCharsets.UTF_8
            );

        Headers headers =
            exchange.getResponseHeaders();

        headers.set(
            "Content-Type",
            "application/json; charset=UTF-8"
        );

        headers.set(
            "Access-Control-Allow-Origin",
            "*"
        );

        headers.set(
            "Access-Control-Allow-Methods",
            "GET, POST, OPTIONS, HEAD"
        );

        headers.set(
            "Access-Control-Allow-Headers",
            "Content-Type"
        );

        String method =
            exchange.getRequestMethod()
                .toUpperCase();

        if (method.equals("HEAD")) {

            exchange.sendResponseHeaders(
                status,
                -1
            );

            exchange.close();

            return;
        }

        exchange.sendResponseHeaders(
            status,
            data.length
        );

        try (OutputStream output =
                exchange.getResponseBody()) {

            output.write(data);
            output.flush();
        }
    }


    private static void sendText(
        HttpExchange exchange,
        int status,
        String text,
        String contentType
    ) throws IOException {

        byte[] data =
            text.getBytes(
                StandardCharsets.UTF_8
            );

        Headers headers =
            exchange.getResponseHeaders();

        headers.set(
            "Content-Type",
            contentType
        );

        headers.set(
            "Access-Control-Allow-Origin",
            "*"
        );

        String method =
            exchange.getRequestMethod()
                .toUpperCase();

        if (method.equals("HEAD")) {

            exchange.sendResponseHeaders(
                status,
                -1
            );

            exchange.close();

            return;
        }

        exchange.sendResponseHeaders(
            status,
            data.length
        );

        try (OutputStream output =
                exchange.getResponseBody()) {

            output.write(data);
            output.flush();
        }
    }


    // ============================================================
    // JSON ESCAPE
    // ============================================================

    private static String jsonEscape(
        String value
    ) {

        if (value == null) {
            return "";
        }

        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "\\r")
            .replace("\n", "\\n")
            .replace("\t", "\\t");
    }
}
