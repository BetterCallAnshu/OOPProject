import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Walks a directory tree and finds groups of files with identical content.
 *
 * Strategy (uses collections heavily):
 *  1. Group every file by size in a HashMap - files of unique size cannot
 *     be duplicates, so they are discarded early.
 *  2. Hash only the remaining candidates with SHA-256 and group them by
 *     hash + size.
 *  3. Keep groups with more than one member as duplicate sets.
 */
public class DuplicateDetector {

    private final FileHasher hasher = new FileHasher();

    /**
     * Finds all duplicate file groups under the given root directory.
     *
     * @return a list of groups; each group is a list of files sharing
     *         identical content, sorted by path
     * @throws IOException if the directory tree cannot be walked
     */
    public List<List<FileMetadata>> findDuplicates(Path root) throws IOException {
        Map<Long, List<Path>> bySize = collectBySize(root);

        Map<String, List<FileMetadata>> byHash = new HashMap<>();
        for (Map.Entry<Long, List<Path>> entry : bySize.entrySet()) {
            List<Path> candidates = entry.getValue();
            if (candidates.size() < 2) {
                continue; // unique size -> cannot be a duplicate
            }
            hashCandidates(candidates, entry.getKey(), byHash);
        }

        return buildGroups(byHash);
    }

    /**
     * Recursively collects regular files, keyed by byte size.
     * Inaccessible files/directories (e.g. protected system folders) are
     * skipped with a warning instead of aborting the whole scan.
     */
    private Map<Long, List<Path>> collectBySize(Path root) throws IOException {
        Map<Long, List<Path>> bySize = new HashMap<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (attrs.isRegularFile()) {
                    bySize.computeIfAbsent(attrs.size(), k -> new ArrayList<>()).add(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path file, IOException exc) {
                System.err.println("Skipping (inaccessible): " + file + " - " + exc.getMessage());
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) {
                if (exc != null) {
                    System.err.println("Skipping (unreadable dir): " + dir + " - " + exc.getMessage());
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return bySize;
    }

    /** Hashes candidate files that share a size and groups them by digest. */
    private void hashCandidates(List<Path> candidates, long size,
                                Map<String, List<FileMetadata>> byHash) {
        for (Path path : candidates) {
            try {
                String hash = hasher.hash(path);
                FileMetadata meta = new FileMetadata(path, size, hash);
                String key = size + ":" + hash;
                byHash.computeIfAbsent(key, k -> new ArrayList<>()).add(meta);
            } catch (IOException e) {
                System.err.println("Skipping (hash failed): " + path + " - " + e.getMessage());
            }
        }
    }

    /** Converts hash groups into a sorted list of duplicate groups (size > 1). */
    private List<List<FileMetadata>> buildGroups(Map<String, List<FileMetadata>> byHash) {
        List<List<FileMetadata>> groups = new ArrayList<>();
        for (List<FileMetadata> group : byHash.values()) {
            if (group.size() > 1) {
                group.sort(Comparator.comparing(m -> m.getPath().toString()));
                groups.add(group);
            }
        }
        groups.sort(Comparator.comparing(g -> g.get(0).getPath().toString()));
        return groups;
    }
}
