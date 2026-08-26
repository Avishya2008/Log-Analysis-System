import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class Main {

    public static void main(String[] args) {

        String filePath = "data/logfiles/logfiles.log";

        System.out.println("======================================");
        System.out.println("        LOG ANALYSIS SYSTEM");
        System.out.println("======================================");

        LogAnalyzer analyzer = new LogAnalyzer();

        int lineCount = 0;

        try (BufferedReader reader =
                new BufferedReader(new FileReader(filePath))) {

            String line;

            while ((line = reader.readLine()) != null) {

                lineCount++;

                // Analyze every log entry
                analyzer.analyzeLine(line);
            }

            System.out.println();
            System.out.println("Log file successfully read!");
            System.out.println("Total log entries: " + lineCount);

            // Display analysis report
            analyzer.printReport();

        } catch (IOException e) {

            System.out.println("Error reading log file.");
            System.out.println("Reason: " + e.getMessage());
        }
    }
}