# Test Case Regresi Teknis (DasPos)

## 1. Auth
1. Login Supabase online sukses
2. Login offline dengan cache sesi sukses
3. Login gagal dengan password salah
4. Register email activation mengirim status sukses
5. Role admin/kasir terbaca benar di session

## 2. Produk
1. CRUD produk lokal/supabase
2. Stok berkurang saat transaksi selesai
3. Stok kembali saat transaksi dihapus
4. Pencarian dan sorting konsisten

## 3. Transaksi
1. Simpan transaksi normal
2. Simpan transaksi offline (enqueue pending)
3. Sync pending saat online kembali
4. Tidak ada double insert saat retry
5. No transaksi tetap konsisten di list/struk

## 4. Laporan
1. Harian sesuai jumlah transaksi aktual
2. Bulanan sesuai agregasi harian
3. Klik item laporan buka struk tanpa crash
4. Pagination/fetch banyak data tidak memotong list

## 5. Best Seller
1. Ranking berdasarkan qty desc
2. Nomor ranking berlanjut antar halaman
3. Toggle harian/bulanan konsisten

## 6. Backup/Restore
1. Backup format v3 terbentuk valid JSON
2. Restore dari file lama (v1/v2) tetap sukses
3. Restore v3 mengembalikan menuAccess/store/receipt
4. Timestamp transaksi setelah restore tidak rusak
5. Restore invalid file ditolak dengan pesan benar

## 7. Role & Permission
1. Kasir tidak bisa akses halaman admin-protected
2. Admin bisa kelola kasir + akses menu kasir
3. Menu kasir di Home sesuai konfigurasi

## 8. Printer
1. Simpan konfigurasi printer
2. Test print bluetooth/network sesuai mode

## 9. Stabilitas
1. Tidak ada ANR/crash pada flow utama
2. Perpindahan screen cepat (tidak blank lama)
3. Memory leak tidak terlihat pada smoke test 30 menit

## Template hasil eksekusi
- Build: __________
- Device/OS: __________
- Lulus: ___ / ___
- Gagal: ___
- Bug IDs: __________________
