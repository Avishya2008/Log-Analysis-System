import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Server {

    // ============================================================
    // CONFIGURATION
    // ============================================================

    private static final int DEFAULT_PORT = 10000;

    private static final String[] FRONTEND_PATHS = {
    "../frontend/index.html",
    ".././frontend/index.html",
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
            "./data/logfiles.log"
    };

    // ============================================================
    // MAIN
    // ============================================================

    public static void main(String[] args) {

        try {

            int port = getPort();

            System.out.println();
            System.out.println("======================================");
            System.out.println("       LOG ANALYSIS WEB SERVER");
            System.out.println("======================================");
            System.out.println();

            HttpServer server =
                    HttpServer.create(
                            new InetSocketAddress("0.0.0.0", port),
                            0
                    );

            // ----------------------------------------------------
            // ROUTES
            // ----------------------------------------------------

            server.createContext("/", new FrontendHandler());

            server.createContext(
                    "/api/analyze",
                    new AnalyzeHandler()
            );

            server.createContext(
                    "/api/upload",
                    new UploadHandler()
            );

            server.setExecutor(
                    Executors.newCachedThreadPool()
            );

            server.start();

            System.out.println("Server started successfully!");
            System.out.println("Port: " + port);
            System.out.println();
            System.out.println(
                    "Website:"
            );
            System.out.println(
                    "http://localhost:" + port
            );
            System.out.println();
            System.out.println(
                    "Analyze API:"
            );
            System.out.println(
                    "http://localhost:" +
                            port +
                            "/api/analyze"
            );
            System.out.println();
            System.out.println(
                    "Upload API:"
            );
            System.out.println(
                    "http://localhost:" +
                            port +
                            "/api/upload"
            );
            System.out.println();
            System.out.println(
                    "Keep this server running."
            );
            System.out.println();

        } catch (Exception e) {

            System.err.println(
                    "Unable to start server."
            );

            e.printStackTrace();
        }
    }

    // ============================================================
    // PORT
    // ============================================================

    private static int getPort() {

        String environmentPort =
                System.getenv("PORT");

        if (
                environmentPort != null &&
                !environmentPort.isBlank()
        ) {

            try {

                return Integer.parseInt(
                        environmentPort.trim()
                );

            } catch (NumberFormatException e) {

                System.out.println(
                        "Invalid PORT environment variable. " +
                        "Using default port."
                );
            }
        }

        return DEFAULT_PORT;
    }

    // ============================================================
    // FRONTEND HANDLER
    // ============================================================

    static class FrontendHandler
            implements HttpHandler {

        @Override
        public void handle(
                HttpExchange exchange
        ) throws IOException {

            String method =
                    exchange
                            .getRequestMethod()
                            .toUpperCase();

            String path =
                    exchange
                            .getRequestURI()
                            .getPath();

            System.out.println(
                    "Requested file: " + path
            );

            // ----------------------------------------------------
            // Only serve index.html for the root route.
            // API routes are handled separately.
            // ----------------------------------------------------

            if (
                    !path.equals("/") &&
                    !path.equals("/index.html")
            ) {

                sendText(
                        exchange,
                        404,
                        "404 - Page Not Found",
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
                    "no-cache"
            );

            headers.set(
                    "Access-Control-Allow-Origin",
                    "*"
            );

            // ----------------------------------------------------
            // IMPORTANT:
            // Render/browser may send HEAD requests.
            //
            // A HEAD request must return headers WITHOUT
            // writing the body.
            //
            // This prevents the:
            //
            // java.io.IOException:
            // connection closed before all data received
            //
            // problem shown in your Render logs.
            // ----------------------------------------------------

            if (method.equals("HEAD")) {

                exchange.sendResponseHeaders(
                        200,
                        -1
                );

                exchange.close();

                return;
            }

            if (!method.equals("GET")) {

                sendText(
                        exchange,
                        405,
                        "Method Not Allowed",
                        "text/plain; charset=UTF-8"
                );

                return;
            }

            exchange.sendResponseHeaders(
                    200,
                    content.length
            );

            try (
                    OutputStream output =
                            exchange.getResponseBody()
            ) {

                output.write(content);
                output.flush();
            }

            exchange.close();
        }
    }

    // ============================================================
    // ANALYZE HANDLER
    // ============================================================

    static class AnalyzeHandler
            implements HttpHandler {

        @Override
        public void handle(
                HttpExchange exchange
        ) throws IOException {

            String method =
                    exchange
                            .getRequestMethod()
                            .toUpperCase();

            if (
                    !method.equals("GET") &&
                    !method.equals("HEAD")
            ) {

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

                    content =
                            Files.readString(
                                    logFile,
                                    StandardCharsets.UTF_8
                            );
                }

                Map<String, Object> result =
                        analyzeLogs(content);

                String json =
                        resultToJson(result);

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
    // UPLOAD HANDLER
    // ============================================================

    static class UploadHandler
            implements HttpHandler {

        @Override
        public void handle(
                HttpExchange exchange
        ) throws IOException {

            String method =
                    exchange
                            .getRequestMethod()
                            .toUpperCase();

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
                        exchange
                                .getRequestHeaders()
                                .getFirst(
                                        "Content-Type"
                                );

                if (
                        contentType == null ||
                        !contentType
                                .toLowerCase()
                                .startsWith(
                                        "multipart/form-data"
                                )
                ) {

                    sendJson(
                            exchange,
                            400,
                            "{\"error\":\"Expected multipart/form-data upload\"}"
                    );

                    return;
                }

                byte[] requestBody;

                try (
                        InputStream input =
                                exchange.getRequestBody()
                ) {

                    requestBody =
                            input.readAllBytes();
                }

                String uploadedText =
                        extractUploadedFile(
                                requestBody,
                                contentType
                        );

                if (
                        uploadedText == null ||
                        uploadedText.isBlank()
                ) {

                    sendJson(
                            exchange,
                            400,
                            "{\"error\":\"Uploaded file could not be read\"}"
                    );

                    return;
                }

                Map<String, Object> result =
                        analyzeLogs(uploadedText);

                String json =
                        resultToJson(result);

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
                        "{\"error\":\"Unable to process uploaded file\"}"
                );
            }
        }
    }

    // ============================================================
    // FIND FRONTEND
    // ============================================================

    private static Path findFrontendFile() {

        for (String location :
                FRONTEND_PATHS) {

            try {

                Path path =
                        Paths.get(location);

                if (
                        Files.exists(path) &&
                        Files.isRegularFile(path)
                ) {

                    System.out.println(
                            "Frontend found: " +
                            path.toAbsolutePath()
                    );

                    return path;
                }

            } catch (Exception ignored) {
            }
        }

        return null;
    }

    // ============================================================
    // FIND SAMPLE LOG
    // ============================================================

    private static Path findSampleLogFile() {

        for (String location :
                SAMPLE_LOG_PATHS) {

            try {

                Path path =
                        Paths.get(location);

                if (
                        Files.exists(path) &&
                        Files.isRegularFile(path)
                ) {

                    System.out.println(
                            "Log dataset found: " +
                            path.toAbsolutePath()
                    );

                    return path;
                }

            } catch (Exception ignored) {
            }
        }

        return null;
    }

    // ============================================================
    // LOG ANALYSIS
    // ============================================================

    private static Map<String, Object>
    analyzeLogs(String content) {

        Map<String, Object> result =
                new LinkedHashMap<>();

        int totalLogs = 0;
        int info = 0;
        int warnings = 0;
        int errors = 0;
        int critical = 0;

        Map<String, Integer>
                statusCounts =
                new LinkedHashMap<>();

        Map<String, Integer>
                errorPatterns =
                new LinkedHashMap<>();

        if (
                content == null ||
                content.isBlank()
        ) {

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
                content.split(
                        "\\r?\\n"
                );

        Pattern httpPattern =
                Pattern.compile(
                        "\\b([1-5][0-9]{2})\\b"
                );

        for (String line : lines) {

            if (
                    line == null ||
                    line.trim().isEmpty()
            ) {
                continue;
            }

            totalLogs++;

            String upper =
                    line.toUpperCase();

            // ----------------------------------------------------
            // SEVERITY
            // ----------------------------------------------------

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

            // ----------------------------------------------------
            // HTTP STATUS CODES
            // ----------------------------------------------------

            Matcher matcher =
                    httpPattern.matcher(line);

            while (
                    matcher.find()
            ) {

                String status =
                        matcher.group(1);

                int code =
                        Integer.parseInt(
                                status
                        );

                // Avoid treating random numbers such as years
                // as HTTP status codes.

                if (
                        code >= 100 &&
                        code <= 599
                ) {

                    statusCounts.put(
                            status,
                            statusCounts.getOrDefault(
                                    status,
                                    0
                            ) + 1
                    );

                    // Error pattern counts
                    if (code >= 400) {

                        errorPatterns.put(
                                status,
                                errorPatterns.getOrDefault(
                                        status,
                                        0
                                ) + 1
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

    // ============================================================
    // WORD DETECTION
    // ============================================================

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

    // ============================================================
    // SORT MAP
    // ============================================================

    private static Map<String, Integer>
    sortMapByValueDescending(
            Map<String, Integer> input
    ) {

        List<
                Map.Entry<String, Integer>
                > entries =
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
    // MULTIPART FILE EXTRACTION
    // ============================================================

    private static String extractUploadedFile(
            byte[] body,
            String contentType
    ) {

        try {

            String boundary =
                    getBoundary(
                            contentType
                    );

            if (
                    boundary == null ||
                    boundary.isBlank()
            ) {

                return null;
            }

            byte[] boundaryBytes =
                    (
                            "--" +
                            boundary
                    )
                    .getBytes(
                            StandardCharsets.ISO_8859_1
                    );

            int boundaryPosition =
                    indexOf(
                            body,
                            boundaryBytes,
                            0
                    );

            if (
                    boundaryPosition < 0
            ) {

                return null;
            }

            int headerStart =
                    boundaryPosition +
                    boundaryBytes.length;

            int headerEnd =
                    indexOf(
                            body,
                            new byte[]{
                                    '\r',
                                    '\n',
                                    '\r',
                                    '\n'
                            },
                            headerStart
                    );

            if (
                    headerEnd < 0
            ) {

                return null;
            }

            String headers =
                    new String(
                            body,
                            headerStart,
                            headerEnd -
                                    headerStart,
                            StandardCharsets.ISO_8859_1
                    );

            int dataStart =
                    headerEnd + 4;

            byte[] endBoundary =
                    (
                            "\r\n--" +
                            boundary
                    )
                    .getBytes(
                            StandardCharsets.ISO_8859_1
                    );

            int dataEnd =
                    indexOf(
                            body,
                            endBoundary,
                            dataStart
                    );

            if (
                    dataEnd < 0
            ) {

                dataEnd =
                        body.length;
            }

            // Make sure this is the file field.
            if (
                    !headers
                            .toLowerCase()
                            .contains(
                                    "filename="
                            )
            ) {

                return null;
            }

            return new String(
                    body,
                    dataStart,
                    Math.max(
                            0,
                            dataEnd - dataStart
                    ),
                    StandardCharsets.UTF_8
            );

        } catch (Exception e) {

            e.printStackTrace();

            return null;
        }
    }

    // ============================================================
    // GET MULTIPART BOUNDARY
    // ============================================================

    private static String getBoundary(
            String contentType
    ) {

        Pattern pattern =
                Pattern.compile(
                        "boundary=([^;]+)",
                        Pattern.CASE_INSENSITIVE
                );

        Matcher matcher =
                pattern.matcher(
                        contentType
                );

        if (!matcher.find()) {

            return null;
        }

        String boundary =
                matcher.group(1).trim();

        if (
                boundary.startsWith("\"") &&
                boundary.endsWith("\"")
        ) {

            boundary =
                    boundary.substring(
                            1,
                            boundary.length() - 1
                    );
        }

        return boundary;
    }

    // ============================================================
    // BYTE ARRAY INDEX
    // ============================================================

    private static int indexOf(
            byte[] source,
            byte[] target,
            int start
    ) {

        if (
                target.length == 0
        ) {

            return start;
        }

        outer:
        for (
                int i = start;
                i <= source.length -
                        target.length;
                i++
        ) {

            for (
                    int j = 0;
                    j < target.length;
                    j++
            ) {

                if (
                        source[i + j] !=
                        target[j]
                ) {

                    continue outer;
                }
            }

            return i;
        }

        return -1;
    }

    // ============================================================
    // JSON RESPONSE
    // ============================================================

    private static String resultToJson(
            Map<String, Object> result
    ) {

        StringBuilder json =
                new StringBuilder();

        json.append("{");

        json.append(
                "\"totalLogs\":"
        );

        json.append(
                result.get("totalLogs")
        );

        json.append(",");

        json.append(
                "\"info\":"
        );

        json.append(
                result.get("info")
        );

        json.append(",");

        json.append(
                "\"warnings\":"
        );

        json.append(
                result.get("warnings")
        );

        json.append(",");

        json.append(
                "\"errors\":"
        );

        json.append(
                result.get("errors")
        );

        json.append(",");

        json.append(
                "\"critical\":"
        );

        json.append(
                result.get("critical")
        );

        json.append(",");

        json.append(
                "\"statusCounts\":"
        );

        json.append(
                mapToJson(
                        (Map<String, Integer>)
                                result.get(
                                        "statusCounts"
                                )
                )
        );

        json.append(",");

        json.append(
                "\"errorPatterns\":"
        );

        json.append(
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

    // ============================================================
    // STATUS MAP JSON
    // ============================================================

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

            json.append(
                    "\"" +
                    jsonEscape(
                            entry.getKey()
                    ) +
                    "\":"
            );

            json.append(
                    entry.getValue()
            );
        }

        json.append("}");

        return json.toString();
    }

    // ============================================================
    // ERROR PATTERN JSON
    // ============================================================

    private static String errorPatternsToJson(
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

            json.append(
                    "\"" +
                    jsonEscape(
                            entry.getKey()
                    ) +
                    "\":{"
            );

            json.append(
                    "\"count\":" +
                    entry.getValue()
            );

            json.append(",");

            json.append(
                    "\"description\":\"" +
                    jsonEscape(
                            description
                    ) +
                    "\""
            );

            json.append("}");
        }

        json.append("}");

        return json.toString();
    }

    // ============================================================
    // HTTP STATUS DESCRIPTIONS
    // ============================================================

    private static String getStatusDescription(
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
    // SEND JSON
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
                exchange
                        .getRequestMethod()
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

        try (
                OutputStream output =
                        exchange.getResponseBody()
        ) {

            output.write(data);
            output.flush();
        }

        exchange.close();
    }

    // ============================================================
    // SEND TEXT
    // ============================================================

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
                exchange
                        .getRequestMethod()
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

        try (
                OutputStream output =
                        exchange.getResponseBody()
        ) {

            output.write(data);
            output.flush();
        }

        exchange.close();
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
                .replace(
                        "\\",
                        "\\\\"
                )
                .replace(
                        "\"",
                        "\\\""
                )
                .replace(
                        "\r",
                        "\\r"
                )
                .replace(
                        "\n",
                        "\\n"
                )
                .replace(
                        "\t",
                        "\\t"
                );
    }
}