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

    private static final String TARGET_ANDROID = "android";
    private static final String TARGET_SYSTEM_UI = "com.android.systemui";
    
    // 已确认的目标输入法包名
    private static final List<String> TARGET_IME_PACKAGES = Arrays.asList(
            "com.tencent.wetype", 
            "com.bytedance.android.doubaoime"
    );

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (TARGET_ANDROID.equals(lpparam.packageName) || TARGET_SYSTEM_UI.equals(lpparam.packageName)) {
            injectMiuiImeWhitelistDynamic(lpparam);
        }
    }

    private void injectMiuiImeWhitelistDynamic(XC_LoadPackage.LoadPackageParam lpparam) {
        XposedBridge.log("[IME_Bridge] 正在启动 HyperOS 启发式白名单扫描...");

        String[] targetClasses = {
                "miui.view.inputmethod.InputMethodHelper",
                "android.inputmethodservice.InputMethodServiceInjector",
                "com.miui.inputmethod.InputMethodBottomManager",
                "com.miui.inputmethod.InputMethodBottomManagerHelper"
        };

        for (String className : targetClasses) {
            Class<?> clazz = XposedHelpers.findClassIfExists(className, lpparam.classLoader);
            if (clazz == null) continue;

            // 遍历该类下的所有声明方法
            for (Method method : clazz.getDeclaredMethods()) {
                // 忽略抽象方法与底层 Native 方法
                if (Modifier.isAbstract(method.getModifiers()) || Modifier.isNative(method.getModifiers())) {
                    continue;
                }

                // 拦截策略 1：拦截所有无参且返回列表/数组的方法（可能为获取全局白名单）
                if (method.getParameterTypes().length == 0) {
                    if (List.class.isAssignableFrom(method.getReturnType()) || String[].class.isAssignableFrom(method.getReturnType())) {
                        try {
                            XposedBridge.hookMethod(method, new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    Object result = param.getResult();
                                    if (result == null) return;

                                    if (result instanceof List) {
                                        List<String> supportList = new ArrayList<>((List<String>) result);
                                        boolean modified = false;
                                        for (String pkg : TARGET_IME_PACKAGES) {
                                            if (!supportList.contains(pkg)) {
                                                supportList.add(pkg);
                                                modified = true;
                                            }
                                        }
                                        if (modified) {
                                            param.setResult(supportList);
                                            XposedBridge.log("[IME_Bridge] 动态列表注入成功: " + method.getName());
                                        }
                                    } else if (result instanceof String[]) {
                                        List<String> supportList = new ArrayList<>(Arrays.asList((String[]) result));
                                        boolean modified = false;
                                        for (String pkg : TARGET_IME_PACKAGES) {
                                            if (!supportList.contains(pkg)) {
                                                supportList.add(pkg);
                                                modified = true;
                                            }
                                        }
                                        if (modified) {
                                            param.setResult(supportList.toArray(new String[0]));
                                            XposedBridge.log("[IME_Bridge] 动态数组注入成功: " + method.getName());
                                        }
                                    }
                                }
                            });
                        } catch (Throwable t) {
                            // 屏蔽单一方法挂载异常
                        }
                    }
                }

                // 拦截策略 2：拦截所有返回 boolean 且参数包含 String 的方法（可能为包名鉴权验证）
                if (method.getReturnType() == boolean.class) {
                    Class<?>[] paramTypes = method.getParameterTypes();
                    for (int i = 0; i < paramTypes.length; i++) {
                        if (paramTypes[i] == String.class) {
                            final int stringParamIndex = i; // 锁定包名字符串所在的参数索引
                            try {
                                XposedBridge.hookMethod(method, new XC_MethodHook() {
                                    @Override
                                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                                        String pkgName = (String) param.args[stringParamIndex];
                                        if (TARGET_IME_PACKAGES.contains(pkgName)) {
                                            param.setResult(true);
                                            XposedBridge.log("[IME_Bridge] 动态布尔验证放行 [" + method.getName() + "]: " + pkgName);
                                        }
                                    }
                                });
                            } catch (Throwable t) {
                                // 屏蔽单一方法挂载异常
                            }
                            break; // 该方法已完成 Hook 注册，跳出参数遍历
                        }
                    }
                }
            }
        }
    }
}
