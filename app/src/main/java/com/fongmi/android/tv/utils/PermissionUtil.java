package com.fongmi.android.tv.utils;

import android.Manifest;
import android.net.Uri;
import android.os.Build;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.fongmi.android.tv.App;
import com.fongmi.android.tv.impl.PermissionCallback;
import com.fongmi.android.tv.setting.Setting;
import com.github.catvod.crawler.SpiderDebug;
import com.github.catvod.utils.Path;
import com.permissionx.guolindev.PermissionX;

import java.io.File;
import java.io.IOException;
import java.util.function.Consumer;

public class PermissionUtil {

    public static void requestAudio(FragmentActivity activity, Consumer<Boolean> callback) {
        PermissionX.init(activity).permissions(Manifest.permission.RECORD_AUDIO).request(new PermissionCallback(callback));
    }

    public static boolean requiresFileAccess(String url) {
        if (!"file".equals(UrlUtil.scheme(url))) return false;
        // 跟读取配置时用同一套路径规则，兼容 file://TV/... 和应用私有目录的绝对路径。
        String localUrl = "file:" + Uri.decode(url.substring("file:".length()));
        return requiresFileAccess(Path.local(localUrl), App.get().getDataDir());
    }

    static boolean requiresFileAccess(File file, File privateDir) {
        try {
            String privatePath = privateDir.getCanonicalPath();
            String filePath = file.getCanonicalPath();
            if (filePath.equals(privatePath) || filePath.startsWith(privatePath + File.separator)) return false;
        } catch (IOException ignored) {
            return true;
        }
        return !file.isFile() || !file.canRead();
    }

    public static void requestFile(FragmentActivity activity, Consumer<Boolean> callback) {
        if (hasFileAccess(callback)) return;
        boolean fileManager = shouldRequestFileManager();
        SpiderDebug.log("permission", "request file access managerRequired=%s", fileManager);
        if (fileManager) PermissionX.init(activity).permissions().requestManageExternalStoragePermissionNow(new PermissionCallback(result -> finishFileRequest(callback, result)));
        else requestLegacyFile(activity, callback);
    }

    public static void requestFile(Fragment fragment, Consumer<Boolean> callback) {
        if (hasFileAccess(callback)) return;
        boolean fileManager = shouldRequestFileManager();
        SpiderDebug.log("permission", "request file access managerRequired=%s", fileManager);
        if (fileManager) PermissionX.init(fragment).permissions().requestManageExternalStoragePermissionNow(new PermissionCallback(result -> finishFileRequest(callback, result)));
        else requestLegacyFile(fragment, callback);
    }

    private static void requestLegacyFile(FragmentActivity activity, Consumer<Boolean> callback) {
        PermissionX.init(activity).permissions(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE).request(new PermissionCallback(result -> finishFileRequest(callback, result)));
    }

    private static void requestLegacyFile(Fragment fragment, Consumer<Boolean> callback) {
        PermissionX.init(fragment).permissions(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE).request(new PermissionCallback(result -> finishFileRequest(callback, result)));
    }

    private static boolean shouldRequestFileManager() {
        return Setting.hasFileManager() && App.get().getApplicationInfo().targetSdkVersion >= Build.VERSION_CODES.R;
    }

    private static boolean hasFileAccess(Consumer<Boolean> callback) {
        if (!Setting.hasFileAccess()) return false;
        SpiderDebug.log("permission", "file access already granted");
        if (callback != null) callback.accept(true);
        return true;
    }

    private static void finishFileRequest(Consumer<Boolean> callback, boolean result) {
        boolean granted = Setting.hasFileAccess();
        SpiderDebug.log("permission", "file access request result callback=%s granted=%s", result, granted);
        if (callback != null) callback.accept(granted);
    }

    public static void requestNotify(FragmentActivity activity) {
        PermissionX.init(activity).permissions(PermissionX.permission.POST_NOTIFICATIONS).request(new PermissionCallback());
    }
}
