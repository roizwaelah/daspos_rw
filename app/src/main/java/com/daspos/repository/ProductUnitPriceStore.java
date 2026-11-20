package com.daspos.repository;

import android.content.Context;
import android.content.SharedPreferences;

import com.daspos.model.Product;
import com.daspos.model.SalesUnit;

import org.json.JSONObject;

final class ProductUnitPriceStore {
    private static final String PREF = "product_unit_prices";

    private ProductUnitPriceStore() {
    }

    static void save(
            Context context,
            String productId,
            double priceEcer,
            double priceRenteng,
            double pricePak,
            double priceKarton,
            int factorRenteng,
            int factorPak,
            int factorKarton,
            boolean tierPricingEnabled
    ) {
        if (context == null || productId == null || productId.trim().isEmpty()) return;
        try {
            JSONObject json = new JSONObject();
            json.put("ecer", Math.max(0, priceEcer));
            json.put("renteng", Math.max(0, priceRenteng));
            json.put("pak", Math.max(0, pricePak));
            json.put("karton", Math.max(0, priceKarton));
            json.put("factor_renteng", Math.max(1, factorRenteng));
            json.put("factor_pak", Math.max(1, factorPak));
            json.put("factor_karton", Math.max(1, factorKarton));
            json.put("tier_pricing_enabled", tierPricingEnabled);
            prefs(context).edit().putString(key(productId), json.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    static void save(Context context, Product product) {
        if (context == null || product == null) return;
        save(
                context,
                product.getId(),
                product.getPriceEcer(),
                product.getPriceRenteng(),
                product.getPricePak(),
                product.getPriceKarton(),
                product.getFactorRenteng(),
                product.getFactorPak(),
                product.getFactorKarton(),
                product.isTierPricingEnabled()
        );
    }

    static void apply(Context context, Product product) {
        if (context == null || product == null || product.getId() == null) return;
        String raw = prefs(context).getString(key(product.getId()), "");
        if (raw == null || raw.trim().isEmpty()) {
            ensureDefaults(product);
            return;
        }
        try {
            JSONObject json = new JSONObject(raw);
            double ecer = json.optDouble("ecer", product.getPrice());
            if (ecer <= 0) ecer = product.getPrice();
            if (ecer <= 0) ecer = 0;

            int factorRenteng = Math.max(1, json.optInt("factor_renteng", product.getFactorRenteng()));
            int factorPak = Math.max(1, json.optInt("factor_pak", product.getFactorPak()));
            int factorKarton = Math.max(1, json.optInt("factor_karton", product.getFactorKarton()));
            if (factorRenteng <= 0) factorRenteng = SalesUnit.defaultFactor(SalesUnit.RENTENG);
            if (factorPak <= 0) factorPak = SalesUnit.defaultFactor(SalesUnit.PAK);
            if (factorKarton <= 0) factorKarton = SalesUnit.defaultFactor(SalesUnit.KARTON);

            double renteng = json.optDouble("renteng", ecer * factorRenteng);
            double pak = json.optDouble("pak", ecer * factorPak);
            double karton = json.optDouble("karton", ecer * factorKarton);

            product.setPriceEcer(Math.max(0, ecer));
            product.setFactorRenteng(factorRenteng);
            product.setFactorPak(factorPak);
            product.setFactorKarton(factorKarton);
            
            if (json.has("tier_pricing_enabled")) {
                boolean enabled = json.getBoolean("tier_pricing_enabled");
                if (!enabled) {
                    product.setPriceRenteng(ecer);
                    product.setPricePak(ecer);
                    product.setPriceKarton(ecer);
                    product.setFactorRenteng(1);
                    product.setFactorPak(1);
                    product.setFactorKarton(1);
                } else {
                    product.setPriceRenteng(Math.max(0, renteng));
                    product.setPricePak(Math.max(0, pak));
                    product.setPriceKarton(Math.max(0, karton));
                }
            } else {
                product.setPriceRenteng(Math.max(0, renteng));
                product.setPricePak(Math.max(0, pak));
                product.setPriceKarton(Math.max(0, karton));
            }
        } catch (Exception ignored) {
            ensureDefaults(product);
        }
    }

    private static void ensureDefaults(Product product) {
        double ecer = product.getPriceEcer() > 0 ? product.getPriceEcer() : product.getPrice();
        if (ecer < 0) ecer = 0;
        product.setPriceEcer(ecer);
        if (product.getFactorRenteng() <= 0) {
            product.setFactorRenteng(SalesUnit.defaultFactor(SalesUnit.RENTENG));
        }
        if (product.getFactorPak() <= 0) {
            product.setFactorPak(SalesUnit.defaultFactor(SalesUnit.PAK));
        }
        if (product.getFactorKarton() <= 0) {
            product.setFactorKarton(SalesUnit.defaultFactor(SalesUnit.KARTON));
        }
        if (product.getPriceRenteng() <= 0) {
            product.setPriceRenteng(ecer * product.getFactorRenteng());
        }
        if (product.getPricePak() <= 0) {
            product.setPricePak(ecer * product.getFactorPak());
        }
        if (product.getPriceKarton() <= 0) {
            product.setPriceKarton(ecer * product.getFactorKarton());
        }
    }

    private static String key(String productId) {
        return "units_" + productId.trim();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }
}
