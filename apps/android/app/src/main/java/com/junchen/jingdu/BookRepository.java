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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;

final class BookRepository {
    static final String AUTO = "AUTO";
    static final String[] ENCODINGS = {AUTO, "UTF-8", "GB18030", "GBK", "GB2312", "Big5", "UTF-16", "UTF-16LE", "UTF-16BE"};

    static final class Book {
        final String id;
        final String name;
        final String encoding;
        final long size;
        long progress;
        long touchedAt;

        Book(String id, String name, String encoding, long size, long progress, long touchedAt) {
            this.id = id; this.name = name; this.encoding = encoding; this.size = size; this.progress = progress; this.touchedAt = touchedAt;
        }
    }

    private static final String PREFS = "jingdu.library.v2";
    private static final String KEY = "books";
    private final Context context;
    private final File root;

    BookRepository(Context context) {
        this.context = context.getApplicationContext();
        this.root = new File(context.getFilesDir(), "books");
        if (!root.isDirectory() && !root.mkdirs()) throw new IllegalStateException("cannot create books directory");
    }

    synchronized List<Book> list() {
        ArrayList<Book> books = new ArrayList<>();
        try {
            JSONArray array = new JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"));
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                File normalized = normalizedFile(o.getString("id"));
                if (normalized.isFile()) books.add(new Book(o.getString("id"), o.getString("name"), o.getString("encoding"), o.optLong("size"), o.optLong("progress"), o.optLong("touchedAt")));
            }
        } catch (Exception ignored) { }
        books.sort(Comparator.comparingLong((Book b) -> b.touchedAt).reversed());
        return books;
    }

    synchronized Book importUri(Uri uri, String requestedEncoding) throws Exception {
        File temp = File.createTempFile("import-", ".bin", context.getCacheDir());
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long size = 0;
        try (InputStream in = new BufferedInputStream(context.getContentResolver().openInputStream(uri)); FileOutputStream out = new FileOutputStream(temp)) {
            if (in == null) throw new IOException("cannot open selected file");
            byte[] buffer = new byte[64 * 1024]; int count;
            while ((count = in.read(buffer)) != -1) { digest.update(buffer, 0, count); out.write(buffer, 0, count); size += count; }
        }
        String id = HexFormat.of().formatHex(digest.digest());
        File dir = directory(id); if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("cannot create book directory");
        File raw = rawFile(id); if (raw.exists() && !raw.delete()) throw new IOException("cannot replace private source copy");
        if (!temp.renameTo(raw)) { copy(temp, raw); if (!temp.delete()) temp.deleteOnExit(); }

        String encoding = requestedEncoding == null ? AUTO : requestedEncoding;
        if (AUTO.equals(encoding)) encoding = detect(raw);
        normalize(raw, normalizedFile(id), encoding);

        Book book = new Book(id, displayName(uri), encoding, size, 0, System.currentTimeMillis());
        upsert(book); return book;
    }

    synchronized void saveProgress(Book book, long progress) {
        if (book == null) return; book.progress = Math.max(0, progress); book.touchedAt = System.currentTimeMillis(); upsert(book);
    }

    synchronized void delete(Book book) {
        if (book == null) return; deleteTree(directory(book.id));
        List<Book> books = list(); books.removeIf(b -> b.id.equals(book.id)); write(books);
    }

    File normalizedFile(Book book) { return normalizedFile(book.id); }
    File cleanPreviewFile(Book book) { return new File(directory(book.id), "clean.txt"); }

    private String detect(File raw) throws IOException {
        byte[] sample = new byte[(int) Math.min(raw.length(), 64 * 1024)];
        try (FileInputStream in = new FileInputStream(raw)) { int read = in.read(sample); if (read < sample.length && read >= 0) sample = java.util.Arrays.copyOf(sample, read); }
        return NativeCore.detectEncoding(sample);
    }

    private void normalize(File raw, File target, String encoding) throws IOException {
        Charset charset;
        try { charset = Charset.forName(encoding); } catch (Exception error) { throw new IOException("unsupported encoding: " + encoding, error); }
        File temp = new File(target.getParentFile(), "normalized.tmp");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(raw), charset), 64 * 1024);
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(temp), StandardCharsets.UTF_8), 64 * 1024)) {
            char[] buffer = new char[32 * 1024]; int count; boolean first = true;
            while ((count = reader.read(buffer)) != -1) {
                int start = 0;
                if (first && count > 0 && buffer[0] == '\uFEFF') start = 1;
                first = false; writer.write(buffer, start, count - start);
            }
        }
        if (target.exists() && !target.delete()) throw new IOException("cannot replace normalized copy");
        if (!temp.renameTo(target)) { copy(temp, target); if (!temp.delete()) temp.deleteOnExit(); }
    }

    private synchronized void upsert(Book book) {
        List<Book> books = list(); books.removeIf(b -> b.id.equals(book.id)); books.add(book); write(books);
    }

    private void write(List<Book> books) {
        JSONArray array = new JSONArray();
        try {
            for (Book b : books) { JSONObject o = new JSONObject(); o.put("id", b.id); o.put("name", b.name); o.put("encoding", b.encoding); o.put("size", b.size); o.put("progress", b.progress); o.put("touchedAt", b.touchedAt); array.put(o); }
        } catch (Exception error) { throw new IllegalStateException(error); }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, array.toString()).apply();
    }

    private String displayName(Uri uri) {
        ContentResolver resolver = context.getContentResolver();
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) { String name = cursor.getString(0); if (name != null && !name.isBlank()) return name; }
        } catch (Exception ignored) { }
        String fallback = uri.getLastPathSegment(); return fallback == null ? "TXT" : fallback;
    }

    private File directory(String id) { return new File(root, id); }
    private File rawFile(String id) { return new File(directory(id), "source.bin"); }
    private File normalizedFile(String id) { return new File(directory(id), "document.txt"); }
    private static void copy(File source, File target) throws IOException { try (FileInputStream in = new FileInputStream(source); FileOutputStream out = new FileOutputStream(target)) { byte[] b = new byte[64 * 1024]; int n; while ((n = in.read(b)) != -1) out.write(b, 0, n); } }
    private static void deleteTree(File file) { if (file == null || !file.exists()) return; File[] children = file.listFiles(); if (children != null) for (File child : children) deleteTree(child); if (!file.delete()) file.deleteOnExit(); }
}
