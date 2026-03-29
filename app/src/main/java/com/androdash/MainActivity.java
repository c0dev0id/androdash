package com.androdash;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Bundle;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class MainActivity extends AppCompatActivity {

    private RecyclerView appGrid;
    private AppGridAdapter adapter;
    private LetterBar letterBar;
    private List<AppModel> allApps;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refreshApps();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        setContentView(R.layout.activity_main);

        HorizontalScrollView scrollView = findViewById(R.id.letterScroll);
        LinearLayout letterContainer = findViewById(R.id.letterContainer);
        appGrid = findViewById(R.id.appGrid);

        adapter = new AppGridAdapter(this);
        appGrid.setAdapter(adapter);
        setupGridLayout();

        letterBar = new LetterBar(this, letterContainer, scrollView);
        letterBar.setOnFilterChangedListener(filteredApps -> adapter.updateApps(filteredApps));

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
        // Act as backspace: unselect last selected letter
        letterBar.removeLastLetter();
    }
}
