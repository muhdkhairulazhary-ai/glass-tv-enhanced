# Glass TV Enhanced

**PRISM+ / keep-stock-launcher HOME test:** use the [safe APK and iPhone guide](docs/PRISM-HOME-TEST.md).
Do not use the older build-and-install or debloating route below for this TV; it disables the stock launcher.
The new HOME handling is experimental until tested with the physical remote on your device.

**Your Android TV. Fast, ad-free and truly yours.** A fork of [Glass TV](https://github.com/cocombee/glass-tv) by
Gathin that adds widgets, a wallpaper studio and a choice of language, all driven from an ordinary TV remote.

**[Website](https://aizverus.github.io/glass-tv-enhanced/)** ·
**[Watch the 45-second video](docs/media/glass-tv-enhanced-ad.mp4)** ·
**[Features and user guide](docs/ENHANCED-GUIDE.md)**

<p>
  <img src="docs/media/poster.png" width="38%" alt="Glass TV Enhanced poster: Home with weather, prayer times, Wisdom and Premier League widgets">
  <img src="docs/screenshots/enhanced/home.jpg" width="60%" alt="Home with the clock, weather, prayer times, a Quran verse and Premier League scores above the glass dock">
</p>

## What Glass TV Enhanced adds

- **Widgets you can select with the remote:** weather for any town (Open-Meteo), prayer times with an azan
  (JAKIM e-Solat), **Wisdom** (Quran verses, hadith, motivation and quotes you can add, download or pin) and
  Premier League scores with the table and highlights.
- **Hide anything:** hold OK on a widget and choose *Hide from Home*; each one has its own tile in All apps to
  bring it back. Not into football or prayer times? Keep just the weather and your wallpapers.
- **Wallpaper studio:** try any picture on the real Home before choosing it, add one of 10 filters (Sepia, Noir,
  Vivid, Dramatic…) at any strength, delete pictures, and send photos from a phone over Wi-Fi.
- **Your language:** English, Bahasa Melayu or 中文, switched from the Language tile; add another with one file.
- **Little touches:** an edge glow in the selected app's colour, a soft vignette, the winner in bold, widgets that
  step aside when you browse the rows below the dock.
- **Cleaner every day:** the TV's app caches are cleared daily, and a Clear cache tile does it on demand.

### Install it with AI, the way it was built

Turn on developer options and USB / network debugging on the TV, clone this repository on a computer on the
same Wi-Fi, open an AI coding assistant such as [Claude Code](https://claude.com/claude-code) in the folder and
paste:

> Set up Glass TV Enhanced on my TV. It is a *brand and model* at *192.168.x.x*. Follow AGENTS.md: check the
> model first, back up before changing anything, ask me before each change and show me a screenshot after each
> step. Then set up the widgets I want and the language *English / Bahasa Melayu / 中文*.

The step-by-step version is in the [guide](docs/ENHANCED-GUIDE.md#install-it-with-an-ai-assistant). The manual
setup is the same as Glass TV's, below; everything Glass TV does is still here.

| | |
|---|---|
| ![Trying a wallpaper with the Sepia filter on Home](docs/screenshots/enhanced/filter-sepia.jpg) | ![The weather page](docs/screenshots/enhanced/weather-page.jpg) |
| ![Prayer times for the week](docs/screenshots/enhanced/prayer-page.jpg) | ![The Wisdom page](docs/screenshots/enhanced/wisdom-page.jpg) |

Glass TV Enhanced is released under the same MIT licence as Glass TV. Credits for the azan recordings and the
data sources are in the [guide](docs/ENHANCED-GUIDE.md#credits).

## Say thanks

Glass TV Enhanced is free and always will be. If it made your TV better and you'd like to say thanks, a small
Bitcoin tip is welcome (Bitcoin network only):

<img src="docs/media/btc-qr.png" width="160" alt="QR code for the Bitcoin address below">

`1CFwe2gB5NE6kBVikU9dkWk3h18kZREbaj`

Starring the repository and sharing it helps just as much.

---

# Glass TV (the original project)


**Make your Android TV or Google TV fast and ad-free again: strip out the bloat and ads, free up memory,
and come home to just your apps, with a new place to visit behind them every week.**

Glass TV debloats any Android TV or Google TV over Wi-Fi, with no root and nothing that can't be undone, then
swaps the ad-filled home screen for a light one in frosted glass, with a new set of places behind your apps
every week.

<p>
  <img src="docs/screenshots/promo-1-home.jpg" width="32%" alt="Your TV, fast and ad-free again: the TV behind on the left, the app grid behind on the right, Home over Mount Sneffels in front">
  <img src="docs/screenshots/promo-2-features.jpg" width="32%" alt="Looks like Apple, runs on Android: glass, Inputs, a place a week, your own app order, no ads">
  <img src="docs/screenshots/promo-3-clock.jpg" width="32%" alt="Ads off, time on: the clock screensaver">
</p>

## What you get

- **Keeps itself clean.** Every 15 days, while Home sits idle, the TV clears its app caches, where the Play
  Store leaves gigabytes of download leftovers that can make apps open to a blank screen. No computer needed,
  sign-ins and app data are never touched; the TV asks once to allow it.
- **Glass, like Apple's.** The dock's tray, the top-bar buttons and the menus show the picture behind them
  softened, with a shine along their edges. Android 10 has no live blur, so the picture is blurred once each
  time it changes and the glass draws its patch of it.
- **One selection that glides.** Between apps it is a pane of glass with the app's shadow under it; on the top
  bar and in the menus it is dark. It glides and changes shape from one to the next, never jumps.
- **Places to visit that renew themselves.** Once a week the TV takes the pictures Bing has shown on its home
  page over the past days — landmarks, landscapes, wildlife — deletes last week's and rotates through them,
  dimmed so the clock and tiles stay readable. The shelf above the dock names each one, its country and a few
  lines about it. Pictures with people in them are skipped: by the words in their caption, and by Android's
  own face detector on the TV. No key, no account, nothing to set up.
- **Every app tints the room.** The glow behind Home, and the glass with it, blends to the colour of whichever
  app is selected.
- **Arrange your apps from the remote.** Hold OK on an app for a glass menu with **Move** and **App info**.
  Move picks the app up: it wiggles, the arrows carry it (into or out of the dock too) and the other apps
  slide aside; OK puts it down. The order is kept, and apps installed later join at the end.
- **An Inputs menu in glass.** The selection glides out of the Inputs button into the list of HDMI and AV
  inputs, and Up from the top of the list glides it back.
- **Comes back as you left it.** Back from an app, the app you left is still selected; pressing Home on Home
  goes to the first app. When Android has closed Home to free memory, it comes back whole in one frame,
  with no black screen and no half-built dock.
- **A clock screensaver.** A grey analog dial with a yellow-orange second hand that ticks, the time and
  date beneath, drifting a little each minute so nothing burns in.
- **Rows feather out under the top bar** instead of being cut off when you scroll, and the Wi-Fi / Ethernet
  button can be hidden (`show_network_pill`).
- **Rotating wallpapers of your own** instead, with `"places": false`: put pictures in a theme's `wallpapers/`
  folder and choose how often they change, down to every 5 seconds. Each picture is decoded in the background
  and cross-faded.

| | |
|---|---|
| ![Home with Mount Sneffels behind the apps, its name, country and story above the glass dock](docs/screenshots/home.jpg) | ![The app grid scrolled down over the Chauvet cave, rows feathering out under the top bar](docs/screenshots/grid.jpg) |
| *Home, with this week's place* | *Every app, feathering out under the top bar* |
| ![The glass Inputs menu with HDMI 1 selected](docs/screenshots/inputs.jpg) | ![The clock screensaver: a grey analog dial, the time and the date](docs/screenshots/clock.jpg) |
| *Inputs, in glass* | *The clock screensaver* |

Still light: on a Xiaomi Mi TV with 1.8 GB of RAM the launcher uses about 80–100 MB and stays there over hours
of use, one wallpaper is in memory at a time, the week's places take about 2 MB of storage, and nothing runs
while you are inside an app.

## Setting it up

**Using an AI coding agent** (Claude Code, Codex, Cursor, Copilot…)? Point it at this repository and ask it to
set up your TV. [AGENTS.md](AGENTS.md) tells any agent, even a small model, how to do it safely on any Android TV or Google
TV: it first works out which Glass TV your TV can run (the Android launcher, the LG webOS app, or neither on
Samsung and Roku), then
asks before each change, backs up first, never uninstalls, and checks each step with you as it goes.

The full step-by-step guide for debloating and installing is in
[docs/CLEANYOURTV-README.md](docs/CLEANYOURTV-README.md), and [PROMPT.md](PROMPT.md) lets a coding assistant do
it for you. In short:

```bash
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
export TV_HOST=192.168.1.50                                   # your TV's IP address
tools/disable.sh examples/<your tv>/debloat.txt               # debloat
launcher/build-and-install.sh                                 # build the launcher and make it Home
tools/theme.sh themes/<your theme>                            # push your settings and wallpapers
```

Glass TV clears the TV's caches by itself every 15 days ([how](docs/cache-clean.md)). To clean several TVs
from one computer instead, [`tools/cache-clean.sh`](docs/cache-clean.md) finds the TVs on your network and
clears their caches, leaving sign-ins and app data untouched.

## Which TVs work

Android TV and Google TV, Android 8 or newer, on the TV or on a box plugged into any TV.
[docs/DEVICES.md](docs/DEVICES.md) lists every tested model and how far each was tested: end to end with
Glass TV, with the original launcher, or debloat only, plus what is expected to work and what cannot.

## LG and Samsung TVs

LG TVs run webOS and Samsung TVs run Tizen; neither can run Android apps, and neither lets any app replace its
home screen or the Home button. For the full Glass TV, plug in a Google TV or Android TV box (Chromecast with
Google TV, Xiaomi Mi Box, NVIDIA Shield): the TV becomes the screen and Glass TV is Home. On LG, a version you
open as an app is in preview in [webos/](webos/README.md). A Samsung version that comes back after Samsung's home is
planned: see [docs/ROADMAP.md](docs/ROADMAP.md).

## Places, or pictures of your own

Places are on from the start. To change how often the picture changes, or to use your own pictures and keep
the launcher offline, make a theme folder with a `config.json` like this and push it with `tools/theme.sh`:

```json
{
  "places": false,
  "wallpaper_seconds": 15,
  "show_network_pill": false
}
```

All keys are listed in [themes/README.md](themes/README.md). With `"places": false`, the launcher never goes
online.

## Credits and licence

- Built on [CleanYourTV](https://github.com/Gathin23/cleanyourtv) © 2026 Gathin, MIT licence — see
  [LICENSE](LICENSE). Its debloat lists, tools and launcher are the foundation of this project.
- The Outfit typeface is under the SIL Open Font License (`launcher/FONT-LICENSE-OFL.txt`).
- The Inputs icon is from Google's Material Icons (Apache 2.0).
- Pictures of places and their captions are Bing's daily images (Microsoft). The launcher fetches them on your
  own TV; the promo pictures above show three of them.
- Gotham, used in the promo pictures only, is a Hoefler&Co. typeface and is not part of this repository.
