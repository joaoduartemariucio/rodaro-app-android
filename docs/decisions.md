# Decisions

Short record of choices that are not obvious from the code. Each one says what was decided,
why, and what it costs. Newest first within each group. Product decisions are shared with
[kmais-app-ios](https://github.com/joaoduartemariucio/kmais-app-ios); entries marked
**Android** record where this app had to differ.

## Platform parity

### Android: privacy controls (LGPD)

Result of an LGPD audit (2026-11-06). The privacy policy v2 describes all of it.

- **Statistics are opt-out, personalized ads are opt-in.** `Preferences.usageStatisticsEnabled`
  (default on) switches Analytics, Crashlytics and Performance together; the legal basis is
  legitimate interest, which needs a way to object (LGPD art. 18, § 2º) but not prior consent.
  `Preferences.personalizedAdsEnabled` (default off) is consent (art. 7º, I): until it is on,
  every ad request goes through `AdMobGate.request()`, which adds `npa=1` so AdMob serves
  non-personalized ads. Ads themselves stay on — they keep the app free. Analytics Consent Mode
  follows the same two switches: `ANALYTICS_STORAGE` with statistics, `AD_STORAGE`,
  `AD_USER_DATA` and `AD_PERSONALIZATION` only with personalized ads.
- **Applied in `app/PrivacySettings`**, at start-up (`FirebaseSetup`, from `PreferenceGate`) and
  whenever either switch changes (`PreferencesViewModel.applyPrivacy`). Firebase persists the
  collection flags, so the choice also holds before `FirebaseSetup` runs on the next launch.
- **Why not Google's UMP consent form:** its message types target GDPR and US-state laws; the
  in-app switch is the same choice, in the app's own settings, with nothing to configure in the
  AdMob console.
- **Deleting data works without an account.** Profile shows "Apagar dados deste aparelho" when
  nobody is signed in. `clearLocalData` cancels WorkManager, drops notifications,
  `clearAllTables()`, clears the preferences (keeping only the statistics choice, so an objection
  survives the wipe; personalized ads go back to off), and resets the Firebase identifiers —
  Analytics data, unsent crash reports, the FCM token and the Firebase Installation id — so
  nothing collected before can be linked to what comes after. A failed reset (offline) is logged
  and does not block the deletion.
- **`AdMobGate` starts disabled**, like the `enableAdMob` Remote Config default, so no ad loads
  before the flag is known on a first launch.
- **Still sent twice:** the OpenRouteService key goes in the `Authorization` header and the
  `api_key` query parameter. The query parameter was added on purpose with the trip cost feature;
  drop it only after checking that every ORS endpoint the app calls accepts the header alone.
- **Cost:** two more switches to keep honest with the policy; the copy in
  `values*/strings.xml` and `privacidade.html` must change together.

### Android: Firebase Authentication and account deletion

The account is **optional**: every screen works without one, and sign-in lives in the Profile tab
(`presentation/profile/navigation/ProfileRoute.Auth` → `presentation/auth/`).

- **Providers:** e-mail/password and Google. Google goes through Credential Manager
  (`androidx.credentials` 1.6.0 + `credentials-play-services-auth`, `googleid` 1.1.0 — the version\n  the credentials provider itself depends on; 1.2.x ships Kotlin 2.4 metadata that Kotlin 2.2\n  cannot read) instead of
  the deprecated `GoogleSignInClient`. The id token is exchanged for a Firebase credential inside
  `FirebaseAccountService` (Data), so Presentation only ever sees the Domain `AccountService`.
- **Needs a Web client id:** `default_web_client_id`, which the google-services plugin only
  generates when `google-services.json` holds a type-3 oauth client (Firebase console →
  Authentication → sign-in method → Google). Until then `signInWithGoogle` throws
  `AccountError.GoogleNotConfigured` and the screen explains it instead of crashing.
- **Errors** are mapped from Firebase codes in `data/common/services/AuthErrors.kt`
  (`accountError`, accepting codes with and without the `ERROR_` prefix) so the mapping stays plain
  Kotlin with unit tests; the SDK glue itself is coverage-ignored.
- **Deleting from the app:** Profile → "Excluir conta e dados" calls `currentUser.delete()`.
  `REQUIRES_RECENT_LOGIN` opens a confirm step — the password for e-mail accounts, a second Google
  sign-in for Google ones — instead of failing, and only after the account is gone does
  `AppDependencies.clearLocalData` run (see "Android: privacy controls (LGPD)").
  The privacy site teaches the same steps (`hosting/public/privacidade.html`, section 13).
- **Why optional:** the app is local-first; a mandatory login would lock people out of what
  already works offline, and neither Analytics nor AdMob needs an account.

### Android: Firebase and AdMob

Firebase project `rodaro` (`app/google-services.json`), versions pinned in the catalog: BoM
34.19.0 rather than the brand-new 35.x line, google-services 4.5.0, Crashlytics plugin 3.0.8,
Performance plugin 2.0.2, `play-services-ads` 25.5.0.

- **Start-up:** `app/FirebaseSetup` runs in `RodaroApplication.onCreate`. If no Firebase app can
  be created (Robolectric tests), it does nothing.
- **Crashlytics** collects only in non-debuggable builds, so local crashes do not pollute the
  dashboard. **Performance** is automatic through its plugin.
- **Analytics** goes through the Domain `Analytics` interface (`FirebaseAnalyticsTracker` in
  Data, `NoAnalytics` for previews and tests). `AppNavHost` logs a `screen_view` named after the
  top route each time it changes, so every screen is tracked without touching the screens.
- **Remote Config** loads `res/xml/remote_config_defaults.xml` (empty for now) and calls
  `fetchAndActivate` on start-up. Keys are added together with their first use.
- **Push:** `data/push/services/PushMessagingService` shows notification and data messages
  (`title`/`body`) on the `general` channel ("News and alerts") with the app's small icon. The
  manifest sets that icon and channel as FCM defaults for messages that arrive in the background.
  Nothing is sent to a server: the token stays inside FCM until a backend needs it, so
  `onNewToken` is an empty override that exists only because Lint requires the callback.
- **AdMob** (monetization; Google Ads would be the opposite side, paying for installs): the
  SDK starts on a background thread, but `InterstitialAd.load` and `AppOpenAd.load` must run on
  the main thread, so `FirebaseSetup` hops back to `Dispatchers.Main` before preparing them —
  calling them from `Dispatchers.IO` crashes the app at start-up. The inventory is one adaptive
  `AdBanner` at the end of the
  Route tab and at the end of every form dialog, a native advanced card (`AdNative`) at the end of
  the vehicle list, an interstitial after saving a fuel-up or a service, creating a vehicle or
  finishing a route calculation, and an app-open ad on launch. Banners stay out of the fields and
  interstitials only fire when a task has just completed, which is what keeps them from costing
  store rating.
- **AdMob ids per build type** (`resValue` in `app/build.gradle.kts`): debug always uses
  Google's test ids, so nobody sees or clicks a real ad while developing, which is the main
  reason AdMob accounts get suspended. Release uses the real app id
  `ca-app-pub-1068825778444539~3367882368` and the units banner `…/5512371965`, interstitial
  `…/8484477679`, native advanced `…/3466673450` and app open `…/6672702556`.
- **`AD_ID` permission:** declared in the manifest. Apps targeting Android 13+ that carry an ad
  unit must declare `com.google.android.gms.permission.AD_ID`, otherwise the Advertising ID comes
  back as all zeroes.
- **app-ads.txt:** served by Firebase Hosting of the same project, at
  `https://rodaro.web.app/app-ads.txt` (`hosting/public/`, `firebase.json`). That domain is the
  developer website on the Play Store listing, so AdMob can verify the publisher
  `pub-1068825778444539`. Deploy with `firebase deploy --only hosting`.
- **Privacy policy:** `https://rodaro.web.app/privacidade` (`hosting/public/privacidade.html`, in
  pt-BR). It lists what stays on the device, what each Google service collects, the Android
  backup, permissions and LGPD rights. It must change whenever a feature, SDK or permission
  changes what is collected. Contact: the developer's e-mail.
- **Cost:** `google-services.json` must exist for every build, CI included. It identifies the
  Firebase project and is not a secret, but it carries an API key, and this repository is public,
  so it is gitignored and `.github/workflows/ci.yml` recreates it from the `GOOGLE_SERVICES_JSON`
  secret (`gh secret set GOOGLE_SERVICES_JSON < app/google-services.json`). That key should also
  stay restricted to this app in the Google Cloud console.
- **`fragment-ktx` 1.8.9 is a direct dependency** even though the app has no Fragments:
  `registerForActivityResult` in `MainActivity` makes Lint check the *declared* fragment version,
  and the 1.1.0 that a transitive brings in fails `InvalidFragmentVersionForActivityResult` even
  though Gradle resolves it to something newer. Declaring it once, explicitly, is cheaper than
  explaining the false positive forever.

### Android: the app is Rodaro, `br.com.joaoduartemariucio.rodaro`

The app was renamed from KMais to Rodaro. The package, `namespace` and `applicationId` all became
`br.com.joaoduartemariucio.rodaro`, and the `Kmais…` types became `Rodaro…` (`RodaroDatabase`,
`RodaroApplication`, `RodaroTheme`, `Theme.Rodaro`). With no `_` in the package, ktlint's
`package-name` rule is back on.

- **Cost:** a new `applicationId` is a new app for Android and the Play Store. An install of the
  old id keeps its data and is not upgraded; the database file is now `rodaro.db`.
- **Not renamed:** the repository (`kmais-app-android`, `rootProject.name`) and the links to
  `kmais-app-ios`, which follow the GitHub repository names.

### Android: launcher icon from a bitmap

The icon is the teal "R" with a road on `#0E131D`. The adaptive icon uses PNG layers per
density (`mipmap-*/ic_launcher_foreground.png`, `ic_launcher_monochrome.png` for themed icons)
over the `ic_launcher_background` color, with the logo inside the 66 dp safe zone. The
notification small icon (`drawable-*/ic_notification.png`) is the same silhouette in white.
Legacy `ic_launcher.webp` files were removed: with `minSdk` 28 (adaptive icons exist since API 26) the adaptive icon is always used.

- **Revisit when:** a vector version of the logo exists — it would replace the PNGs.

### One app, two native codebases, same rules

The Android app is a port of the iOS app, not a reinterpretation: the same screens, the same
Domain rules with the same test cases, the same strings in English and pt-BR, the same agents
and the same CI gates. Platform idioms win only where copying iOS would feel wrong on Android
(sheets become full-screen dialogs, swipe actions and long-press menus replace context menus,
Material 3 components replace SwiftUI's).

- **Why:** the domain is the product; two apps that disagree on when an oil change is due
  would be worse than one app.
- **Cost:** every rule change lands twice. The shared test names make the diff between the two
  test suites the checklist.

### Android: minimum SDK 28 (Android 9)

The app runs on Android 9 and newer. Android Lint (`NewApi`, warnings as errors) is the
check that nothing newer than API 28 is called without a version guard. Three spots needed one:

- **Notification permission:** `POST_NOTIFICATIONS` only exists from Android 13 (API 33).
  Below it, `NotificationPermission` counts as granted while
  `NotificationManagerCompat.areNotificationsEnabled()` is true and never asks, because there
  is no runtime dialog to show; `MainActivity` answers "denied" to a request on those versions.
- **App language:** see "the app language goes through the platform `LocaleManager`".
- **Map links:** `URLEncoder.encode(String, Charset)` is API 33, so the charset is passed by name.

- **Why:** an API 36 minimum shut out every phone not yet on Android 16, for
  three small compatibility spots.
- **Cost:** AppCompat as a dependency and its theme on `MainActivity`; two code paths in
  `NotificationPermission` and `LocaleLanguageApplier`, each tested on API 28 and 33+ with
  Robolectric.
- **Revisit when:** Play Console shows almost no installs below Android 13; then raising the
  minimum to 33 removes both paths.

## Preferences

### Currency changes the format, never the value

Rodaro is offline: the preferred currency (`CurrencyOption`) only changes the symbol and
formatting applied by `Formats.currency`. No exchange rate is fetched, stored or implied, and
the Preferences screen states so next to the picker (`pref_currency_note`). Values keep being
stored exactly as entered — converting them would need a network the app does not have and
would silently rewrite the user's history.

### Units are canonically metric, imperial only at the edges

Everything in Domain and Room stays in km, liters, km/L and cost/km. The preferred unit system
(`UnitSystem`) converts in exactly two places: the `formattedX` extensions in `FormatStyles.kt`
for display, and the four form ViewModels (`FuelEntryFormViewModel`, `VehicleFormViewModel`,
`ServiceEntryFormViewModel`, `TripCostViewModel`) which convert what the user types back to
metric before validating or saving. `presentation/common/DisplayPreferences.kt` holds the
preference as Compose state so the ~40 display call sites need no per-file changes. Validation,
Room and every calculation therefore keep seeing one unit system only.

### Preferences live in SharedPreferences, not Room

`Preferences` is five scalars, read at start-up, by `PushMessagingService` and by
`ReminderWorker` — types the system instantiates outside any screen. A SharedPreferences file
(`data/preferences/SharedPreferencesPreferencesRepository`) is the idiomatic home for settings,
adds no schema migration, and `data/preferences/PreferenceGate` lets those system-instantiated
types read it from a plain `Context` without a repository or a coroutine graph.

### Two independent notification switches

"Push" (`pushEnabled`) gates the remote FCM channel (`PushMessagingService` drops messages when
off) and "Maintenance reminders" (`remindersEnabled`) gates local WorkManager jobs
(`ServiceReminderSync` cancels every pending reminder and `ReminderWorker` returns early when
off). One combined switch would force users who want local oil-change alerts to also accept
marketing push, or the opposite.

### Android: the app language goes through the platform `LocaleManager`

On Android 13 and newer, `LocaleManager.setApplicationLocales` is the native per-app language
API: the system persists the choice, recreates the Activity and updates the resources, so the
app keeps no duplicate language state. `LanguageOption.SYSTEM` clears the override with an
empty `LocaleList`. Below Android 13 `LocaleLanguageApplier` calls
`AppCompatDelegate.setApplicationLocales` instead; that is why `MainActivity` is an
`AppCompatActivity` with an AppCompat theme and the manifest enables
`AppLocalesMetadataHolderService` with `autoStoreLocales`, so AppCompat saves the choice and
reapplies it on the next launch. `android:localeConfig` stays; older versions ignore it.

- **Why not AppCompat on every version:** on Android 13+ `AppCompatDelegate` only reaches
  `LocaleManager` through a live AppCompat activity and silently does nothing without one;
  calling `LocaleManager` directly there keeps the current behavior and its tests.
- **Cost:** two code paths for one setting.

## Data entry

### Odometer out of order is a warning, not an error

When the odometer typed in a fuel-up or service does not fit between the previous and the
next record (by date), the form shows an orange note — "the next record has 56,000 km;
saving this changes the vehicle's history" — and still lets the user save.

- **Why:** people log old receipts, fix typos and register a car after the fact. Blocking
  the save made editing old records impossible.
- **Cost:** the history can hold inconsistent readings. Domain already copes:
  `Consumption.anomalies(records)` flags them and they are left out of consumption stats.

### Numeric fields are digit-shifting masks

Typing fills from the right, like banking apps: `5899` becomes `R$ 5,899`. Each field has
a `NumberInput.Style`:

| Style | Fraction digits | Used for |
|---|---|---|
| `KILOMETERS`, `MONTHS` | 0 | odometer, rule intervals |
| `LITERS` | 2 | liters, tank capacity |
| `FUEL_PRICE` | 3 | price per liter (pumps in Brazil charge in 3 decimals) |
| `MONEY` | 2 | service cost |

- **Why:** a plain number pad is enough (no decimal separator to type or get wrong), the
  value is always shown formatted for the locale, and parsing is just
  `digits / 10^fractionDigits` — identical in any locale.
- **Cost:** the caret always sits at the end; editing in the middle of a number is not
  supported. Deleting a non-digit (a trailing currency symbol) removes the last digit.
- **Android:** the field uses `KeyboardType.NumberPassword`, the only Android keyboard that is
  digits only on every IME; the text itself is never hidden.

### Keyboard closes on tap outside, scroll or "Done"

`Modifier.dismissesKeyboard()` on each form clears focus when the user taps outside a text
field or drags the list; number fields close it with the keyboard's Done key.

- **Why:** Compose has no "tap outside" of its own. A parent gesture that clears focus also
  fires on taps that land on a field, so text inputs mark the gesture with
  `Modifier.keepsKeyboard()` and the parent skips it — the same rule as the iOS recognizer,
  which ignores touches on `UITextField`.
- **Android:** only drags made with a finger clear focus. Focusing a field under the keyboard
  scrolls it into view, and that automatic scroll must not dismiss the keyboard again.

## Backup

### Android Auto Backup is an allowlist

`backup_rules.xml` (Android 9–11) and `data_extraction_rules.xml` (Android 12+, both
`<cloud-backup>` and `<device-transfer>`) include only `rodaro.db`, the `preferences`
SharedPreferences file and AppCompat's language record
(`androidx.appcompat.app.AppCompatDelegate.application_locales_record_file`). Everything else —
Firebase, Auth, AdMob and Analytics state, Crashlytics files, the WorkManager database — stays
out. `BackupRulesTests` checks that the three sections list the same files.

- **Why an allowlist:** the templates that shipped were empty, so Android copied every file in
  the app's data directory, including SDK identifiers and Firebase's sign-in state. Those belong
  to one install; restoring them on another phone is wrong at best. An allowlist also keeps
  whatever a future SDK adds out of the user's Google account by default.
- **No `BackupAgent`:** the app process is stopped while Auto Backup copies files, and the
  platform adds `rodaro.db-wal` and `rodaro.db-journal` on its own whenever a database is
  included, so the database and its write-ahead log travel together and SQLite replays the log on
  the first open. That is why the `-wal` file is not listed explicitly.
- **`restoreAnyVersion` stays false:** Android only restores into the same or a newer app
  version, and Room migrations take an older database forward.
- **Reminders are rebuilt, not restored:** WorkManager jobs are not backed up; the first
  vehicle list `load()` after a restore runs `ServiceReminderSync`, which plans them again.
- **Privacy switches are restored** with the preferences file. That is acceptable because the
  copy lives in the same Google account and the choice was the user's own; the privacy policy
  says so.
- **Cost:** Auto Backup runs only on Wi-Fi, while idle and charging, at most once a day, and the
  quota is 25 MB per app. A user who just switched phones may not have a recent copy — that is
  what the manual backup below is for.

### Manual backup is a versioned JSON file and restoring replaces everything

Profile → Backup and export writes a JSON file through the Storage Access Framework
(`CreateDocument`) and restores one picked with `OpenDocument`. The file is
`{ format: "rodaro.backup", formatVersion: 1, exportedAt, app, vehicles, fuelEntries,
serviceEntries, serviceRules, savedRoutes, preferences }`: money as plain decimal strings,
instants in UTC with milliseconds, lowercase UUIDs (read case-insensitively), enums by their
raw value (`"car"`, `"oilChange"`, `"BRL"`, `"metric"`, `"pt-BR"`, `"system"`) and the route path as
a precision-6 polyline. `JsonBackupCodec` reads it; `BackupValidation` (Domain) rejects files
with repeated ids or records pointing at a vehicle the file does not have; only then does the
screen show what is in the file next to what is on the phone and ask before replacing.

- **Versioning:** `formatVersion` only grows when an old reader would misread a file. Adding an
  optional key does not bump it — unknown keys are ignored and missing ones take their defaults.
  A file with a higher version is refused with "update the app"; each supported version keeps
  its own decoding branch (`when (version)`), so old files keep working forever.
- **Why replace instead of merge:** a merge needs rules for the same vehicle edited on two
  phones, records deleted on one of them and odometers that no longer line up. Replace is easy to
  explain, and the confirmation shows both counts. Everything happens in one Room transaction
  (`BackupDao.replaceAll`), so a failure leaves the old data untouched.
- **What is not imported:** the statistics and personalized-ads switches. They are consent on
  this device; a file from somewhere else must not turn personalized ads on. Currency, units,
  language and notification switches are restored, best-effort after the data commit, and the
  language is applied last because it recreates the activity.
- **Why the ViewModel uses `viewModelScope`:** other screens launch work from
  `rememberCoroutineScope`, which is cancelled when the composable leaves composition. A restore
  must not stop between the commit and the preference and reminder sync, so `BackupViewModel`
  exposes thin `launch…` wrappers over its `suspend` methods; tests call the `suspend` methods
  directly. That scope survives recomposition and activity recreation (a language change, a
  rotation), but not leaving the screen: popping the Navigation 3 entry clears its
  `ViewModelStore`. So while an action runs, `BackupScreen` disables the top-bar back button
  and swallows system back with `BackHandler`.
- **Language per call:** the CSV and its file name take the screen's current `Localizer` as an
  argument instead of one captured when the ViewModel was created, so an import that changes the
  language does not leave a later export in the old one.
- **SAF and process death:** if Android kills the app while the file picker is open, the result
  is lost and the user picks again. Nothing was written, so retrying is safe.
- **Fallback store:** when the on-disk database could not be opened (`isUsingFallbackStore`),
  every action is disabled: exporting an empty in-memory store or importing into it would only
  mislead.
- **Cost:** a file from an old phone wipes anything newer on this one; the dialog says so and
  suggests exporting first. Files are capped at 32 MB of text.
- **iOS parity:** the format is shared. `app/src/test/resources/backup/v1.json` is the contract:
  it is never edited, and both apps must keep decoding it.

### Spreadsheet export is a read-only CSV

"Export spreadsheet" writes one CSV with fuel-ups and services of every vehicle, archived ones
included, sorted by vehicle name (then vehicle, so two vehicles with the same name do not
interleave), date and odometer. It has a UTF-8 BOM and CRLF line ends so Excel
opens accents correctly; the separator is `;` when the language uses a decimal comma (pt-BR) and
`,` otherwise; numbers are plain with the locale's decimal separator; headers are translated and
units are always metric. Text typed by the user that starts with `=`, `+`, `-`, `@` or a tab gets
a leading `'` so a spreadsheet does not run it as a formula.

- **Why CSV and one file:** every spreadsheet app opens it with no library; one sheet is easier
  to filter than one file per vehicle.
- **Cost:** it cannot be restored — the JSON backup is the restorable format — and saved routes
  and reminder rules are not in it.

## Quality gates

### Coverage is checked on changed lines, not on the whole project

CI requires 80% of the executable lines added or modified by a change to run in tests
(see [CI](ci.md#coverage-of-changed-lines)). Compose layout and app wiring are left out via
`.coverageignore`.

- **Why:** a global threshold either blocks everything from day one or is set so low it
  means nothing. Checking new code raises coverage with every change and puts the cost on
  whoever writes the code, while it is still fresh.
- **Cost:** old untested code only gets tests when someone touches it — refactors and even
  reformatting pull those lines into the check. The ignore list needs judgment: a composable
  that grows real logic should move it to its ViewModel rather than stay excluded.
- **Android:** JaCoCo needs `includeNoLocationClasses` to see code run under Robolectric,
  which is how the Room repositories and the scheduler are tested.

### ktlint with community defaults

The formatter runs ktlint's official style through Spotless, with options pinned only where
the defaults clash with the project ([CI](ci.md#format--spotless--ktlint)). Formatting is
checked, never discussed in review.

### Layer rules as a script instead of a lint plugin

**Android:** SwiftLint's custom regex rules have no direct equivalent; a detekt or Android
Lint rule would need its own module. `scripts/layer_rules.py` applies the same three rules
with the same names, ignoring comments and strings.

## Architecture

### Layers as packages in one module, guarded by lint

- **Why:** one developer, one app module. Separate Gradle modules would add build
  configuration for little gain today.
- **Cost:** the compiler can't stop `presentation` from using a `data` type; the layer rule
  does, and its list of names is manual.
- **Revisit when:** a second target (widget, Wear OS, Android Auto) needs the same data —
  then `domain` and `data` become Gradle modules.

### No reactive queries in the UI

- **Why:** collecting Room `Flow`s in composables puts storage types in the UI and erases the
  Data/Presentation boundary — the same reason the iOS app does not use `@Query`.
- **Cost:** ViewModels reload after every write; a change made elsewhere shows up on the
  next load. Fine for a single-user, offline app.
- **Revisit when:** a second writer appears (sync, widget) — an additive `observe…(): Flow`
  method on the repositories fits without changing the existing ones.

### No use-case layer

- **Why:** most use cases would be one-line pass-throughs to a repository. Business rules
  live in `domain/<feature>/calculations` and are tested there.
- **Revisit when:** the same flow is triggered from three or more screens, or one operation
  has to coordinate several repositories consistently.

### Domain and Data grouped by feature

`domain/` and `data/` are split into `vehicles/`, `fueling/`, `maintenance/` and `reminders/`
(plus `costs/` in Domain and `database/` in Data), with `common/` for what every feature
uses. Inside each feature the Clean Architecture folders stay: `entities/`, `repositories/`,
`calculations/`, `services/` in Domain; `models/`, `mappers/`, `repositories/`, `services/` in
Data. The odometer belongs to
`vehicles/` because readings come from both fuel-ups and services; `Cost` gets its own
package for the same reason.

- **Why:** the same split as `presentation/`, so a feature is found by name in every layer,
  and inside it each kind of type still has its usual place.
- **Cost:** Room exports the schema under the database's qualified name, so the folder in
  `app/schemas/` moved to `…data.database.RodaroDatabase`. WorkManager stores the worker's
  class name, so a reminder scheduled before the move fails to start; the next sync replaces
  it (`enqueueUniqueWork` with `REPLACE`).

### One folder per screen in `presentation/`

Each screen gets its own package with its composable, its ViewModel and a `components/`
folder for the rows and cards only it uses: `vehicles/list`, `vehicles/detail`,
`vehicles/form`, `fueling/form`, `maintenance/form`. The fuel and service rows live in
`vehicles/detail/components` because the detail timeline is the only place they show up.
`VehicleStats` stays in `vehicles/` because both the list and the detail use it; code shared
across features stays in `common/`.

- **Why:** finding a screen means opening one folder, and a screen's private pieces are not
  mixed with another screen's.
- **Cost:** a component that starts being reused has to move to the feature root or to
  `common/`.

### Android: Room with foreign keys, sync off

The iOS schema stays CloudKit-ready (no unique constraints, optional relationships, orphans
dropped by the mappers). Room needs a primary key, so ids are unique, and every record
references its vehicle with `ON DELETE CASCADE`, so orphans cannot exist and the mappers have
nothing to drop.

- **Cost:** a future sync layer has to bring its own conflict handling for ids.

### Trip cost uses the vehicle's real average, editable

The Route tab estimates a trip's fuel cost offline: distance (doubled for a round trip) ÷ km/L ×
price per liter. Picking a vehicle fills km/L with its `Consumption.average` (full tank to full
tank) and the price with its last fuel-up; both stay editable, and a value the user typed is kept
when switching vehicles. A vehicle without two full tanks leaves km/L empty for the user.

- **Why:** the average of real fill-ups is closer to a trip than the manufacturer's figure, and
  the latest price is what the next fill-up will cost. No map or routing API: the user types the
  distance.
- **Revisit when:** tolls or splitting the cost between passengers are wanted, or a maps
  integration brings the distance.

### Android: bottom bar with Vehicles, Archived, Route and Profile

Archived moved from a button on the vehicle list to its own tab, next to Vehicles, Route
and Profile.
Each tab keeps its own back stack, and the bar stays visible on pushed screens, like a tab
bar on iOS, so the content never changes size during a transition. Switching tabs is
instant, while push and pop inside a tab slide.

- **Cost:** only the routes of the tabs you are not on are kept. Their entries leave the
  `NavDisplay` back stack, so their ViewModels and saved UI state are cleared. Coming back
  reloads the screen; with local data that takes a few milliseconds.
- **Profile** has no account behind it yet: it shows the app's name, purpose and version.

### Android: Profile is about the user, not the app

The Profile tab opens with who is signed in (avatar with initials, name, e-mail, provider)
and a summary of the garage: active vehicles, records (fill-ups plus services) and total
spent. The records and the total include archived vehicles, because they are still the
user's history; the vehicle count does not, to match the Vehicles tab. Settings rows show
the current value (notifications, currency and units, language) and all open Preferences.
About groups rating on Google Play, sharing and the privacy policy. "Account and data" opens
with Backup and export (the JSON backup, restore and CSV), then sign out and delete as plain
rows, the delete one in red, instead of a full-width red button.

- **Why:** the old screen led with the app's logo and tagline, hid the account below it and
  gave the irreversible delete the most prominent button on the screen.
- **Cost:** the summary reads every vehicle's records on each visit to the tab; fine for a
  personal garage, worth revisiting if a fleet ever lives in one install.

### Android: navigation per feature, one main host, slide transitions

Each feature declares its routes and entries in `presentation/<feature>/navigation`; the
shared `Route` and `Navigator` are in `presentation/common/navigation`; `app/navigation/AppNavHost`
is the single `NavDisplay` that adds every feature's entries. Screens only get callbacks.
A feature's routes are its own business, the same way its screens are — one central file
listing every destination would grow with every feature and mix them together.
Pushes slide the new screen in from the right over the old one, and pops do the reverse, like a
navigation stack on iOS.

- **Why:** the Navigation 3 default is a crossfade. Halfway through it both screens are
  translucent and the window background shows through, which looked like a blink, worst in
  dark mode, where the window theme was still light. Slides keep both screens opaque, the
  window theme now follows the system (`values-night/themes.xml`) and `AppNavHost` draws an
  opaque background under every transition.
- **Also:** `LoadingIndicator` shows the spinner only after 400 ms, so a fast local load never
  flashes a spinner during the transition.
- **Cost:** the back stack lives in `remember`, so a configuration change that recreates the
  activity returns to the vehicle list. Keeping it needs saveable routes (ids instead of the
  `Vehicle` in `VehiclesRoute.VehicleDetail`).

### Android: ViewModels live in the navigation entry; forms live in their dialog

List and detail ViewModels are created with `viewModel { }` inside a Navigation 3 entry, so
they survive rotation and are cleared when the screen leaves the back stack. Form ViewModels
are `remember`ed by their dialog, so each opening starts clean — the iOS `@State` behavior.

- **Cost:** an open form is lost on rotation.

### Android: localized text outside composables goes through `Localizer`

`ServiceReminderSync` builds notification text and `VehicleStats` builds "in 3 days" labels
outside any composable. Both take a `Localizer`: Android resources in the app,
`TestLocalizer` (which reads the real `strings.xml`) in tests. Domain errors carry no text;
`Throwable.message(localizer)` turns them into the copy the iOS `LocalizedError` provides.

## Calculations

### Money is `BigDecimal`; distance and volume are `Double`

Prices and totals never go through floating-point arithmetic. `Double.toDecimal()` converts
through the shortest string form, so `0.1` stays `0.1`. Divisions use `MathContext.DECIMAL128`
and comparisons use `compareTo`, never `equals` (which also compares scale).

### Consumption only between full tanks

A km/L value needs a full tank at both ends; partial fill-ups in between add their liters
to the interval. Entries with non-positive liters are skipped entirely and reported as
anomalies.

### Forecasts are deterministic

`Maintenance.forecast` never reads the clock; the latest reading is "now". The next
service is the earlier of the distance-based date (from the vehicle's km/day pace) and
the calendar-based date. Months use `365.25 / 12` days only when shown to the user.

### Android: calendar math on `java.time` with an explicit zone

Every function that iOS gives a `Calendar` takes a `ZoneId` here. Adding months clamps to
the end of the month exactly like `Calendar.date(byAdding:)`, and day counts compare local
dates, so tests pin `ZoneOffset.UTC` the way the iOS tests pin a UTC calendar.

### The current odometer is the most recent reading, not the highest

A reading that goes backwards is bad data; returning the maximum would hide it.

## Reminders

### Reminders are re-planned from scratch on every sync

`ServiceReminderSync` loads every active vehicle, asks `ReminderPlan` (pure, in `domain`)
when each rule is due, cancels pending notifications that no longer match a rule and
schedules the rest. Each notification's id is its rule's id, so scheduling again replaces it.

- **Why:** due dates move whenever anything changes — a fuel-up shifts the km/month pace, a
  service resets the interval, a rule is edited or a vehicle archived. Recomputing everything
  is simpler and harder to get wrong than updating reminders incrementally per event.
- **Cost:** a sync reads all active vehicles. Fine for one driver's garage; revisit if a
  sync ever shows up in a profile.

### Sync runs at the end of the list and detail `load()`

Those two screens already reload after every write and when the app opens, so syncing there
covers every change without each form knowing about reminders.

- **Cost:** a sync also runs when the user just navigates. It is idempotent and cheap.
- **Revisit when:** reads become reactive — then sync on data changes instead.

### Android: notifications are WorkManager jobs

Each reminder is a unique one-time `WorkRequest` named after its rule id, delayed until the
fire date, tagged so `pendingIdentifiers()` can list them. `ReminderWorker` posts the
notification when it runs.

- **Why:** WorkManager survives reboots and app updates and can list what is pending, which
  `ServiceReminderSync` needs to cancel stale reminders. `AlarmManager` can do neither.
- **Cost:** delivery is not to the minute — Android may batch the job while the phone dozes,
  so a 9:00 reminder can arrive a little later. Exact alarms need a special permission that a
  maintenance reminder does not justify.

### Android: asking for notification permission

`POST_NOTIFICATIONS` is a runtime permission from Android 13. `NotificationPermission` asks
through the activity when `ServiceReminderSync` has something to schedule — the same moment
iOS asks — and answers "denied" when no activity is around. Below Android 13 nothing is asked:
the answer is whether the app's notifications are turned on in the system settings.

### When a reminder fires

Reminders use the same status as the screen (`Maintenance.status`), so a notification never
calls something overdue that the app shows as due soon.

- **Ahead of time:** at 9:00 on the forecast due day.
- **Overdue** (calendar date passed, or the odometer reached the due km): at the next 9:00,
  daily for the first 7 days, then weekly, until the service is logged. "Overdue since" is
  the calendar due date, or the date of the first reading at or past the due km. Daily
  nagging forever teaches people to turn notifications off.
- **Pace estimate passed, not overdue:** no repeats. The notification already fired on the
  estimated day; repeating it daily for a car that sits in the garage would be noise. A rule
  that also has a months interval is still reminded at 9:00 on its calendar due day.
- **Km-only rule without a km/day pace** (one reading, or no movement): no date to schedule,
  so it is notified only once a logged reading reaches the due km. Readings only change when
  the user logs something, and every log triggers a sync, so that is when it fires.

When a rule is overdue both ways, "since" is the earlier of the two dates and the text says
"Overdue since" the calendar date. A rule with both intervals whose pace estimate has passed
still gets a reminder at 9:00 on its calendar due day.

Only one notification is pending per rule, and it is re-planned on every sync, so the daily
and weekly repeats happen only while the app keeps being opened.

Each notification names the vehicle and the service. Overdue by km says the due km and the
last reading, because the app only knows the last logged odometer, not the live one.
Permission to notify is asked only when there is something to schedule.

### `ServiceReminderSync` is the first use case

It coordinates three repositories and the scheduler and is triggered from two screens — the
trigger the "No use-case layer" decision set for revisiting. It lives in
`presentation/reminders` because it builds localized notification text; the scheduling rule
itself is in `domain/reminders/calculations/ReminderPlan.kt`.

### Service status: on track, due soon, overdue

Each reminder shows a status computed by `Maintenance.status(due, currentOdometer, now, zone)`,
counted from the last service of that kind (or the vehicle's creation).

- **Overdue** only when the calendar due date has passed or the odometer reached the due km.
  The distance-based date is an estimate from the km/day pace; a car parked for weeks must
  not turn red just because the estimate passed.
- **Due soon** when km left ≤ 15% of the interval, clamped to 300–1,000 km, or days left to
  the forecast date ≤ 15% of the period, clamped to 15–30 days. The floors keep short
  intervals (a 1,000 km chain) from warning one day ahead; the caps keep long ones (40,000 km
  spark plugs) from warning months ahead. Same rule for cars and motorcycles.
- A km-only rule on a vehicle without a km/day pace yet still shows, from km alone, with a
  hint to log another reading; it has no date until there is a second reading with distance.

## Presentation

### Route tab starts with saved routes; calculator is a separate screen

The Route tab now opens on a saved-routes list. Starting a new estimate opens a dedicated
calculation screen, and restoring a route opens that same screen prefilled.

- **Why:** daily use is mostly revisiting known routes; separating list and form reduces visual
  clutter and typing.
- **Cost:** one extra tap for the first estimate in a session.

### Dashboard on the vehicle screen

The detail screen leads with the odometer and km/month, a 2×2 grid of metrics, every
reminder with its status and progress, a km/L chart (two or more intervals) and a six-month
spending chart (months without records count as zero), then the history with an
All / Fuel / Services filter. Adding records moved to a bottom bar so it is one tap.

### New fuel-ups start with the last price

The price per liter is filled in from the latest fuel-up, with a footer saying so. Most
fill-ups happen at the same station, so this saves typing; the field stays editable.

### Category colors

Fuel is teal and services indigo everywhere (rows, charts, badges). Status uses green,
orange and red, so fuel deliberately avoids orange. The values are Apple's system colors, so
both apps look alike.

### Android: charts are drawn on a Canvas

There is no Swift Charts on Android, and a chart library would be the heaviest dependency of
the app for two small charts. `Charts.kt` draws the km/L line (with the dashed average) and
the stacked monthly bars with `Canvas` and `TextMeasurer`.

### Android: icons from Material Symbols

Each SF Symbol maps to the closest Material icon (`OilBarrel` for oil, `TireRepair` for tires,
`TwoWheeler` for motorcycles, …). Air and fuel filters, which share a symbol on iOS, get
different icons because Material has a filter icon.

## Analytics

### Every action and navigation is instrumented

The goal is to know exactly what a user does. `domain/common/services/Analytics` exposes two
methods — `screen(name)` for navigation and `event(name, parameters)` for actions — and every
ViewModel that performs a user action takes an `analytics: Analytics` parameter defaulted to
`NoAnalytics`, so existing tests and previews are untouched. The composition root
(`AppDependencies`) injects the Firebase implementation (`FirebaseAnalyticsTracker`).

- **Screens:** `AppNavHost` logs one `screen_view` per top-of-stack route, so navigation needs
  no per-screen code.
- **Actions:** each mutating ViewModel logs after the write succeeds — `fuel_up_saved`,
  `service_saved`, `vehicle_created`/`vehicle_edited`, `vehicle_archived`/`vehicle_restored`,
  `timeline_entry_deleted`, `route_calculated`/`route_vehicle_selected`,
  `route_distance_looked_up`/`route_saved`/`route_deleted`,
  `preference_changed`, `auth_signed_in`/`auth_account_created`, `account_signed_out`,
  `account_deleted`, `backup_exported`, `backup_spreadsheet_exported`, `backup_imported`.
  Failed backups log `backup_export_failed`/`backup_import_failed` with a `reason` from a
  closed set (`unreadable`, `newer_format`, `invalid`, `too_large`, `storage`) and no user data. Parameters stay small (`mode`, `kind`, `round_trip`, the changed
  preference `key`) and never carry values, odometer readings, e-mail or the typed places,
  so no personal or vehicle data leaves the device.
- **Why a Domain interface:** Presentation must not reach for Firebase directly (the layer
  rules forbid `data` types in `presentation`), and the no-op default keeps unit tests free of
  a mocking framework. Events are emitted only on the success path, so a failed save is not
  counted as an action.

## Routes and networking

### Route estimate shows fuel-only scope; tolls are out of scope

OpenRouteService can tell if a route has toll roads, but does not provide a reliable toll amount.
The Route screen therefore explains that the estimate is based only on distance, consumption and
fuel price, and that toll costs should be checked separately.

- **Why:** avoid showing a misleading total when the app cannot compute toll prices correctly.
- **Cost:** users need an extra step to account for tolls.

### Real distance and tolls come from OpenRouteService

The route tab looks up the real distance between an origin and a destination, and tells
whether the road has tolls. The provider is the free
[OpenRouteService](https://openrouteservice.org) (HeiGIT, built on OpenStreetMap data):

- Geocoding by place name: `GET /geocode/search`, which answers a GeoJSON feature list; the
  first feature's coordinates feed the route.
- Driving route: `POST /v2/directions/driving-car` with `extra_info: ["tollways"]`, which
  answers `summary.distance` (m) and `summary.duration` (s), plus a `tollways` extra whose
  summary carries a 0/1 value per road segment — any 1 means the route crosses a tollway.
- A free API key (from `openrouteservice.org/dev`) is read from `local.properties`
  (`ors.api.key`), injected as `BuildConfig.ORS_API_KEY` and sent as the `Authorization`
  header. Without a key the app degrades gracefully: the lookup button explains that a key
  is missing and nothing else breaks. The `OPEN_ROUTE_API_KEY` environment variable is the
  fallback, and CI writes it from the GitHub secret of the same name into `local.properties`.
  Debug builds tolerate a missing key, but `assembleRelease` and `bundleRelease` fail without
  one, so a release can never ship with route lookup silently disabled.
- **Why ORS:** it is the free routing API with an explicit toll flag; Google's equivalent
  needs billing, and OSRM-class servers have no toll data at all.
- **Why behind a Domain interface:** `RouteEstimateProvider` lives in Domain, so the
  concrete provider (Retrofit + kotlinx-serialization, `data/network`) can be swapped —
  another API, a paid tier, an offline cache — without touching Presentation.

### Saved calculations are Room rows with a snapshot

`TripCostScreen` can save a calculation. The saved route stores the typed origin and
destination, the one-way distance, the round-trip flag, the toll flag and a snapshot of the
inputs and result (km/L used, price per liter, liters, fuel cost, cost per kilometer) with the
vehicle and the date.

- **Why a snapshot instead of a reference:** consumption and fuel price change with every
  fuel-up; a saved trip is a record of what the user calculated that day, not a live
  recalculation.
- `trip_routes` was introduced in database version 2 (`MIGRATION_1_2`), later extended with
  `tollCost` in version 3 (`MIGRATION_2_3`) and with `kilometersPerLiter` and
  `pricePerLiter` in version 4 (`MIGRATION_3_4`). `RodaroDatabaseMigrationTests` covers each
  step and checks existing data survives with safe defaults on new columns.

### Trip cost shows fill-ups from the tank capacity and where each input came from

The trip screens use the selected vehicle's tank capacity and fuel history instead of only
the typed numbers.

- **Fill-ups:** `TankPlan` divides the trip's liters by the tank capacity. "Fill up N times"
  is that ratio rounded up (a tiny tolerance keeps exactly 2.0 tanks at 2), and the stops on
  the way are N − 1, assuming the driver leaves with a full tank. Range per tank is capacity
  × km/L. There is no reserve margin: the screen states the assumption instead of guessing a
  percentage. Without a tank capacity the screen asks for one rather than hiding the section.
- **Inputs stay editable:** consumption is still pre-filled with the full-tank average and
  price with the last fuel-up, but the screen now says so (number of full-to-full intervals,
  date of the last price). Once the user types another value it is labelled as edited and a
  "Use average" / "Use last price" action restores the suggestion. Switching vehicles still
  keeps typed values, as before.
- **Restoring a saved calculation** keeps its snapshot values (see below) and only loads the
  vehicle history as an offer, so editing an old trip never silently changes its numbers.
- **Saved calculations open a detail screen** first; editing is an explicit action. The tank
  plan in the detail uses the vehicle's current capacity, since capacity is not part of the
  snapshot and rarely changes.

- **Routes come only from OpenRouteService; the toll amount is not looked up.** An earlier
  version priced tolls through our own rodaro-api, but that API is no longer going to be
  deployed: every lookup would cost money on a paid toll provider, and the free path already
  gives distance, duration, the map path and a reliable "has tolls" flag. When a route has
  tolls and no amount, the total is labelled "+ tolls, not included" and the footer says the
  estimate leaves tolls out. `tollCost` stays in `RouteEstimate` and in `trip_routes`, so routes
  saved with a toll amount keep showing it, and a future free source (for example ANTT/ARTESP
  open data on toll plazas) can fill it without a migration.
- **Cost:** the ORS key ships in the APK, so all users share its free daily quota; if usage
  grows, geocoding is the first call to move off ORS.

- **The route preview is MapLibre on OpenFreeMap tiles.** Both are free with no API key or
  usage quota, unlike Google Maps SDK or Mapbox. The map is a static preview (gestures off) so
  it does not fight the form's scroll; navigation is handed to Google Maps or Waze by URL,
  which needs no SDK or package query. The path is stored with the saved route as a
  precision-6 encoded polyline, so a saved calculation shows its map without a new lookup;
  routes saved before this have an empty path and show only the open-in buttons.

- **A trip's total is fuel plus tolls; cost per km stays fuel only.** The total is what the
  trip costs out of pocket, so tolls belong in it, and the fuel/toll split is shown under it.
  Cost per km is kept as fuel cost so it stays comparable with the vehicle's own cost per km,
  which comes from fill-ups and has no tolls.

### Release bundles are built and signed by GitHub Actions

GitHub secrets cannot be read back, not even by their owner, so a local build never sees the
OpenRouteService key or the keystore stored there. Releases are therefore built by
`release.yml` (see [CI](ci.md#release)), which hands them to Gradle as environment variables.

- **Signing:** `signingConfigs.release` exists only when `KEYSTORE_PATH` is set; without it a
  local release is unsigned and can still be signed with Android Studio's "Generate Signed
  Bundle". The keystore is the Play upload key, so a leak is recoverable by asking Google for a
  new upload key; a copy is kept outside GitHub.
- **Version:** `version.properties` is the single source for `versionName` and `versionCode`.
  The release workflow bumps it (patch, minor or major, and always +1 on the code), commits and
  tags `vX.Y.Z` itself, so the number in the repo always matches the last build sent out and
  nobody edits it by hand. The commit and tag are pushed only after the build passes. Releases
  run only on `main`, since the bump commit lands on the branch the run started from.
- **Distribution:** App Distribution gets the signed **APK**, not the bundle, because sending
  an AAB there requires linking Firebase to the Play app and goes through Play's own signing;
  the APK installs as is. Testers who also have the Play version must uninstall it first, since
  Play re-signs with the app signing key and the APK carries the upload key. Google Play gets
  the bundle through `r0adkll/upload-google-play`; the track is chosen on each run and defaults to
  internal, so production is never reached by accident.
- **Cost:** a release is a manual run, and the first bundle of a new app must
  still be uploaded to Play by hand before the API accepts uploads.

### Debug builds log requests and analytics under `Rodaro.*` tags

Logcat on an emulator is mostly system noise, so the app writes its own entries with tags
that start with `Rodaro.`, only in debug builds (`app/DebugLog`):

- `Rodaro.Http`: one framed block per request (`┌ #3 → POST url`, headers, body) and one per
  response (`┌ #3 ← 200 OK … in 120 ms`, headers, body) or failure (`┌ #3 ✕ FAILED …`). The
  number pairs a response with its request when several run at once. JSON bodies are
  pretty-printed; binary bodies show only their size, and text is cut at 2,500 characters so an
  entry stays under Logcat's size limit. The response is read with `peekBody`, so the app still
  gets the whole body. `HttpLogInterceptor` runs after the interceptor that adds the ORS key, so
  the headers are the ones really sent, but `Authorization`, `Cookie`, `Set-Cookie` and
  `X-Api-Key` are shown as `██ (hidden)` and `api_key`, `key` and `token` are dropped from the
  URL; a test checks the key never reaches the log.
- `Rodaro.Analytics`: every screen and event, through `LoggingAnalytics`, which wraps the
  Firebase tracker and still forwards everything to it.
- `Rodaro.Reminders` and `Rodaro.Startup`: the existing error logs, renamed to share the prefix.

`make logs` runs `adb logcat -s` with those tags plus `AndroidRuntime:E` for crashes; in Android
Studio the same view is the filter `package:mine tag~:Rodaro`. Release builds log nothing.

### Place suggestions wait for a pause in typing

The origin and destination fields ask OpenRouteService for suggestions only after 400 ms
without a new keystroke, and each new keystroke cancels the search still waiting or running
for that field (cancelling the coroutine also cancels the OkHttp call). Picking a suggestion
cancels the pending search too, so a late answer cannot reopen the list. Text shorter than
three letters clears the list at once without a request.

- **Why:** typing "Campinas" used to send six requests in parallel; on the free ORS quota,
  shared by every user, that is most of the cost of the screen, and on a slow network the
  answers arrived out of order.
- **Cost:** suggestions appear 400 ms later than before.

## Tooling

### Four specialized AI agents instead of one

Development uses four Claude Code subagents — a car and motorcycle expert, an Android
specialist, a reviewer and an executor — with shared rules in `AGENTS.md`. Only the executor
edits code, and nothing is done until `make ci` passes. Rationale and workflow: [agents.md](agents.md).
