# Setup MySQL untuk Local Network

Dokumen ini menyiapkan instance MySQL lokal yang bisa diakses dari perangkat lain di jaringan LAN.

## 1) Jalankan container

```bash
./scripts/setup-mysql-local.sh
```

Atau manual:

```bash
docker compose -f docker-compose.mysql.yml up -d
```

## 2) Kredensial default

- Host (lokal): `127.0.0.1`
- Host (LAN): IP mesin Anda (contoh `192.168.1.10`)
- Port: `3306`
- Database: `daspos`
- User aplikasi: `daspos`
- Password aplikasi: `daspos123`
- Root password: `root123`

> Ubah password default sebelum dipakai di lingkungan produksi.

## 3) Verifikasi dari mesin host

```bash
mysql -h 127.0.0.1 -P 3306 -u daspos -pdaspos123 -e "SHOW DATABASES;"
```

## 4) Verifikasi dari perangkat lain di LAN

Gunakan client MySQL dari device lain:

```bash
mysql -h <IP_LAN_SERVER> -P 3306 -u daspos -pdaspos123 -e "SELECT NOW();"
```

Jika gagal konek:

1. Pastikan server dan client berada di jaringan yang sama.
2. Pastikan firewall membuka TCP port `3306`.
3. Pastikan container sehat:
   ```bash
   docker compose -f docker-compose.mysql.yml ps
   ```

## 5) Stop / reset

Stop service:

```bash
docker compose -f docker-compose.mysql.yml down
```

Hapus data volume (reset total):

```bash
docker compose -f docker-compose.mysql.yml down -v
```
