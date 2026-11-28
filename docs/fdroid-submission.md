# F-Droid & FOSS Distribution Guide — Kluddra

Kluddra (`app.kluddra.android`) är 100 % ren FOSS (Apache-2.0, noll Google Play Services, noll Firebase, noll spårning) med `dependenciesInfo` avstängt och komplett Fastlane-metadata i `fastlane/metadata/android/` (engelska + svenska, ikon, funktionsgrafik, telefon- och surfplatteskärmdumpar).

---

## 1. Publicera på officiella F-Droid (rekommenderas)

F-Droid bygger appen direkt från din GitHub-källkod från taggen `v1.0.0`, signerar den och publicerar den i F-Droid-butiken (och automatiskt för varje ny `vX.Y.Z`-tagg i framtiden). **Inga testare och inga avgifter krävs.**

### Steg 1: Skapa en Git-tagg `v1.0.0` (och GitHub Release)
När ändringarna är pushade till `main`:
```bash
git tag -a v1.0.0 -m "Kluddra v1.0.0"
git push origin v1.0.0
```
*(Skapa gärna även en GitHub Release på taggen `v1.0.0` och bifoga den signerade `app-release.apk` – då fungerar även direkt nedladdning, Obtainium och IzzyOnDroid!)*

### Steg 2: Skicka in till F-Droid (välj det enklaste sättet)

#### Alternativ A (enklast – 2 minuter via webbläsaren): Request For Packaging (`fdroid/rfp`)
1. Gå till: https://gitlab.com/fdroid/rfp/-/issues/new
2. Skapa ett nytt ärende med titeln: `Kluddra — Private local-first notes & shopping list`
3. Fyll i följande uppgifter:
   - **App ID:** `app.kluddra.android`
   - **Source code:** `https://github.com/fragge1337/kluddra`
   - **License:** `Apache-2.0`
   - **Category:** `Writing`
   - **Summary:** `Private notes & shopping lists. Local-first, fast, optional own-cloud sync.`
   - **Notes:** `100% FOSS (no GMS/Firebase/trackers), dependenciesInfo disabled, Fastlane metadata (en-US & sv-SE + screenshots) in repo, Gradle Android project in subdir android/app, tagged v1.0.0.`

#### Alternativ B (snabbast granskning): Merge Request till `fdroid/fdroiddata`
Forka https://gitlab.com/fdroid/fdroiddata, skapa filen `metadata/app.kluddra.android.yml` med exakt detta innehåll och öppna en Merge Request:

```yaml
Categories:
  - Writing
License: Apache-2.0
AuthorName: fragge1337
SourceCode: https://github.com/fragge1337/kluddra
IssueTracker: https://github.com/fragge1337/kluddra/issues
Changelog: https://github.com/fragge1337/kluddra/releases

AutoName: Kluddra

RepoType: git
Repo: https://github.com/fragge1337/kluddra.git

Builds:
  - versionName: 1.0.0
    versionCode: 1
    commit: v1.0.0
    subdir: android/app
    gradle:
      - yes

AutoUpdateMode: Version v%v
UpdateCheckMode: Tags ^v[0-9]+\.[0-9]+\.[0-9]+$
CurrentVersion: 1.0.0
CurrentVersionCode: 1
```

---

## 2. Bonus: Snabb lansering via IzzyOnDroid (1–3 dagar) & GitHub Releases

Medan officiella F-Droid granskar första bygget (oftast 1–3 veckor) kan du redan idag låta användare installera Kluddra via:
- **GitHub Releases + Obtainium:** Ladda upp den signerade APK:n (`android/app/build/outputs/apk/release/app-release.apk`, t.ex. omdöpt till `kluddra-v1.0.0.apk`) under **GitHub → Releases → Draft a new release → tag `v1.0.0`**.
- **IzzyOnDroid** (populärt F-Droid-kompatibelt repo): Öppna ett ärende på https://gitlab.com/IzzyOnDroid/repo/-/issues/new och länka till din GitHub Release (`https://github.com/fragge1337/kluddra/releases`). IzzyOnDroid använder din signerade APK + Fastlane-metadatan direkt och godkänner oftast inom 1–3 dagar.

---

## 3. Framtida uppdateringar (automatiska på F-Droid!)

När Kluddra väl finns i F-Droid:
1. Öka `versionCode` och `versionName` i `android/app/build.gradle.kts` (t.ex. `2` och `"1.0.1"`).
2. Lägg till `fastlane/metadata/android/en-US/changelogs/2.txt` och `sv-SE/changelogs/2.txt`.
3. Pusha till `main` och skapa taggen `v1.0.1`.
4. F-Droid upptäcker taggen automatiskt, bygger nya versionen och skickar ut uppdateringen till alla användare!
