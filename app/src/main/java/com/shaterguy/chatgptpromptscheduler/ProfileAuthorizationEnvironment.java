package com.shaterguy.chatgptpromptscheduler;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import com.google.android.gms.common.GoogleApiAvailability;
import java.security.MessageDigest;
import java.util.Locale;

/** Public installed-package metadata only; never queries accounts or authorization state. */
final class ProfileAuthorizationEnvironment {
    static long googleVersion(Context context) {
        try {
            PackageInfo info=context.getPackageManager().getPackageInfo("com.google.android.gms",0);
            return Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        } catch (PackageManager.NameNotFoundException | RuntimeException ignored) { return -1; }
    }
    static int googleAvailability(Context context) {
        try { return GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context); }
        catch (RuntimeException ignored) { return -1; }
    }
    static String report(Context context) {
        return "app="+BuildConfig.APPLICATION_ID+" version="+BuildConfig.VERSION_NAME+" code="+BuildConfig.VERSION_CODE
                +"\nandroid_sdk="+Build.VERSION.SDK_INT+" auth_sdk=21.6.0"
                +"\nsigning_sha1="+signingSha1(context)
                +"\nenvironment_at_copy: gms="+googleVersion(context)+" availability="+googleAvailability(context)+"\n";
    }
    private static String signingSha1(Context context) {
        try {
            int flags=Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
            PackageInfo info=context.getPackageManager().getPackageInfo(context.getPackageName(),flags);
            Signature[] signatures=Build.VERSION.SDK_INT >= 28 && info.signingInfo != null
                    ? info.signingInfo.getApkContentsSigners() : info.signatures;
            if (signatures == null || signatures.length != 1) return "UNAVAILABLE";
            byte[] digest=MessageDigest.getInstance("SHA-1").digest(signatures[0].toByteArray());
            StringBuilder out=new StringBuilder();
            for(byte item:digest) { if(out.length()>0)out.append(':'); out.append(String.format(Locale.ROOT,"%02X",item & 0xff)); }
            return out.toString();
        } catch (Exception ignored) { return "UNAVAILABLE"; }
    }
    private ProfileAuthorizationEnvironment() {}
}
