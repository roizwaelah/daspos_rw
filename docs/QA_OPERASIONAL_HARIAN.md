# QA Operasional Harian (DasPos)

Tanggal: __________
Outlet: __________
Device: __________
Build: __________
Tester: __________

## A. Login & Sesi
- [ ] Login admin berhasil (email + password benar)
- [ ] Login kasir berhasil
- [ ] Logout kembali ke layar login
- [ ] Auto-logout timeout berjalan sesuai aturan

## B. Master Produk
- [ ] Tambah produk berhasil
- [ ] Edit produk berhasil
- [ ] Hapus produk berhasil
- [ ] Stok tampil sesuai setelah perubahan

## C. Transaksi Kasir
- [ ] Tambah produk ke keranjang berhasil
- [ ] Validasi stok kurang muncul saat qty melebihi stok
- [ ] Simpan transaksi berhasil
- [ ] Struk transaksi bisa dibuka
- [ ] Hapus struk mengembalikan stok

## D. Laporan
- [ ] Laporan harian tampil lengkap
- [ ] Laporan bulanan tampil lengkap
- [ ] Klik nomor transaksi buka detail struk tanpa crash
- [ ] Export PDF berhasil
- [ ] Export Excel/CSV berhasil

## E. Backup & Restore
- [ ] Backup file JSON berhasil dibuat
- [ ] Restore file valid berhasil
- [ ] Data produk, user, transaksi sesuai setelah restore
- [ ] Setting printer/store/receipt sesuai setelah restore

## F. Offline/Online Sync
- [ ] Saat offline, transaksi tetap bisa disimpan lokal
- [ ] Saat online kembali, data tersinkron
- [ ] Tidak ada duplikasi transaksi setelah sinkron
- [ ] Waktu sinkron masih wajar (catat detik): ______

## G. Akses Role
- [ ] Kasir tidak bisa akses menu admin yang dibatasi
- [ ] Admin bisa akses semua menu yang semestinya
- [ ] Menu kasir sesuai izin yang di-set admin

## H. Hasil Akhir
- [ ] LULUS harian
- [ ] GAGAL (wajib tiket bug)

Catatan bug/temuan:
1. ______________________________
2. ______________________________
3. ______________________________
