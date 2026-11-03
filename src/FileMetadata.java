import java.nio.file.Path;

/**
 * Immutable description of a scanned file: its path, size and content hash.
 * Demonstrates encapsulation - fields are private and exposed via getters.
 */
public final class FileMetadata {

    private final Path path;
    private final long size;
    private final String hash;

    public FileMetadata(Path path, long size, String hash) {
        this.path = path;
        this.size = size;
        this.hash = hash;
    }

    public Path getPath() {
        return path;
    }

    public long getSize() {
        return size;
    }

    public String getHash() {
        return hash;
    }

    @Override
    public String toString() {
        return path + " (" + size + " bytes)";
    }
}
