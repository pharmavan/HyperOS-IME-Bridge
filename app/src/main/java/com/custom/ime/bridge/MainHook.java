package com.custom.ime.bridge;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainHook implements IXposedHookLoadPackage {

    private static final String TARGET_ANDROID = "android";
    private static final String TARGET_SYSTEM_UI = "com.android.systemui";
    
    // 已确认的目标输入法包名：微信键盘与豆包输入法
    private static final List<String> TARGET_IME_PACKAGES = Arrays.asList(
            "com.tencent.wetype", 
            "com.bytedance.android.doubaoime"
    );

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (TARGET_ANDROID.equals(lpparam.packageName) || TARGET_SYSTEM_UI.equals(lpparam.packageName)) {
            injectMiuiImeWhitelist(lpparam);
        }
    }

    private void injectMiuiImeWhitelist(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.log("[IME_Bridge] 正在挂载 HyperOS 全面屏键盘优化白名单拦截...");

        String[] targetClasses = {
                "miui.view.inputmethod.InputMethodHelper",
                "android.inputmethodservice.InputMethodServiceInjector",
                "com.miui.inputmethod.InputMethodBottomManager",
                "com.miui.inputmethod.InputMethodBottomManagerHelper"
        };

        for (String className : targetClasses) {
            Class<?> clazz = XposedHelpers.findClassIfExists(className, lpparam.classLoader);
            if (clazz == null) continue;

            try {
                XposedHelpers.findAndHookMethod(clazz, "getSupportIme", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        Object result = param.getResult();
                        if (result == null) return;

                        if (result instanceof List) {
                            List<String> supportList = new ArrayList<>((List<String>) result);
                            for (String pkg : TARGET_IME_PACKAGES) {
                                if (!supportList.contains(pkg)) {
                                    supportList.add(pkg);
                                    XposedBridge.log("[IME_Bridge] 已向白名单追加 (List): " + pkg);
                                }
                            }
                            param.setResult(supportList);
                        } else if (result instanceof String[]) {
                            String[] supportArray = (String[]) result;
                            List<String> supportList = new ArrayList<>(Arrays.asList(supportArray));
                            for (String pkg : TARGET_IME_PACKAGES) {
                                if (!supportList.contains(pkg)) {
                                    supportList.add(pkg);
                                    XposedBridge.log("[IME_Bridge] 已向白名单追加 (Array): " + pkg);
                                }
                            }
                            param.setResult(supportList.toArray(new String[0]));
                        }
                    }
                });
            } catch (Throwable t) {
            }

            try {
                XposedHelpers.findAndHookMethod(clazz, "isSupportIme", String.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        String pkgName = (String) param.args[0];
                        if (TARGET_IME_PACKAGES.contains(pkgName)) {
                            param.setResult(true);
                            XposedBridge.log("[IME_Bridge] 强制放行包名验证: " + pkgName);
                        }
                    }
                });
            } catch (Throwable t) {
            }
        }
    }
}
