package com.androdash;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class AppGridAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_APP = 0;
    private static final int VIEW_TYPE_CONFIG = 1;

    public interface OnConfigToggleListener {
        void onShowAllAppsChanged(boolean showAll);
    }

    public interface OnAppHiddenChangedListener {
        void onAppHiddenChanged();
    }

    private final List<AppModel> apps = new ArrayList<>();
    private final Context context;
    private final HiddenAppsStore hiddenAppsStore;
    private boolean configMode = false;
    private boolean showAllApps = false;
    private OnConfigToggleListener configToggleListener;
    private OnAppHiddenChangedListener appHiddenChangedListener;

    public AppGridAdapter(Context context, HiddenAppsStore hiddenAppsStore) {
        this.context = context;
        this.hiddenAppsStore = hiddenAppsStore;
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

    public void updateApps(List<AppModel> newApps) {
        DiffUtil.DiffResult result = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override
            public int getOldListSize() { return apps.size(); }

            @Override
            public int getNewListSize() { return newApps.size(); }

            @Override
            public boolean areItemsTheSame(int oldPos, int newPos) {
                return apps.get(oldPos).packageName.equals(newApps.get(newPos).packageName);
            }

            @Override
            public boolean areContentsTheSame(int oldPos, int newPos) {
                return apps.get(oldPos).packageName.equals(newApps.get(newPos).packageName);
            }
        });

        apps.clear();
        apps.addAll(newApps);
        result.dispatchUpdatesTo(this);
    }

    @Override
    public int getItemViewType(int position) {
        return configMode ? VIEW_TYPE_CONFIG : VIEW_TYPE_APP;
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

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (configMode) {
            bindConfigItem((ConfigViewHolder) holder, position);
        } else {
            bindAppItem((AppViewHolder) holder, position);
        }
    }

    private void bindAppItem(AppViewHolder holder, int position) {
        AppModel app = apps.get(position);
        holder.icon.setImageDrawable(app.icon);
        holder.label.setText(app.label);

        boolean isHidden = hiddenAppsStore.isHidden(app.packageName);
        holder.itemView.setAlpha(isHidden && showAllApps ? 0.4f : 1.0f);

        holder.itemView.setOnClickListener(v -> {
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
        }
    }

    @Override
    public int getItemCount() {
        if (configMode) return 1; // "Show all Apps"
        return apps.size();
    }

    private void showAppMenu(AppModel app) {
        boolean isHidden = hiddenAppsStore.isHidden(app.packageName);
        String[] options = {isHidden ? "Show" : "Hide", "Uninstall", "App Info", "Cancel"};
        new MaterialAlertDialogBuilder(context)
                .setTitle(app.label)
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: // Hide or Show
                            if (isHidden) {
                                hiddenAppsStore.showApp(app.packageName);
                            } else {
                                hiddenAppsStore.hideApp(app.packageName);
                            }
                            if (appHiddenChangedListener != null) {
                                appHiddenChangedListener.onAppHiddenChanged();
                            }
                            break;
                        case 1: // Uninstall
                            Intent uninstall = new Intent(Intent.ACTION_DELETE,
                                    Uri.parse("package:" + app.packageName));
                            context.startActivity(uninstall);
                            break;
                        case 2: // App Info
                            Intent info = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:" + app.packageName));
                            context.startActivity(info);
                            break;
                        case 3: // Cancel
                            dialog.dismiss();
                            break;
                    }
                })
                .show();
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
