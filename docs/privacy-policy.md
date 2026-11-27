# Privacy Policy — Kluddra

**Last updated:** 2026-11-27

Kluddra (*"Your data. Your device. Your own cloud."*) is an open-source notes and shopping list app designed from the ground up for complete privacy.

## 1. Data We Collect
**None.** The Kluddra developer does not operate any central servers, does not collect personal data, does not use analytics, crash-reporting SDKs, advertising IDs, or third-party trackers of any kind.

## 2. Local-First Storage
By default, all notes, shopping list items, and app preferences are stored exclusively on your Android device in a local SQLite database. When used in local-only mode, Kluddra makes **zero** network requests.

## 3. Optional Self-Hosted Sync
If you choose to connect Kluddra to your own self-hosted Kluddra Server:
- Your notes, shopping list items, username, and authentication tokens are transmitted **only** directly between your device and the server URL you explicitly entered.
- Public internet connections require encrypted HTTPS; unencrypted HTTP is blocked on public addresses.
- Authentication credentials on your device are encrypted using Android Keystore (AES-256-GCM).
- The Kluddra developer has zero access to your self-hosted server or any data stored on it.

## 4. Permissions Used
Kluddra requests only the minimum network permissions required for optional self-hosted synchronization:
- `INTERNET` — used solely to communicate with the self-hosted server you configure (never used in local-only mode).
- `ACCESS_NETWORK_STATE` — used to check connectivity before attempting background synchronization.

Kluddra does **not** request access to your camera, microphone, contacts, location, photos, or Advertising ID.

## 5. Data Deletion & Export
- You can export all your notes and shopping items at any time in **Settings** (JSON or Markdown ZIP).
- You can delete notes permanently via Trash, clear all local data by signing out or uninstalling the app, or manage/delete data directly on your own server.

## 6. Children's Privacy
Kluddra does not collect any personal information from anyone, including children under 13.

## 7. Open Source & Contact
Kluddra is licensed under the Apache License 2.0. You can inspect the complete source code or report privacy questions via GitHub Issues:
- Repository: https://github.com/fragge1337/kluddra

---

# Integritetspolicy — Kluddra (Svenska)

**Senast uppdaterad:** 2026-11-27

Kluddra (*"Din data. Din enhet. Ditt eget moln."*) är en öppen källkods-app för anteckningar och inköpslistor byggd från grunden för fullständig integritet.

- **Ingen datainsamling:** Utvecklaren samlar inte in någon persondata, driver inga centrala molnservrar och använder varken reklam, analysverktyg, telemetri eller tredjepartsspårning.
- **Lokal lagring som standard:** Alla anteckningar och inköpslistor sparas lokalt på din enhet. I lokalt läge gör appen **noll** nätverksanrop.
- **Valfri egen server:** Om du ansluter din egen självhostade Kluddra-server skickas data enbart direkt mellan din enhet och den serveradress du själv anger (via krypterad HTTPS över internet). Inloggningsuppgifter på enheten skyddas med Android Keystore (AES-256-GCM).
- **Minimala behörigheter:** Endast `INTERNET` och `ACCESS_NETWORK_STATE` för valfri synkronisering. Ingen åtkomst till kamera, mikrofon, plats, kontakter eller reklam-ID.
- **Export & radering:** Du kan när som helst exportera all din data som JSON eller Markdown-ZIP i Inställningar, eller radera all lokal data genom att logga ut eller avinstallera appen.
- **Källkod & kontakt:** https://github.com/fragge1337/kluddra
