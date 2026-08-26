import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogAnalyzer {

    private int infoCount = 0;
    private int warningCount = 0;
    private int errorCount = 0;
    private int criticalCount = 0;
    private int unknownCount = 0;

    // HTTP status frequency
    private final Map<Integer, Integer> statusCounts =
            new HashMap<>();

    // Recurring error patterns
    private final Map<String, Integer> errorPatterns =
            new HashMap<>();

    // Predefined error signatures
    private final List<String> errorSignatures =
            new ArrayList<>();

    // Detect HTTP status codes such as 200, 404, 500
    private static final Pattern STATUS_PATTERN =
            Pattern.compile("\\b([1-5]\\d{2})\\b");

    // Detect explicit severity levels
    private static final Pattern SEVERITY_PATTERN =
            Pattern.compile(
                    "\\b(INFO|WARN|WARNING|ERROR|CRITICAL|FATAL)\\b",
                    Pattern.CASE_INSENSITIVE
            );

    public LogAnalyzer() {

        // Error signatures
        errorSignatures.add("connection refused");
        errorSignatures.add("connection timeout");
        errorSignatures.add("timeout");
        errorSignatures.add("database error");
        errorSignatures.add("database connection");
        errorSignatures.add("out of memory");
        errorSignatures.add("memory usage");
        errorSignatures.add("internal server error");
        errorSignatures.add("bad gateway");
        errorSignatures.add("service unavailable");
        errorSignatures.add("gateway timeout");
        errorSignatures.add("file not found");
        errorSignatures.add("not found");
        errorSignatures.add("unauthorized");
        errorSignatures.add("forbidden");
        errorSignatures.add("server connection lost");
        errorSignatures.add("connection failed");
    }

    // =========================================================
    // ANALYZE ONE LOG LINE
    // =========================================================

    public void analyzeLine(String line) {

        if (line == null || line.trim().isEmpty()) {
            return;
        }

        String trimmedLine = line.trim();
        String lowerLine = trimmedLine.toLowerCase();

        boolean severityDetected = false;

        // =====================================================
        // 1. DETECT EXPLICIT SEVERITY
        // =====================================================

        Matcher severityMatcher =
                SEVERITY_PATTERN.matcher(trimmedLine);

        if (severityMatcher.find()) {

            String severity =
                    severityMatcher.group(1).toUpperCase();

            switch (severity) {

                case "INFO":
                    infoCount++;
                    severityDetected = true;
                    break;

                case "WARN":
                case "WARNING":
                    warningCount++;
                    severityDetected = true;
                    break;

                case "ERROR":
                    errorCount++;
                    severityDetected = true;
                    break;

                case "CRITICAL":
                case "FATAL":
                    criticalCount++;
                    severityDetected = true;
                    break;

                default:
                    break;
            }
        }

        // =====================================================
        // 2. DETECT HTTP STATUS CODE
        // =====================================================

        Matcher statusMatcher =
                STATUS_PATTERN.matcher(trimmedLine);

        if (statusMatcher.find()) {

            int statusCode =
                    Integer.parseInt(
                            statusMatcher.group(1)
                    );

            // Do not treat the date 2026 as an HTTP status.
            if (statusCode >= 100 && statusCode <= 599) {

                statusCounts.put(
                        statusCode,
                        statusCounts.getOrDefault(
                                statusCode,
                                0
                        ) + 1
                );

                // If no explicit severity was found,
                // classify using HTTP status code.
                if (!severityDetected) {

                    classifyHttpStatus(statusCode);

                    severityDetected = true;
                }
            }
        }

        // =====================================================
        // 3. DETECT ERROR SIGNATURES
        // =====================================================

        for (String signature : errorSignatures) {

            if (lowerLine.contains(signature)) {

                errorPatterns.put(
                        signature,
                        errorPatterns.getOrDefault(
                                signature,
                                0
                        ) + 1
                );
            }
        }

        // =====================================================
        // 4. UNKNOWN LOG
        // =====================================================

        if (!severityDetected) {
            unknownCount++;
        }
    }

    // =========================================================
    // CLASSIFY HTTP STATUS
    // =========================================================

    private void classifyHttpStatus(int statusCode) {

        if (statusCode >= 200 && statusCode < 400) {

            infoCount++;

        } else if (statusCode >= 400 && statusCode < 500) {

            warningCount++;

        } else if (statusCode >= 500 && statusCode < 600) {

            criticalCount++;

        } else {

            unknownCount++;
        }
    }

    // =========================================================
    // GET COUNTS
    // =========================================================

    public int getInfoCount() {
        return infoCount;
    }

    public int getWarningCount() {
        return warningCount;
    }

    public int getErrorCount() {
        return errorCount;
    }

    public int getCriticalCount() {
        return criticalCount;
    }

    public int getUnknownCount() {
        return unknownCount;
    }

    public int getTotalAnalyzed() {

        return infoCount
                + warningCount
                + errorCount
                + criticalCount
                + unknownCount;
    }

    // =========================================================
    // STATUS COUNTS
    // =========================================================

    public Map<Integer, Integer> getStatusCounts() {
        return statusCounts;
    }

    // =========================================================
    // ERROR PATTERNS
    // =========================================================

    public Map<String, Integer> getErrorPatterns() {
        return errorPatterns;
    }

    // =========================================================
    // STATUS DESCRIPTION
    // =========================================================

    private String getStatusDescription(int status) {

        switch (status) {

            case 100:
                return "Continue";

            case 200:
                return "OK";

            case 201:
                return "Created";

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

            case 408:
                return "Request Timeout";

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

    // =========================================================
    // JSON - STATUS COUNTS
    // =========================================================

    public String getStatusCountsJson() {

        StringBuilder json =
                new StringBuilder();

        json.append("{");

        boolean first = true;

        for (Map.Entry<Integer, Integer> entry :
                statusCounts.entrySet()) {

            if (!first) {
                json.append(",");
            }

            json.append("\"")
                    .append(entry.getKey())
                    .append("\":")
                    .append(entry.getValue());

            first = false;
        }

        json.append("}");

        return json.toString();
    }

    // =========================================================
    // JSON - ERROR PATTERNS
    // =========================================================

    public String getErrorPatternsJson() {

        StringBuilder json =
                new StringBuilder();

        json.append("{");

        boolean first = true;

        List<Map.Entry<String, Integer>> sorted =
                new ArrayList<>(
                        errorPatterns.entrySet()
                );

        sorted.sort(
                Map.Entry
                        .<String, Integer>
                        comparingByValue()
                        .reversed()
        );

        for (Map.Entry<String, Integer> entry :
                sorted) {

            if (!first) {
                json.append(",");
            }

            json.append("\"")
                    .append(escapeJson(entry.getKey()))
                    .append("\":")
                    .append(entry.getValue());

            first = false;
        }

        json.append("}");

        return json.toString();
    }

    // =========================================================
    // ESCAPE JSON
    // =========================================================

    private String escapeJson(String text) {

        if (text == null) {
            return "";
        }

        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    // =========================================================
    // CONSOLE REPORT
    // =========================================================

    public void printReport() {

        System.out.println();
        System.out.println("======================================");
        System.out.println("        LOG ANALYSIS REPORT");
        System.out.println("======================================");

        System.out.println(
                "Total Entries Analyzed : "
                        + getTotalAnalyzed()
        );

        System.out.println();
        System.out.println("Severity Summary");
        System.out.println("--------------------------------------");

        System.out.println(
                "INFO                  : "
                        + infoCount
        );

        System.out.println(
                "WARNING               : "
                        + warningCount
        );

        System.out.println(
                "ERROR                 : "
                        + errorCount
        );

        System.out.println(
                "CRITICAL              : "
                        + criticalCount
        );

        System.out.println(
                "UNKNOWN               : "
                        + unknownCount
        );

        System.out.println();
        System.out.println("HTTP Status Code Frequency");
        System.out.println("--------------------------------------");

        statusCounts.entrySet()
                .stream()
                .sorted(
                        Map.Entry
                                .<Integer, Integer>
                                comparingByValue()
                                .reversed()
                )
                .forEach(entry -> {

                    System.out.println(
                            "HTTP "
                                    + entry.getKey()
                                    + " - "
                                    + getStatusDescription(
                                            entry.getKey()
                                    )
                                    + " : "
                                    + entry.getValue()
                    );
                });

        System.out.println();
        System.out.println("Recurring Error Patterns");
        System.out.println("--------------------------------------");

        if (errorPatterns.isEmpty()) {

            System.out.println(
                    "No predefined error signatures detected."
            );

        } else {

            errorPatterns.entrySet()
                    .stream()
                    .sorted(
                            Map.Entry
                                    .<String, Integer>
                                    comparingByValue()
                                    .reversed()
                    )
                    .limit(10)
                    .forEach(entry -> {

                        System.out.println(
                                entry.getKey()
                                        + " : "
                                        + entry.getValue()
                                        + " occurrences"
                        );
                    });
        }

        System.out.println(
                "======================================"
        );
    }
}
