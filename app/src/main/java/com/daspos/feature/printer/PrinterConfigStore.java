package com.daspos.feature.printer;

import android.content.Context;
import android.content.SharedPreferences;

public class PrinterConfigStore {
    private static final String PREF = "printer_config";
    private static final String KEY_TYPE = "type";
    private static final String KEY_BT_NAME = "bt_name";
    private static final String KEY_BT_ADDRESS = "bt_address";
    private static final String KEY_IP = "ip";
    private static final String KEY_PORT = "port";

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
    }

    public static void save(Context c, String type, String btName, String btAddress, String ip, String port) {
        prefs(c).edit()
                .putString(KEY_TYPE, type)
                .putString(KEY_BT_NAME, btName)
                .putString(KEY_BT_ADDRESS, btAddress)
                .putString(KEY_IP, ip)
                .putString(KEY_PORT, port)
                .apply();
    }

    public static String getType(Context c) { return prefs(c).getString(KEY_TYPE, "none"); }
    public static String getBluetoothName(Context c) { return prefs(c).getString(KEY_BT_NAME, ""); }
    public static String getBluetoothAddress(Context c) { return prefs(c).getString(KEY_BT_ADDRESS, ""); }
    public static String getIp(Context c) { return prefs(c).getString(KEY_IP, ""); }
    public static String getPort(Context c) { return prefs(c).getString(KEY_PORT, ""); }
}
