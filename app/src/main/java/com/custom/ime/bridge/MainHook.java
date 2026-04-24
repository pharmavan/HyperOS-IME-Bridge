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
        
        String[] targetClasses = {
                "miui.view.inputmethod.InputMethodHelper",
                "android.inputmethodservice.InputMethodServiceInjector",
                "com.miui.inputmethod.InputMethodBottomManager",
                "com.miui.inputmethod.InputMethodBottomManagerHelper",
                "com.android.systemui.inputmethod.InputMethodBottomManager"
        };

        // 进程效验：如果当前进程未包含任何目标鉴权类，直接退出节约性能
        boolean isTargetProcess = false;
        for (String cls : targetClasses) {
            if (XposedHelpers.findClassIfExists(cls, lpparam.classLoader) != null) {
                isTargetProcess = true;
                break;
            }
        }
        if (!isTargetProcess) return;

        XposedBridge.log("[IME_Bridge] V1.2.0 终极伪装版注入进程: " + lpparam.packageName);

        for (String className : targetClasses) {
            Class<?> clazz = XposedHelpers.findClassIfExists(className, lpparam.classLoader);
            if (clazz == null) continue;

            for (Method method : clazz.getDeclaredMethods()) {
                if (Modifier.isAbstract(method.getModifiers()) || Modifier.isNative(method.getModifiers())) {
                    continue;
                }

                String mName = method.getName().toLowerCase();

                // 策略 1: 列表注入（防 NPE 安全版）
                if (mName.equals("getsupportime") && List.class.isAssignableFrom(method.getReturnType())) {
                    XposedBridge.hookMethod(method, new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                            Object res = param.getResult();
                            if (res instanceof List) {
                                List<String> modified = new ArrayList<>((List<String>) res);
                                boolean changed = false;
                                for (String pkg : TARGET_IMES) {
                                    if (!modified.contains(pkg)) {
                                        modified.add(pkg);
                                        changed = true;
                                    }
                                }
                                if (changed) param.setResult(modified);
                            }
                        }
                    });
                    XposedBridge.log("[IME_Bridge] 挂载列表注入: " + method.getName() + " in " + className);
                }

                // 策略 2: 无差别特征欺骗（修正参数拦截错误）
                if (method.getReturnType() == boolean.class) {
                    boolean isTargetBoolean = false;
                    
                    // 涵盖所有底栏白名单鉴权
                    if (mName.contains("support") || mName.contains("white") || mName.contains("opt")) {
                        isTargetBoolean = true;
                    } 
                    // 涵盖所有小米深度定制版输入法（带剪贴板）判定
                    else if (mName.startsWith("is") && (mName.contains("sogou") || mName.contains("baidu") || mName.contains("miui") || mName.contains("custom"))) {
                        isTargetBoolean = true;
                    }

                    if (isTargetBoolean) {
                        try {
                            XposedBridge.hookMethod(method, new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                    // 无论系统传什么参数过来，一律暴力回答 true
                                    param.setResult(true);
                                }
                            });
                            XposedBridge.log("[IME_Bridge] 挂载布尔欺骗: " + method.getName() + " in " + className);
                        } catch (Throwable t) {}
                    }
                }
            }
        }
    }
}
