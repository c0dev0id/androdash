package com.androdash;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AppLoader {

    public static List<AppModel> loadApps(Context context) {
        PackageManager pm = context.getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

        List<ResolveInfo> resolveInfos = pm.queryIntentActivities(mainIntent, 0);
        List<AppModel> apps = new ArrayList<>();
        String ownPackage = context.getPackageName();

        for (ResolveInfo ri : resolveInfos) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ownPackage)) continue;

            String label = ri.loadLabel(pm).toString();
            Intent launchIntent = pm.getLaunchIntentForPackage(pkg);
            if (launchIntent == null) continue;

            apps.add(new AppModel(
                    label,
                    pkg,
                    ri.loadIcon(pm),
                    launchIntent
            ));
        }

        Collections.sort(apps);
        return apps;
    }
}
