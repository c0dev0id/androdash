package de.codevoid.androdash;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.FocusFinder;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.widget.Toast;

import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private FrameLayout rootLayout;
    private RecyclerView appGrid;
    private AppGridAdapter adapter;
    private LetterBar letterBar;
    private ViewGroup letterScrollView;
    private List<AppModel> allApps;
    private HiddenAppsStore hiddenAppsStore;
    private LetterSortStore letterSortStore;
    private LetterBarPositionStore letterBarPositionStore;
    private AppHistoryStore appHistoryStore;
    private MatchMethodStore matchMethodStore;
    private BookmarkStore bookmarkStore;
    private FolderStore folderStore;
    private int spanCount;
    private boolean wasConfigMode = false;
    private boolean appsDirty = false;
    private boolean isResumed = false;
    private boolean pickingImageForFolder = false;

    private ActivityResultLauncher<Intent> imagePickerLauncher;
    private ActivityResultLauncher<Intent> backupImportLauncher;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            appsDirty = true;
            // Clean up folder membership when an app is uninstalled
            if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction()) && folderStore != null) {
                Uri data = intent.getData();
                if (data != null) {
                    String packageName = data.getSchemeSpecificPart();
                    if (packageName != null) {
                        folderStore.removePackageFromAllFolders(packageName);
                    }
                }
            }
        }
    };

    private static final String DMD_ACTION = "com.thorkracing.wireddevices.keypress";

    // Keydown/keyup latch for the joystick. A full-deflection payload
    // (magnitude 5 on the dominant axis) is treated as a key-down event and
    // triggers exactly one navigation step; "Y0X0" (the neutral sentinel per
    // andRemote2 SPECIFICATION.md §"Signal 1") is the key-up event that
    // re-arms the latch. Any payload received while held is ignored — so
    // natural finger drift at full deflection can't produce repeated moves.
    private boolean joyHeld = false;

    private final BroadcastReceiver remoteListener = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (letterBar == null) return;
            if (intent.getIntExtra("repeat", 0) != 0) return;

            String joy = intent.getStringExtra("joy");
            if (joy != null) {
                // Per andRemote2 SPECIFICATION.md §"Signal 1": joy is a
                // sequence of <axis><magnitude> pairs where axis ∈ {U,D,L,R}
                // and magnitude ∈ {2..5}; "Y0X0" is the neutral sentinel sent
                // once on release. We treat the joystick as a digital key:
                //   - "Y0X0" / "Y0" / "X0" → key-up: re-arm the latch, do nothing.
                //   - dominant axis
                //     at magnitude 5  → key-down: navigate once, hold latch.
                //   - anything else   → ignore (sub-threshold or drift while
                //                        already held).
                if ("Y0X0".equals(joy) || "Y0".equals(joy) || "X0".equals(joy)) {
                    joyHeld = false;
                    return;
                }
                if (joyHeld) return;
                char dominantAxis = 0;
                int dominantMagnitude = 0;
                for (int i = 0; i + 1 < joy.length(); i += 2) {
                    char magChar = joy.charAt(i + 1);
                    if (magChar < '0' || magChar > '9') return;
                    int magnitude = magChar - '0';
                    if (magnitude > dominantMagnitude) {
                        dominantMagnitude = magnitude;
                        dominantAxis = joy.charAt(i);
                    }
                }
                if (dominantMagnitude < 5) return;
                joyHeld = true;
                switch (dominantAxis) {
                    case 'U': navigateFocus(View.FOCUS_UP);    break;
                    case 'D': navigateFocus(View.FOCUS_DOWN);  break;
                    case 'L': navigateFocus(View.FOCUS_LEFT);  break;
                    case 'R': navigateFocus(View.FOCUS_RIGHT); break;
                    default: return;  // unknown axis — no action, no fallback
                }
                // falls through to post() below
            } else {
                if (!intent.hasExtra("key_press")) return;
                int keyCode = intent.getIntExtra("key_press", 0);
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_LEFT:   navigateFocus(View.FOCUS_LEFT);  break;
                    case KeyEvent.KEYCODE_DPAD_RIGHT:  navigateFocus(View.FOCUS_RIGHT); break;
                    case KeyEvent.KEYCODE_DPAD_UP:     navigateFocus(View.FOCUS_UP);    break;
                    case KeyEvent.KEYCODE_DPAD_DOWN:   navigateFocus(View.FOCUS_DOWN);  break;
                    case KeyEvent.KEYCODE_ENTER: {
                        View focused = getCurrentFocus();
                        if (focused != null) focused.performClick();
                        break;
                    }
                    case KeyEvent.KEYCODE_ESCAPE:
                        letterBar.clearAllLetters();
                        letterBar.focusFirstButton();
                        break;
                }
                // falls through to post() below
            }
            // Shared focus fallback — runs after joy navigation and key_press handling.
            // Some actions (e.g. ENTER on a letter button) remove the focused view;
            // defer the check so it runs after updateButtons() completes.
            rootLayout.post(() -> {
                if (getCurrentFocus() == null) letterBar.focusFirstButton();
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        hiddenAppsStore = new HiddenAppsStore(this);
        letterSortStore = new LetterSortStore(this);
        letterBarPositionStore = new LetterBarPositionStore(this);
        appHistoryStore = new AppHistoryStore(this);
        matchMethodStore = new MatchMethodStore(this);
        bookmarkStore = new BookmarkStore(this);
        folderStore = new FolderStore(this);

        rootLayout = findViewById(R.id.rootLayout);
        int baseSpacing = getResources().getDimensionPixelSize(R.dimen.grid_spacing);
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    insets.left + baseSpacing,
                    insets.top + baseSpacing,
                    insets.right + baseSpacing,
                    insets.bottom + baseSpacing);
            return WindowInsetsCompat.CONSUMED;
        });

        appGrid = new RecyclerView(this);
        appGrid.setId(View.generateViewId());
        appGrid.setClipToPadding(false);

        adapter = new AppGridAdapter(this, hiddenAppsStore, letterSortStore,
                letterBarPositionStore, appHistoryStore, matchMethodStore, bookmarkStore, folderStore);
        appGrid.setAdapter(adapter);

        // Register image picker for bookmarks and folders
        imagePickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        if (imageUri == null) return;
                        try {
                            InputStream is = getContentResolver().openInputStream(imageUri);
                            Bitmap bitmap = BitmapFactory.decodeStream(is);
                            if (is != null) is.close();
                            if (bitmap != null) {
                                Bitmap scaled = Bitmap.createScaledBitmap(bitmap, 96, 96, true);
                                if (scaled != bitmap) bitmap.recycle();
                                if (pickingImageForFolder) {
                                    adapter.setPickedFolderIcon(scaled);
                                } else {
                                    adapter.setPickedBookmarkIcon(scaled);
                                }
                            }
                        } catch (Exception ignored) {
                        }
                        pickingImageForFolder = false;
                    }
                });

        backupImportLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri fileUri = result.getData().getData();
                        if (fileUri == null) return;
                        try {
                            InputStream is = getContentResolver().openInputStream(fileUri);
                            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
                            StringBuilder sb = new StringBuilder();
                            String line;
                            while ((line = reader.readLine()) != null) {
                                sb.append(line);
                            }
                            reader.close();
                            if (is != null) is.close();

                            JSONObject json = new JSONObject(sb.toString());
                            BackupManager backupManager = new BackupManager(this, bookmarkStore, folderStore);
                            if (backupManager.importFromJson(json)) {
                                refreshAfterImport();
                                Toast.makeText(this, "Backup restored", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(this, "Invalid backup file", Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {
                            Toast.makeText(this, "Failed to read backup file", Toast.LENGTH_SHORT).show();
                        }
                    }
                });

        adapter.setOnConfigToggleListener(new AppGridAdapter.OnConfigToggleListener() {
            @Override
            public void onShowAllAppsChanged(boolean showAll) {
                refreshDisplayedApps();
            }

            @Override
            public void onLetterSortChanged(boolean usageSort) {
                // Sort mode is already persisted by LetterSortStore;
                // no additional action needed since LetterBar reads it on rebuild
            }

            @Override
            public void onMatchMethodChanged(int method) {
                // Method is persisted by MatchMethodStore;
                // LetterBar reads it dynamically on each filter call
            }

            @Override
            public void onLetterBarPositionChanged(int position) {
                rebuildLayout();
            }

            @Override
            public void onAppHistoryChanged(boolean enabled) {
                refreshDisplayedApps();
            }
        });

        adapter.setOnAppHiddenChangedListener(() -> refreshDisplayedApps());

        adapter.setOnBookmarkChangedListener(new AppGridAdapter.OnBookmarkChangedListener() {
            @Override
            public void onBookmarkChanged() {
                mergeBookmarksIntoApps();
                mergeFoldersIntoApps();
                letterBar.updateApps(allApps);
                refreshDisplayedApps();
            }

            @Override
            public void onPickImageForBookmark() {
                pickingImageForFolder = false;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                imagePickerLauncher.launch(intent);
            }
        });

        adapter.setOnFolderChangedListener(new AppGridAdapter.OnFolderChangedListener() {
            @Override
            public void onFolderChanged() {
                mergeBookmarksIntoApps();
                mergeFoldersIntoApps();
                letterBar.updateApps(allApps);
                refreshDisplayedApps();
            }

            @Override
            public void onFolderClicked(AppModel folder) {
                String folderId = folder.packageName.substring("folder://".length());
                List<AppModel> contents = getFolderContents(folderId);
                letterBar.enterFolderMode(folderId, contents);
                adapter.setActiveFolderId(folderId);
            }

            @Override
            public void onPickImageForFolder() {
                pickingImageForFolder = true;
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setType("image/*");
                imagePickerLauncher.launch(intent);
            }
        });

        adapter.setOnBackupListener(new AppGridAdapter.OnBackupListener() {
            @Override
            public void onExportRequested() {
                performBackupExport();
            }

            @Override
            public void onImportRequested() {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("*/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                backupImportLauncher.launch(intent);
            }
        });

        buildLayout();
        refreshApps();
        registerPackageReceiver();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(remoteListener, new IntentFilter(DMD_ACTION), Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(remoteListener, new IntentFilter(DMD_ACTION));
        }

        if (letterBar == null) return;

        // Exit config mode if returning from background
        if (wasConfigMode) {
            wasConfigMode = false;
            adapter.setConfigMode(false);
        }

        if (appsDirty) {
            appsDirty = false;
            allApps = AppLoader.loadApps(this);
            mergeBookmarksIntoApps();
            mergeFoldersIntoApps();
            letterBar.updateApps(allApps);
        }

        isResumed = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        isResumed = false;
        try {
            unregisterReceiver(remoteListener);
        } catch (Exception ignored) {}
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (letterBar != null && isResumed) {
            letterBar.clearSelection();
        }
    }

    private void buildLayout() {
        rootLayout.removeAllViews();

        int position = letterBarPositionStore.getPosition();
        boolean vertical = letterBarPositionStore.isVertical();

        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        wrapper.setOrientation(vertical ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);

        // Create letterbar scroll + container
        ViewGroup scrollView;
        LinearLayout letterContainer = new LinearLayout(this);
        letterContainer.setOrientation(vertical ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        letterContainer.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        int letterBarPadding = getResources().getDimensionPixelSize(R.dimen.letter_bar_padding);

        if (vertical) {
            ScrollView sv = new ScrollView(this);
            sv.setScrollbarFadingEnabled(true);
            sv.setVerticalScrollBarEnabled(false);
            LinearLayout.LayoutParams svParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT);
            sv.setLayoutParams(svParams);
            if (position == LetterBarPositionStore.POSITION_LEFT) {
                sv.setPadding(0, 0, letterBarPadding, 0);
            } else {
                sv.setPadding(letterBarPadding, 0, 0, 0);
            }
            sv.addView(letterContainer);
            scrollView = sv;
        } else {
            HorizontalScrollView hsv = new HorizontalScrollView(this);
            hsv.setHorizontalScrollBarEnabled(false);
            LinearLayout.LayoutParams hsvParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hsv.setLayoutParams(hsvParams);
            if (position == LetterBarPositionStore.POSITION_TOP) {
                hsv.setPadding(0, 0, 0, letterBarPadding);
            } else {
                hsv.setPadding(0, letterBarPadding, 0, 0);
            }
            hsv.addView(letterContainer);
            scrollView = hsv;
        }

        // Detach appGrid from any parent
        if (appGrid.getParent() != null) {
            ((ViewGroup) appGrid.getParent()).removeView(appGrid);
        }
        LinearLayout.LayoutParams gridParams;
        if (vertical) {
            gridParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT);
        } else {
            gridParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0);
        }
        gridParams.weight = 1;
        appGrid.setLayoutParams(gridParams);

        // Add in correct order
        boolean letterBarFirst = (position == LetterBarPositionStore.POSITION_TOP
                || position == LetterBarPositionStore.POSITION_LEFT);
        if (letterBarFirst) {
            wrapper.addView(scrollView);
            wrapper.addView(appGrid);
        } else {
            wrapper.addView(appGrid);
            wrapper.addView(scrollView);
        }

        rootLayout.addView(wrapper);
        setupGridLayout();

        // Initialize LetterBar
        letterScrollView = scrollView;
        letterBar = new LetterBar(this, letterContainer, scrollView);
        letterBar.setLetterSortStore(letterSortStore);
        letterBar.setMatchMethodStore(matchMethodStore);
        letterBar.setOnFilterChangedListener(filteredApps -> {
            // Sync folder state with adapter
            if (!letterBar.isFolderMode()) {
                adapter.setActiveFolderId(null);
            }

            boolean inConfig = letterBar.isConfigMode();
            if (inConfig != wasConfigMode) {
                wasConfigMode = inConfig;
                appGrid.animate().alpha(0f).setDuration(150).withEndAction(() -> {
                    if (inConfig) {
                        adapter.setConfigMode(true);
                    } else {
                        adapter.setConfigMode(false);
                        refreshDisplayedApps();
                    }
                    appGrid.animate().alpha(1f).setDuration(150).start();
                }).start();
            } else if (!inConfig) {
                refreshDisplayedApps();
            }
        });

        if (allApps != null) {
            letterBar.setApps(allApps);
        }
    }

    private void rebuildLayout() {
        boolean wasInConfig = wasConfigMode;
        buildLayout();
        if (wasInConfig && letterBar != null) {
            // Re-enter config mode after rebuild
            letterBar.enterConfigMode();
            adapter.setConfigMode(true);
            wasConfigMode = true;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(packageReceiver);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        setupGridLayout();
    }

    private void setupGridLayout() {
        if (letterBarPositionStore.isVertical()) {
            spanCount = isPortrait() ? 2 : 4;
        } else {
            spanCount = isPortrait() ? 3 : 5;
        }
        GridLayoutManager layoutManager = new GridLayoutManager(this, spanCount);
        appGrid.setLayoutManager(layoutManager);

        int spacing = getResources().getDimensionPixelSize(R.dimen.grid_spacing);
        // Remove old decorations before adding new one
        while (appGrid.getItemDecorationCount() > 0) {
            appGrid.removeItemDecorationAt(0);
        }
        appGrid.addItemDecoration(new GridSpacingDecoration(spacing));
    }

    private boolean isPortrait() {
        return getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
    }

    private void refreshApps() {
        allApps = AppLoader.loadApps(this);
        mergeBookmarksIntoApps();
        mergeFoldersIntoApps();
        letterBar.setApps(allApps);
    }

    private void mergeBookmarksIntoApps() {
        // Remove any existing bookmarks and folders from allApps
        List<AppModel> realApps = new ArrayList<>();
        for (AppModel app : allApps) {
            if (!app.isBookmark && !app.isFolder) {
                realApps.add(app);
            }
        }

        // Add bookmarks
        List<BookmarkStore.BookmarkData> bookmarks = bookmarkStore.getAll();
        for (BookmarkStore.BookmarkData b : bookmarks) {
            Drawable icon = bookmarkStore.loadIconDrawable(b.id);
            Intent launchIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(b.url));
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            realApps.add(new AppModel(b.label, b.getPackageName(), icon, launchIntent, true));
        }

        Collections.sort(realApps);
        allApps = realApps;
    }

    private void mergeFoldersIntoApps() {
        // Remove any existing folders from allApps
        List<AppModel> withoutFolders = new ArrayList<>();
        for (AppModel app : allApps) {
            if (!app.isFolder) {
                withoutFolders.add(app);
            }
        }

        // Add folders
        List<FolderStore.FolderData> folders = folderStore.getAll();
        for (FolderStore.FolderData f : folders) {
            Drawable icon = folderStore.loadIconDrawable(f.id);
            withoutFolders.add(new AppModel(f.label, f.getPackageName(), icon, null, false, true));
        }

        Collections.sort(withoutFolders);
        allApps = withoutFolders;
    }

    private List<AppModel> getFolderContents(String folderId) {
        List<String> memberPackages = folderStore.getMembers(folderId);
        Set<String> memberSet = new HashSet<>(memberPackages);
        List<AppModel> contents = new ArrayList<>();
        for (AppModel app : allApps) {
            if (memberSet.contains(app.packageName)) {
                contents.add(app);
            }
        }
        Collections.sort(contents);
        return contents;
    }

    private void refreshDisplayedApps() {
        if (appsDirty) {
            appsDirty = false;
            allApps = AppLoader.loadApps(this);
            mergeBookmarksIntoApps();
            mergeFoldersIntoApps();
            letterBar.updateApps(allApps);
            return;
        }

        List<AppModel> letterFiltered = letterBar.getFilteredApps();
        List<AppModel> displayApps;
        if (!adapter.isShowAllApps()) {
            displayApps = new ArrayList<>();
            for (AppModel app : letterFiltered) {
                if (!hiddenAppsStore.isHidden(app.packageName)) {
                    displayApps.add(app);
                }
            }
        } else {
            displayApps = letterFiltered;
        }

        // When not in folder mode, hide items that are in a folder
        if (!letterBar.isFolderMode()) {
            List<AppModel> nonFoldered = new ArrayList<>();
            for (AppModel app : displayApps) {
                if (!folderStore.isInAnyFolder(app.packageName)) {
                    nonFoldered.add(app);
                }
            }
            displayApps = nonFoldered;
        }

        List<AppModel> historyList = new ArrayList<>();
        if (appHistoryStore.isEnabled() && !letterBar.hasSelection()) {
            Map<String, AppModel> appsByPackage = new HashMap<>();
            for (AppModel app : allApps) {
                appsByPackage.put(app.packageName, app);
            }
            List<String> recentPackages = appHistoryStore.getRecentPackages();
            int limit = Math.min(recentPackages.size(), spanCount);
            for (int i = 0; i < limit; i++) {
                String pkg = recentPackages.get(i);
                AppModel app = appsByPackage.get(pkg);
                if (app != null && !hiddenAppsStore.isHidden(pkg) && !hiddenAppsStore.isExcludedFromHistory(pkg)) {
                    historyList.add(app);
                }
            }
        }
        if (!historyList.isEmpty()) {
            Set<String> historyPackages = new HashSet<>();
            for (AppModel app : historyList) {
                historyPackages.add(app.packageName);
            }
            List<AppModel> deduped = new ArrayList<>();
            for (AppModel app : displayApps) {
                if (!historyPackages.contains(app.packageName)) {
                    deduped.add(app);
                }
            }
            displayApps = deduped;
        }

        adapter.setHistoryApps(historyList);
        adapter.updateApps(displayApps);
    }

    private void performBackupExport() {
        BackupManager backupManager = new BackupManager(this, bookmarkStore, folderStore);
        JSONObject json = backupManager.exportToJson();
        if (json == null) {
            Toast.makeText(this, "Failed to create backup", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            String timestamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
            String filename = "androdash-backup-" + timestamp + ".json";
            File cacheFile = new File(getCacheDir(), filename);
            FileOutputStream fos = new FileOutputStream(cacheFile);
            fos.write(json.toString(2).getBytes());
            fos.close();

            Uri fileUri = FileProvider.getUriForFile(this,
                    getPackageName() + ".fileprovider", cacheFile);
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/json");
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(shareIntent, "Save Backup"));
        } catch (Exception e) {
            Toast.makeText(this, "Failed to export backup", Toast.LENGTH_SHORT).show();
        }
    }

    private void refreshAfterImport() {
        hiddenAppsStore = new HiddenAppsStore(this);
        appHistoryStore = new AppHistoryStore(this);
        letterBarPositionStore = new LetterBarPositionStore(this);
        matchMethodStore = new MatchMethodStore(this);
        letterSortStore = new LetterSortStore(this);
        allApps = AppLoader.loadApps(this);
        mergeBookmarksIntoApps();
        mergeFoldersIntoApps();
        rebuildLayout();
    }

    private void registerPackageReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        registerReceiver(packageReceiver, filter, Context.RECEIVER_EXPORTED);
    }

    private void navigateFocus(int direction) {
        View current = getCurrentFocus();
        if (current == null) { letterBar.focusFirstButton(); return; }

        // Search within the letter bar's scroll container first. This scopes
        // FocusFinder to the bar's children and prevents the scroll container
        // itself from being selected (which has no visible focus indicator).
        View next = null;
        if (letterScrollView != null) {
            next = FocusFinder.getInstance().findNextFocus(
                    letterScrollView, current, direction);
        }

        // Fall back to window-wide search for cross-boundary moves (e.g. bar ↔ app grid).
        if (next == null) {
            next = current.focusSearch(direction);
        }

        // Guard: if focusSearch returned the scroll container itself, ignore it.
        if (next == letterScrollView) return;

        if (next != null && next != current) {
            // requestFocusFromTouch exits touch mode before requesting focus.
            // Remote input never goes through ViewRootImpl so touch mode is
            // never exited automatically; requestFocus() would silently fail.
            next.requestFocusFromTouch();
        }
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        // Navigation is handled entirely by the wireddevices broadcast receiver.
        // Consuming SOURCE_JOYSTICK here prevents RecyclerView from scrolling the
        // app grid via its built-in joystick-scroll handler, which would recycle
        // the focused item off-screen and lose focus.
        if (event.isFromSource(InputDevice.SOURCE_CLASS_JOYSTICK)) {
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (letterBar == null || event.getAction() != KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event);
        }
        int keyCode = event.getKeyCode();

        if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            View focused = getCurrentFocus();
            if (focused != null) focused.performClick();
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
            letterBar.clearAllLetters();
            letterBar.focusFirstButton();
            return true;
        }

        if (keyCode == KeyEvent.KEYCODE_DEL) {
            letterBar.removeLastLetter();
            return true;
        }

        int unicode = event.getUnicodeChar();
        if (unicode != 0 && Character.isLetter((char) unicode)) {
            letterBar.selectLetter((char) unicode);
            return true;
        }

        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        // Act as backspace: unselect last selected letter (or gear)
        letterBar.removeLastLetter();
    }
}
