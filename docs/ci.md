# CI

Every push and pull request runs [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) on
GitHub Actions (`ubuntu-latest`, JDK 17, Gradle with build cache). A change is mergeable only
when every check below passes. The gates are the same as in
[kmais-app-ios](https://github.com/joaoduartemariucio/kmais-app-ios); only the tools differ.

```mermaid
graph LR
    subgraph static-checks["Lint and format"]
        Lint --> Format["Format check"]
    end
    subgraph build-and-test["Build, test and coverage"]
        Build --> Test --> Coverage["Coverage of changed lines ≥ 80%"]
    end
```

The two jobs run in parallel. A newer push to the same branch cancels the run in progress.

## Rules

| Check | Command | Fails when |
|---|---|---|
| **Lint** | `make lint` | a layer rule is broken, or Android Lint reports anything (warnings are errors) |
| **Format** | `make format-check` | any file differs from what ktlint would produce |
| **Script tests** | `make scripts-test` | a Python unit test in `scripts/test_*.py` fails (the ANP pipeline) |
| **Build** | `make build` | the app or the unit tests do not compile |
| **Test** | `make test` | any unit test fails |
| **Coverage** | `make coverage` | less than **80%** of the changed executable lines are covered by tests |

### Lint — layer rules and Android Lint

[`scripts/layer_rules.py`](../scripts/layer_rules.py) holds the three rules that keep the layers apart:

| Rule | Fails when |
|---|---|
| `domain_no_android` | a file in `domain/` imports Android or Jetpack |
| `domain_no_outer_layers` | a file in `domain/` imports from `data`, `presentation` or `app` |
| `presentation_no_data_types` | a file in `presentation/` mentions a Data type |

Android Lint runs next (`lintDebug`, `warningsAsErrors = true`). Only the checks that report
newer library versions are turned off: dependency updates are a deliberate change, not a
lint failure. CI uploads the HTML report.

### Format — Spotless + ktlint

Config: the `spotless` block in [`build.gradle.kts`](../build.gradle.kts).
[ktlint](https://pinterest.github.io/ktlint/)'s `ktlint_official` style — the community
baseline for Kotlin — with a few options pinned:

| Option | Value | Why |
|---|---|---|
| Max line length | 140 | Compose call chains get long; off for tests, whose names are sentences |
| `function-naming` | Composables may be PascalCase; off for tests (backtick names) | |
| `property-naming`, `backing-property-naming` | off | Compose state and `R` constants clash with them |

Run `make format` to fix everything at once.

### Build and test

`make build` compiles the debug app and the unit tests; `make test` runs every unit test,
Robolectric included, with JaCoCo instrumentation and writes
`app/build/reports/coverage/test/debug/report.xml`. CI uploads the test and coverage reports,
so a failure can be opened in a browser.

Instrumented (emulator) tests are not part of CI.

### Coverage of changed lines

The rule is about **new code**, not the whole project: of the executable lines added or
modified by the change, at least **80%** must run during the tests.

[`scripts/diff_coverage.py`](../scripts/diff_coverage.py):

1. diffs the change against its base, ignoring whitespace-only edits:
   - **pull request** → the PR's base commit;
   - **push** → the commit the branch was at before the push (`origin/main` for a new branch);
   - **locally** → `origin/main`, including uncommitted edits; files not yet added to git count in full;
2. reads per-line execution counts from the JaCoCo XML report;
3. keeps only executable lines (comments, braces and declarations never count) in files not
   listed in [`.coverageignore`](../.coverageignore);
4. fails if covered / executable < 80%, printing every uncovered line. In CI the same table
   appears in the run's summary.

A change with no executable lines (docs, composables only, config) passes automatically.

#### What does not count

[`.coverageignore`](../.coverageignore) lists code with no logic of its own, which unit tests
can't meaningfully exercise:

- `app/` — entry point and composition root;
- Compose screens, dialogs, rows, cards and charts (`*Screen.kt`, `*Dialog.kt`, `*Row.kt`,
  `*Card.kt`, `Charts.kt` and a few common components) and the theme;
- `KeyboardDismissal.kt` and `Localizer.kt` — Android glue;
- `data/PreviewData.kt` — preview and seed fixtures.

**ViewModels, `domain` and `data` always count.** Logic in a composable belongs in its
ViewModel, where it can be tested. A new Room repository needs tests against an in-memory
database.

## Running it locally

```bash
make ci
```

`make ci` runs the same steps as CI in order. Each one also runs on its own:

| Command | What it does |
|---|---|
| `make lint` | layer rules, then Android Lint |
| `make format` | formats the code in place |
| `make format-check` | fails if anything would be reformatted |
| `make build` | builds the app and the unit tests |
| `make test` | runs the unit tests with coverage |
| `make scripts-test` | runs the Python script tests (stdlib `unittest`) |
| `make hosting-deploy` | rebuilds `hosting/public/anp/precos-uf.json` from the published copy, then `firebase deploy --only hosting` |
| `make coverage` | checks changed lines against `origin/main`; `BASE=<ref>` or `MIN_COVERAGE=<n>` to override |
| `make clean` | removes build output |

## Release

[`.github/workflows/release.yml`](../.github/workflows/release.yml) is started by hand from the
Actions tab, on `main` only. It picks which part of the version to bump and where to send the
build:

| Input | Options |
|---|---|
| `bump` | `patch` (1.0.0 → 1.0.1), `minor` (→ 1.1.0) or `major` (→ 2.0.0) |
| `play_track` | `internal` (default), `alpha`, `beta`, `production` or `none` |
| `app_distribution` | send the APK to Firebase App Distribution (default on) |

```mermaid
graph LR
    Bump["Bump version.properties"] --> Commit["Commit + tag vX.Y.Z"] --> Build["Signed APK + AAB"]
    Build --> Push["Push commit and tag"] --> Distribute["App Distribution + Google Play"]
```

The version lives in [`version.properties`](../version.properties) (`VERSION_NAME`,
`VERSION_CODE`), which Gradle reads. [`scripts/bump_version.sh`](../scripts/bump_version.sh)
bumps the chosen part, resets the parts after it and always adds 1 to `VERSION_CODE`. The
commit `chore(release): vX.Y.Z (code)` and the tag are pushed only after the build succeeds,
so a failed build burns no version. Release notes are the commit subjects since the previous
`v*` tag; they go into the tag, to the testers and to Play's "What's new".

| Secret | Used for |
|---|---|
| `GOOGLE_SERVICES_JSON` | Firebase config; also gives the App Distribution app id |
| `OPEN_ROUTE_API_KEY` | route lookup, passed as the `OPEN_ROUTE_API_KEY` environment variable |
| `KEYSTORE_BASE64` | upload keystore in base64, decoded to `$RUNNER_TEMP/release.jks` and deleted at the end |
| `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD` | the keystore's passwords and alias |
| `FIREBASE_SERVICE_ACCOUNT` | JSON of a service account with the **Firebase App Distribution Admin** role |
| `PLAY_SERVICE_ACCOUNT` | JSON of a service account invited in the Play Console with release permission |

| Variable (optional) | Default | Used for |
|---|---|---|
| `FIREBASE_TESTER_GROUPS` | `testers` | App Distribution group aliases, comma separated |
| `PLAY_RELEASE_STATUS` | `completed` | `draft` while the app has never been published on Play |
| `PLAY_NOTES_LANGUAGE` | `pt-BR` | language of the "What's new" text; must exist in the Play listing |

Without `FIREBASE_SERVICE_ACCOUNT` or `PLAY_SERVICE_ACCOUNT` that step is skipped with a
warning, so the run still produces the files. Google Play only accepts uploads through the API
after the first bundle of the app has been uploaded by hand in the Play Console.

## ANP prices

[`.github/workflows/anp-prices.yml`](../.github/workflows/anp-prices.yml) publishes the ANP
state averages the app compares fuel prices with. It never commits: `hosting/public/anp/` is
gitignored and the JSON only lives on Firebase Hosting.

| When | What |
|---|---|
| Tuesdays and Fridays, 13:00 UTC | download the newest 3 weekly summaries (absorbs a late upload) |
| `workflow_dispatch` | same, with the `weeks` input to fetch more |
| first run (published JSON answers 404) | backfill the last 52 weeks on its own |

```mermaid
graph LR
    Tests["make scripts-test"] --> Build["anp_prices.py: published JSON + newest summaries"]
    Build -->|changed| Deploy["firebase deploy --only hosting"]
    Build -->|identical or failed| Stop["no deploy"]
```

The script exits with an error, and nothing is deployed, when the published JSON cannot be read
(anything but a 404), a spreadsheet has an unexpected layout, or a week fails the sanity check
(gasolina comum in at least 20 states, every mean between R$ 1 and R$ 20). The deploy step only
runs on `main`.

| Secret | Used for |
|---|---|
| `FIREBASE_HOSTING_SERVICE_ACCOUNT` | JSON key of a service account with only the **Firebase Hosting Admin** role on project `rodaro` |

Create it in the Google Cloud console (IAM → Service accounts → create, grant "Firebase Hosting
Admin", Keys → add JSON key) and store it with `gh secret set FIREBASE_HOSTING_SERVICE_ACCOUNT <
key.json`. Without it the job still builds and tests the JSON, then logs a `::warning::` and
skips the deploy. The key is written to `$RUNNER_TEMP` only for the deploy step and deleted at the
end of the job. That Hosting Admin alone is enough for `firebase deploy --only hosting` is
expected but **unverified until the first run**; if the deploy is refused, add "Service Usage
Consumer" (or "API Keys Viewer") to the same account.

Manual deploys of the site (privacy page, `app-ads.txt`) must use `make hosting-deploy`, which
rebuilds the ANP JSON first; a bare `firebase deploy` would publish the site without it.

