<p align="center"><img src="docs/banner.png" alt="HackerTube TV" width="720"></p>

# HackerTube TV

**[hackertube.org](https://hackertube.org) on your Fire TV / Android TV.** HackerTube is a curated index of the YouTube videos Hacker News keeps telling people to watch. This app is a small fork of [SmartTube](https://github.com/yuliskov/SmartTube) that adds HackerTube to SmartTube's TV interface.

| Sidebar section | What's in it |
|---|---|
| **HackerTube** (opens on launch) | *Picked for you*, *Recently recommended on HN*, *Hidden gems*, *Evergreen classics*, *Talks*, *Lectures*, *Documentaries*, *Over 1 hour*, *Under 4 minutes* |
| **HN Topics** | One row per HackerTube topic area, best first |
| **HN Playlists** | The playlists curated on hackertube.org, in their intended order |
| **Search** | HackerTube matches appear as the first row, then normal YouTube results |

While a HackerTube video plays, the first suggestions row is **Up next from HackerTube** (related videos from hackertube.org), and autoplay picks the first one you haven't watched. *Picked for you* skips videos you've already watched. Everything else is SmartTube as usual.

It installs as its own app (`org.hackertube.tv`), next to SmartTube if you have it.

---

## Install on Fire TV

1. **Developer options:** *Settings → My Fire TV → About*, click the device name 7 times.
2. **Install Downloader** (by AFTVnews) from the Fire TV Appstore.
3. **Allow it:** *Settings → My Fire TV → Developer options → Install unknown apps* → turn on **Downloader**.
4. In Downloader, open:

   ```
   https://github.com/shreyasudupi/HackerTube-TV/releases/download/latest/HackerTube.apk
   ```

   Install, then open **HackerTube** from *Your Apps & Channels*.

**Updates:** the app checks this repo's stable releases. The first time it offers an update, allow *HackerTube* to install unknown apps; after that updates install in place.

Other downloads on the [latest release](../../releases/tag/latest): `HackerTube_arm64-v8a.apk`, `HackerTube_universal.apk` (any device), `HackerTube_x86-emulator.apk` (Android TV emulator). Android TV / Google TV: the same APK works; sideload with Downloader or `adb install HackerTube.apk`.

### Beta
Want to try changes before they ship? Install **HackerTube Beta**. It's a separate app (`org.hackertube.tv.beta`) that sits next to the stable one and updates from the beta channel:

```
https://github.com/shreyasudupi/HackerTube-TV/releases/download/beta/HackerTube-Beta.apk
```

---

## How releases work

```
push / weekly SmartTube sync
        │
        ▼
  Build (stable + beta APKs, signed)
        │
        ▼
  Smoke test on an Android TV emulator ── fail ──▶ nothing ships
        │ pass
        ▼
  Beta channel  (HackerTube Beta + a versioned pre-release)
        │  test on a real Fire TV
        ▼
  "Promote beta to stable" (manual, Actions tab)
        │  copies the exact tested APKs, refuses to go backwards
        ▼
  Stable channel  ('latest' release: the link above and in-app updates)
```

- **Smoke test** (`tests/smoke-test.sh`): installs the app on an Android TV emulator, checks that HackerTube rows load from hackertube.org, opens a video, browses rows, and fails on any crash. Screenshots and logs are attached to every run.
- **Weekly sync** (`sync-upstream.yml`): every Monday it moves to the newest SmartTube if the HackerTube patch still applies. Like any change, it lands in beta first.
- **Promote** (`promote.yml`): *Actions → Promote beta to stable → Run workflow*. Leave the version empty to promote the newest beta.

## Repo layout

This repo doesn't vendor SmartTube's source. It pins an upstream commit and layers HackerTube on top:

```
UPSTREAM_COMMIT                         SmartTube commit the app is built from
overlay/                                new files copied into the SmartTube tree
  common/.../hackertube/                  HackerTubeService (API client), HtMediaGroup, HtMediaItem
  */src/sthackertube*/                    stable and beta flavors: name, icons, update channel
patches/0001-hackertube-sections.patch  small edits to upstream files (sidebar, browse, search, player)
scripts/prepare.sh                      upstream @ commit + submodules, overlay, patch
tests/smoke-test.sh                     emulator smoke test
.github/workflows/                      build/test/beta, promote, weekly upstream sync
```

Build locally (JDK 17 + Android SDK):
```bash
HACKERTUBE_REPO=owner/repo scripts/prepare.sh   # creates build/SmartTube
cd build/SmartTube && ./gradlew assembleSthackertubeRelease
```

Release signing uses repository secrets (`SIGNING_KEY`, `KEY_STORE_PASSWORD`, `ALIAS`, `KEY_PASSWORD`). No keys are stored in the repo.

## Privacy

HackerTube TV adds no analytics, tracking or accounts. On top of what SmartTube itself does, it talks only to hackertube.org:

- To load rows, playlists, search and related videos, it calls hackertube.org's public JSON API (the same one the website uses).
- For *Picked for you*, it sends the IDs of up to 150 videos you recently watched, so the feed can skip them. This is the same "seen" list the website keeps in your browser. Only YouTube video IDs are sent, nothing else about you or your device. Your watch history otherwise stays on the device.

## Credits & license

- [SmartTube](https://github.com/yuliskov/SmartTube) by Yuriy Liskov and contributors (MIT): the player, UI and YouTube integration.
- [HackerTube](https://hackertube.org): the curation, data and API.
- MIT licensed (see `LICENSE`). Not affiliated with SmartTube, HackerTube, Hacker News, Y Combinator or Google/YouTube.
