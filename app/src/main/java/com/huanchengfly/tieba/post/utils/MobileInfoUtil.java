package com.huanchengfly.tieba.post.utils;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.TelephonyManager;

import androidx.core.content.ContextCompat;

public class MobileInfoUtil {
    public static final String DEFAULT_IMEI = "000000000000000";

    @SuppressLint({"HardwareIds", "MissingPermission"})
    public static String getIMEI(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return DEFAULT_IMEI;
        }
        try {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE)
                    != PackageManager.PERMISSION_GRANTED) {
                return DEFAULT_IMEI;
            }
            TelephonyManager telephonyManager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            String imei = null;
            if (telephonyManager != null) {
                imei = telephonyManager.getDeviceId();
            }
            if (imei == null) {
                imei = DEFAULT_IMEI;
            }
            return imei;
        } catch (SecurityException e) {
            return DEFAULT_IMEI;
        } catch (Exception e) {
            return DEFAULT_IMEI;
        }
    }
}
