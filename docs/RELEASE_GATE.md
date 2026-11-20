# Release Gate (Boleh Rilis / Tidak)

## A. Gate Wajib (semua harus PASS)
- [ ] Build debug/release sukses
- [ ] Tidak ada crash blocker di flow login, transaksi, laporan, backup/restore
- [ ] QA operasional harian LULUS
- [ ] Test regresi minimal 90% pass
- [ ] Tidak ada bug severity P0/P1 yang open
- [ ] Validasi role kasir/admin sesuai requirement
- [ ] Sync offline-online lolos di minimal 2 device fisik

## B. Gate Data
- [ ] Jumlah transaksi sebelum/sesudah patch konsisten (sample check)
- [ ] Rekap harian/bulanan tidak mismatch
- [ ] Restore file lama (v1/v2) dan file baru (v3) sama-sama aman

## C. Gate Operasional
- [ ] Release note ditulis
- [ ] Rollback plan tersedia
- [ ] Backup DB/konfigurasi sebelum deploy dilakukan
- [ ] PIC support ditentukan untuk 24-48 jam pasca rilis

## Keputusan
- [ ] BOLEH RILIS
- [ ] TIDAK BOLEH RILIS

Alasan (wajib isi jika tidak lolos):
1. ______________________________
2. ______________________________

Approver:
- QA Lead: __________
- Tech Lead: __________
- Owner/PM: __________

Tanggal keputusan: __________
