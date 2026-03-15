package com.daspos.shared.util;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public class CurrencyUtils {
    public static String formatRupiah(double value) {
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(new Locale("in", "ID"));
        symbols.setCurrencySymbol("Rp ");
        symbols.setGroupingSeparator('.');
        symbols.setMonetaryDecimalSeparator(',');
        DecimalFormat format = new DecimalFormat("'Rp '###,###", symbols);
        return format.format(value);
    }
}
