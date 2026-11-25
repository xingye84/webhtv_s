package com.fongmi.android.tv.ui.bean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;

import com.fongmi.android.tv.setting.CustomCspSetting;
import com.google.gson.Gson;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 28, application = Application.class)
public class SiteInjectHomeButtonSourceTest {

    @Test
    public void normalizingMergedSitesKeepsTheCurrentHomeSelection() {
        CustomCspSetting.Registry registry = new Gson().fromJson("""
                {"homeKey":"cinema","items":[
                    {"id":"current","kind":"csp","key":"cinema"},
                    {"id":"legacy","kind":"csp","key":"cinema"}
                ]}
                """, CustomCspSetting.Registry.class).normalize();

        assertEquals("cinema", registry.getHomeKey());
        assertEquals("cinema", registry.getItems().get(0).getKey());
        assertEquals("cinema_2", registry.getItems().get(1).getKey());
    }

    @Test
    public void generatedKeyDoesNotTakeTheHomeSelectionFromAnOriginalKey() {
        CustomCspSetting.Registry registry = new Gson().fromJson("""
                {"homeKey":"cinema_2","items":[
                    {"id":"first","kind":"csp","key":"cinema"},
                    {"id":"duplicate","kind":"csp","key":"cinema"},
                    {"id":"selected","kind":"csp","key":"cinema_2"}
                ]}
                """, CustomCspSetting.Registry.class).normalize();

        assertEquals("cinema_2", registry.getItems().get(1).getKey());
        assertEquals(registry.getItems().get(2).getKey(), registry.getHomeKey());
    }

    @Test
    public void homeButtonCatalogUsesASeparateSiteInjectionId() throws Exception {
        String source = read("app/src/leanback/java/com/fongmi/android/tv/bean/HomeButton.java");
        assertTrue(source.contains("ALL = \"0,1,2,3,8,4,5,6,7,9\""));
        assertTrue(source.contains("FollowingSettings.isEnabled()"));
        assertTrue(source.contains("new HomeButton(9, R.string.home_custom_csp)"));
        assertTrue(!source.contains("home_adblock"));
    }

    @Test
    public void siteInjectionIsNotEnabledByDefault() throws Exception {
        String source = read("app/src/leanback/java/com/fongmi/android/tv/bean/HomeButton.java");
        String defaults = methodBody(source, "getDefaultButtons()");
        assertFalse("站点注入必须默认不开启：默认按钮串不得包含 id 9", defaults.contains("\"9\""));
        assertTrue("默认按钮串必须保留原有按钮", defaults.contains("ids.add(\"7\")"));
        assertTrue("站点注入必须仍可在“个性设置 → 首页按钮”中手动启用", source.contains("new HomeButton(9, R.string.home_custom_csp)"));
    }

    private static String methodBody(String source, String signature) {
        // 匹配方法声明（call site 后面没有 “ {”），避免命中其他方法内的同名调用。
        int start = source.indexOf(signature + " {");
        assertTrue("missing method " + signature, start >= 0);
        int from = source.indexOf('{', start);
        int depth = 0;
        for (int i = from; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') {
                depth--;
                if (depth == 0) return source.substring(from, i + 1);
            }
        }
        throw new IllegalStateException("unterminated method " + signature);
    }

    @Test
    public void functionButtonShowsRegistryStateAndHomeOpensTheEditor() throws Exception {
        String func = read("app/src/leanback/java/com/fongmi/android/tv/bean/Func.java");
        assertTrue(func.contains("CustomCspSetting.status()"));
        assertTrue(func.contains("R.string.home_custom_csp_on"));
        assertTrue(func.contains("R.string.home_custom_csp_off"));
        assertTrue(func.contains("R.drawable.ic_site_config"));
        assertTrue(func.contains("getText().equals(other.getText())"));
        assertTrue(!func.contains("home_adblock"));

        String home = read("app/src/leanback/java/com/fongmi/android/tv/ui/activity/HomeActivity.java");
        assertTrue(home.contains("else if (item.getResId() == R.string.home_custom_csp) openCustomCsp()"));
        String openCustomCsp = methodBody(home, "private void openCustomCsp()");
        assertFalse(openCustomCsp.contains("PermissionUtil.requestFile"));
        assertTrue(openCustomCsp.contains("CustomCspDialog.show(this, this::setFunc)"));
        assertTrue(openCustomCsp.contains("getSupportFragmentManager().isStateSaved()"));
        String manage = read("app/src/main/java/com/fongmi/android/tv/ui/dialog/ManagePageDialog.java");
        String openManagement = methodBody(manage, "public static void show(FragmentActivity activity)");
        assertFalse(openManagement.contains("PermissionUtil.requestFile"));
        assertTrue(openManagement.contains("ManageService.start(activity)"));
        assertTrue(!home.contains("home_adblock"));

        String dialog = read("app/src/main/java/com/fongmi/android/tv/ui/dialog/CustomCspDialog.java");
        assertTrue(dialog.contains("public static void show(FragmentActivity activity, Runnable callback)"));
        assertTrue(dialog.contains("CustomCspSetting.save(registry)"));

        String csp = read("app/src/main/java/com/fongmi/android/tv/setting/CustomCspSetting.java");
        assertTrue(!csp.contains("toggleEnabled()"));
    }

    @Test
    public void allLeanbackLocalesExposeSiteInjectionLabels() throws Exception {
        for (String locale : new String[]{"values", "values-zh-rCN", "values-zh-rTW"}) {
            String strings = read("app/src/leanback/res/" + locale + "/strings.xml");
            assertTrue(locale, strings.contains("<string name=\"home_custom_csp\">")
                    && strings.contains("<string name=\"home_custom_csp_on\">")
                    && strings.contains("<string name=\"home_custom_csp_off\">")
                    && !strings.contains("home_adblock"));
        }
    }

    private static String read(String path) throws Exception {
        Path direct = Path.of(path);
        if (Files.exists(direct)) return Files.readString(direct, StandardCharsets.UTF_8);
        return Files.readString(Path.of("..").resolve(path), StandardCharsets.UTF_8);
    }
}
