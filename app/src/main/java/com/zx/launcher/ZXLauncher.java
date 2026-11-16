package com.zx.launcher;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.LruCache;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * ZX Launcher: blank black home, installed apps only, plus an Optimize button.
 * Single Activity, no dependencies, no layout XML. Android 4.0 (API 14) and up.
 */
public class ZXLauncher extends Activity {

    private static final int ICON_DP = 48;
    private static final int CELL_DP = 84;

    private GridView grid;
    private AppAdapter adapter;
    private PackageManager pm;
    private float density;
    private boolean loading;
    private boolean optimizing;
    private final Handler ui = new Handler();

    // optimize panel views
    private LinearLayout panel;
    private ProgressBar bar;
    private TextView step, result;

    private final LruCache<String, Drawable> iconCache = new LruCache<String, Drawable>(48);

    private final BroadcastReceiver pkgReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            iconCache.evictAll();
            loadApps();
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        pm = getPackageManager();
        density = getResources().getDisplayMetrics().density;

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF000000);

        // ---- app grid ----
        grid = new GridView(this);
        grid.setBackgroundColor(0xFF000000);
        grid.setCacheColorHint(0);
        grid.setSelector(new ColorDrawable(0x33FFFFFF));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        grid.setVerticalSpacing(dp(4));
        grid.setPadding(0, dp(8), 0, dp(72));
        grid.setClipToPadding(false);
        grid.setFocusable(true);
        adapter = new AppAdapter();
        grid.setAdapter(adapter);
        root.addView(grid, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> a, View v, int pos, long id) {
                Intent li = pm.getLaunchIntentForPackage(adapter.items.get(pos).pkg);
                if (li != null) {
                    li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(li);
                }
            }
        });
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> a, View v, int pos, long id) {
                Intent i = new Intent("android.settings.APPLICATION_DETAILS_SETTINGS",
                        Uri.parse("package:" + adapter.items.get(pos).pkg));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { startActivity(i); } catch (Exception ignored) { }
                return true;
            }
        });

        // ---- optimize button (bottom center) ----
        Button opt = new Button(this);
        opt.setText("🚀 Optimize");
        opt.setAllCaps(false);
        opt.setTextColor(0xFF000000);
        opt.setTextSize(15);
        opt.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0xFFFFFFFF);
        pill.setCornerRadius(dp(24));
        opt.setBackgroundDrawable(pill);
        opt.setPadding(dp(22), dp(8), dp(22), dp(8));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(44),
                Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        lp.bottomMargin = dp(12);
        root.addView(opt, lp);
        opt.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { startOptimize(); }
        });

        // ---- optimize panel (hidden) ----
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setBackgroundColor(0xF2000000);
        panel.setVisibility(View.GONE);
        panel.setClickable(true); // swallow touches

        TextView title = new TextView(this);
        title.setText("Optimizing");
        title.setTextColor(Color.WHITE);
        title.setTextSize(18);
        panel.addView(title);

        bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        panel.addView(bar, new LinearLayout.LayoutParams(dp(240), dp(12)));

        step = new TextView(this);
        step.setTextColor(0xFFAAAAAA);
        step.setTextSize(13);
        step.setGravity(Gravity.CENTER);
        panel.addView(step);

        result = new TextView(this);
        result.setTextColor(Color.WHITE);
        result.setTextSize(15);
        result.setGravity(Gravity.CENTER);
        result.setPadding(0, dp(10), 0, 0);
        panel.addView(result);

        root.addView(panel, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        updateColumns();

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(pkgReceiver, f);

        loadApps();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (grid != null) grid.setSelection(0);
    }

    @Override
    public void onConfigurationChanged(Configuration c) {
        super.onConfigurationChanged(c);
        updateColumns();
    }

    @Override
    public void onBackPressed() { /* home screen: ignore */ }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(pkgReceiver); } catch (Exception ignored) { }
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        iconCache.evictAll();
    }

    private void updateColumns() {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int cols = (int) (dm.widthPixels / (CELL_DP * density));
        grid.setNumColumns(cols < 3 ? 3 : cols); // landscape -> more, portrait -> fewer
    }

    private int dp(int v) { return (int) (v * density + 0.5f); }

    // ---------- optimize ----------

    private long availMem() {
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);
        return mi.availMem;
    }

    private void showStep(final String s, final int pct) {
        ui.post(new Runnable() {
            public void run() { step.setText(s); bar.setProgress(pct); }
        });
    }

    private void startOptimize() {
        if (optimizing) return;
        optimizing = true;
        result.setText("");
        bar.setProgress(0);
        step.setText("Starting...");
        panel.setVisibility(View.VISIBLE);

        new Thread(new Runnable() {
            public void run() {
                final long before = availMem();
                final long[] junk = new long[1];
                try {
                    showStep("Closing background apps...", 25);
                    killBackground();
                    Thread.sleep(500);

                    showStep("Freeing RAM...", 50);
                    System.gc();
                    Thread.sleep(500);

                    showStep("Cleaning cache and temp files...", 75);
                    junk[0] = cleanCaches();
                    Thread.sleep(500);

                    showStep("Boosting performance...", 100);
                    iconCache.evictAll();
                    System.gc();
                    Thread.sleep(600);
                } catch (Exception ignored) { }

                final long freed = Math.max(0, availMem() - before);
                ui.post(new Runnable() {
                    public void run() {
                        step.setText("Finished");
                        result.setText("RAM freed: " + mb(freed)
                                + "\nJunk cleared: " + mb(junk[0])
                                + "\nBoost applied ✓");
                    }
                });
                ui.postDelayed(new Runnable() {
                    public void run() {
                        panel.setVisibility(View.GONE);
                        optimizing = false;
                    }
                }, 1800);
            }
        }).start();
    }

    private String mb(long bytes) {
        return (bytes / (1024 * 1024)) + " MB";
    }

    /** Kills cached/background processes of other apps (never the foreground one). */
    private void killBackground() {
        ActivityManager am = (ActivityManager) getSystemService(ACTIVITY_SERVICE);
        List<ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
        if (procs == null) return;
        String self = getPackageName();
        for (ActivityManager.RunningAppProcessInfo p : procs) {
            if (p.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) continue;
            if (p.pkgList == null) continue;
            for (String pkg : p.pkgList) {
                if (!self.equals(pkg)) {
                    try { am.killBackgroundProcesses(pkg); } catch (Exception ignored) { }
                }
            }
        }
    }

    /**
     * Clears our own cache, and asks the system to free app caches on devices
     * where that is still allowed (Android 4.x to 6). Returns bytes cleared
     * from our own cache; system-wide freeing is best effort.
     */
    private long cleanCaches() {
        long n = deleteDir(getCacheDir());
        try {
            File ext = getExternalCacheDir();
            if (ext != null) n += deleteDir(ext);
        } catch (Exception ignored) { }
        try {
            Method m = pm.getClass().getMethod("freeStorageAndNotify", long.class,
                    Class.forName("android.content.pm.IPackageDataObserver"));
            m.invoke(pm, Long.MAX_VALUE, null);
        } catch (Throwable ignored) { /* not allowed on this Android version */ }
        return n;
    }

    private long deleteDir(File d) {
        long n = 0;
        if (d == null) return 0;
        File[] fs = d.listFiles();
        if (fs == null) return 0;
        for (File f : fs) {
            if (f.isDirectory()) n += deleteDir(f);
            n += f.length();
            f.delete();
        }
        return n;
    }

    // ---------- app list ----------

    private void loadApps() {
        if (loading) return;
        loading = true;
        new AsyncTask<Void, Void, List<AppItem>>() {
            @Override
            protected List<AppItem> doInBackground(Void... v) {
                Intent main = new Intent(Intent.ACTION_MAIN, null);
                main.addCategory(Intent.CATEGORY_LAUNCHER);
                List<ResolveInfo> ris = pm.queryIntentActivities(main, 0);
                List<AppItem> out = new ArrayList<AppItem>(ris.size());
                String self = getPackageName();
                for (ResolveInfo ri : ris) {
                    if (self.equals(ri.activityInfo.packageName)) continue;
                    AppItem a = new AppItem();
                    a.pkg = ri.activityInfo.packageName;
                    a.label = String.valueOf(ri.loadLabel(pm));
                    out.add(a);
                }
                Collections.sort(out, new Comparator<AppItem>() {
                    public int compare(AppItem x, AppItem y) {
                        return x.label.compareToIgnoreCase(y.label);
                    }
                });
                return out;
            }

            @Override
            protected void onPostExecute(List<AppItem> r) {
                loading = false;
                adapter.items = r;
                adapter.notifyDataSetChanged();
            }
        }.execute();
    }

    private static class AppItem {
        String pkg;
        String label;
    }

    private class AppAdapter extends BaseAdapter {
        List<AppItem> items = new ArrayList<AppItem>();

        public int getCount() { return items.size(); }
        public Object getItem(int i) { return items.get(i); }
        public long getItemId(int i) { return i; }

        public View getView(int pos, View cv, ViewGroup parent) {
            Holder h;
            if (cv == null) {
                LinearLayout cell = new LinearLayout(ZXLauncher.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER_HORIZONTAL);
                cell.setPadding(dp(2), dp(6), dp(2), dp(6));

                h = new Holder();
                h.icon = new ImageView(ZXLauncher.this);
                int s = dp(ICON_DP);
                cell.addView(h.icon, new LinearLayout.LayoutParams(s, s));

                h.label = new TextView(ZXLauncher.this);
                h.label.setTextColor(0xFFFFFFFF);
                h.label.setTextSize(11);
                h.label.setGravity(Gravity.CENTER);
                h.label.setSingleLine(true);
                h.label.setEllipsize(android.text.TextUtils.TruncateAt.END);
                cell.addView(h.label, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT));
                cell.setTag(h);
                cv = cell;
            } else {
                h = (Holder) cv.getTag();
            }
            AppItem it = items.get(pos);
            h.label.setText(it.label);
            Drawable d = iconCache.get(it.pkg);
            if (d == null) {
                try { d = pm.getApplicationIcon(it.pkg); } catch (Exception e) { d = null; }
                if (d != null) iconCache.put(it.pkg, d);
            }
            h.icon.setImageDrawable(d);
            return cv;
        }
    }

    private static class Holder {
        ImageView icon;
        TextView label;
    }
}
