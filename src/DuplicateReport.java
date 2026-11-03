import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Builds a human-readable report from duplicate groups and can write it
 * to a text file (file handling).
 */
public class DuplicateReport {

    private final List<List<FileMetadata>> groups;

    public DuplicateReport(List<List<FileMetadata>> groups) {
        this.groups = groups;
    }

    public int groupCount() {
        return groups.size();
    }

    /** Bytes that could be freed by deleting all but one copy of each group. */
    public long wastedBytes() {
        long wasted = 0;
        for (List<FileMetadata> group : groups) {
            long size = group.get(0).getSize();
            wasted += size * (group.size() - 1L);
        }
        return wasted;
    }

    public String summary() {
        return String.format("Found %d duplicate group(s); %s could be reclaimed.",
                groupCount(), humanReadable(wastedBytes()));
    }

    /** Full report body: one block per duplicate group. */
    public String format() {
        if (groups.isEmpty()) {
            return "No duplicate files found.";
        }
        StringBuilder sb = new StringBuilder();
        int index = 1;
        for (List<FileMetadata> group : groups) {
            sb.append("Group ").append(index++)
              .append(" - ").append(group.size()).append(" copies, ")
              .append(humanReadable(group.get(0).getSize()))
              .append(" each:\n");
            for (FileMetadata meta : group) {
                sb.append("  ").append(meta.getPath().toAbsolutePath()).append('\n');
            }
            sb.append('\n');
        }
        return sb.toString().stripTrailing();
    }

    /** Writes the summary and full report to a text file. */
    public void writeTo(Path file) throws IOException {
        String content = summary() + "\n\n" + format() + "\n";
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }

    public static String humanReadable(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double value = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int i = 0;
        while (value >= 1024 && i < units.length - 1) {
            value /= 1024;
            i++;
        }
        return String.format("%.1f %s", value, units[i]);
    }
}
