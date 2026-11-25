# Säkerhetskopiering, uppdatering och återställning

Du äger alltid din data. All beständig serverdata ligger i `/data`-volymen (`./data` på värden).

## Automatisk och manuell varm backup (medan servern körs)

Kopiera **inte** `kluddra.db` direkt medan containern körs (SQLite WAL-läge skriver till `-wal`-filen). Använd istället Kluddras inbyggda atomiska backup (`VACUUM INTO`):

- **Automatiskt:** Körs var 24:e timme till `/data/backups/kluddra-YYYYMMDD-HHMMSS.db` (14 dagars historik som standard).
- **Manuellt:**
  ```bash
  docker exec kluddra /kluddra backup
  ```

Säkerhetskopiera sedan mappen `./data/backups/` till din externa backup (t.ex. Synology Hyper Backup, Restic eller Borg).

## Uppdatera Kluddra Server utan dataförlust

```bash
cd ~/kluddra
docker compose pull
docker compose up -d
```

Före varje ny databasmigrering skapar Kluddra automatiskt en extra säkerhetskopia i `/data/backups/`.

## Återställa från backup

1. Stoppa containern: `docker compose down`
2. Flytta undan befintlig databas och WAL-filer:
   ```bash
   rm -f ./data/kluddra.db-wal ./data/kluddra.db-shm
   cp ./data/backups/kluddra-YYYYMMDD-HHMMSS.db ./data/kluddra.db
   chown 65532:65532 ./data/kluddra.db
   ```
3. Starta containern: `docker compose up -d`
4. Anslutna Android-enheter känner automatiskt av sekvensskillnaden och synkroniserar om utan dataförlust.

## Dataexport (ingen leverantörsinlåsning)

- **I Android-appen (fungerar även offline):** *Inställningar → Dina data → Exportera backup (JSON)* eller *Exportera läsbara anteckningar (Markdown ZIP)*.
- **Via serverns API:** `GET /api/v1/export?format=json` eller `GET /api/v1/export?format=markdown`.
