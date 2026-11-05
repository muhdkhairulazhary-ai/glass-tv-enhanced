package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.AnimationUtils;
import android.view.animation.LayoutAnimationController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.dynamicanimation.animation.DynamicAnimation;
import androidx.dynamicanimation.animation.FloatPropertyCompat;
import androidx.dynamicanimation.animation.SpringAnimation;
import androidx.dynamicanimation.animation.SpringForce;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * tvOS style home: black, a pinned dock row, an app grid, and one glass highlight that springs between tiles.
 */
public class MainActivity extends Activity implements AppAdapter.TileListener {

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }

    /** Tiles per row and dock slots (res/values/config.xml, columns). Set in onCreate. */
    static int COLUMNS = 4;
    private static final long MINUTE_MS = 60_000L;
    private String liveTvPkg;
    private String liveTvAction;
    private String inputAction;
    private String sourcePanelService;
    private String sourcePanelAction;

    private AppAdapter adapter;
    private RecyclerView grid;
    private FrameLayout root;
    private GlassView glass;
    private View ambient;
    /** The focus: one dark shape gliding between tiles and pills (see Glider). */
    private Glider glider;
    /** Whether the glider is on the top menu, so a move down travels dark. */
    private boolean gliderOnPill;
    private final int[] pillAt = new int[2], rootAt = new int[2];
    /** The top-right buttons and the two rectangles used to line them up with the dock. */
    private View topRow, topSettings;
    private final Rect alignCard = new Rect(), alignButton = new Rect();
    /** How much of the space between the side paddings the top shelf may use. */
    private static final float HERO_WIDTH_SHARE = 0.40f;
    /** The name is one line, and may run wider than the description, up to this share. */
    private static final float HERO_NAME_WIDTH_SHARE = 0.60f;
    private static final float HERO_TITLE_MAX_SP = 40f;
    /** The breathing room between the shelf's last line and the top of the app container. */
    private static final float HERO_GAP_DP = 67f;
    private static final float HERO_TITLE_MIN_SP = 26f;
    /** The shelf's width in pixels, worked out once from the screen. */
    private int heroMaxWidth, heroNameMaxWidth;
    private View heroShelf;
    private TextView heroName;
    private TextView heroDesc;
    private TextView heroWhere;
    /** The place on screen and a few lines about it; null for the user's own wallpapers. */
    private String placeName;
    private String placeText = "", placeCountry = "";
    private int ambientColor = -1;
    private boolean heroShown;
    /** The glow behind the tiles. It follows every app, while the shelf only names dock apps. */
    private boolean ambientShown;

    /** The colour the glass is blending from, how far along it is, and the blend itself. */
    private int glowFrom;
    private float glowMix = 1f;
    private android.animation.ValueAnimator glowBlend;
    private Bitmap ambientShape;
    /** Where the shelf's bottom edge belongs; its top follows once the lines have been measured. */
    private float heroBottom;
    private View btnInputs;
    private TextView btnWifi;
    private TextView shamsiDate;
    private boolean shamsi;
    /** JAKIM zone for the prayer row, empty when it is off; the row's six time cells and the info line after them. */
    private String prayerZone = "";
    private boolean heroQuotes;
    private boolean tileGlow;
    private boolean dockMagnify;
    private Set<String> eplTeams = Collections.emptySet();
    private TextView eplCard;
    private boolean eplRefreshRunning;
    private Dialog eplDialog;
    private boolean hourGlow;
    private boolean prayerAlert;
    private boolean cornerGlow;
    /** The edge glow's slow breath (CornerGlow.pulse), only while Home is in front. */
    private android.animation.ValueAnimator glowPulse;

    private void spinCorners(boolean on) {
        if (!cornerGlow) return;
        if (glowPulse != null) glowPulse.cancel();
        glowPulse = null;
        CornerGlow.pulse = 1f;
        if (!on || reducedMotion) return;
        glowPulse = android.animation.ValueAnimator.ofFloat(1f, 0.5f);
        glowPulse.setDuration(3500);
        glowPulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        glowPulse.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        glowPulse.setInterpolator(new android.view.animation.AccelerateDecelerateInterpolator());
        glowPulse.addUpdateListener(a -> {
            CornerGlow.pulse = (float) a.getAnimatedValue();
            if (ambientShown) ambient.invalidate();
        });
        glowPulse.start();
    }
    /** The hover tick (hover_sound): loaded once, played as the selection moves between apps. */
    private android.media.SoundPool ticks;
    private int tick;
    private int tickedAt = -1;
    private long tickAt;

    /** The hover tick, at most once per move however the move arrives (sideways, up, down). */
    private void playTick() {
        long now = android.os.SystemClock.uptimeMillis();
        if (ticks == null || now - tickAt < 120) return;
        tickAt = now;
        ticks.play(tick, 0.35f, 0.35f, 0, 0, 1f);
    }
    /** The cell blinking in the last seconds before its time, or -1. */
    private int blinking = -1;
    private android.animation.ValueAnimator blink;
    private VoiceGlow voiceGlow;
    /** Name of the wallpaper on screen, so a picture chosen in the picker can be put up on return. */
    private String wallName;
    /** Dock tiles currently raised beside the selected one. */
    private final List<View> raised = new ArrayList<>();
    private LinearLayout prayerRow;
    private final TextView[] prayerLabels = new TextView[PrayerTimes.NAMES.length];
    private final TextView[] prayerClocks = new TextView[PrayerTimes.NAMES.length];
    private TextView prayerInfo;
    /** The chosen place's weather under the Hijri date; OK opens the weather page. */
    private TextView weatherLine;
    private boolean weatherRefreshRunning;
    /** Space round the prayer texts, in dp, so their glow is not cut off at the text's edge. */
    private static final int GLOW_ROOM = 9;
    private boolean prayerRefreshRunning;
    private String vpnApp;
    private SpringAnimation springX, springY;
    private float glassTargetX, glassTargetY;
    // How far the grid has scrolled the glass. springY works without it, in grid content space, so a
    // scroll moves the glass in lockstep with its tile instead of becoming a moving spring target.
    private float glassScrollShift;
    private boolean glassShown;
    // Static so they survive the activity being destroyed while another app is open (low-memory TVs,
    // or Developer options → "Don't keep activities"); they only reset when the process dies.
    private static int lastFocused = 0;
    /** App of the last focused tile, so focus follows it when the app list changes. */
    private static String lastFocusedKey;
    private float density;
    /** Module cards (res/values/config.xml, module_cards): discovered on resume, shown only after the owner's Show. */
    private boolean moduleCards;
    /** Calmer motion for weak GPUs (config.xml, reduced_motion): stiff spring, short fades, no replay. */
    private boolean reducedMotion;
    /** hidden_apps as the adapter sees them (config.xml or a theme's config.json): a hidden app gets no card either. */
    private Set<String> hiddenApps = Collections.emptySet();
    private List<Cards.Card> cards = Collections.emptyList();
    /** The grid has been laid out and focused since the last resume; the card prompt waits for this. */
    private boolean tilesReady;
    private boolean tilesAnimatedOnce;
    /** Grid rows left showing under the dock (config rows_below_dock). */
    private int rowsBelowDockSetting;
    /** The longest Home holds its first frame while it puts itself together (see holdFirstFrame). */
    private static final long FIRST_FRAME_WAIT_MS = 1200;
    private static final long RETURN_SETTLE_MS = 6000;
    /** True while Home rebuilds on return; focus passes through the pills then and must not light them. */
    private boolean resuming;
    /** True while Home's first frame is held back (see holdFirstFrame). */
    private boolean firstFrameHeld;
    private Dialog cardPrompt;
    /** Set when a decision could not be saved: no more prompts until the next resume, instead of asking in a loop. */
    private boolean promptsPaused;
    /** The Inputs or Module cards dropdown while one is open, so onPause can close it. */
    private PopupWindow openPopup;

    private final Handler handler = new Handler(Looper.getMainLooper());
    /** Rotating wallpaper (Config.wallpapers), the view that shows it and the bitmap on screen. */
    private List<File> wallpapers = Collections.emptyList();
    private long wallpaperIntervalMs;
    /** Set while Home is behind another app, for the change-on-every-return choice. */
    private boolean returning;
    private int storageTries;

    private final Runnable storageWait = new Runnable() {
        @Override
        public void run() {
            if (isDestroyed()) return;
            if (Config.ready(MainActivity.this)) {
                android.util.Log.i("TvHome.Config", "shared storage is up: reloading the theme");
                recreate();
            } else if (++storageTries < 360) {
                handler.postDelayed(this, 500);
            }
        }
    };

    /** How often the picture changes: the picker's choice, else the theme's. 0 = only when Home starts. */
    private long wallInterval() {
        long chosen = getSharedPreferences(WallpaperPickerActivity.PREFS, MODE_PRIVATE)
                .getLong(WallpaperPickerActivity.INTERVAL, Long.MIN_VALUE);
        return chosen == Long.MIN_VALUE ? wallpaperIntervalMs : chosen;
    }
    /** Decodes the next picture off the main thread, so a switch never stutters the D-pad animations. */
    private final java.util.concurrent.ExecutorService wallDecoder = java.util.concurrent.Executors.newSingleThreadExecutor();
    private long wallShownAt;
    private android.widget.ImageView wallView;
    private android.graphics.Bitmap wallBitmap;
    /** Settings kept for the places refresh, which runs after onCreate. */
    private Config config;
    /** Pictures of places are on (see PlaceWallpapers); they are dimmed behind the tiles. */
    private boolean placesMode;
    private volatile boolean placesRefreshRunning;
    /** Refreshes the network pill when the connection changes: at boot Wi-Fi often connects after Home is shown. */
    private final ConnectivityManager.NetworkCallback networkCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
            // Online at last (at boot Wi-Fi comes up after Home): fetch what failed while offline.
            handler.post(() -> {
                if (isDestroyed()) return;
                if (EplScores.matches.isEmpty()) EplScores.fetchedAt = 0;
                refreshEplIfDue();
                refreshWeatherIfDue();
            });
        }

        @Override
        public void onLost(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }
    };
    /** Shows the Inputs pill once the TV registers an input: at boot HDMI inputs can appear after Home is shown. */
    private final TvInputManager.TvInputCallback inputCallback = new TvInputManager.TvInputCallback() {
        @Override
        public void onInputAdded(String inputId) {
            updateInputsButton();
        }

        @Override
        public void onInputRemoved(String inputId) {
            updateInputsButton();
        }
    };
    /** Listens for any VPN coming up or going down, even one this app's traffic is excluded from. */
    private final ConnectivityManager.NetworkCallback vpnCallback = new ConnectivityManager.NetworkCallback() {
        @Override
        public void onAvailable(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }

        @Override
        public void onLost(@NonNull Network network) {
            handler.post(MainActivity.this::updateWifiLabel);
        }
    };
    private final IntentFilter packageFilter = new IntentFilter();
    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Updates send REMOVED followed by ADDED; keep the tile until the new APK is ready.
            if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())
                    && intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) return;
            reloadApps(false);
        }
    };
    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            updateShamsiDate();
            updatePrayerTimes();
            refreshEplIfDue();
            refreshWeatherIfDue();
            updateWifiLabel();
            refreshPlacesIfDue();
            // Wisdom set to change every few minutes.
            if (heroQuotes && wallName != null) setPlace(new File(wallName));
            handler.postDelayed(this, MINUTE_MS);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        HomeDiagnostics.activity(this, "onCreate", getIntent());
        HomeDiagnostics.selection(this);
        setContentView(R.layout.activity_main);
        density = getResources().getDisplayMetrics().density;
        current = new java.lang.ref.WeakReference<>(this);
        // config.xml, overlaid by config.json from a pushed theme (see Config and tools/theme.sh).
        Config cfg = Config.load(this);
        COLUMNS = cfg.columns;
        AppAdapter.TILE_SCALE = cfg.tileSizePercent / 100f;
        // Started before shared storage was mounted (just after the TV turned on): the theme came from
        // the internal copy, but the pictures are not reachable yet. Look again until they are, then
        // start over once with everything in place.
        if (!Config.ready(this)) handler.postDelayed(storageWait, 500);
        AppAdapter.MAX_TILES = cfg.maxTiles;
        AppAdapter.SHEEN = cfg.tileSheen;
        AppAdapter.GLASS_ICON = cfg.glassIcon;
        AppAdapter.PULSE = cfg.tilePulse;
        prayerAlert = cfg.prayerAlert;
        cornerGlow = cfg.cornerGlow;
        if (cfg.hoverSound) {
            ticks = new android.media.SoundPool.Builder().setMaxStreams(2)
                    .setAudioAttributes(new android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .build();
            tick = ticks.load(this, R.raw.hover_tick, 1);
        }
        eplTeams = new HashSet<>(cfg.eplTeams);
        hourGlow = cfg.hourGlow;
        if (cfg.voiceGlow) {
            voiceGlow = new VoiceGlow(this);
            voiceGlow.start();
        }
        tileGlow = cfg.tileGlow;
        dockMagnify = cfg.dockMagnify;
        reducedMotion = cfg.reducedMotion;
        liveTvPkg = cfg.liveTvPackage;
        liveTvAction = cfg.liveTvAction;
        inputAction = cfg.inputAction;
        sourcePanelService = cfg.sourcePanelService;
        sourcePanelAction = cfg.sourcePanelAction;

        root = findViewById(R.id.root);
        // The tiles' entrance plays once per TV boot. Android reclaims the launcher during long stays
        // in big apps; starting again then is a return to Home, not a first launch.
        long bootAt = System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime();
        android.content.SharedPreferences launch = getSharedPreferences("launch", MODE_PRIVATE);
        if (Math.abs(launch.getLong("boot_at", 0) - bootAt) < 60_000) tilesAnimatedOnce = true;
        else launch.edit().putLong("boot_at", bootAt).apply();
        root.setBackgroundColor(cfg.backgroundColor);
        config = cfg;
        wallpapers = cfg.wallpapers;
        wallpaperIntervalMs = cfg.wallpaperIntervalMs;
        placesMode = cfg.places;
        // Places only, when they are on: never the theme's own pictures, even before the first
        // week's places have arrived.
        if (placesMode) wallpapers = PlaceWallpapers.list(this);
        if (!wallpapers.isEmpty()) showNextWallpaper(false);
        else if (cfg.wallpaper != null && !placesMode) setWallpaper(decodeWallpaper(cfg.wallpaper), false);
        else if (lastWallpaper().exists()) {
            // Shared storage is not up yet (just after the TV starts): the picture last on screen,
            // kept in the app's own storage, so Home looks itself at once instead of plain.
            wallDecoder.execute(() -> {
                android.graphics.Bitmap bmp = WallFilters.apply(decodeWallpaper(lastWallpaper()),
                        WallFilters.of(this, getSharedPreferences(WallpaperPickerActivity.PREFS, MODE_PRIVATE).getString(LAST_NAME, null)));
                handler.post(() -> {
                    if (!isDestroyed()) setWallpaper(bmp, false);
                });
            });
        }
        glass = findViewById(R.id.glass);
        ambient = findViewById(R.id.ambient);
        Backdrop.setWorker(wallDecoder, handler);
        heroShelf = findViewById(R.id.heroShelf);
        heroName = findViewById(R.id.heroName);
        heroDesc = findViewById(R.id.heroDesc);
        heroWhere = findViewById(R.id.heroWhere);
        // A share of the space between the side paddings, so the description wraps into a column
        // rather than running the width of the screen. The name is one line, allowed a wider share.
        int usable = getResources().getDisplayMetrics().widthPixels - (int) ((55 + 56) * density);
        heroMaxWidth = Math.round(usable * HERO_WIDTH_SHARE);
        heroNameMaxWidth = Math.round(usable * HERO_NAME_WIDTH_SHARE);
        heroName.setMaxWidth(heroNameMaxWidth);
        heroDesc.setMaxWidth(heroMaxWidth);
        // The shelf grows and shrinks with the name and the description, so it hangs from its
        // bottom edge: every time it is measured, its top is worked out again. Positioning it by
        // the height it happened to have beforehand is what let a tall block run into the dock.
        heroShelf.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orr, ob) -> {
            // The Wisdom widget hangs from the top instead, under the prayer times, so a short
            // saying does not drop it (and the score card beside it) down towards the dock.
            if (heroQuotes) v.setY(widgetTop());
            else if (heroBottom > 0) v.setY(heroBottom - v.getHeight());
        });
        btnWifi = findViewById(R.id.btnWifi);
        if (!cfg.showNetworkPill) btnWifi.setVisibility(View.GONE);
        shamsiDate = findViewById(R.id.shamsiDate);
        shamsi = cfg.shamsiDate;
        if (shamsi) {
            shamsiDate.setVisibility(View.VISIBLE);
            TextView date = findViewById(R.id.date);
            date.setTextSize(11f);
            date.setPadding(0, (int) (4 * density), 0, 0);
        }
        // Beside the clock, the date sits on the digits' baseline, and AM/PM rests halfway between
        // the top of the digits and the top of the date, its letters' foot on that line.
        TextView clock = findViewById(R.id.clock), amPm = findViewById(R.id.ampm), dateLine = findViewById(R.id.date);
        View dateBlock = findViewById(R.id.dateBlock);
        dateBlock.setTranslationY(dateLine.getPaint().getFontMetrics().descent - clock.getPaint().getFontMetrics().descent);
        // Placed once the whole column is laid out, AM/PM and the date both.
        ((View) dateBlock.getParent()).addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            android.graphics.Rect cap = new android.graphics.Rect();
            dateLine.getPaint().getTextBounds("O", 0, 1, cap);
            float capsAtTop = ClockDream.capInset(clock, "0") - ClockDream.capInset(amPm, "AM");
            float foot = amPm.getTop() + capsAtTop + amPm.getBaseline();
            float dateTop = dateBlock.getTop() + dateBlock.getTranslationY() + dateLine.getTop() + dateLine.getBaseline() + cap.top;
            amPm.setTranslationY(Math.round(capsAtTop + (foot + dateTop) / 2f - foot));
        });
        prayerZone = cfg.prayerZone;
        heroQuotes = cfg.heroQuotes;
        prayerRow = findViewById(R.id.prayerRow);
        if (heroQuotes) setupWisdomWidget();
        if (!prayerZone.isEmpty()) buildPrayerRow();
        vpnApp = cfg.vpnApp;
        moduleCards = cfg.moduleCards;
        hiddenApps = new HashSet<>(cfg.hiddenApps);

        grid = findViewById(R.id.grid);
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS) {
            @Override
            public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
                // The dock's line is set before any row is placed. Set afterwards, the grid kept its
                // rows where the first layout had put them, and Home came back with the dock and its
                // tray hundreds of pixels up the screen until focus slid them down.
                parkDock();
                super.onLayoutChildren(recycler, state);
            }
        });
        grid.setHasFixedSize(true);
        grid.setItemAnimator(null);
        DockDecoration tray = new DockDecoration(COLUMNS, density);
        grid.addItemDecoration(tray);
        // The top-right buttons line up with the dock, which is only known once the tiles are laid out.
        grid.getViewTreeObserver().addOnGlobalLayoutListener(this::alignTopButtons);
        // Glass shows the blurred wallpaper behind it: the dock tray (drawn by the grid) and the
        // top-bar pills redraw when a new blurred copy is ready.
        Backdrop.watch(grid);
        setUpGlider();
        holdFirstFrame();
        for (int i = 0; i < grid.getItemDecorationCount(); i++) {
            if (grid.getItemDecorationAt(i) instanceof DockDecoration) {
                ((DockDecoration) grid.getItemDecorationAt(i)).setGlider(glider);
            }
        }
        // The grid only shows the shape on the tray.
        glider.addCompanion(grid, tray.area);
        for (int id : new int[]{R.id.btnInputs, R.id.btnWifi, R.id.btnWallpaper, R.id.btnSettings}) {
            View pill = findViewById(id);
            pill.setBackground(new GlassDrawable(pill, 21 * density, density, glider));
            glider.addCompanion(pill);
            Backdrop.watch(pill);
            pill.setOnFocusChangeListener((v, has) -> {
                if (has) moveTopHighlight(v);
            });
        }
        // The apps rise into place on the first launch after the TV starts, and only then. Setting
        // the animation arms it for the grid's first draw, so on any later fresh start (Android
        // closing Home while an app was open) it is not set at all: armed, it drew the dock's tray
        // empty while the apps faded in.
        if (!tilesAnimatedOnce) {
            int enterAnim = reducedMotion ? R.anim.tile_enter_reduced : R.anim.tile_enter;
            float enterDelay = reducedMotion ? 0f : 0.06f;
            grid.setLayoutAnimation(new LayoutAnimationController(
                    AnimationUtils.loadAnimation(this, enterAnim), enterDelay));
        }
        adapter = new AppAdapter(this, this, COLUMNS, cfg);
        grid.setAdapter(adapter);

        // tvOS layout: push the dock row towards the bottom of the screen, leaving room for
        // rows_below_dock grid rows under it; the rest of the grid stays below until you press Down.
        rowsBelowDockSetting = cfg.rowsBelowDock;
        grid.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            parkDock();
            updateTopFade();
        });

        // Rows feather out under the top bar instead of being cut off. The fade needs an offscreen
        // layer and draws the grid twice, on every frame of every animation, so it is on only while a
        // row is actually in the band: it switches on in the same frame a row scrolls into it.
        gridWrap = findViewById(R.id.gridWrap);

        // Keep the glass glued to its tile while the grid scrolls.
        grid.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView rv, int dx, int dy) {
                updateTopFade();
                if (!glassShown) return;
                glassTargetY -= dy;
                glassScrollShift -= dy;
                glider.scrolled(dy);
            }
        });

        // Liquid feel: soft springs on the highlight's position.
        springX = new SpringAnimation(glass, DynamicAnimation.TRANSLATION_X);
        springY = new SpringAnimation(glass, new FloatPropertyCompat<View>("glassContentY") {
            @Override
            public float getValue(View v) {
                return v.getTranslationY() - glassScrollShift;
            }

            @Override
            public void setValue(View v, float value) {
                v.setTranslationY(value + glassScrollShift);
            }
        });
        for (SpringAnimation s : new SpringAnimation[]{springX, springY}) {
            if (reducedMotion) {
                // Stiff and almost critically damped: settles in a few frames instead of oscillating,
                // and stops once the highlight is within 1.5 px of its tile.
                s.setSpring(new SpringForce().setStiffness(1400f).setDampingRatio(0.95f));
                s.setMinimumVisibleChange(1.5f);
            } else {
                // Critically damped: glides onto the tile and stops there, with no bounce back.
                s.setSpring(new SpringForce().setStiffness(900f).setDampingRatio(1.0f));
            }
        }

        btnInputs = findViewById(R.id.btnInputs);
        View btnSettings = findViewById(R.id.btnSettings);
        btnInputs.setOnClickListener(v -> showInputs());
        btnWifi.setOnClickListener(v -> openNetworkPill());
        btnWifi.setOnLongClickListener(v -> {
            open(systemIntent(Settings.ACTION_WIFI_SETTINGS));
            return true;
        });
        btnSettings.setOnClickListener(v -> open(systemIntent(Settings.ACTION_SETTINGS)));
        View btnWallpaper = findViewById(R.id.btnWallpaper);
        btnWallpaper.setOnClickListener(v -> open(new Intent(this, WallpaperPickerActivity.class)));
        btnSettings.setOnLongClickListener(v -> {
            if (!moduleCards) return false;   // off: a long press does nothing, as before the feature
            showModuleCards();
            return true;
        });

        // Keep focus on the top bar at its ends and at the top: Android's focus finder would otherwise
        // jump to a tile far below when there is nothing in that direction.
        View.OnKeyListener pillKeys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                // Down the screen in order: weather, prayer times, the score card, then the dock.
                if (weatherLine != null && weatherLine.getVisibility() == View.VISIBLE) weatherLine.requestFocus();
                else if (prayerRow != null && prayerRow.getVisibility() == View.VISIBLE) prayerRow.requestFocus();
                else focusMiddle(v);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                // The left end is Inputs, or Wi-Fi on a box without inputs, or Settings when both are gone.
                return v.getId() == R.id.btnInputs
                        || (v.getId() == R.id.btnWifi && btnInputs.getVisibility() != View.VISIBLE)
                        || (v.getId() == R.id.btnSettings && btnInputs.getVisibility() != View.VISIBLE
                            && btnWifi.getVisibility() != View.VISIBLE);
            }
            return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v.getId() == R.id.btnSettings;
        };
        btnInputs.setOnKeyListener(pillKeys);
        btnWifi.setOnKeyListener(pillKeys);
        btnSettings.setOnKeyListener(pillKeys);
        btnWallpaper.setOnKeyListener(pillKeys);

        // Hide the glass when focus leaves the tiles.
        getWindow().getDecorView().getViewTreeObserver().addOnGlobalFocusChangeListener((oldF, newF) -> {
            boolean onTile = newF != null && newF.getId() == R.id.card;
            if (!onTile) hideGlass();
            if (!onTile) muteOthers(null);
            if (!onTile && (newF == null || !(isTopPill(newF) || inMenu(newF)))) glider.hide();
        });

        packageFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        packageFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        packageFilter.addDataScheme("package");

        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) tim.registerCallback(inputCallback, handler);
        if (cm != null) {
            cm.registerDefaultNetworkCallback(networkCallback);
            cm.registerNetworkCallback(new NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_VPN)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .build(), vpnCallback);
        }
    }

    /**
     * A theme's full-screen picture behind everything. Decoded once, downsampled to the screen so a 4K
     * JPEG costs a 1080p bitmap at most (about 8 MB), which is the price of a wallpaper on a 1 GB TV.
     */
    private android.graphics.Bitmap decodeWallpaper(File file) {
        try {
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            android.graphics.BitmapFactory.Options opts = new android.graphics.BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            android.graphics.BitmapFactory.decodeFile(file.getPath(), opts);
            int sample = 1;
            while (opts.outWidth / (sample * 2) >= dm.widthPixels && opts.outHeight / (sample * 2) >= dm.heightPixels) sample *= 2;
            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sample;
            opts.inPreferredConfig = android.graphics.Bitmap.Config.RGB_565;   // no alpha needed, half the memory
            return android.graphics.BitmapFactory.decodeFile(file.getPath(), opts);
        } catch (Exception | OutOfMemoryError e) {
            // A bad or huge picture must never take the home screen down; the plain colour stays.
            return null;
        }
    }

    /** A copy of the picture on screen in the app's own storage, readable before shared storage is up. */
    private File lastWallpaper() {
        return new File(getFilesDir(), "last-wallpaper.jpg");
    }

    /** Pref naming the picture copied to {@link #lastWallpaper()}, for its filter. */
    private static final String LAST_NAME = "last_name";

    /** Copies the picture on screen to {@link #lastWallpaper()}; call off the main thread. */
    private void keepAsLast(File file) {
        try {
            java.nio.file.Files.copy(file.toPath(), lastWallpaper().toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            getSharedPreferences(WallpaperPickerActivity.PREFS, MODE_PRIVATE).edit().putString(LAST_NAME, file.getName()).apply();
        } catch (Exception ignored) {
        }
    }

    /** Puts a picture behind everything, cross-fading from the previous one when asked. */
    private void setWallpaper(android.graphics.Bitmap bmp, boolean fade) {
        setWallpaper(bmp, fade, null);
    }

    /** Fill the screen (cropping the edges) or fit the whole picture, as chosen in the picker. */
    private void applyWallpaperFit() {
        if (wallView == null) return;
        boolean fit = "fit".equals(getSharedPreferences(WallpaperPickerActivity.PREFS, MODE_PRIVATE)
                .getString(WallpaperPickerActivity.FIT, "fill"));
        wallView.setScaleType(fit ? android.widget.ImageView.ScaleType.FIT_CENTER
                : android.widget.ImageView.ScaleType.CENTER_CROP);
    }

    /** With {@code blur} made in advance, the glass changes in step with the picture. */
    private void setWallpaper(android.graphics.Bitmap bmp, boolean fade, android.graphics.Bitmap blur) {
        if (bmp == null) return;
        if (wallView == null) {
            wallView = new android.widget.ImageView(this);
            wallView.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
            handler.post(this::applyWallpaperFit);
            // Photos are bright: dim them so the clock, names and tiles stay readable.
            if (placesMode) wallView.setColorFilter(0xFF7A7A7A, android.graphics.PorterDuff.Mode.MULTIPLY);
            root.addView(wallView, 0, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            // A faint vignette over the picture, under everything else.
            View vignette = new View(this);
            vignette.setBackground(new Vignette());
            root.addView(vignette, 1, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        android.graphics.Bitmap old = wallBitmap;
        wallBitmap = bmp;
        // The edge glow takes the picture's colours near each edge.
        try {
            CornerGlow.wallpaper = android.graphics.Bitmap.createScaledBitmap(bmp, 8, 5, true);
        } catch (Exception ignored) {
        }
        if (fade && old != null && !reducedMotion) {
            android.graphics.drawable.TransitionDrawable t = new android.graphics.drawable.TransitionDrawable(new android.graphics.drawable.Drawable[]{
                    new android.graphics.drawable.BitmapDrawable(getResources(), old),
                    new android.graphics.drawable.BitmapDrawable(getResources(), bmp)});
            t.setCrossFadeEnabled(true);
            wallView.setImageDrawable(t);
            t.startTransition(800);
            // The glass fades to the new picture over the same 800 ms as the wallpaper itself.
            if (blur != null) Backdrop.present(blur, 800);
            else blurBackdrop(bmp);
            // Drop the old picture once the fade is done, so only one 1080p bitmap stays in memory.
            handler.postDelayed(() -> {
                if (wallBitmap == bmp) wallView.setImageBitmap(bmp);
            }, 900);
        } else {
            wallView.setImageBitmap(bmp);
            if (blur != null) Backdrop.present(blur, 0);
            else blurBackdrop(bmp);
        }
        // An opaque picture covers the whole screen, so the black window and the plain background
        // under it are never seen. Painting them anyway cost two full-screen passes on every frame,
        // and this TV's graphics chip, not the app, is what holds each frame back.
        if (!bmp.hasAlpha()) {
            root.setBackground(null);
            getWindow().setBackgroundDrawable(null);
        }
    }

    /** Home while it exists, so the wallpaper picker (see-through) can try pictures on it. */
    static java.lang.ref.WeakReference<MainActivity> current = new java.lang.ref.WeakReference<>(null);
    /** The picture Home had before the picker started trying others, put back if none is kept. */
    private android.graphics.Bitmap beforePreview;
    private volatile File previewing;
    private volatile String previewFilter;
    /** The look Home's own picture had when the trying began: if it changed, the picture comes back in the new one. */
    private String beforeFilter;

    /** Shows {@code file} with the look {@code filter} behind the picker, as Home would, without choosing it. */
    void previewWallpaper(File file, String filter) {
        if (placesMode || isDestroyed()) return;
        if (beforePreview == null) {
            beforePreview = wallBitmap;
            beforeFilter = WallFilters.of(this, wallName);
        }
        previewing = file;
        previewFilter = filter;
        applyWallpaperFit();
        wallDecoder.execute(() -> {
            if (previewing != file || !filter.equals(previewFilter)) return;
            android.graphics.Bitmap bmp = WallFilters.apply(decodeWallpaper(file), filter);
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            android.graphics.Bitmap blur = bmp == null || previewing != file ? null : Backdrop.blur(bmp, dm.widthPixels, dm.heightPixels, 0);
            handler.post(() -> {
                if (isDestroyed() || previewing != file || !filter.equals(previewFilter) || bmp == null) return;
                setWallpaper(bmp, true, blur);
            });
        });
    }

    /** The picker is done trying: keep {@code kept} (already on screen) or put the old picture back. */
    void endPreview(File kept) {
        if (beforePreview == null && previewing == null) return;
        File shown = previewing;
        previewing = null;
        android.graphics.Bitmap old = beforePreview;
        beforePreview = null;
        if (kept != null && kept.equals(shown) && wallBitmap != old) {
            wallName = kept.getName();
            setPlace(kept);
            wallDecoder.execute(() -> keepAsLast(kept));
        } else if (wallName != null && !WallFilters.of(this, wallName).equals(beforeFilter)) {
            // Home's own picture was given a new look while trying: it comes back in that look.
            for (File f : wallpapers) {
                if (!f.getName().equals(wallName)) continue;
                String look = WallFilters.of(this, wallName);
                wallDecoder.execute(() -> {
                    android.graphics.Bitmap bmp = WallFilters.apply(decodeWallpaper(f), look);
                    handler.post(() -> {
                        if (!isDestroyed() && bmp != null && previewing == null) setWallpaper(bmp, true);
                    });
                });
            }
        } else if (old != null && wallBitmap != old) {
            setWallpaper(old, true);
        }
    }

    /** Hands the picture now on screen to the glass, which blurs it once, off the main thread. */
    private void blurBackdrop(android.graphics.Bitmap bmp) {
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        Backdrop.update(bmp, dm.widthPixels, dm.heightPixels, placesMode ? 0xFF7A7A7A : 0, wallDecoder, handler);
    }

    /** Shows the next picture from the wallpapers folder; the position survives restarts. */
    private void showNextWallpaper(boolean fade) {
        android.content.SharedPreferences prefs = getSharedPreferences("wallpaper", MODE_PRIVATE);
        int next = (prefs.getInt("index", -1) + 1) % wallpapers.size();
        // A picture chosen in the picker stays until Auto is chosen again.
        String fixed = prefs.getString(WallpaperPickerActivity.FIXED, "");
        for (int i = 0; i < wallpapers.size() && !fixed.isEmpty(); i++) {
            if (wallpapers.get(i).getName().equals(fixed)) next = i;
        }
        prefs.edit().putInt("index", next).apply();
        wallShownAt = android.os.SystemClock.elapsedRealtime();
        final File file = wallpapers.get(next);
        wallName = file.getName();
        // On a cold start, name the place straight away: waiting for the decode showed the app's name
        // at full size and shrank it a moment later. After that the name changes with the picture,
        // or it would name the next place over the last one's picture while that one decodes.
        if (placeName == null) setPlace(file);
        final boolean firstPicture = wallBitmap == null;
        wallDecoder.execute(() -> {
            android.graphics.Bitmap bmp = WallFilters.apply(decodeWallpaper(file), WallFilters.of(this, file.getName()));
            if (bmp != null) keepAsLast(file);
            // After the first picture, the blur is made here, before the picture is shown, so the glass
            // changes with the wallpaper rather than after it. The first picture of a fresh start does
            // not wait for its blur: Home is held for it, and the glass catches up a moment later.
            android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
            android.graphics.Bitmap blur = firstPicture ? null
                    : Backdrop.blur(bmp, dm.widthPixels, dm.heightPixels, placesMode ? 0xFF7A7A7A : 0);
            handler.post(() -> {
                if (isDestroyed()) return;
                setWallpaper(bmp, fade, blur);
                setPlace(file);
            });
        });
    }

    /** Switches to the next picture every wallpaper interval while Home is in front. */
    private final Runnable wallTick = new Runnable() {
        @Override
        public void run() {
            if (wallpapers.size() >= 2) showNextWallpaper(true);
            handler.postDelayed(this, Math.max(60_000L, wallInterval()));
        }
    };

    /** Fetches the week's places in the background, then switches the rotation to them. */
    private void refreshPlacesIfDue() {
        if (!placesMode || placesRefreshRunning || !PlaceWallpapers.due(this)) return;
        placesRefreshRunning = true;
        new Thread(() -> {
            boolean changed = PlaceWallpapers.refresh(getApplicationContext());
            handler.post(() -> {
                placesRefreshRunning = false;
                if (!changed || isDestroyed()) return;
                List<File> places = PlaceWallpapers.list(this);
                if (places.isEmpty()) return;
                // Last week's pictures are gone: show one of this week's straight away, so the
                // name on the shelf and the picture behind it always match.
                wallpapers = places;
                showNextWallpaper(wallBitmap != null);
                if (!isFinishing() && hasWindowFocus()) startWallpaperRotation();
            });
        }, "place-wallpapers").start();
    }

    /** Clears the TV's app caches when due, once Home has sat idle on screen for a while. */
    private final Runnable cacheClean = () -> {
        if (hasWindowFocus() && config != null) CacheCleaner.maybeRun(this, config.cacheCleanDays);
    };

    /** Whenever Home comes back: picks the rotation up where the interval left it. */
    private void startWallpaperRotation() {
        refreshPlacesIfDue();
        handler.removeCallbacks(wallTick);
        applyWallpaperFit();
        // Pictures added or removed in the picker since Home last looked.
        if (!placesMode && config != null) {
            List<File> now = Config.load(this).wallpapers;
            if (!now.equals(wallpapers)) {
                wallpapers = now;
                if (wallName != null && !wallpapers.isEmpty()) {
                    boolean gone = true;
                    for (File f : wallpapers) if (f.getName().equals(wallName)) gone = false;
                    if (gone) showNextWallpaper(true);
                }
            }
        }
        String fixed = getSharedPreferences("wallpaper", MODE_PRIVATE).getString(WallpaperPickerActivity.FIXED, "");
        if (!fixed.isEmpty()) {
            if (!fixed.equals(wallName) && !wallpapers.isEmpty()) showNextWallpaper(true);
            return;
        }
        long interval = wallInterval();
        // "Every time you come back from an app": a new picture on each return to Home.
        if (interval == WallpaperPickerActivity.EVERY_RETURN && returning && wallpapers.size() >= 2) showNextWallpaper(true);
        returning = false;
        if (wallpapers.size() < 2 || interval <= 0) return;
        long waited = android.os.SystemClock.elapsedRealtime() - wallShownAt;
        // A picture that fell due while you were in an app waits a few seconds: decoding, blurring
        // and fading it the moment you come back competed with Home for the TV's slow CPU.
        handler.postDelayed(wallTick, Math.max(RETURN_SETTLE_MS, interval - waited));
    }

    /**
     * Home pressed while Home is already in front: singleTask delivers it here, then onResume runs.
     * Back to the top with the first dock tile focused, like the stock launcher. Back from an app
     * never comes this way, so returning to the app you left from is unchanged (#118).
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        HomeDiagnostics.activity(this, "onNewIntent", intent);
        if (eplDialog != null) eplDialog.dismiss();
        if (intent != null && intent.getBooleanExtra(CHOOSE_AZAN, false)) {
            // The Waktu Solat tile in All apps.
            handler.postDelayed(() -> {
                if (!isDestroyed()) choosePrayerSound();
            }, 350);
            return;
        }
        if (intent != null && intent.getBooleanExtra(CLEAN_NOW, false)) {
            // The Clear cache tile in All apps.
            Toast.makeText(this, getString(R.string.clean_running), Toast.LENGTH_SHORT).show();
            CacheCleaner.runNow(this, said -> handler.post(() -> Toast.makeText(this, said, Toast.LENGTH_LONG).show()));
            return;
        }
        if (intent != null && intent.getBooleanExtra(SHOW_EPL, false)) {
            // The Premier League tile in All apps: the table, once Home is back in front.
            if (EplScores.matches.isEmpty()) EplScores.fetchedAt = 0;
            handler.postDelayed(() -> {
                if (!isDestroyed()) showEplTable();
            }, 350);
            return;
        }
        if (intent == null || !intent.hasCategory(Intent.CATEGORY_HOME)) return;
        // Home pressed on Home goes back to the first app. Home pressed in another app comes back to
        // the app you left from: resetting then made the selection run from it to the first app.
        if (hiddenSinceResume) return;
        grid.scrollToPosition(0);
        lastFocused = 0;
        lastFocusedKey = adapter.getItemCount() > 0 ? adapter.keyAt(0) : null;
    }

    /**
     * Home went behind another app since it was last resumed. A Home press that brings it back
     * arrives before onResume, so this tells it apart from a Home press on Home itself.
     */
    private boolean hiddenSinceResume;
    /** Extra on the intent from All apps' Premier League tile: open the league table. */
    static final String SHOW_EPL = "show_epl";
    /** Extra on the intent from All apps' Clear cache tile. */
    static final String CLEAN_NOW = "clean_now";
    /** Extra on the intent from the Waktu Solat page's azan button: choose the azan sound. */
    static final String CHOOSE_AZAN = "choose_azan";
    /** The phone upload page (wallpapers, azan audio), served while Home is in front. */
    private WallpaperImport.Server uploadServer;

    @Override
    protected void onStop() {
        hiddenSinceResume = true;
        handler.removeCallbacks(cacheClean);
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        HomeDiagnostics.activity(this, "onResume", getIntent());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(packageReceiver, packageFilter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(packageReceiver, packageFilter);
        }
        tilesReady = false;
        promptsPaused = false;
        resuming = true;
        reloadApps(true);
        updateShamsiDate();
        updatePrayerTimes();
        updateEplCard();
        // The words may have been changed on the Wisdom page.
        if (heroQuotes && wallName != null) setPlace(new File(wallName));
        showWisdom();
        refreshEplIfDue();
        refreshWeatherIfDue();
        scheduleHourGlow();
        spinCorners(true);
        if (uploadServer != null) uploadServer.close();
        uploadServer = new WallpaperImport.Server(this, () -> handler.post(() -> {
            if (isDestroyed()) return;
            android.widget.Toast.makeText(this, getString(R.string.received_phone), android.widget.Toast.LENGTH_SHORT).show();
            if (!placesMode && config != null) wallpapers = Config.load(this).wallpapers;
        }));
        uploadServer.start();
        if (tileGlow && glider != null && ambientColor != -1) glider.setGlowColor(ambientColor);
        updateWifiLabel();
        updateInputsButton();
        startWallpaperRotation();
        handler.postDelayed(minuteTick, MINUTE_MS);
        handler.removeCallbacks(cacheClean);
        handler.postDelayed(cacheClean, CacheCleaner.IDLE_MS);
        hiddenSinceResume = false;
    }

    /** Rebuilds the tiles (and the module cards), then puts focus back where it was. */
    private void reloadApps(boolean animate) {
        // Capture the app before rebinding can move focus and overwrite its key.
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        final boolean restoreFocus = animate || grid.hasFocus();
        long started = android.os.SystemClock.uptimeMillis();
        boolean rebuilt = reloadTiles();
        // Nothing installed or removed: Home comes back exactly as it was left, with no rebuild and
        // no shelf, glow or selection fading back in.
        if (restoreFocus && rebuilt) {
            glassShown = false;
            glider.hideNow();
            heroShown = false;
            heroShelf.setAlpha(0f);
            ambientShown = false;
            ambient.setAlpha(0f);
            syncGlassGlow();
        }
        android.util.Log.i("TvHome.Perf", "tiles " + (rebuilt ? "rebuilt" : "unchanged") + " in "
                + (android.os.SystemClock.uptimeMillis() - started) + " ms");
        // The tiles rise into place on the first launch only. Replayed on every return from an app,
        // it showed the glass tray empty for a moment and held Home back while the tiles arrived.
        if (animate && !reducedMotion && !tilesAnimatedOnce) grid.scheduleLayoutAnimation();
        if (animate) tilesAnimatedOnce = true;

        if (restoreFocus) {
            // Follow the same app, or the nearest remaining slot when it was removed.
            restoreFocus();
        } else {
            // A package change must not steal focus from a top button or the Inputs popup.
            int pos = adapter.positionOf(returnKey);
            lastFocused = Math.max(0, Math.min(pos >= 0 ? pos : returnSlot, adapter.getItemCount() - 1));
            lastFocusedKey = adapter.keyAt(lastFocused);
            if (pos < 0) hideHero();
            // A newly installed app may have brought a card with it.
            tilesReady = true;
            showCardPrompt();
        }
    }

    /**
     * Rebuilds the tiles; the module cards the owner chose to show stand in for their app's tile.
     * Every step into the card code runs under CardFeature: if it fails, the tiles are the ordinary
     * ones and cards stay off for this process.
     */
    private boolean reloadTiles() {
        // Read these before reload(): rebinding can move focus to the first tile and overwrite them.
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        final List<Cards.Card> none = Collections.emptyList();
        cards = moduleCards ? CardFeature.run(() -> Cards.discover(this, hiddenApps, Cards.load(Cards.storeFile(this))), none) : none;
        boolean rebuilt = adapter.reload(cards.isEmpty() ? none : CardFeature.run(() -> Cards.shown(cards), none));
        lastFocusedKey = returnKey;
        lastFocused = returnSlot;
        return rebuilt;
    }

    /**
     * Return to the app we left from, or the same slot if that app is gone. Always refocus: after
     * reload() the view that holds focus may no longer show the app it had.
     */
    private void restoreFocus() {
        final String returnKey = lastFocusedKey;
        final int returnSlot = lastFocused;
        grid.post(() -> {
            int pos = adapter.positionOf(returnKey);
            focusTile(pos >= 0 ? pos : returnSlot);
            tilesReady = true;
            resuming = false;
            View now = getCurrentFocus();
            if (now != null && isTopPill(now)) moveTopHighlight(now);
            showCardPrompt();
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        HomeDiagnostics.activity(this, "onPause", getIntent());
        unregisterReceiver(packageReceiver);
        handler.removeCallbacks(minuteTick);
        handler.removeCallbacks(wallTick);
        handler.removeCallbacks(hourGlowTick);
        handler.removeCallbacks(ambientIdle);
        handler.removeCallbacks(blinkStart);
        if (prayerClocks[0] != null) stopBlink();
        if (glider != null) glider.setGlowColor(0);   // no breathing behind another app
        spinCorners(false);
        if (uploadServer != null) uploadServer.close();
        uploadServer = null;
        returning = true;
        // Nothing of ours stays on screen over another app; an undecided prompt simply returns next time.
        if (cardPrompt != null) cardPrompt.dismiss();
        if (openPopup != null) openPopup.dismiss();
        if (eplDialog != null) eplDialog.dismiss();
        closeMenu(false);
        finishMove();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) showCardPrompt();
    }

    /**
     * Android is short of memory while Home sits behind another app: give back what can be remade,
     * so the launcher is less likely to be reclaimed and Home comes back without starting over.
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level >= TRIM_MEMORY_BACKGROUND) Backdrop.trim();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        wallDecoder.shutdownNow();
        if (voiceGlow != null) voiceGlow.stop();
        if (ticks != null) ticks.release();
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (tim != null) tim.unregisterCallback(inputCallback);
        if (cm != null) {
            cm.unregisterNetworkCallback(networkCallback);
            cm.unregisterNetworkCallback(vpnCallback);
        }
    }

    @Override
    public void onBackPressed() {
        // Home screen: Back goes nowhere.
    }

    // ---- glass highlight -------------------------------------------------------------------------

    /** Reused by every focus move. */
    private final Rect tileRect = new Rect(), showRect = new Rect();

    @Override
    public void onTileFocused(int position, View card) {
        // Only when the selection moves to another app, not when Home puts it back on return.
        if (!resuming && position != tickedAt) playTick();
        tickedAt = position;
        if (dockMagnify) magnifyDock(position, card);
        // The other apps keep their own colours: only the selected one changes.
        lastFocused = position;
        lastFocusedKey = adapter.keyAt(position);
        // Tile rect in root coordinates, using layout positions (ignores the scale animation).
        Rect r = tileRect;
        r.set(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, r);
        float pad = 6f * density;        // matches GlassView's glow inset
        float w = card.getWidth() * AppAdapter.FOCUS_SCALE + 2 * pad;
        float h = card.getHeight() * AppAdapter.FOCUS_SCALE + 2 * pad;
        float x = r.exactCenterX() - w / 2f;
        float y = r.exactCenterY() - h / 2f;

        ViewGroup.LayoutParams lp = glass.getLayoutParams();
        if (lp.width != (int) w || lp.height != (int) h) {
            lp.width = (int) w;
            lp.height = (int) h;
            glass.setLayoutParams(lp);
            // The rim is drawn inset by pad, right on the zoomed tile, so its corners use the tile's radius as is.
            glass.setRadius(AppAdapter.cornerRadius(card.getWidth(), density) * AppAdapter.FOCUS_SCALE);
        }
        glassTargetX = x;
        glassTargetY = y;
        // Exactly the zoomed card, behind it: at rest the app hides it completely, so it never lies
        // over an app and only shows while it travels between places.
        float cardRadius = AppAdapter.cornerRadius(card.getWidth(), density) * AppAdapter.FOCUS_SCALE;
        if (gliderOnPill) {
            // Down from the top menu it travels dark, and turns to glass once it lands, unseen behind
            // the app.
            glider.moveTo(x + pad, y + pad, x + w - pad, y + h - pad, cardRadius);
            glider.onArrive(() -> {
                if (!gliderOnPill) glider.setStyle(Glider.GLASS);
            });
        } else {
            // From app to app it is a pane of glass gliding behind them.
            glider.setStyle(Glider.GLASS);
            glider.moveTo(x + pad, y + pad, x + w - pad, y + h - pad, cardRadius);
        }
        gliderOnPill = false;
        glassShown = true;
        View item = (View) card.getParent();
        if (position < COLUMNS) {
            // Dock: park it at the bottom of the screen and show the top shelf for this app.
            int decoratedTop = item.getTop() - (int) (6 * density);
            int dy = decoratedTop - grid.getPaddingTop();
            // A 1px mismatch used to start a scroll on every dock move. Only scroll when the row is
            // actually off; reduced motion does it in one jump so it does not fight the highlight.
            boolean scroll = Math.abs(dy) > (int) (8 * density);
            if (scroll) {
                if (reducedMotion) grid.scrollBy(0, dy);
                else grid.smoothScrollBy(0, dy);
            }
            showHero(position, scroll ? r.top - dy : r.top);
            fadeWidgets(true);
        } else {
            // The rows below scroll up over where the widgets sit: they step aside until the dock is back.
            fadeWidgets(false);
            // Below the dock there is no shelf, but the glow still takes this app's colour, so every
            // tile lights the room the same way the dock does.
            hideShelf();
            showAmbient(adapter.ambientColorAt(position));
            // Scroll just enough so the whole glass (not only the tile) is visible.
            showRect.set(0, -(int) pad * 2, item.getWidth(), item.getHeight() + (int) pad);
            grid.requestChildRectangleOnScreen(item, showRect, reducedMotion);
        }
    }

    // ---- top shelf -------------------------------------------------------------------------------

    private void showHero(int position, float dockTopInRoot) {
        // The place the picture is of, when there is one; the app's own name is the fallback for
        // the user's own wallpapers, which carry no title.
        if (heroQuotes) {
            // The Wisdom widget stays put whichever app is selected; only the glow follows the app.
            showWisdom();
            showAmbient(adapter.ambientColorAt(position));
            return;
        }
        final String label = placeName != null ? placeName : adapter.labelAt(position);
        final String desc = placeName != null ? placeText : "";
        final String where = placeName != null ? placeCountry : "";
        int color = adapter.ambientColorAt(position);
        heroBottom = dockTopInRoot - HERO_GAP_DP * density;
        float y = heroBottom - heroShelf.getHeight();
        boolean changed = !label.contentEquals(heroName.getText());

        if (reducedMotion) {
            // Snap the shelf instead of sliding and fading it: each of those frames repaints the
            // full-screen panel, and this switch exists for the GPUs that cannot afford it.
            heroShelf.animate().cancel();
            setHeroText(label, where, desc);
            heroShelf.setY(y);
            heroShelf.setTranslationX(0f);
            heroShelf.setAlpha(1f);
            showAmbient(color);
            heroShown = true;
            return;
        }

        if (!heroShown) {
            setHeroText(label, where, desc);
            heroShelf.setY(y);
            heroShelf.setTranslationX(0f);
            // Coming back to Home, the words are simply there with the picture: faded in after the
            // first frame, they arrived a beat behind everything else.
            heroShelf.animate().cancel();
            if (firstFrameHeld || resuming) heroShelf.setAlpha(1f);
            else heroShelf.animate().alpha(1f).setDuration(260).start();
            showAmbient(color);
            heroShown = true;
        } else if (changed) {
            heroShelf.animate().alpha(0f).translationX(-12 * density).setDuration(120).withEndAction(() -> {
                setHeroText(label, where, desc);
                heroShelf.setY(y);
                heroShelf.setTranslationX(12 * density);
                heroShelf.animate().alpha(1f).translationX(0f).setDuration(200).start();
            }).start();
            showAmbient(color);
        } else {
            // Same words, different app: every dock app shares the place's name, so the colour has
            // to follow focus on its own rather than ride along with a change of text.
            heroShelf.setY(y);
            showAmbient(color);
        }
    }

    /**
     * Lines the top-right buttons up with the dock: the Settings circle's right edge sits on the
     * right edge of the last dock card, and Inputs follows on its left. Card width follows the
     * column count and the screen, so the edge is measured rather than written down.
     */
    private void alignTopButtons() {
        // This runs on every layout pass, which during a grid scroll is every frame, so it keeps
        // its views and rectangles rather than looking them up or allocating each time.
        if (topRow == null) {
            topRow = findViewById(R.id.topButtons);
            topSettings = findViewById(R.id.btnSettings);
        }
        if (topRow == null || topSettings.getWidth() == 0) return;
        RecyclerView.ViewHolder last = grid.findViewHolderForAdapterPosition(COLUMNS - 1);
        if (last == null) return;
        View card = last.itemView.findViewById(R.id.card);
        if (card == null || card.getWidth() == 0) return;
        // Layout positions, not drawn ones: a focused card is scaled up, and the row's own shift
        // must not be counted twice.
        alignCard.set(0, 0, card.getWidth(), card.getHeight());
        root.offsetDescendantRectToMyCoords(card, alignCard);
        alignButton.set(0, 0, topSettings.getWidth(), topSettings.getHeight());
        root.offsetDescendantRectToMyCoords(topSettings, alignButton);
        float shift = alignCard.right - alignButton.right;
        if (Math.abs(topRow.getTranslationX() - shift) >= 1f) {
            topRow.setTranslationX(shift);
            positionEplCard();
        }
    }

    /**
     * The one focus shape (Glider) goes behind everything it marks; the pills' glass makes way for it
     * as it arrives, and the dock tray paints it over its own glass.
     */
    private void setUpGlider() {
        glider = new Glider(this, reducedMotion);
        // Behind the shelf's text, the pills and the tiles, so it never draws over any of them.
        root.addView(glider, root.indexOfChild(heroShelf), new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        // The glider replaces the glass selection outright.
        glass.setVisibility(View.GONE);
    }

    private boolean isTopPill(View v) {
        int id = v.getId();
        return id == R.id.btnInputs || id == R.id.btnWifi || id == R.id.btnWallpaper || id == R.id.btnSettings;
    }

    /** Raises the dock apps either side of the selected one and lowers those raised before. */
    private void magnifyDock(int position, View card) {
        List<View> next = new ArrayList<>();
        if (position < COLUMNS) {
            for (int p = position - 1; p <= position + 1; p += 2) {
                if (p < 0 || p >= COLUMNS) continue;
                RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(p);
                if (vh instanceof AppAdapter.Holder) next.add(((AppAdapter.Holder) vh).card);
            }
        }
        for (View v : raised) {
            if (next.contains(v) || v == card || v.isFocused()) continue;
            v.animate().scaleX(1f).scaleY(1f).setDuration(200).withLayer().start();
        }
        for (View v : next) {
            if (v.isFocused()) continue;
            v.animate().scaleX(AppAdapter.NEIGHBOUR_SCALE).scaleY(AppAdapter.NEIGHBOUR_SCALE)
                    .setDuration(reducedMotion ? 120 : 260).withLayer().start();
        }
        raised.clear();
        raised.addAll(next);
    }

    /** Sends the focus shape onto a pill: from a tile it glides up and narrows into the pill. */
    private void moveTopHighlight(View pill) {
        if (dockMagnify) magnifyDock(Integer.MAX_VALUE, null);
        if (resuming) return;
        pill.getLocationInWindow(pillAt);
        glider.getLocationInWindow(rootAt);
        float l = pillAt[0] - rootAt[0], t = pillAt[1] - rootAt[1];
        // The pills do not scroll with the grid, so the shape stops following the grid's scroll.
        glassShown = false;
        // Up to the top menu it is dark at once, and travels that way.
        gliderOnPill = true;
        glider.setStyle(Glider.DARK);
        glider.moveTo(l, t, l + pill.getWidth(), t + pill.getHeight(), pill.getHeight() / 2f);
    }

    /** The shelf's two lines. An empty description takes no room, so the title keeps its place. */
    private void setHeroText(String title, String where, String desc) {
        heroName.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, titleSizeSp(title));
        heroName.setText(title);
        heroWhere.setText(where);
        heroWhere.setVisibility(where.isEmpty() ? View.GONE : View.VISIBLE);
        // The description sits further down under a country line; with none, close under the name.
        ViewGroup.MarginLayoutParams descLp = (ViewGroup.MarginLayoutParams) heroDesc.getLayoutParams();
        int top = Math.round((where.isEmpty() ? 6f : 9.5f) * density);
        if (descLp.topMargin != top) {
            descLp.topMargin = top;
            heroDesc.setLayoutParams(descLp);
        }
        heroDesc.setText(desc);
        heroDesc.setVisibility(desc.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /**
     * Shouting titles are calmed down: "THE RIBBON HERO" becomes "The Ribbon Hero". A title that
     * already has lower case is left exactly as its studio wrote it, so "LEGO Star Wars" and
     * "WALL·E" keep their capitals instead of being flattened to "Lego" and "Wall·e".
     */
    private static String titleCase(String title) {
        if (title == null || title.isEmpty()) return title;
        for (int i = 0; i < title.length(); i++) {
            if (Character.isLowerCase(title.charAt(i))) return title;
        }
        StringBuilder out = new StringBuilder(title.length());
        boolean start = true;
        for (int i = 0; i < title.length(); i++) {
            char c = title.charAt(i);
            out.append(start ? Character.toUpperCase(c) : Character.toLowerCase(c));
            // A new word begins after a space or any of the marks a title tends to use.
            start = c == ' ' || c == '-' || c == ':' || c == '.' || c == '\'' || c == '(' || c == '/';
        }
        return out.toString();
    }

    /**
     * The largest size at which the name still fits on one line, up to its share of the width.
     * Names run longer than app names ("Wroclaw Market Square"), and a name cut short tells the
     * viewer nothing, so a long one is set smaller rather than clipped.
     */
    private float titleSizeSp(String title) {
        if (title == null || title.isEmpty() || heroNameMaxWidth <= 0) return HERO_TITLE_MAX_SP;
        android.text.TextPaint paint = new android.text.TextPaint(heroName.getPaint());
        float scaled = getResources().getDisplayMetrics().scaledDensity;
        for (float sp = HERO_TITLE_MAX_SP; sp > HERO_TITLE_MIN_SP; sp -= 2f) {
            paint.setTextSize(sp * scaled);
            if (paint.measureText(title) <= heroNameMaxWidth) return sp;
        }
        return HERO_TITLE_MIN_SP;
    }

    /**
     * Follows the rotation: the shelf names whichever place is on screen. Called after the picture
     * changes, so the words and the picture never disagree.
     */
    private void setPlace(File picture) {
        String[] about = placesMode ? PlaceWallpapers.describe(picture) : null;
        if (heroQuotes && picture != null) {
            String[] q = QuoteStore.forPicture(this, picture.getName(), java.util.Calendar.getInstance(), quoteShift);
            if (q != null) about = new String[]{q[0], q[1], q[2]};
        }
        String title = about != null ? (heroQuotes ? about[0] : titleCase(about[0])) : null;
        String overview = about != null ? about[1] : "";
        String country = about != null && about.length > 2 ? about[2] : "";
        if (title == null ? placeName == null : title.equals(placeName)) return;
        placeName = title;
        placeText = overview;
        placeCountry = country;
        // Words for the Wisdom widget with none on screen yet, or none left to show (every kind switched off).
        if (heroQuotes && (title == null || !heroShown)) {
            showWisdom();
            if (title == null) return;
        }
        // When the shelf is up, the words fade out with the old picture and back in with the new
        // one, inside the picture's own cross-fade: swapped at once, the next place's name sat over
        // the last one's picture while it faded. The layout listener puts the shelf back where it
        // belongs once the new lines have been measured.
        if (heroShown && title != null) {
            heroShelf.animate().cancel();
            heroShelf.animate().alpha(0f).setDuration(reducedMotion ? 100 : 250).withEndAction(() -> {
                setHeroText(title, country, overview);
                if (heroShown) heroShelf.animate().alpha(heroQuotes && !widgetsIn ? 0f : 1f).setDuration(reducedMotion ? 120 : 400).start();
            }).start();
        }
    }

    /**
     * The glow used to be a full-screen radial GradientDrawable. On Android 10 that drawable is
     * drawn on the CPU, so fading it for half a second redrew 1920×1080 every frame and the
     * highlight looked like it was running at a few frames per second. A small bitmap stretched
     * over the screen is one texture; changing apps only uploads it once.
     */
    private void setAmbient(int color) {
        setAmbient(color, false);
    }

    private FadeTopFrame gridWrap;

    /** Turns the top fade on while any row reaches into its band, with room for a focused tile's zoom. */
    private void updateTopFade() {
        if (gridWrap == null) return;
        float band = 56 * density + 24 * density;
        boolean inBand = false;
        for (int i = 0; i < grid.getChildCount() && !inBand; i++) {
            View child = grid.getChildAt(i);
            inBand = grid.getTop() + child.getTop() + child.getTranslationY() < band;
        }
        gridWrap.setFadeStrength(inBand ? 1f : 0f);
    }

    /**
     * The dock row sits towards the bottom of the screen, leaving room for rows_below_dock grid rows
     * under it; the rest of the grid stays below until you press Down. Sets the grid's top padding
     * for that, and says whether it was already right.
     */
    private boolean parkDock() {
        int h = grid.getHeight();
        if (h <= 0) return false;
        // Item height follows the tile size, which follows the column count (AppAdapter.tileWidthPx).
        int inner = grid.getWidth() - grid.getPaddingLeft() - grid.getPaddingRight();
        int itemH = AppAdapter.itemHeightPx(AppAdapter.tileHeightPx(AppAdapter.tileWidthPx(inner, COLUMNS, density)), density);
        int dockRow = itemH + (int) ((6 + 12) * density);   // item height plus DockDecoration offsets
        int want = Math.max(0, h - dockRow - itemH * rowsBelowDockSetting - (int) (30 * density));
        if (grid.getPaddingTop() == want) return true;
        grid.setPadding(grid.getPaddingLeft(), want, grid.getPaddingRight(), grid.getPaddingBottom());
        return false;
    }

    /**
     * Home's first frame waits until Home is whole: tiles bound and laid out, the dock parked at the
     * bottom and the wallpaper in. Drawn as soon as the window was ready, it showed the glass tray
     * floating empty near the top, then the tiles, then the dock dropping into place. This matters
     * most after a long stay in another app, when Android has reclaimed the launcher and Home starts
     * from nothing. The wait is capped, so Home can never hang on a slow picture.
     */
    private void holdFirstFrame() {
        final long start = android.os.SystemClock.uptimeMillis();
        firstFrameHeld = true;
        final boolean expectsWallpaper = !wallpapers.isEmpty() || (config.wallpaper != null && !placesMode);
        root.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                // Parked here rather than by the layout listener alone: while frames are held, the
                // second layout pass that listener relies on does not always come.
                boolean parked = parkDock();
                // The place's name and lines come in the same frame as the picture they describe,
                // whenever focus lands in the dock, where the shelf shows.
                boolean whole = adapter.getItemCount() > 0 && grid.getChildCount() > 0 && parked
                        && (wallBitmap != null || !expectsWallpaper) && (heroShown || lastFocused >= COLUMNS);
                if (!whole && android.os.SystemClock.uptimeMillis() - start < FIRST_FRAME_WAIT_MS) {
                    return false;   // skip this frame; the next traversal asks again
                }
                root.getViewTreeObserver().removeOnPreDrawListener(this);
                firstFrameHeld = false;
                android.util.Log.i("TvHome.Perf", "first frame after " + (android.os.SystemClock.uptimeMillis() - start)
                        + " ms" + (whole ? "" : " (gave up waiting: tiles=" + adapter.getItemCount()
                        + " laidOut=" + grid.getChildCount() + " dockParked=" + parked
                        + " wallpaper=" + (wallBitmap != null) + ")"));
                return true;
            }
        });
    }

    /** Tells the glass the glow's colour and strength, so what shows through matches around it. */
    private void syncGlassGlow() {
        Backdrop.setGlow(ambientShape(), glowFrom, ambientColor, glowMix, ambient.getAlpha());
    }

    /** Runs the glass through the same colour blend as the room's TransitionDrawable. */
    private void blendGlassGlow(int from, long ms) {
        if (glowBlend != null) glowBlend.cancel();
        glowFrom = from;
        glowMix = 0f;
        glowBlend = android.animation.ValueAnimator.ofFloat(0f, 1f);
        glowBlend.setDuration(ms);
        glowBlend.addUpdateListener(a -> {
            glowMix = (float) a.getAnimatedValue();
            syncGlassGlow();
        });
        glowBlend.start();
    }

    /** The glow's shape in white, drawn on first use and kept: a tint gives it each app's colour. */
    private Bitmap ambientShape() {
        if (ambientShape != null) return ambientShape;
        int w = 480;
        int h = 270;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setShader(new RadialGradient(
                w * 0.32f, h * 0.42f, w * 0.62f,
                new int[]{0x5CFFFFFF, 0x26FFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.45f, 1f},
                Shader.TileMode.CLAMP));
        new Canvas(bmp).drawRect(0, 0, w, h, paint);
        ambientShape = bmp;
        return bmp;
    }

    /** With crossFade, the old glow fades into the new one (two small textures, one blend). */
    private void setAmbient(int color, boolean crossFade) {
        if (color == ambientColor && ambient.getBackground() != null) return;
        android.graphics.drawable.Drawable old = ambient.getBackground();
        if (old instanceof android.graphics.drawable.TransitionDrawable) {
            android.graphics.drawable.TransitionDrawable t = (android.graphics.drawable.TransitionDrawable) old;
            old = t.getDrawable(t.getNumberOfLayers() - 1);   // a blend still running: start from where it was heading
        }
        int previous = ambientColor;
        ambientColor = color;
        if (tileGlow && glider != null) glider.setGlowColor(color);
        // Every app's glow is the same shape in a different colour, so the shape is drawn once and
        // each app only tints it. Drawing a fresh bitmap per move cost a CPU fill, half a megabyte
        // and a texture upload every time focus moved, which is what made moving around feel heavy
        // once every tile, not only the dock, tinted the room.
        android.graphics.drawable.Drawable drawable;
        if (cornerGlow) {
            drawable = new CornerGlow(color);
        } else {
            BitmapDrawable room = new BitmapDrawable(getResources(), ambientShape());
            room.setGravity(Gravity.FILL);
            room.setColorFilter(new android.graphics.PorterDuffColorFilter(
                    0xFF000000 | color, android.graphics.PorterDuff.Mode.SRC_IN));
            drawable = room;
        }
        if (crossFade && old != null) {
            android.graphics.drawable.TransitionDrawable t = new android.graphics.drawable.TransitionDrawable(
                    new android.graphics.drawable.Drawable[]{old, drawable});
            t.setCrossFadeEnabled(true);
            ambient.setBackground(t);
            t.startTransition(450);
            // The glass blends with the room, over the same 450 ms, instead of jumping ahead of it.
            blendGlassGlow(previous, 450);
        } else {
            ambient.setBackground(drawable);
            if (glowBlend != null) glowBlend.cancel();
            glowFrom = color;
            glowMix = 1f;
            syncGlassGlow();
        }
    }

    /** Focus has left the apps altogether: both the shelf and the glow go. */
    private void hideHero() {
        hideShelf();
        if (!ambientShown) return;
        ambientShown = false;
        ambient.animate().alpha(0f).setDuration(reducedMotion ? 140 : 300)
                .setUpdateListener(a -> syncGlassGlow()).start();
    }

    private void hideShelf() {
        if (heroQuotes) return;   // the Wisdom widget is not tied to the selected app
        if (!heroShown) return;
        heroShown = false;
        heroShelf.animate().alpha(0f).setDuration(reducedMotion ? 100 : 160).start();
    }

    /**
     * Lights the room in this app's colour. From one app to the next it blends straight across,
     * with no dip to plain in between, whether the move is along the dock, down into the grid or
     * back up again.
     */
    private void showAmbient(int color) {
        if (!ambientShown) {
            setAmbient(color);
            // The edge glow comes in quickly with each new app; the room tint more slowly.
            ambient.animate().alpha(1f).setDuration(reducedMotion ? 160 : cornerGlow ? 400 : 600)
                    .setUpdateListener(a -> syncGlassGlow()).start();
            ambientShown = true;
        } else if (color != ambientColor) {
            setAmbient(color, !reducedMotion);
        }
        // Left on one app for 30 seconds, the edge glow fades away; the next move brings it back.
        if (cornerGlow) {
            handler.removeCallbacks(ambientIdle);
            handler.postDelayed(ambientIdle, 30_000L);
        }
    }

    private final Runnable ambientIdle = () -> {
        if (!ambientShown) return;
        ambientShown = false;
        ambient.animate().alpha(0f).setDuration(1500).setUpdateListener(a -> syncGlassGlow()).start();
    };

    private void hideGlass() {
        glassShown = false;
    }

    // ---- navigation ------------------------------------------------------------------------------

    // ---- arranging the tiles ---------------------------------------------------------------------

    /** The tile being moved (adapter position), or -1; its card wiggles while it is. */
    private int movingPos = -1;
    private View movingCard;
    private android.animation.ObjectAnimator wiggle;
    /** A key whose release is swallowed: the OK or Back that put a moved tile down must not also open it. */
    private int swallowRelease = -1;
    /**
     * Slides the tiles into their new places while an app is moved. The grid has no item animations
     * otherwise: they would replay on every rebuild and cost frames while scrolling.
     */
    private androidx.recyclerview.widget.DefaultItemAnimator moveSlide;

    /** Holding OK on an app: a glass menu to move it, or to open Android's page for it. */
    @Override
    public boolean onTileHeld(int position, View card) {
        if (openMenu != null || movingPos >= 0) return true;
        FrameLayout panel = (FrameLayout) getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        final String key = adapter.keyAt(position);
        if (adapter.canMove(position)) {
            addInputRow(rows, () -> {
                // No selection gliding back from the menu: it is simply gone, and the app wiggles.
                glider.hideNow();
                gliderOnPill = false;
                closeMenu(true);
            }, R.drawable.ic_move, getString(R.string.tile_move), -1, () -> startMove(key));
        }
        final ComponentName comp = adapter.componentAt(position);
        if (adapter.isApp(position) && comp != null) {
            addInputRow(rows, () -> closeMenu(false), R.drawable.ic_remove_home, getString(R.string.remove_home), -1, () -> {
                AppAdapter.removeFromHome(this, comp);
                reloadApps(false);
            });
            if (AppAdapter.removable(this, comp.getPackageName())) {
                addInputRow(rows, () -> closeMenu(false), R.drawable.ic_delete, getString(R.string.uninstall), -1,
                        () -> AppAdapter.uninstall(this, comp.getPackageName()));
            }
        }
        addInputRow(rows, () -> closeMenu(false), R.drawable.ic_info, getString(R.string.tile_app_info), -1,
                () -> adapter.openAppInfo(adapter.positionOf(key)));
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int width = Math.max((int) (190 * density), panel.getMeasuredWidth());
        // Under the app when there is room; over it in the dock, which sits near the bottom.
        panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
        card.getLocationInWindow(menuAt);
        root.getLocationInWindow(menuGliderAt);
        int below = menuAt[1] - menuGliderAt[1] + card.getHeight() + (int) (18 * density) + panel.getMeasuredHeight();
        showMenu(panel, rows, card, width, below > root.getHeight() - (int) (24 * density));
        return true;
    }

    /** Picks the app up: it wiggles, the arrows carry it, and OK or Back puts it down. */
    private void startMove(String key) {
        int pos = adapter.positionOf(key);
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        if (vh == null || !adapter.canMove(pos)) return;
        movingPos = pos;
        movingCard = vh.itemView.findViewById(R.id.card);
        movingCard.requestFocus();
        glider.setPaneHidden(true);
        if (moveSlide == null) {
            moveSlide = new androidx.recyclerview.widget.DefaultItemAnimator();
            moveSlide.setSupportsChangeAnimations(false);
            moveSlide.setMoveDuration(reducedMotion ? 140 : 220);
        }
        grid.setItemAnimator(moveSlide);
        float tilt = reducedMotion ? 0.8f : 1.4f;
        wiggle = android.animation.ObjectAnimator.ofFloat(movingCard, View.ROTATION, -tilt, tilt);
        wiggle.setDuration(reducedMotion ? 260 : 140);
        wiggle.setRepeatMode(android.animation.ValueAnimator.REVERSE);
        wiggle.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        wiggle.start();
    }

    /** Puts the moving app down where it is; the order was kept as it moved. */
    private void finishMove() {
        if (movingPos < 0) return;
        if (wiggle != null) wiggle.cancel();
        wiggle = null;
        if (movingCard != null) movingCard.animate().rotation(0f).setDuration(120).start();
        movingCard = null;
        movingPos = -1;
        glider.setPaneHidden(false);
        // Back to no item animations once the last slide has finished.
        RecyclerView.ItemAnimator slide = grid.getItemAnimator();
        if (slide != null) slide.isRunning(() -> {
            if (movingPos < 0 && grid.getItemAnimator() == slide) grid.setItemAnimator(null);
        });
    }

    /** Carries the moving app one step, the other tiles making room, and the selection follows it. */
    private void stepMove(int keyCode) {
        int last = adapter.lastMovable();
        int to = movingPos;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (movingPos % COLUMNS > 0) to = movingPos - 1;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (movingPos % COLUMNS < COLUMNS - 1) to = Math.min(movingPos + 1, last);
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (movingPos >= COLUMNS) to = movingPos - COLUMNS;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (movingPos / COLUMNS < last / COLUMNS) to = Math.min(movingPos + COLUMNS, last);
                break;
            default:
                return;
        }
        if (!adapter.move(movingPos, to)) return;
        movingPos = to;
        final View card = movingCard;
        final int pos = to;
        // Once the tiles have shifted, glide the selection (and the shelf, and the scroll) to it.
        grid.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                grid.removeOnLayoutChangeListener(this);
                grid.post(() -> {
                    if (movingPos == pos && card != null && card.isAttachedToWindow()) onTileFocused(pos, card);
                });
            }
        });
        grid.requestLayout();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int code = event.getKeyCode();
        if (code == swallowRelease && event.getAction() == KeyEvent.ACTION_UP) {
            swallowRelease = -1;
            return true;
        }
        if (movingPos < 0) return super.dispatchKeyEvent(event);
        boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_UP:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (down) stepMove(code);
                return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_BACK:
            case KeyEvent.KEYCODE_ESCAPE:
                if (down) {
                    finishMove();
                    swallowRelease = code;
                }
                return true;
            default:
                return super.dispatchKeyEvent(event);
        }
    }

    @Override
    public boolean onTileVerticalKey(int position, int keyCode) {
        int count = adapter.getItemCount();
        int target;
        // Horizontal: swallow the key at a row's ends so focus never wraps to the next row or
        // escapes up into the top bar. Mid-row moves fall through to the default focus search.
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
            return position % COLUMNS == 0;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            return position % COLUMNS == COLUMNS - 1 || position == count - 1;
        }
        if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            if (position < COLUMNS) {
                // Up from the dock: the widget above that side (Wisdom on the left, the score card on
                // the right), then the prayer times, the weather and the top bar.
                View near = position < COLUMNS / 2 ? heroShelf : eplCard, far = near == heroShelf ? eplCard : heroShelf;
                for (View w : new View[]{near, far}) {
                    if (w == heroShelf ? wisdomFocusable() : eplCard != null && eplCard.getVisibility() == View.VISIBLE) {
                        w.requestFocus();
                        return true;
                    }
                }
                if (prayerRow.getVisibility() == View.VISIBLE) {
                    prayerRow.requestFocus();
                    return true;
                }
                return false;
            }
            target = position - COLUMNS;
        } else {
            int lastRowStart = ((count - 1) / COLUMNS) * COLUMNS;
            if (position >= lastRowStart) return true;     // already on the last row: stay put
            target = Math.min(position + COLUMNS, count - 1);
        }
        if (target != position) {
            playTick();
            tickedAt = target;
        }
        focusTile(target);
        return true;
    }

    /** The tile {@link #focusWhenSettled} focuses once the grid stops scrolling to it. */
    private int pendingFocus;
    private final RecyclerView.OnScrollListener focusWhenSettled = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(@NonNull RecyclerView rv, int newState) {
            if (newState != RecyclerView.SCROLL_STATE_IDLE) return;
            rv.removeOnScrollListener(this);
            RecyclerView.ViewHolder h = rv.findViewHolderForAdapterPosition(pendingFocus);
            View target = h != null ? h.itemView.findViewById(R.id.card) : rv.getChildAt(0);
            if (target != null) target.requestFocus();
        }
    };

    private void focusTile(int position) {
        if (adapter.getItemCount() == 0) {
            (btnWifi.getVisibility() == View.VISIBLE ? btnWifi : findViewById(R.id.btnSettings)).requestFocus();
            return;
        }
        final int pos = Math.max(0, Math.min(position, adapter.getItemCount() - 1));
        RecyclerView.ViewHolder vh = grid.findViewHolderForAdapterPosition(pos);
        if (vh != null) {
            View card = vh.itemView.findViewById(R.id.card);
            // Already focused (e.g. back from an app): no focus change fires, so restore the glass by hand.
            if (card.isFocused()) onTileFocused(pos, card);
            else card.requestFocus();
            return;
        }
        // Not laid out yet: scroll there, then focus once the scroll settles. One listener at most,
        // aimed at the latest target, so asking again mid-scroll cannot pull focus back to an older one.
        pendingFocus = pos;
        grid.removeOnScrollListener(focusWhenSettled);
        grid.addOnScrollListener(focusWhenSettled);
        grid.smoothScrollToPosition(pos);
    }

    // ---- top bar ---------------------------------------------------------------------------------

    private void updateShamsiDate() {
        if (shamsi) shamsiDate.setText(Shamsi.today());
    }

    /** Six cells, a small name over its time, in the clock's typeface, then the Hijri date and the countdown. */
    private void buildPrayerRow() {
        android.graphics.Typeface outfit = getResources().getFont(R.font.outfit);
        for (int i = 0; i < PrayerTimes.NAMES.length; i++) {
            LinearLayout cell = new LinearLayout(this);
            cell.setOrientation(LinearLayout.VERTICAL);
            TextView name = new TextView(this);
            name.setTypeface(outfit);
            name.setFontVariationSettings("'wght' 600");
            name.setTextSize(11f);
            name.setLetterSpacing(0.14f);
            name.setAllCaps(true);
            name.setIncludeFontPadding(false);
            name.setText(PrayerTimes.NAMES[i]);
            // Room round the text for its glow when the row is selected; the row is pulled back by
            // the same amount so nothing moves.
            int g = Math.round(GLOW_ROOM * density);
            name.setPadding(g, g, g, 0);
            TextView time = new TextView(this);
            time.setTypeface(outfit);
            time.setFontVariationSettings("'wght' 450");
            time.setTextSize(22f);
            time.setIncludeFontPadding(false);
            time.setPadding(g, Math.round(4 * density), g, g);
            cell.addView(name);
            cell.addView(time);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(Math.round(30 * density) - 2 * g);
            prayerRow.addView(cell, lp);
            prayerLabels[i] = name;
            prayerClocks[i] = time;
        }
        prayerInfo = new TextView(this);
        prayerInfo.setTypeface(outfit);
        prayerInfo.setFontVariationSettings("'wght' 400");
        prayerInfo.setTextSize(13f);
        prayerInfo.setLetterSpacing(0.04f);
        prayerInfo.setTextColor(0xB3FFFFFF);
        // Two lines that sit level with the names and the times beside them.
        prayerInfo.setIncludeFontPadding(false);
        prayerInfo.setLineSpacing(10 * density, 1f);
        prayerInfo.setGravity(Gravity.TOP);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.gravity = Gravity.TOP;
        lp.topMargin = Math.round((2 + GLOW_ROOM) * density);
        lp.setMarginStart(Math.round(6 * density));
        prayerRow.addView(prayerInfo, lp);
        buildWeatherLine(outfit);
        findViewById(R.id.btnInputs).addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orr, ob) -> {
            if (r - l != orr - ol || l != ol) handler.post(this::fitPrayerRow);
        });
        if (!eplTeams.isEmpty()) buildEplCard(outfit);
        prayerRow.setTranslationX(-GLOW_ROOM * density);
        prayerRow.setTranslationY(-GLOW_ROOM * density);
        // The row itself can be selected: its times glow, and OK chooses the sound for prayer times.
        prayerRow.setFocusable(true);
        prayerRow.setOnFocusChangeListener((v, has) -> {
            widgetHint(v, has);
            if (!has) {
                shadePrayerText();
                return;
            }
            for (int i = 0; i < prayerClocks.length; i++) {
                prayerClocks[i].setShadowLayer(7 * density, 0, 0, 0xFFFFC46B);
                prayerLabels[i].setShadowLayer(6 * density, 0, 0, 0xE6FFC46B);
            }
            prayerInfo.setShadowLayer(6 * density, 0, 0, 0xE6FFC46B);
        });
        prayerInfo.setTextColor(0xE6FFFFFF);
        prayerInfo.setFontVariationSettings("'wght' 500");
        shadePrayerText();
        prayerRow.setOnClickListener(v -> choosePrayerSound());
        prayerRow.setOnKeyListener(holdForMenu(this::prayerMenu, (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (weatherLine != null && weatherLine.getVisibility() == View.VISIBLE) weatherLine.requestFocus();
                    else findViewById(R.id.btnInputs).requestFocus();
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    focusMiddle(wisdomFocusable() ? heroShelf : eplCard);
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (eplCard != null && eplCard.getVisibility() == View.VISIBLE) eplCard.requestFocus();
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    return true;
                default:
                    return false;
            }
        }));
    }

    /** The prayer row's scale last applied, so a fit that changes nothing does not relayout. */
    private float prayerScale = -1f;
    private int prayerGap = -1;

    /**
     * Keeps the prayer times, the Hijri date and the countdown between the clock's left edge and the
     * Inputs button's left edge: the gaps between the times close up first, then the text shrinks a
     * little if that is not enough.
     */
    private void fitPrayerRow() {
        View inputs = findViewById(R.id.btnInputs);
        if (inputs.getVisibility() != View.VISIBLE) inputs = findViewById(R.id.btnWifi);
        View clock = findViewById(R.id.clock);
        if (prayerInfo == null || inputs.getWidth() == 0 || clock.getWidth() == 0) return;
        int[] a = new int[2], b = new int[2];
        clock.getLocationOnScreen(a);
        inputs.getLocationOnScreen(b);
        // From the clock's left edge to the Inputs button's left edge, a small gap short of it.
        int target = b[0] - a[0] - Math.round(8 * density);
        int g = Math.round(GLOW_ROOM * density);
        int defaultGap = Math.round(30 * density) - 2 * g, minGap = Math.round(14 * density) - 2 * g;
        int infoStart = Math.round(6 * density);
        float sd = getResources().getDisplayMetrics().scaledDensity;
        android.text.TextPaint name = new android.text.TextPaint(prayerLabels[0].getPaint());
        android.text.TextPaint time = new android.text.TextPaint(prayerClocks[0].getPaint());
        android.text.TextPaint info = new android.text.TextPaint(prayerInfo.getPaint());
        float scale = 1f;
        int gap = defaultGap;
        for (; scale >= 0.7f; scale -= 0.025f) {
            name.setTextSize(11f * scale * sd);
            time.setTextSize(22f * scale * sd);
            info.setTextSize(13f * scale * sd);
            float cells = 0;
            for (int i = 0; i < prayerClocks.length; i++) {
                cells += Math.max(name.measureText(prayerLabels[i].getText().toString().toUpperCase(Locale.ROOT)),
                        time.measureText(prayerClocks[i].getText().toString())) + 2 * g;
            }
            float infoW = 0;
            for (String line : prayerInfo.getText().toString().split("\n")) infoW = Math.max(infoW, info.measureText(line));
            // The row is pulled left by g, so the first cell's padding does not count.
            float fixed = cells - g + infoStart + infoW + 2;
            gap = (int) Math.floor((target - fixed) / prayerClocks.length);
            if (gap >= minGap) {
                gap = Math.min(gap, defaultGap);
                break;
            }
        }
        scale = Math.max(scale, 0.7f);
        if (Math.abs(scale - prayerScale) < 0.001f && gap == prayerGap) return;
        prayerScale = scale;
        prayerGap = gap;
        for (int i = 0; i < prayerClocks.length; i++) {
            prayerLabels[i].setTextSize(11f * scale);
            prayerClocks[i].setTextSize(22f * scale);
            View cell = (View) prayerClocks[i].getParent();
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) cell.getLayoutParams();
            lp.setMarginEnd(Math.max(gap, minGap));
            cell.setLayoutParams(lp);
        }
        prayerInfo.setTextSize(13f * scale);
        prayerInfo.setLineSpacing(10 * density * scale, 1f);
        // Not wider than the room: the countdown and the date wrap rather than run past Inputs.
        ViewGroup.LayoutParams rowLp = prayerRow.getLayoutParams();
        int width = target + g;
        if (rowLp.width != width) {
            rowLp.width = width;
            prayerRow.setLayoutParams(rowLp);
        }
    }

    /** A soft dark halo under the prayer texts: they read on white and bright pictures too. */
    private void shadePrayerText() {
        for (int i = 0; i < prayerClocks.length; i++) {
            if (prayerClocks[i] == null) return;
            prayerClocks[i].setShadowLayer(5 * density, 0, 0, 0xB3000000);
            prayerLabels[i].setShadowLayer(4 * density, 0, 0, 0xB3000000);
        }
        prayerInfo.setShadowLayer(4 * density, 0, 0, 0xB3000000);
    }

    /**
     * The prayer-time sound: the built-in azans, the family's own recordings, a chime or silence, each
     * heard as it is chosen. "Import" brings in audio from the Download folder (a browser or LocalSend);
     * the wallpaper screen's phone page takes audio files too.
     */
    private void choosePrayerSound() {
        List<String> keys = new ArrayList<>(java.util.Arrays.asList(PrayerAlarm.SOUNDS));
        List<String> names = new ArrayList<>(java.util.Arrays.asList(PrayerAlarm.soundNames(this)));
        for (String h : PrayerAlarm.hidden(this)) {
            int i = keys.indexOf(h);
            if (i >= 0) {
                keys.remove(i);
                names.remove(i);
            }
        }
        for (String[] own : PrayerAlarm.ownSounds(this)) {
            keys.add(keys.size() - 2, own[0]);    // before the chime and silence
            names.add(names.size() - 2, own[1]);
        }
        int checked = Math.max(0, keys.indexOf(PrayerAlarm.sound(this)));
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.azan_title)
                .setSingleChoiceItems(names.toArray(new String[0]), checked, (d, which) -> {
                    PrayerAlarm.setSound(this, keys.get(which));
                    PrayerAlarm.play(this, keys.get(which));   // a preview
                })
                .setNeutralButton(R.string.azan_import, (d, w) -> new Thread(() -> {
                    int n = PrayerAlarm.importFromDownloads(getApplicationContext());
                    handler.post(() -> {
                        Toast.makeText(this, n == 0 ? getString(R.string.azan_none)
                                : getString(R.string.azan_added, n), Toast.LENGTH_LONG).show();
                        if (n > 0 && !isDestroyed()) choosePrayerSound();
                    });
                }, "azan-import").start())
                .setNegativeButton(R.string.azan_delete, (d, w) -> deleteAzan())
                .setPositiveButton("OK", null)
                .show();
    }

    /**
     * Lists the azans: one chosen is deleted after a yes (the family's own recordings) or taken off the
     * list (the built-in ones, which live in the app). Chime and silence always stay.
     */
    private void deleteAzan() {
        List<String[]> own = new ArrayList<>();
        java.util.Set<String> hidden = PrayerAlarm.hidden(this);
        for (int i = 0; i < PrayerAlarm.SOUNDS.length; i++) {
            String k = PrayerAlarm.SOUNDS[i];
            if (k.startsWith("azan") && !hidden.contains(k)) own.add(new String[]{k, PrayerAlarm.soundNames(this)[i]});
        }
        own.addAll(PrayerAlarm.ownSounds(this));
        if (own.isEmpty()) return;
        String[] names = new String[own.size()];
        for (int i = 0; i < names.length; i++) names[i] = own.get(i)[1];
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.azan_delete_which)
                .setItems(names, (d, which) -> new android.app.AlertDialog.Builder(this,
                        android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle(getString(R.string.delete_x, names[which]))
                        .setPositiveButton(R.string.delete, (d2, w2) -> {
                            String key = own.get(which)[0];
                            java.io.File dir = PrayerAlarm.azanDir(this);
                            if (key.startsWith(PrayerAlarm.FILE)) {
                                if (dir != null) new java.io.File(dir, key.substring(PrayerAlarm.FILE.length())).delete();
                            } else {
                                PrayerAlarm.hide(this, key);
                            }
                            // The sound in use went: the chime takes over until another is chosen.
                            if (key.equals(PrayerAlarm.sound(this))) PrayerAlarm.setSound(this, "chime");
                            choosePrayerSound();
                        })
                        .setNegativeButton(R.string.cancel, (d2, w2) -> choosePrayerSound())
                        .show())
                .show();
    }

    /**
     * The weather card under the clock, as wide as the clock and the date together: place, temperature
     * and sky; OK opens the week's forecast.
     */
    private void buildWeatherLine(android.graphics.Typeface outfit) {
        weatherLine = new TextView(this);
        weatherLine.setTypeface(outfit);
        weatherLine.setFontVariationSettings("'wght' 500");
        weatherLine.setTextSize(13f);
        weatherLine.setLetterSpacing(0.03f);
        weatherLine.setTextColor(0xE6FFFFFF);
        weatherLine.setIncludeFontPadding(false);
        weatherLine.setSingleLine(true);
        weatherLine.setEllipsize(android.text.TextUtils.TruncateAt.END);
        weatherLine.setGravity(Gravity.CENTER);
        int padH = Math.round(10 * density), padV = Math.round(6 * density);
        weatherLine.setPadding(padH, padV, padH, padV);
        weatherLine.setFocusable(true);
        weatherLine.setClickable(true);
        weatherLine.setBackground(eplBackground(false));
        weatherLine.setVisibility(View.GONE);
        weatherLine.setOnFocusChangeListener((v, has) -> {
            v.setBackground(eplBackground(has));
            weatherLine.setShadowLayer(has ? 10 * density : 0, 0, 0, 0xCCFFC46B);
            v.animate().scaleX(has ? 1.05f : 1f).scaleY(has ? 1.05f : 1f).setDuration(160).start();
        });
        weatherLine.setOnClickListener(v -> {
            try {
                startActivity(new Intent(this, WeatherActivity.class));
            } catch (Exception ignored) {
            }
        });
        weatherLine.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    findViewById(R.id.btnInputs).requestFocus();
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (prayerRow.getVisibility() == View.VISIBLE) prayerRow.requestFocus();
                    else focusMiddle(wisdomFocusable() ? heroShelf : eplCard);
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    return true;
                default:
                    return false;
            }
        });
        // Between the top bar and the prayer times, its left edge on the clock's.
        ViewGroup column = (ViewGroup) prayerRow.getParent();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Math.round(8 * density);
        column.addView(weatherLine, column.indexOfChild(prayerRow), lp);
        // Room for its zoom and glow when selected: nothing round it cuts them off.
        for (ViewGroup g = column; g != null && g.getId() != R.id.root; g = g.getParent() instanceof ViewGroup ? (ViewGroup) g.getParent() : null) {
            g.setClipChildren(false);
            g.setClipToPadding(false);
        }
        View clock = findViewById(R.id.clock);
        TextView date = findViewById(R.id.date);
        View.OnLayoutChangeListener fit = (v, l, t, r, b2, ol, ot, orr, ob) -> {
            // From the clock's left edge to the end of the date's text.
            int[] c = new int[2], col = new int[2];
            clock.getLocationOnScreen(c);
            column.getLocationOnScreen(col);
            int[] dt = new int[2];
            date.getLocationOnScreen(dt);
            int right = dt[0] + Math.round(date.getPaint().measureText(date.getText().toString().toUpperCase(Locale.getDefault())));
            int width = right - c[0];
            int margin = c[0] - col[0] - column.getPaddingLeft();
            if (width <= 0) return;
            if (lp.width != width || lp.getMarginStart() != margin) {
                lp.width = width;
                lp.setMarginStart(margin);
                handler.post(() -> {
                    weatherLine.setLayoutParams(lp);
                    fitWeatherText();
                });
            }
        };
        clock.addOnLayoutChangeListener(fit);
        date.addOnLayoutChangeListener(fit);
        showWeather(Weather.cached(this));
    }

    private void showWeather(Weather.Report r) {
        if (weatherLine == null || r == null) return;
        weatherLine.setText(r.line(this));
        weatherLine.setVisibility(View.VISIBLE);
        fitWeatherText();
    }

    /** The whole line inside the card's fixed width: a smaller size, never cut off, down to 10sp. */
    private void fitWeatherText() {
        ViewGroup.LayoutParams lp = weatherLine.getLayoutParams();
        if (lp == null || lp.width <= 0) return;
        int room = lp.width - weatherLine.getPaddingLeft() - weatherLine.getPaddingRight();
        String text = weatherLine.getText().toString();
        android.text.TextPaint probe = new android.text.TextPaint(weatherLine.getPaint());
        float sp = 13f;
        for (; sp > 10f; sp -= 0.5f) {
            probe.setTextSize(sp * getResources().getDisplayMetrics().scaledDensity);
            if (probe.measureText(text) <= room) break;
        }
        weatherLine.setTextSize(sp);
    }

    private void refreshWeatherIfDue() {
        if (weatherLine == null || weatherRefreshRunning || !Weather.due(this)) return;
        weatherRefreshRunning = true;
        new Thread(() -> {
            Weather.Report r = Weather.refresh(getApplicationContext());
            handler.post(() -> {
                weatherRefreshRunning = false;
                if (!isDestroyed()) showWeather(r != null ? r : Weather.cached(this));
            });
        }, "weather").start();
    }

    /** The Premier League card at the right end of the prayer row: the latest matches, OK for the table. */
    private void buildEplCard(android.graphics.Typeface outfit) {
        eplCard = new TextView(this);
        eplCard.setTypeface(outfit);
        eplCard.setFontVariationSettings("'wght' 400");
        eplCard.setTextSize(13f);
        eplCard.setTextColor(0xE6FFFFFF);
        eplCard.setLineSpacing(3 * density, 1f);
        eplCard.setFocusable(true);
        eplCard.setClickable(true);
        int padH = Math.round(16 * density), padV = Math.round(9 * density);
        eplCard.setPadding(padH, padV, padH, padV);
        eplCard.setBackground(eplBackground(false));
        eplCard.setVisibility(View.GONE);
        eplCard.setOnFocusChangeListener((v, has) -> {
            widgetHint(v, has);
            v.setBackground(eplBackground(has));
            ((TextView) v).setShadowLayer(has ? 14 * density : 0, 0, 0, 0xCCFFC46B);
            v.animate().scaleX(has ? 1.04f : 1f).scaleY(has ? 1.04f : 1f).setDuration(160).start();
        });
        eplCard.setOnClickListener(v -> showEplTable());
        eplCard.setOnKeyListener(holdForMenu(this::eplMenu, (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                // Up the screen in order: the prayer times, the weather, then the top bar.
                if (prayerRow != null && prayerRow.getVisibility() == View.VISIBLE) prayerRow.requestFocus();
                else if (weatherLine != null && weatherLine.getVisibility() == View.VISIBLE) weatherLine.requestFocus();
                else findViewById(R.id.btnSettings).requestFocus();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                focusTile(lastFocused % COLUMNS);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && wisdomFocusable()) {
                heroShelf.requestFocus();
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && prayerRow.getVisibility() == View.VISIBLE) {
                prayerRow.requestFocus();
                return true;
            }
            return keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
        }));
        // Level with the verse on the left, its right edge under the Settings button.
        FrameLayout home = findViewById(R.id.root);
        home.addView(eplCard, home.indexOfChild(heroShelf) + 1, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        eplCard.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orr, ob) -> positionEplCard());
        heroShelf.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orr, ob) -> positionEplCard());
    }


    private void positionEplCard() {
        if (eplCard == null || eplCard.getWidth() == 0) return;
        View settings = findViewById(R.id.btnSettings);
        View wifi = btnWifi.getVisibility() == View.VISIBLE ? btnWifi : findViewById(R.id.btnWallpaper);
        if (settings.getWidth() == 0 || wifi.getWidth() == 0) return;
        // On-screen places, the top row's slide to line up with the dock included.
        int[] at = new int[2], home = new int[2];
        findViewById(R.id.root).getLocationOnScreen(home);
        settings.getLocationOnScreen(at);
        float right = at[0] - home[0] + settings.getWidth();
        float buttonsBottom = at[1] - home[1] + settings.getHeight();
        wifi.getLocationOnScreen(at);
        float left = at[0] - home[0];
        // As wide as the top bar from Wi-Fi to Settings, or wider leftwards when the lines need it
        // (scores and the live minute are longer than kick-off times), so nothing wraps.
        int width = Math.max(Math.round(right - left), eplNeeded);
        left = right - width;
        ViewGroup.LayoutParams lp = eplCard.getLayoutParams();
        if (width > 0 && lp.width != width) {
            // Not during this layout pass: a size asked for mid-layout waited for the next one.
            handler.post(() -> {
                lp.width = width;
                eplCard.setLayoutParams(lp);
                updateEplCard();   // tab stops follow the new width
            });
        }
        eplCard.setX(left);
        float top = heroQuotes || heroShelf.getHeight() == 0 ? widgetTop() : heroShelf.getY();
        eplCard.setY(top);
    }

    private android.graphics.drawable.GradientDrawable eplBackground(boolean focused) {
        android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
        bg.setCornerRadius(16 * density);
        bg.setColor(focused ? 0x4DFFC46B : 0x1FFFFFFF);
        bg.setStroke(Math.round(focused ? 2.5f * density : 1.5f * density), focused ? 0xFFFFC46B : 0x33FFFFFF);
        return bg;
    }

    private void refreshEplIfDue() {
        if (eplCard == null || eplRefreshRunning || !EplScores.due()) return;
        eplRefreshRunning = true;
        final Set<String> teams = eplTeams;
        new Thread(() -> {
            EplScores.refresh(teams);
            handler.post(() -> {
                eplRefreshRunning = false;
                if (!isDestroyed()) updateEplCard();
            });
        }, "epl-scores").start();
    }

    private void updateEplCard() {
        if (eplCard == null) return;
        List<EplScores.Match> latest = EplScores.latest(eplTeams, 4);
        if (latest.isEmpty() || !HomeWidgets.shown(this, HomeWidgets.EPL)) {
            if (!eplCard.hasFocus()) eplCard.setVisibility(View.GONE);
            return;
        }
        boolean is24 = android.text.format.DateFormat.is24HourFormat(this);
        String head = getString(R.string.epl_head, EplScores.week);
        StringBuilder sb = new StringBuilder(head);
        // Where each finished match's winner (name and goals) sits in the text, to set it in bold.
        List<int[]> winners = new ArrayList<>();
        for (EplScores.Match m : latest) {
            sb.append('\n');
            int at = sb.length();
            String line = EplScores.line(m, is24);
            sb.append(line);
            int won = EplScores.winner(m);
            if (won == 0) continue;
            String part = won > 0 ? m.homeAbbr + " " + m.homeScore : m.awayScore + " " + m.awayAbbr;
            int i = won > 0 ? line.indexOf(part) : line.indexOf(part, line.indexOf('\u2013'));
            if (i >= 0) winners.add(new int[]{at + i, at + i + part.length()});
        }
        SpannableString text = new SpannableString(sb);
        for (int[] w : winners) text.setSpan(new Weight(WINNER_WEIGHT), w[0], w[1], Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        eplFitTabs(text, sb.toString().split("\n"), eplCard);
        text.setSpan(new android.text.style.ForegroundColorSpan(0xFFFFC46B), 0, head.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new android.text.style.RelativeSizeSpan(0.8f), 0, head.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        eplCard.setText(text);
        eplCard.setVisibility(View.VISIBLE);
    }

    private static final int WINNER_WEIGHT = 700;

    /** A weight of the card's own variable font (Outfit), so the bold is real rather than smeared. */
    static final class Weight extends android.text.style.MetricAffectingSpan {
        private final String wght;

        Weight(int weight) {
            wght = "'wght' " + weight;
        }

        @Override
        public void updateDrawState(android.text.TextPaint p) {
            p.setFontVariationSettings(wght);
        }

        @Override
        public void updateMeasureState(@androidx.annotation.NonNull android.text.TextPaint p) {
            p.setFontVariationSettings(wght);
        }
    }

    /** The card's width its widest line needs, padding included; 0 until there are lines. */
    private int eplNeeded;

    /**
     * Tab stops measured from the widest entry of each column, so the date and time columns line up
     * whatever the lines hold, and the card width they need (see {@link #positionEplCard}).
     */
    private void eplFitTabs(SpannableString text, String[] lines, TextView view) {
        android.graphics.Paint paint = view.getPaint();
        // The match column is measured in the winner's weight: bold names are a little wider.
        android.graphics.Paint bold = new android.graphics.Paint(paint);
        bold.setFontVariationSettings("'wght' " + WINNER_WEIGHT);
        float[] col = new float[3];
        for (int i = 1; i < lines.length; i++) {   // line 0 is the heading
            String[] cells = lines[i].split("\t");
            for (int c = 0; c < cells.length && c < 3; c++) col[c] = Math.max(col[c], (c == 0 ? bold : paint).measureText(cells[c]));
        }
        float gap = 16 * density;
        int first = Math.round(col[0] + gap), second = Math.round(col[0] + gap + col[1] + gap);
        text.setSpan(new android.text.style.TabStopSpan.Standard(first), 0, text.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        text.setSpan(new android.text.style.TabStopSpan.Standard(second), 0, text.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        int needed = Math.round(second + col[2] + 4 * density) + view.getPaddingLeft() + view.getPaddingRight();
        if (needed != eplNeeded) {
            eplNeeded = needed;
            handler.post(this::positionEplCard);
        }
    }

    /** Tab stops at shares of the card's inner width, so the date and time columns line up. */
    private void eplTabs(SpannableString text, float date, float time, TextView view) {
        // The width the card is being given, not the one it last had: it may not have been laid out yet.
        int target = view.getLayoutParams() != null && view.getLayoutParams().width > 0
                ? view.getLayoutParams().width : view.getWidth();
        int inner = target - view.getPaddingLeft() - view.getPaddingRight();
        if (inner <= 0) inner = Math.round(300 * density);
        text.setSpan(new android.text.style.TabStopSpan.Standard(Math.round(inner * date)), 0, text.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        text.setSpan(new android.text.style.TabStopSpan.Standard(Math.round(inner * time)), 0, text.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
    }

    private String eplOnHomeLabel() {
        return getString(HomeWidgets.shown(this, HomeWidgets.EPL) ? R.string.epl_hide_home : R.string.epl_show_home);
    }

    /** Hides a widget from Home and says where it comes back from. */
    private void hideWidget(String card, String tile) {
        HomeWidgets.setShown(this, card, false);
        focusTile(lastFocused % COLUMNS);
        updateEplCard();
        updatePrayerTimes();
        showWisdom();
        Toast.makeText(this, getString(R.string.hidden_toast, tile), Toast.LENGTH_LONG).show();
    }

    /** A small list of what a widget can do, opened by holding OK on it. */
    private void widgetMenu(String title, String[] labels, Runnable[] acts) {
        new android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setItems(labels, (dlg, which) -> acts[which].run())
                .show();
    }

    private void eplMenu() {
        widgetMenu(getString(R.string.tile_epl), new String[]{getString(R.string.menu_epl_table), getString(R.string.menu_hide)},
                new Runnable[]{this::showEplTable, () -> hideWidget(HomeWidgets.EPL, getString(R.string.tile_epl))});
    }

    private void prayerMenu() {
        widgetMenu(getString(R.string.tile_prayer), new String[]{getString(R.string.menu_prayer_page), getString(R.string.menu_azan),
                        getString(R.string.menu_hide)},
                new Runnable[]{() -> open(new Intent(this, PrayerActivity.class)), this::choosePrayerSound,
                        () -> hideWidget(HomeWidgets.PRAYER, getString(R.string.tile_prayer))});
    }

    private void wisdomMenu() {
        widgetMenu(getString(R.string.tile_wisdom), new String[]{getString(R.string.menu_wisdom_next),
                        getString(R.string.menu_wisdom_manage), getString(R.string.menu_hide)},
                new Runnable[]{this::nextWisdom, () -> open(new Intent(this, QuotesActivity.class)),
                        () -> hideWidget(HomeWidgets.QUOTES, getString(R.string.tile_wisdom))});
    }

    /**
     * OK pressed and let go is a click; OK held for most of a second opens the widget's menu. The
     * widget takes OK itself so Android's own long press, which a remote does not always send, is
     * not needed.
     */
    private View.OnKeyListener holdForMenu(Runnable menu, View.OnKeyListener keys) {
        final boolean[] fired = {false};
        final Runnable open = () -> {
            fired[0] = true;
            menu.run();
        };
        return (v, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                if (event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (event.getRepeatCount() == 0) {
                        fired[0] = false;
                        handler.postDelayed(open, 700);
                    }
                    return true;
                }
                if (event.getAction() == KeyEvent.ACTION_UP) {
                    handler.removeCallbacks(open);
                    if (!fired[0]) v.performClick();
                    fired[0] = false;
                    return true;
                }
            }
            return keys.onKey(v, keyCode, event);
        };
    }

    private TextView hint;

    /** "Tahan OK · pilihan" under a widget while it is selected, so its menu can be found. */
    private void widgetHint(View widget, boolean on) {
        if (hint == null) {
            hint = new TextView(this);
            hint.setText(R.string.widget_hint);
            hint.setTypeface(getResources().getFont(R.font.outfit));
            hint.setTextSize(11.5f);
            hint.setTextColor(0xE6FFFFFF);
            int p = Math.round(6 * density);
            hint.setPadding(Math.round(10 * density), Math.round(3 * density), Math.round(10 * density), Math.round(3 * density));
            android.graphics.drawable.GradientDrawable bg = new android.graphics.drawable.GradientDrawable();
            bg.setColor(0xB30B0E16);
            bg.setCornerRadius(12 * density);
            hint.setBackground(bg);
            hint.setAlpha(0f);
            root.addView(hint, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
        if (!on) {
            hint.animate().alpha(0f).setDuration(120).start();
            return;
        }
        int[] at = new int[2], home = new int[2];
        root.getLocationOnScreen(home);
        widget.getLocationOnScreen(at);
        hint.bringToFront();
        hint.setX(at[0] - home[0] + 4 * density);
        hint.setY(at[1] - home[1] + widget.getHeight() * widget.getScaleY() + 6 * density);
        hint.animate().alpha(1f).setStartDelay(250).setDuration(200).start();
    }

    // ---- the Wisdom widget ---------------------------------------------------------------------

    /** Where the widgets under the prayer times begin: below the lowest of the clock, weather and prayer rows. */
    private float widgetTop() {
        int[] at = new int[2], home = new int[2];
        root.getLocationOnScreen(home);
        float bottom = 0;
        for (View v : new View[]{findViewById(R.id.leftBlock), weatherLine, prayerRow}) {
            if (v == null || v.getVisibility() != View.VISIBLE || v.getHeight() == 0) continue;
            v.getLocationOnScreen(at);
            bottom = Math.max(bottom, at[1] - home[1] + v.getHeight());
        }
        return bottom + 30 * density;
    }

    private boolean wisdomFocusable() {
        return heroQuotes && HomeWidgets.shown(this, HomeWidgets.QUOTES) && placeName != null;
    }

    /** The Wisdom widget can be selected: OK shows other words, holding OK opens its menu. */
    private void setupWisdomWidget() {
        int p = Math.round(14 * density);
        heroShelf.setPadding(p, Math.round(10 * density), p, Math.round(12 * density));
        ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) heroShelf.getLayoutParams();
        lp.setMarginStart(lp.getMarginStart() - p);
        heroShelf.setLayoutParams(lp);
        heroShelf.setFocusable(true);
        heroShelf.setClickable(true);
        heroShelf.setOnClickListener(v -> nextWisdom());
        heroShelf.setOnFocusChangeListener((v, has) -> {
            widgetHint(v, has);
            v.setBackground(has ? eplBackground(true) : null);
        });
        heroShelf.setOnKeyListener(holdForMenu(this::wisdomMenu, (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (prayerRow.getVisibility() == View.VISIBLE) prayerRow.requestFocus();
                    else if (weatherLine != null && weatherLine.getVisibility() == View.VISIBLE) weatherLine.requestFocus();
                    else findViewById(R.id.btnInputs).requestFocus();
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    if (eplCard != null && eplCard.getVisibility() == View.VISIBLE) eplCard.requestFocus();
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    focusTile(lastFocused % COLUMNS);
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    return true;
                default:
                    return false;
            }
        }));
        // The prayer times or weather appearing or going moves the widgets up or down with them.
        ((View) prayerRow.getParent()).addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orr, ob) -> {
            heroShelf.setY(widgetTop());
            positionEplCard();
        });
    }

    /** Down from the rows above: the Wisdom widget or the score card, whichever {@code prefer} is, else the other, else the dock. */
    private void focusMiddle(View prefer) {
        View other = prefer == heroShelf ? eplCard : heroShelf;
        for (View w : new View[]{prefer, other}) {
            if (w == null) continue;
            if (w == heroShelf ? wisdomFocusable() : eplCard != null && eplCard.getVisibility() == View.VISIBLE) {
                w.requestFocus();
                return;
            }
        }
        focusTile(lastFocused % COLUMNS);
    }

    /** The Wisdom and score widgets fade out while a row below the dock is selected, and back with the dock. */
    private boolean widgetsIn = true;

    private void fadeWidgets(boolean in) {
        widgetsIn = in;
        float to = in ? 1f : 0f;
        int ms = reducedMotion ? 80 : in ? 260 : 160;
        if (eplCard != null && eplCard.getAlpha() != to) eplCard.animate().alpha(to).setDuration(ms).start();
        if (heroQuotes && heroShown && heroShelf.getAlpha() != to) heroShelf.animate().alpha(to).setDuration(ms).start();
    }

    /** Shows the Wisdom widget, or hides it when switched off. */
    private void showWisdom() {
        if (!heroQuotes) return;
        boolean on = HomeWidgets.shown(this, HomeWidgets.QUOTES) && placeName != null;
        heroShelf.setFocusable(on);
        if (!on) {
            if (heroShelf.hasFocus()) focusTile(lastFocused % COLUMNS);
            heroShelf.animate().cancel();
            heroShelf.setAlpha(0f);
            heroShown = false;
            return;
        }
        if (!placeName.contentEquals(heroName.getText())) setHeroText(placeName, placeCountry, placeText);
        heroShelf.setY(widgetTop());
        if (!heroShown) {
            heroShelf.animate().cancel();
            if (firstFrameHeld || resuming || reducedMotion) heroShelf.setAlpha(1f);
            else heroShelf.animate().alpha(1f).setDuration(260).start();
        }
        heroShown = true;
    }

    /** OK on the Wisdom widget: other words for the same picture. */
    private int quoteShift;

    private void nextWisdom() {
        quoteShift++;
        if (wallName != null) setPlace(new File(wallName));
    }

    /** The table on the left, the followed teams' matches on the right; OK on a match looks for its highlights. */
    private void showEplTable() {
        if (eplDialog != null) eplDialog.dismiss();
        Dialog d = new Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
        eplDialog = d;
        android.graphics.Typeface outfit = getResources().getFont(R.font.outfit);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.HORIZONTAL);
        page.setBackgroundColor(0xF2080A10);
        int pad = Math.round(48 * density);
        page.setPadding(pad, Math.round(36 * density), pad, Math.round(24 * density));

        LinearLayout tableCol = new LinearLayout(this);
        tableCol.setOrientation(LinearLayout.VERTICAL);
        tableCol.addView(eplText(outfit, getString(R.string.epl_table), 26f, 0xFFFFFFFF, 300));
        StringBuilder rows = new StringBuilder();
        for (EplScores.Row r : EplScores.table) {
            rows.append(String.format(Locale.ROOT, "%2s  %-16s %3s %5s %4s%n", r.rank, r.team, r.played, r.diff, r.points));
        }
        TextView t = eplText(android.graphics.Typeface.MONOSPACE, rows.length() == 0 ? getString(R.string.no_data) :
                String.format(Locale.ROOT, "%2s  %-16s %3s %5s %4s%n", "#", "Team", "P", "GD", "Pts") + rows, 13f, 0xE6FFFFFF, 400);
        t.setLineSpacing(2 * density, 1f);
        tableCol.addView(t);
        page.addView(tableCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.1f));

        boolean is24 = android.text.format.DateFormat.is24HourFormat(this);

        LinearLayout matchCol = new LinearLayout(this);
        matchCol.setOrientation(LinearLayout.VERTICAL);
        matchCol.addView(eplText(outfit, getString(R.string.epl_week_ok, EplScores.week), 22f, 0xFFFFFFFF, 300));
        View first = eplLink(matchCol, outfit, getString(R.string.epl_channel), () -> openYouTube(
                "https://www.youtube.com/channel/" + EplScores.CHANNEL, "Premier League"));
        eplLink(matchCol, outfit, getString(R.string.epl_best, EplScores.week),
                () -> searchHighlights("Premier League Matchweek " + EplScores.week + " best moments goals"));
        // Hidden from Home by holding OK on the card, it comes back from here.
        TextView[] onHome = new TextView[1];
        onHome[0] = eplLink(matchCol, outfit, eplOnHomeLabel(), () -> {
            HomeWidgets.setShown(this, HomeWidgets.EPL, !HomeWidgets.shown(this, HomeWidgets.EPL));
            onHome[0].setText(eplOnHomeLabel());
            updateEplCard();
        });
        for (EplScores.Match m : EplScores.matches) {
            eplLink(matchCol, outfit, null, () -> searchHighlights(m.home + " vs " + m.away + " highlights"))
                    .setText(eplColumns(EplScores.line(m, is24) + "\t" + m.home + " v " + m.away));
        }
        // After this week's matches: each followed team's next match.
        if (!EplScores.next.isEmpty()) {
            TextView head = eplText(outfit, getString(R.string.epl_next), 22f, 0xFFFFC46B, 300);
            head.setPadding(0, Math.round(18 * density), 0, Math.round(6 * density));
            matchCol.addView(head);
            for (EplScores.Match m : EplScores.next) {
                eplLink(matchCol, outfit, null, () -> searchHighlights(m.home + " vs " + m.away + " preview"))
                        .setText(eplColumns(EplScores.line(m, is24) + "\t" + m.home + " v " + m.away));
            }
        }
        android.widget.ScrollView scroll = new android.widget.ScrollView(this);
        scroll.setClipToPadding(false);
        scroll.addView(matchCol);
        page.addView(scroll, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.15f));
        d.setContentView(page);
        d.show();
        first.requestFocus();
    }

    /** One focusable line in the EPL screen: small, on one line, lit when selected. */
    private TextView eplLink(LinearLayout col, android.graphics.Typeface face, String text, Runnable act) {
        TextView row = eplText(face, text == null ? "" : text, 13f, 0xE6FFFFFF, 400);
        row.setSingleLine(true);
        row.setEllipsize(TextUtils.TruncateAt.END);
        row.setFocusable(true);
        row.setClickable(true);
        int p = Math.round(8 * density);
        row.setPadding(p, p, p, p);
        row.setOnFocusChangeListener((v, has) -> v.setBackground(has ? eplBackground(true) : null));
        row.setOnClickListener(v -> act.run());
        col.addView(row);
        return row;
    }

    /** Match, date, time and teams in fixed columns, so every line lines up. */
    private SpannableString eplColumns(String line) {
        SpannableString s = new SpannableString(line);
        for (int stop : new int[]{108, 196, 262}) {
            s.setSpan(new android.text.style.TabStopSpan.Standard(Math.round(stop * density)), 0, s.length(),
                    Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        }
        return s;
    }

    /** A YouTube page in the YouTube app, else wherever links open; a search if neither will. */
    private void openYouTube(String url, String fallbackSearch) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.google.android.youtube.tv")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            searchHighlights(fallbackSearch);
        }
    }

    private TextView eplText(android.graphics.Typeface face, String s, float sp, int color, int weight) {
        TextView t = new TextView(this);
        t.setTypeface(face);
        if (face != android.graphics.Typeface.MONOSPACE) t.setFontVariationSettings("'wght' " + weight);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setText(s);
        t.setPadding(0, 0, 0, Math.round(10 * density));
        return t;
    }

    /** Opens YouTube's search for the match's highlights. */
    private void searchHighlights(String query) {
        Intent search = new Intent(Intent.ACTION_SEARCH).setPackage("com.google.android.youtube.tv")
                .putExtra(android.app.SearchManager.QUERY, query).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(search);
            return;
        } catch (ActivityNotFoundException ignored) {
        }
        open(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query))));
    }

    /** In the last 30 seconds before a prayer time, its cell blinks and glows; at the time it settles. */
    private final Runnable blinkStart = this::armBlink;

    private void armBlink() {
        handler.removeCallbacks(blinkStart);
        long at = PrayerAlarm.next(this, prayerZone);
        if (at <= 0) return;
        long left = at - System.currentTimeMillis();
        if (left > 30_000) {
            handler.postDelayed(blinkStart, left - 30_000);
            return;
        }
        java.util.Calendar when = java.util.Calendar.getInstance();
        when.setTimeInMillis(at);
        int minute = when.get(java.util.Calendar.HOUR_OF_DAY) * 60 + when.get(java.util.Calendar.MINUTE);
        PrayerTimes.Day day = PrayerTimes.find(this, prayerZone, when);
        int cell = -1;
        for (int i = 0; day != null && i < day.minutes.length; i++) if (day.minutes[i] == minute) cell = i;
        if (cell < 0 || blinking == cell) return;
        startBlink(cell, left);
    }

    private void startBlink(int cell, long ms) {
        stopBlink();
        blinking = cell;
        final TextView time = prayerClocks[cell], name = prayerLabels[cell];
        blink = android.animation.ValueAnimator.ofFloat(0f, 1f);
        blink.setDuration(1000);
        blink.setRepeatCount(android.animation.ValueAnimator.INFINITE);
        blink.addUpdateListener(a -> {
            float w = (float) (0.5 - 0.5 * Math.cos((float) a.getAnimatedValue() * 2 * Math.PI));
            time.setAlpha(0.45f + 0.55f * w);
            name.setAlpha(0.45f + 0.55f * w);
            time.setShadowLayer(2 * density + 16 * density * w, 0, 0, android.graphics.Color.argb(Math.round(220 * w), 255, 196, 107));
        });
        blink.start();
        handler.postDelayed(blinkEnd, Math.max(0, ms) + 300);
    }

    /** The time has come: a last warm glow that fades, then the row moves on to the next prayer. */
    private final Runnable blinkEnd = () -> {
        int cell = blinking;
        stopBlink();
        if (cell < 0) return;
        final TextView time = prayerClocks[cell];
        android.animation.ValueAnimator glow = android.animation.ValueAnimator.ofFloat(1f, 0f);
        glow.setDuration(8000);
        glow.addUpdateListener(a -> {
            float w = (float) a.getAnimatedValue();
            time.setShadowLayer(2 * density + 20 * density * w, 0, 0, android.graphics.Color.argb(Math.round(230 * w), 255, 196, 107));
        });
        glow.start();
        updatePrayerTimes();
    };

    private void stopBlink() {
        handler.removeCallbacks(blinkEnd);
        if (blink != null) blink.cancel();
        blink = null;
        if (blinking >= 0) {
            prayerClocks[blinking].setAlpha(1f);
            prayerLabels[blinking].setAlpha(1f);
            prayerClocks[blinking].setShadowLayer(5 * density, 0, 0, 0xB3000000);
        }
        blinking = -1;
    }

    /** As each hour begins the clock glows warm for a few seconds, then settles. */
    private final Runnable hourGlowTick = () -> {
        playHourGlow();
        scheduleHourGlow();
    };

    private void scheduleHourGlow() {
        handler.removeCallbacks(hourGlowTick);
        if (!hourGlow) return;
        java.util.Calendar c = java.util.Calendar.getInstance();
        // Coming back within the hour's first minute still gets it.
        if (c.get(java.util.Calendar.MINUTE) == 0 && c.get(java.util.Calendar.SECOND) < 50) playHourGlow();
        long ms = ((59 - c.get(java.util.Calendar.MINUTE)) * 60L + (60 - c.get(java.util.Calendar.SECOND))) * 1000L
                - c.get(java.util.Calendar.MILLISECOND) + 300;
        handler.postDelayed(hourGlowTick, ms);
    }

    private android.animation.ValueAnimator hourAnim;

    private void playHourGlow() {
        TextView clock = findViewById(R.id.clock);
        if (clock == null) return;
        if (hourAnim != null) hourAnim.cancel();
        hourAnim = android.animation.ValueAnimator.ofFloat(0f, 1f);
        hourAnim.setDuration(6000);
        hourAnim.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            // Three soft swells that fade out.
            float swell = (float) (0.5 - 0.5 * Math.cos(t * 3 * 2 * Math.PI)) * (1f - t * 0.6f);
            clock.setShadowLayer(4 * density + 22 * density * swell, 0, 0,
                    android.graphics.Color.argb(Math.round(230 * swell), 255, 196, 107));
            float s = 1f + 0.035f * swell;
            clock.setPivotX(0);
            clock.setPivotY(clock.getHeight() * 0.8f);
            clock.setScaleX(s);
            clock.setScaleY(s);
        });
        hourAnim.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(android.animation.Animator animation) {
                clock.setShadowLayer(0, 0, 0, 0);
                clock.setScaleX(1f);
                clock.setScaleY(1f);
            }
        });
        hourAnim.start();
    }

    /** Greys out every tile on screen but the selected one; null brings them all back. */
    private void muteOthers(View selected) {
        for (int i = 0; i < grid.getChildCount(); i++) {
            RecyclerView.ViewHolder vh = grid.getChildViewHolder(grid.getChildAt(i));
            if (vh instanceof AppAdapter.Holder) {
                AppAdapter.Holder h = (AppAdapter.Holder) vh;
                h.setMuted(selected != null && h.card != selected);
            }
        }
    }

    /** Today's times with the next one lit, fetching the year from JAKIM in the background when missing. */
    private void updatePrayerTimes() {
        if (prayerZone.isEmpty()) return;
        java.util.Calendar now = java.util.Calendar.getInstance();
        PrayerTimes.Day day = PrayerTimes.find(this, prayerZone, now);
        if (day == null) {
            prayerRow.setVisibility(View.GONE);
            if (!prayerRefreshRunning && PrayerTimes.due(this, prayerZone)) {
                prayerRefreshRunning = true;
                final String zone = prayerZone;
                new Thread(() -> {
                    boolean stored = PrayerTimes.refresh(getApplicationContext(), zone);
                    handler.post(() -> {
                        prayerRefreshRunning = false;
                        if (stored && !isDestroyed()) updatePrayerTimes();
                    });
                }, "prayer-times").start();
            }
            return;
        }
        boolean is24 = android.text.format.DateFormat.is24HourFormat(this);
        int minute = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE);
        int next = PrayerTimes.next(day, minute);
        for (int i = 0; i < prayerClocks.length; i++) {
            boolean lit = i == next;
            prayerClocks[i].setText(PrayerTimes.format(day.minutes[i], is24));
            // Brighter than before, so the times stay readable on a white or bright picture.
            prayerClocks[i].setTextColor(lit ? 0xFFFFFFFF : 0xE6FFFFFF);
            prayerLabels[i].setTextColor(lit ? 0xFFFFC46B : 0xD9FFFFFF);
        }
        if (!prayerRow.hasFocus() && blinking < 0) shadePrayerText();
        String hijri = PrayerTimes.hijri(day.hijri);
        String countdown;
        if (next >= 0) {
            countdown = getString(R.string.prayer_in, PrayerTimes.NAMES[next], PrayerTimes.until(this, day.minutes[next] - minute));
        } else {
            // After Isyak: tomorrow's Subuh, when the stored year has tomorrow.
            java.util.Calendar tomorrow = (java.util.Calendar) now.clone();
            tomorrow.add(java.util.Calendar.DAY_OF_MONTH, 1);
            PrayerTimes.Day t = PrayerTimes.find(this, prayerZone, tomorrow);
            countdown = t == null ? "" : getString(R.string.prayer_tomorrow, PrayerTimes.format(t.minutes[0], is24));
        }
        prayerInfo.setText(hijri.isEmpty() ? countdown : countdown.isEmpty() ? hijri : hijri + "\n" + countdown);
        // Hidden from Home, the times still count down and the azan still sounds.
        boolean shown = HomeWidgets.shown(this, HomeWidgets.PRAYER);
        if (!shown && prayerRow.hasFocus()) focusTile(lastFocused % COLUMNS);
        prayerRow.setVisibility(shown ? View.VISIBLE : View.GONE);
        if (shown) fitPrayerRow();
        if (prayerAlert) {
            PrayerAlarm.schedule(this, prayerZone);
            armBlink();
        }
    }

    /** Shows the Wi-Fi network name on the button when we can read it, otherwise the connection type. */
    private void updateWifiLabel() {
        if (btnWifi.getVisibility() == View.GONE) return;   // show_network_pill off
        String text = getString(R.string.wifi);
        boolean vpn = false, online = false;
        int icon = R.drawable.ic_wifi;
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            boolean wifi = false, ethernet = false;
            // All networks, not just the active one: with a VPN up the active network is the VPN itself.
            for (Network net : cm != null ? cm.getAllNetworks() : new Network[0]) {
                NetworkCapabilities caps = cm.getNetworkCapabilities(net);
                if (caps == null) continue;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
                    vpn = true;
                    continue;
                }
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue;
                online = true;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) ethernet = true;
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) wifi = true;
            }
            if (!online) {
                text = getString(R.string.offline);
                vpn = false;
            } else if (ethernet) {
                text = getString(R.string.ethernet);
                icon = R.drawable.ic_ethernet;
            } else if (wifi) {
                WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                WifiInfo info = wm != null ? wm.getConnectionInfo() : null;
                String ssid = info != null ? info.getSSID() : null;
                if (ssid != null) {
                    ssid = ssid.replace("\"", "").trim();
                    if (!ssid.isEmpty() && !ssid.contains("unknown") && !ssid.equals("0x")) text = ssid;
                }
            }
        } catch (Exception ignored) {
        }
        // With a VPN app configured, a VPN that is down matters too: show a red "VPN off" capsule then.
        boolean vpnDown = !vpn && online && !vpnApp.isEmpty();
        if (vpn || vpnDown) {
            // Network name, then the green lock-and-VPN (or red "VPN off") capsule drawn in place of the word.
            String badge = getString(vpn ? R.string.vpn : R.string.vpn_off);
            VpnBadge span = new VpnBadge(vpn ? getDrawable(R.drawable.ic_lock) : null, density);
            // The pill has a maximum width and ellipsizes at the end, which would cut the badge off after
            // a long network name, so shorten the name to whatever room the badge leaves. The total paddings
            // include the Wi-Fi icon.
            TextPaint paint = btnWifi.getPaint();
            float room = btnWifi.getMaxWidth() - btnWifi.getTotalPaddingStart() - btnWifi.getTotalPaddingEnd()
                    - span.getSize(paint, badge, 0, badge.length(), null) - paint.measureText(" ") - 2 * density;
            text = TextUtils.ellipsize(text, paint, Math.max(0, room), TextUtils.TruncateAt.END).toString();
            SpannableString label = new SpannableString(text + " " + badge);
            label.setSpan(span, text.length() + 1, label.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            btnWifi.setText(label);
        } else {
            btnWifi.setText(text);
        }
        btnWifi.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
    }

    /** The network pill opens the VPN app when one is configured and installed, otherwise Wi-Fi settings. */
    private void openNetworkPill() {
        Intent i = vpnApp.isEmpty() ? null : getPackageManager().getLeanbackLaunchIntentForPackage(vpnApp);
        if (i == null && !vpnApp.isEmpty()) i = getPackageManager().getLaunchIntentForPackage(vpnApp);
        open(i != null ? i : systemIntent(Settings.ACTION_WIFI_SETTINGS));
    }


    /**
     * China-market MIUI TV firmware answers no standard Settings intents at all; open Xiaomi's own
     * settings app (com.xiaomi.mitv.settings) there, so the Settings and Wi-Fi pills keep working.
     * On firmware with a standard settings handler the behaviour is unchanged.
     */
    private Intent systemIntent(String standardAction) {
        Intent i = new Intent(standardAction);
        if (i.resolveActivity(getPackageManager()) != null) return i;
        i = new Intent().setComponent(new ComponentName(
                "com.xiaomi.mitv.settings", "com.xiaomi.mitv.settings.entry.MainActivity"));
        if (i.resolveActivity(getPackageManager()) != null) return i;
        return new Intent(standardAction);   // nothing found: let open() show the can't-open toast
    }

    /** Hides the Inputs pill when it would have nothing to list: streaming boxes have no HDMI-in, AV or tuner. */
    private void updateInputsButton() {
        boolean any = liveTvIntent() != null || !sourcePanelService.isEmpty();
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        if (!any && tim != null) {
            for (TvInputInfo info : tim.getTvInputList()) {
                if (info.isPassthroughInput()) {
                    any = true;
                    break;
                }
            }
        }
        btnInputs.setVisibility(any ? View.VISIBLE : View.GONE);
    }

    /** The Live TV app's launch intent: its leanback entry point, or the configured action if it has none. */
    private Intent liveTvIntent() {
        if (liveTvPkg.isEmpty()) return null;
        Intent i = getPackageManager().getLeanbackLaunchIntentForPackage(liveTvPkg);
        if (i != null || liveTvAction.isEmpty()) return i;
        Intent byAction = new Intent(liveTvAction).setPackage(liveTvPkg);
        return getPackageManager().resolveActivity(byAction, 0) != null ? byAction : null;
    }

    /** The TV's own source-selection panel, for TVs that have no TvInputManager (TCL Android TV 9). */
    private void openSourcePanel() {
        Intent i = new Intent(sourcePanelAction);
        ComponentName c = ComponentName.unflattenFromString(sourcePanelService);
        if (c != null) i.setComponent(c);
        try {
            startService(i);
        } catch (Exception e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    private void open(Intent i) {
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
        }
    }

    /** Switches to a passthrough input (HDMI, AV). The standard way is ACTION_VIEW on its channel URI,
     *  which the Live TV app handles; where nothing takes that, the configured `input_action` is sent
     *  with the same URI instead (XGIMI's HDMI player only answers com.xgimi.action.hdmiPlayer). */
    private void openInput(String inputId) {
        Uri uri = TvContract.buildChannelUriForPassthroughInput(inputId);
        Intent view = new Intent(Intent.ACTION_VIEW).setDataAndType(uri, "vnd.android.cursor.item/channel")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            startActivity(view);
            return;
        } catch (ActivityNotFoundException e) {
            if (inputAction.isEmpty()) {
                Toast.makeText(this, R.string.not_available, Toast.LENGTH_SHORT).show();
                return;
            }
        }
        open(new Intent(inputAction).setDataAndType(uri, "vnd.android.cursor.item/channel"));
    }

    /** The glass menu while one is open (Inputs, or an app's), and what it opened from. */
    private View openMenu;
    private View menuAnchor;
    private final int[] menuAt = new int[2], menuGliderAt = new int[2];

    /**
     * Menu under the Inputs pill: HDMI ports (with connection state), AV and Live TV. It is drawn in
     * Home's own window rather than a popup window of its own: a new window takes about a quarter of
     * a second to reach the screen on this TV, so the selection could not glide out of the pill into
     * it. Here Home's own focus shape glides from the pill to the first row, the way it glides into
     * the dock's tray, and the menu's glass paints it over itself.
     */
    private void showInputs() {
        if (openMenu != null) return;
        View anchor = btnInputs;
        FrameLayout panel = (FrameLayout) getLayoutInflater().inflate(R.layout.popup_inputs, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        Runnable close = () -> closeMenu(false);

        // Compact: as wide as the Inputs pill (a touch wider so "HDMI 1" fits), hanging right below it.
        int width = Math.max(anchor.getWidth(), (int) (150 * density));
        TvInputManager tim = (TvInputManager) getSystemService(TV_INPUT_SERVICE);
        boolean sawNonHdmi = false;
        String tunerId = null;
        boolean tunerIsHw = false;
        if (tim != null) {
            List<TvInputInfo> inputs = new ArrayList<>(tim.getTvInputList());
            // HDMI ports first in port order, then AV and the rest.
            Collections.sort(inputs, (a, b) -> {
                boolean ha = a.getType() == TvInputInfo.TYPE_HDMI, hb = b.getType() == TvInputInfo.TYPE_HDMI;
                if (ha != hb) return ha ? -1 : 1;
                return String.valueOf(a.loadLabel(this)).compareToIgnoreCase(String.valueOf(b.loadLabel(this)));
            });
            // Some TVs (Sony) report every HDMI port as connected whether or not anything is plugged in,
            // so drop the HDMI status then. A TV with every port really in use loses it too.
            int hdmiPorts = 0, hdmiConnected = 0;
            for (TvInputInfo info : inputs) {
                if (!info.isPassthroughInput() || info.getType() != TvInputInfo.TYPE_HDMI) continue;
                hdmiPorts++;
                if (tim.getInputState(info.getId()) == TvInputManager.INPUT_STATE_CONNECTED) hdmiConnected++;
            }
            boolean hdmiStateBogus = hdmiPorts > 1 && hdmiConnected == hdmiPorts;
            for (TvInputInfo info : inputs) {
                if (info.getType() == TvInputInfo.TYPE_TUNER
                        && (info.getServiceInfo().applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                    // Prefer a hardware tuner: the framework names those ".../HW<n>". System apps such as
                    // AirPlay also register software inputs of type TUNER and may sort first (seen on TCL G05).
                    boolean hw = info.getId().matches(".*/HW\\d+$");
                    if (tunerId == null || (hw && !tunerIsHw) || (hw == tunerIsHw && info.getId().compareTo(tunerId) < 0)) {
                        tunerId = info.getId();
                        tunerIsHw = hw;
                    }
                }
                if (!info.isPassthroughInput()) continue;
                CharSequence l = info.loadLabel(this);
                String label = l == null ? info.getId() : l.toString();
                boolean hdmi = info.getType() == TvInputInfo.TYPE_HDMI;
                if (hdmi && !label.toUpperCase(Locale.ROOT).contains("HDMI")) label = "HDMI " + label;
                final String id = info.getId();
                int state = hdmi && hdmiStateBogus ? -1 : tim.getInputState(id);
                if (!hdmi && rows.getChildCount() > 0 && !sawNonHdmi) addSeparator(rows);
                if (!hdmi) sawNonHdmi = true;
                addInputRow(rows, close, hdmi ? R.drawable.ic_hdmi : R.drawable.ic_av, label, state,
                        () -> openInput(id));
            }
        }
        // Nothing to list where there is no TvInputManager: the TV's own source panel switches inputs.
        if (rows.getChildCount() == 0 && !sourcePanelService.isEmpty()) {
            addInputRow(rows, close, R.drawable.ic_input, getString(R.string.inputs), -1, this::openSourcePanel);
        }
        Intent liveTv = liveTvIntent();
        if (liveTv != null && tunerId != null) {
            liveTv = new Intent(Intent.ACTION_VIEW)
                    .setDataAndType(TvContract.buildChannelUriForPassthroughInput(tunerId), "vnd.android.cursor.item/channel")
                    .setPackage(liveTvPkg);
        }
        if (liveTv != null && config.showLiveTv) {
            if (rows.getChildCount() > 0) addSeparator(rows);
            final Intent liveTvIntent = liveTv;
            addInputRow(rows, close, R.drawable.ic_livetv, getString(R.string.live_tv), -1, () -> open(liveTvIntent));
        }
        if (rows.getChildCount() == 0) {
            Toast.makeText(this, R.string.no_inputs, Toast.LENGTH_SHORT).show();
            return;
        }

        // Fit the widest row: TVs that report a status on every port (Sony) would otherwise squeeze the names out.
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        width = Math.max(width, panel.getMeasuredWidth());

        showMenu(panel, rows, anchor, width, false);
    }

    /**
     * Shows a glass menu in Home's own window, under {@code anchor} or, with {@code above}, over it:
     * Home's focus shape glides from the anchor to the first row, the menu's glass paints it over
     * itself, and the glass and rows fade in around it. Up from the first row (Down from the last when
     * the menu is above), or Back, closes it and the shape glides back to the anchor.
     */
    private void showMenu(FrameLayout panel, LinearLayout rows, View anchor, int width, boolean above) {
        GlassDrawable glass = new GlassDrawable(panel, 20 * density, density, glider, 0.5f, true);
        panel.setBackground(glass);
        Backdrop.watch(panel);
        glider.addCompanion(panel);
        anchor.getLocationInWindow(menuAt);
        root.getLocationInWindow(menuGliderAt);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Math.max(0, Math.min(menuAt[0] - menuGliderAt[0], root.getWidth() - width));
        int gap = (int) (10 * density);
        if (above) {
            panel.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), View.MeasureSpec.UNSPECIFIED);
            lp.topMargin = Math.max(0, menuAt[1] - menuGliderAt[1] - gap - panel.getMeasuredHeight());
        } else {
            lp.topMargin = menuAt[1] - menuGliderAt[1] + anchor.getHeight() + gap;
        }
        root.addView(panel, lp);
        openMenu = panel;
        menuAnchor = anchor;

        // Focus stays in the menu until it closes. Leaving it towards the anchor closes it, and the
        // selection glides back into what it opened from.
        View first = null, last = null;
        for (int i = 0; i < rows.getChildCount(); i++) {
            View row = rows.getChildAt(i);
            if (!row.isFocusable()) continue;
            if (first == null) first = row;
            last = row;
            row.setOnFocusChangeListener((v, has) -> {
                if (has) glideToMenuRow(panel, rows, v);
            });
        }
        final View top = first, bottom = last;
        for (int i = 0; i < rows.getChildCount(); i++) {
            View row = rows.getChildAt(i);
            if (!row.isFocusable()) continue;
            row.setOnKeyListener((v, keyCode, event) -> {
                boolean down = event.getAction() == KeyEvent.ACTION_DOWN;
                switch (keyCode) {
                    case KeyEvent.KEYCODE_DPAD_UP:
                        if (v != top) return false;
                        if (down && !above) closeMenu(true);
                        return true;
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                        if (v != bottom) return false;
                        if (down && above) closeMenu(true);
                        return true;
                    case KeyEvent.KEYCODE_DPAD_LEFT:
                    case KeyEvent.KEYCODE_DPAD_RIGHT:
                        return true;
                    case KeyEvent.KEYCODE_BACK:
                        if (down) closeMenu(true);
                        return true;
                    default:
                        return false;
                }
            });
        }

        // The glass and the rows fade in around the selection as it arrives.
        glass.setAlpha(0);
        rows.setAlpha(0f);
        final float slide = (above ? 8 : -8) * density;
        rows.setTranslationY(slide);
        android.animation.ValueAnimator in = android.animation.ValueAnimator.ofFloat(0f, 1f);
        in.setDuration(220);
        in.setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f));
        in.addUpdateListener(an -> {
            float f = (float) an.getAnimatedValue();
            glass.setAlpha(Math.round(255 * f));
            rows.setAlpha(f);
            rows.setTranslationY(slide * (1f - f));
        });
        in.start();
        if (top != null) top.requestFocus();
    }

    /**
     * Glides Home's focus shape onto a row of the Inputs menu. From layout positions, not on-screen
     * ones: the rows slide into place as the menu opens, and the shape must land where the row ends
     * up, not where it was when focus arrived. The first row takes focus before the menu has been
     * laid out, so then it waits for the layout.
     */
    private void glideToMenuRow(View panel, View rows, View row) {
        if (!panel.isLaidOut() || row.getHeight() == 0) {
            panel.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                    panel.removeOnLayoutChangeListener(this);
                    // Still inside the layout pass here, where the panel does not count as laid
                    // out yet: glide once the pass is over.
                    panel.post(() -> {
                        if (row.hasFocus()) glideToMenuRow(panel, rows, row);
                    });
                }
            });
            return;
        }
        float l = panel.getLeft() + rows.getLeft() + row.getLeft() - glider.getLeft();
        float t = panel.getTop() + rows.getTop() + row.getTop() - glider.getTop();
        gliderOnPill = true;
        glider.setStyle(Glider.DARK);
        glider.moveTo(l, t, l + row.getWidth(), t + row.getHeight(), 13 * density);
    }

    /** Closes the open menu; with {@code toAnchor}, focus and the selection go back to what it opened from. */
    private void closeMenu(boolean toAnchor) {
        View panel = openMenu;
        if (panel == null) return;
        openMenu = null;
        if (toAnchor && menuAnchor != null) menuAnchor.requestFocus();
        menuAnchor = null;
        panel.animate().alpha(0f).setDuration(reducedMotion ? 80 : 150).withEndAction(() -> {
            glider.removeCompanion(panel);
            root.removeView(panel);
        }).start();
    }

    private boolean inMenu(View v) {
        for (View p = v; p != null && openMenu != null; p = p.getParent() instanceof View ? (View) p.getParent() : null) {
            if (p == openMenu) return true;
        }
        return false;
    }

    private PopupWindow glassPopup(View panel, int width) {
        // The same glass as the pills and the tray: what is behind it, softened, with a shining edge.
        panel.setBackground(new GlassDrawable(panel, 20 * density, density, null, 0.5f));
        Backdrop.watch(panel);
        final PopupWindow popup = new PopupWindow(panel, width, ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));   // needed for Back to dismiss
        popup.setOutsideTouchable(true);
        popup.setAnimationStyle(0);
        popup.setElevation(0f);
        popup.setOnDismissListener(() -> {
            if (openPopup == popup) openPopup = null;
        });
        openPopup = popup;
        return popup;
    }

    /** Drop in: fade and unfold from the top edge, hanging under the anchor pill. */
    private void dropIn(PopupWindow popup, View panel, View anchor, int gravity) {
        panel.setAlpha(0f);
        panel.setPivotY(0f);
        panel.setScaleY(0.92f);
        panel.setTranslationY(-8 * density);
        popup.showAsDropDown(anchor, 0, (int) (10 * density), gravity);
        panel.animate().alpha(1f).scaleY(1f).translationY(0f).setDuration(220)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.6f)).start();
    }

    private void addSeparator(LinearLayout rows) {
        View line = new View(this);
        line.setBackgroundColor(0x1FFFFFFF);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) density);
        int m = (int) (6 * density), h = (int) (12 * density);
        lp.setMargins(h, m, h, m);
        rows.addView(line, lp);
    }

    private void addInputRow(LinearLayout rows, Runnable close, int iconRes, String title, int state, Runnable action) {
        View row = getLayoutInflater().inflate(R.layout.item_input, rows, false);
        ((ImageView) row.findViewById(R.id.icon)).setImageResource(iconRes);
        ((TextView) row.findViewById(R.id.title)).setText(title);
        TextView status = row.findViewById(R.id.status);
        View dot = row.findViewById(R.id.dot);
        // This TV reports "standby" for every port whether or not something is plugged in, so only
        // definite states are shown.
        if (state == TvInputManager.INPUT_STATE_CONNECTED) {
            status.setText(R.string.connected);
            dot.setVisibility(View.VISIBLE);
        } else if (state == TvInputManager.INPUT_STATE_DISCONNECTED) {
            status.setText(R.string.not_connected);
            dot.setVisibility(View.GONE);
        } else {
            status.setVisibility(View.GONE);
            dot.setVisibility(View.GONE);
        }
        row.setOnClickListener(v -> {
            close.run();
            action.run();
        });
        rows.addView(row);
    }

    // ---- module cards ----------------------------------------------------------------------------

    /**
     * Asks about the next card that has no decision for its current signing key. One prompt at a
     * time, only over a laid out grid with window focus (not under a popup or another app), and
     * the next one follows as soon as this one is answered. Nothing is shown until the owner says Show.
     */
    private void showCardPrompt() {
        if (!moduleCards || !tilesReady || promptsPaused || cardPrompt != null || !hasWindowFocus() || isFinishing()) return;
        if (cards.isEmpty() || !CardFeature.working()) return;
        CardFeature.run(() -> {
            showCardPromptNow();
            return null;
        }, null);
    }

    private void showCardPromptNow() {
        final Cards.Card card = Cards.firstPending(cards);
        if (card == null) return;

        View panel = getLayoutInflater().inflate(R.layout.dialog_card_prompt, root, false);
        ((TextView) panel.findViewById(R.id.title)).setText(getString(R.string.card_prompt_title, card.appLabel));
        ((TextView) panel.findViewById(R.id.body)).setText(card.state == Cards.State.RESIGNED
                ? getString(R.string.card_prompt_resigned, card.appLabel)
                : card.state == Cards.State.RELABELLED
                ? getString(R.string.card_prompt_relabelled, card.appLabel)
                : getString(R.string.card_prompt_body, card.appLabel));
        TextView names = panel.findViewById(R.id.names);
        if (Cards.fold(card.label).equals(Cards.fold(card.appLabel))) names.setVisibility(View.GONE);
        else names.setText(getString(R.string.card_prompt_names, card.label, card.appLabel));
        ((TextView) panel.findViewById(R.id.identity)).setText(
                getString(R.string.card_prompt_identity, card.pkg, Cards.shortDigest(card.digest)));
        TextView keep = panel.findViewById(R.id.btnKeep);
        TextView show = panel.findViewById(R.id.btnShow);

        final Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(panel);
        dialog.setCanceledOnTouchOutside(false);
        Window w = dialog.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (440 * density), ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setDimAmount(0.6f);
        }
        // One decision per prompt: the buttons and Back (cancel) all end here.
        final boolean[] decided = {false};
        View.OnClickListener choose = v -> {
            if (decided[0]) return;
            decided[0] = true;
            cardPrompt = null;
            dialog.dismiss();
            decideCard(card, v.getId() == R.id.btnShow);   // guards its own card calls; the tile reload stays outside
            restoreFocus();   // the tiles changed under the prompt; this also chains the next prompt
        };
        keep.setOnClickListener(choose);
        show.setOnClickListener(choose);
        dialog.setOnCancelListener(d -> choose.onClick(keep));
        dialog.setOnDismissListener(d -> {
            if (cardPrompt == dialog) cardPrompt = null;
        });
        // Left and Right move between the two buttons; Up and Down stay put; the ends don't wrap.
        View.OnKeyListener keys = (v, keyCode, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
            if (keyCode == KeyEvent.KEYCODE_DPAD_UP || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) return true;
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) return v.getId() == R.id.btnKeep;
            return keyCode == KeyEvent.KEYCODE_DPAD_RIGHT && v.getId() == R.id.btnShow;
        };
        keep.setOnKeyListener(keys);
        show.setOnKeyListener(keys);
        cardPrompt = dialog;
        dialog.show();
        keep.requestFocus();
    }

    /** Writes the owner's decision and rebuilds the tiles; focus is left where it is. */
    private void decideCard(Cards.Card card, boolean show) {
        boolean saved = CardFeature.run(() -> Cards.decide(Cards.storeFile(this), card, show), false);
        if (!saved) {
            Toast.makeText(this, R.string.card_not_saved, Toast.LENGTH_SHORT).show();
            promptsPaused = true;
        }
        reloadTiles();
    }

    /** Dropdown under the Settings pill (hold OK): every discovered card, its package and key; OK toggles Shown and Hidden. */
    private void showModuleCards() {
        if (cards.isEmpty() || !CardFeature.working()) {
            Toast.makeText(this, R.string.no_module_cards, Toast.LENGTH_SHORT).show();
            return;
        }
        CardFeature.run(() -> {
            showModuleCardsNow();
            return null;
        }, null);
    }

    private void showModuleCardsNow() {
        View anchor = findViewById(R.id.btnSettings);
        View panel = getLayoutInflater().inflate(R.layout.popup_cards, root, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        final PopupWindow popup = glassPopup(panel, (int) (400 * density));
        List<Cards.Card> listed = new ArrayList<>(cards);
        Collections.sort(listed, Cards::byLabel);
        for (Cards.Card card : listed) {
            View row = getLayoutInflater().inflate(R.layout.item_card_row, rows, false);
            row.setTag(card.pkg);
            ((TextView) row.findViewById(R.id.title)).setText(card.label);
            TextView app = row.findViewById(R.id.app);
            if (Cards.fold(card.label).equals(Cards.fold(card.appLabel))) app.setVisibility(View.GONE);
            else app.setText(getString(R.string.card_app_name, card.appLabel));
            ((TextView) row.findViewById(R.id.pkg)).setText(card.pkg);
            ((TextView) row.findViewById(R.id.digest)).setText(Cards.longDigest(card.digest));
            final String pkg = card.pkg;
            row.setOnClickListener(v -> CardFeature.run(() -> {
                // The list stays open; the decision is for the card as it is now, not as it was listed.
                Cards.Card now = cardFor(pkg);
                if (now == null) return null;
                decideCard(now, now.state != Cards.State.SHOWN);
                // Every row, not only this one: a toggle can move another card across the limit of 8.
                updateCardStates(rows);
                return null;
            }, null));
            rows.addView(row);
        }
        updateCardStates(rows);
        // Scroll inside the panel instead of running off the bottom of a 720p screen.
        panel.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int[] at = new int[2];
        anchor.getLocationInWindow(at);
        int room = root.getHeight() - (at[1] + anchor.getHeight()) - (int) (34 * density);
        popup.setHeight(Math.min(panel.getMeasuredHeight(), Math.max(room, (int) (120 * density))));
        dropIn(popup, panel, anchor, Gravity.END);
        rows.getChildAt(0).requestFocus();
    }

    private Cards.Card cardFor(String pkg) {
        for (Cards.Card c : cards) if (c.pkg.equals(pkg)) return c;
        return null;
    }

    /** Writes the state of every row in the Module cards list from the current card list. */
    private void updateCardStates(LinearLayout rows) {
        List<Cards.Card> drawn = Cards.shown(cards);
        for (int i = 0; i < rows.getChildCount(); i++) {
            View row = rows.getChildAt(i);
            Cards.Card card = row.getTag() instanceof String ? cardFor((String) row.getTag()) : null;
            ((TextView) row.findViewById(R.id.state)).setText(card != null ? stateText(card, drawn.contains(card)) : "");
        }
    }

    /** The row's state: a card the owner set to Shown but which is beyond the eight drawn says so. */
    private String stateText(Cards.Card card, boolean drawn) {
        switch (card.state) {
            case SHOWN:
                return getString(drawn ? R.string.card_state_shown : R.string.card_state_over_limit);
            case HIDDEN:
                return getString(R.string.card_state_hidden);
            case RESIGNED:
                return getString(R.string.card_state_new_key);
            case RELABELLED:
                return getString(R.string.card_state_new_label);
            default:
                return getString(R.string.card_state_new);
        }
    }
}
