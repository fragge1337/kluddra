# Installationsguide – Docker Compose

Kluddra Server kräver varken extern databas eller Redis – allt körs i en enda container (~25 MB) med SQLite i WAL-läge.

## Installation

```bash
mkdir -p ~/kluddra/data
sudo chown -R 65532:65532 ~/kluddra/data
curl -o ~/kluddra/docker-compose.yml https://raw.githubusercontent.com/fragge1337/kluddra/main/deploy/docker-compose.yml
cd ~/kluddra
docker compose up -d
docker compose logs kluddra
```

## Miljövariabler

| Variabel | Standard | Beskrivning |
| :--- | :--- | :--- |
| `KLUDDRA_PORT` | `8080` | HTTP-port inuti containern |
| `KLUDDRA_DATA_DIR` | `/data` | Katalog för SQLite-databas, hemlig nyckel och backuper |
| `KLUDDRA_TRUSTED_PROXIES` | `127.0.0.1/32,::1/128` | CIDR-intervall för din reverse proxy (används för korrekt klient-IP vid rate limiting) |
| `KLUDDRA_BACKUP_ENABLED` | `true` | Schemalagd nattlig SQLite-backup via `VACUUM INTO` |
| `KLUDDRA_BACKUP_RETAIN_DAYS` | `14` | Antal dagar automatiska backuper behålls |
| `KLUDDRA_TRASH_RETAIN_DAYS` | `30` | Antal dagar innan anteckningar i papperskorgen raderas permanent |
| `KLUDDRA_ADMIN_SETUP_TOKEN` | *(autogenereras)* | Valfri fast engångskod vid första uppstart |
| `KLUDDRA_ARGON2_MEMORY_KB` | `65536` | Argon2id-minne i KiB (minst `32768` för minnessnåla Raspberry Pi) |

## Skapa ytterligare hushållskonto

Öppen registrering är alltid avstängd. För att skapa ett konto till en familjemedlem:

```bash
docker exec kluddra /kluddra user create -username anna -password "EttStarktLösenord123!"
```
