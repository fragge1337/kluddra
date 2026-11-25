# Kluddra

**Self-hosted notes, beautifully simple.**

Kluddra är en modern, självhostad antecknings- och inköpslisteapp med fokus på snabbhet, enkelhet, integritet och **Material 3 Expressive**-design.

- **Kluddra Server** (`server/`) – en minimal Docker-container i Go + SQLite (WAL-läge) för Synology NAS, QNAP, Unraid, TrueNAS, Raspberry Pi och Linux (`amd64` & `arm64`).
- **Kluddra Android** (`android/`) – en native, offline-first Android-app (`app.kluddra.android`) byggd i Kotlin & Jetpack Compose med Material 3 Expressive (`1.5.0`) och Material 3 Adaptive (`1.2.0`).

## Huvudfunktioner

- **Snabba anteckningar:** Valfri rubrik, automatiskt sparande, tomma anteckningar sparas inte, omedelbar lokal fulltextsökning (Room FTS4), papperskorg med återställning och delning in/ut via Android.
- **Fästa & Skrivskydda:** Fäst viktiga anteckningar överst; aktivera skrivskydd som blockerar tangentbord och redigering (även på serverns API) men tillåter markering och kopiering.
- **Dedikerad inköpslista:** Enhandsoptimerad snabbinmatning, tydlig avbockning, dra-och-släpp-sortering, rensa avbockade och töm listan med bekräftelse/ångra.
- **Offline-first & Konfliktsäker synk:** Fungerar helt utan nätverk. Serverkontrollerad sekvens (`server_seq`) hanterar klockskillnader och skapar automatiskt konfliktkopior vid samtidiga offlineredigeringar – noll tyst dataförlust.
- **Integritet:** Noll reklam, noll spårning, noll telemetri, noll externa molntjänster.

## Snabbstart med Docker Compose

```bash
mkdir -p kluddra/data
# För Synology / non-root-behörighet (UID 65532 i distroless):
chown -R 65532:65532 kluddra/data
cp deploy/docker-compose.yml kluddra/
cd kluddra
docker compose up -d
docker compose logs kluddra
```

Läs av den engångskod (`KLUD-XXXX-XXXX`) som skrivs ut i loggen, öppna **Kluddra** på Android, ange din serveradress (t.ex. `https://notes.mindoman.se`) och skapa ditt första konto direkt i appen.

## Dokumentation

- [Installationsguide – Docker Compose](docs/install-docker-compose.md)
- [Installationsguide – Synology Container Manager](docs/install-synology.md)
- [Reverse Proxy & HTTPS](docs/reverse-proxy.md)
- [Säkerhetskopiering, uppdatering & återställning](docs/backup-and-restore.md)
- [Säkerhet & integritetsmodell](docs/security.md)

## Licens

Kluddra är öppen källkod under **Apache License 2.0** – se [LICENSE](LICENSE). Du är fri att använda, ändra och forka projektet.
