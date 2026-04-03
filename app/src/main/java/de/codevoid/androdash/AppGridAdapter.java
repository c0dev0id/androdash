package de.codevoid.androdash;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
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
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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

    public interface OnBookmarkChangedListener {
        void onBookmarkChanged();
        void onPickImageForBookmark();
    }

    public interface OnFolderChangedListener {
        void onFolderChanged();
        void onFolderClicked(AppModel folder);
        void onPickImageForFolder();
    }

    public interface OnBackupListener {
        void onExportRequested();
        void onImportRequested();
    }

    private final List<AppModel> apps = new ArrayList<>();
    private final List<AppModel> historyApps = new ArrayList<>();
    private final Context context;
    private final HiddenAppsStore hiddenAppsStore;
    private final LetterSortStore letterSortStore;
    private final LetterBarPositionStore letterBarPositionStore;
    private final AppHistoryStore appHistoryStore;
    private final MatchMethodStore matchMethodStore;
    private final BookmarkStore bookmarkStore;
    private final FolderStore folderStore;
    private boolean configMode = false;
    private boolean showAllApps = false;
    private OnConfigToggleListener configToggleListener;
    private OnAppHiddenChangedListener appHiddenChangedListener;
    private OnBookmarkChangedListener bookmarkChangedListener;
    private OnFolderChangedListener folderChangedListener;
    private OnBackupListener backupListener;
    private boolean isUpdating = false;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // Transient state for bookmark image picker
    private Bitmap pendingBookmarkIcon;
    private ImageView pendingBookmarkIconPreview;

    // Transient state for folder image picker
    private Bitmap pendingFolderIcon;
    private ImageView pendingFolderIconPreview;

    // Active folder context (set when viewing inside a folder)
    private String activeFolderId = null;

    public AppGridAdapter(Context context, HiddenAppsStore hiddenAppsStore,
                          LetterSortStore letterSortStore, LetterBarPositionStore letterBarPositionStore,
                          AppHistoryStore appHistoryStore, MatchMethodStore matchMethodStore,
                          BookmarkStore bookmarkStore, FolderStore folderStore) {
        this.context = context;
        this.hiddenAppsStore = hiddenAppsStore;
        this.letterSortStore = letterSortStore;
        this.letterBarPositionStore = letterBarPositionStore;
        this.appHistoryStore = appHistoryStore;
        this.matchMethodStore = matchMethodStore;
        this.bookmarkStore = bookmarkStore;
        this.folderStore = folderStore;
    }

    public void setOnConfigToggleListener(OnConfigToggleListener listener) {
        this.configToggleListener = listener;
    }

    public void setOnAppHiddenChangedListener(OnAppHiddenChangedListener listener) {
        this.appHiddenChangedListener = listener;
    }

    public void setOnBookmarkChangedListener(OnBookmarkChangedListener listener) {
        this.bookmarkChangedListener = listener;
    }

    public void setOnFolderChangedListener(OnFolderChangedListener listener) {
        this.folderChangedListener = listener;
    }

    public void setOnBackupListener(OnBackupListener listener) {
        this.backupListener = listener;
    }

    public void setPickedBookmarkIcon(Bitmap bitmap) {
        this.pendingBookmarkIcon = bitmap;
        if (pendingBookmarkIconPreview != null) {
            pendingBookmarkIconPreview.setImageBitmap(bitmap);
        }
    }

    public void setPickedFolderIcon(Bitmap bitmap) {
        this.pendingFolderIcon = bitmap;
        if (pendingFolderIconPreview != null) {
            pendingFolderIconPreview.setImageBitmap(bitmap);
        }
    }

    public void setActiveFolderId(String folderId) {
        this.activeFolderId = folderId;
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

    private void launchApp(AppModel app) {
        appHistoryStore.recordLaunch(app.packageName);
        context.startActivity(app.launchIntent);
    }

    private void bindHistoryItem(AppViewHolder holder, int position) {
        AppModel app = historyApps.get(position);
        if (app.icon != null) {
            holder.icon.setImageDrawable(app.icon);
        } else if (app.isFolder) {
            holder.icon.setImageDrawable(createDefaultFolderIcon());
        }
        holder.label.setText(app.label);
        holder.itemView.setAlpha(1.0f);
        holder.itemView.setBackgroundResource(R.drawable.bg_app_card_history);

        holder.itemView.setOnClickListener(v -> launchApp(app));

        holder.itemView.setOnLongClickListener(v -> {
            if (app.isBookmark) {
                showBookmarkMenu(app);
            } else {
                showAppMenu(app);
            }
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
        if (app.icon != null) {
            holder.icon.setImageDrawable(app.icon);
        } else if (app.isFolder) {
            holder.icon.setImageDrawable(createDefaultFolderIcon());
        }
        holder.label.setText(app.label);
        holder.itemView.setBackgroundResource(R.drawable.bg_app_card);

        boolean isHidden = hiddenAppsStore.isHidden(app.packageName);
        holder.itemView.setAlpha(isHidden && showAllApps ? 0.4f : 1.0f);

        if (app.isFolder) {
            holder.itemView.setOnClickListener(v -> {
                if (folderChangedListener != null) {
                    folderChangedListener.onFolderClicked(app);
                }
            });
            holder.itemView.setOnLongClickListener(v -> {
                showFolderMenu(app);
                return true;
            });
        } else {
            holder.itemView.setOnClickListener(v -> launchApp(app));
            holder.itemView.setOnLongClickListener(v -> {
                if (app.isBookmark) {
                    showBookmarkMenu(app);
                } else {
                    showAppMenu(app);
                }
                return true;
            });
        }
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
        } else if (position == 6) {
            holder.label.setText("Add Bookmark");
            holder.itemView.setBackgroundResource(R.drawable.bg_app_card);
            holder.itemView.setOnClickListener(v -> showBookmarkDialog(null));
        } else if (position == 7) {
            holder.label.setText("Add Folder");
            holder.itemView.setBackgroundResource(R.drawable.bg_app_card);
            holder.itemView.setOnClickListener(v -> showFolderDialog(null));
        } else if (position == 8) {
            holder.label.setText("Backup");
            holder.itemView.setBackgroundResource(R.drawable.bg_app_card);
            holder.itemView.setOnClickListener(v -> showBackupDialog());
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
        if (configMode) return 9;
        return historyApps.size() + apps.size();
    }

    // ---- Bookmark dialog (Add / Edit) ----

    private void showBookmarkDialog(BookmarkStore.BookmarkData existing) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_bookmark, null);

        AlertDialog dialog = new AlertDialog.Builder(context, R.style.Theme_Androdash_Dialog)
                .setView(dialogView)
                .create();

        TextView title = dialogView.findViewById(R.id.bookmarkDialogTitle);
        EditText labelField = dialogView.findViewById(R.id.bookmarkLabel);
        EditText urlField = dialogView.findViewById(R.id.bookmarkUrl);
        ImageView iconPreview = dialogView.findViewById(R.id.bookmarkIconPreview);
        TextView btnPickImage = dialogView.findViewById(R.id.btnPickImage);
        TextView btnCancel = dialogView.findViewById(R.id.btnBookmarkCancel);
        TextView btnSave = dialogView.findViewById(R.id.btnBookmarkSave);

        pendingBookmarkIcon = null;
        pendingBookmarkIconPreview = iconPreview;

        if (existing != null) {
            title.setText("Edit Bookmark");
            labelField.setText(existing.label);
            urlField.setText(existing.url);
            Drawable existingIcon = bookmarkStore.loadIconDrawable(existing.id);
            if (existingIcon != null) {
                iconPreview.setImageDrawable(existingIcon);
            }
        }

        btnPickImage.setOnClickListener(v -> {
            if (bookmarkChangedListener != null) {
                bookmarkChangedListener.onPickImageForBookmark();
            }
        });

        btnCancel.setOnClickListener(v -> {
            pendingBookmarkIconPreview = null;
            pendingBookmarkIcon = null;
            dialog.dismiss();
        });

        btnSave.setOnClickListener(v -> {
            String label = labelField.getText().toString().trim();
            String url = urlField.getText().toString().trim();

            if (label.isEmpty() || url.isEmpty()) return;

            // Ensure URL has a scheme
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "https://" + url;
            }

            String id = existing != null ? existing.id : BookmarkStore.generateId();
            boolean hasCustomIcon = pendingBookmarkIcon != null
                    || (existing != null && existing.hasCustomIcon && pendingBookmarkIcon == null);

            BookmarkStore.BookmarkData data = new BookmarkStore.BookmarkData(
                    id, label, url, hasCustomIcon);

            if (pendingBookmarkIcon != null) {
                bookmarkStore.saveIcon(id, pendingBookmarkIcon);
                data.hasCustomIcon = true;
            }

            bookmarkStore.save(data);

            // If no custom icon, download favicon in background
            if (!data.hasCustomIcon) {
                String faviconUrl = url;
                downloadFavicon(id, faviconUrl);
            }

            pendingBookmarkIconPreview = null;
            pendingBookmarkIcon = null;
            dialog.dismiss();

            if (bookmarkChangedListener != null) {
                bookmarkChangedListener.onBookmarkChanged();
            }
        });

        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(R.drawable.bg_dialog);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.9);
            window.setAttributes(params);
        }
    }

    // ---- Bookmark long-press menu ----

    private void showBookmarkMenu(AppModel app) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_bookmark_menu, null);

        AlertDialog dialog = new AlertDialog.Builder(context, R.style.Theme_Androdash_Dialog)
                .setView(dialogView)
                .create();

        ImageView headerIcon = dialogView.findViewById(R.id.dialogBookmarkIcon);
        TextView headerLabel = dialogView.findViewById(R.id.dialogBookmarkLabel);
        headerIcon.setImageDrawable(app.icon);
        headerLabel.setText(app.label);

        String bookmarkId = app.packageName.substring("bookmark://".length());

        boolean isBookmarkExcluded = hiddenAppsStore.isExcludedFromHistory(app.packageName);
        TextView btnHideFromHistory = dialogView.findViewById(R.id.btnHideFromHistory);
        btnHideFromHistory.setText(isBookmarkExcluded ? "Show in History" : "Hide from History");
        btnHideFromHistory.setOnClickListener(v -> {
            if (isBookmarkExcluded) {
                hiddenAppsStore.showApp(app.packageName);
            } else {
                hiddenAppsStore.excludeFromHistory(app.packageName);
            }
            if (appHiddenChangedListener != null) appHiddenChangedListener.onAppHiddenChanged();
            dialog.dismiss();
        });

        TextView btnEdit = dialogView.findViewById(R.id.btnEditBookmark);
        btnEdit.setOnClickListener(v -> {
            dialog.dismiss();
            BookmarkStore.BookmarkData data = bookmarkStore.getById(bookmarkId);
            if (data != null) {
                showBookmarkDialog(data);
            }
        });

        TextView btnDelete = dialogView.findViewById(R.id.btnDeleteBookmark);
        btnDelete.setOnClickListener(v -> {
            bookmarkStore.delete(bookmarkId);
            dialog.dismiss();
            if (bookmarkChangedListener != null) {
                bookmarkChangedListener.onBookmarkChanged();
            }
        });

        TextView btnAddToFolder = dialogView.findViewById(R.id.btnAddToFolder);
        if (activeFolderId != null) {
            FolderStore.FolderData folder = folderStore.getById(activeFolderId);
            String folderName = folder != null ? folder.label : "Folder";
            btnAddToFolder.setText("Remove from " + folderName);
            btnAddToFolder.setVisibility(View.VISIBLE);
            btnAddToFolder.setOnClickListener(v -> {
                folderStore.removeFromFolder(activeFolderId, app.packageName);
                dialog.dismiss();
                if (folderChangedListener != null) {
                    folderChangedListener.onFolderChanged();
                }
            });
        } else if (folderStore.hasFolders()) {
            String existingFolderId = folderStore.getFolderForPackage(app.packageName);
            if (existingFolderId != null) {
                FolderStore.FolderData existingFolder = folderStore.getById(existingFolderId);
                String folderName = existingFolder != null ? existingFolder.label : "Folder";
                btnAddToFolder.setText("Remove from " + folderName);
                btnAddToFolder.setVisibility(View.VISIBLE);
                btnAddToFolder.setOnClickListener(v -> {
                    folderStore.removeFromFolder(existingFolderId, app.packageName);
                    dialog.dismiss();
                    if (folderChangedListener != null) {
                        folderChangedListener.onFolderChanged();
                    }
                });
            } else {
                btnAddToFolder.setText("Add to Folder");
                btnAddToFolder.setVisibility(View.VISIBLE);
                btnAddToFolder.setOnClickListener(v -> {
                    dialog.dismiss();
                    showFolderSelectionDialog(app);
                });
            }
        }

        TextView btnCancel = dialogView.findViewById(R.id.btnCancelBookmark);
        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(R.drawable.bg_dialog);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.9);
            window.setAttributes(params);
        }
    }

    // ---- Favicon download ----

    private void downloadFavicon(String bookmarkId, String bookmarkUrl) {
        executor.submit(() -> {
            try {
                Uri uri = Uri.parse(bookmarkUrl);
                String host = uri.getHost();
                if (host == null) return;

                URL faviconUrl = new URL("https://www.google.com/s2/favicons?domain=" + host + "&sz=128");
                HttpURLConnection conn = (HttpURLConnection) faviconUrl.openConnection();
                conn.setConnectTimeout(10000);
                conn.setReadTimeout(10000);

                InputStream is = conn.getInputStream();
                Bitmap bitmap = BitmapFactory.decodeStream(is);
                is.close();
                conn.disconnect();

                if (bitmap != null) {
                    bookmarkStore.saveIcon(bookmarkId, bitmap);
                    bitmap.recycle();
                    runOnUi(() -> {
                        if (bookmarkChangedListener != null) {
                            bookmarkChangedListener.onBookmarkChanged();
                        }
                    });
                }
            } catch (Exception ignored) {
            }
        });
    }

    // ---- App long-press menu ----

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
        boolean isExcluded = hiddenAppsStore.isExcludedFromHistory(app.packageName);

        TextView btnHideShow = dialogView.findViewById(R.id.btnHideShow);
        if (isHidden) {
            btnHideShow.setText("Hide from History");
            btnHideShow.setOnClickListener(v -> {
                hiddenAppsStore.excludeFromHistory(app.packageName);
                if (appHiddenChangedListener != null) appHiddenChangedListener.onAppHiddenChanged();
                dialog.dismiss();
            });
        } else if (isExcluded) {
            btnHideShow.setText("Show");
            btnHideShow.setOnClickListener(v -> {
                hiddenAppsStore.showApp(app.packageName);
                if (appHiddenChangedListener != null) appHiddenChangedListener.onAppHiddenChanged();
                dialog.dismiss();
            });
        } else {
            btnHideShow.setText("Hide");
            btnHideShow.setOnClickListener(v -> {
                hiddenAppsStore.hideApp(app.packageName);
                if (appHiddenChangedListener != null) appHiddenChangedListener.onAppHiddenChanged();
                dialog.dismiss();
            });
        }

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

        TextView btnAddToFolder = dialogView.findViewById(R.id.btnAddToFolder);
        if (activeFolderId != null) {
            // Inside a folder — show "Remove from <folder>"
            FolderStore.FolderData folder = folderStore.getById(activeFolderId);
            String folderName = folder != null ? folder.label : "Folder";
            btnAddToFolder.setText("Remove from " + folderName);
            btnAddToFolder.setVisibility(View.VISIBLE);
            btnAddToFolder.setOnClickListener(v -> {
                folderStore.removeFromFolder(activeFolderId, app.packageName);
                dialog.dismiss();
                if (folderChangedListener != null) {
                    folderChangedListener.onFolderChanged();
                }
            });
        } else if (folderStore.hasFolders()) {
            String existingFolderId = folderStore.getFolderForPackage(app.packageName);
            if (existingFolderId != null) {
                FolderStore.FolderData folder = folderStore.getById(existingFolderId);
                String folderName = folder != null ? folder.label : "Folder";
                btnAddToFolder.setText("Remove from " + folderName);
                btnAddToFolder.setVisibility(View.VISIBLE);
                btnAddToFolder.setOnClickListener(v -> {
                    folderStore.removeFromFolder(existingFolderId, app.packageName);
                    dialog.dismiss();
                    if (folderChangedListener != null) {
                        folderChangedListener.onFolderChanged();
                    }
                });
            } else {
                btnAddToFolder.setText("Add to Folder");
                btnAddToFolder.setVisibility(View.VISIBLE);
                btnAddToFolder.setOnClickListener(v -> {
                    dialog.dismiss();
                    showFolderSelectionDialog(app);
                });
            }
        }

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

    // ---- Folder dialog (Add / Edit) ----

    private void showFolderDialog(FolderStore.FolderData existing) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_folder, null);

        AlertDialog dialog = new AlertDialog.Builder(context, R.style.Theme_Androdash_Dialog)
                .setView(dialogView)
                .create();

        TextView title = dialogView.findViewById(R.id.folderDialogTitle);
        EditText labelField = dialogView.findViewById(R.id.folderLabel);
        ImageView iconPreview = dialogView.findViewById(R.id.folderIconPreview);
        TextView btnPickImage = dialogView.findViewById(R.id.btnPickFolderImage);
        TextView btnCancel = dialogView.findViewById(R.id.btnFolderCancel);
        TextView btnSave = dialogView.findViewById(R.id.btnFolderSave);

        pendingFolderIcon = null;
        pendingFolderIconPreview = iconPreview;

        // Set default folder icon in preview
        iconPreview.setImageDrawable(createDefaultFolderIcon());

        if (existing != null) {
            title.setText("Edit Folder");
            labelField.setText(existing.label);
            Drawable existingIcon = folderStore.loadIconDrawable(existing.id);
            if (existingIcon != null) {
                iconPreview.setImageDrawable(existingIcon);
            }
        }

        btnPickImage.setOnClickListener(v -> {
            if (folderChangedListener != null) {
                folderChangedListener.onPickImageForFolder();
            }
        });

        btnCancel.setOnClickListener(v -> {
            pendingFolderIconPreview = null;
            pendingFolderIcon = null;
            dialog.dismiss();
        });

        btnSave.setOnClickListener(v -> {
            String label = labelField.getText().toString().trim();
            if (label.isEmpty()) return;

            String id = existing != null ? existing.id : FolderStore.generateId();
            boolean hasCustomIcon = pendingFolderIcon != null
                    || (existing != null && existing.hasCustomIcon && pendingFolderIcon == null);

            FolderStore.FolderData data = new FolderStore.FolderData(id, label, hasCustomIcon);

            if (pendingFolderIcon != null) {
                folderStore.saveIcon(id, pendingFolderIcon);
                data.hasCustomIcon = true;
            }

            folderStore.save(data);

            pendingFolderIconPreview = null;
            pendingFolderIcon = null;
            dialog.dismiss();

            if (folderChangedListener != null) {
                folderChangedListener.onFolderChanged();
            }
        });

        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(R.drawable.bg_dialog);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.9);
            window.setAttributes(params);
        }
    }

    // ---- Folder long-press menu ----

    private void showFolderMenu(AppModel app) {
        View dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_folder_menu, null);

        AlertDialog dialog = new AlertDialog.Builder(context, R.style.Theme_Androdash_Dialog)
                .setView(dialogView)
                .create();

        ImageView headerIcon = dialogView.findViewById(R.id.dialogFolderIcon);
        TextView headerLabel = dialogView.findViewById(R.id.dialogFolderLabel);
        if (app.icon != null) {
            headerIcon.setImageDrawable(app.icon);
        } else {
            headerIcon.setImageDrawable(createDefaultFolderIcon());
        }
        headerLabel.setText(app.label);

        String folderId = app.packageName.substring("folder://".length());

        TextView btnEdit = dialogView.findViewById(R.id.btnEditFolder);
        btnEdit.setOnClickListener(v -> {
            dialog.dismiss();
            FolderStore.FolderData data = folderStore.getById(folderId);
            if (data != null) {
                showFolderDialog(data);
            }
        });

        TextView btnDelete = dialogView.findViewById(R.id.btnDeleteFolder);
        btnDelete.setOnClickListener(v -> {
            folderStore.delete(folderId);
            dialog.dismiss();
            if (folderChangedListener != null) {
                folderChangedListener.onFolderChanged();
            }
        });

        TextView btnCancelFolder = dialogView.findViewById(R.id.btnCancelFolder);
        btnCancelFolder.setOnClickListener(v -> dialog.dismiss());

        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(R.drawable.bg_dialog);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.9);
            window.setAttributes(params);
        }
    }

    // ---- Folder selection dialog ----

    private void showBackupDialog() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        TextView title = new TextView(context);
        title.setText("Backup");
        title.setTextColor(context.getColor(R.color.text_primary));
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = context.getResources().getDimensionPixelSize(R.dimen.grid_spacing);
        title.setLayoutParams(titleParams);
        layout.addView(title);

        AlertDialog dialog = new AlertDialog.Builder(context, R.style.Theme_Androdash_Dialog)
                .setView(layout)
                .create();

        int spacing = context.getResources().getDimensionPixelSize(R.dimen.grid_spacing);
        int cardHeight = context.getResources().getDimensionPixelSize(R.dimen.app_card_height);

        // Export button
        TextView btnExport = new TextView(context);
        btnExport.setText("Export");
        btnExport.setBackgroundResource(R.drawable.bg_button);
        btnExport.setGravity(Gravity.CENTER);
        btnExport.setTextColor(context.getColor(R.color.text_primary));
        btnExport.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
        LinearLayout.LayoutParams exportLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, cardHeight);
        exportLp.bottomMargin = spacing;
        btnExport.setLayoutParams(exportLp);
        btnExport.setOnClickListener(v -> {
            dialog.dismiss();
            if (backupListener != null) backupListener.onExportRequested();
        });
        layout.addView(btnExport);

        // Import button
        TextView btnImport = new TextView(context);
        btnImport.setText("Import");
        btnImport.setBackgroundResource(R.drawable.bg_button);
        btnImport.setGravity(Gravity.CENTER);
        btnImport.setTextColor(context.getColor(R.color.text_primary));
        btnImport.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
        LinearLayout.LayoutParams importLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, cardHeight);
        importLp.bottomMargin = spacing;
        btnImport.setLayoutParams(importLp);
        btnImport.setOnClickListener(v -> {
            dialog.dismiss();
            if (backupListener != null) backupListener.onImportRequested();
        });
        layout.addView(btnImport);

        // Cancel button
        TextView btnCancel = new TextView(context);
        btnCancel.setText("Cancel");
        btnCancel.setBackgroundResource(R.drawable.bg_button);
        btnCancel.setGravity(Gravity.CENTER);
        btnCancel.setTextColor(context.getColor(R.color.text_primary));
        btnCancel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, cardHeight);
        btnCancel.setLayoutParams(cancelLp);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        layout.addView(btnCancel);

        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(R.drawable.bg_dialog);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.9);
            window.setAttributes(params);
        }
    }

    private void showFolderSelectionDialog(AppModel app) {
        List<FolderStore.FolderData> folders = folderStore.getAll();
        if (folders.isEmpty()) return;

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(32, 32, 32, 32);

        TextView title = new TextView(context);
        title.setText("Add to Folder");
        title.setTextColor(context.getColor(R.color.text_primary));
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 18);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleParams.bottomMargin = context.getResources().getDimensionPixelSize(R.dimen.grid_spacing);
        title.setLayoutParams(titleParams);
        layout.addView(title);

        AlertDialog dialog = new AlertDialog.Builder(context, R.style.Theme_Androdash_Dialog)
                .setView(layout)
                .create();

        int spacing = context.getResources().getDimensionPixelSize(R.dimen.grid_spacing);
        int cardHeight = context.getResources().getDimensionPixelSize(R.dimen.app_card_height);

        for (int i = 0; i < folders.size(); i++) {
            FolderStore.FolderData folder = folders.get(i);
            TextView btn = new TextView(context);
            btn.setText(folder.label);
            btn.setBackgroundResource(R.drawable.bg_button);
            btn.setGravity(Gravity.CENTER);
            btn.setTextColor(context.getColor(R.color.text_primary));
            btn.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, cardHeight);
            if (i < folders.size() - 1) {
                lp.bottomMargin = spacing;
            }
            btn.setLayoutParams(lp);

            btn.setOnClickListener(v -> {
                folderStore.addToFolder(folder.id, app.packageName);
                dialog.dismiss();
                if (folderChangedListener != null) {
                    folderChangedListener.onFolderChanged();
                }
            });

            layout.addView(btn);
        }

        // Cancel button
        TextView btnCancel = new TextView(context);
        btnCancel.setText("Cancel");
        btnCancel.setBackgroundResource(R.drawable.bg_button);
        btnCancel.setGravity(Gravity.CENTER);
        btnCancel.setTextColor(context.getColor(R.color.text_primary));
        btnCancel.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14);
        LinearLayout.LayoutParams cancelLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, cardHeight);
        cancelLp.topMargin = spacing;
        btnCancel.setLayoutParams(cancelLp);
        btnCancel.setOnClickListener(v -> dialog.dismiss());
        layout.addView(btnCancel);

        dialog.show();

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(R.drawable.bg_dialog);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = (int) (context.getResources().getDisplayMetrics().widthPixels * 0.9);
            window.setAttributes(params);
        }
    }

    // ---- Default folder icon ----

    private Drawable createDefaultFolderIcon() {
        int size = 96;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(context.getColor(R.color.text_primary));
        paint.setTextSize(64);
        paint.setTypeface(Typeface.DEFAULT_BOLD);
        paint.setTextAlign(Paint.Align.CENTER);
        Paint.FontMetrics fm = paint.getFontMetrics();
        float y = (size - fm.top - fm.bottom) / 2f;
        canvas.drawText("\u2302", size / 2f, y, paint);
        return new BitmapDrawable(context.getResources(), bitmap);
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
