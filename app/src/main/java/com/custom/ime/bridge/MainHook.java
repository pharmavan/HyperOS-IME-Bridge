package com.custom.ime.bridge;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainHook implements IXposedHookLoadPackage {

    private static final List<String> TARGET_IME_PACKAGES = Arrays.asList(
            "com.tencent.wetype", 
            "com.bytedance.android.doubaoime"
    );

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if ("android".equals(lpparam.packageName) || "com.android.systemui".equals(lpparam.packageName)) {
            XposedBridge.log("[IME_Bridge] [" + lpparam.packageName + "] 启动 v0.11.0 全量特征劫持...");
            
            String[] targetClasses = {
                "miui.view.inputmethod.InputMethodHelper",
                "android.inputmethodservice.InputMethodServiceInjector",
                "com.miui.inputmethod.InputMethodBottomManager",
                "com.miui.inputmethod.InputMethodBottomManagerHelper",
                "com.android.internal.inputmethod.InputMethodPrivilegedOperations",
                "com.android.server.inputmethod.InputMethodManagerService"
            };

            for (String className : targetClasses) {
                Class<?> clazz = XposedHelpers.findClassIfExists(className, lpparam.classLoader);
                if (clazz == null) continue;

                XposedBridge.log("[IME_Bridge] 扫描目标类: " + className);

                for (Method method : clazz.getDeclaredMethods()) {
                    // 策略 A: 强制放行所有返回布尔值且参数中包含 String 的方法 (核心鉴权点)
                    if (method.getReturnType() == boolean.class) {
                        Class<?>[] params = method.getParameterTypes();
                        for (int i = 0; i < params.length; i++) {
                            if (params[i] == String.class) {
                                final int index = i;
                                try {
                                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                                        @Override
                                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                            String inputPkg = (String) param.args[index];
                                            if (TARGET_IME_PACKAGES.contains(inputPkg)) {
                                                param.setResult(true);
                                                XposedBridge.log("[IME_Bridge] 全量拦截生效: " + method.getName() + " -> " + inputPkg);
                                            }
                                        }
                                    });
                                } catch (Throwable t) {}
                                break;
                            }
                        }
                    }

                    // 策略 B: 强制向返回的列表/数组中注入目标包名
                    if (List.class.isAssignableFrom(method.getReturnType()) || String[].class.isAssignableFrom(method.getReturnType())) {
                        try {
                            XposedBridge.hookMethod(method, new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    Object res = param.getResult();
                                    if (res == null) return;
                                    if (res instanceof List) {
                                        List<String> list = new ArrayList<>((List<String>) res);
                                        for (String p : TARGET_IME_PACKAGES) if (!list.contains(p)) list.add(p);
                                        param.setResult(list);
                                    } else if (res instanceof String[]) {
                                        List<String> list = new ArrayList<>(Arrays.asList((String[]) res));
                                        for (String p : TARGET_IME_PACKAGES) if (!list.contains(p)) list.add(p);
                                        param.setResult(list.toArray(new String[0]));
                                    }
                                }
                            });
                        } catch (Throwable t) {}
                    }
                }
            }
        }
    }
}
