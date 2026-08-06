package com.junchen.jingdu;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.content.pm.ActivityInfo;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.text.InputFilter;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.KeyEvent;
import android.view.Gravity;
import android.view.accessibility.AccessibilityManager;
import android.view.DisplayCutout;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.AdapterView;

import com.jingdu.txt.core.ChapterEntry;
import com.jingdu.txt.core.ChapterOutline;
import com.jingdu.txt.core.ChapterOutlineEntry;
import com.jingdu.txt.core.ChapterOutlineMapper;
import com.jingdu.txt.core.ChapterOutlineStore;
import com.jingdu.txt.core.AutoScrollPolicy;
import com.jingdu.txt.core.AutoScrollCompanionSettings;
import com.jingdu.txt.core.AutoScrollResumeSession;
import com.jingdu.txt.core.BookLibraryEntry;
import com.jingdu.txt.core.BookLibraryStore;
import com.jingdu.txt.core.BookEncodingProfile;
import com.jingdu.txt.core.BookEncodingProfileStore;
import com.jingdu.txt.core.BookShelfStateStore;
import com.jingdu.txt.core.BookDeletionJournal;
import com.jingdu.txt.core.BookDeletionPolicy;
import com.jingdu.txt.core.BookBookmark;
import com.jingdu.txt.core.BookBookmarkStore;
import com.jingdu.txt.core.CompanionSleepTimer;
import com.jingdu.txt.core.DocumentIndex;
import com.jingdu.txt.core.DiskDocumentIndex;
import com.jingdu.txt.core.DiskDocumentIndexBuilder;
import com.jingdu.txt.core.DiskIndexBuildResult;
import com.jingdu.txt.core.DiskRepairProjection;
import com.jingdu.txt.core.DiskRepairCandidateIndex;
import com.jingdu.txt.core.DecodingReplacement;
import com.jingdu.txt.core.EncodingDetector;
import com.jingdu.txt.core.DetectedEncoding;
import com.jingdu.txt.core.ExportRecoveryJournal;
import com.jingdu.txt.core.ImportProgressListener;
import com.jingdu.txt.core.ImportResult;
import com.jingdu.txt.core.ImportEncodingPreference;
import com.jingdu.txt.core.IndexedTextWindow;
import com.jingdu.txt.core.GeneratedArtifactPruner;
import com.jingdu.txt.core.RepairFilePipeline;
import com.jingdu.txt.core.RepairFilePreviewPager;
import com.jingdu.txt.core.RepairFileResult;
import com.jingdu.txt.core.RepairMatch;
import com.jingdu.txt.core.RepairOccurrence;
import com.jingdu.txt.core.RepairOrdinalRange;
import com.jingdu.txt.core.RepairPreviewPage;
import com.jingdu.txt.core.RepairRule;
import com.jingdu.txt.core.RepairRuleMergePolicy;
import com.jingdu.txt.core.RepairRuleMergeResult;
import com.jingdu.txt.core.RepairRuleMerger;
import com.jingdu.txt.core.RepairRulePackCodec;
import com.jingdu.txt.core.RepairRuleStore;
import com.jingdu.txt.core.RepairScope;
import com.jingdu.txt.core.ReaderAppearance;
import com.jingdu.txt.core.ReaderDisplayPolicy;
import com.jingdu.txt.core.ReaderNavigationSettings;
import com.jingdu.txt.core.ReaderTextSelection;
import com.jingdu.txt.core.RepairSelection;
import com.jingdu.txt.core.SearchHit;
import com.jingdu.txt.core.SpeechPlaybackQueue;
import com.jingdu.txt.core.SpeechSettings;
import com.jingdu.txt.core.TextImportPipeline;
import com.jingdu.txt.core.TextOffsetRange;
import com.jingdu.txt.core.port.TextToSpeechPort;
import com.jingdu.txt.core.port.ReaderSurface;

import java.io.File;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MainActivity extends Activity {
    private static final int OPEN_TEXT_REQUEST = 1001;
    private static final int IMPORT_RULE_PACK_REQUEST = 1002;
    private static final int EXPORT_RULE_PACK_REQUEST = 1003;
    private static final int EXPORT_CLEAN_TEXT_REQUEST = 1004;
    private static final int BOOK_STATE_MAGIC_V1 = 0x4A444253;
    private static final int BOOK_STATE_MAGIC = 0x4A444254;
    private static final String BOOK_LIBRARY_FILE = "library.bin";
    private static final String BOOK_SHELF_STATE_FILE = "shelf-state.bin";
    private static final String BOOK_DELETE_JOURNAL_FILE = "delete-pending.bin";
    private static final String BOOKMARKS_DIRECTORY = "bookmarks";
    private static final String CHAPTER_OUTLINES_DIRECTORY = "chapter-outlines";
    private static final String ENCODING_PROFILES_DIRECTORY = "encoding-profiles";
    private static final String BOOK_PROGRESS_PREFERENCES = "book-progress-pending";
    private static final String READER_APPEARANCE_PREFERENCES = "reader-appearance";
    private static final String READER_THEME_KEY = "theme";
    private static final String READER_FONT_KEY = "font-family";
    private static final String READER_TEXT_SIZE_KEY = "text-size-sp";
    private static final String READER_LINE_HEIGHT_KEY = "line-height-percent";
    private static final String READER_PARAGRAPH_SPACING_KEY = "paragraph-spacing-dp";
    private static final String READER_MARGIN_KEY = "horizontal-margin-dp";
    private static final String READER_DISPLAY_POLICY_KEY = "display-policy-json";
    private static final String READER_NAVIGATION_KEY = "navigation-json";
    private static final String AUTO_SCROLL_PREFERENCES = "auto-scroll-settings";
    private static final String AUTO_SCROLL_POLICY_KEY = "policy-json";
    private static final String AUTO_SCROLL_COMPANION_KEY = "companion-json";
    private static final String SPEECH_SETTINGS_PREFERENCES = "speech-settings";
    private static final String SPEECH_SETTINGS_KEY = "settings-json";
    private static final String BOOK_PROGRESS_ID_KEY = "book-id";
    private static final String BOOK_PROGRESS_REVISION_KEY = "active-revision";
    private static final String BOOK_PROGRESS_ANCHOR_KEY = "anchor";
    private static final int PREVIEW_BYTES = 256 * 1024;
    private static final int INDEX_WINDOW_CHARACTERS = 128 * 1024;
    private static final int REPAIR_PREVIEW_PAGE_SIZE = 20;
    private static final int SEARCH_RESULT_LIMIT = 50;
    private static final long MEBIBYTE = 1024L * 1024L;
    private static final long[] GENERATED_ARTIFACT_LIMIT_OPTIONS = {
            512L * MEBIBYTE, 1024L * MEBIBYTE, 2048L * MEBIBYTE};
    private static final int[] INACTIVE_ARTIFACT_GROUP_OPTIONS = {0, 2, 4};
    private static final String STORAGE_POLICY_PREFERENCES = "generated-artifact-policy";
    private static final String STORAGE_LIMIT_KEY = "maximum-bytes";
    private static final String STORAGE_GROUPS_KEY = "inactive-groups";
    private static final long SLEEP_TIMER_TICK_MILLIS = 250L;
    private static final long MINUTE_MILLIS = 60L * 1000L;
    private static final int[] READER_TEXT_SIZE_OPTIONS =
            ReaderAppearance.textSizeOptionsSp();
    private static final int[] READER_LINE_HEIGHT_OPTIONS =
            ReaderAppearance.lineHeightOptionsPercent();
    private static final int[] READER_PARAGRAPH_SPACING_OPTIONS =
            ReaderAppearance.paragraphSpacingOptionsDp();
    private static final int[] READER_MARGIN_OPTIONS =
            ReaderAppearance.horizontalMarginOptionsDp();
    private static final int[] AUTO_SCROLL_RESUME_DELAY_OPTIONS =
            AutoScrollCompanionSettings.resumeDelayOptionsSeconds();
    private static final int[] SPEECH_PERCENT_OPTIONS = SpeechSettings.percentOptions();

    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private ReaderSurfaceView readerSurface;
    private TextView status;
    private TextView readerTitle;
    private TextView importEncodingStatus;
    private ScrollView controlsScroll;
    private Button controlsToggleButton;
    private Button quickAutoScrollButton;
    private Button quickSpeechButton;
    private LinearLayout companionMiniBar;
    private TextView companionMiniStatus;
    private FrameLayout readerHost;
    private View readerEmptyState;
    private View readerAppBar;
    private View readerQuickActionBar;
    private boolean readerChromeVisible = true;
    private boolean compactUiForLargeText;
    private View readerSettingsTarget;
    private View companionSettingsTarget;
    private View chapterSettingsTarget;
    private View repairSettingsTarget;
    private View librarySettingsTarget;
    private Button importEncodingPreviousButton;
    private Button importEncodingNextButton;
    private AndroidTextToSpeechAdapter speech;
    private AndroidAudioInterruptionAdapter audioInterruptions;
    private EditText repairMatchInput;
    private EditText repairReplacementInput;
    private EditText repairNoteInput;
    private EditText searchInput;
    private Spinner repairRuleSpinner;
    private Spinner repairScopeSpinner;
    private Spinner repairOccurrenceSpinner;
    private TextView repairPreviewPageStatus;
    private EditText repairRangeStartInput;
    private EditText repairRangeEndInput;
    private Spinner storageLimitSpinner;
    private Spinner inactiveGroupsSpinner;
    private TextView storagePolicyStatus;
    private Spinner sleepTimerSpinner;
    private TextView sleepTimerStatus;
    private Spinner speechVoiceSpinner;
    private Spinner speechRateSpinner;
    private Spinner speechPitchSpinner;
    private final List<TextToSpeechPort.VoiceInfo> speechVoiceOptions = new ArrayList<>();
    private SpeechSettings speechSettings = SpeechSettings.defaults();
    private TextView exportRecoveryStatus;
    private Button retryExportButton;
    private Spinner bookshelfSpinner;
    private Spinner importEncodingSpinner;
    private final List<BookLibraryEntry> bookshelf = new ArrayList<>();
    private Spinner bookmarkSpinner;
    private final List<BookBookmark> currentBookmarks = new ArrayList<>();
    private final BookLibraryStore bookLibraryStore = new BookLibraryStore();
    private final BookShelfStateStore bookShelfStateStore = new BookShelfStateStore();
    private final BookBookmarkStore bookBookmarkStore = new BookBookmarkStore();
    private final BookEncodingProfileStore bookEncodingProfileStore =
            new BookEncodingProfileStore();
    private Spinner chapterSpinner;
    private final List<ChapterEntry> currentChapters = new ArrayList<>();
    private ChapterOutline currentChapterOutline;
    private final ChapterOutlineStore chapterOutlineStore = new ChapterOutlineStore();
    private volatile long chapterOutlineRequest;
    private boolean chapterOutlineTransitionInProgress;
    private Spinner readerThemeSpinner;
    private Spinner readerFontSpinner;
    private Spinner readerTextSizeSpinner;
    private Spinner readerLineHeightSpinner;
    private Spinner readerParagraphSpacingSpinner;
    private Spinner readerMarginSpinner;
    private Spinner readerOrientationSpinner;
    private Spinner readerModeSpinner;
    private Spinner readerVolumeKeySpinner;
    private ReaderAppearance readerAppearance = ReaderAppearance.defaults();
    private ReaderDisplayPolicy readerDisplayPolicy = ReaderDisplayPolicy.defaults();
    private ReaderNavigationSettings readerNavigationSettings =
            ReaderNavigationSettings.defaults();
    private SeekBar autoScrollSpeedSeekBar;
    private TextView autoScrollSpeedStatus;
    private AutoScrollPolicy autoScrollPolicy = AutoScrollPolicy.defaults();
    private Spinner autoScrollResumeDelaySpinner;
    private CheckBox autoScrollKeepScreenOnCheckBox;
    private AutoScrollCompanionSettings autoScrollCompanionSettings =
            AutoScrollCompanionSettings.defaults();
    private final AutoScrollResumeSession autoScrollResumeSession =
            new AutoScrollResumeSession(AutoScrollCompanionSettings.defaults());
    private final Handler autoScrollResumeHandler = new Handler(Looper.getMainLooper());
    private final Runnable autoScrollResumeTick = this::checkAutoScrollResume;
    private int autoScrollResumeAnnouncedSeconds = -1;
    private String currentBookId = "";
    private volatile long bookshelfRequest;
    private long bookmarkRequest;
    private boolean libraryTransitionInProgress;
    private final List<RepairRule> repairRules = new ArrayList<>();
    private final CompanionSleepTimer sleepTimer = new CompanionSleepTimer();
    private final Handler sleepTimerHandler = new Handler(Looper.getMainLooper());
    private final Runnable sleepTimerTick = this::checkCompanionSleepTimer;
    private final List<RepairMatch> previewOccurrences = new ArrayList<>();
    private final Set<RepairOccurrence> excludedOccurrences = new HashSet<>();
    private int selectedRepairRule;
    private boolean refreshingRepairRules;
    private boolean repairApplying;
    private boolean exportInProgress;
    private long repairRulesGeneration;
    private long repairRulesPersistedGeneration = -1;
    private long repairPreviewOffset;
    private long repairPreviewPageRequest;
    private long repairRangeRequest;
    private long generatedArtifactLimitBytes = GENERATED_ARTIFACT_LIMIT_OPTIONS[0];
    private int maximumInactiveArtifactGroups = INACTIVE_ARTIFACT_GROUP_OPTIONS[2];
    private volatile long storagePolicyGeneration;
    private String currentText = "";
    private String currentRevision = "";
    private DocumentIndex currentWindowIndex;
    private DiskDocumentIndex currentDiskIndex;
    private int currentWindowStart;
    private Path baseOriginalPath;
    private String baseRevision = "";
    private Path activeTextPath;
    private Path activeProjectionPath;
    private BookEncodingProfile currentBookEncodingProfile;
    private int encodingReplacementCursor = -1;
    private PendingRepair pendingRepair;
    private int chapterCursor = -1;
    private String sleepTimerRevision = "";
    private RetainedSleepTimer retainedSleepTimer;
    private SpeechPlaybackQueue speechQueue;
    private long speechGeneration;
    private volatile long viewportRequest;
    private volatile long selectionRuleRequest;
    private boolean autoScrollWindowContinuationPending;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        loadStoragePolicy();
        loadReaderAppearance();
        configureEdgeToEdge();
        loadReaderDisplayPolicy();
        loadReaderNavigationSettings();
        loadAutoScrollPolicy();
        loadAutoScrollCompanionSettings();
        loadSpeechSettings();
        speech = new AndroidTextToSpeechAdapter(this, available ->
                runOnUiThread(() -> {
                    if (!isFinishing() && !isDestroyed()) {
                        refreshSpeechVoices(false);
                    }
                }));
        audioInterruptions = new AndroidAudioInterruptionAdapter(
                this, this::pauseSpeechForInterruption);
        setContentView(createContentView());
        updateSystemBarIconAppearance();
        refreshSpeechVoices(false);
        applyRequestedReaderOrientation();
        speech.setListener(createSpeechListener());
        Object retained = getLastNonConfigurationInstance();
        if (retained instanceof RetainedSleepTimer) {
            retainedSleepTimer = (RetainedSleepTimer) retained;
        }
        Uri incoming = getIntent() == null ? null : getIntent().getData();
        if (incoming != null) {
            retainedSleepTimer = null;
            importUri(incoming);
        } else {
            resumeBookshelf();
        }
    }

    private LinearLayout createContentView() {
        compactUiForLargeText = getResources().getConfiguration().fontScale >= 1.5f;
        int padding = Math.round(12 * getResources().getDisplayMetrics().density);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(padding, padding, padding, padding);
        root.setBackgroundColor(Color.rgb(250, 248, 244));

        addSectionHeading(root, R.string.section_library,
                R.string.section_library_description);

        Button importButton = new Button(this);
        importButton.setText(R.string.choose_txt);
        importButton.setOnClickListener(view -> openTextPicker());
        LinearLayout importControls = new LinearLayout(this);
        importControls.setOrientation(LinearLayout.HORIZONTAL);
        librarySettingsTarget = importControls;
        importEncodingSpinner = new Spinner(this);
        importEncodingSpinner.setContentDescription(
                getString(R.string.import_encoding_description));
        importEncodingSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[] {getString(R.string.import_encoding_auto),
                        "UTF-8", "GB18030", "GBK", "GB2312", "Big5",
                        "UTF-16LE", "UTF-16BE"}));
        importControls.addView(importEncodingSpinner, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        importControls.addView(importButton, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(importControls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        importEncodingStatus = new TextView(this);
        importEncodingStatus.setText(R.string.import_encoding_status_empty);
        root.addView(importEncodingStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout encodingNavigation = new LinearLayout(this);
        encodingNavigation.setOrientation(LinearLayout.HORIZONTAL);
        importEncodingPreviousButton = new Button(this);
        importEncodingPreviousButton.setText(R.string.import_encoding_previous);
        importEncodingPreviousButton.setEnabled(false);
        importEncodingPreviousButton.setOnClickListener(
                view -> navigateEncodingReplacement(false));
        encodingNavigation.addView(importEncodingPreviousButton,
                new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        importEncodingNextButton = new Button(this);
        importEncodingNextButton.setText(R.string.import_encoding_next);
        importEncodingNextButton.setEnabled(false);
        importEncodingNextButton.setOnClickListener(
                view -> navigateEncodingReplacement(true));
        encodingNavigation.addView(importEncodingNextButton,
                new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(encodingNavigation, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout bookshelfControls = new LinearLayout(this);
        bookshelfControls.setOrientation(LinearLayout.HORIZONTAL);
        bookshelfSpinner = new Spinner(this);
        bookshelfSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[] {getString(R.string.bookshelf_empty)}));
        bookshelfControls.addView(bookshelfSpinner, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 2));
        addRuleAction(bookshelfControls, R.string.bookshelf_open,
                view -> openSelectedBook());
        root.addView(bookshelfControls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout bookshelfActions = new LinearLayout(this);
        bookshelfActions.setOrientation(LinearLayout.HORIZONTAL);
        addRuleAction(bookshelfActions, R.string.bookshelf_remove,
                view -> confirmRemoveSelectedBook());
        addRuleAction(bookshelfActions, R.string.bookshelf_restore_removed,
                view -> restoreMostRecentRemovedBook());
        addRuleAction(bookshelfActions, R.string.bookshelf_delete_copy,
                view -> confirmDeleteSelectedBook());
        root.addView(bookshelfActions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout bookmarkControls = new LinearLayout(this);
        bookmarkControls.setOrientation(LinearLayout.HORIZONTAL);
        bookmarkSpinner = new Spinner(this);
        bookmarkSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[] {getString(R.string.bookmark_empty)}));
        bookmarkControls.addView(bookmarkSpinner, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 2));
        addRuleAction(bookmarkControls, R.string.bookmark_add,
                view -> addCurrentBookmark());
        root.addView(bookmarkControls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout bookmarkActions = new LinearLayout(this);
        bookmarkActions.setOrientation(LinearLayout.HORIZONTAL);
        addRuleAction(bookmarkActions, R.string.bookmark_jump,
                view -> jumpToSelectedBookmark());
        addRuleAction(bookmarkActions, R.string.bookmark_delete,
                view -> deleteSelectedBookmark());
        root.addView(bookmarkActions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        addSectionHeading(root, R.string.section_reading,
                R.string.section_reading_description);

        LinearLayout appearancePrimary = new LinearLayout(this);
        appearancePrimary.setOrientation(LinearLayout.HORIZONTAL);
        readerSettingsTarget = appearancePrimary;
        readerThemeSpinner = new Spinner(this);
        readerThemeSpinner.setContentDescription(getString(R.string.reader_theme_description));
        readerThemeSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[] {getString(R.string.reader_theme_day),
                        getString(R.string.reader_theme_eye),
                        getString(R.string.reader_theme_night)}));
        appearancePrimary.addView(readerThemeSpinner, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        readerFontSpinner = new Spinner(this);
        readerFontSpinner.setContentDescription(
                getString(R.string.reader_font_description));
        readerFontSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[] {getString(R.string.reader_font_system_sans),
                        getString(R.string.reader_font_serif),
                        getString(R.string.reader_font_monospace)}));
        appearancePrimary.addView(readerFontSpinner, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        readerTextSizeSpinner = new Spinner(this);
        readerTextSizeSpinner.setContentDescription(
                getString(R.string.reader_text_size_description));
        readerTextSizeSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                appearanceLabels(READER_TEXT_SIZE_OPTIONS, R.string.reader_text_size_item)));
        appearancePrimary.addView(readerTextSizeSpinner, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(appearancePrimary, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout appearanceSecondary = new LinearLayout(this);
        appearanceSecondary.setOrientation(LinearLayout.HORIZONTAL);
        readerLineHeightSpinner = new Spinner(this);
        readerLineHeightSpinner.setContentDescription(
                getString(R.string.reader_line_height_description));
        readerLineHeightSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                appearanceLabels(READER_LINE_HEIGHT_OPTIONS,
                        R.string.reader_line_height_item)));
        appearanceSecondary.addView(readerLineHeightSpinner, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        readerParagraphSpacingSpinner = new Spinner(this);
        readerParagraphSpacingSpinner.setContentDescription(
                getString(R.string.reader_paragraph_spacing_description));
        readerParagraphSpacingSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                appearanceLabels(READER_PARAGRAPH_SPACING_OPTIONS,
                        R.string.reader_paragraph_spacing_item)));
        appearanceSecondary.addView(readerParagraphSpacingSpinner,
                new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        readerMarginSpinner = new Spinner(this);
        readerMarginSpinner.setContentDescription(
                getString(R.string.reader_margin_description));
        readerMarginSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                appearanceLabels(READER_MARGIN_OPTIONS, R.string.reader_margin_item)));
        appearanceSecondary.addView(readerMarginSpinner, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(appearanceSecondary, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout appearanceOrientation = new LinearLayout(this);
        appearanceOrientation.setOrientation(LinearLayout.HORIZONTAL);
        readerOrientationSpinner = new Spinner(this);
        readerOrientationSpinner.setContentDescription(
                getString(R.string.reader_orientation_description));
        readerOrientationSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[] {getString(R.string.reader_orientation_follow_system),
                        getString(R.string.reader_orientation_portrait),
                        getString(R.string.reader_orientation_landscape)}));
        appearanceOrientation.addView(readerOrientationSpinner,
                new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 2));
        addRuleAction(appearanceOrientation, R.string.reader_appearance_apply,
                view -> applyReaderAppearance());
        root.addView(appearanceOrientation, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        selectReaderAppearanceControls();

        LinearLayout readerNavigation = new LinearLayout(this);
        readerNavigation.setOrientation(LinearLayout.HORIZONTAL);
        readerModeSpinner = new Spinner(this);
        readerModeSpinner.setContentDescription(
                getString(R.string.reader_mode_description));
        readerModeSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[] {getString(R.string.reader_mode_continuous),
                        getString(R.string.reader_mode_paged)}));
        readerNavigation.addView(readerModeSpinner, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        readerVolumeKeySpinner = new Spinner(this);
        readerVolumeKeySpinner.setContentDescription(
                getString(R.string.reader_volume_key_description));
        readerVolumeKeySpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[] {getString(R.string.reader_volume_key_off),
                        getString(R.string.reader_volume_key_down_forward),
                        getString(R.string.reader_volume_key_up_forward)}));
        readerNavigation.addView(readerVolumeKeySpinner,
                new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        addRuleAction(readerNavigation, R.string.reader_navigation_apply,
                view -> applyReaderNavigationSettings());
        root.addView(readerNavigation, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        selectReaderNavigationControls();

        addSectionHeading(root, R.string.section_companion,
                R.string.section_companion_description);

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.HORIZONTAL);
        companionSettingsTarget = controls;
        Button autoScroll = new Button(this);
        autoScroll.setText(R.string.auto_scroll);
        autoScroll.setOnClickListener(view -> toggleAutoScroll());
        controls.addView(autoScroll, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button speak = new Button(this);
        speak.setText(R.string.speak);
        speak.setOnClickListener(view -> startSpeaking());
        controls.addView(speak, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button stop = new Button(this);
        stop.setText(R.string.stop);
        stop.setOnClickListener(view -> stopCompanionMode());
        controls.addView(stop, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(controls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        autoScrollSpeedStatus = new TextView(this);
        updateAutoScrollSpeedStatus(autoScrollPolicy.getSpeedDpPerSecond());
        root.addView(autoScrollSpeedStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        autoScrollSpeedSeekBar = new SeekBar(this);
        autoScrollSpeedSeekBar.setMin(AutoScrollPolicy.minimumSpeedDpPerSecond());
        autoScrollSpeedSeekBar.setMax(AutoScrollPolicy.maximumSpeedDpPerSecond());
        autoScrollSpeedSeekBar.setProgress(autoScrollPolicy.getSpeedDpPerSecond());
        autoScrollSpeedSeekBar.setContentDescription(getString(
                R.string.auto_scroll_speed_description,
                autoScrollPolicy.getSpeedDpPerSecond()));
        autoScrollSpeedSeekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress,
                            boolean fromUser) {
                        updateAutoScrollSpeedStatus(progress);
                        seekBar.setContentDescription(getString(
                                R.string.auto_scroll_speed_description, progress));
                        if (fromUser && readerSurface != null) {
                            readerSurface.setAutoScrollSpeedDpPerSecond(progress);
                            if (!seekBar.isPressed()) {
                                persistAutoScrollSpeed(progress);
                            }
                        }
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                        // Live preview is applied by onProgressChanged.
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                        persistAutoScrollSpeed(seekBar.getProgress());
                    }
                });
        root.addView(autoScrollSpeedSeekBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout autoScrollCompanionControls = new LinearLayout(this);
        autoScrollCompanionControls.setOrientation(LinearLayout.HORIZONTAL);
        autoScrollResumeDelaySpinner = new Spinner(this);
        autoScrollResumeDelaySpinner.setContentDescription(
                getString(R.string.auto_scroll_resume_delay_description));
        autoScrollResumeDelaySpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[] {getString(R.string.auto_scroll_resume_off),
                        getString(R.string.auto_scroll_resume_3),
                        getString(R.string.auto_scroll_resume_5),
                        getString(R.string.auto_scroll_resume_10)}));
        autoScrollCompanionControls.addView(autoScrollResumeDelaySpinner,
                new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 2));
        autoScrollKeepScreenOnCheckBox = new CheckBox(this);
        autoScrollKeepScreenOnCheckBox.setText(R.string.auto_scroll_keep_screen_on);
        autoScrollCompanionControls.addView(autoScrollKeepScreenOnCheckBox,
                new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        addRuleAction(autoScrollCompanionControls, R.string.auto_scroll_companion_apply,
                view -> applyAutoScrollCompanionSettings());
        root.addView(autoScrollCompanionControls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        selectAutoScrollCompanionControls();

        LinearLayout speechNavigation = new LinearLayout(this);
        speechNavigation.setOrientation(LinearLayout.HORIZONTAL);
        addRuleAction(speechNavigation, R.string.speech_previous,
                view -> moveSpeechPrevious());
        addRuleAction(speechNavigation, R.string.speech_pause,
                view -> pauseSpeaking());
        addRuleAction(speechNavigation, R.string.speech_resume,
                view -> resumeSpeaking());
        addRuleAction(speechNavigation, R.string.speech_next,
                view -> moveSpeechNext());
        root.addView(speechNavigation, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout speechVoiceControls = new LinearLayout(this);
        speechVoiceControls.setOrientation(LinearLayout.HORIZONTAL);
        speechVoiceSpinner = new Spinner(this);
        speechVoiceSpinner.setContentDescription(
                getString(R.string.speech_voice_description));
        speechVoiceSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[] {getString(R.string.speech_voice_system_default)}));
        speechVoiceControls.addView(speechVoiceSpinner,
                new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 3));
        addRuleAction(speechVoiceControls, R.string.speech_voice_refresh,
                view -> refreshSpeechVoices(true));
        root.addView(speechVoiceControls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout speechTuningControls = new LinearLayout(this);
        speechTuningControls.setOrientation(LinearLayout.HORIZONTAL);
        speechRateSpinner = new Spinner(this);
        speechRateSpinner.setContentDescription(
                getString(R.string.speech_rate_description));
        speechRateSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                appearanceLabels(SPEECH_PERCENT_OPTIONS, R.string.speech_rate_option)));
        speechTuningControls.addView(speechRateSpinner,
                new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        speechPitchSpinner = new Spinner(this);
        speechPitchSpinner.setContentDescription(
                getString(R.string.speech_pitch_description));
        speechPitchSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                appearanceLabels(SPEECH_PERCENT_OPTIONS, R.string.speech_pitch_option)));
        speechTuningControls.addView(speechPitchSpinner,
                new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        addRuleAction(speechTuningControls, R.string.speech_settings_apply,
                view -> applySpeechSettings());
        root.addView(speechTuningControls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        selectSpeechSettingsControls();

        LinearLayout sleepControls = new LinearLayout(this);
        sleepControls.setOrientation(LinearLayout.HORIZONTAL);
        sleepTimerSpinner = new Spinner(this);
        sleepTimerSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[] {getString(R.string.sleep_timer_off),
                        getString(R.string.sleep_timer_15),
                        getString(R.string.sleep_timer_30),
                        getString(R.string.sleep_timer_60),
                        getString(R.string.sleep_timer_chapter)}));
        sleepControls.addView(sleepTimerSpinner, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 2));
        addRuleAction(sleepControls, R.string.sleep_timer_apply,
                view -> applyCompanionSleepTimer());
        root.addView(sleepControls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        sleepTimerStatus = new TextView(this);
        sleepTimerStatus.setText(R.string.sleep_timer_inactive);
        root.addView(sleepTimerStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout navigation = new LinearLayout(this);
        navigation.setOrientation(LinearLayout.HORIZONTAL);
        addSectionHeading(root, R.string.section_navigation,
                R.string.section_navigation_description);
        searchInput = new EditText(this);
        searchInput.setHint(R.string.search_hint);
        searchInput.setSingleLine(true);
        navigation.addView(searchInput, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        Button search = new Button(this);
        search.setText(R.string.search);
        search.setOnClickListener(view -> searchCurrentWindow(
                searchInput.getText().toString()));
        navigation.addView(search, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        Button nextChapter = new Button(this);
        nextChapter.setText(R.string.next_chapter);
        nextChapter.setOnClickListener(view -> jumpToNextChapter());
        navigation.addView(nextChapter, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(navigation, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout chapterControls = new LinearLayout(this);
        chapterControls.setOrientation(LinearLayout.HORIZONTAL);
        chapterSettingsTarget = chapterControls;
        chapterSpinner = new Spinner(this);
        chapterSpinner.setContentDescription(getString(R.string.chapter_outline_description));
        chapterSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[] {getString(R.string.no_chapters)}));
        chapterControls.addView(chapterSpinner, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 2));
        addRuleAction(chapterControls, R.string.chapter_open,
                view -> openSelectedChapter());
        root.addView(chapterControls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout chapterActions = new LinearLayout(this);
        chapterActions.setOrientation(LinearLayout.HORIZONTAL);
        addRuleAction(chapterActions, R.string.chapter_rename,
                view -> promptRenameSelectedChapter());
        addRuleAction(chapterActions, R.string.chapter_split,
                view -> promptSplitChapterAtCurrentPosition());
        addRuleAction(chapterActions, R.string.chapter_merge_next,
                view -> confirmMergeSelectedChapter());
        root.addView(chapterActions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout repairInputs = new LinearLayout(this);
        repairInputs.setOrientation(LinearLayout.HORIZONTAL);
        addSectionHeading(root, R.string.section_cleaning,
                R.string.section_cleaning_description);
        repairSettingsTarget = repairInputs;
        repairMatchInput = new EditText(this);
        repairMatchInput.setHint(R.string.repair_match_hint);
        repairMatchInput.setText(R.string.repair_default_match);
        repairMatchInput.setSingleLine(true);
        repairInputs.addView(repairMatchInput, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        repairReplacementInput = new EditText(this);
        repairReplacementInput.setHint(R.string.repair_replacement_hint);
        repairReplacementInput.setText(R.string.repair_default_replacement);
        repairReplacementInput.setSingleLine(true);
        repairInputs.addView(repairReplacementInput, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(repairInputs, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        repairRuleSpinner = new Spinner(this);
        repairRuleSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, android.view.View view,
                    int position, long id) {
                if (!refreshingRepairRules && position >= 0 && position < repairRules.size()) {
                    selectedRepairRule = position;
                    showSelectedRepairRule();
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        root.addView(repairRuleSpinner, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout repairMetadata = new LinearLayout(this);
        repairMetadata.setOrientation(LinearLayout.HORIZONTAL);
        repairScopeSpinner = new Spinner(this);
        repairScopeSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[] {getString(R.string.repair_scope_book),
                        getString(R.string.repair_scope_all)}));
        repairMetadata.addView(repairScopeSpinner, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        repairNoteInput = new EditText(this);
        repairNoteInput.setHint(R.string.repair_note_hint);
        repairNoteInput.setSingleLine(true);
        repairMetadata.addView(repairNoteInput, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 2));
        root.addView(repairMetadata, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout repairEditorActions = new LinearLayout(this);
        repairEditorActions.setOrientation(LinearLayout.HORIZONTAL);
        addRuleAction(repairEditorActions, R.string.repair_add, view -> addRepairRule());
        addRuleAction(repairEditorActions, R.string.repair_save, view -> saveSelectedRepairRule());
        addRuleAction(repairEditorActions, R.string.repair_toggle, view -> toggleSelectedRepairRule());
        addRuleAction(repairEditorActions, R.string.repair_move_up, view -> moveSelectedRepairRuleUp());
        addRuleAction(repairEditorActions, R.string.repair_move_down,
                view -> moveSelectedRepairRuleDown());
        addRuleAction(repairEditorActions, R.string.repair_delete, view -> deleteSelectedRepairRule());
        root.addView(repairEditorActions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout occurrenceActions = new LinearLayout(this);
        occurrenceActions.setOrientation(LinearLayout.HORIZONTAL);
        repairOccurrenceSpinner = new Spinner(this);
        occurrenceActions.addView(repairOccurrenceSpinner, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 3));
        addRuleAction(occurrenceActions, R.string.repair_toggle_occurrence,
                view -> toggleSelectedOccurrence());
        root.addView(occurrenceActions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout occurrencePages = new LinearLayout(this);
        occurrencePages.setOrientation(LinearLayout.HORIZONTAL);
        repairPreviewPageStatus = new TextView(this);
        repairPreviewPageStatus.setText(R.string.repair_preview_page_empty);
        occurrencePages.addView(repairPreviewPageStatus, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 2));
        addRuleAction(occurrencePages, R.string.repair_previous_page,
                view -> loadPreviousRepairPreviewPage());
        addRuleAction(occurrencePages, R.string.repair_next_page,
                view -> loadNextRepairPreviewPage());
        root.addView(occurrencePages, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout occurrenceBatchActions = new LinearLayout(this);
        occurrenceBatchActions.setOrientation(LinearLayout.HORIZONTAL);
        addRuleAction(occurrenceBatchActions, R.string.repair_skip_page,
                view -> setPreviewPageApplied(false));
        addRuleAction(occurrenceBatchActions, R.string.repair_apply_page,
                view -> setPreviewPageApplied(true));
        addRuleAction(occurrenceBatchActions, R.string.repair_select_all,
                view -> selectAllOccurrences());
        root.addView(occurrenceBatchActions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout occurrenceRange = new LinearLayout(this);
        occurrenceRange.setOrientation(LinearLayout.HORIZONTAL);
        repairRangeStartInput = new EditText(this);
        repairRangeStartInput.setHint(R.string.repair_range_start_hint);
        repairRangeStartInput.setSingleLine(true);
        repairRangeStartInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        occurrenceRange.addView(repairRangeStartInput, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        repairRangeEndInput = new EditText(this);
        repairRangeEndInput.setHint(R.string.repair_range_end_hint);
        repairRangeEndInput.setSingleLine(true);
        repairRangeEndInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        occurrenceRange.addView(repairRangeEndInput, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        addRuleAction(occurrenceRange, R.string.repair_skip_range,
                view -> setOccurrenceRangeApplied(false));
        addRuleAction(occurrenceRange, R.string.repair_apply_range,
                view -> setOccurrenceRangeApplied(true));
        root.addView(occurrenceRange, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout transferActions = new LinearLayout(this);
        transferActions.setOrientation(LinearLayout.HORIZONTAL);
        addRuleAction(transferActions, R.string.repair_import_pack,
                view -> openRulePackImport());
        addRuleAction(transferActions, R.string.repair_export_pack,
                view -> createRulePackExport());
        addRuleAction(transferActions, R.string.repair_export_text,
                view -> createCleanTextExport());
        root.addView(transferActions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout exportRecoveryActions = new LinearLayout(this);
        exportRecoveryActions.setOrientation(LinearLayout.HORIZONTAL);
        exportRecoveryStatus = new TextView(this);
        exportRecoveryActions.addView(exportRecoveryStatus, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 2));
        retryExportButton = new Button(this);
        retryExportButton.setText(R.string.repair_export_retry);
        retryExportButton.setOnClickListener(view -> retryPendingExport());
        exportRecoveryActions.addView(retryExportButton, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(exportRecoveryActions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout storagePolicy = new LinearLayout(this);
        storagePolicy.setOrientation(LinearLayout.HORIZONTAL);
        storageLimitSpinner = new Spinner(this);
        storageLimitSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[] {getString(R.string.storage_limit_512),
                        getString(R.string.storage_limit_1024),
                        getString(R.string.storage_limit_2048)}));
        storageLimitSpinner.setSelection(indexOf(
                GENERATED_ARTIFACT_LIMIT_OPTIONS, generatedArtifactLimitBytes));
        storagePolicy.addView(storageLimitSpinner, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        inactiveGroupsSpinner = new Spinner(this);
        inactiveGroupsSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[] {getString(R.string.storage_groups_0),
                        getString(R.string.storage_groups_2),
                        getString(R.string.storage_groups_4)}));
        inactiveGroupsSpinner.setSelection(indexOf(
                INACTIVE_ARTIFACT_GROUP_OPTIONS, maximumInactiveArtifactGroups));
        storagePolicy.addView(inactiveGroupsSpinner, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        addRuleAction(storagePolicy, R.string.storage_save_and_clean,
                view -> saveStoragePolicyAndPrune());
        root.addView(storagePolicy, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        storagePolicyStatus = new TextView(this);
        storagePolicyStatus.setText(getString(R.string.storage_policy_current,
                generatedArtifactLimitBytes / MEBIBYTE, maximumInactiveArtifactGroups));
        root.addView(storagePolicyStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout repairActions = new LinearLayout(this);
        repairActions.setOrientation(LinearLayout.HORIZONTAL);
        Button previewRepair = new Button(this);
        previewRepair.setText(R.string.repair_preview);
        previewRepair.setOnClickListener(view -> previewRepairFromStart());
        repairActions.addView(previewRepair, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button applyRepair = new Button(this);
        applyRepair.setText(R.string.repair_apply);
        applyRepair.setOnClickListener(view -> applyPendingRepair());
        repairActions.addView(applyRepair, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        Button undoRepair = new Button(this);
        undoRepair.setText(R.string.repair_undo);
        undoRepair.setOnClickListener(view -> undoRepair());
        repairActions.addView(undoRepair, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        root.addView(repairActions, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        status = new TextView(this);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        status.setText(R.string.initial_status);
        status.setTextSize(compactUiForLargeText ? 10 : 13);
        status.setTextColor(Color.rgb(89, 82, 73));
        status.setPadding(dp(16), dp(8), dp(16), dp(8));
        status.setBackgroundColor(Color.rgb(247, 242, 234));

        readerSurface = new ReaderSurfaceView(this);
        readerSurface.setAutoScrollListener(running -> {
            readerSurface.setKeepScreenOn(
                    running && autoScrollCompanionSettings.isKeepScreenOn());
            status.setText(running
                    ? getString(R.string.auto_scroll_started_speed,
                            readerSurface.getAutoScrollSpeedDpPerSecond())
                    : getString(R.string.auto_scroll_stopped));
            updateQuickActionState();
        });
        readerSurface.setReaderTouchListener((finished, pausedAutoScroll, anchorOffset) -> {
            if (!finished) {
                cancelAutoScrollResume();
                if (speechQueue != null
                        && speechQueue.getState() == SpeechPlaybackQueue.State.PLAYING) {
                    pauseSpeaking();
                }
            } else if (pausedAutoScroll) {
                scheduleAutoScrollResumeAfterTouch(anchorOffset);
            } else {
                cancelAutoScrollResume();
            }
        });
        readerSurface.setViewportBoundaryListener(
                this::loadAdjacentReaderWindow);
        readerSurface.setSelectionActionListener(this::handleReaderSelectionAction);
        readerSurface.setOnClickListener(view -> toggleReaderChrome());
        readerSurface.setAutoScrollSpeedDpPerSecond(
                autoScrollPolicy.getSpeedDpPerSecond());
        readerSurface.applyTypography(readerAppearance.toTypographyJson());
        readerSurface.setMode(surfaceMode(
                readerNavigationSettings.getReadingMode()));
        readerSurface.setDocumentText(getString(R.string.initial_reader_text), "initial");
        replaceRepairRules(defaultRepairRules());
        refreshOccurrenceSpinner(Collections.emptyList());
        refreshExportRecoveryStatus();
        styleControlTree(root);
        controlsScroll = new ScrollView(this);
        controlsScroll.setFillViewport(true);
        controlsScroll.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        controlsScroll.setVisibility(View.GONE);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(Color.rgb(250, 248, 244));

        LinearLayout appBar = new LinearLayout(this);
        appBar.setOrientation(LinearLayout.HORIZONTAL);
        appBar.setGravity(Gravity.CENTER_VERTICAL);
        appBar.setPadding(dp(16), dp(8), dp(8), dp(8));
        appBar.setBackgroundColor(Color.rgb(250, 248, 244));
        readerAppBar = appBar;
        readerTitle = new TextView(this);
        readerTitle.setText(R.string.app_name_short);
        readerTitle.setTextSize(compactUiForLargeText ? 15 : 22);
        readerTitle.setTextColor(Color.rgb(38, 35, 31));
        readerTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        readerTitle.setSingleLine(true);
        readerTitle.setEllipsize(TextUtils.TruncateAt.END);
        readerTitle.setContentDescription(
                getString(R.string.quick_library_description));
        readerTitle.setBackground(rippleBackground(Color.TRANSPARENT,
                Color.rgb(232, 224, 210), dp(12)));
        readerTitle.setOnClickListener(view -> showBookshelfQuickDialog());
        appBar.addView(readerTitle, new LinearLayout.LayoutParams(0,
                dp(48), 1));
        Button quickImport = compactButton(R.string.quick_import,
                view -> openTextPicker());
        styleToolbarButton(quickImport);
        appBar.addView(quickImport, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
        controlsToggleButton = new Button(this);
        controlsToggleButton.setText(R.string.quick_settings);
        controlsToggleButton.setContentDescription(
                getString(R.string.quick_settings_description));
        controlsToggleButton.setOnClickListener(view -> {
            if (controlsScroll.getVisibility() == View.VISIBLE) {
                toggleControlPanel();
            } else {
                showQuickToolsMenu();
            }
        });
        styleCompactButton(controlsToggleButton);
        styleToolbarButton(controlsToggleButton);
        appBar.addView(controlsToggleButton, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(48)));
        page.addView(appBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(status, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        page.addView(controlsScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        readerHost = new FrameLayout(this);
        readerHost.addView(readerSurface, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        readerEmptyState = createReaderEmptyState();
        readerHost.addView(readerEmptyState, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        readerSurface.setVisibility(View.GONE);
        page.addView(readerHost, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        page.addView(createCompanionMiniBar(), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        readerQuickActionBar = createQuickActionBar();
        page.addView(readerQuickActionBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(68)));
        applySystemBarInsets(page);
        return page;
    }

    private LinearLayout createQuickActionBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER);
        bar.setPadding(dp(8), dp(6), dp(8), dp(6));
        bar.setBackgroundColor(Color.rgb(255, 253, 249));
        bar.setElevation(dp(8));

        addQuickAction(bar, R.drawable.ic_contents, R.string.quick_contents,
                R.string.quick_contents_description,
                view -> showChapterQuickDialog());
        addQuickAction(bar, R.drawable.ic_search, R.string.quick_search,
                R.string.quick_search_description,
                view -> showSearchQuickDialog());
        quickAutoScrollButton = addQuickAction(bar, R.drawable.ic_auto_scroll,
                R.string.quick_auto_scroll,
                R.string.quick_auto_scroll_description,
                view -> toggleAutoScroll());
        quickAutoScrollButton.setOnLongClickListener(view -> {
            openControlPanelAt(companionSettingsTarget);
            return true;
        });
        quickSpeechButton = addQuickAction(bar, R.drawable.ic_speech,
                R.string.quick_speech,
                R.string.quick_speech_description,
                view -> toggleQuickSpeech());
        quickSpeechButton.setOnLongClickListener(view -> {
            openControlPanelAt(companionSettingsTarget);
            return true;
        });
        Button clean = addQuickAction(bar, R.drawable.ic_clean, R.string.quick_clean,
                R.string.quick_clean_description,
                view -> showCleaningQuickDialog());
        clean.setOnLongClickListener(view -> {
            showQuickToolsMenu();
            return true;
        });
        updateQuickActionState();
        return bar;
    }

    private LinearLayout createCompanionMiniBar() {
        companionMiniBar = new LinearLayout(this);
        companionMiniBar.setOrientation(LinearLayout.HORIZONTAL);
        companionMiniBar.setGravity(Gravity.CENTER_VERTICAL);
        companionMiniBar.setPadding(dp(12), dp(6), dp(8), dp(6));
        companionMiniBar.setBackgroundColor(Color.rgb(241, 235, 225));
        companionMiniStatus = new TextView(this);
        companionMiniStatus.setTextSize(13);
        companionMiniStatus.setTextColor(Color.rgb(64, 57, 49));
        companionMiniBar.addView(companionMiniStatus, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        companionMiniBar.addView(miniControl("‹", R.string.speech_previous,
                view -> moveSpeechPrevious()));
        companionMiniBar.addView(miniControl("›", R.string.speech_next,
                view -> moveSpeechNext()));
        companionMiniBar.addView(miniControl("■", R.string.stop,
                view -> stopCompanionMode()));
        companionMiniBar.setVisibility(View.GONE);
        return companionMiniBar;
    }

    private Button addQuickAction(LinearLayout parent, int icon, int label, int description,
            android.view.View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(compactUiForLargeText ? "" : getString(label));
        button.setContentDescription(getString(description));
        button.setTextSize(12);
        button.setTextColor(Color.rgb(64, 57, 49));
        button.setCompoundDrawablesWithIntrinsicBounds(0, icon, 0, 0);
        button.setCompoundDrawablePadding(dp(3));
        button.setCompoundDrawableTintList(ColorStateList.valueOf(
                Color.rgb(91, 81, 70)));
        button.setAllCaps(false);
        button.setPadding(dp(2), 0, dp(2), 0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setBackground(rippleBackground(Color.TRANSPARENT,
                Color.rgb(232, 224, 210), dp(14)));
        button.setOnClickListener(listener);
        parent.addView(button, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return button;
    }

    private Button miniControl(String symbol, int description,
            android.view.View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(symbol);
        button.setTextSize(21);
        button.setContentDescription(getString(description));
        button.setOnClickListener(listener);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(0, 0, 0, 0);
        button.setBackground(rippleBackground(Color.TRANSPARENT,
                Color.rgb(218, 205, 185), dp(20)));
        button.setLayoutParams(new LinearLayout.LayoutParams(dp(42), dp(42)));
        return button;
    }

    private View createReaderEmptyState() {
        LinearLayout empty = new LinearLayout(this);
        empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.CENTER_VERTICAL);
        empty.setPadding(dp(28), dp(24), dp(28), dp(24));
        empty.setBackgroundColor(Color.rgb(250, 248, 244));

        TextView badge = new TextView(this);
        badge.setText(R.string.empty_state_badge);
        badge.setGravity(Gravity.CENTER);
        badge.setTextSize(18);
        badge.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        badge.setTextColor(Color.rgb(255, 253, 249));
        badge.setBackground(rippleBackground(Color.rgb(91, 81, 70),
                Color.rgb(91, 81, 70), dp(18)));
        empty.addView(badge, new LinearLayout.LayoutParams(dp(72), dp(72)));

        TextView title = new TextView(this);
        title.setText(R.string.empty_state_title);
        title.setGravity(Gravity.CENTER);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setTextColor(Color.rgb(38, 35, 31));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = dp(20);
        empty.addView(title, titleParams);

        TextView message = new TextView(this);
        message.setText(R.string.empty_state_message);
        message.setGravity(Gravity.CENTER);
        message.setTextSize(15);
        message.setLineSpacing(0, 1.25f);
        message.setTextColor(Color.rgb(96, 87, 77));
        LinearLayout.LayoutParams messageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        messageParams.topMargin = dp(12);
        empty.addView(message, messageParams);

        Button importButton = compactButton(R.string.empty_state_import,
                view -> openTextPicker());
        importButton.setTextColor(Color.WHITE);
        importButton.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        importButton.setBackground(rippleBackground(Color.rgb(91, 81, 70),
                Color.rgb(70, 61, 53), dp(22)));
        LinearLayout.LayoutParams importParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        importParams.topMargin = dp(24);
        empty.addView(importButton, importParams);

        TextView privacy = new TextView(this);
        privacy.setText(R.string.empty_state_privacy);
        privacy.setGravity(Gravity.CENTER);
        privacy.setTextSize(12);
        privacy.setTextColor(Color.rgb(111, 99, 84));
        LinearLayout.LayoutParams privacyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        privacyParams.topMargin = dp(14);
        empty.addView(privacy, privacyParams);
        ScrollView scroller = new ScrollView(this);
        scroller.setFillViewport(true);
        scroller.setBackgroundColor(Color.rgb(250, 248, 244));
        scroller.addView(empty, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return scroller;
    }

    private void updateReaderEmptyState(boolean hasOpenBook) {
        if (readerEmptyState != null) {
            readerEmptyState.setVisibility(hasOpenBook ? View.GONE : View.VISIBLE);
        }
        if (readerSurface != null) {
            readerSurface.setVisibility(hasOpenBook ? View.VISIBLE : View.GONE);
        }
    }

    private void showCleaningQuickDialog() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(4), dp(20), 0);
        TextView explanation = new TextView(this);
        explanation.setText(R.string.clean_flow_message);
        explanation.setTextSize(15);
        explanation.setTextColor(Color.rgb(78, 70, 61));
        explanation.setLineSpacing(0, 1.2f);
        content.addView(explanation, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        final AlertDialog[] holder = new AlertDialog[1];
        Button manage = compactButton(R.string.clean_flow_manage, view -> {
            holder[0].dismiss();
            openControlPanelAt(repairSettingsTarget);
        });
        Button preview = compactButton(R.string.clean_flow_preview, view -> {
            holder[0].dismiss();
            openControlPanelAt(repairSettingsTarget);
            previewRepairFromStart();
        });
        Button undo = compactButton(R.string.clean_flow_undo, view -> {
            holder[0].dismiss();
            undoRepair();
        });
        for (Button action : new Button[] {manage, preview, undo}) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
            params.topMargin = dp(10);
            content.addView(action, params);
        }
        holder[0] = new AlertDialog.Builder(this)
                .setTitle(R.string.clean_flow_title)
                .setView(content)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        showAsBottomPanel(holder[0]);
    }

    private void showAsBottomPanel(AlertDialog dialog) {
        dialog.setOnShowListener(ignored -> {
            Window window = dialog.getWindow();
            if (window == null) {
                return;
            }
            for (int which : new int[] {AlertDialog.BUTTON_POSITIVE,
                    AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL}) {
                Button action = dialog.getButton(which);
                if (action != null) {
                    action.setAllCaps(false);
                    action.setTextSize(compactUiForLargeText ? 10 : 14);
                    action.setMinHeight(dp(48));
                    action.setPadding(dp(10), 0, dp(10), 0);
                }
            }
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            GradientDrawable background = new GradientDrawable();
            background.setColor(Color.rgb(255, 253, 249));
            background.setCornerRadius(dp(24));
            window.setBackgroundDrawable(background);
            window.getDecorView().setPadding(dp(8), dp(8), dp(8), dp(8));
        });
        dialog.show();
    }

    private void toggleReaderChrome() {
        if (currentBookId.isEmpty() || controlsScroll == null
                || controlsScroll.getVisibility() == View.VISIBLE) {
            return;
        }
        setReaderChromeVisible(!readerChromeVisible);
    }

    private void setReaderChromeVisible(boolean visible) {
        readerChromeVisible = visible;
        if (readerAppBar != null) {
            readerAppBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        if (status != null) {
            status.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        if (readerQuickActionBar != null) {
            readerQuickActionBar.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private Button compactButton(int label,
            android.view.View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(listener);
        styleCompactButton(button);
        return button;
    }

    private void styleCompactButton(Button button) {
        button.setAllCaps(false);
        button.setTextSize(14);
        button.setTextColor(Color.rgb(72, 63, 52));
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(12), 0, dp(12), 0);
        button.setBackground(rippleBackground(Color.rgb(241, 235, 225),
                Color.rgb(218, 205, 185), dp(18)));
    }

    private void styleToolbarButton(Button button) {
        button.setTextSize(compactUiForLargeText ? 9 : 13);
        button.setPadding(dp(9), 0, dp(9), 0);
        button.setBackground(rippleBackground(Color.TRANSPARENT,
                Color.rgb(232, 224, 210), dp(16)));
    }

    private void addSectionHeading(LinearLayout parent, int title, int description) {
        TextView heading = new TextView(this);
        heading.setText(getString(title) + "\n" + getString(description));
        heading.setTextSize(15);
        heading.setTextColor(Color.rgb(61, 54, 46));
        heading.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        heading.setPadding(0, dp(18), 0, dp(8));
        parent.addView(heading, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void styleControlTree(View view) {
        if (view instanceof Button) {
            Button button = (Button) view;
            button.setAllCaps(false);
            button.setTextSize(13);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int index = 0; index < group.getChildCount(); index++) {
                styleControlTree(group.getChildAt(index));
            }
        }
    }

    private RippleDrawable rippleBackground(int fillColor, int pressedColor,
            int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fillColor);
        shape.setCornerRadius(radius);
        return new RippleDrawable(ColorStateList.valueOf(pressedColor), shape, null);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void showChapterQuickDialog() {
        if (currentChapters.isEmpty()) {
            AlertDialog emptyDialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.quick_contents)
                    .setMessage(R.string.quick_contents_empty)
                    .setPositiveButton(R.string.quick_open_settings,
                            (dialog, which) -> openControlPanelAt(chapterSettingsTarget))
                    .setNegativeButton(android.R.string.cancel, null)
                    .create();
            showAsBottomPanel(emptyDialog);
            return;
        }
        String[] labels = new String[currentChapters.size()];
        for (int index = 0; index < currentChapters.size(); index++) {
            labels[index] = currentChapters.get(index).getTitle();
        }
        final int[] selected = {Math.max(0,
                Math.min(chapterCursor, currentChapters.size() - 1))};
        AlertDialog contentsDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.quick_contents_title)
                .setSingleChoiceItems(labels, selected[0],
                        (dialog, which) -> selected[0] = which)
                .setPositiveButton(R.string.chapter_open,
                        (dialog, which) -> openChapterAt(selected[0]))
                .setNeutralButton(R.string.quick_edit_contents,
                        (dialog, which) -> openControlPanelAt(chapterSettingsTarget))
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        showAsBottomPanel(contentsDialog);
    }

    private void showBookshelfQuickDialog() {
        if (bookshelf.isEmpty()) {
            openTextPicker();
            return;
        }
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(12), dp(4), dp(12), dp(4));
        final AlertDialog[] holder = new AlertDialog[1];
        for (int index = 0; index < bookshelf.size(); index++) {
            BookLibraryEntry entry = bookshelf.get(index);
            LinearLayout row = createBookshelfRow(entry,
                    entry.getBookId().equals(currentBookId));
            final int selected = index;
            row.setOnClickListener(view -> {
                holder[0].dismiss();
                bookshelfSpinner.setSelection(selected);
                openSelectedBook();
            });
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(compactUiForLargeText ? 104 : 76));
            rowParams.bottomMargin = dp(8);
            list.addView(row, rowParams);
        }

        Button importButton = compactButton(R.string.quick_import,
                view -> {
                    holder[0].dismiss();
                    openTextPicker();
                });
        LinearLayout.LayoutParams importParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(48));
        importParams.topMargin = dp(4);
        list.addView(importButton, importParams);

        ScrollView scrollingList = new ScrollView(this);
        scrollingList.addView(list, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        holder[0] = new AlertDialog.Builder(this)
                .setTitle(R.string.quick_library_title)
                .setView(scrollingList)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        showAsBottomPanel(holder[0]);
    }

    private LinearLayout createBookshelfRow(BookLibraryEntry entry, boolean current) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), dp(6), dp(10), dp(6));
        row.setBackground(rippleBackground(Color.rgb(248, 244, 237),
                Color.rgb(229, 219, 205), dp(16)));
        row.setContentDescription(entry.getDisplayName() + ", "
                + getString(R.string.bookshelf_quick_meta,
                entry.getAnchorOffset(), entry.getSourceBytes() / 1024.0 / 1024.0));

        TextView cover = new TextView(this);
        cover.setText(R.string.empty_state_badge);
        cover.setGravity(Gravity.CENTER);
        cover.setTextSize(12);
        cover.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        cover.setTextColor(Color.WHITE);
        int[] coverColors = {Color.rgb(91, 81, 70), Color.rgb(79, 99, 91),
                Color.rgb(103, 83, 92), Color.rgb(91, 91, 112)};
        int coverColor = coverColors[Math.floorMod(entry.getBookId().hashCode(),
                coverColors.length)];
        cover.setBackground(rippleBackground(coverColor, coverColor, dp(10)));
        row.addView(cover, new LinearLayout.LayoutParams(dp(48), dp(60)));

        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(dp(12), 0, dp(6), 0);
        TextView name = new TextView(this);
        name.setText(entry.getDisplayName());
        name.setSingleLine(true);
        name.setTextSize(compactUiForLargeText ? 11 : 15);
        name.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        name.setTextColor(Color.rgb(43, 39, 35));
        details.addView(name, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView meta = new TextView(this);
        meta.setText(getString(R.string.bookshelf_quick_meta,
                entry.getAnchorOffset(), entry.getSourceBytes() / 1024.0 / 1024.0));
        meta.setTextSize(compactUiForLargeText ? 9 : 12);
        meta.setTextColor(Color.rgb(103, 93, 81));
        details.addView(meta, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        row.addView(details, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        if (current) {
            TextView badge = new TextView(this);
            badge.setText(R.string.bookshelf_current);
            badge.setTextSize(compactUiForLargeText ? 8 : 10);
            badge.setTextColor(Color.rgb(65, 90, 78));
            badge.setPadding(dp(7), dp(4), dp(7), dp(4));
            badge.setBackground(rippleBackground(Color.rgb(226, 239, 231),
                    Color.rgb(210, 228, 216), dp(12)));
            row.addView(badge, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        return row;
    }

    private void showSearchQuickDialog() {
        EditText query = new EditText(this);
        query.setSingleLine(true);
        query.setHint(R.string.search_hint);
        query.setPadding(dp(20), dp(12), dp(20), dp(12));
        AlertDialog searchDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.quick_search_title)
                .setView(query)
                .setPositiveButton(R.string.search, (dialog, which) -> {
                    String value = query.getText().toString();
                    searchInput.setText(value);
                    searchCurrentWindow(value);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        showAsBottomPanel(searchDialog);
    }

    private void toggleQuickSpeech() {
        if (speechQueue == null
                || speechQueue.getState() == SpeechPlaybackQueue.State.STOPPED
                || speechQueue.getState() == SpeechPlaybackQueue.State.COMPLETE) {
            startSpeaking();
        } else if (speechQueue.getState() == SpeechPlaybackQueue.State.PLAYING) {
            pauseSpeaking();
        } else if (speechQueue.getState() == SpeechPlaybackQueue.State.PAUSED) {
            resumeSpeaking();
        } else {
            startSpeaking();
        }
        updateQuickActionState();
    }

    private void updateQuickActionState() {
        if (quickAutoScrollButton != null) {
            boolean scrolling = readerSurface != null && readerSurface.isAutoScrolling();
            quickAutoScrollButton.setText(compactUiForLargeText ? "" : getString(scrolling
                    ? R.string.quick_auto_pause : R.string.quick_auto_scroll));
            quickAutoScrollButton.setSelected(scrolling);
        }
        if (quickSpeechButton != null) {
            int label = R.string.quick_speech;
            if (speechQueue != null
                    && speechQueue.getState() == SpeechPlaybackQueue.State.PLAYING) {
                label = R.string.quick_speech_pause;
            } else if (speechQueue != null
                    && speechQueue.getState() == SpeechPlaybackQueue.State.PAUSED) {
                label = R.string.quick_speech_resume;
            }
            quickSpeechButton.setText(compactUiForLargeText ? "" : getString(label));
        }
        if (companionMiniBar != null && companionMiniStatus != null) {
            boolean scrolling = readerSurface != null && readerSurface.isAutoScrolling();
            boolean speechActive = speechQueue != null
                    && speechQueue.getState() != SpeechPlaybackQueue.State.STOPPED
                    && speechQueue.getState() != SpeechPlaybackQueue.State.COMPLETE;
            companionMiniBar.setVisibility(scrolling || speechActive
                    ? View.VISIBLE : View.GONE);
            if (scrolling) {
                companionMiniStatus.setText(getString(
                        R.string.companion_mini_scrolling,
                        readerSurface.getAutoScrollSpeedDpPerSecond()));
            } else if (speechActive) {
                companionMiniStatus.setText(speechQueue.getState()
                        == SpeechPlaybackQueue.State.PAUSED
                        ? R.string.companion_mini_speech_paused
                        : R.string.companion_mini_speaking);
            }
        }
    }

    private void showQuickToolsMenu() {
        String[] items = {getString(R.string.quick_menu_library),
                getString(R.string.quick_menu_reading),
                getString(R.string.quick_menu_companion),
                getString(R.string.quick_menu_cleaning),
                getString(R.string.privacy_and_about)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.quick_settings)
                .setItems(items, (dialog, which) -> {
                    if (which == 0) openControlPanelAt(librarySettingsTarget);
                    else if (which == 1) openControlPanelAt(readerSettingsTarget);
                    else if (which == 2) openControlPanelAt(companionSettingsTarget);
                    else if (which == 3) openControlPanelAt(repairSettingsTarget);
                    else showPrivacyAndAbout();
                })
                .show();
    }

    private void openControlPanelAt(View target) {
        if (controlsScroll == null) return;
        setReaderChromeVisible(true);
        controlsScroll.setVisibility(View.VISIBLE);
        if (readerHost != null) {
            readerHost.setVisibility(View.GONE);
        }
        controlsToggleButton.setText(R.string.controls_hide);
        controlsToggleButton.setContentDescription(
                getString(R.string.controls_hide_description));
        controlsScroll.post(() -> controlsScroll.smoothScrollTo(0,
                target == null ? 0 : Math.max(0, target.getTop() - dp(56))));
    }

    private void configureEdgeToEdge() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false);
        } else {
            int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
            window.getDecorView().setSystemUiVisibility(flags);
        }
        if (Build.VERSION.SDK_INT >= 29) {
            window.setStatusBarContrastEnforced(false);
            window.setNavigationBarContrastEnforced(false);
        }
    }

    @SuppressWarnings("deprecation")
    private void updateSystemBarIconAppearance() {
        boolean lightNavigationBackground = readerAppearance == null
                || readerAppearance.getTheme() != ReaderAppearance.Theme.NIGHT;
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
                int appearance = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS;
                if (lightNavigationBackground) {
                    appearance |= WindowInsetsController
                            .APPEARANCE_LIGHT_NAVIGATION_BARS;
                }
                controller.setSystemBarsAppearance(appearance, mask);
            }
            return;
        }
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= 26 && lightNavigationBackground) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    @SuppressWarnings("deprecation")
    private void applySystemBarInsets(View page) {
        page.setOnApplyWindowInsetsListener((view, insets) -> {
            int left;
            int top;
            int right;
            int bottom;
            if (Build.VERSION.SDK_INT >= 30) {
                Insets safe = insets.getInsets(WindowInsets.Type.systemBars()
                        | WindowInsets.Type.displayCutout()
                        | WindowInsets.Type.ime());
                left = safe.left;
                top = safe.top;
                right = safe.right;
                bottom = safe.bottom;
            } else {
                left = insets.getSystemWindowInsetLeft();
                top = insets.getSystemWindowInsetTop();
                right = insets.getSystemWindowInsetRight();
                bottom = insets.getSystemWindowInsetBottom();
                if (Build.VERSION.SDK_INT >= 28) {
                    DisplayCutout cutout = insets.getDisplayCutout();
                    if (cutout != null) {
                        left = Math.max(left, cutout.getSafeInsetLeft());
                        top = Math.max(top, cutout.getSafeInsetTop());
                        right = Math.max(right, cutout.getSafeInsetRight());
                        bottom = Math.max(bottom, cutout.getSafeInsetBottom());
                    }
                }
            }
            view.setPadding(left, top, right, bottom);
            return insets;
        });
        page.requestApplyInsets();
    }

    private void toggleControlPanel() {
        if (controlsScroll == null || controlsToggleButton == null) {
            return;
        }
        boolean show = controlsScroll.getVisibility() != View.VISIBLE;
        setReaderChromeVisible(true);
        controlsScroll.setVisibility(show ? View.VISIBLE : View.GONE);
        if (readerHost != null) {
            readerHost.setVisibility(show ? View.GONE : View.VISIBLE);
        }
        controlsToggleButton.setText(show
                ? R.string.controls_hide : R.string.quick_settings);
        controlsToggleButton.setContentDescription(getString(show
                ? R.string.controls_hide_description
                : R.string.quick_settings_description));
    }

    private void showPrivacyAndAbout() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.privacy_title)
                .setMessage(getString(R.string.privacy_message,
                        BuildConfig.VERSION_NAME))
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        if (keyCode != KeyEvent.KEYCODE_VOLUME_UP
                && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return super.dispatchKeyEvent(event);
        }
        ReaderNavigationSettings.VolumeKey key =
                keyCode == KeyEvent.KEYCODE_VOLUME_UP
                        ? ReaderNavigationSettings.VolumeKey.UP
                        : ReaderNavigationSettings.VolumeKey.DOWN;
        ReaderNavigationSettings.Direction direction =
                readerNavigationSettings.directionFor(key);
        View focused = getCurrentFocus();
        AccessibilityManager accessibility = (AccessibilityManager)
                getSystemService(ACCESSIBILITY_SERVICE);
        boolean touchExploration = accessibility != null
                && accessibility.isTouchExplorationEnabled();
        if (direction == ReaderNavigationSettings.Direction.NONE
                || speechQueue != null || readerSurface == null
                || readerSurface.isAutoScrolling() || currentRevision.isEmpty()
                || libraryTransitionInProgress || repairApplying
                || chapterOutlineTransitionInProgress || exportInProgress
                || readerSurface.currentTextSelection() != null
                || focused instanceof EditText || touchExploration) {
            return super.dispatchKeyEvent(event);
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN
                && event.getRepeatCount() == 0) {
            cancelAutoScrollResume();
            boolean forward = direction == ReaderNavigationSettings.Direction.FORWARD;
            boolean moved = readerSurface.navigateViewport(forward);
            if (moved) {
                status.setText(getString(forward
                                ? R.string.reader_page_forward
                                : R.string.reader_page_backward,
                        readerSurface.visibleCharacterOffset()));
            }
        }
        return true;
    }

    private void addRuleAction(LinearLayout parent, int label,
            android.view.View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(listener);
        parent.addView(button, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1));
    }

    private String[] appearanceLabels(int[] options, int labelResource) {
        String[] labels = new String[options.length];
        for (int index = 0; index < options.length; index++) {
            labels[index] = getString(labelResource, options[index]);
        }
        return labels;
    }

    private void loadReaderAppearance() {
        ReaderAppearance defaults = ReaderAppearance.defaults();
        try {
            SharedPreferences preferences = getSharedPreferences(
                    READER_APPEARANCE_PREFERENCES, MODE_PRIVATE);
            readerAppearance = new ReaderAppearance(
                    ReaderAppearance.Theme.valueOf(preferences.getString(
                            READER_THEME_KEY, defaults.getTheme().name())),
                    ReaderAppearance.FontFamily.valueOf(preferences.getString(
                            READER_FONT_KEY, defaults.getFontFamily().name())),
                    preferences.getInt(READER_TEXT_SIZE_KEY, defaults.getTextSizeSp()),
                    preferences.getInt(READER_LINE_HEIGHT_KEY,
                            defaults.getLineHeightPercent()),
                    preferences.getInt(READER_PARAGRAPH_SPACING_KEY,
                            defaults.getParagraphSpacingDp()),
                    preferences.getInt(READER_MARGIN_KEY,
                            defaults.getHorizontalMarginDp()));
        } catch (RuntimeException invalid) {
            readerAppearance = defaults;
        }
    }

    private void loadReaderDisplayPolicy() {
        ReaderDisplayPolicy defaults = ReaderDisplayPolicy.defaults();
        try {
            String encoded = getSharedPreferences(
                    READER_APPEARANCE_PREFERENCES, MODE_PRIVATE)
                    .getString(READER_DISPLAY_POLICY_KEY, defaults.toJson());
            readerDisplayPolicy = ReaderDisplayPolicy.fromJson(encoded);
        } catch (RuntimeException invalid) {
            readerDisplayPolicy = defaults;
        }
    }

    private void loadReaderNavigationSettings() {
        ReaderNavigationSettings defaults = ReaderNavigationSettings.defaults();
        try {
            String encoded = getSharedPreferences(
                    READER_APPEARANCE_PREFERENCES, MODE_PRIVATE)
                    .getString(READER_NAVIGATION_KEY, defaults.toJson());
            readerNavigationSettings = ReaderNavigationSettings.fromJson(encoded);
        } catch (RuntimeException invalid) {
            readerNavigationSettings = defaults;
        }
    }

    private void selectReaderNavigationControls() {
        readerModeSpinner.setSelection(
                readerNavigationSettings.getReadingMode().ordinal());
        readerVolumeKeySpinner.setSelection(
                readerNavigationSettings.getVolumeKeyMode().ordinal());
    }

    private void applyReaderNavigationSettings() {
        ReaderNavigationSettings previous = readerNavigationSettings;
        int before = readerSurface.visibleCharacterOffset();
        cancelAutoScrollResume();
        readerSurface.stopAutoScroll();
        ReaderNavigationSettings selected;
        String savedMessage;
        try {
            int modeIndex = readerModeSpinner.getSelectedItemPosition();
            int volumeIndex = readerVolumeKeySpinner.getSelectedItemPosition();
            if (modeIndex < 0
                    || modeIndex >= ReaderNavigationSettings.ReadingMode.values().length
                    || volumeIndex < 0
                    || volumeIndex >= ReaderNavigationSettings.VolumeKeyMode.values().length) {
                throw new IllegalArgumentException("invalid reader navigation selection");
            }
            selected = new ReaderNavigationSettings(
                    ReaderNavigationSettings.ReadingMode.values()[modeIndex],
                    ReaderNavigationSettings.VolumeKeyMode.values()[volumeIndex]);
            readerSurface.setMode(surfaceMode(selected.getReadingMode()));
            int after = readerSurface.visibleCharacterOffset();
            savedMessage = getString(R.string.reader_navigation_saved,
                    readerModeSpinner.getSelectedItem().toString(),
                    readerVolumeKeySpinner.getSelectedItem().toString(), before, after);
        } catch (RuntimeException invalid) {
            readerSurface.setMode(surfaceMode(previous.getReadingMode()));
            readerSurface.scrollToAnchor(Integer.toString(before));
            selectReaderNavigationControls();
            status.setText(R.string.reader_navigation_save_failed);
            return;
        }
        boolean saved;
        try {
            saved = getSharedPreferences(
                    READER_APPEARANCE_PREFERENCES, MODE_PRIVATE).edit()
                    .putString(READER_NAVIGATION_KEY, selected.toJson()).commit();
        } catch (RuntimeException storageFailure) {
            saved = false;
        }
        if (!saved) {
            readerSurface.setMode(surfaceMode(previous.getReadingMode()));
            readerSurface.scrollToAnchor(Integer.toString(before));
            selectReaderNavigationControls();
            status.setText(R.string.reader_navigation_save_failed);
            return;
        }
        readerNavigationSettings = selected;
        status.setText(savedMessage);
    }

    private static ReaderSurface.Mode surfaceMode(
            ReaderNavigationSettings.ReadingMode mode) {
        return mode == ReaderNavigationSettings.ReadingMode.PAGED
                ? ReaderSurface.Mode.PAGED : ReaderSurface.Mode.CONTINUOUS_SCROLL;
    }

    private void loadAutoScrollPolicy() {
        AutoScrollPolicy defaults = AutoScrollPolicy.defaults();
        try {
            String encoded = getSharedPreferences(AUTO_SCROLL_PREFERENCES, MODE_PRIVATE)
                    .getString(AUTO_SCROLL_POLICY_KEY, defaults.toJson());
            autoScrollPolicy = AutoScrollPolicy.fromJson(encoded);
        } catch (RuntimeException invalid) {
            autoScrollPolicy = defaults;
        }
    }

    private void loadSpeechSettings() {
        SpeechSettings defaults = SpeechSettings.defaults();
        try {
            String encoded = getSharedPreferences(SPEECH_SETTINGS_PREFERENCES, MODE_PRIVATE)
                    .getString(SPEECH_SETTINGS_KEY, defaults.toJson());
            speechSettings = SpeechSettings.fromJson(encoded);
        } catch (RuntimeException invalid) {
            speechSettings = defaults;
        }
    }

    private void selectSpeechSettingsControls() {
        if (speechRateSpinner != null) {
            speechRateSpinner.setSelection(optionIndex(
                    SPEECH_PERCENT_OPTIONS, speechSettings.getRatePercent()));
        }
        if (speechPitchSpinner != null) {
            speechPitchSpinner.setSelection(optionIndex(
                    SPEECH_PERCENT_OPTIONS, speechSettings.getPitchPercent()));
        }
        if (speechVoiceSpinner != null) {
            int selected = 0;
            String voiceId = speechSettings.getVoiceId();
            for (int index = 0; voiceId != null && index < speechVoiceOptions.size(); index++) {
                if (voiceId.equals(speechVoiceOptions.get(index).id)) {
                    selected = index + 1;
                    break;
                }
            }
            speechVoiceSpinner.setSelection(selected);
        }
    }

    private void refreshSpeechVoices(boolean announce) {
        if (speech == null || speechVoiceSpinner == null) {
            return;
        }
        TextToSpeechPort.Capabilities capabilities = speech.capabilities();
        speechVoiceOptions.clear();
        List<String> labels = new ArrayList<>();
        TextToSpeechPort.VoiceInfo defaultVoice = capabilities.defaultVoice;
        labels.add(defaultVoice == null
                ? getString(R.string.speech_voice_system_default)
                : getString(defaultVoice.networkRequired
                        ? R.string.speech_voice_system_default_network
                        : R.string.speech_voice_system_default_offline));
        if (capabilities.available) {
            speechVoiceOptions.addAll(capabilities.voices);
            for (TextToSpeechPort.VoiceInfo voice : speechVoiceOptions) {
                String locale = voice.localeTag.isEmpty() ? "und" : voice.localeTag;
                labels.add(getString(R.string.speech_voice_entry, locale,
                        summarizeVoiceId(voice.id), getString(voice.networkRequired
                                ? R.string.speech_voice_network
                                : R.string.speech_voice_offline)));
            }
        }
        speechVoiceSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels));
        String configuredVoice = speechSettings.getVoiceId();
        selectSpeechSettingsControls();
        boolean missing = configuredVoice != null
                && speechVoiceSpinner.getSelectedItemPosition() == 0;
        if (!announce) {
            return;
        }
        if (!capabilities.available) {
            status.setText(R.string.speech_voice_engine_not_ready);
        } else if (missing) {
            status.setText(R.string.speech_voice_configured_missing);
        } else {
            status.setText(getString(R.string.speech_voice_refreshed,
                    speechVoiceOptions.size()));
        }
    }

    private void applySpeechSettings() {
        SpeechSettings previous = speechSettings;
        SpeechSettings selected;
        String savedMessage;
        try {
            int voiceIndex = speechVoiceSpinner.getSelectedItemPosition();
            if (voiceIndex < 0 || voiceIndex > speechVoiceOptions.size()) {
                throw new IllegalArgumentException("invalid speech voice selection");
            }
            TextToSpeechPort.VoiceInfo voice = voiceIndex == 0
                    ? null : speechVoiceOptions.get(voiceIndex - 1);
            selected = new SpeechSettings(voice == null ? null : voice.id,
                    selectedOption(speechRateSpinner, SPEECH_PERCENT_OPTIONS),
                    selectedOption(speechPitchSpinner, SPEECH_PERCENT_OPTIONS));
            TextToSpeechPort.VoiceInfo defaultVoice = speech.capabilities().defaultVoice;
            String voiceLabel = voice == null
                    ? getString(R.string.speech_voice_system_default)
                    : summarizeVoiceId(voice.id);
            boolean networkRequired = voice != null
                    ? voice.networkRequired
                    : defaultVoice != null && defaultVoice.networkRequired;
            int message = networkRequired
                    ? R.string.speech_settings_saved_network
                    : R.string.speech_settings_saved;
            savedMessage = getString(message, voiceLabel, selected.getRatePercent(),
                    selected.getPitchPercent(), speechQueue != null
                            ? getString(R.string.speech_settings_stopped_suffix) : "");
            boolean saved = getSharedPreferences(SPEECH_SETTINGS_PREFERENCES, MODE_PRIVATE)
                    .edit().putString(SPEECH_SETTINGS_KEY, selected.toJson()).commit();
            if (!saved) {
                throw new IllegalStateException("speech settings were not persisted");
            }
        } catch (RuntimeException invalid) {
            speechSettings = previous;
            selectSpeechSettingsControls();
            status.setText(R.string.speech_settings_save_failed);
            return;
        }
        boolean stoppedActiveSpeech = speechQueue != null;
        speechSettings = selected;
        if (stoppedActiveSpeech) {
            stopSpeechPlayback(false);
        }
        status.setText(savedMessage);
    }

    private static String summarizeVoiceId(String voiceId) {
        if (voiceId.length() <= 72) {
            return voiceId;
        }
        int end = 72;
        if (Character.isHighSurrogate(voiceId.charAt(end - 1))) {
            end--;
        }
        return voiceId.substring(0, end) + "…";
    }

    private void loadAutoScrollCompanionSettings() {
        AutoScrollCompanionSettings defaults = AutoScrollCompanionSettings.defaults();
        try {
            String encoded = getSharedPreferences(AUTO_SCROLL_PREFERENCES, MODE_PRIVATE)
                    .getString(AUTO_SCROLL_COMPANION_KEY, defaults.toJson());
            autoScrollCompanionSettings =
                    AutoScrollCompanionSettings.fromJson(encoded);
        } catch (RuntimeException invalid) {
            autoScrollCompanionSettings = defaults;
        }
        autoScrollResumeSession.setSettings(autoScrollCompanionSettings);
    }

    private void selectAutoScrollCompanionControls() {
        autoScrollResumeDelaySpinner.setSelection(optionIndex(
                AUTO_SCROLL_RESUME_DELAY_OPTIONS,
                autoScrollCompanionSettings.getResumeDelaySeconds()));
        autoScrollKeepScreenOnCheckBox.setChecked(
                autoScrollCompanionSettings.isKeepScreenOn());
    }

    private void applyAutoScrollCompanionSettings() {
        AutoScrollCompanionSettings previous = autoScrollCompanionSettings;
        cancelAutoScrollResume();
        try {
            AutoScrollCompanionSettings selected = new AutoScrollCompanionSettings(
                    selectedOption(autoScrollResumeDelaySpinner,
                            AUTO_SCROLL_RESUME_DELAY_OPTIONS),
                    autoScrollKeepScreenOnCheckBox.isChecked());
            boolean saved = getSharedPreferences(AUTO_SCROLL_PREFERENCES, MODE_PRIVATE)
                    .edit().putString(AUTO_SCROLL_COMPANION_KEY, selected.toJson()).commit();
            if (!saved) {
                restoreAutoScrollCompanionSettings(previous);
                status.setText(R.string.auto_scroll_companion_save_failed);
                return;
            }
            autoScrollCompanionSettings = selected;
            autoScrollResumeSession.setSettings(selected);
            readerSurface.setKeepScreenOn(readerSurface.isAutoScrolling()
                    && selected.isKeepScreenOn());
            status.setText(getString(R.string.auto_scroll_companion_saved,
                    resumeDelayLabel(selected.getResumeDelaySeconds()),
                    selected.isKeepScreenOn()
                            ? getString(R.string.setting_on) : getString(R.string.setting_off)));
        } catch (RuntimeException invalid) {
            restoreAutoScrollCompanionSettings(previous);
            status.setText(R.string.auto_scroll_companion_save_failed);
        }
    }

    private void restoreAutoScrollCompanionSettings(
            AutoScrollCompanionSettings settings) {
        autoScrollCompanionSettings = settings;
        autoScrollResumeSession.setSettings(settings);
        selectAutoScrollCompanionControls();
        readerSurface.setKeepScreenOn(readerSurface.isAutoScrolling()
                && settings.isKeepScreenOn());
    }

    private String resumeDelayLabel(int seconds) {
        return seconds == 0 ? getString(R.string.setting_off)
                : getString(R.string.auto_scroll_resume_seconds, seconds);
    }

    private void updateAutoScrollSpeedStatus(int speedDpPerSecond) {
        if (autoScrollSpeedStatus != null) {
            autoScrollSpeedStatus.setText(getString(
                    R.string.auto_scroll_speed_value, speedDpPerSecond));
        }
    }

    private void persistAutoScrollSpeed(int speedDpPerSecond) {
        AutoScrollPolicy previous = autoScrollPolicy;
        try {
            AutoScrollPolicy selected = new AutoScrollPolicy(speedDpPerSecond);
            boolean saved = getSharedPreferences(AUTO_SCROLL_PREFERENCES, MODE_PRIVATE)
                    .edit().putString(AUTO_SCROLL_POLICY_KEY, selected.toJson()).commit();
            if (!saved) {
                restoreAutoScrollSpeed(previous);
                status.setText(R.string.auto_scroll_speed_save_failed);
                return;
            }
            autoScrollPolicy = selected;
            readerSurface.setAutoScrollSpeedDpPerSecond(speedDpPerSecond);
            updateAutoScrollSpeedStatus(speedDpPerSecond);
            status.setText(getString(R.string.auto_scroll_speed_saved, speedDpPerSecond));
        } catch (RuntimeException invalid) {
            restoreAutoScrollSpeed(previous);
            status.setText(R.string.auto_scroll_speed_save_failed);
        }
    }

    private void restoreAutoScrollSpeed(AutoScrollPolicy policy) {
        autoScrollPolicy = policy;
        readerSurface.setAutoScrollSpeedDpPerSecond(policy.getSpeedDpPerSecond());
        autoScrollSpeedSeekBar.setProgress(policy.getSpeedDpPerSecond());
        updateAutoScrollSpeedStatus(policy.getSpeedDpPerSecond());
    }

    private void selectReaderAppearanceControls() {
        readerThemeSpinner.setSelection(readerAppearance.getTheme().ordinal());
        readerFontSpinner.setSelection(readerAppearance.getFontFamily().ordinal());
        readerTextSizeSpinner.setSelection(optionIndex(
                READER_TEXT_SIZE_OPTIONS, readerAppearance.getTextSizeSp()));
        readerLineHeightSpinner.setSelection(optionIndex(
                READER_LINE_HEIGHT_OPTIONS, readerAppearance.getLineHeightPercent()));
        readerParagraphSpacingSpinner.setSelection(optionIndex(
                READER_PARAGRAPH_SPACING_OPTIONS,
                readerAppearance.getParagraphSpacingDp()));
        readerMarginSpinner.setSelection(optionIndex(
                READER_MARGIN_OPTIONS, readerAppearance.getHorizontalMarginDp()));
        readerOrientationSpinner.setSelection(
                readerDisplayPolicy.getOrientation().ordinal());
    }

    private void applyReaderAppearance() {
        ReaderAppearance previous = readerAppearance;
        ReaderDisplayPolicy previousDisplayPolicy = readerDisplayPolicy;
        String savedMessage;
        try {
            int themeIndex = readerThemeSpinner.getSelectedItemPosition();
            if (themeIndex < 0 || themeIndex >= ReaderAppearance.Theme.values().length) {
                throw new IllegalArgumentException("invalid theme selection");
            }
            int fontIndex = readerFontSpinner.getSelectedItemPosition();
            if (fontIndex < 0 || fontIndex >= ReaderAppearance.FontFamily.values().length) {
                throw new IllegalArgumentException("invalid font selection");
            }
            ReaderAppearance selected = new ReaderAppearance(
                    ReaderAppearance.Theme.values()[themeIndex],
                    ReaderAppearance.FontFamily.values()[fontIndex],
                    selectedOption(readerTextSizeSpinner, READER_TEXT_SIZE_OPTIONS),
                    selectedOption(readerLineHeightSpinner, READER_LINE_HEIGHT_OPTIONS),
                    selectedOption(readerParagraphSpacingSpinner,
                            READER_PARAGRAPH_SPACING_OPTIONS),
                    selectedOption(readerMarginSpinner, READER_MARGIN_OPTIONS));
            int orientationIndex = readerOrientationSpinner.getSelectedItemPosition();
            if (orientationIndex < 0
                    || orientationIndex >= ReaderDisplayPolicy.Orientation.values().length) {
                throw new IllegalArgumentException("invalid reader orientation selection");
            }
            ReaderDisplayPolicy selectedDisplayPolicy = new ReaderDisplayPolicy(
                    ReaderDisplayPolicy.Orientation.values()[orientationIndex]);
            if (selectedDisplayPolicy.getOrientation()
                    != previousDisplayPolicy.getOrientation()) {
                BookProgressSnapshot snapshot = captureBookProgress();
                if (snapshot != null && !recordPendingBookProgressSynchronously(snapshot)) {
                    status.setText(R.string.reader_appearance_save_failed);
                    selectReaderAppearanceControls();
                    return;
                }
            }
            int before = readerSurface.visibleCharacterOffset();
            readerSurface.applyTypography(selected.toTypographyJson());
            int after = readerSurface.visibleCharacterOffset();
            savedMessage = getString(R.string.reader_appearance_saved,
                    readerThemeSpinner.getSelectedItem().toString(),
                    readerFontSpinner.getSelectedItem().toString(),
                    selected.getTextSizeSp(), selected.getLineHeightPercent(),
                    selected.getParagraphSpacingDp(), selected.getHorizontalMarginDp(),
                    readerOrientationSpinner.getSelectedItem().toString(), before, after);
            boolean saved = getSharedPreferences(
                    READER_APPEARANCE_PREFERENCES, MODE_PRIVATE).edit()
                    .putString(READER_THEME_KEY, selected.getTheme().name())
                    .putString(READER_FONT_KEY, selected.getFontFamily().name())
                    .putInt(READER_TEXT_SIZE_KEY, selected.getTextSizeSp())
                    .putInt(READER_LINE_HEIGHT_KEY, selected.getLineHeightPercent())
                    .putInt(READER_PARAGRAPH_SPACING_KEY,
                            selected.getParagraphSpacingDp())
                    .putInt(READER_MARGIN_KEY, selected.getHorizontalMarginDp())
                    .putString(READER_DISPLAY_POLICY_KEY,
                            selectedDisplayPolicy.toJson())
                    .commit();
            if (!saved) {
                readerSurface.applyTypography(previous.toTypographyJson());
                status.setText(R.string.reader_appearance_save_failed);
                selectReaderAppearanceControls();
                return;
            }
            readerAppearance = selected;
            readerDisplayPolicy = selectedDisplayPolicy;
            updateSystemBarIconAppearance();
        } catch (RuntimeException invalid) {
            readerSurface.applyTypography(previous.toTypographyJson());
            readerAppearance = previous;
            readerDisplayPolicy = previousDisplayPolicy;
            status.setText(R.string.reader_appearance_save_failed);
            selectReaderAppearanceControls();
            return;
        }
        status.setText(savedMessage);
        try {
            applyRequestedReaderOrientation();
        } catch (RuntimeException platformFailure) {
            status.setText(R.string.reader_orientation_apply_failed);
        }
    }

    private boolean recordPendingBookProgressSynchronously(
            BookProgressSnapshot snapshot) {
        return getSharedPreferences(BOOK_PROGRESS_PREFERENCES, MODE_PRIVATE).edit()
                .putString(BOOK_PROGRESS_ID_KEY, snapshot.bookId)
                .putString(BOOK_PROGRESS_REVISION_KEY, snapshot.activeRevision)
                .putInt(BOOK_PROGRESS_ANCHOR_KEY, snapshot.anchorOffset)
                .commit();
    }

    private void applyRequestedReaderOrientation() {
        int requested;
        switch (readerDisplayPolicy.getOrientation()) {
            case PORTRAIT:
                requested = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                break;
            case LANDSCAPE:
                requested = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                break;
            case FOLLOW_SYSTEM:
            default:
                requested = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
                break;
        }
        if (getRequestedOrientation() != requested) {
            setRequestedOrientation(requested);
        }
    }

    private static int selectedOption(Spinner spinner, int[] options) {
        int selected = spinner.getSelectedItemPosition();
        if (selected < 0 || selected >= options.length) {
            throw new IllegalArgumentException("invalid reader option selection");
        }
        return options[selected];
    }

    private static int optionIndex(int[] options, int value) {
        for (int index = 0; index < options.length; index++) {
            if (options[index] == value) {
                return index;
            }
        }
        return 0;
    }

    private void openTextPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, OPEN_TEXT_REQUEST);
    }

    private void openRulePackImport() {
        if (!ruleTransferReady()) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        startActivityForResult(intent, IMPORT_RULE_PACK_REQUEST);
    }

    private void createRulePackExport() {
        createRulePackExport("jingdu-rules.jdrp", false);
    }

    private void createRulePackExport(String displayName, boolean recovering) {
        if (!ruleTransferReady()) {
            return;
        }
        if (!canStartExport(recovering)) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, displayName);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(intent, EXPORT_RULE_PACK_REQUEST);
    }

    private void createCleanTextExport() {
        if (currentRevision.isEmpty()) {
            status.setText(R.string.repair_need_book);
            return;
        }
        createCleanTextExport("jingdu-cleaned-" + currentRevision.substring(0, 8) + ".txt",
                false);
    }

    private void createCleanTextExport(String displayName, boolean recovering) {
        if (repairApplying || chapterOutlineTransitionInProgress) {
            status.setText(R.string.repair_busy);
            return;
        }
        if (activeTextPath == null || currentRevision.isEmpty()) {
            status.setText(R.string.repair_need_book);
            return;
        }
        if (!canStartExport(recovering)) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, displayName);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        startActivityForResult(intent, EXPORT_CLEAN_TEXT_REQUEST);
    }

    private boolean ruleTransferReady() {
        if (repairApplying) {
            status.setText(R.string.repair_busy);
            return false;
        }
        if (baseOriginalPath == null) {
            status.setText(R.string.repair_need_book);
            return false;
        }
        if (repairRulesPersistedGeneration != repairRulesGeneration) {
            status.setText(R.string.repair_rules_not_saved);
            return false;
        }
        return true;
    }

    private boolean canStartExport(boolean recovering) {
        if (exportInProgress) {
            status.setText(R.string.repair_export_in_progress);
            return false;
        }
        if (recovering) {
            return true;
        }
        try {
            if (readExportPending() != null) {
                status.setText(R.string.repair_export_resolve_pending_first);
                refreshExportRecoveryStatus();
                return false;
            }
            return true;
        } catch (Exception error) {
            status.setText(R.string.repair_export_resolve_corrupt_first);
            refreshExportRecoveryStatus();
            return false;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if (requestCode == OPEN_TEXT_REQUEST) {
            if ((data.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) != 0) {
                try {
                    getContentResolver().takePersistableUriPermission(
                            uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                } catch (SecurityException ignored) {
                    // Some providers grant only transient access; the private copy preserves the book.
                }
            }
            importUri(uri);
        } else if (requestCode == IMPORT_RULE_PACK_REQUEST) {
            importRulePack(uri);
        } else if (requestCode == EXPORT_RULE_PACK_REQUEST) {
            exportRulePack(uri);
        } else if (requestCode == EXPORT_CLEAN_TEXT_REQUEST) {
            exportCleanText(uri);
        }
    }

    private void importUri(Uri uri) {
        if (repairApplying) {
            status.setText(R.string.repair_busy);
            return;
        }
        stopCompanionMode();
        cancelCompanionSleepTimer(false);
        repairRulesGeneration++;
        repairRulesPersistedGeneration = -1;
        resetOccurrenceSelection();
        invalidatePendingRepair();
        status.setText(R.string.importing);
        importEncodingStatus.setText(R.string.import_encoding_status_detecting);
        importEncodingPreviousButton.setEnabled(false);
        importEncodingNextButton.setEnabled(false);
        libraryTransitionInProgress = true;
        chapterOutlineRequest++;
        chapterOutlineTransitionInProgress = false;
        viewportRequest++;
        String importedDisplayName = queryDisplayName(uri,
                getString(R.string.bookshelf_unnamed));
        ImportEncodingPreference encodingPreference = selectedImportEncodingPreference();
        long bookshelfOperation = ++bookshelfRequest;
        worker.execute(() -> {
            Path sourceCopy = null;
            try {
                File bookDirectory = new File(getFilesDir(), "books");
                if (!bookDirectory.isDirectory() && !bookDirectory.mkdirs()) {
                    throw new IllegalStateException("无法创建私有书籍目录");
                }
                finishPendingBookDeletion(bookDirectory.toPath());
                sourceCopy = new File(getCacheDir(), "source-" + System.nanoTime() + ".txt").toPath();
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    if (input == null) {
                        throw new IllegalStateException("文件提供方没有返回可读数据");
                    }
                    Files.copy(input, sourceCopy);
                }

                Path normalized = new File(bookDirectory,
                        "book-" + System.currentTimeMillis() + ".utf8.txt").toPath();
                ImportResult result = new TextImportPipeline(new EncodingDetector())
                        .importFile(sourceCopy, normalized, encodingPreference,
                                new ImportProgressListener() {
                            @Override
                            public void onFirstWindowReady(Path file, long characters, long elapsedNanos) {
                                runOnUiThread(() -> {
                                    if (bookshelfOperation == bookshelfRequest) {
                                        status.setText(getString(R.string.first_window_ready,
                                                elapsedNanos / 1_000_000.0));
                                    }
                                });
                            }
                        });
                if (bookshelfOperation != bookshelfRequest) {
                    Files.deleteIfExists(normalized);
                    return;
                }
                Path bookDirectoryPath = bookDirectory.toPath();
                List<BookLibraryEntry> existingBooks = loadBookLibrary(bookDirectoryPath);
                BookLibraryEntry duplicate = bookLibraryStore.find(
                        existingBooks, result.getSourceSha256());
                if (duplicate != null && !isBookEntryRestorable(
                        bookDirectoryPath, duplicate)) {
                    List<BookLibraryEntry> repairedCatalog = new ArrayList<>(existingBooks);
                    repairedCatalog.removeIf(entry -> entry.getBookId().equals(
                            result.getSourceSha256()));
                    existingBooks = repairedCatalog;
                    duplicate = null;
                }
                if (duplicate != null && result.getOutputSha256().equals(
                        DiskDocumentIndexBuilder.computeSha256(resolveBookFile(
                                bookDirectoryPath, duplicate.getBaseFileName(),
                                "书架原文件无效或已丢失")))) {
                    Files.deleteIfExists(normalized);
                    boolean wasRemoved = !duplicate.isShelved();
                    BookLibraryEntry reopened = duplicate.withShelved(
                            true, nextOpenedAt(duplicate)).withProgress(
                                    duplicate.getAnchorOffset(), nextOpenedAt(duplicate));
                    List<BookLibraryEntry> updated = bookLibraryStore.upsert(
                            existingBooks, reopened);
                    BookEncodingProfile previousEncodingProfile =
                            replaceBookEncodingProfile(bookDirectoryPath, result);
                    try {
                        saveBookLibrary(bookDirectoryPath, updated);
                    } catch (Exception error) {
                        rollbackBookEncodingProfile(bookDirectoryPath, result,
                                previousEncodingProfile, error);
                        throw error;
                    }
                    try {
                        removeBookEncodingProfiles(bookDirectoryPath,
                                reopened.getBookId(), reopened.getBaseRevision());
                    } catch (Exception ignored) {
                        // Revision binding makes stale diagnostic profiles inert.
                    }
                    restoreBookEntry(bookDirectoryPath, reopened,
                            wasRemoved ? R.string.bookshelf_readded
                                    : R.string.bookshelf_duplicate_opened,
                            bookshelfOperation);
                    showImportEncodingSummary(result, bookshelfOperation);
                    return;
                }
                if (duplicate != null) {
                    requestRedecodeConfirmation(bookDirectoryPath,
                            new ArrayList<>(existingBooks), duplicate, normalized, result,
                            bookshelfOperation);
                    return;
                }
                commitImportedBook(bookDirectoryPath, existingBooks, null, normalized,
                        result, importedDisplayName, bookshelfOperation);
            } catch (Exception error) {
                String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
                runOnUiThread(() -> {
                    if (bookshelfOperation == bookshelfRequest) {
                        libraryTransitionInProgress = false;
                        status.setText(getString(R.string.import_failed, message));
                        importEncodingStatus.setText(
                                R.string.import_encoding_status_failed);
                        refreshEncodingJumpButton();
                    }
                });
            } finally {
                if (sourceCopy != null) {
                    try {
                        Files.deleteIfExists(sourceCopy);
                    } catch (Exception ignored) {
                        // Cache cleanup can be retried by the platform later.
                    }
                }
            }
        });
    }

    private void requestRedecodeConfirmation(Path bookDirectory,
            List<BookLibraryEntry> existingBooks, BookLibraryEntry existingBook,
            Path normalized, ImportResult result, long bookshelfOperation) {
        runOnUiThread(() -> {
            if (bookshelfOperation != bookshelfRequest) {
                discardPendingImport(normalized);
                return;
            }
            status.setText(getString(R.string.import_redecode_confirmation_status,
                    importEncodingSummary(result)));
            importEncodingStatus.setText(importEncodingSummary(result));
            new AlertDialog.Builder(this)
                    .setTitle(R.string.import_redecode_title)
                    .setMessage(getString(R.string.import_redecode_message,
                            existingBook.getDisplayName(), importEncodingSummary(result)))
                    .setPositiveButton(R.string.import_redecode_confirm,
                            (dialog, which) -> worker.execute(() -> {
                                try {
                                    commitImportedBook(bookDirectory, existingBooks,
                                            existingBook, normalized, result,
                                            existingBook.getDisplayName(), bookshelfOperation);
                                } catch (Exception error) {
                                    discardPendingImport(normalized);
                                    showImportFailure(error, bookshelfOperation);
                                }
                            }))
                    .setNegativeButton(android.R.string.cancel,
                            (dialog, which) -> cancelPendingImport(
                                    normalized, bookshelfOperation))
                    .setOnCancelListener(dialog -> cancelPendingImport(
                            normalized, bookshelfOperation))
                    .show();
        });
    }

    private void commitImportedBook(Path bookDirectory,
            List<BookLibraryEntry> existingBooks, BookLibraryEntry existingBook,
            Path normalized, ImportResult result, String importedDisplayName,
            long bookshelfOperation) throws Exception {
        if (bookshelfOperation != bookshelfRequest) {
            Files.deleteIfExists(normalized);
            return;
        }
        boolean redecode = existingBook != null;
        long importedAt = redecode ? existingBook.getImportedAtEpochMillis()
                : Math.max(1L, System.currentTimeMillis());
        long openedAt = redecode ? nextOpenedAt(existingBook) : importedAt;
        BookLibraryEntry importedBook = new BookLibraryEntry(
                result.getSourceSha256(), importedDisplayName,
                normalized.getFileName().toString(), result.getOutputSha256(),
                normalized.getFileName().toString(), result.getOutputSha256(), "",
                result.getEncoding().getCharset().name(), result.getSourceBytes(),
                importedAt, openedAt, 0, true);
        List<BookLibraryEntry> updatedBooks = bookLibraryStore.upsert(
                existingBooks, importedBook);
        List<RepairRule> loadedRules = loadRepairRules(result.getSourceSha256());
        String preview = readPreview(normalized);
        DocumentIndex windowIndex = DocumentIndex.build(
                preview, result.getOutputSha256());
        List<BookBookmark> previousBookmarks = redecode
                ? loadBookBookmarks(bookDirectory, importedBook.getBookId())
                : Collections.emptyList();
        BookEncodingProfile previousEncodingProfile =
                replaceBookEncodingProfile(bookDirectory, result);
        try {
            if (redecode) {
                bookBookmarkStore.save(bookmarkPath(bookDirectory,
                        importedBook.getBookId()), Collections.emptyList());
            }
            saveBookLibrary(bookDirectory, updatedBooks);
        } catch (Exception error) {
            rollbackBookEncodingProfile(bookDirectory, result,
                    previousEncodingProfile, error);
            if (redecode) {
                try {
                    bookBookmarkStore.save(bookmarkPath(bookDirectory,
                            importedBook.getBookId()), previousBookmarks);
                } catch (Exception rollbackError) {
                    error.addSuppressed(rollbackError);
                }
            }
            throw error;
        }
        if (redecode) {
            try {
                removeBookChapterOutlines(bookDirectory, importedBook.getBookId());
            } catch (Exception ignored) {
                // Versioned stale outlines cannot attach to the new base revision.
            }
        }
        try {
            removeBookEncodingProfiles(bookDirectory, importedBook.getBookId(),
                    importedBook.getBaseRevision());
        } catch (Exception ignored) {
            // A stale diagnostic sidecar cannot attach to a different base revision.
        }
        runOnUiThread(() -> {
                    if (bookshelfOperation != bookshelfRequest) {
                        return;
                    }
                    currentText = preview;
                    currentRevision = result.getOutputSha256();
                    currentWindowIndex = windowIndex;
                    currentDiskIndex = null;
                    currentWindowStart = 0;
                    baseOriginalPath = normalized;
                    baseRevision = result.getOutputSha256();
                    activeTextPath = normalized;
                    activeProjectionPath = null;
                    currentBookId = importedBook.getBookId();
                    currentBookEncodingProfile = encodingProfileFrom(result);
                    encodingReplacementCursor = -1;
                    pendingRepair = null;
                    replaceRepairRules(loadedRules);
                    markLoadedRepairRulesPersisted();
                    resetOccurrenceSelection();
                    chapterCursor = -1;
                    readerSurface.setDocumentText(preview, result.getOutputSha256());
                    importEncodingStatus.setText(importEncodingSummary(result));
                    refreshBookmarks(Collections.emptyList());
                    currentChapterOutline = null;
                    refreshChapters(windowIndex.getChapters(), 0);
                    status.setText(getString(redecode
                                    ? R.string.import_redecode_complete_indexing
                                    : R.string.import_complete_indexing,
                            result.getSourceBytes() / 1024.0 / 1024.0,
                            importEncodingSummary(result),
                            result.getTotalNanos() / 1_000_000.0,
                            windowIndex.getChapters().size()));
                    refreshBookshelf(updatedBooks, importedBook.getBookId());
                    libraryTransitionInProgress = false;
                    refreshEncodingJumpButton();
                    requestArtifactPrune();
                });
        try {
            writeBookState(bookDirectory, new BookState(
                    normalized.getFileName().toString(), result.getOutputSha256(),
                    normalized.getFileName().toString(), result.getOutputSha256(), ""));
        } catch (Exception ignored) {
            // The atomic library is authoritative; the legacy single-book snapshot is best effort.
        }
        buildOrResumeDiskIndex(bookDirectory, normalized, result.getOutputSha256());
    }

    private void cancelPendingImport(Path normalized, long bookshelfOperation) {
        discardPendingImport(normalized);
        if (bookshelfOperation == bookshelfRequest) {
            libraryTransitionInProgress = false;
            status.setText(R.string.import_redecode_cancelled);
            refreshEncodingJumpButton();
        }
    }

    private void discardPendingImport(Path normalized) {
        worker.execute(() -> {
            try {
                Files.deleteIfExists(normalized);
            } catch (Exception ignored) {
                // Generated-asset pruning can remove an abandoned normalized copy later.
            }
        });
    }

    private void showImportFailure(Exception error, long bookshelfOperation) {
        String message = error.getMessage() == null
                ? error.getClass().getSimpleName() : error.getMessage();
        runOnUiThread(() -> {
            if (bookshelfOperation == bookshelfRequest) {
                libraryTransitionInProgress = false;
                status.setText(getString(R.string.import_failed, message));
                importEncodingStatus.setText(R.string.import_encoding_status_failed);
                refreshEncodingJumpButton();
            }
        });
    }

    private void showImportEncodingSummary(ImportResult result,
            long bookshelfOperation) {
        runOnUiThread(() -> {
            if (bookshelfOperation == bookshelfRequest) {
                importEncodingStatus.setText(importEncodingSummary(result));
            }
        });
    }

    private void refreshEncodingJumpButton() {
        if (importEncodingPreviousButton == null
                || importEncodingNextButton == null) {
            return;
        }
        BookEncodingProfile profile = currentBookEncodingProfile;
        boolean available = !libraryTransitionInProgress
                && profile != null
                && !profile.getDecodingReplacements().isEmpty()
                && profile.getBookId().equals(currentBookId)
                && profile.getBaseRevision().equals(baseRevision);
        int size = available ? profile.getDecodingReplacements().size() : 0;
        importEncodingPreviousButton.setEnabled(available
                && encodingReplacementCursor > 0);
        importEncodingNextButton.setEnabled(available
                && encodingReplacementCursor + 1 < size);
    }

    private void navigateEncodingReplacement(boolean forward) {
        if (!libraryActionReady()) {
            return;
        }
        BookEncodingProfile profile = currentBookEncodingProfile;
        if (profile == null || profile.getDecodingReplacements().isEmpty()
                || !profile.getBookId().equals(currentBookId)
                || !profile.getBaseRevision().equals(baseRevision)) {
            refreshEncodingJumpButton();
            return;
        }
        int locationCount = profile.getDecodingReplacements().size();
        int targetIndex = forward ? encodingReplacementCursor + 1
                : encodingReplacementCursor - 1;
        if (targetIndex < 0 || targetIndex >= locationCount) {
            status.setText(forward ? R.string.import_encoding_navigation_last
                    : R.string.import_encoding_navigation_first);
            refreshEncodingJumpButton();
            return;
        }
        DecodingReplacement location =
                profile.getDecodingReplacements().get(targetIndex);
        String bookId = currentBookId;
        String revision = currentRevision;
        Path projection = activeProjectionPath;
        DiskDocumentIndex diskIndex = currentDiskIndex;
        int capturedWindowStart = currentWindowStart;
        int capturedWindowLength = currentText.length();
        long originalAnchor = location.getNormalizedCharacterOffset();
        long request = ++viewportRequest;
        stopSpeechPlayback(false);
        stopAutoScrollAndCancelResume();
        status.setText(R.string.import_encoding_jump_loading);
        worker.execute(() -> {
            try {
                long activeAnchor = originalAnchor;
                if (projection != null) {
                    try (DiskRepairProjection mapping =
                            DiskRepairProjection.open(projection)) {
                        activeAnchor = mapping.mapOriginalToDerived(activeAnchor);
                    }
                }
                int target = (int) Math.min((long) Integer.MAX_VALUE,
                        activeAnchor);
                if (diskIndex == null) {
                    boolean inWindow = target >= capturedWindowStart
                            && (long) target < (long) capturedWindowStart
                                    + capturedWindowLength;
                    runOnUiThread(() -> {
                        if (request != viewportRequest
                                || !bookId.equals(currentBookId)
                                || !revision.equals(currentRevision)
                                || currentBookEncodingProfile != profile) {
                            return;
                        }
                        if (!inWindow) {
                            status.setText(
                                    R.string.import_encoding_jump_index_pending);
                            return;
                        }
                        readerSurface.scrollToAnchor(Integer.toString(target));
                        encodingReplacementCursor = targetIndex;
                        refreshEncodingJumpButton();
                        status.setText(getString(
                                R.string.import_encoding_jump_complete,
                                targetIndex + 1, locationCount,
                                profile.getDecodingReplacementCount(),
                                location.getSourceByteOffset(),
                                originalAnchor, target));
                    });
                    return;
                }
                int boundedTarget = (int) Math.min((long) target,
                        Math.min((long) Integer.MAX_VALUE,
                                diskIndex.getCharacterCount()));
                IndexedTextWindow window = diskIndex.readWindowAround(
                        boundedTarget, INDEX_WINDOW_CHARACTERS);
                DocumentIndex windowIndex = DocumentIndex.build(
                        window.getText(), revision);
                runOnUiThread(() -> {
                    if (request != viewportRequest
                            || !bookId.equals(currentBookId)
                            || !revision.equals(currentRevision)
                            || diskIndex != currentDiskIndex
                            || currentBookEncodingProfile != profile) {
                        return;
                    }
                    currentText = window.getText();
                    currentWindowStart = window.getStartOffset();
                    currentWindowIndex = windowIndex;
                    readerSurface.setDocumentWindow(
                            currentText, revision, currentWindowStart);
                    readerSurface.scrollToAnchor(
                            Integer.toString(boundedTarget));
                    encodingReplacementCursor = targetIndex;
                    refreshEncodingJumpButton();
                    status.setText(getString(
                            R.string.import_encoding_jump_complete,
                            targetIndex + 1, locationCount,
                            profile.getDecodingReplacementCount(),
                            location.getSourceByteOffset(),
                            originalAnchor, boundedTarget));
                });
            } catch (Exception error) {
                String message = safeMessage(error);
                runOnUiThread(() -> {
                    if (request == viewportRequest
                            && bookId.equals(currentBookId)
                            && revision.equals(currentRevision)) {
                        status.setText(getString(
                                R.string.import_encoding_jump_failed, message));
                    }
                });
            }
        });
    }

    private ImportEncodingPreference selectedImportEncodingPreference() {
        int position = importEncodingSpinner == null
                ? 0 : importEncodingSpinner.getSelectedItemPosition();
        ImportEncodingPreference.Choice[] choices =
                ImportEncodingPreference.Choice.values();
        if (position < 0 || position >= choices.length) {
            position = 0;
        }
        return new ImportEncodingPreference(choices[position]);
    }

    private String importEncodingSummary(ImportResult result) {
        DetectedEncoding encoding = result.getEncoding();
        return importEncodingSummary(encoding.getCharset().name(),
                encoding.getConfidence(), encoding.getSelectionMode(),
                encoding.getAdvisory(), result.getDecodingReplacementCount(),
                result.getFirstDecodingErrorByteOffset(),
                result.getDecodingReplacements().size());
    }

    private String importEncodingSummary(BookEncodingProfile profile) {
        return importEncodingSummary(profile.getCharsetName(),
                profile.getConfidence(), profile.getSelectionMode(),
                profile.getAdvisory(), profile.getDecodingReplacementCount(),
                profile.getFirstDecodingErrorByteOffset(),
                profile.getDecodingReplacements().size());
    }

    private String importEncodingSummary(String charsetName, double confidenceValue,
            DetectedEncoding.SelectionMode selectionMode,
            DetectedEncoding.Advisory advisory, long replacementCount,
            long firstErrorByteOffset, int retainedLocationCount) {
        String summary;
        if (selectionMode == DetectedEncoding.SelectionMode.MANUAL_OVERRIDE) {
            summary = getString(R.string.import_encoding_manual_result,
                    charsetName);
        } else {
            int confidence = (int) Math.round(confidenceValue * 100.0);
            switch (advisory) {
                case BIG5_HEURISTIC:
                    summary = getString(R.string.import_encoding_big5_heuristic,
                            confidence);
                    break;
                case LEGACY_AMBIGUOUS:
                    summary = getString(R.string.import_encoding_legacy_ambiguous,
                            confidence);
                    break;
                case MALFORMED_FALLBACK:
                    summary = getString(R.string.import_encoding_malformed_fallback,
                            confidence);
                    break;
                case NONE:
                default:
                    summary = getString(R.string.import_encoding_auto_result,
                            charsetName, confidence);
                    break;
            }
        }
        if (replacementCount > 0) {
            summary += getString(R.string.import_encoding_replacements_suffix,
                    replacementCount, firstErrorByteOffset);
            if (retainedLocationCount == 0) {
                summary += getString(R.string.import_encoding_locations_legacy);
            } else if (replacementCount > retainedLocationCount) {
                summary += getString(R.string.import_encoding_locations_truncated,
                        retainedLocationCount);
            }
        }
        return summary;
    }

    private void importRulePack(Uri uri) {
        if (!ruleTransferReady()) {
            return;
        }
        String expectedRevision = baseRevision;
        long expectedGeneration = repairRulesGeneration;
        List<RepairRule> existing = normalizedRepairRules(repairRules);
        status.setText(R.string.repair_importing_pack);
        worker.execute(() -> {
            try {
                byte[] bytes;
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    if (input == null) {
                        throw new IllegalStateException("规则包没有可读数据");
                    }
                    bytes = readLimited(input, 1024 * 1024);
                }
                List<RepairRule> imported = new RepairRulePackCodec().decode(bytes);
                RepairRuleMergeResult merge = new RepairRuleMerger().merge(existing, imported,
                        RepairRuleMergePolicy.REPLACE_EXISTING);
                runOnUiThread(() -> {
                    if (!expectedRevision.equals(baseRevision)
                            || expectedGeneration != repairRulesGeneration) {
                        status.setText(R.string.repair_import_stale);
                        return;
                    }
                    if (!beginRuleMutation()) {
                        return;
                    }
                    repairRules.clear();
                    repairRules.addAll(merge.getRules());
                    if (repairRules.isEmpty()) {
                        replaceRepairRules(Collections.emptyList());
                    } else {
                        orderRepairRulesAndSelect(repairRules.get(0).getId());
                    }
                    persistRepairRules(() -> status.setText(getString(
                            R.string.repair_import_complete, merge.getAdded(),
                            merge.getReplaced(), merge.getSkipped())));
                });
            } catch (Exception error) {
                String message = error.getMessage() == null
                        ? error.getClass().getSimpleName() : error.getMessage();
                runOnUiThread(() -> {
                    if (expectedRevision.equals(baseRevision)
                            && expectedGeneration == repairRulesGeneration) {
                        status.setText(getString(R.string.repair_failed, message));
                    }
                });
            }
        });
    }

    private void exportRulePack(Uri uri) {
        if (!ruleTransferReady()) {
            return;
        }
        List<RepairRule> snapshot = normalizedRepairRules(repairRules);
        status.setText(R.string.repair_exporting_pack);
        exportInProgress = true;
        worker.execute(() -> {
            try {
                byte[] bytes = new RepairRulePackCodec().encode(snapshot);
                String sourceToken = sha256Hex(bytes);
                String displayName = queryDisplayName(uri, "jingdu-rules.jdrp");
                writeExportPending(new ExportRecoveryJournal.Entry(
                        ExportRecoveryJournal.Kind.RULE_PACK, sourceToken, displayName,
                        bytes.length, System.currentTimeMillis()));
                writeAndVerifyExport(uri, new ByteArrayInputStream(bytes), bytes.length,
                        "规则包目标不可写");
                clearExportPending();
                runOnUiThread(() -> status.setText(getString(
                        R.string.repair_export_pack_complete, snapshot.size(), bytes.length)));
            } catch (Exception error) {
                showRepairError(error);
            } finally {
                runOnUiThread(() -> {
                    exportInProgress = false;
                    refreshExportRecoveryStatus();
                });
            }
        });
    }

    private void exportCleanText(Uri uri) {
        if (repairApplying) {
            status.setText(R.string.repair_busy);
            return;
        }
        Path source = activeTextPath;
        String revision = currentRevision;
        if (source == null || revision.isEmpty()) {
            status.setText(R.string.repair_need_book);
            return;
        }
        status.setText(R.string.repair_exporting_text);
        exportInProgress = true;
        worker.execute(() -> {
            try {
                long bytes = Files.size(source);
                String displayName = queryDisplayName(uri,
                        "jingdu-cleaned-" + revision.substring(0, 8) + ".txt");
                writeExportPending(new ExportRecoveryJournal.Entry(
                        ExportRecoveryJournal.Kind.CLEAN_TEXT, revision, displayName,
                        bytes, System.currentTimeMillis()));
                try (InputStream input = Files.newInputStream(source)) {
                    writeAndVerifyExport(uri, input, bytes, "TXT 导出目标不可写");
                }
                clearExportPending();
                runOnUiThread(() -> status.setText(getString(
                        R.string.repair_export_text_complete, bytes,
                        revision.substring(0, 8))));
            } catch (Exception error) {
                showRepairError(error);
            } finally {
                runOnUiThread(() -> {
                    exportInProgress = false;
                    refreshExportRecoveryStatus();
                });
            }
        });
    }

    private void writeAndVerifyExport(Uri uri, InputStream source, long expectedBytes,
            String unwritableMessage) throws Exception {
        MessageDigest sourceDigest = MessageDigest.getInstance("SHA-256");
        long written = 0;
        try (OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
            if (output == null) {
                throw new IllegalStateException(unwritableMessage);
            }
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = source.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                output.write(buffer, 0, read);
                sourceDigest.update(buffer, 0, read);
                written += read;
            }
            output.flush();
        }
        if (written != expectedBytes) {
            throw new IllegalStateException("导出源在写入期间发生变化");
        }

        MessageDigest targetDigest = MessageDigest.getInstance("SHA-256");
        long verified = 0;
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) {
                throw new IllegalStateException("导出目标不可回读校验");
            }
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read == 0) {
                    continue;
                }
                targetDigest.update(buffer, 0, read);
                verified += read;
            }
        }
        if (verified != expectedBytes
                || !Arrays.equals(sourceDigest.digest(), targetDigest.digest())) {
            throw new IllegalStateException("导出目标回读校验失败，可能是不完整文件");
        }
    }

    private void retryPendingExport() {
        ExportRecoveryJournal.Entry pending;
        try {
            pending = readExportPending();
        } catch (Exception invalidJournal) {
            clearInvalidExportRecovery();
            return;
        }
        if (pending == null) {
            refreshExportRecoveryStatus();
            return;
        }
        try {
            if (pending.getKind() == ExportRecoveryJournal.Kind.CLEAN_TEXT) {
                Path source = activeTextPath;
                if (source == null || !pending.getSourceToken().equals(currentRevision)
                        || Files.size(source) != pending.getExpectedBytes()) {
                    clearStaleExportRecovery();
                    return;
                }
                createCleanTextExport(pending.getDisplayName(), true);
                return;
            }
            if (!ruleTransferReady()) {
                return;
            }
            byte[] bytes = new RepairRulePackCodec().encode(normalizedRepairRules(repairRules));
            if (!pending.getSourceToken().equals(sha256Hex(bytes))
                    || pending.getExpectedBytes() != bytes.length) {
                clearStaleExportRecovery();
                return;
            }
            createRulePackExport(pending.getDisplayName(), true);
        } catch (Exception error) {
            showRepairError(error);
        }
    }

    private void clearInvalidExportRecovery() {
        try {
            clearExportPending();
            refreshExportRecoveryStatus();
            status.setText(R.string.repair_export_recovery_corrupt_cleared);
        } catch (Exception clearError) {
            showRepairError(clearError);
        }
    }

    private static boolean containsControlCharacter(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private void refreshExportRecoveryStatus() {
        try {
            ExportRecoveryJournal.Entry pending = readExportPending();
            if (pending == null) {
                exportRecoveryStatus.setVisibility(View.GONE);
                retryExportButton.setVisibility(View.GONE);
                return;
            }
            String kind = getString(pending.getKind() == ExportRecoveryJournal.Kind.CLEAN_TEXT
                    ? R.string.repair_export_kind_text : R.string.repair_export_kind_pack);
            exportRecoveryStatus.setText(getString(R.string.repair_export_recovery_pending,
                    kind, pending.getDisplayName(), pending.getExpectedBytes()));
            exportRecoveryStatus.setVisibility(View.VISIBLE);
            retryExportButton.setVisibility(View.VISIBLE);
        } catch (Exception error) {
            exportRecoveryStatus.setText(R.string.repair_export_recovery_corrupt);
            exportRecoveryStatus.setVisibility(View.VISIBLE);
            retryExportButton.setVisibility(View.VISIBLE);
        }
    }

    private void clearStaleExportRecovery() throws Exception {
        clearExportPending();
        refreshExportRecoveryStatus();
        status.setText(R.string.repair_export_retry_stale_cleared);
    }

    private ExportRecoveryJournal.Entry readExportPending() throws Exception {
        return new ExportRecoveryJournal().readPending(exportJournalPath());
    }

    private void writeExportPending(ExportRecoveryJournal.Entry entry) throws Exception {
        new ExportRecoveryJournal().writePending(exportJournalPath(), entry);
    }

    private void clearExportPending() throws Exception {
        new ExportRecoveryJournal().clear(exportJournalPath());
    }

    private Path exportJournalPath() {
        return new File(getFilesDir(), "exports/pending.bin").toPath();
    }

    private String queryDisplayName(Uri uri, String fallback) {
        try (Cursor cursor = getContentResolver().query(uri,
                new String[] {OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) {
                String displayName = cursor.getString(0);
                if (displayName != null && !displayName.isEmpty()
                        && displayName.length() <= 255
                        && !containsControlCharacter(displayName)) {
                    return displayName;
                }
            }
        } catch (RuntimeException ignored) {
            // Provider metadata is optional; the safe proposed filename remains useful recovery UI.
        }
        return fallback;
    }

    private static String sha256Hex(byte[] bytes) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            int unsigned = value & 0xFF;
            result.append(Character.forDigit(unsigned >>> 4, 16));
            result.append(Character.forDigit(unsigned & 0x0F, 16));
        }
        return result.toString();
    }

    private static byte[] readLimited(InputStream input, int maximumBytes) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read == 0) {
                continue;
            }
            total += read;
            if (total > maximumBytes) {
                throw new IllegalArgumentException("规则包超过 1 MiB");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private void resumeBookshelf() {
        long request = ++bookshelfRequest;
        worker.execute(() -> {
            try {
                Path bookDirectory = new File(getFilesDir(), "books").toPath();
                boolean deletionRecovered = finishPendingBookDeletion(bookDirectory);
                List<BookLibraryEntry> entries = loadBookLibrary(bookDirectory);
                if (entries.isEmpty()) {
                    BookLibraryEntry migrated = migrateLegacyBook(bookDirectory);
                    if (migrated != null) {
                        entries = bookLibraryStore.upsert(entries, migrated);
                        saveBookLibrary(bookDirectory, entries);
                    }
                }
                entries = applyPendingBookProgress(bookDirectory, entries);
                List<BookLibraryEntry> visibleEntries = bookLibraryStore.shelved(entries);
                if (visibleEntries.isEmpty()) {
                    runOnUiThread(() -> {
                        refreshBookshelf(Collections.emptyList(), "");
                        if (deletionRecovered) {
                            status.setText(R.string.bookshelf_delete_recovered);
                        }
                    });
                    return;
                }
                runOnUiThread(() -> refreshBookshelf(
                        visibleEntries, visibleEntries.get(0).getBookId()));
                restoreBookEntry(bookDirectory, visibleEntries.get(0),
                        deletionRecovered ? R.string.bookshelf_resumed_after_delete
                                : R.string.bookshelf_resumed,
                        request);
            } catch (Exception error) {
                showBookshelfFailure(R.string.resume_failed, error);
            }
        });
    }

    private BookLibraryEntry migrateLegacyBook(Path bookDirectory) throws Exception {
        BookState legacy = readBookState(bookDirectory);
        if (legacy == null) {
            return null;
        }
        Path original = resolveBookFile(bookDirectory, legacy.baseFileName,
                "恢复原文件无效或已丢失");
        resolveBookFile(bookDirectory, legacy.activeFileName,
                "恢复阅读文件无效或已丢失");
        if (!legacy.projectionFileName.isEmpty()) {
            resolveBookFile(bookDirectory, legacy.projectionFileName,
                    "上次净读映射不可用");
        }
        long importedAt = Math.max(1L, Files.getLastModifiedTime(original).toMillis());
        long openedAt = Math.max(importedAt, System.currentTimeMillis());
        return new BookLibraryEntry(legacy.baseRevision, legacy.baseFileName,
                legacy.baseFileName, legacy.baseRevision,
                legacy.activeFileName, legacy.activeRevision, legacy.projectionFileName,
                "UTF-8", Files.size(original), importedAt, openedAt, 0);
    }

    private void confirmRemoveSelectedBook() {
        BookLibraryEntry selected = selectedBookshelfEntry();
        if (selected == null || !libraryActionReady()) {
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.bookshelf_confirm_remove_title,
                        selected.getDisplayName()))
                .setMessage(R.string.bookshelf_confirm_remove_message)
                .setNegativeButton(R.string.bookshelf_cancel, null)
                .setPositiveButton(R.string.bookshelf_remove,
                        (dialog, which) -> removeBookFromShelf(selected))
                .show();
    }

    private void confirmDeleteSelectedBook() {
        BookLibraryEntry selected = selectedBookshelfEntry();
        if (selected == null || !libraryActionReady()) {
            return;
        }
        if (exportInProgress) {
            status.setText(R.string.bookshelf_delete_blocked_export);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.bookshelf_confirm_delete_title,
                        selected.getDisplayName()))
                .setMessage(R.string.bookshelf_confirm_delete_message)
                .setNegativeButton(R.string.bookshelf_cancel, null)
                .setPositiveButton(R.string.bookshelf_delete_copy,
                        (dialog, which) -> deleteBookCopy(selected))
                .show();
    }

    private BookLibraryEntry selectedBookshelfEntry() {
        int selected = bookshelfSpinner.getSelectedItemPosition();
        if (selected < 0 || selected >= bookshelf.size()) {
            status.setText(R.string.bookshelf_empty);
            return null;
        }
        return bookshelf.get(selected);
    }

    private boolean libraryActionReady() {
        if (repairApplying || libraryTransitionInProgress) {
            status.setText(R.string.repair_busy);
            return false;
        }
        return true;
    }

    private void prepareLibraryTransition() {
        stopCompanionMode();
        cancelCompanionSleepTimer(false);
        repairRulesGeneration++;
        repairRulesPersistedGeneration = -1;
        resetOccurrenceSelection();
        invalidatePendingRepair();
        bookmarkRequest++;
        chapterOutlineRequest++;
        chapterOutlineTransitionInProgress = false;
        viewportRequest++;
        libraryTransitionInProgress = true;
        refreshEncodingJumpButton();
    }

    private void removeBookFromShelf(BookLibraryEntry selected) {
        BookProgressSnapshot previous = captureBookProgress();
        boolean removingCurrent = selected.getBookId().equals(currentBookId);
        if (removingCurrent) {
            prepareLibraryTransition();
        } else {
            libraryTransitionInProgress = true;
        }
        long request = ++bookshelfRequest;
        worker.execute(() -> {
            try {
                Path bookDirectory = new File(getFilesDir(), "books").toPath();
                if (readExportPending() != null) {
                    showBookshelfTransitionMessage(
                            R.string.bookshelf_delete_blocked_export, request);
                    return;
                }
                persistBookProgress(bookDirectory, previous);
                List<BookLibraryEntry> entries = loadBookLibrary(bookDirectory);
                BookLibraryEntry current = bookLibraryStore.find(
                        entries, selected.getBookId());
                if (current == null || !current.isShelved()) {
                    throw new IllegalStateException("所选书籍已不在书架中");
                }
                BookLibraryEntry removed = current.withShelved(
                        false, nextOpenedAt(current));
                List<BookLibraryEntry> updated = bookLibraryStore.upsert(entries, removed);
                saveBookLibrary(bookDirectory, updated);
                if (removingCurrent) {
                    Files.deleteIfExists(bookDirectory.resolve("last-book.bin"));
                }
                List<BookLibraryEntry> visible = bookLibraryStore.shelved(updated);
                if (removingCurrent && !visible.isEmpty()) {
                    try {
                        restoreBookEntry(bookDirectory, visible.get(0),
                                R.string.bookshelf_removed_opened, request);
                    } catch (Exception restoreError) {
                        runOnUiThread(() -> {
                            if (request == bookshelfRequest) {
                                clearCurrentBook(visible, getString(
                                        R.string.bookshelf_remove_failed,
                                        safeMessage(restoreError)));
                            }
                        });
                        throw restoreError;
                    }
                } else {
                    runOnUiThread(() -> {
                        if (request != bookshelfRequest) {
                            return;
                        }
                        libraryTransitionInProgress = false;
                        if (removingCurrent) {
                            clearCurrentBook(visible, getString(
                                    R.string.bookshelf_removed, removed.getDisplayName()));
                        } else {
                            refreshBookshelf(updated, currentBookId);
                            status.setText(getString(
                                    R.string.bookshelf_removed, removed.getDisplayName()));
                            refreshEncodingJumpButton();
                        }
                    });
                }
            } catch (Exception error) {
                showBookshelfTransitionFailure(
                        R.string.bookshelf_remove_failed, error, request);
            }
        });
    }

    private void restoreMostRecentRemovedBook() {
        if (!libraryActionReady()) {
            return;
        }
        BookProgressSnapshot previous = captureBookProgress();
        prepareLibraryTransition();
        long request = ++bookshelfRequest;
        worker.execute(() -> {
            try {
                Path bookDirectory = new File(getFilesDir(), "books").toPath();
                persistBookProgress(bookDirectory, previous);
                List<BookLibraryEntry> entries = loadBookLibrary(bookDirectory);
                BookLibraryEntry removed = null;
                for (BookLibraryEntry entry : entries) {
                    if (!entry.isShelved()) {
                        removed = entry;
                        break;
                    }
                }
                if (removed == null) {
                    showBookshelfTransitionMessage(R.string.bookshelf_no_removed, request);
                    return;
                }
                if (!isBookEntryRestorable(bookDirectory, removed)) {
                    throw new IllegalStateException("已移出副本缺失或损坏，未重新上架");
                }
                BookLibraryEntry restored = removed.withShelved(
                        true, nextOpenedAt(removed));
                List<BookLibraryEntry> updated = bookLibraryStore.upsert(entries, restored);
                saveBookLibrary(bookDirectory, updated);
                restoreBookEntry(bookDirectory, restored,
                        R.string.bookshelf_restored_removed, request);
            } catch (Exception error) {
                showBookshelfTransitionFailure(
                        R.string.bookshelf_open_failed, error, request);
            }
        });
    }

    private void deleteBookCopy(BookLibraryEntry selected) {
        boolean deletingCurrent = selected.getBookId().equals(currentBookId);
        if (deletingCurrent) {
            prepareLibraryTransition();
        } else {
            libraryTransitionInProgress = true;
        }
        long request = ++bookshelfRequest;
        status.setText(R.string.bookshelf_deleting);
        worker.execute(() -> {
            boolean journalPublished = false;
            try {
                Path bookDirectory = new File(getFilesDir(), "books").toPath();
                if (readExportPending() != null || exportInProgress) {
                    showBookshelfTransitionMessage(
                            R.string.bookshelf_delete_blocked_export, request);
                    return;
                }
                if (new BookDeletionJournal().readPending(
                        deletionJournalPath(bookDirectory)) != null) {
                    finishPendingBookDeletion(bookDirectory);
                }
                List<BookLibraryEntry> entries = loadBookLibrary(bookDirectory);
                BookLibraryEntry current = bookLibraryStore.find(
                        entries, selected.getBookId());
                if (current == null) {
                    throw new IllegalStateException("所选书籍已被删除");
                }
                BookDeletionJournal journal = new BookDeletionJournal();
                journal.writePending(deletionJournalPath(bookDirectory),
                        createDeletionEntry(current));
                journalPublished = true;
                finishPendingBookDeletion(bookDirectory);
                List<BookLibraryEntry> remaining = loadBookLibrary(bookDirectory);
                List<BookLibraryEntry> visible = bookLibraryStore.shelved(remaining);
                if (deletingCurrent && !visible.isEmpty()) {
                    restoreBookEntry(bookDirectory, visible.get(0),
                            R.string.bookshelf_deleted_opened, request);
                } else {
                    runOnUiThread(() -> {
                        if (request != bookshelfRequest) {
                            return;
                        }
                        libraryTransitionInProgress = false;
                        if (deletingCurrent) {
                            clearCurrentBook(visible, getString(
                                    R.string.bookshelf_deleted,
                                    current.getDisplayName()));
                        } else {
                            refreshBookshelf(remaining, currentBookId);
                            status.setText(getString(R.string.bookshelf_deleted,
                                    current.getDisplayName()));
                            refreshEncodingJumpButton();
                        }
                    });
                }
            } catch (Exception error) {
                if (journalPublished && deletingCurrent) {
                    runOnUiThread(() -> {
                        if (request == bookshelfRequest) {
                            clearCurrentBook(Collections.emptyList(), getString(
                                    R.string.bookshelf_delete_pending_failed,
                                    safeMessage(error)));
                        }
                    });
                }
                showBookshelfTransitionFailure(
                        journalPublished ? R.string.bookshelf_delete_pending_failed
                                : R.string.bookshelf_open_failed,
                        error, request);
            }
        });
    }

    private BookDeletionJournal.Entry createDeletionEntry(BookLibraryEntry entry) {
        List<String> fileNames = new ArrayList<>();
        addUnique(fileNames, entry.getBaseFileName());
        addUnique(fileNames, entry.getActiveFileName());
        if (!entry.getProjectionFileName().isEmpty()) {
            addUnique(fileNames, entry.getProjectionFileName());
        }
        List<String> revisions = new ArrayList<>();
        addUnique(revisions, entry.getBaseRevision());
        addUnique(revisions, entry.getActiveRevision());
        return new BookDeletionJournal.Entry(entry.getBookId(), fileNames,
                revisions, Math.max(1L, System.currentTimeMillis()));
    }

    private static void addUnique(List<String> values, String value) {
        if (!values.contains(value)) {
            values.add(value);
        }
    }

    private List<BookLibraryEntry> loadBookLibrary(Path bookDirectory) throws Exception {
        List<BookLibraryEntry> entries = bookLibraryStore.load(
                bookDirectory.resolve(BOOK_LIBRARY_FILE));
        Set<String> hiddenIds = bookShelfStateStore.load(
                bookDirectory.resolve(BOOK_SHELF_STATE_FILE));
        List<BookLibraryEntry> resolved = new ArrayList<>(entries.size());
        for (BookLibraryEntry entry : entries) {
            resolved.add(entry.withShelved(!hiddenIds.contains(entry.getBookId()),
                    entry.getLastOpenedAtEpochMillis()));
        }
        return resolved;
    }

    private void saveBookLibrary(Path bookDirectory, List<BookLibraryEntry> entries)
            throws Exception {
        bookLibraryStore.save(bookDirectory.resolve(BOOK_LIBRARY_FILE), entries);
        Set<String> hiddenIds = new HashSet<>();
        for (BookLibraryEntry entry : entries) {
            if (!entry.isShelved()) {
                hiddenIds.add(entry.getBookId());
            }
        }
        bookShelfStateStore.save(bookDirectory.resolve(BOOK_SHELF_STATE_FILE), hiddenIds);
    }

    private boolean finishPendingBookDeletion(Path bookDirectory) throws Exception {
        BookDeletionJournal journal = new BookDeletionJournal();
        Path journalPath = deletionJournalPath(bookDirectory);
        BookDeletionJournal.Entry pending = journal.readPending(journalPath);
        if (pending == null) {
            return false;
        }
        List<BookLibraryEntry> entries = loadBookLibrary(bookDirectory);
        List<BookLibraryEntry> remaining = bookLibraryStore.remove(
                entries, pending.getBookId());
        saveBookLibrary(bookDirectory, remaining);
        removeBookRulesProfile(pending.getBookId());
        removeBookBookmarks(bookDirectory, pending.getBookId());
        removeBookChapterOutlines(bookDirectory, pending.getBookId());
        removeBookEncodingProfiles(bookDirectory, pending.getBookId(), null);
        BookDeletionPolicy.Plan deletionPlan = new BookDeletionPolicy().resolve(
                pending, remaining);
        for (String fileName : deletionPlan.getFileNames()) {
            deleteKnownBookFile(bookDirectory, fileName);
        }
        for (String revision : deletionPlan.getIndexRevisions()) {
            deleteBookTree(bookDirectory,
                    bookDirectory.resolve("indexes").resolve(revision));
        }
        Path legacyState = bookDirectory.resolve("last-book.bin");
        if (remaining.isEmpty()) {
            Files.deleteIfExists(legacyState);
        } else if (Files.isRegularFile(legacyState)) {
            BookState state = readBookState(bookDirectory);
            if (state != null && (pending.getIndexRevisions().contains(state.baseRevision)
                    || pending.getIndexRevisions().contains(state.activeRevision))) {
                Files.deleteIfExists(legacyState);
            }
        }
        SharedPreferences progress = getSharedPreferences(
                BOOK_PROGRESS_PREFERENCES, MODE_PRIVATE);
        if (pending.getBookId().equals(progress.getString(BOOK_PROGRESS_ID_KEY, ""))) {
            progress.edit().clear().apply();
        }
        journal.clear(journalPath);
        return true;
    }

    private void removeBookRulesProfile(String bookId) throws Exception {
        Path catalogPath = new File(getFilesDir(), "rules/catalog.bin").toPath();
        if (!Files.isRegularFile(catalogPath)) {
            return;
        }
        RepairRuleStore store = new RepairRuleStore();
        Map<String, List<RepairRule>> catalog = store.loadCatalog(catalogPath);
        if (catalog.remove(bookId) != null) {
            store.saveCatalog(catalogPath, catalog);
        }
    }

    private void removeBookBookmarks(Path bookDirectory, String bookId) throws Exception {
        Files.deleteIfExists(bookmarkPath(bookDirectory, bookId));
    }

    private void removeBookChapterOutlines(Path bookDirectory, String bookId) throws Exception {
        if (bookId == null || !bookId.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("无效章节目录书籍 ID");
        }
        Path directory = bookDirectory.resolve(CHAPTER_OUTLINES_DIRECTORY);
        if (!Files.isDirectory(directory)) {
            return;
        }
        String prefix = bookId + "-";
        try (java.nio.file.DirectoryStream<Path> paths =
                Files.newDirectoryStream(directory)) {
            for (Path path : paths) {
                String name = path.getFileName().toString();
                if ((name.equals(bookId + ".bin")
                        || name.matches(prefix + "[0-9a-f]{64}\\.bin"))
                        && Files.isRegularFile(path)
                        && !Files.isSymbolicLink(path)) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private Path bookmarkPath(Path bookDirectory, String bookId) {
        if (bookId == null || !bookId.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("无效书签书籍 ID");
        }
        return bookDirectory.resolve(BOOKMARKS_DIRECTORY).resolve(bookId + ".bin");
    }

    private Path chapterOutlinePath(Path bookDirectory, String bookId,
            String baseRevision) {
        if (bookId == null || !bookId.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("无效章节目录书籍 ID");
        }
        if (baseRevision == null || !baseRevision.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("无效章节目录原文版本");
        }
        return bookDirectory.resolve(CHAPTER_OUTLINES_DIRECTORY)
                .resolve(bookId + "-" + baseRevision + ".bin");
    }

    private Path encodingProfilePath(Path bookDirectory, String bookId,
            String baseRevision) {
        if (bookId == null || !bookId.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("无效编码诊断书籍 ID");
        }
        if (baseRevision == null || !baseRevision.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("无效编码诊断原文版本");
        }
        return bookDirectory.resolve(ENCODING_PROFILES_DIRECTORY)
                .resolve(bookId + "-" + baseRevision + ".bin");
    }

    private BookEncodingProfile replaceBookEncodingProfile(Path bookDirectory,
            ImportResult result)
            throws Exception {
        BookEncodingProfile profile = encodingProfileFrom(result);
        Path path = encodingProfilePath(bookDirectory,
                profile.getBookId(), profile.getBaseRevision());
        BookEncodingProfile previous = bookEncodingProfileStore.load(path);
        bookEncodingProfileStore.save(path, profile);
        return previous;
    }

    private static BookEncodingProfile encodingProfileFrom(ImportResult result) {
        return BookEncodingProfile.from(result.getSourceSha256(),
                result.getOutputSha256(), result.getEncoding(),
                result.getDecodingReplacementCount(),
                result.getFirstDecodingErrorByteOffset(),
                result.getFirstReplacementCharacterOffset(),
                result.getDecodingReplacements());
    }

    private void rollbackBookEncodingProfile(Path bookDirectory,
            ImportResult result, BookEncodingProfile previous,
            Exception originalError) {
        try {
            Path path = encodingProfilePath(bookDirectory,
                    result.getSourceSha256(), result.getOutputSha256());
            if (previous == null) {
                Files.deleteIfExists(path);
            } else {
                bookEncodingProfileStore.save(path, previous);
            }
        } catch (Exception rollbackError) {
            originalError.addSuppressed(rollbackError);
        }
    }

    private BookEncodingProfile loadBookEncodingProfile(Path bookDirectory,
            String bookId, String expectedBaseRevision) throws Exception {
        BookEncodingProfile profile = bookEncodingProfileStore.load(
                encodingProfilePath(bookDirectory, bookId, expectedBaseRevision));
        if (profile != null && (!profile.getBookId().equals(bookId)
                || !profile.getBaseRevision().equals(expectedBaseRevision))) {
            throw new IllegalStateException("编码诊断与书籍版本不匹配");
        }
        return profile;
    }

    private void removeBookEncodingProfiles(Path bookDirectory, String bookId,
            String keptBaseRevision) throws Exception {
        if (bookId == null || !bookId.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("无效编码诊断书籍 ID");
        }
        if (keptBaseRevision != null
                && !keptBaseRevision.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("无效保留编码诊断版本");
        }
        Path directory = bookDirectory.resolve(ENCODING_PROFILES_DIRECTORY);
        if (!Files.isDirectory(directory)) {
            return;
        }
        String prefix = bookId + "-";
        String keptName = keptBaseRevision == null ? ""
                : prefix + keptBaseRevision + ".bin";
        try (java.nio.file.DirectoryStream<Path> paths =
                Files.newDirectoryStream(directory)) {
            for (Path path : paths) {
                String name = path.getFileName().toString();
                if (name.matches(prefix + "[0-9a-f]{64}\\.bin")
                        && !name.equals(keptName)
                        && Files.isRegularFile(path)
                        && !Files.isSymbolicLink(path)) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private ChapterOutline loadChapterOutline(Path bookDirectory, String bookId,
            String expectedBaseRevision) throws Exception {
        ChapterOutline loaded = chapterOutlineStore.load(
                chapterOutlinePath(bookDirectory, bookId, expectedBaseRevision));
        if (loaded != null && !loaded.getBaseRevision().equals(expectedBaseRevision)) {
            throw new IllegalStateException("章节目录与原文版本不匹配");
        }
        return loaded;
    }

    private List<ChapterEntry> mapChapterOutline(ChapterOutline outline,
            Path projection) throws Exception {
        if (outline == null) {
            return Collections.emptyList();
        }
        ChapterOutlineMapper mapper = new ChapterOutlineMapper();
        if (projection == null) {
            return mapper.identity(outline);
        }
        try (DiskRepairProjection mapping = DiskRepairProjection.open(projection)) {
            return mapper.map(outline, mapping::mapOriginalToDerived);
        }
    }

    private List<BookBookmark> loadBookBookmarks(Path bookDirectory, String bookId)
            throws Exception {
        List<BookBookmark> loaded = bookBookmarkStore.load(
                bookmarkPath(bookDirectory, bookId));
        return bookBookmarkStore.requireBookProfile(loaded, bookId);
    }

    private static void deleteKnownBookFile(Path bookDirectory, String fileName)
            throws Exception {
        Path root = bookDirectory.toAbsolutePath().normalize();
        Path target = bookDirectory.resolve(fileName).toAbsolutePath().normalize();
        if (!target.startsWith(root) || target.equals(root)
                || !target.getParent().equals(root)) {
            throw new IllegalStateException("拒绝删除书籍目录外的文件");
        }
        Files.deleteIfExists(target);
    }

    private static void deleteBookTree(Path bookDirectory, Path target) throws Exception {
        Path root = bookDirectory.toAbsolutePath().normalize();
        Path normalized = target.toAbsolutePath().normalize();
        if (!normalized.startsWith(root) || normalized.equals(root)) {
            throw new IllegalStateException("拒绝删除书籍目录外的索引");
        }
        if (!Files.exists(normalized)) {
            return;
        }
        if (Files.isSymbolicLink(normalized) || !Files.isDirectory(normalized)) {
            Files.deleteIfExists(normalized);
            return;
        }
        try (java.util.stream.Stream<Path> paths = Files.walk(normalized)) {
            List<Path> ordered = new ArrayList<>();
            paths.forEach(ordered::add);
            Collections.sort(ordered, Comparator.reverseOrder());
            for (Path path : ordered) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static Path deletionJournalPath(Path bookDirectory) {
        return bookDirectory.resolve(BOOK_DELETE_JOURNAL_FILE);
    }

    private void clearCurrentBook(List<BookLibraryEntry> entries, String message) {
        currentText = "";
        currentRevision = "";
        currentWindowIndex = null;
        currentDiskIndex = null;
        currentWindowStart = 0;
        baseOriginalPath = null;
        baseRevision = "";
        activeTextPath = null;
        activeProjectionPath = null;
        currentBookId = "";
        currentBookEncodingProfile = null;
        encodingReplacementCursor = -1;
        pendingRepair = null;
        chapterCursor = -1;
        replaceRepairRules(defaultRepairRules());
        resetOccurrenceSelection();
        readerSurface.setDocumentText(
                getString(R.string.initial_reader_text), "initial");
        importEncodingStatus.setText(R.string.import_encoding_status_empty);
        refreshEncodingJumpButton();
        refreshBookshelf(entries, "");
        refreshBookmarks(Collections.emptyList());
        currentChapterOutline = null;
        refreshChapters(Collections.emptyList(), -1);
        status.setText(message);
        refreshExportRecoveryStatus();
    }

    private void showBookshelfTransitionMessage(int messageResource, long request) {
        runOnUiThread(() -> {
            if (request == bookshelfRequest) {
                libraryTransitionInProgress = false;
                status.setText(messageResource);
                refreshEncodingJumpButton();
            }
        });
    }

    private static String safeMessage(Exception error) {
        return error.getMessage() == null
                ? error.getClass().getSimpleName() : error.getMessage();
    }

    private void openSelectedBook() {
        if (!libraryActionReady()) {
            return;
        }
        int selected = bookshelfSpinner.getSelectedItemPosition();
        if (selected < 0 || selected >= bookshelf.size()) {
            status.setText(R.string.bookshelf_empty);
            return;
        }
        BookLibraryEntry selectedBook = bookshelf.get(selected);
        BookProgressSnapshot previous = captureBookProgress();
        prepareLibraryTransition();
        long request = ++bookshelfRequest;
        status.setText(R.string.bookshelf_switching);
        worker.execute(() -> {
            try {
                Path bookDirectory = new File(getFilesDir(), "books").toPath();
                persistBookProgress(bookDirectory, previous);
                List<BookLibraryEntry> latest = loadBookLibrary(bookDirectory);
                BookLibraryEntry target = bookLibraryStore.find(
                        latest, selectedBook.getBookId());
                if (target == null) {
                    throw new IllegalStateException("所选书籍已不在书架中");
                }
                restoreBookEntry(bookDirectory, target,
                        R.string.bookshelf_opened, request);
            } catch (Exception error) {
                showBookshelfTransitionFailure(
                        R.string.bookshelf_open_failed, error, request);
            }
        });
    }

    private void restoreBookEntry(Path bookDirectory, BookLibraryEntry entry,
            int successMessage, long request) throws Exception {
        if (!entry.isShelved()) {
            throw new IllegalStateException("已移出书籍必须先恢复到书架");
        }
        Path original = resolveBookFile(bookDirectory, entry.getBaseFileName(),
                "书架原文件无效或已丢失");
        Path active = resolveBookFile(bookDirectory, entry.getActiveFileName(),
                "书架阅读文件无效或已丢失");
        Path projection = entry.getProjectionFileName().isEmpty() ? null
                : resolveBookFile(bookDirectory, entry.getProjectionFileName(),
                        "书架净读映射不可用");
        String activeSha256 = DiskDocumentIndexBuilder.computeSha256(active);
        Path indexRoot = bookDirectory.resolve("indexes").resolve(entry.getActiveRevision());
        new DiskDocumentIndexBuilder().build(active, indexRoot,
                entry.getActiveRevision(), activeSha256, null);
        DiskDocumentIndex diskIndex = DiskDocumentIndex.openActive(indexRoot);
        int anchor = (int) Math.min((long) entry.getAnchorOffset(),
                Math.min((long) Integer.MAX_VALUE, diskIndex.getCharacterCount()));
        IndexedTextWindow window = diskIndex.readWindowAround(anchor, INDEX_WINDOW_CHARACTERS);
        DocumentIndex windowIndex = DocumentIndex.build(
                window.getText(), entry.getActiveRevision());
        List<RepairRule> loadedRules = loadRepairRules(entry.getBookId());
        List<BookBookmark> loadedBookmarks = loadBookBookmarks(
                bookDirectory, entry.getBookId());
        ChapterOutline loadedChapterOutline = loadChapterOutline(
                bookDirectory, entry.getBookId(), entry.getBaseRevision());
        BookEncodingProfile loadedEncodingProfile = null;
        boolean encodingProfileInvalid = false;
        try {
            loadedEncodingProfile = loadBookEncodingProfile(bookDirectory,
                    entry.getBookId(), entry.getBaseRevision());
        } catch (Exception invalidProfile) {
            encodingProfileInvalid = true;
        }
        List<ChapterEntry> loadedChapters = loadedChapterOutline == null
                ? diskIndex.getChapters()
                : mapChapterOutline(loadedChapterOutline, projection);
        long openedAt = nextOpenedAt(entry);
        BookLibraryEntry opened = entry.withProgress(anchor, openedAt);
        List<BookLibraryEntry> entries = loadBookLibrary(bookDirectory);
        entries = bookLibraryStore.upsert(entries, opened);
        saveBookLibrary(bookDirectory, entries);
        writeBookState(bookDirectory, new BookState(
                opened.getBaseFileName(), opened.getBaseRevision(),
                opened.getActiveFileName(), opened.getActiveRevision(),
                opened.getProjectionFileName()));
        List<BookLibraryEntry> finalEntries = entries;
        Path finalProjection = projection;
        BookEncodingProfile finalEncodingProfile = loadedEncodingProfile;
        boolean finalEncodingProfileInvalid = encodingProfileInvalid;
        runOnUiThread(() -> {
            if (request != bookshelfRequest) {
                return;
            }
            currentText = window.getText();
            currentRevision = opened.getActiveRevision();
            currentWindowIndex = windowIndex;
            currentDiskIndex = diskIndex;
            currentWindowStart = window.getStartOffset();
            baseOriginalPath = original;
            baseRevision = opened.getBaseRevision();
            activeTextPath = active;
            activeProjectionPath = finalProjection;
            currentBookId = opened.getBookId();
            currentBookEncodingProfile = finalEncodingProfile;
            encodingReplacementCursor = -1;
            libraryTransitionInProgress = false;
            pendingRepair = null;
            replaceRepairRules(loadedRules);
            markLoadedRepairRulesPersisted();
            resetOccurrenceSelection();
            chapterCursor = -1;
            readerSurface.setDocumentWindow(
                    currentText, currentRevision, currentWindowStart);
            if (finalEncodingProfile != null) {
                importEncodingStatus.setText(
                        importEncodingSummary(finalEncodingProfile));
            } else if (finalEncodingProfileInvalid) {
                importEncodingStatus.setText(getString(
                        R.string.import_encoding_status_corrupted,
                        opened.getEncodingName()));
            } else {
                importEncodingStatus.setText(getString(
                        R.string.import_encoding_status_restored,
                        opened.getEncodingName()));
            }
            readerSurface.scrollToAnchor(Integer.toString(anchor));
            refreshBookshelf(finalEntries, opened.getBookId());
            refreshBookmarks(loadedBookmarks);
            currentChapterOutline = loadedChapterOutline;
            refreshChapters(loadedChapters, 0);
            status.setText(getString(successMessage, opened.getDisplayName(), anchor,
                    opened.getEncodingName(), opened.getSourceBytes() / 1024.0 / 1024.0));
            refreshExportRecoveryStatus();
            refreshEncodingJumpButton();
            restoreRetainedSleepTimer();
            requestArtifactPrune();
        });
    }

    private static Path resolveBookFile(Path bookDirectory, String fileName,
            String failureMessage) {
        Path root = bookDirectory.toAbsolutePath().normalize();
        Path resolved = bookDirectory.resolve(fileName).toAbsolutePath().normalize();
        if (!resolved.startsWith(root) || !Files.isRegularFile(resolved)) {
            throw new IllegalStateException(failureMessage);
        }
        return resolved;
    }

    private static boolean isBookEntryRestorable(Path bookDirectory,
            BookLibraryEntry entry) {
        try {
            resolveBookFile(bookDirectory, entry.getBaseFileName(), "missing base file");
            resolveBookFile(bookDirectory, entry.getActiveFileName(), "missing active file");
            if (!entry.getProjectionFileName().isEmpty()) {
                resolveBookFile(bookDirectory, entry.getProjectionFileName(),
                        "missing projection file");
            }
            return true;
        } catch (IllegalStateException invalid) {
            return false;
        }
    }

    private void refreshBookshelf(List<BookLibraryEntry> entries, String selectedBookId) {
        bookshelf.clear();
        bookshelf.addAll(bookLibraryStore.shelved(entries));
        List<String> labels = new ArrayList<>();
        int selected = 0;
        for (int index = 0; index < bookshelf.size(); index++) {
            BookLibraryEntry entry = bookshelf.get(index);
            labels.add(getString(R.string.bookshelf_item, entry.getDisplayName(),
                    entry.getAnchorOffset(), entry.getSourceBytes() / 1024.0 / 1024.0));
            if (entry.getBookId().equals(selectedBookId)) {
                selected = index;
            }
        }
        if (labels.isEmpty()) {
            labels.add(getString(R.string.bookshelf_empty));
        }
        bookshelfSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels));
        if (!bookshelf.isEmpty()) {
            bookshelfSpinner.setSelection(selected);
        }
        if (readerTitle != null) {
            readerTitle.setText(bookshelf.isEmpty()
                    ? getString(R.string.app_name_short)
                    : bookshelf.get(selected).getDisplayName());
        }
        updateReaderEmptyState(selectedBookId != null && !selectedBookId.isEmpty());
    }

    private void refreshChapters(List<ChapterEntry> chapters, int selectedIndex) {
        currentChapters.clear();
        if (chapters != null) {
            currentChapters.addAll(chapters);
        }
        List<String> labels = new ArrayList<>();
        for (int index = 0; index < currentChapters.size(); index++) {
            ChapterEntry chapter = currentChapters.get(index);
            boolean manual = currentChapterOutline != null
                    && index < currentChapterOutline.getEntries().size()
                    && currentChapterOutline.getEntries().get(index).getOrigin()
                            == ChapterOutlineEntry.Origin.MANUAL;
            labels.add(manual
                    ? getString(R.string.chapter_manual_item, chapter.getTitle())
                    : getString(R.string.chapter_auto_item, chapter.getTitle(),
                            chapter.getConfidencePercent()));
        }
        if (labels.isEmpty()) {
            labels.add(getString(R.string.no_chapters));
        }
        chapterSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels));
        if (!currentChapters.isEmpty()) {
            int bounded = Math.max(0, Math.min(selectedIndex,
                    currentChapters.size() - 1));
            chapterSpinner.setSelection(bounded);
            chapterCursor = bounded;
        } else {
            chapterCursor = -1;
        }
    }

    private BookProgressSnapshot captureBookProgress() {
        if (currentBookId.isEmpty() || activeTextPath == null) {
            return null;
        }
        return new BookProgressSnapshot(currentBookId,
                activeTextPath.getFileName().toString(), currentRevision,
                activeProjectionPath == null ? "" : activeProjectionPath.getFileName().toString(),
                readerSurface.visibleCharacterOffset());
    }

    private void persistBookProgress(Path bookDirectory, BookProgressSnapshot snapshot)
            throws Exception {
        if (snapshot == null) {
            return;
        }
        List<BookLibraryEntry> entries = loadBookLibrary(bookDirectory);
        BookLibraryEntry existing = bookLibraryStore.find(entries, snapshot.bookId);
        if (existing == null) {
            return;
        }
        BookLibraryEntry progressed = existing.withActiveView(
                snapshot.activeFileName, snapshot.activeRevision,
                snapshot.projectionFileName, snapshot.anchorOffset,
                nextOpenedAt(existing));
        saveBookLibrary(bookDirectory, bookLibraryStore.upsert(entries, progressed));
        clearPendingBookProgress(snapshot);
    }

    private List<BookLibraryEntry> applyPendingBookProgress(Path bookDirectory,
            List<BookLibraryEntry> entries) throws Exception {
        SharedPreferences preferences = getSharedPreferences(
                BOOK_PROGRESS_PREFERENCES, MODE_PRIVATE);
        String bookId = preferences.getString(BOOK_PROGRESS_ID_KEY, "");
        String activeRevision = preferences.getString(BOOK_PROGRESS_REVISION_KEY, "");
        int anchor = preferences.getInt(BOOK_PROGRESS_ANCHOR_KEY, -1);
        if (bookId == null || bookId.isEmpty()) {
            return entries;
        }
        BookLibraryEntry entry = bookLibraryStore.find(entries, bookId);
        if (entry == null || !entry.getActiveRevision().equals(activeRevision) || anchor < 0) {
            preferences.edit().clear().apply();
            return entries;
        }
        BookLibraryEntry progressed = entry.withProgress(anchor, nextOpenedAt(entry));
        List<BookLibraryEntry> updated = bookLibraryStore.upsert(entries, progressed);
        saveBookLibrary(bookDirectory, updated);
        preferences.edit().clear().apply();
        return updated;
    }

    private void recordPendingBookProgress(BookProgressSnapshot snapshot) {
        getSharedPreferences(BOOK_PROGRESS_PREFERENCES, MODE_PRIVATE).edit()
                .putString(BOOK_PROGRESS_ID_KEY, snapshot.bookId)
                .putString(BOOK_PROGRESS_REVISION_KEY, snapshot.activeRevision)
                .putInt(BOOK_PROGRESS_ANCHOR_KEY, snapshot.anchorOffset)
                .apply();
    }

    private void clearPendingBookProgress(BookProgressSnapshot snapshot) {
        SharedPreferences preferences = getSharedPreferences(
                BOOK_PROGRESS_PREFERENCES, MODE_PRIVATE);
        if (snapshot.bookId.equals(preferences.getString(BOOK_PROGRESS_ID_KEY, ""))
                && snapshot.activeRevision.equals(preferences.getString(
                        BOOK_PROGRESS_REVISION_KEY, ""))
                && snapshot.anchorOffset == preferences.getInt(
                        BOOK_PROGRESS_ANCHOR_KEY, -1)) {
            preferences.edit().clear().apply();
        }
    }

    private static long nextOpenedAt(BookLibraryEntry entry) {
        long now = Math.max(entry.getImportedAtEpochMillis(), System.currentTimeMillis());
        if (now <= entry.getLastOpenedAtEpochMillis()
                && entry.getLastOpenedAtEpochMillis() < Long.MAX_VALUE) {
            return entry.getLastOpenedAtEpochMillis() + 1L;
        }
        return Math.max(now, entry.getLastOpenedAtEpochMillis());
    }

    private void showBookshelfFailure(int messageResource, Exception error) {
        String message = error.getMessage() == null
                ? error.getClass().getSimpleName() : error.getMessage();
        runOnUiThread(() -> status.setText(getString(messageResource, message)));
    }

    private void showBookshelfTransitionFailure(int messageResource,
            Exception error, long request) {
        String message = error.getMessage() == null
                ? error.getClass().getSimpleName() : error.getMessage();
        runOnUiThread(() -> {
            if (request == bookshelfRequest) {
                libraryTransitionInProgress = false;
                status.setText(getString(messageResource, message));
                refreshEncodingJumpButton();
            }
        });
    }

    private void buildOrResumeDiskIndex(Path bookDirectory, Path normalized, String revision) {
        try {
            Path indexRoot = bookDirectory.resolve("indexes").resolve(revision);
            String normalizedSha256 = DiskDocumentIndexBuilder.computeSha256(normalized);
            DiskIndexBuildResult diskBuild = new DiskDocumentIndexBuilder().build(
                    normalized, indexRoot, revision, normalizedSha256, null);
            DiskDocumentIndex diskIndex = DiskDocumentIndex.openActive(indexRoot);
            runOnUiThread(() -> {
                if (!revision.equals(currentRevision)) {
                    return;
                }
                currentDiskIndex = diskIndex;
                chapterCursor = -1;
                if (currentChapterOutline == null) {
                    refreshChapters(diskIndex.getChapters(), 0);
                }
                status.setText(getString(R.string.index_complete,
                        diskIndex.getChapters().size(), diskBuild.getSegmentCount(),
                        diskBuild.getBuildNanos() / 1_000_000.0,
                        diskBuild.isResumed() ? getString(R.string.index_resumed) : "",
                        diskBuild.isChapterListTruncated()
                                ? getString(R.string.index_chapters_truncated) : ""));
            });
        } catch (Exception indexError) {
            String indexMessage = indexError.getMessage() == null
                    ? indexError.getClass().getSimpleName() : indexError.getMessage();
            runOnUiThread(() -> status.setText(getString(
                    R.string.index_failed_reading_available, indexMessage)));
        }
    }

    private static void writeBookState(Path bookDirectory, BookState state) throws Exception {
        Files.createDirectories(bookDirectory);
        Path temporary = bookDirectory.resolve("last-book.bin.tmp");
        try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(
                Files.newOutputStream(temporary)))) {
            output.writeInt(BOOK_STATE_MAGIC);
            output.writeUTF(state.baseFileName);
            output.writeUTF(state.baseRevision);
            output.writeUTF(state.activeFileName);
            output.writeUTF(state.activeRevision);
            output.writeUTF(state.projectionFileName);
        }
        try {
            Files.move(temporary, bookDirectory.resolve("last-book.bin"),
                    StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, bookDirectory.resolve("last-book.bin"),
                    StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static BookState readBookState(Path bookDirectory) throws Exception {
        Path path = bookDirectory.resolve("last-book.bin");
        if (!Files.isRegularFile(path)) {
            return null;
        }
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(
                Files.newInputStream(path)))) {
            int magic = input.readInt();
            if (magic == BOOK_STATE_MAGIC_V1) {
                String fileName = input.readUTF();
                String revision = input.readUTF();
                validateBookStateValue(fileName, revision, "");
                return new BookState(fileName, revision, fileName, revision, "");
            }
            if (magic != BOOK_STATE_MAGIC) {
                throw new IllegalStateException("书籍恢复状态格式无效");
            }
            BookState state = new BookState(input.readUTF(), input.readUTF(), input.readUTF(),
                    input.readUTF(), input.readUTF());
            validateBookStateValue(state.baseFileName, state.baseRevision, "");
            validateBookStateValue(state.activeFileName, state.activeRevision,
                    state.projectionFileName);
            return state;
        }
    }

    private static void validateBookStateValue(String fileName, String revision,
            String projectionFileName) {
        if (fileName.contains("/") || fileName.contains("\\")
                || projectionFileName.contains("/") || projectionFileName.contains("\\")
                || !revision.matches("[0-9a-f]{64}")) {
            throw new IllegalStateException("书籍恢复状态内容无效");
        }
    }

    private static String readPreview(Path path) throws Exception {
        try (RandomAccessFile input = new RandomAccessFile(path.toFile(), "r")) {
            int length = (int) Math.min(PREVIEW_BYTES, input.length());
            byte[] bytes = new byte[length];
            input.readFully(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private List<RepairRule> loadRepairRules(String bookId) throws Exception {
        RepairRuleStore store = new RepairRuleStore();
        Path catalogPath = new File(getFilesDir(), "rules/catalog.bin").toPath();
        Map<String, List<RepairRule>> catalog = store.loadCatalog(catalogPath);
        List<RepairRule> loaded = new ArrayList<>();
        Set<String> loadedIds = new HashSet<>();
        for (RepairRule rule : catalog.getOrDefault("*", Collections.emptyList())) {
            if (rule.getScope() != RepairScope.ALL_BOOKS) {
                throw new IllegalStateException("全部书规则文件包含错误作用域");
            }
            if (!loadedIds.add(rule.getId())) {
                throw new IllegalStateException("净读规则 ID 重复：" + rule.getId());
            }
            loaded.add(rule);
        }
        for (RepairRule rule : catalog.getOrDefault(bookId, Collections.emptyList())) {
            if (rule.getScope() != RepairScope.CURRENT_BOOK) {
                throw new IllegalStateException("当前书规则文件包含错误作用域");
            }
            if (!loadedIds.add(rule.getId())) {
                throw new IllegalStateException("净读规则 ID 重复：" + rule.getId());
            }
            loaded.add(rule);
        }
        if (loaded.isEmpty()) {
            loaded.addAll(defaultRepairRules());
            saveRepairRules(bookId, loaded);
        }
        return loaded;
    }

    private void saveRepairRules(String bookId, List<RepairRule> inputRules)
            throws Exception {
        List<RepairRule> global = new ArrayList<>();
        List<RepairRule> currentBook = new ArrayList<>();
        List<RepairRule> normalized = normalizedRepairRules(inputRules);
        for (RepairRule rule : normalized) {
            if (rule.getScope() == RepairScope.ALL_BOOKS) {
                global.add(rule);
            } else {
                currentBook.add(rule);
            }
        }
        RepairRuleStore store = new RepairRuleStore();
        Path catalogPath = new File(getFilesDir(), "rules/catalog.bin").toPath();
        Map<String, List<RepairRule>> catalog = store.loadCatalog(catalogPath);
        catalog.put("*", global);
        catalog.put(bookId, currentBook);
        store.saveCatalog(catalogPath, catalog);
    }

    private void persistRepairRules() {
        persistRepairRules(null);
    }

    private void persistRepairRules(Runnable onCurrentGenerationSaved) {
        Path original = baseOriginalPath;
        String bookId = currentBookId;
        if (original == null || bookId.isEmpty()) {
            status.setText(R.string.repair_need_book);
            return;
        }
        List<RepairRule> snapshot = normalizedRepairRules(repairRules);
        long snapshotGeneration = repairRulesGeneration;
        worker.execute(() -> {
            try {
                saveRepairRules(bookId, snapshot);
                runOnUiThread(() -> {
                    repairRulesPersistedGeneration = Math.max(
                            repairRulesPersistedGeneration, snapshotGeneration);
                    if (bookId.equals(currentBookId)
                            && snapshotGeneration == repairRulesGeneration) {
                        if (onCurrentGenerationSaved == null) {
                            status.setText(getString(
                                    R.string.repair_rules_saved, snapshot.size()));
                        } else {
                            onCurrentGenerationSaved.run();
                        }
                    }
                });
            } catch (Exception error) {
                String message = error.getMessage() == null
                        ? error.getClass().getSimpleName() : error.getMessage();
                runOnUiThread(() -> {
                    if (bookId.equals(currentBookId) && original.equals(baseOriginalPath)
                            && snapshotGeneration == repairRulesGeneration) {
                        status.setText(getString(R.string.repair_failed, message));
                    }
                });
            }
        });
    }

    private static List<RepairRule> defaultRepairRules() {
        return Arrays.asList(
                new RepairRule("default-pronoun", "祂", "他", true, 10,
                        RepairScope.CURRENT_BOOK, "人称修复"),
                new RepairRule("default-typo", "錯別字", "错别字", true, 20,
                        RepairScope.CURRENT_BOOK, "繁体错别字示例"));
    }

    private static List<RepairRule> normalizedRepairRules(List<RepairRule> inputRules) {
        List<RepairRule> normalized = new ArrayList<>(inputRules.size());
        for (int index = 0; index < inputRules.size(); index++) {
            RepairRule rule = inputRules.get(index);
            normalized.add(new RepairRule(rule.getId(), rule.getMatchText(),
                    rule.getReplacement(), rule.isEnabled(), (index + 1) * 10,
                    rule.getScope(), rule.getNote()));
        }
        return normalized;
    }

    private void replaceRepairRules(List<RepairRule> rules) {
        repairRules.clear();
        repairRules.addAll(normalizedRepairRules(rules));
        selectedRepairRule = Math.min(selectedRepairRule,
                Math.max(0, repairRules.size() - 1));
        refreshRepairRuleSpinner();
        showSelectedRepairRule();
    }

    private void refreshRepairRuleSpinner() {
        List<String> labels = new ArrayList<>();
        for (int index = 0; index < repairRules.size(); index++) {
            RepairRule rule = repairRules.get(index);
            labels.add(getString(R.string.repair_rule_label, index + 1,
                    rule.isEnabled() ? getString(R.string.repair_enabled)
                            : getString(R.string.repair_disabled),
                    rule.getScope() == RepairScope.ALL_BOOKS
                            ? getString(R.string.repair_scope_all)
                            : getString(R.string.repair_scope_book),
                    rule.getMatchText(), rule.getReplacement()));
        }
        if (labels.isEmpty()) {
            labels.add(getString(R.string.repair_no_rules));
        }
        refreshingRepairRules = true;
        repairRuleSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels));
        if (!repairRules.isEmpty()) {
            repairRuleSpinner.setSelection(selectedRepairRule);
        }
        refreshingRepairRules = false;
        repairRuleSpinner.setContentDescription("repair-rules:" + repairRules.size());
    }

    private void showSelectedRepairRule() {
        if (repairRules.isEmpty()) {
            repairMatchInput.setText("");
            repairReplacementInput.setText("");
            repairNoteInput.setText("");
            return;
        }
        RepairRule rule = repairRules.get(selectedRepairRule);
        repairMatchInput.setText(rule.getMatchText());
        repairReplacementInput.setText(rule.getReplacement());
        repairNoteInput.setText(rule.getNote());
        repairScopeSpinner.setSelection(
                rule.getScope() == RepairScope.ALL_BOOKS ? 1 : 0);
    }

    private RepairRule ruleFromInputs(String id, boolean enabled, int order) {
        String match = repairMatchInput.getText().toString();
        if (match.isEmpty()) {
            throw new IllegalArgumentException(getString(R.string.repair_need_match));
        }
        RepairScope scope = repairScopeSpinner.getSelectedItemPosition() == 1
                ? RepairScope.ALL_BOOKS : RepairScope.CURRENT_BOOK;
        return new RepairRule(id, match, repairReplacementInput.getText().toString(), enabled,
                order, scope, repairNoteInput.getText().toString());
    }

    private void addRepairRule() {
        try {
            if (repairRules.size() >= RepairRuleMerger.MAXIMUM_RULES) {
                status.setText(R.string.repair_rule_limit);
                return;
            }
            if (!beginRuleMutation()) {
                return;
            }
            RepairRule rule = ruleFromInputs("rule-" + Long.toHexString(System.nanoTime()),
                    true, (repairRules.size() + 1) * 10);
            repairRules.add(rule);
            orderRepairRulesAndSelect(rule.getId());
            persistRepairRules();
        } catch (Exception error) {
            showRepairError(error);
        }
    }

    private void saveSelectedRepairRule() {
        if (repairRules.isEmpty()) {
            addRepairRule();
            return;
        }
        try {
            if (!beginRuleMutation()) {
                return;
            }
            RepairRule existing = repairRules.get(selectedRepairRule);
            RepairRule updated = ruleFromInputs(existing.getId(), existing.isEnabled(),
                    existing.getOrder());
            repairRules.set(selectedRepairRule, updated);
            orderRepairRulesAndSelect(updated.getId());
            persistRepairRules();
        } catch (Exception error) {
            showRepairError(error);
        }
    }

    private void toggleSelectedRepairRule() {
        if (repairRules.isEmpty()) {
            return;
        }
        if (!beginRuleMutation()) {
            return;
        }
        RepairRule existing = repairRules.get(selectedRepairRule);
        repairRules.set(selectedRepairRule, new RepairRule(existing.getId(),
                existing.getMatchText(), existing.getReplacement(), !existing.isEnabled(),
                existing.getOrder(), existing.getScope(), existing.getNote()));
        refreshRepairRuleSpinner();
        persistRepairRules();
    }

    private void moveSelectedRepairRuleUp() {
        if (selectedRepairRule <= 0 || repairRules.isEmpty()) {
            return;
        }
        if (repairRules.get(selectedRepairRule - 1).getScope()
                != repairRules.get(selectedRepairRule).getScope()) {
            status.setText(R.string.repair_scope_order_boundary);
            return;
        }
        if (!beginRuleMutation()) {
            return;
        }
        Collections.swap(repairRules, selectedRepairRule, selectedRepairRule - 1);
        selectedRepairRule--;
        replaceRepairRules(new ArrayList<>(repairRules));
        persistRepairRules();
    }

    private void moveSelectedRepairRuleDown() {
        if (repairRules.isEmpty() || selectedRepairRule >= repairRules.size() - 1) {
            return;
        }
        if (repairRules.get(selectedRepairRule + 1).getScope()
                != repairRules.get(selectedRepairRule).getScope()) {
            status.setText(R.string.repair_scope_order_boundary);
            return;
        }
        if (!beginRuleMutation()) {
            return;
        }
        Collections.swap(repairRules, selectedRepairRule, selectedRepairRule + 1);
        selectedRepairRule++;
        replaceRepairRules(new ArrayList<>(repairRules));
        persistRepairRules();
    }

    private void orderRepairRulesAndSelect(String selectedId) {
        Collections.sort(repairRules, Comparator
                .comparingInt((RepairRule rule) -> rule.getScope() == RepairScope.ALL_BOOKS ? 0 : 1)
                .thenComparingInt(RepairRule::getOrder)
                .thenComparing(RepairRule::getId));
        for (int index = 0; index < repairRules.size(); index++) {
            if (repairRules.get(index).getId().equals(selectedId)) {
                selectedRepairRule = index;
                break;
            }
        }
        replaceRepairRules(new ArrayList<>(repairRules));
    }

    private void deleteSelectedRepairRule() {
        if (repairRules.isEmpty()) {
            return;
        }
        if (!beginRuleMutation()) {
            return;
        }
        repairRules.remove(selectedRepairRule);
        selectedRepairRule = Math.min(selectedRepairRule,
                Math.max(0, repairRules.size() - 1));
        refreshRepairRuleSpinner();
        showSelectedRepairRule();
        persistRepairRules();
    }

    private void addCurrentBookmark() {
        if (!libraryActionReady()) {
            return;
        }
        if (currentBookId.isEmpty() || baseOriginalPath == null) {
            status.setText(R.string.repair_need_book);
            return;
        }
        int derivedAnchor = readerSurface.visibleCharacterOffset();
        String label = bookmarkLabel(derivedAnchor);
        String bookId = currentBookId;
        Path projection = activeProjectionPath;
        Path bookDirectory = baseOriginalPath.getParent();
        long request = ++bookmarkRequest;
        worker.execute(() -> {
            try {
                long originalAnchor = derivedAnchor;
                if (projection != null) {
                    try (DiskRepairProjection mapping = DiskRepairProjection.open(projection)) {
                        originalAnchor = mapping.mapDerivedToOriginal(derivedAnchor);
                    }
                }
                int boundedAnchor = (int) Math.min(Integer.MAX_VALUE, originalAnchor);
                Path bookmarkPath = bookmarkPath(bookDirectory, bookId);
                List<BookBookmark> selected = loadBookBookmarks(bookDirectory, bookId);
                for (BookBookmark bookmark : selected) {
                    if (bookmark.getOriginalAnchorOffset() == boundedAnchor) {
                        runOnUiThread(() -> {
                            if (request == bookmarkRequest && bookId.equals(currentBookId)) {
                                status.setText(R.string.bookmark_exists);
                            }
                        });
                        return;
                    }
                }
                if (selected.size() >= 200) {
                    runOnUiThread(() -> {
                        if (request == bookmarkRequest && bookId.equals(currentBookId)) {
                            status.setText(R.string.bookmark_limit);
                        }
                    });
                    return;
                }
                BookBookmark bookmark = new BookBookmark(
                        UUID.randomUUID().toString().replace("-", ""), bookId,
                        boundedAnchor, label, Math.max(1L, System.currentTimeMillis()));
                List<BookBookmark> updated = bookBookmarkStore.upsert(selected, bookmark);
                bookBookmarkStore.save(bookmarkPath, updated);
                List<BookBookmark> bookBookmarks = bookBookmarkStore.forBook(updated, bookId);
                runOnUiThread(() -> {
                    if (request != bookmarkRequest || !bookId.equals(currentBookId)) {
                        return;
                    }
                    refreshBookmarks(bookBookmarks);
                    selectBookmark(bookmark.getBookmarkId());
                    status.setText(getString(R.string.bookmark_added, boundedAnchor));
                });
            } catch (Exception error) {
                showBookmarkFailure(error, request, bookId);
            }
        });
    }

    private void jumpToSelectedBookmark() {
        viewportRequest++;
        cancelAutoScrollResume();
        int selectedIndex = bookmarkSpinner.getSelectedItemPosition();
        if (!libraryActionReady() || currentBookId.isEmpty()) {
            return;
        }
        if (selectedIndex < 0 || selectedIndex >= currentBookmarks.size()) {
            status.setText(R.string.bookmark_empty);
            return;
        }
        BookBookmark bookmark = currentBookmarks.get(selectedIndex);
        String bookId = currentBookId;
        String revision = currentRevision;
        Path projection = activeProjectionPath;
        DiskDocumentIndex diskIndex = currentDiskIndex;
        int capturedWindowStart = currentWindowStart;
        int capturedWindowLength = currentText.length();
        long request = ++bookmarkRequest;
        stopSpeechPlayback(false);
        stopAutoScrollAndCancelResume();
        status.setText(R.string.bookmark_loading);
        worker.execute(() -> {
            try {
                long derivedAnchor = bookmark.getOriginalAnchorOffset();
                if (projection != null) {
                    try (DiskRepairProjection mapping = DiskRepairProjection.open(projection)) {
                        derivedAnchor = mapping.mapOriginalToDerived(derivedAnchor);
                    }
                }
                int target = (int) Math.min(Integer.MAX_VALUE, derivedAnchor);
                if (diskIndex == null) {
                    boolean inWindow = target >= capturedWindowStart
                            && (long) target <= (long) capturedWindowStart
                                    + capturedWindowLength;
                    runOnUiThread(() -> {
                        if (!bookmarkPublishable(request, bookId, revision)) {
                            return;
                        }
                        if (!inWindow) {
                            status.setText(R.string.bookmark_index_pending);
                            return;
                        }
                        readerSurface.scrollToAnchor(Integer.toString(target));
                        status.setText(getString(R.string.bookmark_jumped,
                                bookmark.getOriginalAnchorOffset(), target));
                    });
                    return;
                }
                int boundedTarget = (int) Math.min((long) target,
                        Math.min((long) Integer.MAX_VALUE, diskIndex.getCharacterCount()));
                IndexedTextWindow window = diskIndex.readWindowAround(
                        boundedTarget, INDEX_WINDOW_CHARACTERS);
                DocumentIndex windowIndex = DocumentIndex.build(window.getText(), revision);
                runOnUiThread(() -> {
                    if (!bookmarkPublishable(request, bookId, revision)
                            || diskIndex != currentDiskIndex) {
                        return;
                    }
                    currentText = window.getText();
                    currentWindowStart = window.getStartOffset();
                    currentWindowIndex = windowIndex;
                    readerSurface.setDocumentWindow(currentText, revision, currentWindowStart);
                    readerSurface.scrollToAnchor(Integer.toString(boundedTarget));
                    status.setText(getString(R.string.bookmark_jumped,
                            bookmark.getOriginalAnchorOffset(), boundedTarget));
                });
            } catch (Exception error) {
                showBookmarkFailure(error, request, bookId);
            }
        });
    }

    private void deleteSelectedBookmark() {
        int selectedIndex = bookmarkSpinner.getSelectedItemPosition();
        if (!libraryActionReady() || currentBookId.isEmpty()) {
            return;
        }
        if (selectedIndex < 0 || selectedIndex >= currentBookmarks.size()) {
            status.setText(R.string.bookmark_empty);
            return;
        }
        BookBookmark bookmark = currentBookmarks.get(selectedIndex);
        String bookId = currentBookId;
        Path bookDirectory = baseOriginalPath.getParent();
        long request = ++bookmarkRequest;
        worker.execute(() -> {
            try {
                Path bookmarkPath = bookmarkPath(bookDirectory, bookId);
                List<BookBookmark> all = loadBookBookmarks(bookDirectory, bookId);
                List<BookBookmark> updated = bookBookmarkStore.remove(
                        all, bookmark.getBookmarkId());
                bookBookmarkStore.save(bookmarkPath, updated);
                List<BookBookmark> bookBookmarks = bookBookmarkStore.forBook(updated, bookId);
                runOnUiThread(() -> {
                    if (request != bookmarkRequest || !bookId.equals(currentBookId)) {
                        return;
                    }
                    refreshBookmarks(bookBookmarks);
                    status.setText(getString(R.string.bookmark_deleted,
                            bookmark.getOriginalAnchorOffset()));
                });
            } catch (Exception error) {
                showBookmarkFailure(error, request, bookId);
            }
        });
    }

    private boolean bookmarkPublishable(long request, String bookId, String revision) {
        return request == bookmarkRequest && bookId.equals(currentBookId)
                && revision.equals(currentRevision) && !libraryTransitionInProgress;
    }

    private void showBookmarkFailure(Exception error, long request, String bookId) {
        String message = safeMessage(error);
        runOnUiThread(() -> {
            if (request == bookmarkRequest && bookId.equals(currentBookId)) {
                status.setText(getString(R.string.bookmark_failed, message));
            }
        });
    }

    private String bookmarkLabel(int globalAnchor) {
        long relativeAnchor = (long) globalAnchor - currentWindowStart;
        int localAnchor = (int) Math.max(0L, Math.min(
                (long) currentText.length(), relativeAnchor));
        int start = Math.max(0, localAnchor - 24);
        int end = Math.min(currentText.length(), localAnchor + 56);
        if (start > 0 && Character.isLowSurrogate(currentText.charAt(start))) {
            start--;
        }
        if (end < currentText.length() && end > 0
                && Character.isHighSurrogate(currentText.charAt(end - 1))) {
            end++;
        }
        String label = currentText.substring(start, end)
                .replaceAll("[\\p{Cc}\\s]+", " ").trim();
        if (label.isEmpty()) {
            label = "位置 " + globalAnchor;
        }
        if (label.length() <= 160) {
            return label;
        }
        int truncateAt = 160;
        if (Character.isHighSurrogate(label.charAt(truncateAt - 1))) {
            truncateAt--;
        }
        return label.substring(0, truncateAt);
    }

    private void refreshBookmarks(List<BookBookmark> bookmarks) {
        currentBookmarks.clear();
        currentBookmarks.addAll(bookmarks);
        List<String> labels = new ArrayList<>();
        for (BookBookmark bookmark : currentBookmarks) {
            labels.add(getString(R.string.bookmark_item,
                    bookmark.getOriginalAnchorOffset(), bookmark.getLabel()));
        }
        if (labels.isEmpty()) {
            labels.add(getString(R.string.bookmark_empty));
        }
        bookmarkSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels));
    }

    private void selectBookmark(String bookmarkId) {
        for (int index = 0; index < currentBookmarks.size(); index++) {
            if (currentBookmarks.get(index).getBookmarkId().equals(bookmarkId)) {
                bookmarkSpinner.setSelection(index);
                return;
            }
        }
    }

    private void searchCurrentWindow(String query) {
        viewportRequest++;
        cancelAutoScrollResume();
        if (currentWindowIndex == null) {
            status.setText(R.string.import_before_searching);
            return;
        }
        String normalizedQuery = query == null ? "" : query.trim();
        if (normalizedQuery.isEmpty()) {
            status.setText(R.string.enter_search_query);
            return;
        }
        if (normalizedQuery.length() > DiskDocumentIndexBuilder.MAX_QUERY_CHARACTERS) {
            status.setText(getString(R.string.reader_selection_search_too_long,
                    DiskDocumentIndexBuilder.MAX_QUERY_CHARACTERS));
            return;
        }
        stopSpeechPlayback(false);
        DiskDocumentIndex diskIndex = currentDiskIndex;
        if (diskIndex != null) {
            String revision = currentRevision;
            status.setText(R.string.searching_full_book);
            worker.execute(() -> searchDiskIndex(diskIndex, revision, normalizedQuery));
            return;
        }
        try {
            List<SearchHit> hits = currentWindowIndex.search(normalizedQuery,
                    SEARCH_RESULT_LIMIT, currentRevision);
            if (hits.isEmpty()) {
                status.setText(getString(R.string.search_no_result, normalizedQuery));
                return;
            }
            showSearchResults(normalizedQuery, hits, null, currentRevision,
                    currentWindowStart);
        } catch (IllegalStateException stale) {
            status.setText(R.string.index_rebuilding);
        }
    }

    private void handleReaderSelectionAction(ReaderSurfaceView.SelectionAction action,
            ReaderTextSelection selection) {
        if (!selectionMatchesCurrentWindow(selection, currentRevision, currentBookId)) {
            status.setText(R.string.reader_selection_stale);
            return;
        }
        switch (action) {
            case COPY:
                ClipboardManager clipboard = (ClipboardManager) getSystemService(
                        Context.CLIPBOARD_SERVICE);
                if (clipboard == null) {
                    status.setText(R.string.reader_selection_copy_failed);
                    return;
                }
                clipboard.setPrimaryClip(ClipData.newPlainText(
                        getString(R.string.reader_selection_clip_label),
                        selection.getSelectedText()));
                status.setText(getString(R.string.reader_selection_copied,
                        selection.length()));
                break;
            case SEARCH:
                if (selection.length() > DiskDocumentIndexBuilder.MAX_QUERY_CHARACTERS) {
                    status.setText(getString(R.string.reader_selection_search_too_long,
                            DiskDocumentIndexBuilder.MAX_QUERY_CHARACTERS));
                    return;
                }
                searchInput.setText(selection.getSelectedText());
                searchInput.setSelection(searchInput.length());
                searchCurrentWindow(selection.getSelectedText());
                break;
            case CREATE_RULE:
                resolveAndPromptRuleFromSelection(selection);
                break;
            default:
                throw new IllegalStateException("unknown selection action");
        }
    }

    private void resolveAndPromptRuleFromSelection(ReaderTextSelection selection) {
        String revision = currentRevision;
        String bookId = currentBookId;
        Path original = baseOriginalPath;
        Path projection = activeProjectionPath;
        String originalRevision = baseRevision;
        if (bookId.isEmpty()) {
            status.setText(R.string.repair_need_book);
            return;
        }
        if (!readerSelectionRuleActionReady()) {
            status.setText(R.string.reader_selection_rule_busy);
            return;
        }
        if (original == null || originalRevision.isEmpty()) {
            status.setText(R.string.reader_selection_stale);
            return;
        }
        if (selection.length() > RepairRule.MAXIMUM_FIELD_CHARACTERS) {
            status.setText(getString(R.string.reader_selection_rule_too_long,
                    RepairRule.MAXIMUM_FIELD_CHARACTERS));
            return;
        }
        if (selection.getSelectedText().trim().isEmpty()) {
            status.setText(R.string.reader_selection_rule_whitespace);
            return;
        }
        if (repairRules.size() >= RepairRuleMerger.MAXIMUM_RULES) {
            status.setText(R.string.repair_rule_limit);
            return;
        }

        long request = ++selectionRuleRequest;
        if (projection == null) {
            showCreateRuleDialog(selection, selection.getSelectedText(),
                    selection.getStartOffset(), revision, bookId, original,
                    originalRevision, null, request);
            return;
        }
        status.setText(R.string.reader_selection_rule_resolving);
        worker.execute(() -> {
            try {
                TextOffsetRange originalRange;
                try (DiskRepairProjection mapping =
                        DiskRepairProjection.open(projection)) {
                    originalRange = mapping.mapDerivedRangeToOriginal(
                            selection.getStartOffset(), selection.getEndOffset());
                }
                if (originalRange.length() <= 0
                        || originalRange.length() > RepairRule.MAXIMUM_FIELD_CHARACTERS
                        || originalRange.getEndOffset() > Integer.MAX_VALUE) {
                    throw new IllegalArgumentException(getString(
                            R.string.reader_selection_rule_mapped_too_long,
                            RepairRule.MAXIMUM_FIELD_CHARACTERS));
                }
                String originalSha256 = DiskDocumentIndexBuilder.computeSha256(original);
                Path indexRoot = original.getParent().resolve("indexes")
                        .resolve(originalRevision);
                new DiskDocumentIndexBuilder().build(original, indexRoot,
                        originalRevision, originalSha256, null);
                DiskDocumentIndex originalIndex = DiskDocumentIndex.openActive(indexRoot);
                int originalStart = (int) originalRange.getStartOffset();
                String matchText = originalIndex.readTextRange(originalStart,
                        (int) originalRange.getEndOffset(),
                        RepairRule.MAXIMUM_FIELD_CHARACTERS);
                runOnUiThread(() -> showCreateRuleDialog(selection, matchText,
                        originalStart, revision, bookId, original,
                        originalRevision, projection, request));
            } catch (Exception error) {
                String message = safeMessage(error);
                runOnUiThread(() -> {
                    if (request == selectionRuleRequest
                            && selectionMatchesCurrentWindow(
                                    selection, revision, bookId)) {
                        status.setText(getString(
                                R.string.reader_selection_rule_resolve_failed, message));
                    }
                });
            }
        });
    }

    private void showCreateRuleDialog(ReaderTextSelection visibleSelection,
            String matchText, int originalStart, String expectedRevision,
            String expectedBookId, Path expectedOriginal,
            String expectedOriginalRevision, Path expectedProjection,
            long request) {
        if (request != selectionRuleRequest
                || !ruleCreationContextMatches(visibleSelection,
                        expectedRevision, expectedBookId, expectedOriginal,
                        expectedOriginalRevision, expectedProjection)) {
            status.setText(R.string.reader_selection_stale);
            return;
        }
        if (matchText.isEmpty() || matchText.length() > RepairRule.MAXIMUM_FIELD_CHARACTERS
                || matchText.trim().isEmpty()) {
            status.setText(R.string.reader_selection_rule_whitespace);
            return;
        }

        LinearLayout editor = new LinearLayout(this);
        editor.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(16 * getResources().getDisplayMetrics().density);
        editor.setPadding(padding, padding, padding, 0);
        TextView match = new TextView(this);
        match.setText(matchText.equals(visibleSelection.getSelectedText())
                ? getString(R.string.reader_selection_rule_match, matchText)
                : getString(R.string.reader_selection_rule_match_mapped,
                        visibleSelection.getSelectedText(), matchText));
        match.setTextIsSelectable(true);
        editor.addView(match);
        EditText replacement = new EditText(this);
        replacement.setHint(R.string.repair_replacement_hint);
        replacement.setFilters(new InputFilter[] {
                new InputFilter.LengthFilter(RepairRule.MAXIMUM_FIELD_CHARACTERS)});
        editor.addView(replacement);
        Spinner scope = new Spinner(this);
        scope.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item,
                new String[] {getString(R.string.repair_scope_book),
                        getString(R.string.repair_scope_all)}));
        editor.addView(scope);

        new AlertDialog.Builder(this)
                .setTitle(R.string.reader_selection_create_rule)
                .setView(editor)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.reader_selection_rule_save,
                        (dialog, which) -> createRuleFromSelection(visibleSelection,
                                matchText, originalStart, expectedRevision,
                                expectedBookId, expectedOriginal,
                                expectedOriginalRevision, expectedProjection, request,
                                replacement.getText().toString(),
                                scope.getSelectedItemPosition() == 1
                                        ? RepairScope.ALL_BOOKS
                                        : RepairScope.CURRENT_BOOK))
                .show();
    }

    private void createRuleFromSelection(ReaderTextSelection visibleSelection,
            String matchText, int originalStart, String expectedRevision,
            String expectedBookId, Path expectedOriginal,
            String expectedOriginalRevision, Path expectedProjection, long request,
            String replacement, RepairScope scope) {
        if (request != selectionRuleRequest
                || !ruleCreationContextMatches(visibleSelection,
                        expectedRevision, expectedBookId, expectedOriginal,
                        expectedOriginalRevision, expectedProjection)) {
            status.setText(R.string.reader_selection_stale);
            return;
        }
        try {
            if (repairRules.size() >= RepairRuleMerger.MAXIMUM_RULES) {
                status.setText(R.string.repair_rule_limit);
                return;
            }
            if (!beginRuleMutation()) {
                return;
            }
            RepairRule rule = new RepairRule(
                    "rule-" + Long.toHexString(System.nanoTime()),
                    matchText, replacement, true,
                    (repairRules.size() + 1) * 10, scope,
                    getString(R.string.reader_selection_rule_note,
                            originalStart));
            repairRules.add(rule);
            orderRepairRulesAndSelect(rule.getId());
            persistRepairRules(() -> status.setText(getString(
                    R.string.reader_selection_rule_saved,
                    rule.getMatchText().length(), rule.getReplacement().length())));
        } catch (Exception error) {
            showRepairError(error);
        }
    }

    private boolean selectionMatchesCurrentWindow(ReaderTextSelection selection,
            String expectedRevision, String expectedBookId) {
        if (selection == null || expectedRevision == null || expectedBookId == null
                || !expectedRevision.equals(currentRevision)
                || !expectedBookId.equals(currentBookId)) {
            return false;
        }
        long localStartLong = (long) selection.getStartOffset() - currentWindowStart;
        long localEndLong = (long) selection.getEndOffset() - currentWindowStart;
        if (localStartLong < 0 || localEndLong > currentText.length()
                || localStartLong >= localEndLong) {
            return false;
        }
        int localStart = (int) localStartLong;
        int localEnd = (int) localEndLong;
        return currentText.substring(localStart, localEnd)
                .equals(selection.getSelectedText());
    }

    private boolean readerSelectionRuleActionReady() {
        return !currentBookId.isEmpty() && !libraryTransitionInProgress
                && !chapterOutlineTransitionInProgress && !repairApplying
                && !exportInProgress;
    }

    private boolean ruleCreationContextMatches(ReaderTextSelection selection,
            String expectedRevision, String expectedBookId, Path expectedOriginal,
            String expectedOriginalRevision, Path expectedProjection) {
        return readerSelectionRuleActionReady()
                && selectionMatchesCurrentWindow(
                        selection, expectedRevision, expectedBookId)
                && expectedOriginal != null && expectedOriginal.equals(baseOriginalPath)
                && expectedOriginalRevision.equals(baseRevision)
                && (expectedProjection == null
                        ? activeProjectionPath == null
                        : expectedProjection.equals(activeProjectionPath));
    }

    private void loadAdjacentReaderWindow(boolean forward, int anchorOffset,
            boolean resumeAutoScroll) {
        cancelAutoScrollResume();
        autoScrollWindowContinuationPending = resumeAutoScroll;
        DiskDocumentIndex diskIndex = currentDiskIndex;
        String revision = currentRevision;
        if (diskIndex == null || !revision.equals(diskIndex.getRevision())) {
            autoScrollWindowContinuationPending = false;
            status.setText(R.string.index_rebuilding);
            return;
        }
        long target;
        if (forward) {
            target = (long) currentWindowStart + currentText.length();
            if (target >= diskIndex.getCharacterCount()) {
                autoScrollWindowContinuationPending = false;
                status.setText(R.string.reader_document_end);
                return;
            }
        } else {
            if (currentWindowStart <= 0) {
                autoScrollWindowContinuationPending = false;
                status.setText(R.string.reader_document_start);
                return;
            }
            target = currentWindowStart - 1L;
        }
        int boundedTarget = (int) Math.max(0L,
                Math.min((long) Integer.MAX_VALUE, target));
        long request = ++viewportRequest;
        status.setText(R.string.reader_window_loading);
        worker.execute(() -> {
            try {
                IndexedTextWindow window = diskIndex.readWindowAround(
                        boundedTarget, INDEX_WINDOW_CHARACTERS);
                DocumentIndex windowIndex = DocumentIndex.build(
                        window.getText(), revision);
                runOnUiThread(() -> {
                    if (request != viewportRequest
                            || !revision.equals(currentRevision)
                            || diskIndex != currentDiskIndex) {
                        return;
                    }
                    currentText = window.getText();
                    currentWindowStart = window.getStartOffset();
                    currentWindowIndex = windowIndex;
                    readerSurface.setDocumentWindow(
                            currentText, revision, currentWindowStart);
                    readerSurface.setMode(surfaceMode(
                            readerNavigationSettings.getReadingMode()));
                    readerSurface.scrollToAnchor(Integer.toString(boundedTarget));
                    if (resumeAutoScroll
                            && autoScrollWindowContinuationPending
                            && readerNavigationSettings.getReadingMode()
                                    == ReaderNavigationSettings.ReadingMode.CONTINUOUS_SCROLL
                            && speechQueue == null) {
                        autoScrollWindowContinuationPending = false;
                        readerSurface.toggleAutoScroll();
                    } else {
                        autoScrollWindowContinuationPending = false;
                        status.setText(getString(forward
                                        ? R.string.reader_page_forward
                                        : R.string.reader_page_backward,
                                readerSurface.visibleCharacterOffset()));
                    }
                });
            } catch (Exception error) {
                String message = safeMessage(error);
                runOnUiThread(() -> {
                    if (request == viewportRequest
                            && revision.equals(currentRevision)) {
                        autoScrollWindowContinuationPending = false;
                        status.setText(getString(
                                R.string.reader_window_load_failed, message));
                    }
                });
            }
        });
    }

    private void searchDiskIndex(DiskDocumentIndex diskIndex, String revision, String query) {
        try {
            List<SearchHit> hits = diskIndex.search(query, SEARCH_RESULT_LIMIT, revision);
            if (hits.isEmpty()) {
                runOnUiThread(() -> {
                    if (revision.equals(currentRevision)) {
                        status.setText(getString(R.string.search_no_result, query));
                    }
                });
                return;
            }
            runOnUiThread(() -> {
                if (!revision.equals(currentRevision) || diskIndex != currentDiskIndex) {
                    return;
                }
                showSearchResults(query, hits, diskIndex, revision, 0);
            });
        } catch (Exception error) {
            String message = error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage();
            runOnUiThread(() -> status.setText(getString(R.string.index_read_failed, message)));
        }
    }

    private void showSearchResults(String query, List<SearchHit> hits,
            DiskDocumentIndex diskIndex, String revision, int windowStart) {
        String[] labels = new String[hits.size()];
        for (int index = 0; index < hits.size(); index++) {
            SearchHit hit = hits.get(index);
            long globalOffset = (long) windowStart + hit.getStartOffset();
            labels[index] = getString(R.string.search_result_item, globalOffset,
                    hit.getContext().replace('\n', ' ').trim());
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(getString(R.string.search_results_title, query, hits.size()))
                .setItems(labels, (dialog, which) -> {
                    SearchHit selected = hits.get(which);
                    if (diskIndex == null) {
                        int globalOffset = windowStart + selected.getStartOffset();
                        stopAutoScrollAndCancelResume();
                        readerSurface.scrollToAnchor(Integer.toString(globalOffset));
                        status.setText(getString(R.string.search_result, globalOffset,
                                selected.getContext().replace('\n', ' ')));
                    } else {
                        openDiskSearchHit(diskIndex, revision, selected);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null);
        if (hits.size() == SEARCH_RESULT_LIMIT) {
            builder.setNeutralButton(getString(R.string.search_results_limited,
                    SEARCH_RESULT_LIMIT), null);
        }
        showAsBottomPanel(builder.create());
    }

    private void openDiskSearchHit(DiskDocumentIndex diskIndex, String revision,
            SearchHit hit) {
        status.setText(R.string.searching_full_book);
        worker.execute(() -> {
            try {
                IndexedTextWindow window = diskIndex.readWindowAround(
                        hit.getStartOffset(), INDEX_WINDOW_CHARACTERS);
                DocumentIndex windowIndex = DocumentIndex.build(window.getText(), revision);
                runOnUiThread(() -> {
                    if (!revision.equals(currentRevision)
                            || diskIndex != currentDiskIndex) {
                        return;
                    }
                    currentText = window.getText();
                    currentWindowStart = window.getStartOffset();
                    currentWindowIndex = windowIndex;
                    readerSurface.setDocumentWindow(currentText, revision,
                            currentWindowStart);
                    readerSurface.scrollToAnchor(Integer.toString(hit.getStartOffset()));
                    status.setText(getString(R.string.search_result,
                            hit.getStartOffset(), hit.getContext().replace('\n', ' ')));
                });
            } catch (Exception error) {
                String message = safeMessage(error);
                runOnUiThread(() -> status.setText(
                        getString(R.string.index_read_failed, message)));
            }
        });
    }

    private void jumpToNextChapter() {
        cancelAutoScrollResume();
        if (currentWindowIndex == null) {
            status.setText(R.string.import_before_searching);
            return;
        }
        if (currentChapters.isEmpty()) {
            status.setText(R.string.no_chapters);
            return;
        }
        openChapterAt((chapterCursor + 1) % currentChapters.size());
    }

    private void openSelectedChapter() {
        int selected = chapterSpinner.getSelectedItemPosition();
        if (selected < 0 || selected >= currentChapters.size()) {
            status.setText(R.string.no_chapters);
            return;
        }
        openChapterAt(selected);
    }

    private void openChapterAt(int index) {
        viewportRequest++;
        cancelAutoScrollResume();
        if (index < 0 || index >= currentChapters.size()) {
            status.setText(R.string.no_chapters);
            return;
        }
        stopSpeechPlayback(false);
        chapterCursor = index;
        chapterSpinner.setSelection(index);
        ChapterEntry chapter = currentChapters.get(index);
        String chapterStatus = getString(R.string.chapter_result,
                chapterCursor + 1, currentChapters.size(), chapter.getTitle(),
                chapter.getConfidencePercent());
        DiskDocumentIndex diskIndex = currentDiskIndex;
        if (diskIndex == null) {
            stopAutoScrollAndCancelResume();
            readerSurface.scrollToAnchor(Integer.toString(chapter.getCharacterOffset()));
            status.setText(chapterStatus);
            return;
        }
        String revision = currentRevision;
        status.setText(R.string.loading_chapter_window);
        worker.execute(() -> loadChapterWindow(
                diskIndex, revision, chapter.getCharacterOffset(), chapterStatus));
    }

    private void promptRenameSelectedChapter() {
        int selected = selectedChapterForEdit();
        if (selected < 0) {
            return;
        }
        EditText title = new EditText(this);
        title.setHint(R.string.chapter_title_hint);
        title.setSingleLine(true);
        title.setText(currentChapters.get(selected).getTitle());
        title.setSelection(title.getText().length());
        new AlertDialog.Builder(this)
                .setTitle(R.string.chapter_rename_title)
                .setView(title)
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        startChapterOutlineEdit(ChapterEditAction.RENAME, selected,
                                -1, title.getText().toString().trim()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void promptSplitChapterAtCurrentPosition() {
        if (!chapterOutlineEditReady()) {
            return;
        }
        int derivedAnchor = readerSurface.visibleCharacterOffset();
        EditText title = new EditText(this);
        title.setHint(R.string.chapter_title_hint);
        title.setSingleLine(true);
        new AlertDialog.Builder(this)
                .setTitle(R.string.chapter_split_title)
                .setView(title)
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        startChapterOutlineEdit(ChapterEditAction.SPLIT, -1,
                                derivedAnchor, title.getText().toString().trim()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void confirmMergeSelectedChapter() {
        int selected = selectedChapterForEdit();
        if (selected < 0) {
            return;
        }
        if (selected + 1 >= currentChapters.size()) {
            status.setText(R.string.chapter_outline_stale);
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle(R.string.chapter_merge_title)
                .setMessage(getString(R.string.chapter_merge_message,
                        currentChapters.get(selected).getTitle()))
                .setPositiveButton(android.R.string.ok, (dialog, which) ->
                        startChapterOutlineEdit(ChapterEditAction.MERGE_NEXT,
                                selected, -1, ""))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private int selectedChapterForEdit() {
        if (!chapterOutlineEditReady()) {
            return -1;
        }
        int selected = chapterSpinner.getSelectedItemPosition();
        if (selected < 0 || selected >= currentChapters.size()) {
            status.setText(R.string.no_chapters);
            return -1;
        }
        return selected;
    }

    private boolean chapterOutlineEditReady() {
        if (libraryTransitionInProgress || repairApplying) {
            status.setText(R.string.repair_busy);
            return false;
        }
        if (chapterOutlineTransitionInProgress) {
            status.setText(R.string.chapter_outline_busy);
            return false;
        }
        if (currentBookId.isEmpty() || baseOriginalPath == null
                || baseRevision.isEmpty()) {
            status.setText(R.string.import_before_searching);
            return false;
        }
        return true;
    }

    private void startChapterOutlineEdit(ChapterEditAction action, int selected,
            int derivedAnchor, String title) {
        if (!chapterOutlineEditReady()) {
            return;
        }
        String expectedBookId = currentBookId;
        String expectedBaseRevision = baseRevision;
        String expectedActiveRevision = currentRevision;
        Path expectedOriginal = baseOriginalPath;
        Path expectedProjection = activeProjectionPath;
        Path bookDirectory = new File(getFilesDir(), "books").toPath();
        long request = ++chapterOutlineRequest;
        chapterOutlineTransitionInProgress = true;
        status.setText(R.string.chapter_outline_busy);
        worker.execute(() -> {
            try {
                if (request != chapterOutlineRequest) {
                    return;
                }
                ChapterOutline outline = loadChapterOutline(bookDirectory,
                        expectedBookId, expectedBaseRevision);
                if (outline == null) {
                    String originalSha256 = DiskDocumentIndexBuilder.computeSha256(
                            expectedOriginal);
                    Path baseIndexRoot = bookDirectory.resolve("indexes")
                            .resolve(expectedBaseRevision);
                    new DiskDocumentIndexBuilder().build(expectedOriginal,
                            baseIndexRoot, expectedBaseRevision, originalSha256, null);
                    DiskDocumentIndex baseIndex =
                            DiskDocumentIndex.openActive(baseIndexRoot);
                    outline = ChapterOutline.fromDetected(expectedBaseRevision,
                            baseIndex.getChapters());
                }
                int originalAnchor = derivedAnchor;
                if (action == ChapterEditAction.SPLIT
                        && expectedProjection != null) {
                    try (DiskRepairProjection mapping =
                            DiskRepairProjection.open(expectedProjection)) {
                        long mapped = mapping.mapDerivedToOriginal(derivedAnchor);
                        if (mapped < 0 || mapped > Integer.MAX_VALUE) {
                            throw new IllegalStateException("章节拆分位置超出支持范围");
                        }
                        originalAnchor = (int) mapped;
                    }
                }
                if (action == ChapterEditAction.SPLIT) {
                    String originalSha256 = DiskDocumentIndexBuilder.computeSha256(
                            expectedOriginal);
                    Path baseIndexRoot = bookDirectory.resolve("indexes")
                            .resolve(expectedBaseRevision);
                    new DiskDocumentIndexBuilder().build(expectedOriginal,
                            baseIndexRoot, expectedBaseRevision, originalSha256, null);
                    DiskDocumentIndex baseIndex =
                            DiskDocumentIndex.openActive(baseIndexRoot);
                    if ((long) originalAnchor >= baseIndex.getCharacterCount()) {
                        throw new IllegalArgumentException("不能在文末创建空章节");
                    }
                }
                ChapterOutline updated;
                if (action == ChapterEditAction.RENAME) {
                    updated = outline.rename(selected, title);
                } else if (action == ChapterEditAction.SPLIT) {
                    updated = outline.split(originalAnchor, title);
                } else {
                    updated = outline.mergeWithNext(selected);
                }
                List<ChapterEntry> mapped = mapChapterOutline(
                        updated, expectedProjection);
                chapterOutlineStore.save(chapterOutlinePath(bookDirectory,
                        expectedBookId, expectedBaseRevision), updated);
                int targetSelection = action == ChapterEditAction.SPLIT
                        ? chapterIndexAtOriginalOffset(updated, originalAnchor)
                        : Math.max(0, Math.min(selected, mapped.size() - 1));
                ChapterOutline finalOutline = updated;
                runOnUiThread(() -> {
                    if (request != chapterOutlineRequest
                            || !expectedBookId.equals(currentBookId)
                            || !expectedBaseRevision.equals(baseRevision)
                            || !expectedActiveRevision.equals(currentRevision)) {
                        return;
                    }
                    currentChapterOutline = finalOutline;
                    refreshChapters(mapped, targetSelection);
                    chapterOutlineTransitionInProgress = false;
                    status.setText(getString(R.string.chapter_outline_saved,
                            mapped.size()));
                });
            } catch (Exception error) {
                String message = safeMessage(error);
                runOnUiThread(() -> {
                    if (request == chapterOutlineRequest) {
                        chapterOutlineTransitionInProgress = false;
                        status.setText(getString(
                                R.string.chapter_outline_failed, message));
                    }
                });
            }
        });
    }

    private static int chapterIndexAtOriginalOffset(ChapterOutline outline,
            int originalOffset) {
        for (int index = 0; index < outline.getEntries().size(); index++) {
            if (outline.getEntries().get(index).getOriginalCharacterOffset()
                    == originalOffset) {
                return index;
            }
        }
        return 0;
    }

    private void loadChapterWindow(DiskDocumentIndex diskIndex, String revision, int offset,
            String chapterStatus) {
        try {
            IndexedTextWindow window = diskIndex.readWindowAround(offset, INDEX_WINDOW_CHARACTERS);
            DocumentIndex windowIndex = DocumentIndex.build(window.getText(), revision);
            runOnUiThread(() -> {
                if (!revision.equals(currentRevision) || diskIndex != currentDiskIndex) {
                    return;
                }
                currentText = window.getText();
                currentWindowStart = window.getStartOffset();
                currentWindowIndex = windowIndex;
                readerSurface.setDocumentWindow(currentText, revision, currentWindowStart);
                readerSurface.scrollToAnchor(Integer.toString(offset));
                status.setText(chapterStatus);
            });
        } catch (Exception error) {
            String message = error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage();
            runOnUiThread(() -> status.setText(getString(R.string.index_read_failed, message)));
        }
    }

    private void previewRepairFromStart() {
        repairPreviewOffset = 0;
        previewRepair();
    }

    private void previewRepair() {
        if (repairApplying) {
            status.setText(R.string.repair_busy);
            return;
        }
        Path original = baseOriginalPath;
        if (original == null) {
            status.setText(R.string.repair_need_book);
            return;
        }
        if (repairRulesPersistedGeneration != repairRulesGeneration) {
            status.setText(R.string.repair_rules_not_saved);
            return;
        }
        List<RepairRule> rules = normalizedRepairRules(repairRules);
        boolean anyEnabled = false;
        for (RepairRule rule : rules) {
            anyEnabled |= rule.isEnabled();
        }
        if (!anyEnabled) {
            status.setText(R.string.repair_need_enabled_rule);
            return;
        }
        String expectedBaseRevision = baseRevision;
        long expectedRulesGeneration = repairRulesGeneration;
        long requestedPreviewOffset = repairPreviewOffset;
        long pageRequest = ++repairPreviewPageRequest;
        repairRangeRequest++;
        RepairSelection selection = RepairSelection.excluding(
                new ArrayList<>(excludedOccurrences));
        invalidatePendingRepair();
        status.setText(R.string.repair_previewing);
        worker.execute(() -> {
            try {
                Path bookDirectory = original.getParent();
                String candidateId = Long.toHexString(System.nanoTime());
                Path derived = bookDirectory.resolve("repair-" + candidateId + ".utf8.txt");
                Path projection = bookDirectory.resolve("repair-" + candidateId + ".projection.bin");
                Path candidates = bookDirectory.resolve("repair-" + candidateId + ".candidates.bin");
                RepairFileResult result = new RepairFilePipeline().apply(original, derived,
                        projection, candidates, rules, selection, requestedPreviewOffset,
                        REPAIR_PREVIEW_PAGE_SIZE);
                String sample = previewSummary(result.getPreviews());
                runOnUiThread(() -> {
                    if (!expectedBaseRevision.equals(baseRevision)
                            || !original.equals(baseOriginalPath)
                            || expectedRulesGeneration != repairRulesGeneration
                            || pageRequest != repairPreviewPageRequest) {
                        worker.execute(() -> deleteRepairCandidate(result));
                        return;
                    }
                    PendingRepair previous = pendingRepair;
                    pendingRepair = new PendingRepair(result);
                    repairPreviewOffset = requestedPreviewOffset;
                    refreshOccurrenceSpinner(result.getPreviews());
                    updateRepairPreviewPageStatus(result.getCandidateMatchCount());
                    if (previous != null) {
                        worker.execute(() -> deleteRepairCandidate(previous.result));
                    }
                    status.setText(getString(R.string.repair_preview_ready,
                            result.getMatchCount(), result.getWarnings().size(),
                            ruleMatchSummary(rules, result.getRuleMatchCounts(),
                                    result.getRuleCandidateCounts()), sample));
                    requestArtifactPrune();
                });
            } catch (Exception error) {
                String message = error.getMessage() == null
                        ? error.getClass().getSimpleName() : error.getMessage();
                runOnUiThread(() -> {
                    if (expectedBaseRevision.equals(baseRevision)
                            && original.equals(baseOriginalPath)
                            && expectedRulesGeneration == repairRulesGeneration
                            && pageRequest == repairPreviewPageRequest) {
                        status.setText(getString(R.string.repair_failed, message));
                    }
                });
            }
        });
    }

    private void applyPendingRepair() {
        cancelAutoScrollResume();
        if (repairApplying || chapterOutlineTransitionInProgress) {
            status.setText(R.string.repair_busy);
            return;
        }
        PendingRepair pending = pendingRepair;
        if (pending == null) {
            status.setText(R.string.repair_need_preview);
            return;
        }
        if (pending.result.getMatchCount() == 0) {
            status.setText(R.string.repair_no_matches);
            return;
        }
        long currentAnchor = readerSurface.visibleCharacterOffset();
        Path currentProjection = activeProjectionPath;
        Path original = baseOriginalPath;
        String originalRevision = baseRevision;
        String bookId = currentBookId;
        repairApplying = true;
        repairRangeRequest++;
        stopSpeechPlayback(false);
        status.setText(R.string.repair_applying);
        worker.execute(() -> {
            try {
                long originalAnchor = currentAnchor;
                if (currentProjection != null) {
                    try (DiskRepairProjection mapping = DiskRepairProjection.open(currentProjection)) {
                        originalAnchor = mapping.mapDerivedToOriginal(currentAnchor);
                    }
                }
                long mappedAnchor;
                try (DiskRepairProjection mapping = DiskRepairProjection.open(
                        pending.result.getProjectionFile())) {
                    mappedAnchor = mapping.mapOriginalToDerived(originalAnchor);
                }
                Path bookDirectory = original.getParent();
                String revision = pending.result.getRevisionId();
                Path indexRoot = bookDirectory.resolve("indexes").resolve(revision);
                DiskIndexBuildResult build = new DiskDocumentIndexBuilder().build(
                        pending.result.getDerivedFile(), indexRoot, revision,
                        pending.result.getDerivedSha256(), null);
                DiskDocumentIndex diskIndex = DiskDocumentIndex.openActive(indexRoot);
                IndexedTextWindow window = diskIndex.readWindowAround(
                        (int) Math.min(Integer.MAX_VALUE, mappedAnchor), INDEX_WINDOW_CHARACTERS);
                DocumentIndex windowIndex = DocumentIndex.build(window.getText(), revision);
                ChapterOutline loadedOutline = loadChapterOutline(
                        bookDirectory, bookId, originalRevision);
                List<ChapterEntry> mappedChapters = loadedOutline == null
                        ? diskIndex.getChapters()
                        : mapChapterOutline(loadedOutline,
                                pending.result.getProjectionFile());
                int finalAnchor = (int) Math.min(Integer.MAX_VALUE, mappedAnchor);
                persistBookProgress(bookDirectory, new BookProgressSnapshot(bookId,
                        pending.result.getDerivedFile().getFileName().toString(), revision,
                        pending.result.getProjectionFile().getFileName().toString(), finalAnchor));
                writeBookState(bookDirectory, new BookState(
                        original.getFileName().toString(), originalRevision,
                        pending.result.getDerivedFile().getFileName().toString(), revision,
                        pending.result.getProjectionFile().getFileName().toString()));
                List<BookLibraryEntry> updatedBooks = loadBookLibrary(bookDirectory);
                runOnUiThread(() -> {
                    if (pending != pendingRepair) {
                        repairApplying = false;
                        return;
                    }
                    currentRevision = revision;
                    activeTextPath = pending.result.getDerivedFile();
                    activeProjectionPath = pending.result.getProjectionFile();
                    currentDiskIndex = diskIndex;
                    currentText = window.getText();
                    currentWindowStart = window.getStartOffset();
                    currentWindowIndex = windowIndex;
                    pendingRepair = null;
                    resetOccurrenceSelection();
                    repairApplying = false;
                    chapterCursor = -1;
                    currentChapterOutline = loadedOutline;
                    refreshChapters(mappedChapters, 0);
                    readerSurface.setDocumentWindow(currentText, revision, currentWindowStart);
                    readerSurface.scrollToAnchor(Integer.toString(finalAnchor));
                    refreshBookshelf(updatedBooks, bookId);
                    status.setText(getString(R.string.repair_applied,
                            pending.result.getMatchCount(), revision.substring(0, 8), finalAnchor));
                    requestArtifactPrune();
                });
            } catch (Exception error) {
                String message = error.getMessage() == null
                        ? error.getClass().getSimpleName() : error.getMessage();
                runOnUiThread(() -> {
                    repairApplying = false;
                    status.setText(getString(R.string.repair_failed, message));
                });
            }
        });
    }

    private void undoRepair() {
        cancelAutoScrollResume();
        if (repairApplying || chapterOutlineTransitionInProgress) {
            status.setText(R.string.repair_busy);
            return;
        }
        Path projection = activeProjectionPath;
        Path original = baseOriginalPath;
        if (original == null) {
            status.setText(R.string.repair_need_book);
            return;
        }
        if (projection == null) {
            status.setText(R.string.repair_not_active);
            return;
        }
        long derivedAnchor = readerSurface.visibleCharacterOffset();
        String originalRevision = baseRevision;
        String bookId = currentBookId;
        repairApplying = true;
        repairRangeRequest++;
        stopSpeechPlayback(false);
        status.setText(R.string.repair_undoing);
        worker.execute(() -> {
            try {
                long originalAnchor;
                try (DiskRepairProjection mapping = DiskRepairProjection.open(projection)) {
                    originalAnchor = mapping.mapDerivedToOriginal(derivedAnchor);
                }
                Path bookDirectory = original.getParent();
                String originalSha = DiskDocumentIndexBuilder.computeSha256(original);
                Path indexRoot = bookDirectory.resolve("indexes").resolve(originalRevision);
                new DiskDocumentIndexBuilder().build(original, indexRoot, originalRevision,
                        originalSha, null);
                DiskDocumentIndex diskIndex = DiskDocumentIndex.openActive(indexRoot);
                int finalAnchor = (int) Math.min(Integer.MAX_VALUE, originalAnchor);
                IndexedTextWindow window = diskIndex.readWindowAround(
                        finalAnchor, INDEX_WINDOW_CHARACTERS);
                DocumentIndex windowIndex = DocumentIndex.build(window.getText(), originalRevision);
                ChapterOutline loadedOutline = loadChapterOutline(
                        bookDirectory, bookId, originalRevision);
                List<ChapterEntry> restoredChapters = loadedOutline == null
                        ? diskIndex.getChapters()
                        : mapChapterOutline(loadedOutline, null);
                persistBookProgress(bookDirectory, new BookProgressSnapshot(bookId,
                        original.getFileName().toString(), originalRevision, "", finalAnchor));
                writeBookState(bookDirectory, new BookState(
                        original.getFileName().toString(), originalRevision,
                        original.getFileName().toString(), originalRevision, ""));
                List<BookLibraryEntry> updatedBooks = loadBookLibrary(bookDirectory);
                runOnUiThread(() -> {
                    currentRevision = originalRevision;
                    activeTextPath = original;
                    activeProjectionPath = null;
                    currentDiskIndex = diskIndex;
                    currentText = window.getText();
                    currentWindowStart = window.getStartOffset();
                    currentWindowIndex = windowIndex;
                    pendingRepair = null;
                    resetOccurrenceSelection();
                    repairApplying = false;
                    chapterCursor = -1;
                    currentChapterOutline = loadedOutline;
                    refreshChapters(restoredChapters, 0);
                    readerSurface.setDocumentWindow(currentText,
                            originalRevision, currentWindowStart);
                    readerSurface.scrollToAnchor(Integer.toString(finalAnchor));
                    refreshBookshelf(updatedBooks, bookId);
                    status.setText(getString(R.string.repair_undone, finalAnchor));
                    requestArtifactPrune();
                });
            } catch (Exception error) {
                String message = error.getMessage() == null
                        ? error.getClass().getSimpleName() : error.getMessage();
                runOnUiThread(() -> {
                    repairApplying = false;
                    status.setText(getString(R.string.repair_failed, message));
                });
            }
        });
    }

    private void showRepairError(Exception error) {
        String message = error.getMessage() == null
                ? error.getClass().getSimpleName() : error.getMessage();
        runOnUiThread(() -> status.setText(getString(R.string.repair_failed, message)));
    }

    private void invalidatePendingRepair() {
        PendingRepair previous = pendingRepair;
        pendingRepair = null;
        if (previous != null) {
            worker.execute(() -> deleteRepairCandidate(previous.result));
        }
    }

    private boolean beginRuleMutation() {
        if (repairApplying) {
            status.setText(R.string.repair_busy);
            return false;
        }
        repairRulesGeneration++;
        resetOccurrenceSelection();
        invalidatePendingRepair();
        return true;
    }

    private void markLoadedRepairRulesPersisted() {
        repairRulesGeneration++;
        repairRulesPersistedGeneration = repairRulesGeneration;
    }

    private void deleteRepairCandidate(RepairFileResult result) {
        Path activeText = activeTextPath;
        Path activeProjection = activeProjectionPath;
        try {
            if (!result.getDerivedFile().equals(activeText)) {
                Files.deleteIfExists(result.getDerivedFile());
            }
            if (!result.getProjectionFile().equals(activeProjection)) {
                Files.deleteIfExists(result.getProjectionFile());
            }
            if (result.getCandidateIndexFile() != null) {
                Files.deleteIfExists(result.getCandidateIndexFile());
            }
        } catch (Exception ignored) {
            // Generated candidates are retried by capacity cleanup; source files are never targeted.
        }
    }

    private void loadStoragePolicy() {
        SharedPreferences preferences = getSharedPreferences(
                STORAGE_POLICY_PREFERENCES, MODE_PRIVATE);
        long storedLimit = preferences.getLong(
                STORAGE_LIMIT_KEY, GENERATED_ARTIFACT_LIMIT_OPTIONS[0]);
        int storedGroups = preferences.getInt(
                STORAGE_GROUPS_KEY, INACTIVE_ARTIFACT_GROUP_OPTIONS[2]);
        int limitIndex = indexOf(GENERATED_ARTIFACT_LIMIT_OPTIONS, storedLimit);
        int groupsIndex = indexOf(INACTIVE_ARTIFACT_GROUP_OPTIONS, storedGroups);
        generatedArtifactLimitBytes = GENERATED_ARTIFACT_LIMIT_OPTIONS[limitIndex];
        maximumInactiveArtifactGroups = INACTIVE_ARTIFACT_GROUP_OPTIONS[groupsIndex];
        storagePolicyGeneration++;
    }

    private void saveStoragePolicyAndPrune() {
        if (repairApplying) {
            status.setText(R.string.repair_busy);
            return;
        }
        int limitIndex = storageLimitSpinner.getSelectedItemPosition();
        int groupsIndex = inactiveGroupsSpinner.getSelectedItemPosition();
        if (limitIndex < 0 || limitIndex >= GENERATED_ARTIFACT_LIMIT_OPTIONS.length
                || groupsIndex < 0 || groupsIndex >= INACTIVE_ARTIFACT_GROUP_OPTIONS.length) {
            storagePolicyStatus.setText(R.string.storage_policy_invalid);
            return;
        }
        long selectedLimitBytes = GENERATED_ARTIFACT_LIMIT_OPTIONS[limitIndex];
        int selectedInactiveGroups = INACTIVE_ARTIFACT_GROUP_OPTIONS[groupsIndex];
        boolean saved = getSharedPreferences(STORAGE_POLICY_PREFERENCES, MODE_PRIVATE).edit()
                .putLong(STORAGE_LIMIT_KEY, selectedLimitBytes)
                .putInt(STORAGE_GROUPS_KEY, selectedInactiveGroups)
                .commit();
        if (!saved) {
            storageLimitSpinner.setSelection(indexOf(
                    GENERATED_ARTIFACT_LIMIT_OPTIONS, generatedArtifactLimitBytes));
            inactiveGroupsSpinner.setSelection(indexOf(
                    INACTIVE_ARTIFACT_GROUP_OPTIONS, maximumInactiveArtifactGroups));
            storagePolicyStatus.setText(R.string.storage_policy_save_failed);
            return;
        }
        generatedArtifactLimitBytes = selectedLimitBytes;
        maximumInactiveArtifactGroups = selectedInactiveGroups;
        storagePolicyGeneration++;
        storagePolicyStatus.setText(R.string.storage_cleaning);
        requestArtifactPrune(true);
    }

    private static int indexOf(long[] values, long expected) {
        for (int index = 0; index < values.length; index++) {
            if (values[index] == expected) {
                return index;
            }
        }
        return 0;
    }

    private static int indexOf(int[] values, int expected) {
        for (int index = 0; index < values.length; index++) {
            if (values[index] == expected) {
                return index;
            }
        }
        return values.length - 1;
    }

    private void requestArtifactPrune() {
        requestArtifactPrune(false);
    }

    private void requestArtifactPrune(boolean userRequested) {
        Path original = baseOriginalPath;
        if (original == null) {
            if (userRequested) {
                storagePolicyStatus.setText(R.string.storage_need_book);
            }
            return;
        }
        Path activeText = activeTextPath;
        Path activeProjection = activeProjectionPath;
        PendingRepair pending = pendingRepair;
        String originalRevision = baseRevision;
        String activeRevision = currentRevision;
        int ruleCount = repairRules.size();
        long byteLimit = generatedArtifactLimitBytes;
        int inactiveGroupLimit = maximumInactiveArtifactGroups;
        long policyGeneration = storagePolicyGeneration;
        Set<Path> protectedPaths = new HashSet<>();
        protectedPaths.add(original);
        if (activeText != null) {
            protectedPaths.add(activeText);
        }
        if (activeProjection != null) {
            protectedPaths.add(activeProjection);
        }
        Set<String> protectedRevisions = new HashSet<>();
        protectedRevisions.add(originalRevision);
        protectedRevisions.add(activeRevision);
        if (pending != null) {
            protectedPaths.add(pending.result.getDerivedFile());
            protectedPaths.add(pending.result.getProjectionFile());
            protectedPaths.add(pending.result.getCandidateIndexFile());
            protectedRevisions.add(pending.result.getRevisionId());
        }
        worker.execute(() -> {
            try {
                if (policyGeneration != storagePolicyGeneration) {
                    return;
                }
                List<BookLibraryEntry> libraryEntries = loadBookLibrary(
                        original.getParent());
                for (BookLibraryEntry entry : libraryEntries) {
                    protectedPaths.add(resolveBookFile(original.getParent(),
                            entry.getBaseFileName(), "书架原文件无效或已丢失"));
                    protectedPaths.add(resolveBookFile(original.getParent(),
                            entry.getActiveFileName(), "书架阅读文件无效或已丢失"));
                    if (!entry.getProjectionFileName().isEmpty()) {
                        protectedPaths.add(resolveBookFile(original.getParent(),
                                entry.getProjectionFileName(), "书架净读映射不可用"));
                    }
                    protectedRevisions.add(entry.getBaseRevision());
                    protectedRevisions.add(entry.getActiveRevision());
                }
                GeneratedArtifactPruner.Result result = new GeneratedArtifactPruner().prune(
                        original.getParent(), protectedPaths, protectedRevisions,
                        byteLimit, inactiveGroupLimit);
                runOnUiThread(() -> {
                    if (!original.equals(baseOriginalPath)
                            || byteLimit != generatedArtifactLimitBytes
                            || inactiveGroupLimit != maximumInactiveArtifactGroups
                            || policyGeneration != storagePolicyGeneration) {
                        return;
                    }
                    repairRuleSpinner.setContentDescription(
                            "repair-rules:" + ruleCount + ";pruned-groups:"
                                    + result.deletedGroups + ";reclaimed-bytes:"
                                    + result.reclaimedBytes + ";protected-bytes:"
                                    + result.protectedBytes + ";retained-bytes:"
                                    + result.retainedBytes + ";quota-bytes:" + byteLimit);
                    if (result.protectedDataExceedsLimit) {
                        storagePolicyStatus.setText(getString(
                                R.string.storage_protected_over_limit,
                                mebibytesCeiling(result.protectedBytes),
                                byteLimit / MEBIBYTE,
                                mebibytesCeiling(result.reclaimedBytes)));
                    } else {
                        storagePolicyStatus.setText(getString(R.string.storage_clean_complete,
                                result.deletedGroups,
                                mebibytesCeiling(result.reclaimedBytes),
                                mebibytesCeiling(result.retainedBytes),
                                byteLimit / MEBIBYTE, inactiveGroupLimit));
                    }
                });
            } catch (Exception error) {
                if (userRequested) {
                    String message = error.getMessage() == null
                            ? error.getClass().getSimpleName() : error.getMessage();
                    runOnUiThread(() -> {
                        if (original.equals(baseOriginalPath)
                                && byteLimit == generatedArtifactLimitBytes
                                && inactiveGroupLimit == maximumInactiveArtifactGroups
                                && policyGeneration == storagePolicyGeneration) {
                            storagePolicyStatus.setText(getString(
                                    R.string.storage_clean_failed, message));
                        }
                    });
                }
            }
        });
    }

    private static long mebibytesCeiling(long bytes) {
        return bytes / MEBIBYTE + (bytes % MEBIBYTE == 0 ? 0 : 1);
    }

    private void toggleSelectedOccurrence() {
        if (repairApplying) {
            status.setText(R.string.repair_busy);
            return;
        }
        int position = repairOccurrenceSpinner.getSelectedItemPosition();
        if (position < 0 || position >= previewOccurrences.size()) {
            status.setText(R.string.repair_need_preview);
            return;
        }
        RepairMatch match = previewOccurrences.get(position);
        RepairOccurrence occurrence = new RepairOccurrence(
                match.getRuleId(), match.getOriginalStart());
        if (!excludedOccurrences.remove(occurrence)) {
            if (excludedOccurrences.size() >= RepairSelection.MAXIMUM_EXCLUSIONS) {
                status.setText(getString(R.string.repair_exclusion_limit,
                        RepairSelection.MAXIMUM_EXCLUSIONS));
                return;
            }
            excludedOccurrences.add(occurrence);
        }
        previewRepair();
    }

    private void loadPreviousRepairPreviewPage() {
        loadRepairPreviewPage(Math.max(0, repairPreviewOffset - REPAIR_PREVIEW_PAGE_SIZE));
    }

    private void loadNextRepairPreviewPage() {
        PendingRepair pending = pendingRepair;
        if (pending == null) {
            status.setText(R.string.repair_need_preview);
            return;
        }
        long next = repairPreviewOffset + previewOccurrences.size();
        if (next >= pending.result.getCandidateMatchCount()) {
            status.setText(R.string.repair_preview_last_page);
            return;
        }
        loadRepairPreviewPage(next);
    }

    private void loadRepairPreviewPage(long requestedOffset) {
        if (repairApplying) {
            status.setText(R.string.repair_busy);
            return;
        }
        PendingRepair pending = pendingRepair;
        Path original = baseOriginalPath;
        if (pending == null || original == null) {
            status.setText(R.string.repair_need_preview);
            return;
        }
        long total = pending.result.getCandidateMatchCount();
        if (total == 0) {
            status.setText(R.string.repair_no_matches);
            return;
        }
        long boundedOffset = Math.max(0, requestedOffset);
        if (boundedOffset >= total) {
            boundedOffset = ((total - 1) / REPAIR_PREVIEW_PAGE_SIZE)
                    * REPAIR_PREVIEW_PAGE_SIZE;
        }
        String expectedBaseRevision = baseRevision;
        long expectedRulesGeneration = repairRulesGeneration;
        long pageRequest = ++repairPreviewPageRequest;
        List<RepairRule> rules = normalizedRepairRules(repairRules);
        RepairSelection selection = RepairSelection.excluding(
                new ArrayList<>(excludedOccurrences));
        long finalOffset = boundedOffset;
        status.setText(R.string.repair_preview_loading_page);
        worker.execute(() -> {
            try {
                RepairPreviewPage page;
                boolean usedCandidateIndex = false;
                Path candidateIndexPath = pending.result.getCandidateIndexFile();
                try {
                    if (candidateIndexPath == null) {
                        throw new IllegalStateException("candidate index unavailable");
                    }
                    Path sourceIndexRoot = original.getParent().resolve("indexes")
                            .resolve(expectedBaseRevision);
                    DiskDocumentIndex sourceIndex = DiskDocumentIndex.openActive(sourceIndexRoot);
                    try (DiskRepairCandidateIndex candidateIndex =
                            DiskRepairCandidateIndex.open(candidateIndexPath,
                                    pending.result.getSourceSha256(), rules)) {
                        page = candidateIndex.readPage(sourceIndex, selection, finalOffset,
                                REPAIR_PREVIEW_PAGE_SIZE);
                        usedCandidateIndex = true;
                    }
                } catch (Exception unavailable) {
                    page = new RepairFilePreviewPager().readPage(
                            original, rules, selection, finalOffset, REPAIR_PREVIEW_PAGE_SIZE);
                }
                RepairPreviewPage loadedPage = page;
                boolean indexedPage = usedCandidateIndex;
                runOnUiThread(() -> {
                    if (pending != pendingRepair
                            || !expectedBaseRevision.equals(baseRevision)
                            || !original.equals(baseOriginalPath)
                            || expectedRulesGeneration != repairRulesGeneration
                            || pageRequest != repairPreviewPageRequest) {
                        return;
                    }
                    repairPreviewOffset = loadedPage.getMatchOffset();
                    refreshOccurrenceSpinner(loadedPage.getMatches());
                    updateRepairPreviewPageStatus(total);
                    status.setText(getString(R.string.repair_preview_page_loaded,
                            repairPreviewOffset + 1,
                            repairPreviewOffset + loadedPage.getMatches().size(), total,
                            indexedPage ? getString(R.string.repair_preview_page_indexed)
                                    : getString(R.string.repair_preview_page_sequential)));
                });
            } catch (Exception error) {
                String message = error.getMessage() == null
                        ? error.getClass().getSimpleName() : error.getMessage();
                runOnUiThread(() -> {
                    if (pending == pendingRepair && pageRequest == repairPreviewPageRequest) {
                        status.setText(getString(R.string.repair_failed, message));
                    }
                });
            }
        });
    }

    private void setPreviewPageApplied(boolean applied) {
        if (repairApplying) {
            status.setText(R.string.repair_busy);
            return;
        }
        if (pendingRepair == null || previewOccurrences.isEmpty()) {
            status.setText(R.string.repair_need_preview);
            return;
        }
        Set<RepairOccurrence> updated = new HashSet<>(excludedOccurrences);
        for (RepairMatch match : previewOccurrences) {
            RepairOccurrence occurrence = new RepairOccurrence(
                    match.getRuleId(), match.getOriginalStart());
            if (applied) {
                updated.remove(occurrence);
            } else {
                updated.add(occurrence);
            }
        }
        if (updated.size() > RepairSelection.MAXIMUM_EXCLUSIONS) {
            status.setText(getString(R.string.repair_exclusion_limit,
                    RepairSelection.MAXIMUM_EXCLUSIONS));
            return;
        }
        if (updated.equals(excludedOccurrences)) {
            status.setText(R.string.repair_page_selection_unchanged);
            return;
        }
        excludedOccurrences.clear();
        excludedOccurrences.addAll(updated);
        previewRepair();
    }

    private void setOccurrenceRangeApplied(boolean applied) {
        if (repairApplying) {
            status.setText(R.string.repair_busy);
            return;
        }
        PendingRepair pending = pendingRepair;
        if (pending == null) {
            status.setText(R.string.repair_need_preview);
            return;
        }
        RepairOrdinalRange range;
        try {
            long first = Long.parseLong(repairRangeStartInput.getText().toString().trim());
            long last = Long.parseLong(repairRangeEndInput.getText().toString().trim());
            range = RepairOrdinalRange.fromOneBased(first, last,
                    pending.result.getCandidateMatchCount(),
                    RepairSelection.MAXIMUM_EXCLUSIONS);
        } catch (IllegalArgumentException invalid) {
            status.setText(getString(R.string.repair_range_invalid,
                    pending.result.getCandidateMatchCount(),
                    RepairSelection.MAXIMUM_EXCLUSIONS));
            return;
        }
        long expectedRulesGeneration = repairRulesGeneration;
        long rangeRequest = ++repairRangeRequest;
        List<RepairRule> rules = normalizedRepairRules(repairRules);
        status.setText(getString(R.string.repair_range_loading,
                range.getFirstOneBased(), range.getLastOneBased()));
        worker.execute(() -> {
            try {
                Path candidateIndexPath = pending.result.getCandidateIndexFile();
                if (candidateIndexPath == null) {
                    throw new IllegalStateException("candidate index unavailable");
                }
                List<RepairOccurrence> occurrences;
                try (DiskRepairCandidateIndex candidateIndex = DiskRepairCandidateIndex.open(
                        candidateIndexPath, pending.result.getSourceSha256(), rules)) {
                    if (candidateIndex.getCandidateCount()
                            != pending.result.getCandidateMatchCount()) {
                        throw new IllegalStateException("candidate count changed");
                    }
                    occurrences = candidateIndex.readOccurrences(
                            range.getZeroBasedOffset(), range.getCount());
                }
                if (occurrences.size() != range.getCount()) {
                    throw new IllegalStateException("candidate range is incomplete");
                }
                runOnUiThread(() -> {
                    if (pending != pendingRepair
                            || repairApplying
                            || expectedRulesGeneration != repairRulesGeneration
                            || rangeRequest != repairRangeRequest) {
                        return;
                    }
                    Set<RepairOccurrence> updated = new HashSet<>(excludedOccurrences);
                    if (applied) {
                        updated.removeAll(occurrences);
                    } else {
                        updated.addAll(occurrences);
                    }
                    if (updated.size() > RepairSelection.MAXIMUM_EXCLUSIONS) {
                        status.setText(getString(R.string.repair_exclusion_limit,
                                RepairSelection.MAXIMUM_EXCLUSIONS));
                        return;
                    }
                    if (updated.equals(excludedOccurrences)) {
                        status.setText(R.string.repair_range_selection_unchanged);
                        return;
                    }
                    excludedOccurrences.clear();
                    excludedOccurrences.addAll(updated);
                    previewRepair();
                });
            } catch (Exception error) {
                String message = error.getMessage() == null
                        ? error.getClass().getSimpleName() : error.getMessage();
                runOnUiThread(() -> {
                    if (pending == pendingRepair
                            && !repairApplying
                            && expectedRulesGeneration == repairRulesGeneration
                            && rangeRequest == repairRangeRequest) {
                        status.setText(getString(R.string.repair_range_failed, message));
                    }
                });
            }
        });
    }

    private void selectAllOccurrences() {
        if (repairApplying) {
            status.setText(R.string.repair_busy);
            return;
        }
        if (pendingRepair != null && excludedOccurrences.isEmpty()) {
            status.setText(R.string.repair_all_already_applied);
            return;
        }
        excludedOccurrences.clear();
        previewRepair();
    }

    private void resetOccurrenceSelection() {
        repairPreviewPageRequest++;
        repairRangeRequest++;
        repairPreviewOffset = 0;
        excludedOccurrences.clear();
        if (repairRangeStartInput != null) {
            repairRangeStartInput.setText("");
        }
        if (repairRangeEndInput != null) {
            repairRangeEndInput.setText("");
        }
        refreshOccurrenceSpinner(Collections.emptyList());
        if (repairPreviewPageStatus != null) {
            repairPreviewPageStatus.setText(R.string.repair_preview_page_empty);
        }
    }

    private void refreshOccurrenceSpinner(List<RepairMatch> matches) {
        previewOccurrences.clear();
        previewOccurrences.addAll(matches);
        List<String> labels = new ArrayList<>();
        for (int index = 0; index < matches.size(); index++) {
            RepairMatch match = matches.get(index);
            labels.add(getString(R.string.repair_occurrence_label,
                    repairPreviewOffset + index + 1,
                    match.isApplied() ? getString(R.string.repair_occurrence_apply)
                            : getString(R.string.repair_occurrence_skip),
                    match.getOriginalStart(), match.getMatchedText(), match.getReplacement()));
        }
        if (labels.isEmpty()) {
            labels.add(getString(R.string.repair_occurrence_empty));
        }
        repairOccurrenceSpinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, labels));
        repairOccurrenceSpinner.setContentDescription(
                "repair-preview-occurrences:" + previewOccurrences.size()
                        + ";offset:" + repairPreviewOffset
                        + ";excluded:" + excludedOccurrences.size());
    }

    private void updateRepairPreviewPageStatus(long totalCandidates) {
        if (repairPreviewPageStatus == null || totalCandidates == 0) {
            if (repairPreviewPageStatus != null) {
                repairPreviewPageStatus.setText(R.string.repair_preview_page_empty);
            }
            return;
        }
        long start = repairPreviewOffset + 1;
        long end = repairPreviewOffset + previewOccurrences.size();
        long page = repairPreviewOffset / REPAIR_PREVIEW_PAGE_SIZE + 1;
        long pages = (totalCandidates + REPAIR_PREVIEW_PAGE_SIZE - 1)
                / REPAIR_PREVIEW_PAGE_SIZE;
        repairPreviewPageStatus.setText(getString(R.string.repair_preview_page_status,
                page, pages, start, end, totalCandidates));
    }

    private static String ruleMatchSummary(List<RepairRule> rules,
            Map<String, Long> appliedCounts, Map<String, Long> candidateCounts) {
        StringBuilder summary = new StringBuilder();
        for (RepairRule rule : rules) {
            if (!rule.isEnabled()) {
                continue;
            }
            if (summary.length() > 0) {
                summary.append("，");
            }
            summary.append(rule.getMatchText()).append("→")
                    .append(rule.getReplacement()).append(":")
                    .append(appliedCounts.get(rule.getId())).append("/")
                    .append(candidateCounts.get(rule.getId()));
        }
        return summary.toString();
    }

    private static String previewSummary(List<RepairMatch> previews) {
        if (previews.isEmpty()) {
            return "无命中上下文";
        }
        RepairMatch first = previews.get(0);
        return (first.getBeforeContext() + "["
                + (first.isApplied() ? "" : "跳过:") + first.getMatchedText() + "→"
                + first.getReplacement() + "]" + first.getAfterContext()).replace('\n', ' ');
    }

    private void toggleAutoScroll() {
        if (autoScrollWindowContinuationPending) {
            cancelAutoScrollResume();
            status.setText(R.string.auto_scroll_stopped);
            return;
        }
        cancelAutoScrollResume();
        if (readerNavigationSettings.getReadingMode()
                == ReaderNavigationSettings.ReadingMode.PAGED) {
            status.setText(R.string.reader_auto_scroll_requires_continuous);
            return;
        }
        if (!readerSurface.isAutoScrolling()) {
            stopSpeechPlayback(false);
        }
        readerSurface.toggleAutoScroll();
    }

    private void stopCompanionMode() {
        stopAutoScrollAndCancelResume();
        stopSpeechPlayback(false);
        status.setText(R.string.companion_stopped);
        updateQuickActionState();
    }

    private void scheduleAutoScrollResumeAfterTouch(int anchorOffset) {
        cancelAutoScrollResume();
        if (!autoScrollResumeSession.arm(autoScrollContextToken(), anchorOffset,
                SystemClock.elapsedRealtime())) {
            return;
        }
        checkAutoScrollResume();
    }

    private void checkAutoScrollResume() {
        if (!autoScrollResumeSession.isArmed()) {
            return;
        }
        long now = SystemClock.elapsedRealtime();
        boolean due = autoScrollResumeSession.consumeIfDue(
                autoScrollContextToken(), readerSurface.visibleCharacterOffset(), now);
        if (due) {
            if (speechQueue == null && !readerSurface.isAutoScrolling()) {
                readerSurface.toggleAutoScroll();
            }
            return;
        }
        if (!autoScrollResumeSession.isArmed()) {
            return;
        }
        int remainingSeconds = autoScrollResumeSession.remainingSeconds(now);
        if (remainingSeconds != autoScrollResumeAnnouncedSeconds) {
            autoScrollResumeAnnouncedSeconds = remainingSeconds;
            status.setText(getString(
                    R.string.auto_scroll_resume_countdown, remainingSeconds));
        }
        autoScrollResumeHandler.removeCallbacks(autoScrollResumeTick);
        autoScrollResumeHandler.postDelayed(autoScrollResumeTick, 250L);
    }

    private String autoScrollContextToken() {
        return currentRevision.isEmpty() ? "initial" : currentRevision;
    }

    private void cancelAutoScrollResume() {
        autoScrollResumeHandler.removeCallbacks(autoScrollResumeTick);
        autoScrollResumeSession.cancel();
        autoScrollResumeAnnouncedSeconds = -1;
        if (autoScrollWindowContinuationPending) {
            autoScrollWindowContinuationPending = false;
            viewportRequest++;
        }
    }

    private void stopAutoScrollAndCancelResume() {
        cancelAutoScrollResume();
        readerSurface.stopAutoScroll();
    }

    private void applyCompanionSleepTimer() {
        if (currentRevision.isEmpty()) {
            status.setText(R.string.repair_need_book);
            return;
        }
        int selection = sleepTimerSpinner.getSelectedItemPosition();
        if (selection <= 0) {
            cancelCompanionSleepTimer(true);
            return;
        }
        try {
            if (selection <= 3) {
                long minutes = selection == 1 ? 15L : selection == 2 ? 30L : 60L;
                sleepTimer.armForDuration(SystemClock.elapsedRealtime(), minutes * MINUTE_MILLIS);
                sleepTimerRevision = currentRevision;
                sleepTimerStatus.setText(getString(R.string.sleep_timer_duration_armed, minutes));
            } else {
                long currentAnchor = readerSurface.visibleCharacterOffset();
                long chapterEnd = findCurrentChapterEnd(currentAnchor);
                if (chapterEnd <= currentAnchor) {
                    cancelCompanionSleepTimer(false);
                    status.setText(R.string.sleep_timer_chapter_unavailable);
                    return;
                }
                sleepTimer.armForChapterEnd(currentAnchor, chapterEnd);
                sleepTimerRevision = currentRevision;
                sleepTimerStatus.setText(getString(
                        R.string.sleep_timer_chapter_armed, chapterEnd));
            }
            sleepTimerHandler.removeCallbacks(sleepTimerTick);
            sleepTimerHandler.postDelayed(sleepTimerTick, SLEEP_TIMER_TICK_MILLIS);
        } catch (Exception error) {
            showRepairError(error);
        }
    }

    private long findCurrentChapterEnd(long currentAnchor) {
        DiskDocumentIndex diskIndex = currentDiskIndex;
        if (diskIndex == null || !currentRevision.equals(diskIndex.getRevision())) {
            return -1;
        }
        if (currentChapters.isEmpty()) {
            return -1;
        }
        for (ChapterEntry chapter : currentChapters) {
            if (chapter.getCharacterOffset() > currentAnchor) {
                return chapter.getCharacterOffset();
            }
        }
        if (currentChapterOutline == null && diskIndex.isChapterListTruncated()) {
            return -1;
        }
        return diskIndex.getCharacterCount();
    }

    private void checkCompanionSleepTimer() {
        if (sleepTimer.getMode() == CompanionSleepTimer.Mode.OFF) {
            return;
        }
        if (!sleepTimerRevision.equals(currentRevision)) {
            cancelCompanionSleepTimer(false);
            sleepTimerStatus.setText(R.string.sleep_timer_revision_changed);
            return;
        }
        long now = SystemClock.elapsedRealtime();
        SpeechPlaybackQueue activeSpeech = speechQueue;
        int companionAnchor = activeSpeech == null
                ? readerSurface.visibleCharacterOffset() : activeSpeech.getAnchor();
        if (sleepTimer.consumeIfExpired(now, companionAnchor)) {
            sleepTimerHandler.removeCallbacks(sleepTimerTick);
            sleepTimerRevision = "";
            stopAutoScrollAndCancelResume();
            stopSpeechPlayback(false);
            sleepTimerStatus.setText(R.string.sleep_timer_expired);
            status.setText(R.string.sleep_timer_expired);
            return;
        }
        if (sleepTimer.getMode() == CompanionSleepTimer.Mode.DEADLINE) {
            long seconds = (sleepTimer.remainingMillis(now) + 999L) / 1000L;
            sleepTimerStatus.setText(getString(R.string.sleep_timer_remaining, seconds));
        }
        sleepTimerHandler.postDelayed(sleepTimerTick, SLEEP_TIMER_TICK_MILLIS);
    }

    private void cancelCompanionSleepTimer(boolean userRequested) {
        sleepTimerHandler.removeCallbacks(sleepTimerTick);
        sleepTimer.cancel();
        sleepTimerRevision = "";
        if (sleepTimerStatus != null) {
            sleepTimerStatus.setText(userRequested
                    ? R.string.sleep_timer_cancelled : R.string.sleep_timer_inactive);
        }
    }

    private void restoreRetainedSleepTimer() {
        RetainedSleepTimer retained = retainedSleepTimer;
        retainedSleepTimer = null;
        if (retained == null) {
            return;
        }
        if (!retained.revision.equals(currentRevision)) {
            sleepTimerStatus.setText(R.string.sleep_timer_revision_changed);
            return;
        }
        long now = SystemClock.elapsedRealtime();
        if (retained.mode == CompanionSleepTimer.Mode.DEADLINE) {
            long remaining = retained.target - now;
            if (remaining <= 0) {
                sleepTimerStatus.setText(R.string.sleep_timer_expired);
                return;
            }
            sleepTimer.armForDuration(now, remaining);
        } else if (retained.mode == CompanionSleepTimer.Mode.CHAPTER_END) {
            long currentAnchor = readerSurface.visibleCharacterOffset();
            if (retained.target <= currentAnchor) {
                sleepTimerStatus.setText(R.string.sleep_timer_expired);
                return;
            }
            sleepTimer.armForChapterEnd(currentAnchor, retained.target);
        } else {
            return;
        }
        sleepTimerRevision = retained.revision;
        sleepTimerStatus.setText(R.string.sleep_timer_restored);
        sleepTimerHandler.postDelayed(sleepTimerTick, SLEEP_TIMER_TICK_MILLIS);
    }

    private void startSpeaking() {
        if (currentText.isEmpty()) {
            status.setText(R.string.import_before_speaking);
            return;
        }
        if (!speech.capabilities().available) {
            status.setText(R.string.tts_unavailable);
            return;
        }
        stopAutoScrollAndCancelResume();
        int globalStart = SpeechPlaybackQueue.paragraphStartAt(
                currentText, currentWindowStart,
                readerSurface.visibleCharacterOffset());
        startSpeechQueue(currentRevision, currentWindowStart, currentText, globalStart);
    }

    private void startSpeechQueue(String revision, int windowStart, String text, int anchor) {
        startSpeechQueue(revision, windowStart, text, anchor, true);
    }

    private void startSpeechQueue(String revision, int windowStart, String text, int anchor,
            boolean newSession) {
        int maximum = Math.min(3000, speech.capabilities().maxInputCharacters);
        if (maximum < 2) {
            status.setText(R.string.tts_input_limit_invalid);
            return;
        }
        SpeechPlaybackQueue candidate;
        SpeechPlaybackQueue.Item item;
        try {
            candidate = new SpeechPlaybackQueue(
                    revision, windowStart, text, anchor, maximum);
            item = candidate.begin();
        } catch (IllegalArgumentException invalidWindow) {
            status.setText(getString(R.string.tts_error, invalidWindow.getMessage()));
            return;
        }
        if (newSession) {
            stopSpeechPlayback(false);
            if (!audioInterruptions.start()) {
                status.setText(R.string.speech_audio_focus_denied);
                return;
            }
        } else {
            if (speechQueue != null) {
                speechQueue.stop();
            }
            speech.stop();
            if (!audioInterruptions.isActive()) {
                speechQueue = candidate;
                if (candidate.getState() == SpeechPlaybackQueue.State.PLAYING) {
                    candidate.pause();
                }
                status.setText(R.string.speech_focus_lost_during_load);
                return;
            }
        }
        speechQueue = candidate;
        updateQuickActionState();
        if (item == null) {
            continueSpeechFromNextWindow(anchor);
            return;
        }
        speakQueueItem(item);
    }

    private void speakQueueItem(SpeechPlaybackQueue.Item item) {
        SpeechPlaybackQueue queue = speechQueue;
        SpeechPlaybackQueue.Highlight fallback = queue == null
                ? null : queue.fallbackHighlight(item.getUtteranceId());
        if (!showSpeechHighlight(queue, fallback)) {
            stopSpeechPlayback(false);
            status.setText(R.string.speech_highlight_invalid);
            return;
        }
        speech.speak(item.getUtteranceId(), item.getText(), speechSettings.getVoiceId(),
                speechSettings.getRateMultiplier(), speechSettings.getPitchMultiplier());
        if (speechQueue != queue || queue.getState() != SpeechPlaybackQueue.State.PLAYING
                || !queue.accepts(item.getUtteranceId())) {
            return;
        }
        status.setText(getString(R.string.speaking_segment,
                item.getStartOffset(), item.getEndOffset()));
        updateQuickActionState();
    }

    private void pauseSpeaking() {
        SpeechPlaybackQueue queue = speechQueue;
        if (queue == null || queue.getState() != SpeechPlaybackQueue.State.PLAYING) {
            status.setText(R.string.speech_not_playing);
            return;
        }
        queue.pause();
        speech.pause();
        audioInterruptions.stop();
        status.setText(getString(R.string.speech_paused_at, queue.getAnchor()));
        updateQuickActionState();
    }

    private void resumeSpeaking() {
        SpeechPlaybackQueue queue = speechQueue;
        if (queue == null || queue.getState() != SpeechPlaybackQueue.State.PAUSED) {
            status.setText(R.string.speech_not_paused);
            return;
        }
        if (!audioInterruptions.start()) {
            status.setText(R.string.speech_audio_focus_denied_paused);
            return;
        }
        SpeechPlaybackQueue.Item item = queue.resume();
        updateQuickActionState();
        if (item == null) {
            continueSpeechFromNextWindow(queue.getAnchor());
            return;
        }
        speakQueueItem(item);
    }

    private void moveSpeechNext() {
        SpeechPlaybackQueue queue = speechQueue;
        if (queue == null || queue.getState() == SpeechPlaybackQueue.State.STOPPED
                || queue.getState() == SpeechPlaybackQueue.State.COMPLETE) {
            status.setText(R.string.speech_not_playing);
            return;
        }
        if (!audioInterruptions.start()) {
            status.setText(R.string.speech_audio_focus_denied_paused);
            return;
        }
        SpeechPlaybackQueue.Item item = queue.moveNext();
        if (item == null) {
            continueSpeechFromNextWindow(queue.getWindowEndOffset());
            return;
        }
        speakQueueItem(item);
    }

    private void moveSpeechPrevious() {
        SpeechPlaybackQueue queue = speechQueue;
        if (queue == null || queue.getState() == SpeechPlaybackQueue.State.STOPPED
                || queue.getState() == SpeechPlaybackQueue.State.COMPLETE) {
            status.setText(R.string.speech_not_playing);
            return;
        }
        if (!audioInterruptions.start()) {
            status.setText(R.string.speech_audio_focus_denied_paused);
            return;
        }
        SpeechPlaybackQueue.Item item = queue.movePrevious();
        if (item == null) {
            if (queue.getState() == SpeechPlaybackQueue.State.PAUSED) {
                audioInterruptions.stop();
            }
            status.setText(R.string.speech_first_segment_in_window);
            return;
        }
        speakQueueItem(item);
    }

    private void continueSpeechFromNextWindow(int anchor) {
        DiskDocumentIndex diskIndex = currentDiskIndex;
        String revision = currentRevision;
        if (diskIndex == null || !revision.equals(diskIndex.getRevision())
                || anchor >= diskIndex.getCharacterCount()) {
            stopSpeechPlayback(false);
            status.setText(diskIndex == null
                    ? R.string.speech_index_not_ready : R.string.speaking_complete);
            return;
        }
        long request = ++speechGeneration;
        if (speechQueue != null) {
            speechQueue.stop();
        }
        speech.stop();
        status.setText(R.string.speech_loading_next_window);
        worker.execute(() -> {
            try {
                IndexedTextWindow window = diskIndex.readWindowAround(
                        anchor, INDEX_WINDOW_CHARACTERS);
                String nextText = window.getText();
                int windowStart = window.getStartOffset();
                runOnUiThread(() -> {
                    if (request != speechGeneration || !revision.equals(currentRevision)) {
                        return;
                    }
                    currentText = nextText;
                    currentWindowStart = windowStart;
                    currentWindowIndex = DocumentIndex.build(nextText, revision);
                    readerSurface.setDocumentWindow(nextText, revision, windowStart);
                    startSpeechQueue(revision, windowStart, nextText, anchor, false);
                });
            } catch (Exception error) {
                String message = error.getMessage() == null
                        ? error.getClass().getSimpleName() : error.getMessage();
                runOnUiThread(() -> {
                    if (request == speechGeneration && revision.equals(currentRevision)) {
                        stopSpeechPlayback(false);
                        status.setText(getString(R.string.tts_error, message));
                    }
                });
            }
        });
    }

    private void stopSpeechPlayback(boolean userRequested) {
        speechGeneration++;
        if (speechQueue != null) {
            speechQueue.stop();
            speechQueue = null;
        }
        speech.stop();
        audioInterruptions.stop();
        if (readerSurface != null) {
            readerSurface.clearSpeechHighlight();
        }
        if (userRequested) {
            status.setText(R.string.speaking_stopped);
        }
        updateQuickActionState();
    }

    private void pauseSpeechForInterruption(AndroidAudioInterruptionAdapter.Reason reason) {
        SpeechPlaybackQueue queue = speechQueue;
        if (queue == null || queue.getState() != SpeechPlaybackQueue.State.PLAYING) {
            audioInterruptions.stop();
            return;
        }
        queue.pause();
        speech.pause();
        audioInterruptions.stop();
        int message = reason == AndroidAudioInterruptionAdapter.Reason.OUTPUT_DISCONNECTED
                ? R.string.speech_output_disconnected : R.string.speech_focus_interrupted;
        status.setText(getString(message, queue.getAnchor()));
        updateQuickActionState();
    }

    private TextToSpeechPort.Listener createSpeechListener() {
        return new TextToSpeechPort.Listener() {
            @Override
            public void onStart(String utteranceId) {
                runOnUiThread(() -> {
                    if (speechQueue != null && speechQueue.accepts(utteranceId)) {
                        status.setText(R.string.speaking_current_window);
                    }
                });
            }

            @Override
            public void onRange(String utteranceId, int start, int end) {
                runOnUiThread(() -> {
                    SpeechPlaybackQueue queue = speechQueue;
                    SpeechPlaybackQueue.Highlight highlight = queue == null
                            ? null : queue.onRangeHighlight(utteranceId, start, end);
                    if (highlight != null && !showSpeechHighlight(queue, highlight)) {
                        stopSpeechPlayback(false);
                        status.setText(R.string.speech_highlight_invalid);
                    }
                });
            }

            @Override
            public void onDone(String utteranceId) {
                runOnUiThread(() -> {
                    SpeechPlaybackQueue queue = speechQueue;
                    if (queue == null || !queue.accepts(utteranceId)) {
                        return;
                    }
                    SpeechPlaybackQueue.Item next = queue.onDone(utteranceId);
                    if (next != null) {
                        speakQueueItem(next);
                    } else if (queue.getState() == SpeechPlaybackQueue.State.COMPLETE) {
                        continueSpeechFromNextWindow(queue.getAnchor());
                    }
                });
            }

            @Override
            public void onStopped(String utteranceId, boolean interrupted) {
                runOnUiThread(() -> {
                    SpeechPlaybackQueue queue = speechQueue;
                    if (queue != null && queue.accepts(utteranceId)) {
                        queue.pause();
                        audioInterruptions.stop();
                        status.setText(getString(R.string.speech_interrupted_at,
                                queue.getAnchor()));
                        updateQuickActionState();
                    }
                });
            }

            @Override
            public void onError(String utteranceId, String errorCode) {
                runOnUiThread(() -> {
                    SpeechPlaybackQueue queue = speechQueue;
                    if (queue != null && queue.onError(utteranceId)) {
                        stopSpeechPlayback(false);
                        if ("voice-unavailable".equals(errorCode)) {
                            status.setText(R.string.speech_voice_unavailable);
                        } else if ("speech-settings-rejected".equals(errorCode)) {
                            status.setText(R.string.speech_settings_rejected);
                        } else if ("speech-input-invalid".equals(errorCode)) {
                            status.setText(R.string.speech_input_rejected);
                        } else {
                            status.setText(getString(R.string.tts_error, errorCode));
                        }
                    }
                });
            }
        };
    }

    private boolean showSpeechHighlight(SpeechPlaybackQueue queue,
            SpeechPlaybackQueue.Highlight highlight) {
        if (queue == null || highlight == null
                || !queue.getRevision().equals(currentRevision)) {
            return false;
        }
        try {
            readerSurface.highlightSpeechRange(highlight.getStartOffset(),
                    highlight.getEndOffset(), highlight.getFollowOffset(),
                    highlight.isParagraphFallback());
            return true;
        } catch (IllegalArgumentException staleWindow) {
            return false;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (readerSurface != null) {
            readerSurface.setKeepScreenOn(readerSurface.isAutoScrolling()
                    && autoScrollCompanionSettings.isKeepScreenOn());
        }
        worker.execute(() -> {
            try {
                Path bookDirectory = new File(getFilesDir(), "books").toPath();
                List<BookLibraryEntry> entries = loadBookLibrary(bookDirectory);
                List<BookLibraryEntry> updated = applyPendingBookProgress(
                        bookDirectory, entries);
                if (!updated.isEmpty()) {
                    runOnUiThread(() -> refreshBookshelf(updated, currentBookId));
                }
            } catch (Exception error) {
                showBookshelfFailure(R.string.bookshelf_progress_save_failed, error);
            }
        });
    }

    @Override
    protected void onPause() {
        cancelAutoScrollResume();
        readerSurface.setKeepScreenOn(false);
        BookProgressSnapshot snapshot = repairApplying || libraryTransitionInProgress
                ? null : captureBookProgress();
        if (snapshot != null) {
            recordPendingBookProgress(snapshot);
            worker.execute(() -> {
                try {
                    persistBookProgress(new File(getFilesDir(), "books").toPath(), snapshot);
                } catch (Exception error) {
                    showBookshelfFailure(R.string.bookshelf_progress_save_failed, error);
                }
            });
        }
        super.onPause();
    }

    @Override
    public Object onRetainNonConfigurationInstance() {
        if (sleepTimer.getMode() == CompanionSleepTimer.Mode.OFF) {
            return null;
        }
        return new RetainedSleepTimer(
                sleepTimer.getMode(), sleepTimer.getTarget(), sleepTimerRevision);
    }

    @Override
    protected void onDestroy() {
        cancelAutoScrollResume();
        if (readerSurface != null) {
            readerSurface.setKeepScreenOn(false);
        }
        sleepTimerHandler.removeCallbacks(sleepTimerTick);
        worker.shutdownNow();
        audioInterruptions.stop();
        speech.shutdown();
        super.onDestroy();
    }

    private enum ChapterEditAction {
        RENAME,
        SPLIT,
        MERGE_NEXT
    }

    private static final class BookState {
        final String baseFileName;
        final String baseRevision;
        final String activeFileName;
        final String activeRevision;
        final String projectionFileName;

        BookState(String baseFileName, String baseRevision, String activeFileName,
                String activeRevision, String projectionFileName) {
            this.baseFileName = baseFileName;
            this.baseRevision = baseRevision;
            this.activeFileName = activeFileName;
            this.activeRevision = activeRevision;
            this.projectionFileName = projectionFileName;
        }
    }

    private static final class BookProgressSnapshot {
        final String bookId;
        final String activeFileName;
        final String activeRevision;
        final String projectionFileName;
        final int anchorOffset;

        BookProgressSnapshot(String bookId, String activeFileName, String activeRevision,
                String projectionFileName, int anchorOffset) {
            this.bookId = bookId;
            this.activeFileName = activeFileName;
            this.activeRevision = activeRevision;
            this.projectionFileName = projectionFileName;
            this.anchorOffset = anchorOffset;
        }
    }

    private static final class RetainedSleepTimer {
        final CompanionSleepTimer.Mode mode;
        final long target;
        final String revision;

        RetainedSleepTimer(CompanionSleepTimer.Mode mode, long target, String revision) {
            this.mode = mode;
            this.target = target;
            this.revision = revision;
        }
    }

    private static final class PendingRepair {
        final RepairFileResult result;

        PendingRepair(RepairFileResult result) {
            this.result = result;
        }
    }
}
