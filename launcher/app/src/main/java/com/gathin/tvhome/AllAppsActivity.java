package com.gathin.tvhome;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Every app on the TV, A to Z, behind the "More" tile: Home keeps two rows, and anything past them,
 * apps installed later included, is found here. OK opens an app, Back returns to Home.
 */
public class AllAppsActivity extends Activity {

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }
    private static final int COLUMNS = 6;
    /** Opened from Home's "Add" box: OK puts the app on Home and goes back, instead of opening it. */
    static final String PICK = "pick";
    private boolean picking;
    private RecyclerView grid;
    private boolean shownOnce;

    /** Back from an uninstall or an app's info page: the list shows what is installed now. */
    @Override
    protected void onResume() {
        super.onResume();
        if (shownOnce && grid != null) grid.setAdapter(new Adapter(load(), getResources().getDisplayMetrics().density));
        shownOnce = true;
    }

    private static final class App {
        final String label;
        final ComponentName component;
        final Drawable banner, icon;

        App(String label, ComponentName component, Drawable banner, Drawable icon) {
            this.label = label;
            this.component = component;
            this.banner = banner;
            this.icon = icon;
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        float d = getResources().getDisplayMetrics().density;
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xF0080A10);
        page.setPadding(Math.round(56 * d), Math.round(36 * d), Math.round(56 * d), 0);

        TextView title = new TextView(this);
        picking = getIntent().getBooleanExtra(PICK, false);
        title.setText(picking ? "Add to Home" : getString(R.string.all_apps));
        title.setTypeface(getResources().getFont(R.font.outfit));
        title.setFontVariationSettings("'wght' 300");
        title.setTextSize(34f);
        title.setTextColor(0xFFFFFFFF);
        page.addView(title);

        grid = new RecyclerView(this);
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS));
        grid.setClipToPadding(false);
        grid.setClipChildren(false);
        page.setClipChildren(true);   // scrolled rows stay under the title; the grid's padding leaves room for the zoom
        // Room round the edges for a selected tile's zoom and glow.
        grid.setPadding(Math.round(14 * d), Math.round(24 * d), Math.round(14 * d), Math.round(40 * d));
        grid.setAdapter(new Adapter(load(), d));
        page.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        setContentView(page);
        grid.post(() -> {
            RecyclerView.ViewHolder first = grid.findViewHolderForAdapterPosition(0);
            if (first != null) first.itemView.requestFocus();
        });
    }

    /** Hold OK on an app: put it on Home, uninstall it, or open its info page. */
    private void appMenu(App a) {
        if (a.banner == null && a.label.startsWith("+")) return;   // the Google Play shortcut
        if (isTool(a)) return;
        List<String> labels = new ArrayList<>();
        List<Runnable> acts = new ArrayList<>();
        labels.add(getString(R.string.add_to_home));
        acts.add(() -> {
            AppAdapter.addToHome(this, a.component);
            Toast.makeText(this, getString(R.string.is_on_home, a.label), Toast.LENGTH_SHORT).show();
        });
        if (AppAdapter.removable(this, a.component.getPackageName())) {
            labels.add(getString(R.string.uninstall));
            acts.add(() -> AppAdapter.uninstall(this, a.component.getPackageName()));
        }
        labels.add(getString(R.string.tile_app_info));
        acts.add(() -> {
            try {
                startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        android.net.Uri.parse("package:" + a.component.getPackageName())));
            } catch (Exception ignored) {
            }
        });
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(a.label)
                .setItems(labels.toArray(new String[0]), (d, which) -> acts.get(which).run())
                .show();
    }

        private List<App> load() {
        PackageManager pm = getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<App> apps = new ArrayList<>();
        for (ResolveInfo ri : pm.queryIntentActivities(probe, 0)) {
            if (ri.activityInfo == null || ri.activityInfo.packageName.equals(getPackageName())) continue;
            Drawable banner = ri.activityInfo.loadBanner(pm);
            apps.add(new App(String.valueOf(ri.loadLabel(pm)),
                    new ComponentName(ri.activityInfo.packageName, ri.activityInfo.name),
                    banner, banner == null ? ri.loadIcon(pm) : null));
        }
        Collections.sort(apps, (a, b) -> a.label.compareToIgnoreCase(b.label));
        // First: more apps from Google Play.
        Intent store = pm.getLeanbackLaunchIntentForPackage("com.android.vending");
        if (store != null && store.getComponent() != null) {
            Drawable icon = null;
            try {
                icon = pm.getApplicationIcon("com.android.vending");
            } catch (PackageManager.NameNotFoundException ignored) {
            }
            apps.add(0, new App(getString(R.string.get_more_apps), store.getComponent(), null, icon));
        }
        // Then the Premier League table and highlights, the same as Home's score card opens.
        int at = store != null && store.getComponent() != null ? 1 : 0;
        apps.add(at, new App(getString(R.string.tile_epl), new ComponentName(this, MainActivity.class), null, getDrawable(R.drawable.ic_epl)));
        apps.add(at + 1, new App(getString(R.string.tile_prayer), new ComponentName(this, PrayerActivity.class), null, getDrawable(R.drawable.ic_prayer)));
        apps.add(at + 2, new App(getString(R.string.tile_wisdom), new ComponentName(this, QuotesActivity.class), null, getDrawable(R.drawable.ic_quote)));
        // A tile that clears the TV's app caches at once (it is also done every day by itself).
        apps.add(at + 3, new App(getString(R.string.tile_clean), new ComponentName(this, MainActivity.class), null, getDrawable(R.drawable.ic_clean)));
        // The launcher's language, named in English as well so anyone can find it.
        apps.add(at + 4, new App(getString(R.string.tile_language), new ComponentName(this, AllAppsActivity.class), null, getDrawable(R.drawable.ic_language)));
        apps.add(new App(getString(R.string.home_controls), new ComponentName(this, HomeSettingsActivity.class), null, getDrawable(R.drawable.ic_home_settings)));
        return apps;
    }

    /** Tiles of the launcher's own (Premier League, Prayer Times, Wisdom, Clear cache, Language) rather than apps. */
    private static boolean isTool(App a) {
        String c = a.component.getClassName();
        return c.equals(MainActivity.class.getName()) || c.equals(QuotesActivity.class.getName())
                || c.equals(PrayerActivity.class.getName()) || c.equals(AllAppsActivity.class.getName())
                || c.equals(HomeSettingsActivity.class.getName());
    }

    private boolean isEpl(App a) {
        return isTool(a) && a.label.equals(getString(R.string.tile_epl));
    }

    /** The Language tile: the launcher's language; Home starts again in it. */
    private void chooseLanguage() {
        int checked = java.util.Arrays.asList(Lang.CODES).indexOf(Lang.code(this));
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(getString(R.string.language_title).equals("Language") ? "Language"
                        : getString(R.string.language_title) + " \u00B7 Language")
                .setSingleChoiceItems(Lang.NAMES, Math.max(0, checked), (dlg, which) -> {
                    dlg.dismiss();
                    Lang.set(this, Lang.CODES[which]);
                    startActivity(new Intent(this, MainActivity.class)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                    finish();
                })
                .show();
    }

    private final class Adapter extends RecyclerView.Adapter<Adapter.Tile> {
        private final List<App> apps;
        private final float d;

        Adapter(List<App> apps, float d) {
            this.apps = apps;
            this.d = d;
        }

        final class Tile extends RecyclerView.ViewHolder {
            final FrameLayout card;
            final ImageView banner, icon;
            final TextView label, name;

            Tile(LinearLayout v) {
                super(v);
                card = (FrameLayout) v.getChildAt(0);
                banner = (ImageView) card.getChildAt(0);
                icon = (ImageView) card.getChildAt(1);
                label = (TextView) card.getChildAt(2);
                name = (TextView) v.getChildAt(1);
            }
        }

        @NonNull
        @Override
        public Tile onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            int w = (parent.getWidth() > 0 ? parent.getWidth() : Math.round(1808 * d / 2)) / COLUMNS - Math.round(28 * d);
            LinearLayout cell = new LinearLayout(AllAppsActivity.this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(0, Math.round(10 * d), 0, Math.round(4 * d));
            cell.setClipChildren(false);
            cell.setClipToPadding(false);

            FrameLayout card = new FrameLayout(AllAppsActivity.this);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFF1C2230);
            bg.setCornerRadius(12 * d);
            card.setBackground(bg);
            card.setClipToOutline(true);
            card.setFocusable(true);
            card.setClickable(true);
            ImageView banner = new ImageView(AllAppsActivity.this);
            banner.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(banner, new FrameLayout.LayoutParams(-1, -1));
            ImageView icon = new ImageView(AllAppsActivity.this);
            FrameLayout.LayoutParams ilp = new FrameLayout.LayoutParams(Math.round(40 * d), Math.round(40 * d), Gravity.CENTER);
            ilp.bottomMargin = Math.round(12 * d);
            card.addView(icon, ilp);
            TextView label = new TextView(AllAppsActivity.this);
            label.setTextColor(0xFFFFFFFF);
            label.setTextSize(12f);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            card.addView(label, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
            ((FrameLayout.LayoutParams) label.getLayoutParams()).bottomMargin = Math.round(6 * d);
            cell.addView(card, new LinearLayout.LayoutParams(w, Math.round(w * 101f / 180f)));

            TextView name = new TextView(AllAppsActivity.this);
            name.setTextColor(0xFFFFFFFF);
            name.setTextSize(13f);
            name.setSingleLine(true);
            name.setGravity(Gravity.CENTER);
            name.setAlpha(0f);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(w, -2);
            nlp.topMargin = Math.round(8 * d);
            cell.addView(name, nlp);

            card.setOnFocusChangeListener((v, has) -> {
                v.animate().scaleX(has ? 1.1f : 1f).scaleY(has ? 1.1f : 1f).setDuration(180).start();
                // A soft light glow round the selected tile: its own shadow, drawn white and dim.
                v.setElevation(has ? 18 * d : 0);
                // Centred: the ambient shadow drawn as light, no spot shadow falling below.
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    v.setOutlineAmbientShadowColor(0xCCFFFFFF);
                    v.setOutlineSpotShadowColor(0x00000000);
                }
                name.animate().alpha(has ? 1f : 0f).setDuration(150).start();
            });
            return new Tile(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull Tile t, int position) {
            App a = apps.get(position);
            t.banner.setImageDrawable(a.banner);
            t.banner.setVisibility(a.banner != null ? View.VISIBLE : View.GONE);
            t.icon.setImageDrawable(a.icon);
            t.icon.setVisibility(a.banner == null ? View.VISIBLE : View.GONE);
            t.label.setText(a.label);
            t.label.setVisibility(a.banner == null ? View.VISIBLE : View.GONE);
            t.name.setText(a.label);
            t.card.setOnLongClickListener(v -> {
                appMenu(a);
                return true;
            });
            t.card.setOnClickListener(v -> {
                if (picking) {
                    if (a.banner == null && a.label.startsWith("+")) return;   // the Google Play shortcut
                    if (isTool(a)) return;
                    AppAdapter.addToHome(AllAppsActivity.this, a.component);
                    Toast.makeText(AllAppsActivity.this, getString(R.string.is_on_home, a.label), Toast.LENGTH_SHORT).show();
                    finish();
                    return;
                }
                if (a.component.getClassName().equals(AllAppsActivity.class.getName())) {
                    chooseLanguage();
                    return;
                }
                if (a.component.getClassName().equals(HomeSettingsActivity.class.getName())
                        || a.component.getClassName().equals(QuotesActivity.class.getName())
                        || a.component.getClassName().equals(PrayerActivity.class.getName())) {
                    startActivity(new Intent().setComponent(a.component));
                    finish();
                    return;
                }
                if (isTool(a)) {
                    startActivity(new Intent(AllAppsActivity.this, MainActivity.class)
                            .putExtra(isEpl(a) ? MainActivity.SHOW_EPL : MainActivity.CLEAN_NOW, true)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
                    finish();
                    return;
                }
                try {
                    startActivity(new Intent(Intent.ACTION_MAIN)
                            .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                            .setComponent(a.component)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
                    finish();
                } catch (Exception e) {
                    Toast.makeText(AllAppsActivity.this, getString(R.string.cant_open, a.label), Toast.LENGTH_SHORT).show();
                }
            });
        }

        @Override
        public int getItemCount() {
            return apps.size();
        }
    }
}
