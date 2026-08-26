import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
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

            HttpServer server = HttpServer.create(
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

            System.out.println();
            System.out.println("Server started successfully!");
            System.out.println();
            System.out.println("Port: " + port);
            System.out.println();
            System.out.println("Website:");
            System.out.println(
                "http://localhost:" + port
            );
            System.out.println();
            System.out.println("Upload API:");
            System.out.println(
                "http://localhost:" + port + "/api/upload"
            );
            System.out.println();
            System.out.println("Keep this server running.");
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

        String port =
            System.getenv("PORT");

        if (
            port != null &&
            !port.trim().isEmpty()
        ) {

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
                exchange
                    .getRequestMethod()
                    .toUpperCase();

            String path =
                exchange
                    .getRequestURI()
                    .getPath();

            System.out.println(
                "Requested " +
                method +
                " file: " +
                path
            );

            // ----------------------------------------------------
            // OPTIONS
            // ----------------------------------------------------

            if (method.equals("OPTIONS")) {

                sendEmpty(
                    exchange,
                    204
                );

                return;
            }

            // ----------------------------------------------------
            // Only allow frontend pages
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
                Files.readAllBytes(
                    frontend
                );

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

            // ----------------------------------------------------
            // IMPORTANT:
            // HEAD requests must NOT have a response body.
            // ----------------------------------------------------

            if (method.equals("HEAD")) {

                exchange.sendResponseHeaders(
                    200,
                    -1
                );

                exchange.close();

                return;
            }

            // ----------------------------------------------------
            // Only GET after this point
            // ----------------------------------------------------

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

            } finally {

                exchange.close();
            }
        }
    }

    // ============================================================
    // SAMPLE DATASET
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

            if (method.equals("OPTIONS")) {

                sendEmpty(
                    exchange,
                    204
                );

                return;
            }

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
                        readFile(logFile);
                }

                Map<String, Object> result =
                    analyzeLogs(content);

                sendJson(
                    exchange,
                    200,
                    resultToJson(result)
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
                exchange
                    .getRequestMethod()
                    .toUpperCase();

            System.out.println(
                "Upload request received. Method: " +
                method
            );

            // ----------------------------------------------------
            // OPTIONS
            // ----------------------------------------------------

            if (method.equals("OPTIONS")) {

                sendEmpty(
                    exchange,
                    204
                );

                return;
            }

            // ----------------------------------------------------
            // POST only
            // ----------------------------------------------------

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
                        .getFirst("Content-Type");

                System.out.println(
                    "Upload Content-Type: " +
                    contentType
                );

                if (contentType == null) {

                    sendJson(
                        exchange,
                        400,
                        "{\"error\":\"Missing Content-Type\"}"
                    );

                    return;
                }

                // ------------------------------------------------
                // READ REQUEST BODY
                //
                // The current index.html sends:
                //
                // Content-Type:
                // text/plain; charset=UTF-8
                //
                // Therefore we read the body directly.
                // ------------------------------------------------

                byte[] body =
                    readRequestBody(
                        exchange.getRequestBody()
                    );

                System.out.println(
                    "Received upload bytes: " +
                    body.length
                );

                if (body.length == 0) {

                    sendJson(
                        exchange,
                        400,
                        "{\"error\":\"Uploaded file is empty\"}"
                    );

                    return;
                }

                String uploadedText;

                // ------------------------------------------------
                // Support BOTH:
                //
                // 1. text/plain
                // 2. multipart/form-data
                // ------------------------------------------------

                if (
                    contentType
                        .toLowerCase()
                        .startsWith("multipart/form-data")
                ) {

                    uploadedText =
                        extractUploadedFile(
                            body,
                            contentType
                        );

                } else {

                    uploadedText =
                        new String(
                            body,
                            StandardCharsets.UTF_8
                        );
                }

                if (
                    uploadedText == null ||
                    uploadedText.trim().isEmpty()
                ) {

                    sendJson(
                        exchange,
                        400,
                        "{\"error\":\"Could not read uploaded file or file is empty\"}"
                    );

                    return;
                }

                System.out.println(
                    "Uploaded text length: " +
                    uploadedText.length()
                );

                // ------------------------------------------------
                // ANALYZE
                // ------------------------------------------------

                Map<String, Object> result =
                    analyzeLogs(uploadedText);

                String json =
                    resultToJson(result);

                System.out.println(
                    "Analysis completed successfully."
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
    // FRONTEND FILE
    // ============================================================

    private static Path findFrontendFile() {

        for (
            String location :
            FRONTEND_PATHS
        ) {

            try {

                Path path =
                    Paths.get(location);

                if (
                    Files.exists(path) &&
                    Files.isRegularFile(path)
                ) {

                    return path;
                }

            } catch (Exception ignored) {
            }
        }

        return null;
    }

    // ============================================================
    // SAMPLE LOG FILE
    // ============================================================

    private static Path findSampleLogFile() {

        for (
            String location :
            SAMPLE_LOG_PATHS
        ) {

            try {

                Path path =
                    Paths.get(location);

                if (
                    Files.exists(path) &&
                    Files.isRegularFile(path)
                ) {

                    return path;
                }

            } catch (Exception ignored) {
            }
        }

        return null;
    }

    // ============================================================
    // READ FILE
    // ============================================================

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

    // ============================================================
    // READ REQUEST BODY
    // ============================================================

    private static byte[] readRequestBody(
        InputStream input
    ) throws IOException {

        ByteArrayOutputStream output =
            new ByteArrayOutputStream();

        byte[] buffer =
            new byte[8192];

        int length;

        while (
            (length =
                input.read(buffer)) != -1
        ) {

            output.write(
                buffer,
                0,
                length
            );
        }

        return output.toByteArray();
    }

    // ============================================================
    // LOG ANALYSIS
    // ============================================================

    private static Map<String, Object>
    analyzeLogs(
        String content
    ) {

        Map<String, Object> result =
            new LinkedHashMap<String, Object>();

        int totalLogs = 0;
        int info = 0;
        int warnings = 0;
        int errors = 0;
        int critical = 0;

        Map<String, Integer> statusCounts =
            new LinkedHashMap<String, Integer>();

        Map<String, Integer> errorPatterns =
            new LinkedHashMap<String, Integer>();

        // --------------------------------------------------------
        // EMPTY DATASET
        // --------------------------------------------------------

        if (
            content == null ||
            content.trim().isEmpty()
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

        // --------------------------------------------------------
        // SPLIT LOG LINES
        // --------------------------------------------------------

        String[] lines =
            content.split("\\r?\\n");

        // --------------------------------------------------------
        // HTTP STATUS CODE
        // --------------------------------------------------------

        Pattern httpPattern =
            Pattern.compile(
                "\\b([1-5][0-9]{2})\\b"
            );

        // --------------------------------------------------------
        // ANALYZE EACH LINE
        // --------------------------------------------------------

        for (
            String line :
            lines
        ) {

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
                    Integer.parseInt(status);

                if (
                    code >= 100 &&
                    code <= 599
                ) {

                    Integer old =
                        statusCounts.get(
                            status
                        );

                    statusCounts.put(
                        status,
                        old == null
                            ? 1
                            : old + 1
                    );

                    // 4xx and 5xx are treated
                    // as error patterns.

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

        // --------------------------------------------------------
        // RESULT
        // --------------------------------------------------------

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
    // WORD CHECK
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

        List<Map.Entry<String, Integer>> entries =
            new ArrayList<Map.Entry<String, Integer>>(
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
            new LinkedHashMap<String, Integer>();

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
    // MULTIPART EXTRACTION
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

            if (boundary == null) {
                return null;
            }

            byte[] boundaryBytes =
                (
                    "--" +
                    boundary
                ).getBytes(
                    StandardCharsets.ISO_8859_1
                );

            int firstBoundary =
                indexOf(
                    body,
                    boundaryBytes,
                    0
                );

            if (firstBoundary < 0) {
                return null;
            }

            int headerStart =
                firstBoundary +
                boundaryBytes.length;

            int headerEnd =
                indexOf(
                    body,
                    new byte[] {
                        '\r',
                        '\n',
                        '\r',
                        '\n'
                    },
                    headerStart
                );

            if (headerEnd < 0) {
                return null;
            }

            String headers =
                new String(
                    body,
                    headerStart,
                    headerEnd - headerStart,
                    StandardCharsets.ISO_8859_1
                );

            if (
                !headers
                    .toLowerCase()
                    .contains("filename=")
            ) {

                return null;
            }

            int dataStart =
                headerEnd + 4;

            byte[] endBoundary =
                (
                    "\r\n--" +
                    boundary
                ).getBytes(
                    StandardCharsets.ISO_8859_1
                );

            int dataEnd =
                indexOf(
                    body,
                    endBoundary,
                    dataStart
                );

            if (dataEnd < 0) {

                dataEnd =
                    body.length;
            }

            if (dataEnd < dataStart) {
                return null;
            }

            return new String(
                body,
                dataStart,
                dataEnd - dataStart,
                StandardCharsets.UTF_8
            );

        } catch (Exception e) {

            e.printStackTrace();

            return null;
        }
    }

    // ============================================================
    // BOUNDARY
    // ============================================================

    private static String getBoundary(
        String contentType
    ) {

        Pattern pattern =
            Pattern.compile(
                "boundary\\s*=\\s*(\"[^\"]+\"|[^;]+)",
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
    // BYTE ARRAY SEARCH
    // ============================================================

    private static int indexOf(
        byte[] source,
        byte[] target,
        int start
    ) {

        if (target.length == 0) {
            return start;
        }

        outer:

        for (
            int i = start;
            i <= source.length - target.length;
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
    // JSON RESULT
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

    // ============================================================
    // MAP TO JSON
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

    // ============================================================
    // ERROR PATTERNS JSON
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

            json.append("\"description\":\"")
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

    private static String getStatusDescription(
        int status
    ) {

        switch (status) {

            case 100:
                return "Continue";

            case 101:
                return "Switching Protocols";

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

            case 307:
                return "Temporary Redirect";

            case 308:
                return "Permanent Redirect";

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

            case 410:
                return "Gone";

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

        // --------------------------------------------------------
        // HEAD must never send body
        // --------------------------------------------------------

        if (
            exchange
                .getRequestMethod()
                .equalsIgnoreCase("HEAD")
        ) {

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

        } finally {

            exchange.close();
        }
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

        // --------------------------------------------------------
        // HEAD must never send body
        // --------------------------------------------------------

        if (
            exchange
                .getRequestMethod()
                .equalsIgnoreCase("HEAD")
        ) {

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

        } finally {

            exchange.close();
        }
    }

    // ============================================================
    // SEND EMPTY RESPONSE
    // ============================================================

    private static void sendEmpty(
        HttpExchange exchange,
        int status
    ) throws IOException {

        Headers headers =
            exchange.getResponseHeaders();

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

        exchange.sendResponseHeaders(
            status,
            -1
        );

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
