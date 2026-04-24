package com.custom.ime.bridge;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

public class MainHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        
        // 锁定 HyperOS 底部导航栏注入核心类
        String[] targetClasses = {
                "miui.view.inputmethod.InputMethodHelper",
                "android.inputmethodservice.InputMethodServiceInjector"
        };

        for (String className : targetClasses) {
            Class<?> clazz = XposedHelpers.findClassIfExists(className, lpparam.classLoader);
            if (clazz == null) continue;

            for (Method method : clazz.getDeclaredMethods()) {
                if (Modifier.isAbstract(method.getModifiers()) || Modifier.isNative(method.getModifiers())) {
                    continue;
                }
                
                // 仅拦截返回 boolean 且包含特征名的方法（如 isImeSupport, isSplitImeSupport）
                if (method.getReturnType() == boolean.class) {
                    String name = method.getName().toLowerCase();
                    if (name.contains("support") || name.contains("white") || name.contains("opt")) {
                        try {
                            XposedBridge.hookMethod(method, new XC_MethodHook() {
                                @Override
                                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                    // 强制无条件返回 true，直接切断底层判断逻辑
                                    param.setResult(true);
                                    XposedBridge.log("[IME_Bridge] 精准放行 [" + method.getName() + "] in " + lpparam.packageName);
                                }
                            });
                        } catch (Throwable t) {
                            // 屏蔽异常，保证其他 Hook 继续执行
                        }
                    }
                }
            }
        }
    }
}
