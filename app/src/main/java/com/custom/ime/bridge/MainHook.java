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

    private static final List<String> TARGET_IMES = Arrays.asList(
            "com.tencent.wetype",
            "com.bytedance.android.doubaoime"
    );

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        
        // 全面覆盖系统核心与 SystemUI 中的底栏控制类
        String[] targetClasses = {
                "miui.view.inputmethod.InputMethodHelper",
                "android.inputmethodservice.InputMethodServiceInjector",
                "com.miui.inputmethod.InputMethodBottomManager",
                "com.miui.inputmethod.InputMethodBottomManagerHelper",
                "com.android.systemui.inputmethod.InputMethodBottomManager"
        };

        for (String className : targetClasses) {
            Class<?> clazz = XposedHelpers.findClassIfExists(className, lpparam.classLoader);
            if (clazz == null) continue;

            for (Method method : clazz.getDeclaredMethods()) {
                if (Modifier.isAbstract(method.getModifiers()) || Modifier.isNative(method.getModifiers())) {
                    continue;
                }

                // 策略 1: 精准定点狙击白名单列表（修复此前强转导致的 NPE 内存崩溃）
                if (method.getName().equals("getSupportIme") && List.class.isAssignableFrom(method.getReturnType())) {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            Object res = param.getResult();
                            if (res instanceof List) {
                                // 深度克隆系统原始列表，安全注入目标包名
                                List<String> modified = new ArrayList<>((List<String>) res);
                                boolean changed = false;
                                for (String pkg : TARGET_IMES) {
                                    if (!modified.contains(pkg)) {
                                        modified.add(pkg);
                                        changed = true;
                                    }
                                }
                                if (changed) {
                                    param.setResult(modified);
                                    XposedBridge.log("[IME_Bridge] 成功注入列表: " + method.getName() + " in " + lpparam.packageName);
                                }
                            }
                        }
                    });
                }

                // 策略 2: 全量劫持包名特征验证（欺骗系统使其渲染 MIUI 专属剪贴板底栏）
                if (method.getReturnType() == boolean.class) {
                    Class<?>[] params = method.getParameterTypes();
                    // 拦截接收 String (包名) 的布尔方法
                    if (params.length == 1 && params[0] == String.class) {
                        String mName = method.getName().toLowerCase();
                        // 涵盖 isImeSupport, isSogouIme, isBaiduIme, isMiuiCustomIme 等所有判定
                        if (mName.startsWith("is") || mName.contains("support") || mName.contains("white") || mName.contains("ime")) {
                            XposedBridge.hookMethod(method, new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                    String arg = (String) param.args[0];
                                    if (arg != null) {
                                        for (String target : TARGET_IMES) {
                                            if (arg.contains(target)) {
                                                param.setResult(true);
                                                XposedBridge.log("[IME_Bridge] 包名深度伪装放行 [" + method.getName() + "] -> " + target + " in " + lpparam.packageName);
                                                return;
                                            }
                                        }
                                    }
                                }
                            });
                        }
                    }
                    // 策略 3: 强制开启底栏全局状态开关
                    else if (params.length == 0) {
                        String mName = method.getName().toLowerCase();
                        if (mName.contains("bottom") && (mName.contains("enable") || mName.contains("show"))) {
                            XposedBridge.hookMethod(method, new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                    param.setResult(true);
                                    XposedBridge.log("[IME_Bridge] 底部全局开关放行 [" + method.getName() + "] in " + lpparam.packageName);
                                }
                            });
                        }
                    }
                }
            }
        }
    }
}
