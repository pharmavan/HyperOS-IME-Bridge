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
        XposedBridge.log("[IME_Bridge] [" + lpparam.packageName + "] 正在启动 HyperOS 绝杀版白名单扫描...");

        String[] targetClasses = {
                "miui.view.inputmethod.InputMethodHelper",
                "android.inputmethodservice.InputMethodServiceInjector",
                "com.miui.inputmethod.InputMethodBottomManager",
                "com.miui.inputmethod.InputMethodBottomManagerHelper",
                "com.android.systemui.inputmethod.InputMethodBottomManager",
                "com.miui.systemui.inputmethod.InputMethodBottomManager"
        };

        boolean foundAnyClass = false;

        for (String className : targetClasses) {
            Class<?> clazz = XposedHelpers.findClassIfExists(className, lpparam.classLoader);
            if (clazz == null) continue;

            foundAnyClass = true;
            XposedBridge.log("[IME_Bridge] [" + lpparam.packageName + "] 成功命中底层类: " + className);

            for (Method method : clazz.getDeclaredMethods()) {
                if (Modifier.isAbstract(method.getModifiers()) || Modifier.isNative(method.getModifiers())) {
                    continue;
                }

                // 绝杀策略 1：拦截返回列表/数组的方法
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
                        XposedBridge.log("[IME_Bridge] 已挂载列表鉴权方法: " + method.getName());
                    } catch (Throwable t) {}
                }

                // 绝杀策略 2：无视参数类型，强行拦截布尔鉴权方法
                if (method.getReturnType() == boolean.class) {
                    String mName = method.getName().toLowerCase();
                    // 锁定可能与输入法白名单或全面屏优化相关的特征名
                    if (mName.contains("support") || mName.contains("white") || mName.contains("full") || mName.contains("opt")) {
                        try {
                            XposedBridge.hookMethod(method, new XC_MethodHook() {
                                @Override
                                protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                                    boolean originalResult = false;
                                    if (param.getResult() != null) {
                                        originalResult = (boolean) param.getResult();
                                    }
                                    
                                    if (originalResult) return; // 系统本来就放行的不干预

                                    // 检查参数堆栈中是否包含我们的输入法包名（处理 InputMethodInfo 等复杂对象传参）
                                    boolean shouldForceTrue = false;
                                    if (param.args == null || param.args.length == 0) {
                                        // 无参方法，直接盲狙放行
                                        shouldForceTrue = true;
                                    } else {
                                        for (Object arg : param.args) {
                                            if (arg == null) continue;
                                            String argStr = arg.toString();
                                            for (String pkg : TARGET_IME_PACKAGES) {
                                                if (argStr.contains(pkg)) {
                                                    shouldForceTrue = true;
                                                    break;
                                                }
                                            }
                                        }
                                    }

                                    if (shouldForceTrue) {
                                        param.setResult(true);
                                        XposedBridge.log("[IME_Bridge] 布尔鉴权强势放行 -> true [" + method.getName() + "]");
                                    }
                                }
                            });
                            XposedBridge.log("[IME_Bridge] 已挂载布尔鉴权方法: " + method.getName());
                        } catch (Throwable t) {}
                    }
                }
            }
        }

        if (!foundAnyClass) {
            XposedBridge.log("[IME_Bridge] [" + lpparam.packageName + "] 严重警告: 未找到任何已知鉴权类，HyperOS 可能已将底栏逻辑迁移至其他包！");
        }
    }
}
