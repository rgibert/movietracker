# Movie Tracker

A small, free Trakt client for keeping up with **movies only** — the movie
companion to [TV Tracker](https://github.com/rgibert/tvtracker).

## What it does

- **To Watch** – movies on your Trakt watchlist that are out now, in your
  watchlist's order. Each one shows where to stream it (Netflix, Max, Prime
  Video…) with a brand badge, or "Rent or buy" with the stores when it isn't
  included anywhere. Films still in cinemas show **In theatres** with their
  digital date when one is known. Tap the bookmark to remove a movie from the
  watchlist (with undo).
- **Coming Up** – watchlist movies that aren't out yet, as a list grouped by
  day or a month calendar. Each release window gets its own entry —
  theatrical, digital, Blu-ray & DVD and TV — using the dates for your region,
  and movies Trakt has no date for yet are listed under "Date to be announced".
- **Diary** – your watch history, newest first and grouped by month, with your
  rating for each movie.
- **Movie page** – overview, rating on Trakt, release dates for your region,
  every streaming/free/rent/buy option, and:
  - **Add to / remove from watchlist**
  - **Log a watch** – pick the date, add a rating and an optional review in
    one go (Trakt stores these as a history play, a rating and a comment)
  - **Rate** with half stars (Trakt's 1–10 scale)
  - **Write and delete reviews**, with spoiler marking
  - **Remove diary entries**
  - **Pin where you'll watch it** – for a service TMDB doesn't list, or your
    own Plex server. A pinned service shows on To Watch and counts the movie
    as available.
- **Add movies** – search Trakt from the 🔍 button and add results to your
  watchlist in one tap.
- **Offline-friendly** – lists, the diary and movie pages are cached on the
  device and shown with a "saved data" banner when Trakt can't be reached.
- **Light on the APIs** – release dates and streaming options are cached per
  movie (3 days and 1 day), films from two or more years ago skip the
  release-date lookup, and lookups run a few at a time.

### Why Trakt (and not Letterboxd)

Letterboxd's API is private and invite-only, so the app uses Trakt, which has
a public API covering everything above: watchlists, history (the diary),
ratings, comments (reviews), search and per-country release dates.

Trakt's own "where to watch" endpoint is limited access, so streaming
services come from [TMDB](https://www.themoviedb.org)'s free watch-provider
API (JustWatch data). That part is optional: without a TMDB key the app
still works and you pin services by hand.

## First run

If the app was built with a Trakt **Client ID** (see
[Build](#build)), it skips API setup and opens straight on the Trakt
connection step.

1. Open Movie Tracker and tap **Connect with Trakt**.
2. Enter the short code shown in the app at the Trakt activation page, and
   approve the app.
3. Your watchlist loads automatically.

Release dates and streaming services use your device's country; change it
under **Settings → Region**.

You can reuse the same Trakt API app as TV Tracker, or create one at
<https://app.trakt.tv/settings/apps/api/new>:

- **Name:** anything, for example `Movie Tracker`.
- **Redirect URI:** `https://example.com/oauth/callback` (or any HTTPS URL you
  control). The app uses Trakt's device-code flow, so this URI is never
  called.
- Copy the **Client ID**. A client secret is not needed.

## Build

Requirements: JDK 17+ and an Android SDK with platform 35.

```sh
./gradlew :app:assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:installDebug         # install on a device/emulator
./gradlew :app:testDebugUnitTest :app:lintDebug
```

### Build-time values

All are optional and read in this order: Gradle property → environment
variable → `local.properties` (gitignored).

| Value | Gradle property | Env var | `local.properties` | Without it |
| --- | --- | --- | --- | --- |
| Trakt Client ID | `-PtraktClientId` | `TRAKT_CLIENT_ID` | `trakt.clientId` | The app asks for a Client ID on first launch |
| TMDB API key (v3) | `-PtmdbApiKey` | `TMDB_API_KEY` | `tmdb.apiKey` | No streaming providers; pin services by hand |
| Sentry DSN | `-PsentryDsn` | `SENTRY_DSN` | `sentry.dsn` | Sentry is never initialised |

The Trakt Client ID and the TMDB key are compiled into `BuildConfig`. Both
are app identifiers that ship inside the APK anyway, not secrets in the strict
sense, but keeping them out of Git means each build can use its own.

Get a TMDB key by creating a free account, then **Settings → API → Create**
at <https://www.themoviedb.org/settings/api>. Use the **API Key** (v3), not
the read access token. Per TMDB's terms the app credits TMDB under
**Settings → About**.

Sentry events are tagged with the build type as the environment and
`ca.gibert.movietracker@<versionName>+<versionCode>` as the release; default
PII is not sent.

### GitHub Actions

`.github/workflows/build.yml` matches TV Tracker's: it runs for pull requests
against `main` and when a version tag (`v*`) is pushed. Every build runs the
unit tests and lint and uploads the debug APK. Pull request builds comment a
link to the `movie-tracker-debug` artifact on the PR. Tag builds also build
the release APK and publish both on a GitHub **Release**.

The tag drives the version: `v1.2.3` becomes `versionName` `1.2.3` and
`versionCode` `1002003`. Pull request builds use the short commit SHA and the
run number.

#### Repository secrets

Add these under **Settings → Secrets and variables → Actions → Secrets**. All
are optional; the build succeeds without any of them.

| Secret | Used for | Without it |
| --- | --- | --- |
| `TRAKT_CLIENT_ID` | Bundled Trakt Client ID | APK asks for a Client ID on first launch |
| `TMDB_API_KEY` | Streaming providers (TMDB v3 API key) | No "where to watch" data |
| `SENTRY_DSN` | Crash reporting | Sentry disabled |
| `RELEASE_KEYSTORE_BASE64` | Release signing: base64-encoded keystore | Release APK is unsigned |
| `RELEASE_KEYSTORE_PASSWORD` | Release signing: keystore password | |
| `RELEASE_KEY_ALIAS` | Release signing: key alias | |
| `RELEASE_KEY_PASSWORD` | Release signing: key password | |

No repository *variables* are needed, and the workflow's built-in
`GITHUB_TOKEN` covers publishing releases and PR comments (the workflow asks
for `contents: write` and `pull-requests: write`).

To create a signing keystore once:

```sh
keytool -genkeypair -v \
  -keystore release.keystore \
  -alias movietracker \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Movie Tracker" \
  -storepass 'your-password' \
  -keypass 'your-password'
```

```sh
base64 -i release.keystore | pbcopy   # macOS; on Linux: base64 -w0 release.keystore
```

Keep `release.keystore` backed up and out of Git (it is ignored); losing it
prevents updating an installed release build. Then publish a release:

```sh
git tag v1.0.0
git push origin v1.0.0
```

The release assets are named `movietracker-<version>-debug.apk` and
`movietracker-<version>.apk` (`-unsigned.apk` without signing secrets).

To build a signed release locally:

```sh
RELEASE_KEYSTORE_FILE="$PWD/release.keystore" \
RELEASE_KEYSTORE_PASSWORD='your-password' \
RELEASE_KEY_ALIAS=movietracker \
RELEASE_KEY_PASSWORD='your-password' \
./gradlew :app:assembleRelease
```

## Notes

- Package name: `ca.gibert.movietracker`. Minimum Android version: 8.0 (API 26).
- Tokens live in app-private DataStore storage; snapshots are plain JSON
  files in app-private storage. Signing out clears both.
- Trakt only allows deleting a comment within two weeks of posting it, and
  needs comments to be at least five words; anything over 200 words is posted
  as a review.
- This is an unofficial app and is not affiliated with Trakt, TMDB, JustWatch
  or Letterboxd.
