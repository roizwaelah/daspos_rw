# Mockup Final - Manajemen Harga Bertingkat

## 1) Product Form

```
+--------------------------------------------------------+
| [<] Tambah/Edit Produk                                 |
+--------------------------------------------------------+
| Nama Produk                                            |
| [ AIR MINERAL                                   ]      |
|                                                        |
| Stok (Dasar / Ecer)                                    |
| [ 500                                           ]      |
|                                                        |
| Harga Bertingkat (Ecer/Renteng/Pak/Karton)            |
| 1 Renteng=10 Ecer | 1 Pak=20 Ecer | 1 Karton=100 Ecer |
|                                                        |
| Harga Ecer                                             |
| [ 3000                                          ]      |
| Harga Renteng                                          |
| [ 28000                                         ]      |
| Harga Pak                                              |
| [ 55000                                         ]      |
| Harga Karton                                           |
| [ 260000                                        ]      |
|                                                        |
|                                  [Batal] [Simpan]     |
+--------------------------------------------------------+
```

## 2) Cart (Transaksi)

```
+--------------------------------------------------------+
| AIR MINERAL                                            |
| 2 x KARTON @ Rp260.000                                 |
| [ECER|RENTENG|PAK|KARTON]              Rp520.000       |
|                             [-]                [+]     |
+--------------------------------------------------------+
```

## 3) Struk

```
+--------------------------------------------------------+
| DASPOS STORE                                           |
| No. Transaksi: 2604230007             23 Apr 2026      |
|--------------------------------------------------------|
| AIR MINERAL                            Rp520.000        |
| 2 x KARTON @ Rp260.000                                 |
|--------------------------------------------------------|
| Total                                   Rp520.000      |
| Bayar                                   Rp600.000      |
| Kembalian                                Rp80.000      |
+--------------------------------------------------------+
```

Catatan:
- Tidak ada baris tambahan `base: n ecer` pada struk.
- Stok tetap dikelola dalam satuan dasar (`ecer`) di backend aplikasi.
