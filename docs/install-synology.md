# Installationsguide – Synology Container Manager

Denna guide visar hur du kör **Kluddra Server** som en enda lättvikts-container på en Synology NAS (DSM 7.2+ med Container Manager).

## 1. Förbered delad mapp

1. Öppna **File Station** på din Synology NAS.
2. Skapa mappen `/docker/kluddra/data`.
3. Eftersom Kluddra-containern körs säkert som icke-root (`UID 65532`), ge mappen skrivrättigheter:
   - Via SSH: `sudo chown -R 65532:65532 /volume1/docker/kluddra/data`
   - *Alternativt:* lägg till `user: "1026:100"` (din NAS-användares UID/GID) i `docker-compose.yml`.

## 2. Skapa projekt i Container Manager

1. Öppna **Container Manager → Projekt → Skapa**.
2. Namn: `kluddra`, Sökväg: `/docker/kluddra`.
3. Välj **Skapa docker-compose.yml** och klistra in innehållet från [`deploy/docker-compose.yml`](../deploy/docker-compose.yml).
4. Starta projektet.

## 3. Hämta engångskod för första konto

1. Öppna **Container Manager → Containrar → kluddra → Logg**.
2. Leta upp raden med `SETUP CODE` (t.ex. `KLUD-7F3A-92C1`).

## 4. Aktivera HTTPS via Synology Reverse Proxy (rekommenderas)

1. Öppna **Kontrollpanelen → Inloggningsportal → Avancerat → Omvänd proxy → Skapa**.
2. **Källa:** Protokoll `HTTPS`, värdnamn `notes.mindoman.se`, port `443`, aktivera HSTS.
3. **Destination:** Protokoll `HTTP`, värdnamn `localhost`, port `8080`.
4. Koppla ett Let's Encrypt-certifikat under **Kontrollpanelen → Säkerhet → Certifikat**.

## 5. Anslut från Kluddra Android

1. Öppna Kluddra-appen på din telefon eller surfplatta.
2. Ange `https://notes.mindoman.se`.
3. Skriv in engångskoden från steg 3 samt välj användarnamn och ett starkt lösenord. Klart!
