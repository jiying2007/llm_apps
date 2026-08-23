package com.junchen.jingdu;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class BookRepository {
    static final String AUTO = "AUTO";
    static final String[] ENCODINGS = {
            AUTO, "UTF-8", "GB18030", "GBK", "GB2312", "Big5",
            "UTF-16", "UTF-16LE", "UTF-16BE"
    };
    private static final int SAMPLE_BYTES = 64 * 1024;
    private static final String PREFS = "jingdu.library.v2";
    private static final String KEY = "books";

    static final class Book {
        final String id;
        final String name;
        final String encoding;
        final long size;
        final String sourceSha256;
        final String normalizedSha256;
        long progress;
        long charCount;
        long touchedAt;

        Book(String id, String name, String encoding, long size, String sourceSha256,
             String normalizedSha256, long progress, long charCount, long touchedAt) {
            this.id = id;
            this.name = name;
            this.encoding = encoding;
            this.size = size;
            this.sourceSha256 = sourceSha256;
            this.normalizedSha256 = normalizedSha256;
            this.progress = progress;
            this.charCount = charCount;
            this.touchedAt = touchedAt;
        }
    }

    private final Context context;
    private final File root;

    BookRepository(Context context) {
        this.context = context.getApplicationContext();
        root = new File(context.getFilesDir(), "books");
        if (!root.isDirectory() && !root.mkdirs()) {
            throw new IllegalStateException("cannot create books directory");
        }
        cleanupRootTemporaries();
    }

    synchronized List<Book> list() {
        ArrayList<Book> books = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject item = array.getJSONObject(i);
                String id = item.getString("id");
                String sourceSha = item.getString("sourceSha256");
                String normalizedSha = item.getString("normalizedSha256");
                if (id.length() != 64 || !id.equals(sourceSha) || normalizedSha.length() != 64) continue;
                if (!rawFile(id).isFile() || !normalizedFile(id, normalizedSha).isFile()) continue;
                books.add(new Book(
                        id,
                        item.getString("name"),
                        item.getString("encoding"),
                        item.optLong("size"),
                        sourceSha,
                        normalizedSha,
                        item.optLong("progress"),
                        item.optLong("charCount"),
                        item.optLong("touchedAt")));
            }
        } catch (Exception ignored) {
            // Hard-cut v2 metadata: older/malformed private state is intentionally not migrated.
        }
        books.sort(Comparator.comparingLong((Book book) -> book.touchedAt).reversed());
        return books;
    }

    synchronized Book importUri(Uri uri, String requestedEncoding) throws Exception {
        File sourceTemporary = File.createTempFile(".source-", ".tmp", root);
        File normalizedTemporary = null;
        try {
            long size = copyUri(uri, sourceTemporary);
            String sourceSha = NativeCore.fileSha256(sourceTemporary);
            Book existing = findById(sourceSha);
            File directory = directory(sourceSha);
            if (!directory.isDirectory() && !directory.mkdirs()) {
                throw new IOException("cannot create book directory");
            }

            String encoding = requestedEncoding == null ? AUTO : requestedEncoding;
            if (AUTO.equals(encoding)) encoding = detect(sourceTemporary);

            normalizedTemporary = File.createTempFile(".document-", ".tmp", directory);
            normalize(sourceTemporary, normalizedTemporary, encoding);
            String normalizedSha = NativeCore.fileSha256(normalizedTemporary);

            publishImmutable(sourceTemporary, rawFile(sourceSha));
            publishImmutable(normalizedTemporary, normalizedFile(sourceSha, normalizedSha));
            normalizedTemporary = null;

            boolean sameRevision = existing != null && existing.normalizedSha256.equals(normalizedSha);
            Book book = new Book(
                    sourceSha,
                    displayName(uri),
                    encoding,
                    size,
                    sourceSha,
                    normalizedSha,
                    sameRevision ? existing.progress : 0,
                    sameRevision ? existing.charCount : 0,
                    System.currentTimeMillis());
            upsert(book);
            return book;
        } finally {
            deleteTemporary(sourceTemporary);
            deleteTemporary(normalizedTemporary);
        }
    }

    synchronized Book redecode(Book book, String requestedEncoding) throws Exception {
        if (book == null) throw new IOException("no book selected");
        File raw = rawFile(book.id);
        if (!raw.isFile()) throw new IOException("private source copy is missing");

        String encoding = requestedEncoding == null ? AUTO : requestedEncoding;
        if (AUTO.equals(encoding)) encoding = detect(raw);

        File temporary = File.createTempFile(".document-", ".tmp", directory(book.id));
        try {
            normalize(raw, temporary, encoding);
            String normalizedSha = NativeCore.fileSha256(temporary);
            publishImmutable(temporary, normalizedFile(book.id, normalizedSha));
            boolean sameRevision = book.normalizedSha256.equals(normalizedSha);
            Book updated = new Book(
                    book.id,
                    book.name,
                    encoding,
                    book.size,
                    book.sourceSha256,
                    normalizedSha,
                    sameRevision ? book.progress : 0,
                    sameRevision ? book.charCount : 0,
                    System.currentTimeMillis());
            upsert(updated);
            return updated;
        } finally {
            deleteTemporary(temporary);
        }
    }

    synchronized void saveProgress(Book book, long progress) {
        if (book == null) return;
        book.progress = Math.max(0, progress);
        book.touchedAt = System.currentTimeMillis();
        upsert(book);
    }

    synchronized void updateCharCount(Book book, long charCount) {
        if (book == null || charCount <= 0 || book.charCount == charCount) return;
        book.charCount = charCount;
        upsert(book);
    }

    synchronized void delete(Book book) {
        if (book == null) return;
        deleteTree(directory(book.id));
        List<Book> books = list();
        books.removeIf(item -> item.id.equals(book.id));
        write(books);
    }

    File normalizedFile(Book book) {
        return normalizedFile(book.id, book.normalizedSha256);
    }

    File cleanFile(Book book, String revision) {
        return new File(directory(book.id), "clean-" + revision + ".txt");
    }

    String repairRevision(Book book, String packedRules) throws IOException {
        return NativeCore.repairRevision(
                book.normalizedSha256,
                packedRules == null ? "" : packedRules);
    }

    void pruneDocumentRevisions(Book book) {
        File directory = directory(book.id);
        File keep = normalizedFile(book);
        File[] files = directory.listFiles();
        if (files == null) return;
        for (File file : files) {
            String name = file.getName();
            if (name.startsWith("document-") && name.endsWith(".txt") && !file.equals(keep)) {
                deleteTemporary(file);
            } else if (name.startsWith(".document-") && name.endsWith(".tmp")) {
                deleteTemporary(file);
            } else if (name.equals("document.txt")) {
                deleteTemporary(file);
            }
        }
    }

    void pruneCleanRevisions(Book book, File keep) {
        File[] files = directory(book.id).listFiles();
        if (files == null) return;
        for (File file : files) {
            String name = file.getName();
            if (name.startsWith("clean-") && name.endsWith(".txt") && !file.equals(keep)) {
                deleteTemporary(file);
            } else if (name.startsWith("clean-") && name.endsWith(".txt.tmp")) {
                deleteTemporary(file);
            } else if (name.equals("clean.txt") || name.equals("clean.revision")) {
                deleteTemporary(file);
            }
        }
    }

    private void cleanupRootTemporaries() {
        File[] files = root.listFiles();
        if (files == null) return;
        for (File file : files) {
            String name = file.getName();
            if (name.startsWith(".source-") && name.endsWith(".tmp")) {
                deleteTemporary(file);
            }
        }
    }

    private Book findById(String id) {
        for (Book book : list()) {
            if (book.id.equals(id)) return book;
        }
        return null;
    }

    private long copyUri(Uri uri, File target) throws IOException {
        long total = 0;
        try (InputStream input = new BufferedInputStream(context.getContentResolver().openInputStream(uri));
             FileOutputStream output = new FileOutputStream(target)) {
            if (input == null) throw new IOException("cannot open selected file");
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
                total += count;
            }
            output.getFD().sync();
        }
        return total;
    }

    private String detect(File raw) throws IOException {
        int sampleLength = (int) Math.min(raw.length(), SAMPLE_BYTES);
        byte[] sample = new byte[sampleLength];
        try (FileInputStream input = new FileInputStream(raw)) {
            int total = 0;
            while (total < sample.length) {
                int count = input.read(sample, total, sample.length - total);
                if (count < 0) break;
                total += count;
            }
            if (total != sample.length) sample = java.util.Arrays.copyOf(sample, total);
        }
        return NativeCore.detectEncoding(sample, raw.length() > sample.length);
    }

    private void normalize(File raw, File target, String encodingName) throws IOException {
        final Charset charset;
        try {
            charset = Charset.forName(encodingName);
        } catch (Exception error) {
            throw new IOException("unsupported encoding: " + encodingName, error);
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                     new FileInputStream(raw),
                     charset.newDecoder()
                             .onMalformedInput(CodingErrorAction.REPLACE)
                             .onUnmappableCharacter(CodingErrorAction.REPLACE)), 64 * 1024);
             FileOutputStream output = new FileOutputStream(target);
             BufferedWriter writer = new BufferedWriter(
                     new OutputStreamWriter(output, StandardCharsets.UTF_8), 64 * 1024)) {
            char[] buffer = new char[32 * 1024];
            boolean first = true;
            int count;
            while ((count = reader.read(buffer)) != -1) {
                int start = first && count > 0 && buffer[0] == '\uFEFF' ? 1 : 0;
                first = false;
                writer.write(buffer, start, count - start);
            }
            writer.flush();
            output.getFD().sync();
        }
    }

    private synchronized void upsert(Book book) {
        List<Book> books = list();
        books.removeIf(item -> item.id.equals(book.id));
        books.add(book);
        write(books);
    }

    private void write(List<Book> books) {
        JSONArray array = new JSONArray();
        try {
            for (Book book : books) {
                JSONObject item = new JSONObject();
                item.put("id", book.id);
                item.put("name", book.name);
                item.put("encoding", book.encoding);
                item.put("size", book.size);
                item.put("sourceSha256", book.sourceSha256);
                item.put("normalizedSha256", book.normalizedSha256);
                item.put("progress", book.progress);
                item.put("charCount", book.charCount);
                item.put("touchedAt", book.touchedAt);
                array.put(item);
            }
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY, array.toString())
                .apply();
    }

    private String displayName(Uri uri) {
        ContentResolver resolver = context.getContentResolver();
        try (Cursor cursor = resolver.query(
                uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.trim().isEmpty()) return name;
            }
        } catch (Exception ignored) {
        }
        String fallback = uri.getLastPathSegment();
        return fallback == null ? "TXT" : fallback;
    }

    private File directory(String id) { return new File(root, id); }
    private File rawFile(String id) { return new File(directory(id), "source.bin"); }
    private File normalizedFile(String id, String normalizedSha) {
        return new File(directory(id), "document-" + normalizedSha + ".txt");
    }

    private static void publishImmutable(File source, File target) throws IOException {
        if (target.isFile()) {
            deleteTemporary(source);
            return;
        }
        try {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException error) {
            try {
                Files.move(source.toPath(), target.toPath());
            } catch (FileAlreadyExistsException alreadyPublished) {
                deleteTemporary(source);
            }
        } catch (FileAlreadyExistsException alreadyPublished) {
            deleteTemporary(source);
        }
    }

    private static void deleteTemporary(File file) {
        if (file != null && file.exists() && !file.delete()) file.deleteOnExit();
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteTree(child);
        if (!file.delete()) file.deleteOnExit();
    }
}
