package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
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

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The wallpaper collection. OK on a picture tries it on Home, which stays visible behind this see-through
 * screen: left and right try the pictures beside it, OK keeps the one shown, up marks it and asks to
 * delete it, down gives it a look (Sepia, Mono, Noir...), and Back returns to the collection with Home's picture as it was. Auto goes back to a new one
 * every interval (the "fixed" name in the "wallpaper" prefs). Round icons beside the title choose how
 * pictures fill the screen and add new ones (from the web by hand, from a phone, from the Download
 * folder); each names itself while selected. Holding OK on a picture for 2 seconds shows a small ✕ on
 * every picture to delete them; Back puts the ✕s away.
 */
public class WallpaperPickerActivity extends Activity {

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }
    static final String PREFS = "wallpaper", FIXED = "fixed", FIT = "fit", INTERVAL = "interval_ms";
    /** Interval value meaning: a new picture every time Home comes back from an app. */
    static final long EVERY_RETURN = -1;
    private static final long[] INTERVALS = {5 * 60_000L, 10 * 60_000L, 30 * 60_000L, 3600_000L, 6 * 3600_000L,
            0, EVERY_RETURN};
    private static final int[] INTERVAL_NAMES = {R.string.wp_every_5, R.string.wp_every_10, R.string.wp_every_30,
            R.string.wp_every_hour, R.string.wp_every_6h, R.string.wp_each_on, R.string.wp_each_return};

    private String[] intervalNames() {
        String[] out = new String[INTERVAL_NAMES.length];
        for (int i = 0; i < out.length; i++) out[i] = getString(INTERVAL_NAMES[i]);
        return out;
    }

    private String intervalName() {
        long v = prefs().getLong(INTERVAL, 3600_000L);
        for (int i = 0; i < INTERVALS.length; i++) if (INTERVALS[i] == v) return getString(INTERVAL_NAMES[i]);
        return getString(INTERVAL_NAMES[3]);
    }

    /** Auto: a new picture on a schedule; choosing one also leaves any picture that was kept. */
    private void chooseAuto() {
        long v = prefs().getLong(INTERVAL, 3600_000L);
        int checked = 3;
        for (int i = 0; i < INTERVALS.length; i++) if (INTERVALS[i] == v) checked = i;
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.wp_change_title)
                .setSingleChoiceItems(intervalNames(), checked, (dlg, which) -> {
                    prefs().edit().putLong(INTERVAL, INTERVALS[which]).apply();
                    dlg.dismiss();
                    choose("");
                })
                .show();
    }
    private static final int COLUMNS = 5;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final List<File> pictures = new ArrayList<>();
    private volatile boolean closed;
    private Adapter adapter;
    private RecyclerView grid;
    private WallpaperImport.Server server;
    private TextView hint;
    private boolean editing;
    private float d;
    private LinearLayout page;
    /** The picture being tried on Home, or -1 while the collection is shown. */
    private int previewAt = -1;
    private TextView previewBar;
    /** While choosing a look for the picture being tried: which one in {@link WallFilters#NAMES}, else -1. */
    private int filterAt = -1;
    /** The strength of that look, in percent. */
    private int strength = 100;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        d = getResources().getDisplayMetrics().density;
        load();

        page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xF0080A10);
        page.setPadding(Math.round(56 * d), Math.round(32 * d), Math.round(56 * d), 0);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setClipChildren(false);
        head.setClipToPadding(false);
        head.setPadding(0, Math.round(6 * d), Math.round(8 * d), Math.round(6 * d));
        TextView title = new TextView(this);
        title.setText(R.string.wallpaper);
        title.setTypeface(getResources().getFont(R.font.outfit));
        title.setFontVariationSettings("'wght' 300");
        title.setTextSize(34f);
        title.setTextColor(0xFFFFFFFF);
        head.addView(title);
        hint = new TextView(this);
        hint.setTypeface(getResources().getFont(R.font.outfit));
        hint.setTextSize(15f);
        hint.setTextColor(0xCCFFFFFF);
        hint.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hl = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        hl.setMarginEnd(Math.round(18 * d));
        head.addView(hint, hl);
        action(head, R.drawable.ic_wp_auto, () -> (prefs().getString(FIXED, "").isEmpty() ? "\u2713 " : "")
                + getString(R.string.wp_auto, intervalName()), this::chooseAuto);
        action(head, R.drawable.ic_wp_size, this::fitLabel, () -> {
            boolean fit = "fit".equals(prefs().getString(FIT, "fill"));
            prefs().edit().putString(FIT, fit ? "fill" : "fit").apply();
            hint.setText(fitLabel());
        });
        action(head, R.drawable.ic_wp_web, () -> getString(R.string.wp_web),
                this::openWebSources);
        action(head, R.drawable.ic_wp_phone, () -> getString(R.string.wp_phone), this::showPhoneUpload);
        action(head, R.drawable.ic_wp_import, () -> getString(R.string.wp_import),
                () -> runImport(getString(R.string.wp_importing), () -> WallpaperImport.fromLocalSend(WallpaperPickerActivity.this)));
        page.addView(head);

        grid = new RecyclerView(this);
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS));
        grid.setClipToPadding(false);
        grid.setClipChildren(false);
        grid.setPadding(Math.round(14 * d), Math.round(22 * d), Math.round(14 * d), Math.round(40 * d));
        adapter = new Adapter();
        grid.setAdapter(adapter);
        page.addView(grid, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        FrameLayout frame = new FrameLayout(this);
        frame.addView(page, new FrameLayout.LayoutParams(-1, -1));
        previewBar = new TextView(this);
        previewBar.setTypeface(getResources().getFont(R.font.outfit));
        previewBar.setTextSize(12.5f);
        previewBar.setTextColor(0xFFFFFFFF);
        previewBar.setGravity(Gravity.CENTER);
        int px = Math.round(18 * d), py = Math.round(8 * d);
        previewBar.setPadding(px, py, px, py);
        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0xCC0B0E16);
        pill.setCornerRadius(40 * d);
        pill.setStroke(Math.round(1 * d), 0x40FFFFFF);
        previewBar.setBackground(pill);
        previewBar.setVisibility(View.GONE);
        FrameLayout.LayoutParams bl = new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        bl.bottomMargin = Math.round(30 * d);
        frame.addView(previewBar, bl);
        setContentView(frame);
        int start = Math.max(0, indexOf(prefs().getString(FIXED, "")));
        grid.post(() -> {
            grid.scrollToPosition(start);
            grid.post(() -> focus(start));
        });
    }

    /** A round icon button in the title row that names itself in the hint while selected. */
    private void action(LinearLayout head, int icon, java.util.function.Supplier<String> label, Runnable act) {
        ImageView b = new ImageView(this);
        b.setImageResource(icon);
        int p = Math.round(11 * d);
        b.setPadding(p, p, p, p);
        b.setFocusable(true);
        b.setClickable(true);
        b.setBackground(circle(false));
        b.setOnFocusChangeListener((v, has) -> {
            v.setBackground(circle(has));
            ((ImageView) v).setColorFilter(has ? 0xFF14110C : 0xFFFFFFFF);
            v.animate().scaleX(has ? 1.12f : 1f).scaleY(has ? 1.12f : 1f).setDuration(160).start();
            glow(v, has);
            if (has) hint.setText(label.get());
            else if (!anyActionFocused()) hint.setText("");
        });
        b.setOnClickListener(v -> act.run());
        b.setOnKeyListener((v, keyCode, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN || keyCode != KeyEvent.KEYCODE_DPAD_DOWN) return false;
            focus(0);
            return true;
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Math.round(46 * d), Math.round(46 * d));
        lp.setMarginStart(Math.round(12 * d));
        head.addView(b, lp);
    }

    private boolean anyActionFocused() {
        View f = getCurrentFocus();
        return f instanceof ImageView && f.getParent() == hint.getParent();
    }

    private GradientDrawable circle(boolean focused) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(focused ? 0xFFFFFFFF : 0x26FFFFFF);
        g.setStroke(Math.round(1.5f * d), focused ? 0xFFFFC46B : 0x4DFFFFFF);
        return g;
    }

    /** A soft light glow centred round a view: its ambient shadow, drawn white, with no spot shadow below. */
    private void glow(View v, boolean on) {
        if (android.os.Build.VERSION.SDK_INT >= 28) {
            v.setOutlineAmbientShadowColor(0xFFFFFFFF);
            v.setOutlineSpotShadowColor(0x00000000);
        }
        v.setElevation(on ? 22 * d : 0);
    }

    /** Tries picture {@code at} on Home: the collection fades away and Home shows it behind a small bar. */
    private void preview(int at) {
        if (pictures.isEmpty()) return;
        at = (at + pictures.size()) % pictures.size();
        MainActivity home = MainActivity.current.get();
        if (home == null || home.isDestroyed()) {
            choose(pictures.get(at).getName());   // no Home to try it on: keep it, as before
            return;
        }
        boolean entering = previewAt < 0;
        previewAt = at;
        filterAt = -1;
        home.previewWallpaper(pictures.get(at), WallFilters.of(this, pictures.get(at).getName()));
        showPreviewBar(false);
        if (entering) {
            page.animate().alpha(0f).setDuration(250).withEndAction(() -> page.setVisibility(View.INVISIBLE)).start();
            previewBar.setAlpha(0f);
            previewBar.setVisibility(View.VISIBLE);
            previewBar.animate().alpha(1f).setDuration(250).start();
        }
    }

    /** The bar under the picture being tried: where it is, and the keys, named in bold. */
    private void showPreviewBar(boolean marked) {
        android.text.SpannableStringBuilder t = new android.text.SpannableStringBuilder();
        String dot = "   \u00B7   ";
        if (marked) {
            t.append(getString(R.string.wp_delete_q));
        } else if (filterAt >= 0) {
            key(t, WallFilters.label(this, WallFilters.NAMES[filterAt]));
            if (filterAt > 0) t.append(" " + strength + "%");
            t.append(dot);
            key(t, "\u25C0\u25B6");
            t.append(" " + getString(R.string.wp_filter) + dot);
            key(t, "\u25B2\u25BC");
            t.append(" " + getString(R.string.wp_strength) + dot);
            key(t, "OK");
            t.append(" " + getString(R.string.wp_keep) + dot);
            key(t, getString(R.string.back_key));
            t.append(" " + getString(R.string.cancel));
        } else {
            t.append((previewAt + 1) + "/" + pictures.size() + dot);
            key(t, "\u25C0\u25B6");
            t.append(" " + getString(R.string.wp_next) + dot);
            key(t, "OK");
            t.append(" " + getString(R.string.wp_use) + dot);
            key(t, "\u25B2");
            t.append(" " + getString(R.string.delete) + dot);
            key(t, "\u25BC");
            t.append(" " + getString(R.string.wp_filter) + dot);
            key(t, getString(R.string.back_key));
            t.append(" " + getString(R.string.wp_return));
        }
        previewBar.setText(t);
        GradientDrawable pill = (GradientDrawable) previewBar.getBackground();
        pill.setColor(marked ? 0xE6B3261E : 0xCC0B0E16);
        pill.setStroke(Math.round((marked ? 2 : 1) * d), marked ? 0xFFFF8A80 : 0x40FFFFFF);
    }

    private static void key(android.text.SpannableStringBuilder t, String k) {
        int at = t.length();
        t.append(k);
        t.setSpan(new MainActivity.Weight(700), at, t.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    /** Up or down while trying a picture: it is marked in red and a box asks before deleting it. */
    private void markForDelete() {
        File f = pictures.get(previewAt);
        showPreviewBar(true);
        AlertDialog box = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.wp_delete_title)
                .setMessage(f.getName())
                .setPositiveButton(R.string.delete, (dlg, w) -> {
                    int at = previewAt;
                    if (f.delete() && f.getName().equals(prefs().getString(FIXED, ""))) {
                        prefs().edit().putString(FIXED, "").apply();
                    }
                    reload();
                    if (pictures.isEmpty()) {
                        endPreview(false);
                        return;
                    }
                    // Still trying pictures, on the one that took its place.
                    preview(Math.min(at, pictures.size() - 1));
                })
                .setNegativeButton(R.string.cancel, null)
                .setOnDismissListener(dlg -> {
                    if (previewAt >= 0) showPreviewBar(false);
                })
                .show();
        // Cancel is selected first, so a stray OK keeps the picture.
        View cancel = box.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (cancel != null) cancel.requestFocus();
    }

    /** Down while trying a picture: left and right go through the looks, shown on Home as they are chosen. */
    private void startFilters() {
        String kept = WallFilters.of(this, pictures.get(previewAt).getName());
        filterAt = java.util.Arrays.asList(WallFilters.NAMES).indexOf(WallFilters.name(kept));
        if (filterAt < 0) filterAt = 0;
        strength = WallFilters.strength(kept);
        showPreviewBar(false);
    }

    private void stepFilter(int by) {
        int n = WallFilters.NAMES.length;
        filterAt = ((filterAt + by) % n + n) % n;
        strength = 100;
        showFilter();
    }

    /** Up and down while choosing a look: stronger or fainter, 10% at a time. */
    private void stepStrength(int by) {
        if (filterAt <= 0) return;
        int next = Math.max(10, Math.min(100, strength + by));
        if (next == strength) return;
        strength = next;
        showFilter();
    }

    private void showFilter() {
        showPreviewBar(false);
        MainActivity home = MainActivity.current.get();
        if (home != null) home.previewWallpaper(pictures.get(previewAt), WallFilters.look(WallFilters.NAMES[filterAt], strength));
    }

    /** OK keeps the look for this picture; Back puts back the one it had. */
    private void endFilters(boolean keep) {
        File f = pictures.get(previewAt);
        if (keep) {
            WallFilters.set(this, f.getName(), WallFilters.look(WallFilters.NAMES[filterAt], strength));
            adapter.notifyItemChanged(previewAt);
        }
        filterAt = -1;
        showPreviewBar(false);
        MainActivity home = MainActivity.current.get();
        if (!keep && home != null) home.previewWallpaper(f, WallFilters.of(this, f.getName()));
    }

    /** Back from trying pictures: the collection returns, on the last picture tried. */
    private void endPreview(boolean keep) {
        if (previewAt < 0) return;
        int at = previewAt;
        previewAt = -1;
        filterAt = -1;
        MainActivity home = MainActivity.current.get();
        if (home != null) home.endPreview(keep ? pictures.get(at) : null);
        if (keep) return;
        previewBar.setVisibility(View.GONE);
        page.setVisibility(View.VISIBLE);
        page.animate().alpha(1f).setDuration(200).start();
        grid.scrollToPosition(at);
        grid.post(() -> focus(at));
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (previewAt < 0) return super.dispatchKeyEvent(e);
        int k = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        if (filterAt >= 0) {
            switch (k) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    if (down) stepFilter(-1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (down) stepFilter(1);
                    return true;
                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                    if (down && e.getRepeatCount() == 0) endFilters(true);
                    return true;
                case KeyEvent.KEYCODE_BACK:
                    if (!down) endFilters(false);
                    return true;
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (down) stepStrength(10);
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (down) stepStrength(-10);
                    return true;
                default:
                    return super.dispatchKeyEvent(e);
            }
        }
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (down) preview(previewAt - 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (down) preview(previewAt + 1);
                return true;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (down && e.getRepeatCount() == 0) markForDelete();
                return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down && e.getRepeatCount() == 0) startFilters();
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
                if (down && e.getRepeatCount() == 0) {
                    String name = pictures.get(previewAt).getName();
                    endPreview(true);
                    choose(name);
                }
                return true;
            case KeyEvent.KEYCODE_BACK:
                if (!down) endPreview(false);
                return true;
            default:
                return super.dispatchKeyEvent(e);
        }
    }

    @Override
    public void onBackPressed() {
        if (editing) {
            setEditing(false);
            return;
        }
        super.onBackPressed();
    }

    private void setEditing(boolean on) {
        editing = on;
        adapter.notifyItemRangeChanged(0, adapter.getItemCount());
        hint.setText(on ? getString(R.string.wp_edit_hint) : "");
    }

    @Override
    protected void onResume() {
        super.onResume();
        server = new WallpaperImport.Server(this, () -> main.post(this::reload));
        server.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Leaving while trying a picture (Home pressed): Home keeps the picture it had.
        if (previewAt >= 0 && !isFinishing()) endPreview(false);
        if (server != null) server.close();
        server = null;
    }

    @Override
    protected void onDestroy() {
        closed = true;
        super.onDestroy();
    }

    private android.content.SharedPreferences prefs() {
        return getSharedPreferences(PREFS, MODE_PRIVATE);
    }

    private void load() {
        pictures.clear();
        File dir = WallpaperImport.dir(this);
        File[] files = dir != null ? dir.listFiles() : null;
        if (files != null) {
            for (File f : files) {
                String n = f.getName().toLowerCase(Locale.ROOT);
                if (f.isFile() && (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png"))) pictures.add(f);
            }
            Collections.sort(pictures);
        }
    }

    private void reload() {
        if (closed) return;
        load();
        adapter.notifyDataSetChanged();
    }

    private void focus(int position) {
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(position);
        if (vh != null) ((LinearLayout) vh.itemView).getChildAt(0).requestFocus();
    }

    private int indexOf(String name) {
        for (int i = 0; i < pictures.size(); i++) if (pictures.get(i).getName().equals(name)) return i;
        return -1;
    }

    private void choose(String name) {
        endPreview(false);
        prefs().edit().putString(FIXED, name).apply();
        finish();
    }

    private void delete(File f) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.wp_delete_title)
                .setMessage(f.getName())
                .setPositiveButton(R.string.delete, (dlg, w) -> {
                    int at = pictures.indexOf(f);
                    if (f.delete() && f.getName().equals(prefs().getString(FIXED, ""))) {
                        prefs().edit().putString(FIXED, "").apply();
                    }
                    reload();
                    // The selection moves to the picture that took its place (or the one before the
                    // last), not up to the buttons.
                    int next = Math.max(0, Math.min(at, pictures.size() - 1));
                    if (!pictures.isEmpty()) grid.post(() -> {
                        grid.scrollToPosition(next);
                        grid.post(() -> focus(next));
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** Free picture sites, opened in the TV's browser; what is downloaded there comes in with Import. */
    private void openWebSources() {
        String[] names = {getString(R.string.wp_src_unsplash), getString(R.string.wp_src_commons),
                getString(R.string.wp_src_bing)};
        String[] urls = {"https://unsplash.com/wallpapers/desktop",
                "https://commons.wikimedia.org/wiki/Commons:Featured_pictures",
                "https://peapix.com/bing"};
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.wp_open_browser)
                .setItems(names, (dlg, which) -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(urls[which]))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                    } catch (Exception e) {
                        Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
                    }
                })
                .show();
    }

    /** Runs an import off the main thread and says how many pictures it added. */
    private void runImport(String what, java.util.concurrent.Callable<Integer> job) {
        Toast.makeText(this, what + "…", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            int n;
            try {
                n = job.call();
            } catch (Exception e) {
                n = 0;
            }
            final int added = n;
            main.post(() -> {
                if (closed) return;
                Toast.makeText(this, added == 0 ? getString(R.string.wp_none_new) : getString(R.string.wp_added, added), Toast.LENGTH_LONG).show();
                reload();
            });
        }, "wallpaper-import").start();
    }

    private void showPhoneUpload() {
        String ip = "";
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
        if (wm != null) {
            int a = wm.getConnectionInfo().getIpAddress();
            ip = String.format(Locale.ROOT, "%d.%d.%d.%d", a & 0xFF, (a >> 8) & 0xFF, (a >> 16) & 0xFF, (a >> 24) & 0xFF);
        }
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.wp_phone_title)
                .setMessage(getString(R.string.wp_phone_body, ip, WallpaperImport.PORT))
                .setPositiveButton("OK", null)
                .show();
    }

    private String fitLabel() {
        return getString("fit".equals(prefs().getString(FIT, "fill")) ? R.string.wp_size_fit : R.string.wp_size_fill);
    }

    private final class Adapter extends RecyclerView.Adapter<Adapter.Tile> {
        final class Tile extends RecyclerView.ViewHolder {
            final FrameLayout card;
            final ImageView picture;
            final TextView label, remove;
            String loading;

            Tile(LinearLayout v) {
                super(v);
                card = (FrameLayout) v.getChildAt(0);
                picture = (ImageView) card.getChildAt(0);
                label = (TextView) card.getChildAt(1);
                remove = (TextView) card.getChildAt(2);
            }
        }

        @NonNull
        @Override
        public Tile onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            int w = parent.getWidth() / COLUMNS - Math.round(30 * d);
            LinearLayout cell = new LinearLayout(WallpaperPickerActivity.this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            cell.setPadding(0, Math.round(12 * d), 0, Math.round(12 * d));
            cell.setClipChildren(false);
            cell.setClipToPadding(false);
            FrameLayout card = new FrameLayout(WallpaperPickerActivity.this);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0xFF1C2230);
            bg.setCornerRadius(12 * d);
            card.setBackground(bg);
            card.setClipToOutline(true);
            card.setFocusable(true);
            card.setClickable(true);
            ImageView picture = new ImageView(WallpaperPickerActivity.this);
            picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
            card.addView(picture, new FrameLayout.LayoutParams(-1, -1));
            TextView label = new TextView(WallpaperPickerActivity.this);
            label.setTextColor(0xFFFFFFFF);
            label.setTextSize(22f);
            label.setGravity(Gravity.CENTER);
            label.setShadowLayer(6 * d, 0, 0, 0xCC000000);
            card.addView(label, new FrameLayout.LayoutParams(-1, -1));
            cell.addView(card, new LinearLayout.LayoutParams(w, Math.round(w * 9f / 16f)));
            card.setOnFocusChangeListener((v, has) -> {
                v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setDuration(180).start();
                glow(v, has);
                // While deleting, the selected picture's ✕ lights up red: that is the one OK deletes.
                TextView x = (TextView) ((FrameLayout) v).getChildAt(2);
                if (editing) markRemove(x, has);
                GradientDrawable r = new GradientDrawable();
                r.setCornerRadius(12 * d);
                r.setStroke(Math.round(3 * d), 0xFFFFFFFF);
                v.setForeground(has ? r : null);
            });

            // A small round ✕ in the top-right corner, on every picture while deleting.
            TextView remove = new TextView(WallpaperPickerActivity.this);
            remove.setText("✕");
            remove.setTextColor(0xFFFFFFFF);
            remove.setTextSize(10f);
            remove.setGravity(Gravity.CENTER);
            remove.setFocusable(true);
            remove.setClickable(true);
            remove.setBackground(round(false));
            remove.setVisibility(View.GONE);
            FrameLayout.LayoutParams rl = new FrameLayout.LayoutParams(Math.round(24 * d), Math.round(24 * d),
                    Gravity.TOP | Gravity.END);
            rl.topMargin = Math.round(7 * d);
            rl.setMarginEnd(Math.round(7 * d));
            card.addView(remove, rl);
            remove.setOnFocusChangeListener((v, has) -> markRemove((TextView) v, has || ((View) v.getParent()).isFocused()));
            Tile tile = new Tile(cell);
            holdToEdit(tile);
            return tile;
        }

        /**
         * OK pressed and let go is a click; OK held for 2 seconds on a picture shows the ✕ on every
         * picture and moves the selection onto this one's. The card takes OK itself so Android's own
         * long press does not fire.
         */
        private void holdToEdit(Tile t) {
            final boolean[] fired = {false};
            final Runnable edit = () -> {
                fired[0] = true;
                // Every picture shows its ✕; the selection stays on this picture, whose ✕ is lit.
                setEditing(true);
            };
            t.card.setOnKeyListener((v, keyCode, event) -> {
                if (keyCode != KeyEvent.KEYCODE_DPAD_CENTER && keyCode != KeyEvent.KEYCODE_ENTER) return false;
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (event.getRepeatCount() == 0 && !editing) {
                        fired[0] = false;
                        main.postDelayed(edit, 2000);
                    }
                    return true;
                }
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    main.removeCallbacks(edit);
                    if (!fired[0]) v.performClick();
                    fired[0] = false;
                    return true;
                }
                return false;
            });
        }

        /** The ✕ that OK would delete: red, larger and glowing; the others stay small and dark. */
        private void markRemove(TextView x, boolean on) {
            x.setBackground(round(on));
            x.animate().scaleX(on ? 1.35f : 1f).scaleY(on ? 1.35f : 1f).setDuration(150).start();
            if (android.os.Build.VERSION.SDK_INT >= 28) {
                x.setOutlineAmbientShadowColor(0xFFFF5A4F);
                x.setOutlineSpotShadowColor(0x00000000);
            }
            x.setElevation(on ? 18 * d : 0);
        }

        private GradientDrawable round(boolean focused) {
            GradientDrawable g = new GradientDrawable();
            g.setShape(GradientDrawable.OVAL);
            g.setColor(focused ? 0xFFFF4F45 : 0x99000000);
            g.setStroke(Math.round(1.2f * d), focused ? 0xFFFFFFFF : 0xB3FFFFFF);
            return g;
        }

        @Override
        public void onBindViewHolder(@NonNull Tile t, int position) {
            File f = pictures.get(position);
            String fixed = prefs().getString(FIXED, "");
            t.remove.setVisibility(editing ? View.VISIBLE : View.GONE);
            markRemove(t.remove, editing && t.card.isFocused());
            t.remove.setOnClickListener(v -> delete(f));
            t.label.setText(f.getName().equals(fixed) ? "✓" : "");
            // While deleting, OK on the picture asks to delete it too.
            t.card.setOnClickListener(v -> {
                if (editing) delete(f);
                else preview(pictures.indexOf(f));
            });
            // The picture's look, if it has one, on its tile too.
            android.graphics.ColorMatrix look = WallFilters.matrix(WallFilters.of(WallpaperPickerActivity.this, f.getName()));
            t.picture.setColorFilter(look == null ? null : new android.graphics.ColorMatrixColorFilter(look));
            if (f.getName().equals(t.loading) && t.picture.getDrawable() != null) return;
            t.loading = f.getName();
            t.picture.setImageDrawable(null);
            new Thread(() -> {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inSampleSize = 8;
                Bitmap b = closed ? null : BitmapFactory.decodeFile(f.getPath(), o);
                main.post(() -> {
                    if (!closed && f.getName().equals(t.loading)) t.picture.setImageBitmap(b);
                });
            }, "wallpaper-thumb").start();
        }

        @Override
        public int getItemCount() {
            return pictures.size();
        }
    }
}
