# Google Play Store Listing & Submission Guide — Kluddra

Denna guide innehåller färdiga butikstexter, inställningar för Google Play Console och checklista för lansering av **Kluddra (`app.kluddra.android`)**.

---

## 1. Butikstexter — Engelska (`en-US` / standard)

- **App name (max 30 chars):** `Kluddra`
- **Short description (max 80 chars — 74 chars):**
  `Private notes & shopping lists. Local-first, fast, optional own-cloud sync.`
- **Full description (max 4000 chars):**
```text
Your data. Your device. Your own cloud.

Kluddra is a fast, modern, open-source notes and shopping list app built with Material 3 Expressive design and complete privacy at its core. Start using it immediately on your device — with zero accounts, zero ads, and zero tracking — or connect your own self-hosted Kluddra Server whenever you want seamless sync across your phone and tablet.

NOTES THAT STAY OUT OF YOUR WAY
• Start writing immediately — titles are optional and auto-derived from your note.
• Checklists, numbered lists, and bold formatting with a clean floating toolbar.
• 10 calibrated pastel note colors for both Light and dimmed Dark themes.
• Pin important notes to the top tray and lock read-only notes so you can read or copy without accidental edits.
• Instant local full-text search, timestamps, and 4-way sorting (Last edited, Newest, Oldest, Title A–Z).
• Share text directly into Kluddra from any Android app, and export everything anytime as JSON or Markdown ZIP.

EFFORTLESS SHOPPING LIST
• Dedicated one-handed shopping tab with instant inline item entry.
• Drag-and-drop reordering, satisfying check-off animations, and one-tap clear completed with Undo.

DESIGNED FOR PHONES & TABLETS
• Adaptive dual-pane layout with a centered navigation rail on tablets.
• Spacious landscape mode on phones with an auto-hiding header while scrolling.
• Available in 11 European languages with Android Per-App Language support.

PRIVACY FIRST — LOCAL OR SELF-HOSTED
• Works 100% offline with zero network calls in local-only mode.
• Optional sync to your own self-hosted Kluddra Server (Docker / Synology / Raspberry Pi / Linux) with conflict-safe sync that never silently loses edits.
• No ads, no analytics, no telemetry, no third-party cloud lock-in. Open-source under Apache-2.0.
```

- **Release notes (`v1.0.0`):**
```text
First release of Kluddra!
• Local-first notes with 10 pastel colors, checklists, numbered lists, pinning, read-only lock, full-text search, dates & 4-way sorting
• Dedicated shopping list with inline add & drag-and-drop sorting
• Optional conflict-safe sync to your own self-hosted Kluddra Server
• Adaptive Material 3 Expressive design for phones and tablets in Light & Dark themes
```

---

## 2. Butikstexter — Svenska (`sv-SE`)

- **Appnamn (max 30 tecken):** `Kluddra`
- **Kort beskrivning (max 80 tecken — 72 tecken):**
  `Privata anteckningar & inköpslista. Lokalt först, valfri egen molnsynk.`
- **Fullständig beskrivning (max 4000 tecken):**
```text
Din data. Din enhet. Ditt eget moln.

Kluddra är en snabb, modern och öppen källkods-app för anteckningar och inköpslistor byggd med Material 3 Expressive och fullständig integritet i fokus. Börja använda appen direkt på din enhet – utan konto, reklam eller spårning – eller anslut din egen självhostade Kluddra-server när du vill synka mellan telefon och surfplatta.

ANTECKNINGAR UTAN KRÅNGEL
• Börja skriva direkt – rubrik är frivillig och skapas automatiskt vid behov.
• Checklistor, numrerade listor och fetstil med en smidig svävande verktygsrad.
• 10 kalibrerade pastellfärger för både ljust och mörkt tema.
• Fäst viktiga anteckningar överst och skrivskydda anteckningar för säker läsning och kopiering.
• Blixtsnabb lokal fulltextsökning, datumstämplar och 4-vägssortering (Senast ändrad, Senast skapad, Äldst skapad, Titel A–Ö).
• Dela text direkt till Kluddra från andra appar och exportera allt när som helst som JSON eller Markdown-ZIP.

SMIDIG INKÖPSLISTA
• Dedikerad flik för inköpslistan med blixtsnabb inmatning direkt i listan.
• Dra-och-släpp-sortering, tydlig avbockning och rensa klara med ett tryck och ångra-stöd.

BYGGD FÖR BÅDE TELEFON & SURFPLATTA
• Adaptiv tvåspaltsvy med centrerad sidopanel på surfplattor.
• Rymligt liggande läge på telefoner där toppraden fälls in vid skrollning.
• Finns på 11 språk med stöd för Androids appspecifika språkval.

INTEGRITET FÖRST – LOKALT ELLER EGEN SERVER
• Fungerar 100 % offline utan ett enda nätverksanrop i lokalt läge.
• Valfri konfliktsäker synkronisering mot din egen Kluddra-server (Docker / Synology / Raspberry Pi / Linux).
• Ingen reklam, ingen spårning, ingen telemetri. Öppen källkod under Apache-2.0.
```

- **Versionsanteckningar (`v1.0.0`):**
```text
Första versionen av Kluddra!
• Lokala anteckningar med 10 färger, checklistor, numrerade listor, fastnålning, skrivskydd, fulltextsökning, datum och sortering
• Dedikerad inköpslista med snabb inmatning och dra-och-släpp
• Valfri konfliktsäker synk mot din egen självhostade Kluddra-server
• Adaptiv Material 3 Expressive-design för telefon och surfplatta i ljust och mörkt läge
```

---

## 3. Färdiga svar för Google Play Console

| Sektion i Play Console | Rekommenderat val |
| :--- | :--- |
| **App category** | `Productivity` (`Produktivitet`) |
| **Free / Paid** | `Free` (`Gratis`), innehåller **inga** annonser (`No, my app does not contain ads`) |
| **Privacy policy URL** | `https://github.com/fragge1337/kluddra/blob/main/docs/privacy-policy.md` *(eller via GitHub Pages)* |
| **App access** | `All functionality is available without special access` *(granskare trycker bara "Start using locally" – inget testkonto behövs!)* |
| **Data safety — Collects or shares user data?** | **No** *(ingen data samlas in av utvecklaren; eventuell synk sker enbart till användarens egen server)* |
| **Advertising ID** | **No** |
| **Content rating (IARC)** | Productivity / Utility — alla svar `No` → **PEGI 3 / Everyone** |
| **Target audience** | `18 and over` eller `13+` *(undviker onödig "Designed for Families"-granskning)* |
| **Play App Signing** | **Aktivera** (standard i Play Console – Google hanterar slutlig app-signering och din lokala `.jks` fungerar som säker Upload Key) |

---

## 4. Bygga en signerad `.aab` (App Bundle) lokalt

1. Skapa `android/keystore.properties` *(gitignorerad – laddas aldrig upp till GitHub!)*:
   ```properties
   storeFile=../../kluddra-upload.jks
   storePassword=<DITT_KEYSTORE_LOSENORD>
   keyAlias=kluddra-upload
   keyPassword=<DITT_KEY_LOSENORD>
   ```
2. Bygg och verifiera Play-paketet:
   ```powershell
   cd android
   .\gradlew.bat bundleRelease --no-daemon
   ```
3. Ladda upp dessa två filer till **Play Console → Testning → Sluten testning (Closed testing)**:
   - App Bundle: `android/app/build/outputs/bundle/release/app-release.aab`
   - R8-mappningsfil (för läsbara kraschloggar): `android/app/build/outputs/mapping/release/mapping.txt`

> **Säkerhetskopiera din `.jks`-nyckel + lösenord direkt** (t.ex. i din lösenordshanterare och på din Synology NAS)! Förloras uppladdningsnyckeln kan den visserligen återställas via Google Play App Signing-support, men backup gör framtida uppdateringar friktionsfria.
