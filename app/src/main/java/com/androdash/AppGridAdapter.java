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
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class AppGridAdapter extends RecyclerView.Adapter<AppGridAdapter.ViewHolder> {

    private final List<AppModel> apps = new ArrayList<>();
    private final Context context;

    public AppGridAdapter(Context context) {
        this.context = context;
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

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_app, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AppModel app = apps.get(position);
        holder.icon.setImageDrawable(app.icon);
        holder.label.setText(app.label);

        holder.itemView.setOnClickListener(v -> {
            context.startActivity(app.launchIntent);
        });

        holder.itemView.setOnLongClickListener(v -> {
            showAppMenu(app);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return apps.size();
    }

    private void showAppMenu(AppModel app) {
        String[] options = {"Hide", "Uninstall", "App Info", "Cancel"};
        new MaterialAlertDialogBuilder(context)
                .setTitle(app.label)
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0: // Hide
                            Toast.makeText(context, "Coming soon", Toast.LENGTH_SHORT).show();
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

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView icon;
        final TextView label;

        ViewHolder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.appIcon);
            label = itemView.findViewById(R.id.appLabel);
        }
    }
}
