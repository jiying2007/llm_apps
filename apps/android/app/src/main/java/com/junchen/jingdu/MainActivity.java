package com.junchen.jingdu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public final class MainActivity extends Activity {
    private static final int REQ_IMPORT = 100;
    private static final int REQ_EXPORT = 101;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ReaderController reader = new ReaderController();
    private final ExecutorService workers = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "jingdu-worker");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicLong workGeneration = new AtomicLong();

    private BookRepository repository;
    private TtsController tts;
    private BookRepository.Book currentBook;
    private TextView text;
    private TextView status;
    private String pendingEncoding = BookRepository.AUTO;
    private File pendingExport;
    private boolean cleanMode;
    private boolean autoReading;
    private long autoDelayMs = 6000;
    private float fontSp = 20;
    private boolean night;

    private final Runnable autoStep = new Runnable() {
        @Override public void run() {
            if (!autoReading || currentBook == null) return;
            reader.next();
            render();
            if (reader.position() < Math.max(0, reader.length() - 1)) main.postDelayed(this, autoDelayMs);
            else stopAuto();
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        repository = new BookRepository(this);
        tts = new TtsController(this);
        buildUi();
        Uri incoming = getIntent() == null ? null : getIntent().getData();
        if (incoming != null) importUri(incoming, BookRepository.AUTO);
        else openMostRecent();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        HorizontalScrollView strip = new HorizontalScrollView(this);
        LinearLayout tools = new LinearLayout(this);
        tools.setOrientation(LinearLayout.HORIZONTAL);
        tools.setPadding(8, 8, 8, 8);
        strip.addView(tools);
        button(tools, "导入", v -> chooseImport());
        button(tools, "书架", v -> showLibrary());
        button(tools, "搜索", v -> promptSearch());
        button(tools, "目录", v -> showChapters());
        button(tools, "书签", v -> showBookmarks());
        button(tools, "净读", v -> showRepair());
        Button ttsButton = button(tools, "朗读", v -> toggleTts());
        ttsButton.setOnLongClickListener(v -> { showSleepTimer(); return true; });
        Button autoButton = button(tools, "自动", v -> toggleAuto());
        autoButton.setOnLongClickListener(v -> { chooseAutoSpeed(); return true; });
        button(tools, "主题", v -> { night = !night; applyAppearance(); });
        button(tools, "A-", v -> { fontSp = Math.max(14, fontSp - 2); text.setTextSize(fontSp); });
        button(tools, "A+", v -> { fontSp = Math.min(36, fontSp + 2); text.setTextSize(fontSp); });
        button(tools, "删除", v -> deleteCurrent());
        root.addView(strip, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        status = new TextView(this);
        status.setPadding(16, 8, 16, 8);
        status.setTextSize(12);
        root.addView(status);
        ScrollView scroll = new ScrollView(this);
        text = new TextView(this);
        text.setPadding(28, 20, 28, 80);
        text.setTextSize(fontSp);
        text.setLineSpacing(4, 1.35f);
        text.setTextIsSelectable(true);
        scroll.addView(text, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        LinearLayout nav = new LinearLayout(this);
        nav.setGravity(Gravity.CENTER);
        Button prev = button(nav, "上一页", v -> { reader.previous(); render(); });
        Button next = button(nav, "下一页", v -> { reader.next(); render(); });
        prev.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        next.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(nav);
        setContentView(root);
        applyAppearance();
    }

    private Button button(LinearLayout parent, String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        parent.addView(button);
        return button;
    }

    private <T> void runWork(String message, Callable<T> task, Consumer<T> success, String errorTitle) {
        final long token = workGeneration.incrementAndGet();
        status.setText(message);
        workers.execute(() -> {
            try {
                T result = task.call();
                main.post(() -> {
                    if (isDestroyed() || token != workGeneration.get()) return;
                    success.accept(result);
                });
            } catch (Throwable error) {
                main.post(() -> {
                    if (isDestroyed() || token != workGeneration.get()) return;
                    showError(errorTitle, error);
                });
            }
        });
    }

    private void cancelLongWork() { workGeneration.incrementAndGet(); }

    private void chooseImport() {
        new AlertDialog.Builder(this).setTitle("源文件编码").setItems(BookRepository.ENCODINGS, (d, which) -> {
            pendingEncoding = BookRepository.ENCODINGS[which];
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("text/plain").addCategory(Intent.CATEGORY_OPENABLE);
            startActivityForResult(intent, REQ_IMPORT);
        }).show();
    }

    private void importUri(Uri uri, String encoding) {
        runWork("正在导入…", () -> repository.importUri(uri, encoding), book -> openBook(book, false), "导入失败");
    }

    private void openMostRecent() {
        List<BookRepository.Book> books = repository.list();
        if (books.isEmpty()) {
            text.setText("导入本地 TXT 开始阅读。\n\n文件只复制到应用私有目录，源文件不会被修改。");
            status.setText("离线 · 无账号 · 无网络权限");
        } else openBook(books.get(0), false);
    }

    private void openBook(BookRepository.Book book, boolean clean) {
        stopAuto();
        tts.stop(null);
        long restored = currentBook != null && currentBook.id.equals(book.id)
                ? reader.position() : book.progress;
        if (currentBook != null) repository.saveProgress(currentBook, reader.position());
        currentBook = null;
        reader.close();
        cleanMode = false;

        if (!clean) {
            File file = repository.normalizedFile(book);
            runWork("正在打开…", () -> {
                reader.open(file, restored);
                return book;
            }, opened -> {
                currentBook = opened;
                cleanMode = false;
                render();
            }, "打开失败");
            return;
        }

        runWork("正在生成净读视图…", () -> {
            File file = buildClean(book);
            reader.open(file, restored);
            return book;
        }, opened -> {
            currentBook = opened;
            cleanMode = true;
            render();
        }, "净读失败");
    }

    private void render() {
        if (currentBook == null) return;
        try {
            text.setText(reader.page());
            repository.saveProgress(currentBook, reader.position());
            long percent = reader.length() == 0 ? 0 : Math.min(100, reader.position() * 100 / reader.length());
            status.setText(currentBook.name + " · " + percent + "% · " + currentBook.encoding
                    + " · " + currentBook.normalizedSha256.substring(0, 8)
                    + (cleanMode ? " · 净读" : " · 原文视图"));
        } catch (Exception error) {
            showError("读取失败", error);
        }
    }

    private void showLibrary() {
        List<BookRepository.Book> books = repository.list();
        if (books.isEmpty()) { toast("书架为空"); return; }
        String[] names = new String[books.size()];
        for (int i = 0; i < books.size(); i++) names[i] = books.get(i).name + "  [" + books.get(i).encoding + "]";
        new AlertDialog.Builder(this).setTitle("书架").setItems(names, (d, which) -> openBook(books.get(which), false)).show();
    }

    private void promptSearch() {
        if (currentBook == null) return;
        EditText input = new EditText(this);
        input.setSingleLine();
        new AlertDialog.Builder(this).setTitle("全文搜索").setView(input)
                .setPositiveButton("搜索", (d, w) -> runSearch(input.getText().toString()))
                .setNegativeButton("取消", null).show();
    }

    private void runSearch(String query) {
        if (query.trim().isEmpty()) return;
        runWork("正在搜索…", () -> reader.search(query), hits -> {
            if (hits.isEmpty()) { toast("没有命中"); render(); return; }
            String[] items = new String[hits.size()];
            for (int i = 0; i < hits.size(); i++) items[i] = hits.get(i).context();
            new AlertDialog.Builder(this).setTitle("搜索结果 · " + hits.size()).setItems(items, (d, which) -> {
                reader.jump(hits.get(which).offset());
                render();
            }).show();
        }, "搜索失败");
    }

    private void showChapters() {
        if (currentBook == null) return;
        runWork("正在生成目录…", reader::chapters, chapters -> {
            if (chapters.isEmpty()) { toast("未识别到章节标题"); render(); return; }
            String[] items = new String[chapters.size()];
            for (int i = 0; i < chapters.size(); i++) items[i] = chapters.get(i).title();
            new AlertDialog.Builder(this).setTitle("目录 · " + chapters.size()).setItems(items, (d, which) -> {
                reader.jump(chapters.get(which).offset());
                render();
            }).show();
        }, "目录失败");
    }

    private Set<String> bookmarkSet() {
        return currentBook == null ? new HashSet<>() : new HashSet<>(getPreferences(MODE_PRIVATE)
                .getStringSet("bookmarks." + currentBook.id, Collections.emptySet()));
    }

    private void showBookmarks() {
        if (currentBook == null) return;
        ArrayList<Long> positions = new ArrayList<>();
        for (String value : bookmarkSet()) {
            try { positions.add(Long.parseLong(value)); } catch (Exception ignored) { }
        }
        Collections.sort(positions);
        ArrayList<String> labels = new ArrayList<>();
        labels.add("＋ 添加当前位置");
        for (Long p : positions) labels.add("位置 " + p);
        new AlertDialog.Builder(this).setTitle("书签").setItems(labels.toArray(new String[0]), (d, which) -> {
            if (which == 0) {
                Set<String> set = bookmarkSet();
                set.add(Long.toString(reader.position()));
                getPreferences(MODE_PRIVATE).edit().putStringSet("bookmarks." + currentBook.id, set).apply();
                toast("已添加书签");
            } else {
                reader.jump(positions.get(which - 1));
                render();
            }
        }).setNeutralButton("清空", (d, w) -> getPreferences(MODE_PRIVATE).edit()
                .remove("bookmarks." + currentBook.id).apply()).show();
    }

    private String rules() {
        return currentBook == null ? "" : rulesFor(currentBook);
    }

    private String rulesFor(BookRepository.Book book) {
        return getPreferences(MODE_PRIVATE).getString("rules." + book.id, "");
    }

    private void showRepair() {
        if (currentBook == null) return;
        String[] actions = {"添加字面规则", cleanMode ? "切回原文视图" : "预览净读视图", "导出净读 TXT", "清空规则"};
        new AlertDialog.Builder(this).setTitle("净读规则 · " + ruleCount()).setItems(actions, (d, which) -> {
            if (which == 0) addRule();
            else if (which == 1) openBook(currentBook, !cleanMode);
            else if (which == 2) exportClean();
            else {
                getPreferences(MODE_PRIVATE).edit().remove("rules." + currentBook.id).apply();
                if (cleanMode) openBook(currentBook, false);
            }
        }).show();
    }

    private int ruleCount() {
        String value = rules();
        if (value.isEmpty()) return 0;
        int count = 1;
        for (int i = 0; i < value.length(); i++) if (value.charAt(i) == 0x1e) count++;
        return count;
    }

    private void addRule() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        EditText from = new EditText(this);
        from.setHint("查找（字面文本）");
        EditText to = new EditText(this);
        to.setHint("替换为");
        layout.addView(from);
        layout.addView(to);
        new AlertDialog.Builder(this).setTitle("新增净读规则").setView(layout).setPositiveButton("保存", (d, w) -> {
            String a = from.getText().toString();
            String b = to.getText().toString();
            if (a.isEmpty() || a.indexOf(0x1f) >= 0 || a.indexOf(0x1e) >= 0
                    || b.indexOf(0x1f) >= 0 || b.indexOf(0x1e) >= 0) {
                toast("规则包含保留分隔符或查找为空");
                return;
            }
            String old = rules();
            String packed = old + (old.isEmpty() ? "" : "\u001e") + a + "\u001f" + b;
            getPreferences(MODE_PRIVATE).edit().putString("rules." + currentBook.id, packed).apply();
            if (cleanMode) openBook(currentBook, true);
        }).setNegativeButton("取消", null).show();
    }

    private File buildClean(BookRepository.Book book) throws IOException {
        File output = repository.cleanPreviewFile(book);
        String packedRules = rulesFor(book);
        String revision = repository.repairRevision(book, packedRules);
        File revisionFile = new File(output.getParentFile(), "clean.revision");
        if (output.isFile() && revisionFile.isFile()) {
            try (FileInputStream in = new FileInputStream(revisionFile)) {
                byte[] data = new byte[(int) Math.min(128, revisionFile.length())];
                int count = in.read(data);
                if (count > 0 && revision.equals(new String(data, 0, count, java.nio.charset.StandardCharsets.UTF_8))) return output;
            }
        }
        try (ReaderController base = new ReaderController()) {
            base.open(repository.normalizedFile(book), 0);
            base.exportRules(packedRules, output);
        }
        try (java.io.FileOutputStream revisionOut = new java.io.FileOutputStream(revisionFile)) {
            revisionOut.write(revision.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            revisionOut.getFD().sync();
        }
        return output;
    }

    private void exportClean() {
        if (currentBook == null) return;
        BookRepository.Book book = currentBook;
        runWork("正在准备导出…", () -> buildClean(book), file -> {
            pendingExport = file;
            Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain")
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .putExtra(Intent.EXTRA_TITLE, stripTxt(book.name) + "-净读.txt");
            startActivityForResult(intent, REQ_EXPORT);
        }, "准备导出失败");
    }

    private static String stripTxt(String name) {
        return name.toLowerCase(java.util.Locale.ROOT).endsWith(".txt") ? name.substring(0, name.length() - 4) : name;
    }

    private void toggleTts() {
        if (currentBook == null) return;
        stopAuto();
        if (tts.isSpeaking()) {
            tts.stop(null);
            status.setText("朗读已暂停");
            return;
        }
        tts.start(reader, reader.position(), new TtsController.Listener() {
            @Override public void onPosition(long offset) { reader.jump(offset); render(); }
            @Override public void onStopped(String reason) {
                if (reason != null && !reason.equals("end")) toast(reason);
            }
        });
    }

    private void toggleAuto() {
        if (currentBook == null) return;
        if (autoReading) stopAuto();
        else {
            tts.stop(null);
            autoReading = true;
            status.setText("自动阅读中");
            main.postDelayed(autoStep, autoDelayMs);
        }
    }

    private void stopAuto() { autoReading = false; main.removeCallbacks(autoStep); }

    private void chooseAutoSpeed() {
        String[] items = {"2 秒/页", "4 秒/页", "6 秒/页", "10 秒/页"};
        long[] values = {2000, 4000, 6000, 10000};
        new AlertDialog.Builder(this).setTitle("自动阅读速度")
                .setItems(items, (d, w) -> autoDelayMs = values[w]).show();
    }

    private void showSleepTimer() {
        String[] items = {"关闭", "15 分钟", "30 分钟", "60 分钟"};
        long[] minutes = {0, 15, 30, 60};
        new AlertDialog.Builder(this).setTitle("伴读睡眠定时").setItems(items, (d, w) -> {
            main.removeCallbacksAndMessages("sleep");
            if (minutes[w] > 0) main.postAtTime(() -> {
                stopAuto();
                tts.stop("睡眠定时结束");
            }, "sleep", android.os.SystemClock.uptimeMillis() + minutes[w] * 60_000L);
        }).show();
    }

    private void deleteCurrent() {
        if (currentBook == null) return;
        BookRepository.Book doomed = currentBook;
        new AlertDialog.Builder(this).setTitle("删除私有副本？")
                .setMessage(doomed.name + "\n源 TXT 不会被删除。此操作不保留旧版本兼容数据。")
                .setPositiveButton("删除", (d, w) -> {
                    cancelLongWork();
                    stopAuto();
                    tts.stop(null);
                    reader.close();
                    repository.delete(doomed);
                    getPreferences(MODE_PRIVATE).edit()
                            .remove("bookmarks." + doomed.id)
                            .remove("rules." + doomed.id).apply();
                    currentBook = null;
                    cleanMode = false;
                    text.setText("");
                    openMostRecent();
                }).setNegativeButton("取消", null).show();
    }

    private void applyAppearance() {
        int bg = night ? Color.rgb(24, 24, 24) : Color.rgb(250, 248, 240);
        int fg = night ? Color.rgb(225, 225, 225) : Color.rgb(35, 35, 35);
        if (text != null) {
            text.setBackgroundColor(bg);
            text.setTextColor(fg);
            text.setTextSize(fontSp);
        }
        if (status != null) {
            status.setBackgroundColor(bg);
            status.setTextColor(fg);
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQ_IMPORT) importUri(uri, pendingEncoding);
        else if (requestCode == REQ_EXPORT && pendingExport != null) {
            File source = pendingExport;
            runWork("正在导出…", () -> {
                try (FileInputStream in = new FileInputStream(source);
                     OutputStream out = getContentResolver().openOutputStream(uri, "w")) {
                    if (out == null) throw new IOException("cannot open export destination");
                    byte[] buffer = new byte[64 * 1024];
                    int count;
                    while ((count = in.read(buffer)) != -1) out.write(buffer, 0, count);
                    out.flush();
                }
                return Boolean.TRUE;
            }, ignored -> { pendingExport = null; toast("导出完成"); render(); }, "导出失败");
        }
    }

    @Override public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (currentBook != null && keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) { reader.next(); render(); return true; }
        if (currentBook != null && keyCode == KeyEvent.KEYCODE_VOLUME_UP) { reader.previous(); render(); return true; }
        return super.onKeyDown(keyCode, event);
    }

    @Override protected void onPause() {
        super.onPause();
        if (currentBook != null) repository.saveProgress(currentBook, reader.position());
    }

    @Override protected void onDestroy() {
        cancelLongWork();
        stopAuto();
        main.removeCallbacksAndMessages(null);
        tts.close();
        reader.close();
        workers.shutdownNow();
        super.onDestroy();
    }

    private void showError(String title, Throwable error) {
        new AlertDialog.Builder(this).setTitle(title)
                .setMessage(error.getMessage() == null ? error.toString() : error.getMessage())
                .setPositiveButton("确定", null).show();
    }

    private void toast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
}
