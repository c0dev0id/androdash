package com.androdash;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Process;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AppGridAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_APP = 0;
    private static final int VIEW_TYPE_CONFIG = 1;
    private static final int VIEW_TYPE_HISTORY = 2;

    public interface OnConfigToggleListener {
        void onShowAllAppsChanged(boolean showAll);
        void onLetterSortChanged(boolean usageSort);
        void onMatchMethodChanged(int method);
        void onLetterBarPositionChanged(int position);
        void onAppHistoryChanged(boolean enabled);
    }

    public interface OnAppHiddenChangedListener {
        void onAppHiddenChanged();
    }

    private final List<AppModel> apps = new ArrayList<>();
    private final List<AppModel> historyApps = new ArrayList<>();
    private final Context context;
    private final HiddenAppsStore hiddenAppsStore;
    private final LetterSortStore letterSortStore;
    private final LetterBarPositionStore letterBarPositionStore;
    private final AppHistoryStore appHistoryStore;
    private final MatchMethodStore matchMethodStore;
    private boolean configMode = false;
    private boolean showAllApps = false;
    private OnConfigToggleListener configToggleListener;
    private OnAppHiddenChangedListener appHiddenChangedListener;
    private boolean isUpdating = false;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public AppGridAdapter(Context context, HiddenAppsStore hiddenAppsStore,
                          LetterSortStore letterSortStore, LetterBarPositionStore letterBarPositionStore,
                          AppHistoryStore appHistoryStore, MatchMethodStore matchMethodStore) {
        this.context = context;
        this.hiddenAppsStore = hiddenAppsStore;
        this.letterSortStore = letterSortStore;
        this.letterBarPositionStore = letterBarPositionStore;
        this.appHistoryStore = appHistoryStore;
        this.matchMethodStore = matchMethodStore;
    }

    public void setOnConfigToggleListener(OnConfigToggleListener listener) {
        this.configToggleListener = listener;
    }

    public void setOnAppHiddenChangedListener(OnAppHiddenChangedListener listener) {
        this.appHiddenChangedListener = listener;
    }

    public void setConfigMode(boolean configMode) {
        this.configMode = configMode;
        notifyDataSetChanged();
    }

    public void setShowAllApps(boolean showAllApps) {
        this.showAllApps = showAllApps;
        if (!configMode) {
            notifyDataSetChanged();
        }
    }

    public boolean isShowAllApps() {
        return showAllApps;
    }

    public void setHistoryApps(List<AppModel> history) {
        this.historyApps.clear();
        this.historyApps.addAll(history);
    }

    public void updateApps(List<AppModel> newApps) {
        apps.clear();
        apps.addAll(newApps);
        notifyDataSetChanged();
    }

    @Override
    public int getItemViewType(int position) {
        if (configMode) return VIEW_TYPE_CONFIG;
        if (position < historyApps.size()) return VIEW_TYPE_HISTORY;
        return VIEW_TYPE_APP;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_CONFIG) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_config, parent, false);
            return new ConfigViewHolder(view);
        }
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app, parent, false);
        return new AppViewHolder(view);
    }

    private void bindHistoryItem(AppViewHolder holder, int position) {
        AppModel app = historyApps.get(position);
        holder.icon.setImageDrawable(app.icon);
        holder.label.setText(app.label);
        holder.itemView.setAlpha(1.0f);
        holder.itemView.setBackgroundResource(R.drawable.bg_app_card_history);

        holder.itemView.setOnClickListener(v -> {
            appHistoryStore.recordLaunch(app.packageName);
            context.startActivity(app.launchIntent);
        });

        holder.itemView.setOnLongClickListener(v -> {
            showAppMenu(app);
            return true;
        });
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (configMode) {
            bindConfigItem((ConfigViewHolder) holder, position);
        } else if (position < historyApps.size()) {
            bindHistoryItem((AppViewHolder) holder, position);
        } else {
            bindAppItem((AppViewHolder) holder, position - historyApps.size());
        }
    }

    private void bindAppItem(AppViewHolder holder, int position) {
        AppModel app = apps.get(position);
        holder.icon.setImageDrawable(app.icon);
        holder.label.setText(app.label);
        holder.itemView.setBackgroundResource(R.drawable.bg_app_card);

        boolean isHidden = hiddenAppsStore.isHidden(app.packageName);
        holder.itemView.setAlpha(isHidden && showAllApps ? 0.4f : 1.0f);

        holder.itemView.setOnClickListener(v -> {
            appHistoryStore.recordLaunch(app.packageName);
            context.startActivity(app.launchIntent);
        });

        holder.itemView.setOnLongClickListener(v -> {
            showAppMenu(app);
            return true;
        });
    }

    private void bindConfigItem(ConfigViewHolder holder, int position) {
        if (position == 0) {
            holder.label.setText("Show all Apps");
            holder.itemView.setBackgroundResource(
                    showAllApps ? R.drawable.bg_button_selected : R.drawable.bg_app_card);
            holder.itemView.setOnClickListener(v -> {
                showAllApps = !showAllApps;
                holder.itemView.setBackgroundResource(
                        showAllApps ? R.drawable.bg_button_selected : R.drawable.bg_app_card);
                if (configToggleListener != null) {
                    configToggleListener.onShowAllAppsChanged(showAllApps);
                }
            });
        } else if (position == 1) {
            boolean usageSort = letterSortStore.isUsageSort();
            holder.label.setText(usageSort ? "Lettersort: Usage" : "Lettersort: ABC");
            holder.itemView.setBackgroundResource(
                    usageSort ? R.drawable.bg_button_selected : R.drawable.bg_app_card);
            holder.itemView.setOnClickListener(v -> {
                boolean newState = !letterSortStore.isUsageSort();
                letterSortStore.setUsageSort(newState);
                holder.label.setText(newState ? "Lettersort: Usage" : "Lettersort: ABC");
                holder.itemView.setBackgroundResource(
                        newState ? R.drawable.bg_button_selected : R.drawable.bg_app_card);
                if (configToggleListener != null) {
                    configToggleListener.onLetterSortChanged(newState);
                }
            });
        } else if (position == 2) {
            int method = matchMethodStore.getMethod();
            holder.label.setText("Match: " + matchMethodStore.getLabel(method));
            holder.itemView.setBackgroundResource(R.drawable.bg_app_card);
            holder.itemView.setOnClickListener(v -> {
                int newMethod = matchMethodStore.nextMethod();
                holder.label.setText("Match: " + matchMethodStore.getLabel(newMethod));
                if (configToggleListener != null) {
                    configToggleListener.onMatchMethodChanged(newMethod);
                }
            });
        } else if (position == 3) {
            int pos = letterBarPositionStore.getPosition();
            holder.label.setText("Letterbar: " + letterBarPositionStore.getArrow(pos));
            holder.itemView.setBackgroundResource(R.drawable.bg_app_card);
            holder.itemView.setOnClickListener(v -> {
                int newPos = letterBarPositionStore.nextPosition();
                holder.label.setText("Letterbar: " + letterBarPositionStore.getArrow(newPos));
                if (configToggleListener != null) {
                    configToggleListener.onLetterBarPositionChanged(newPos);
                }
            });
        } else if (position == 4) {
            boolean historyEnabled = appHistoryStore.isEnabled();
            holder.label.setText("App History");
            holder.itemView.setBackgroundResource(
                    historyEnabled ? R.drawable.bg_button_selected : R.drawable.bg_app_card);
            holder.itemView.setOnClickListener(v -> {
                boolean newState = !appHistoryStore.isEnabled();
                appHistoryStore.setEnabled(newState);
                holder.itemView.setBackgroundResource(
                        newState ? R.drawable.bg_button_selected : R.drawable.bg_app_card);
                if (configToggleListener != null) {
                    configToggleListener.onAppHistoryChanged(newState);
                }
            });
        } else if (position == 5) {
            holder.label.setText("Version: " + BuildConfig.GIT_HASH);
            holder.itemView.setBackgroundResource(R.drawable.bg_app_card);
            holder.itemView.setOnClickListener(v -> {
                if (isUpdating) return;
                checkForUpdate(holder);
            });
        }
    }

    private void checkForUpdate(ConfigViewHolder holder) {
        isUpdating = true;
        holder.label.setText("Checking...");

        executor.submit(() -> {
            try {
                URL url = new URL("https://api.github.com/repos/c0dev0id/androdash/releases/tags/nightly");
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestProperty("Accept", "application/vnd.github+json");
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                InputStream is = conn.getInputStream();
                byte[] buf = new byte[4096];
                StringBuilder sb = new StringBuilder();
                int n;
                while ((n = is.read(buf)) != -1) {
                    sb.append(new String(buf, 0, n));
                }
                is.close();
                conn.disconnect();

                JSONObject release = new JSONObject(sb.toString());
                JSONArray assets = release.getJSONArray("assets");
                if (assets.length() == 0) {
                    runOnUi(() -> {
                        holder.label.setText("No release found");
                        isUpdating = false;
                    });
                    return;
                }

                JSONObject asset = assets.getJSONObject(0);
                String assetName = asset.getString("name");
                String downloadUrl = asset.getString("browser_download_url");

                // Extract hash from filename: androdash-nightly-<hash>.apk
                String remoteHash = assetName.replace("androdash-nightly-", "").replace(".apk", "");

                if (remoteHash.equals(BuildConfig.GIT_HASH)) {
                    runOnUi(() -> {
                        holder.label.setText("Version: " + BuildConfig.GIT_HASH + " (latest)");
                        isUpdating = false;
                    });
                    return;
                }

                // New version available — download it
                runOnUi(() -> holder.label.setText("Downloading 0%"));
                downloadAndInstall(holder, downloadUrl, assetName);

            } catch (Exception e) {
                runOnUi(() -> {
                    holder.label.setText("Update check failed");
                    holder.itemView.setBackgroundResource(R.drawable.bg_app_card);
                    isUpdating = false;
                });
            }
        });
    }

    private void downloadAndInstall(ConfigViewHolder holder, String downloadUrl, String fileName) {
        try {
            // Set up progress drawable
            float cornerRadius = context.getResources().getDimension(R.dimen.corner_radius);

            GradientDrawable baseBg = new GradientDrawable();
            baseBg.setShape(GradientDrawable.RECTANGLE);
            baseBg.setColor(ContextCompat.getColor(context, R.color.surface_card));
            baseBg.setCornerRadius(cornerRadius);

            GradientDrawable progressFill = new GradientDrawable();
            progressFill.setShape(GradientDrawable.RECTANGLE);
            progressFill.setColor(ContextCompat.getColor(context, R.color.progress_fill));
            progressFill.setCornerRadius(cornerRadius);

            ClipDrawable clipDrawable = new ClipDrawable(progressFill, Gravity.START, ClipDrawable.HORIZONTAL);
            clipDrawable.setLevel(0);

            LayerDrawable layerDrawable = new LayerDrawable(new android.graphics.drawable.Drawable[]{baseBg, clipDrawable});

            runOnUi(() -> holder.itemView.setBackground(layerDrawable));

            // Download
            URL url = new URL(downloadUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);
            int totalSize = conn.getContentLength();

            File apkFile = new File(context.getCacheDir(), "update.apk");
            InputStream is = new BufferedInputStream(conn.getInputStream());
            FileOutputStream fos = new FileOutputStream(apkFile);

            byte[] buf = new byte[8192];
            int downloaded = 0;
            int n;
            while ((n = is.read(buf)) != -1) {
                fos.write(buf, 0, n);
                downloaded += n;
                if (totalSize > 0) {
                    int pct = (int) ((downloaded * 100L) / totalSize);
                    int level = pct * 100; // ClipDrawable level: 0-10000
                    runOnUi(() -> {
                        clipDrawable.setLevel(level);
                        holder.label.setText("Downloading " + pct + "%");
                    });
                }
            }
            fos.close();
            is.close();
            conn.disconnect();

            // Install
            runOnUi(() -> {
                holder.label.setText("Installing...");
                Uri uri = FileProvider.getUriForFile(context,
                        context.getPackageName() + ".fileprovider", apkFile);
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setDataAndType(uri, "application/vnd.android.package-archive");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);

                holder.label.setText("Version: " + BuildConfig.GIT_HASH);
                holder.itemView.setBackgroundResource(R.drawable.bg_app_card);
                isUpdating = false;
            });

        } catch (Exception e) {
            runOnUi(() -> {
                holder.label.setText("Download failed");
                holder.itemView.setBackgroundResource(R.drawable.bg_app_card);
                isUpdating = false;
            });
        }
    }

    private void runOnUi(Runnable r) {
        ((Activity) context).runOnUiThread(r);
    }

    @Override
    public int getItemCount() {
        if (configMode) return 6;
        return historyApps.size() + apps.size();
    }

    private void showAppMenu(AppModel app) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_app_menu, null);

        AlertDialog dialog = new AlertDialog.Builder(context, R.style.Theme_Androdash_Dialog)
                .setView(dialogView)
                .create();

        ImageView headerIcon = dialogView.findViewById(R.id.dialogAppIcon);
        TextView headerLabel = dialogView.findViewById(R.id.dialogAppLabel);
        headerIcon.setImageDrawable(app.icon);
        headerLabel.setText(app.label);

        boolean isHidden = hiddenAppsStore.isHidden(app.packageName);

        TextView btnHideShow = dialogView.findViewById(R.id.btnHideShow);
        btnHideShow.setText(isHidden ? "Show" : "Hide");
        btnHideShow.setOnClickListener(v -> {
            if (isHidden) {
                hiddenAppsStore.showApp(app.packageName);
            } else {
                hiddenAppsStore.hideApp(app.packageName);
            }
            if (appHiddenChangedListener != null) {
                appHiddenChangedListener.onAppHiddenChanged();
            }
            dialog.dismiss();
        });

        TextView btnUninstall = dialogView.findViewById(R.id.btnUninstall);
        btnUninstall.setOnClickListener(v -> {
            Intent uninstall = new Intent(Intent.ACTION_DELETE,
                    Uri.parse("package:" + app.packageName));
            context.startActivity(uninstall);
            dialog.dismiss();
        });

        TextView btnAppInfo = dialogView.findViewById(R.id.btnAppInfo);
        btnAppInfo.setOnClickListener(v -> {
            Intent info = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + app.packageName));
            context.startActivity(info);
            dialog.dismiss();
        });

        TextView btnCancel = dialogView.findViewById(R.id.btnCancel);
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        LinearLayout shortcutContainer = dialogView.findViewById(R.id.shortcutContainer);
        TextView noShortcutsText = dialogView.findViewById(R.id.noShortcutsText);

        LauncherApps launcherApps = (LauncherApps) context.getSystemService(Context.LAUNCHER_APPS_SERVICE);
        boolean hasShortcuts = false;

        if (launcherApps != null && launcherApps.hasShortcutHostPermission()) {
            try {
                LauncherApps.ShortcutQuery query = new LauncherApps.ShortcutQuery();
                query.setPackage(app.packageName);
                query.setQueryFlags(
                        LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC
                                | LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST);

                List<ShortcutInfo> shortcuts = launcherApps.getShortcuts(query, Process.myUserHandle());

                if (shortcuts != null && !shortcuts.isEmpty()) {
                    hasShortcuts = true;
                    int spacing = context.getResources().getDimensionPixelSize(R.dimen.grid_spacing);

                    for (int i = 0; i < shortcuts.size(); i++) {
                        ShortcutInfo shortcut = shortcuts.get(i);
                        View item = LayoutInflater.from(context)
                                .inflate(R.layout.item_shortcut, shortcutContainer, false);

                        ImageView icon = item.findViewById(R.id.shortcutIcon);
                        TextView label = item.findViewById(R.id.shortcutLabel);

                        Drawable shortcutIcon = launcherApps.getShortcutIconDrawable(shortcut,
                                context.getResources().getDisplayMetrics().densityDpi);
                        if (shortcutIcon != null) {
                            icon.setImageDrawable(shortcutIcon);
                        } else {
                            icon.setImageDrawable(app.icon);
                        }

                        CharSequence shortLabel = shortcut.getShortLabel();
                        label.setText(shortLabel != null ? shortLabel : "");

                        item.setOnClickListener(v -> {
                            launcherApps.startShortcut(shortcut, null, null);
                            dialog.dismiss();
                        });

                        if (i > 0) {
                            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) item.getLayoutParams();
                            if (lp == null) {
                                lp = new LinearLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        context.getResources().getDimensionPixelSize(R.dimen.app_card_height));
                            }
                            lp.topMargin = spacing;
                            item.setLayoutParams(lp);
                        }

                        shortcutContainer.addView(item);
                    }
                }
            } catch (Exception e) {
                // Shortcut query failed, show fallback
            }
        }

        if (!hasShortcuts) {
            noShortcutsText.setVisibility(View.VISIBLE);
            if (launcherApps == null || !launcherApps.hasShortcutHostPermission()) {
                noShortcutsText.setText("Set as default launcher\nfor shortcuts");
            }
        }

        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(R.drawable.bg_dialog);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.9);
            window.setAttributes(params);
        }
    }

    static class AppViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView label;

        AppViewHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.appIcon);
            label = itemView.findViewById(R.id.appLabel);
        }
    }

    static class ConfigViewHolder extends RecyclerView.ViewHolder {
        final TextView label;

        ConfigViewHolder(View itemView) {
            super(itemView);
            label = itemView.findViewById(R.id.configLabel);
        }
    }
}
