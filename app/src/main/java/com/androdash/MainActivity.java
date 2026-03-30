package com.androdash;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private FrameLayout rootLayout;
    private RecyclerView appGrid;
    private AppGridAdapter adapter;
    private LetterBar letterBar;
    private List<AppModel> allApps;
    private HiddenAppsStore hiddenAppsStore;
    private LetterSortStore letterSortStore;
    private LetterBarPositionStore letterBarPositionStore;
    private AppHistoryStore appHistoryStore;
    private int spanCount;
    private boolean showAllApps = false;
    private boolean wasConfigMode = false;
    private boolean appsDirty = false;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            appsDirty = true;
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

        adapter = new AppGridAdapter(this, hiddenAppsStore, letterSortStore, letterBarPositionStore, appHistoryStore);
        appGrid.setAdapter(adapter);

        adapter.setOnConfigToggleListener(new AppGridAdapter.OnConfigToggleListener() {
            @Override
            public void onShowAllAppsChanged(boolean showAll) {
                showAllApps = showAll;
                adapter.setShowAllApps(showAll);
            }

            @Override
            public void onLetterSortChanged(boolean usageSort) {
                // Sort mode is already persisted by LetterSortStore;
                // no additional action needed since LetterBar reads it on rebuild
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

        buildLayout();
        refreshApps();
        registerPackageReceiver();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (letterBar == null) return;

        // Exit config mode if returning from background
        if (wasConfigMode) {
            wasConfigMode = false;
            adapter.setConfigMode(false);
        }

        if (appsDirty) {
            appsDirty = false;
            allApps = AppLoader.loadApps(this);
            letterBar.setApps(allApps);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
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
        letterBar = new LetterBar(this, letterContainer, scrollView);
        letterBar.setLetterSortStore(letterSortStore);
        letterBar.setOnFilterChangedListener(filteredApps -> {
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
        letterBar.setApps(allApps);
    }

    private void refreshDisplayedApps() {
        if (appsDirty) {
            appsDirty = false;
            allApps = AppLoader.loadApps(this);
            letterBar.updateApps(allApps);
            return;
        }

        List<AppModel> letterFiltered = letterBar.getFilteredApps();
        List<AppModel> displayApps;
        if (!showAllApps) {
            displayApps = new ArrayList<>();
            for (AppModel app : letterFiltered) {
                if (!hiddenAppsStore.isHidden(app.packageName)) {
                    displayApps.add(app);
                }
            }
        } else {
            displayApps = letterFiltered;
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
                if (app != null && !hiddenAppsStore.isHidden(pkg)) {
                    historyList.add(app);
                }
            }
        }
        adapter.setHistoryApps(historyList);
        adapter.updateApps(displayApps);
    }

    private void registerPackageReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        registerReceiver(packageReceiver, filter, Context.RECEIVER_EXPORTED);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (letterBar == null || event.getAction() != KeyEvent.ACTION_DOWN) {
            return super.dispatchKeyEvent(event);
        }
        int keyCode = event.getKeyCode();

        // Backspace → remove last selected letter
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            letterBar.removeLastLetter();
            return true;
        }

        // Enter → launch first app in the filtered result
        if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
            AppModel first = letterBar.getFirstFilteredApp();
            if (first != null && first.launchIntent != null) {
                appHistoryStore.recordLaunch(first.packageName);
                startActivity(first.launchIntent);
            }
            return true;
        }

        // Letter keys → select the matching letter on the bar if available
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
