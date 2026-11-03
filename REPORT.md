# Duplicate File Detection System — Project Report

**Language:** Pure Java (no external libraries)
**Location:** `D:\OOP project`
**Requirements:** JDK 11 or newer (developed and tested on JDK 26)

---

## 1. Project Structure

```
D:\OOP project\
├── src\
│   ├── Main.java              — entry point / command-line interface
│   ├── FileMetadata.java      — immutable value object (path, size, hash)
│   ├── FileHasher.java        — SHA-256 content hashing
│   ├── DuplicateDetector.java — directory scan + duplicate grouping
│   └── DuplicateReport.java   — report formatting and file output
├── bin\                       — compiled .class files (generated)
└── REPORT.md                  — this document
```

---

## 2. How It Is Run

### Compile (once)

```bat
cd /d "D:\OOP project"
javac -d bin src\*.java
```

### Run

```bat
java -cp bin Main [directory] [reportFile]
```

| Argument      | Meaning                                             | Default                     |
|---------------|-----------------------------------------------------|-----------------------------|
| `directory`   | Root folder to scan recursively                     | Current directory (`.`)     |
| `reportFile`  | Where to write the text report                      | `<directory>\duplicate-report.txt` |

### Examples

```bat
java -cp bin Main "D:\Downloads"
java -cp bin Main "D:\" "D:\OOP project\drive-scan-report.txt"
java -cp bin Main
```

### Sample output

```
Scanning: D:\Java Practice
Found 2 duplicate group(s); 69 B could be reclaimed.

Group 1 - 3 copies, 24 B each:
  D:\...\a.txt
  D:\...\b.txt
  D:\...\sub\c.txt

Group 2 - 2 copies, 21 B each:
  D:\...\d1.dat
  D:\...\sub\d2.dat

Report written to: D:\...\report.txt
```

### Exit behaviour

* Exit code `0` — scan completed (duplicate groups or not; both are success).
* Exit code `1` — the target is not a directory, or the scan failed (`IOException`).
* Unreadable files/directories (e.g. `$RECYCLE.BIN`, `System Volume Information`)
  are skipped with a warning on `stderr` instead of aborting the scan.

---

## 3. How It Works (Algorithm)

The detector never hashes every file — hashing is the expensive part, so the
work is narrowed down in **two phases**:

```
Phase 1 — Scan & size grouping           Phase 2 — Hashing & grouping
──────────────────────────────            ─────────────────────────────
Files.walkFileTree(root)                  for each size with ≥ 2 files:
        │                                         │
        v                                         v
regular files ──► HashMap<Long,          SHA-256 each candidate
                  List<Path>>                    │
                  keyed by byte size             v
                  (attrs.size())         HashMap<String, List<FileMetadata>>
        │                                 keyed by "size:hash"
        v                                         │
sizes with only 1 file ──► DISCARDED             v
                                          groups with > 1 file
                                          = duplicate groups
                                                  │
                                                  v
                                          sort by path, build report
                                          print to console + write .txt
```

**Why two phases?** Two files can only be duplicates if they have the same
size. Comparing a 8-byte size is nearly free; computing SHA-256 reads the
entire file. So files with a unique size are discarded *before* any hashing.
In practice this eliminates the vast majority of files, so only a small set
of candidates ever gets read fully.

**Correctness:** files are grouped by `size + SHA-256 digest`. SHA-256 is a
cryptographic hash — two different files producing the same digest is
computationally infeasible, so equal digests plus equal sizes ⇒ identical
content.

**Reporting:** for each group, every copy except one is potentially
reclaimable, so wasted space = `size × (copies − 1)`. The report lists one
block per group and the total reclaimable bytes (formatted as B/KB/MB/GB/TB).

---

## 4. Architecture

Five classes, each with a single, well-defined responsibility. `Main` only
wires them together; no class reaches into another's internals — all
collaboration happens through method calls on encapsulated state.

```
                 ┌────────────┐
                 │   Main     │  CLI: parse args, orchestrate, exit codes
                 └─────┬──────┘
        findDuplicates │  ▲ summary()/format()/writeTo()
        ┌──────────────▼──┴───────────────────┐
        │           DuplicateDetector         │
        │  phase 1: walk + size map           │
        │  phase 2: hash + duplicate map      │──uses──►┌────────────┐
        └──────────────┬──────────────────────┘          │ FileHasher │
                       │ creates                         │ SHA-256    │
                       ▼                                 └────────────┘
              ┌─────────────────┐
              │  FileMetadata   │  immutable: path, size, hash
              └────────┬────────┘
                       │ list of groups passed to
                       ▼
              ┌─────────────────┐
              │ DuplicateReport │  summary, formatting, file output
              └─────────────────┘
```

### Class responsibilities

| Class              | Responsibility | Key JDK APIs |
|--------------------|----------------|--------------|
| `Main`             | Entry point: argument handling, user-facing messages, exit status | `Paths`, `Files.isDirectory`, `System.exit` |
| `FileMetadata`     | Value object describing one file; fully `final` fields, getters only, no setters | — |
| `FileHasher`       | Streams a file through `MessageDigest` and returns a lowercase hex string | `MessageDigest`, `InputStream`, `Files.newInputStream` |
| `DuplicateDetector`| Orchestrates the two-phase detection algorithm, tolerates inaccessible paths | `Files.walkFileTree`, `SimpleFileVisitor`, `HashMap`, `Comparator` |
| `DuplicateReport`  | Turns duplicate groups into human-readable text and writes it to disk | `Files.write`, `StandardCharsets.UTF_8`, `String.format` |

### Data flow

`Path` → (size) `Map<Long, List<Path>>` → (hash) `Map<String, List<FileMetadata>>`
→ `List<List<FileMetadata>>` (duplicate groups) → `String` report → console + file.

---

## 5. Important Topics Used

### 5.1 Object-Oriented Programming

* **Encapsulation** — every class keeps its state `private` (`FileMetadata`'s
  three `final` fields, `DuplicateDetector`'s `hasher`, `DuplicateReport`'s
  group list) and exposes behaviour through public methods. No mutable
  internals leak out.
* **Abstraction** — `Main` calls `detector.findDuplicates(root)` and never
  sees the size-map/hash-map mechanics; the algorithm's complexity is hidden
  behind one method.
* **Inheritance & polymorphism** — the anonymous `SimpleFileVisitor<>`
  subclass overrides `visitFile`, `visitFileFailed` and
  `postVisitDirectory`; the JDK's traversal engine calls those overridden
  methods through the base-class reference (callback polymorphism).
* **Immutability** — `FileMetadata` is `final` with only `final` fields and
  no setters, so a scanned file's description can never change after
  creation, making it safe to share across collections.
* **Single-responsibility principle** — hashing, detection, formatting and
  presentation live in separate classes; each can be changed independently.

### 5.2 File Handling (I/O)

* **NIO.2 `java.nio.file` API** (modern) rather than legacy `java.io.File`:
  `Path`, `Files.walkFileTree`, `Files.newInputStream`, `Files.write`.
* **`Files.walkFileTree` + `SimpleFileVisitor`** for recursion — chosen over
  `Files.walk` because visit failures (access-denied system folders) can be
  handled *per node* via `visitFileFailed`/`postVisitDirectory`, so one
  protected folder never aborts the whole scan.
* **Streaming reads with a fixed buffer** — files are read 8 KB at a time
  into a byte array, so memory usage stays constant even for huge files
  (no `readAllBytes`).
* **Writing output** — the report is built as a `String` and written with
  `Files.write(..., StandardCharsets.UTF_8)`.

### 5.3 Hashing

* **SHA-256** via `java.security.MessageDigest` — industry-standard
  cryptographic digest; same content ⇒ same digest, different content ⇒
  (practically) different digest.
* **Reuse + reset** — one `MessageDigest` instance is reused (`reset()`
  before each file) instead of allocating a new one per file.
* **Hex encoding** — the 32 raw digest bytes are converted to a 64-character
  lowercase hex string for readable comparison and reporting.

### 5.4 Collections

* **`HashMap<Long, List<Path>>`** — grouping files by size in one pass;
  the map *is* the grouping algorithm (hash-partitioning).
* **`HashMap<String, List<FileMetadata>>`** — final grouping by
  `size:hash` key; lookup/insert is O(1) per file.
* **`List` / `ArrayList`** — accumulates candidates and result groups.
* **`Map.Entry` iteration** — phase 2 iterates `bySize.entrySet()` once.
* **`Comparator` + `sort`** — groups and their members are sorted by path so
  output is deterministic; `Comparator.comparing(...)` composes comparisons.
* **Generics** — every map/list is typed (`Long`, `String`,
  `FileMetadata`), catching mismatches at compile time.
* **`computeIfAbsent`** — the idiomatic one-liner for
  "get list for key, create if missing".

### 5.5 Exception Handling

* **Checked `IOException`** declared on `findDuplicates`, `hash`, `writeTo`;
  `Main` catches it, prints a clear message and sets exit code `1`.
* **Graceful degradation** — per-file failures (unreadable, hash failure)
  are caught inside the detector, logged to `stderr`, and the scan continues;
  only a truly fatal error stops the program.
* **Fail-fast constructor** — if `SHA-256` were unavailable,
  `FileHasher`'s constructor throws `IllegalStateException` immediately
  rather than failing mysteriously later.

### 5.6 Performance

* **Two-phase filtering** (size, then hash) avoids reading files that cannot
  possibly be duplicates — the difference between seconds and hours on a
  large drive.
* **Single tree traversal** — one `walkFileTree` pass collects everything;
  `attrs.size()` is read from the directory entry, no extra `stat` call.
* **Complexity:** O(N) for the walk plus O(M) bytes hashed for candidates,
  where M ≪ total drive bytes.

### 5.7 Other Practices

* **Deterministic output** — sorting groups makes runs reproducible.
* **Javadoc/comments** on every class and public method.
* **Human-readable numbers** — byte counts formatted as B/KB/MB/GB/TB.
* **CLI contract** — usage defaults, exit codes, `stdout` for results vs
  `stderr` for warnings/errors.

---

## 6. Verification Performed

1. `javac -d bin src/*.java` — compiles with no warnings/errors.
2. Synthetic test fixture (nested folders, two duplicate sets of 3 and 2
   copies, unique files, lonely files): both groups detected, unique files
   ignored, reclaimable bytes correct, report file written.
3. Invalid directory argument: clean error message, exit code `1`.
4. Full `D:\` scan: protected folders (`$RECYCLE.BIN`,
   `System Volume Information`) are skipped with warnings instead of
   crashing, and the scan completes and writes `drive-scan-report.txt`.

---

## 7. Possible Extensions

* Delete/undo option: move duplicates to the Recycle Bin after confirmation.
* Skip the report file itself on repeated scans.
* Optional filters: minimum size, file patterns, max depth, hard-link check.
* Parallel hashing (`ExecutorService`) for very large drives.
* Maven/Gradle layout with JUnit tests for a submission-ready package.
