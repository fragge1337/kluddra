# Säkerhets- och integritetsmodell

Kluddra är byggd för privat självhosting med noll externa molnberoenden, noll reklam och noll telemetri.

## Vad som skyddas och hur

| Lager | Skydd i Kluddra v1.0 | Begränsning (transparent dokumentation) |
| :--- | :--- | :--- |
| **Transport (nätverk)** | HTTPS / TLS 1.3 via reverse proxy. Okrypterad HTTP till publika adresser blockeras i Android-appen. | Om användaren aktivt godkänner lokal HTTP på hemnätverket krypteras inte LAN-trafiken. |
| **Autentisering** | Argon2id-hashade lösenord, kortlivade HS256-JWT (15 min), SHA-256-hashade refresh tokens med rotation och automatisk familjeåterkallelse vid återanvändning. | Ingen öppen registrering – konton skapas enbart med uppstartskod eller admin-CLI. |
| **Android-enheten** | Tokens krypteras med AES-256-GCM i **Android Keystore**; enheten skyddas av Androids filbaserade kryptering (FBE). Minimala behörigheter (`INTERNET`, `ACCESS_NETWORK_STATE`). | Lokal Room-databas använder inte separat app-lösenord utöver Androids systemkryptering. |
| **Kluddra Server (NAS)** | Icke-root distroless-container (`read_only`), parameteriserad SQL, strikt `user_id`-isolering, rate limiting och säkra loggar (anteckningar loggas aldrig). | **Ej End-to-End-krypterad (E2EE):** Anteckningstext lagras i klartext i din egen SQLite-databas på din NAS. Aktivera gärna krypterad delad mapp/volym på din NAS. |

## Skrivskyddade anteckningar

Skrivskydd förhindrar oavsiktliga ändringar på telefon, surfplatta och serverns API (`423 Locked`). Det är ett redigeringsskydd, inte ett krypterat kassaskåp.
