package com.androdash;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private RecyclerView appGrid;
    private AppGridAdapter adapter;
    private LetterBar letterBar;
    private List<AppModel> allApps;
    private HiddenAppsStore hiddenAppsStore;
    private boolean showAllApps = false;
    private boolean wasConfigMode = false;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshApps();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        hiddenAppsStore = new HiddenAppsStore(this);

        View rootView = findViewById(R.id.rootLayout);
        int baseSpacing = getResources().getDimensionPixelSize(R.dimen.grid_spacing);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    insets.left + baseSpacing,
                    insets.top + baseSpacing,
                    insets.right + baseSpacing,
                    insets.bottom + baseSpacing);
            return WindowInsetsCompat.CONSUMED;
        });

        HorizontalScrollView scrollView = findViewById(R.id.letterScroll);
        LinearLayout letterContainer = findViewById(R.id.letterContainer);
        appGrid = findViewById(R.id.appGrid);

        adapter = new AppGridAdapter(this, hiddenAppsStore);
        appGrid.setAdapter(adapter);
        setupGridLayout();

        adapter.setOnConfigToggleListener(showAll -> {
            this.showAllApps = showAll;
            adapter.setShowAllApps(showAll);
        });

        adapter.setOnAppHiddenChangedListener(() -> refreshDisplayedApps());

        letterBar = new LetterBar(this, letterContainer, scrollView);
        letterBar.setOnFilterChangedListener(filteredApps -> {
            boolean inConfig = letterBar.isConfigMode();
            if (inConfig != wasConfigMode) {
                // Mode changed — crossfade
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

        refreshApps();
        registerPackageReceiver();
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
        int spanCount = isPortrait() ? 3 : 5;
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
        List<AppModel> letterFiltered = letterBar.getFilteredApps();
        if (!showAllApps) {
            List<AppModel> visible = new ArrayList<>();
            for (AppModel app : letterFiltered) {
                if (!hiddenAppsStore.isHidden(app.packageName)) {
                    visible.add(app);
                }
            }
            adapter.updateApps(visible);
        } else {
            adapter.updateApps(letterFiltered);
        }
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
    public void onBackPressed() {
        // Act as backspace: unselect last selected letter (or gear)
        letterBar.removeLastLetter();
    }
}
