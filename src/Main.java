import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Duplicate File Detection System.
 *
 * Scans a directory tree, groups files with identical content using
 * size filtering + SHA-256 hashing, and prints/writes a report.
 *
 * Usage: java Main [directory] [reportFile]
 */
public class Main {

    public static void main(String[] args) {
        Path root = Paths.get(args.length > 0 ? args[0] : ".");
        if (!Files.isDirectory(root)) {
            System.err.println("Error: '" + root + "' is not a directory.");
            System.exit(1);
        }

        Path reportFile = args.length > 1
                ? Paths.get(args[1])
                : root.resolve("duplicate-report.txt");

        System.out.println("Scanning: " + root.toAbsolutePath());
        DuplicateDetector detector = new DuplicateDetector();

        List<List<FileMetadata>> duplicates;
        try {
            duplicates = detector.findDuplicates(root);
        } catch (IOException e) {
            System.err.println("Scan failed: " + e.getMessage());
            System.exit(1);
            return;
        }

        DuplicateReport report = new DuplicateReport(duplicates);
        System.out.println(report.summary());
        System.out.println();
        System.out.println(report.format());

        try {
            report.writeTo(reportFile);
            System.out.println("Report written to: " + reportFile.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Could not write report: " + e.getMessage());
        }
    }
}
