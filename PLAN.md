# EQ App: Plan, Features & Tech Stack

> Status: v0.1 implemented (core math + importers, Room data, background engine, circuit UI). Verified on an Android 16 emulator; real-phone testing with a USB DAC is next.
> Platform: **Android only** (iOS dropped: it can't process other apps' audio).

---

## 1. Goal
An Easy Effects-style audio app for Android that:
- runs silently in the background, set once and forget;
- applies EQ and effects **only to the connected DAC / earphones** (per device), leaving the phone speaker untouched;
- needs **no root, no audio capture, no recording prompt, no mic permission, and adds no latency**;
- has proper parametric EQ, imports (AutoEQ, Equalizer APO, Easy Effects), user profiles, presets, and an offline headphone and target-curve database.

## 2. Key technical decisions

### Why not a "virtual sink" like PipeWire
- Android doesn't let normal apps create audio sinks or reroute other apps' audio. Output devices are fixed by the phone maker, and rerouting needs permissions only system apps get.
- The only non-root sink-like method is **audio capture** (how RootlessJamesDSP works). It adds 50–150 ms latency, a recording prompt, a persistent recording indicator, and some apps (Spotify, Chrome) block it. **Rejected for now.**
- Being treated as a system app requires root, a custom ROM, or the phone maker's signing key. Not an option.

### Chosen approach: Android's built-in effects engine, per device
- Uses `DynamicsProcessing` (Android 9+), which processes audio inside Android's own pipeline (like Wavelet).
- Attaches to:
  1. session 0 (global output mix; deprecated, but works on many phones)
  2. per-app sessions announced via `ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION` broadcasts (Spotify etc.)
  3. optionally, sessions found via Shizuku with the `DUMP` permission (Wavelet's "enhanced detection")
- Effects are only enabled while a chosen output (USB DAC, wired, Bluetooth) is active, and switch off when you unplug.
- **Parametric EQ is converted to Android's EQ bands:** the app calculates the exact curve from the filters and sets the bands to match it. The user always edits normal parametric filters.
- Limitations:
  - very high-Q notches and very steep HP/LP slopes are slightly approximated;
  - linear-phase/FIR modes aren't possible;
  - Android 14 bit-perfect USB mode bypasses all effects.

### Accuracy check (7Hz Salnotes Zero 2 AutoEQ file: preamp −2.2 dB, 10 filters)
Simulated conversion to bands vs the exact curve (assumes smooth blending between bands):

| Bands | Max error | Avg error |
|---|---|---|
| 16 | 2.43 dB | 0.23 dB |
| 32 | 1.48 dB | 0.07 dB |
| **64** | **0.23 dB** | **0.016 dB** |
| 128 | 0.17 dB | 0.005 dB |

→ At 64+ bands the difference is inaudible. **To do:** test the maximum band count on the actual phone.

---

## 3. Features

### 3.1 Audio effects (no capture needed)
- **Parametric EQ:** unlimited filters (peak, low shelf, high shelf, high-pass, low-pass, notch, band-pass); frequency/gain/Q; separate L/R or linked
- **Graphic EQ:** 10 / 15 / 31 bands
- **Preamp** with auto-preamp (= −max boost) + clip indicator
- **Bass tuner** (shelf + frequency limit) and **treble/brilliance** shelf
- **Tilt EQ:** one slider, warmer ↔ brighter
- **Loudness compensation** (ISO 226 equal-loudness, follows volume)
- **Compressor**, **multiband compressor**, **noise gate/expander**
- **Limiter**
- **Channel balance**, separate L/R EQ (fixes mismatched channels or hearing differences between ears)
- **Fine volume steps** (0.5 dB) + **volume cap** for hearing protection
- **Hearing test → personal correction EQ** (app plays tones; no mic)
- **Depend on the phone:** Virtualizer, reverb presets, loudness enhancer (Android's older built-in effects)
- Per-effect bypass, global bypass, A/B compare, undo/redo
- *Later, only if capture mode is ever added:* convolution/IR (HeSuVi), crossfeed, exciter, stereo width, delay

### 3.2 Devices / outputs
- Auto-detects USB-C DAC, wired 3.5 mm, Bluetooth (A2DP / LE Audio), speaker, HDMI/dock
- Shows device name, supported sample rates/formats, and whether bit-perfect is supported (Android 14+), with a warning that bit-perfect bypasses EQ
- **Setups:** the phone can't tell which IEM is plugged *into* a DAC, so a profile saves combos like "DAC + Salnotes Zero 2"; the last one used per DAC loads automatically; a quick switcher changes earphones in one tap
- Per-device auto-switching (< 1 s on plug/unplug); effects off for the speaker unless enabled
- Rename devices, add icons, group them
- Per-app presets where possible (via the session broadcast's package name)

### 3.3 User profiles & presets
- Multiple user profiles on one phone (e.g. "Adi", "Guest"), each with its own presets, favourites, setups, device links and hearing settings
- Switch profile from the app, Quick Settings tile or notification
- Preset library: favourites, tags, search, duplicate, version history, A/B slots
- Per-profile export/import, full backup/restore (zip), optional auto-backup folder (works with a Drive/Syncthing folder via the Android file picker, no internet needed)

### 3.4 Import / export
- **Import:**
  - AutoEQ `ParametricEQ.txt` / Equalizer APO `config.txt` (e.g. `7Hz Salnotes Zero 2 ParametricEq.txt`)
  - squig.link / REW filter exports
  - **Easy Effects preset JSON** (EQ, compressor, limiter sections)
  - GraphicEQ strings
  - frequency-response CSVs (measurements)
  - target-curve CSVs
  - shared presets via file / QR
- "Open with" support for `.txt` / `.json` / `.csv` from any file manager or chat app
- **Export:** native JSON, Equalizer APO txt, Easy Effects JSON, QR code

### 3.5 Offline database (bundled, no internet)
- **AutoEQ headphone/IEM database:** thousands of models (oratory1990, crinacle, Rtings, Innerfidelity, …), searchable, applied in one tap
- **Measurements + target curves**, tagged by measurement rig (IEC 711 / B&K 5128 / GRAS); only matching pairs are offered
- **On-device EQ generator** (AutoEQ algorithm ported):
  1. pick headphone + target
  2. adjust bass boost, tilt, max filters, max gain
  3. generates an editable parametric EQ
- **Target builder:** start from Harman / DF, adjust bass shelf, treble tilt and ear gain, then save/share
- Database updates come with app updates or an imported database file (the app has no internet permission)

### 3.6 Tunings

**Target curves (need a headphone measurement → EQ is computed)**
- Famous:
  - Harman over-ear 2013/2015/2018
  - Harman in-ear 2016/2017/2019
  - "no bass" variants
  - Diffuse field
  - Free field
  - B&K 1974 tilt
  - Etymotic-style neutral
- Community/niche:
  - Crinacle IEF Neutral
  - Moondrop VDSF
  - SoundGuys Headphone Preference Curve
  - Rtings target
  - squig.link reviewer targets (Super* Review, Precogvision, Antdroid, Gizaudio, …)
  - B&K 5128-based targets

**Sound signature presets (work on any headphone, strength slider)**
- Neutral/flat, V-shape, U-shape, warm/smooth, bright/detailed, bass head, vocal forward, dark/relaxed
- Loudness, anti-sibilance (6–8 kHz cut), studio/monitor
- Genre presets: rock, EDM, classical, podcast/speech

**How they get into the app**

| Source | Method | Notes |
|---|---|---|
| AutoEQ targets (Harman, DF, …) | Bundled | MIT license |
| AutoEQ measurements + precomputed EQs | Bundled | Raw measurement data belongs to each measurer; check before publishing |
| Signature presets | Built by us | Simple curves, no licensing issues |
| Crinacle / Moondrop / squig.link reviewer targets | User imports CSV | Don't ship without permission |
| Custom targets | Target builder | Saved and shareable |
| User's own measurements | Import FR CSV | EQ computed for any target |

### 3.7 Fixes for common audiophile complaints
| Complaint | Answer |
|---|---|
| Ads, weekly subscriptions | No ads, open source |
| "Sketchy" capture apps | No capture, **no INTERNET permission**, open source |
| Doesn't work with app X | Global session + per-app sessions + Shizuku detection + in-app compatibility list |
| Clipping from boosts | Auto-preamp, limiter, clip meter |
| Settings lost when switching headphones | Per-device + per-setup auto profiles |
| Latency / lip-sync | Built-in effects engine adds no latency |
| App killed in the background | Foreground service, start on boot, battery-optimization guide (dontkillmyapp) |
| Conflicts with other EQ apps | Detects when another app holds the effect (`hasControl() == false`) and tells the user |
| 48 kHz resampling / bit-perfect confusion | Device info screen that explains the real output path |
| Coarse volume steps | Fine volume stage |

### 3.8 Background behaviour (Easy Effects style)
- Starts on boot, survives swipe-away, Quick Settings tile, optional home-screen widget
- Silent low-priority notification (required by Android; hideable on Android 13+)
- Battery-optimization exemption prompt with a per-manufacturer guide

---

## 3.9 UI / design language ("modern circuit")
- **Look:** near-black background, warm beige elements (retro amp colours), kept subtle; optional faint amber for "signal active".
- **Fonts:** headings and stage labels in **Cinzel** (engraved amp front-panel lettering); Archivo for UI text; IBM Plex Mono for values.
- **Layout:** effect pills sit in a 2-column grid, never more than 2 per line; a line with a single pill (Gate, Limiter) centres it. Wires run straight from one connected pill to the next (left → right, then down); when a pill is off, the wire runs straight behind it down its column, changing column only in the gap just above the next connected pill. Spacing is tight.
- **Mockup:** `mockup/home.html` (published as a private artifact).
- **Home = signal chain drawn as connected nodes**, top → bottom, PCB-trace-style connectors (straight lines with 45° bends).
  - **Top pill:** the current output, e.g. the DAC, or DAC + IEM as one combined pill. Shows a device image.
  - **Effect pills below it**, in the order audio actually flows through them.
  - **Off:** dotted outline, not connected. **On:** outline becomes solid, a trace animates in, and it joins the chain.
  - **Side-by-side pills** = effects merged into the same stage (see the engine mapping below).
  - Optional: a subtle signal pulse along the traces while music is playing (playback state needs no permission).
- **Gestures:** swipe to change presets and user profiles; swipe on the device pill to change IEM/setup. Avoid the screen edges, where Android's back gesture lives.
- **Device images:** the app has no INTERNET permission, so images can't be downloaded. Use bundled beige line-art per category (USB dongle DAC, IEM, over-ear, TWS, speaker, car) + an optional user photo from the gallery.
- **Engine reality behind the chain:** `DynamicsProcessing` has a fixed internal order: input gain → pre-EQ → multiband compressor → post-EQ → limiter. So:
  - Row "Input": preamp + balance (side by side, both are gain)
  - Row "Tone": parametric EQ + bass tuner + tilt + loudness + signature (side by side, all mathematically summed into one EQ curve)
  - Row "Dynamics": compressor / multiband / gate
  - Optional "Post-tone" row: the EQ can be dragged to after the compressor (it uses the post-EQ slot)
  - Row "Limiter": always last
  - Virtualizer / reverb: separate Android effects; their order is decided by Android, so show them as a final row
  - Reordering is only allowed where the engine really supports it, so the drawing always matches what you hear.
- **Compose implementation:**
  - pills are composables;
  - traces are drawn on Canvas (Path + PathMeasure for the animated "connect" draw-in);
  - dotted → solid uses an animated `PathEffect.dashPathEffect`;
  - drag-to-reorder with snap to valid slots;
  - `HorizontalPager` for presets/profiles;
  - custom Material 3 colour scheme (black/beige).

## 4. Tech stack (Android only)

| Area | Choice |
|---|---|
| Language | **Kotlin 2.x** |
| UI | **Jetpack Compose + Material 3**, Navigation Compose; EQ graph drawn on Compose Canvas (log axis, draggable filter points) |
| Architecture | MVVM, ViewModel + StateFlow, Coroutines/Flow, manual DI (repository singleton) |
| Audio engine | `DynamicsProcessing`, plus optional `Virtualizer`, `PresetReverb`, `LoudnessEnhancer` |
| Background | Foreground service, start on boot, Quick Settings tile |
| Device detection | `AudioDeviceCallback` + `AudioDeviceInfo` |
| App coverage | Effect-session broadcast receiver + session 0 + optional **Shizuku** |
| DSP math | Pure Kotlin (filter math, curve calculation, band conversion, loudness curves) |
| AutoEQ generator | Kotlin + **Apache Commons Math** (Levenberg–Marquardt optimizer) |
| User data | **Room (SQLite)**: profiles, presets, setups, device links, history |
| Headphone/target catalog | Prebuilt read-only **SQLite** with full-text search (FTS), bundled |
| Settings | DataStore |
| Preset formats | **kotlinx.serialization** (own JSON, Easy Effects JSON); hand-written APO/AutoEQ/CSV parsers |
| Import/export | Android file picker (SAF), "Open with" intent filters, share sheet, optional ZXing QR |
| Catalog builder | **Python** script (runs on Arch): AutoEQ repo → `catalog.db` |
| Testing | JUnit5, Turbine, scipy reference curves, Compose UI tests |
| Build/quality | Gradle Kotlin DSL + version catalog, ktlint/detekt, R8, GitHub Actions |
| SDK | minSdk 28 (Android 9), target the latest SDK; **no INTERNET permission** |
| Later (optional) | C++ (NDK) + Oboe, only if capture mode / convolution / crossfeed is added |

### Project structure
```
eq/
├─ app/              Compose UI, navigation, Quick Settings tile, widget
├─ core/model/       Preset, Filter, Target, Profile, Setup (pure Kotlin)
├─ core/dsp/         filter math, band conversion, loudness, AutoEQ optimizer (pure Kotlin)
├─ core/data/        Room DB, catalog DB, importers/exporters, repositories
├─ engine/           foreground service, DynamicsProcessing controller, device monitor, profile switching
└─ tools/catalog/    Python: AutoEQ repo → catalog.db
```
`core/model` and `core/dsp` contain no Android code, so their tests run on the desktop without a phone or emulator.

---

## 5. Build order
0. **Spikes on the real phone:** does session 0 work? What's the max DynamicsProcessing band count? Does Spotify send session broadcasts?
1. **Core math + importers:** APO/AutoEQ/Easy Effects parsers → filters → curve → bands; tested with the Salnotes file
2. **Engine:** foreground service, boot, tile, device detection, per-device presets
3. **Data:** profiles, setups, preset library, bundled AutoEQ catalog + targets, backup
4. **EQ generator + target builder + signature presets**
5. **UI:** per the user's design language (to be provided)
6. **Hardening:** Shizuku, compatibility list, conflict detection, battery, bit-perfect info

## 6. Verification
- Unit tests:
  - filter math vs scipy reference (±0.1 dB)
  - band-conversion error < 0.5 dB at 20 Hz–20 kHz
  - every importer/exporter tested both ways with real files
- On device: play a sine sweep through the USB DAC, measure in REW on the Arch PC, and compare with the same preset in Easy Effects
- Compatibility matrix: Spotify, YouTube, YouTube Music, Tidal, Qobuz, Poweramp, Chrome
- 8-hour playback battery soak; reboot auto-start; hot-swap wired ↔ USB ↔ Bluetooth switches profiles in < 1 s

## 7. Open items
- [x] UI / design language: node-chain "modern circuit", black + beige (section 3.9)
- [ ] Decide exact swipe mapping (presets vs profiles vs setups) and screen details for the effect editors
- [ ] Test session 0 + band count on the user's phone
- [ ] Licensing check before bundling raw measurement data (for public release)

## 8. Sources
- RootlessJamesDSP: https://github.com/timschneeb/RootlessJamesDSP
- Why Android EQ apps are inconsistent: https://www.esper.io/blog/android-equalizer-apps-inconsistent
- DynamicsProcessing API: https://developer.android.com/reference/android/media/audiofx/DynamicsProcessing
- Android 14 bit-perfect USB: https://source.android.com/docs/core/audio/preferred-mixer-attr
- Android resampling complaints: https://www.headphonesty.com/2025/10/android-devices-lossless-streams-spotify-tidal-qobuz/
- Wavelet features: https://www.xda-developers.com/make-your-headphones-sound-better-automatic-eq-wavelet/
- Easy Effects: https://github.com/wwmm/easyeffects
- AutoEQ: https://github.com/jaakkopasanen/AutoEq
- Note: Reddit/X couldn't be fetched directly; community complaints were taken from articles, forums and app trackers that summarise them.
