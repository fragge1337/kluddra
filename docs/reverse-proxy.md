# Reverse Proxy & HTTPS

Kluddra Android blockerar alltid okrypterad HTTP över internet. För åtkomst utanför ditt lokala hemnätverk ska Kluddra Server placeras bakom en reverse proxy med giltigt TLS/HTTPS-certifikat (t.ex. Let's Encrypt).

## Caddy (enklast – automatisk HTTPS)

```caddyfile
notes.example.com {
    reverse_proxy kluddra:8080 {
        header_up X-Real-IP {remote_host}
    }
}
```

## Nginx / Nginx Proxy Manager

- **Scheme:** `http`
- **Forward Hostname / IP:** `kluddra` (eller värdens IP)
- **Forward Port:** `8080`
- Aktivera **SSL → Request a new SSL Certificate (Let's Encrypt)**, **Force SSL** och **HTTP/2 Support**.
- Ställ in `KLUDDRA_TRUSTED_PROXIES` i `docker-compose.yml` till ditt Docker-nätverk så rate-limitern ser verklig klient-IP.

## Tailscale / VPN (utan publika portar)

Om du använder Tailscale kan du aktivera **Tailscale Serve (`tailscale serve https / http://127.0.0.1:8080`)** och ansluta med din privata `https://<nas>.<tailnet>.ts.net`-adress utan att öppna några portar i routern.
