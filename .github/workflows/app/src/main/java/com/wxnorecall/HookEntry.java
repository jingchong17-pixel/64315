package com.wxnorecall;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** 微信 8.0.77：消息防撤回 + 禁止上报正在输入 */
public class HookEntry implements IXposedHookLoadPackage {

    private static final String TAG = "WxNoRecall";
    private static final String PKG = "com.tencent.mm";

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lp) {
        if (!PKG.equals(lp.packageName)) return;
        ClassLoader cl = lp.classLoader;

        // 1) 消息防撤回：跳过 doRevokeMsg，原消息不会被改写成“对方撤回了一条消息”
        try {
            Class<?> c = XposedHelpers.findClassIfExists("u21.u", cl);
            if (c == null) {
                XposedBridge.log(TAG + ": 未找到 u21.u，防撤回未生效");
            } else {
                XposedHelpers.findAndHookMethod(c, "e",
                        String.class, long.class, "com.tencent.mm.modelbase.p0",
                        String.class, String.class, String.class,
                        new XC_MethodReplacement() {
                            @Override
                            protected Object replaceHookedMethod(MethodHookParam p) {
                                XposedBridge.log(TAG + ": 已丢弃撤回指令 talker="
                                        + p.args[0] + " srvId=" + p.args[1]);
                                return null; // 原方法返回 void
                            }
                        });
                XposedBridge.log(TAG + ": 防撤回已挂载 u21.u#e");
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": 防撤回初始化失败 " + t);
        }

        // 2) 禁止上报正在输入：让微信自带开关恒为 1，走官方分支自己 return
        try {
            Class<?> cfg = XposedHelpers.findClassIfExists("op.j", cl);
            if (cfg != null) {
                XposedBridge.hookAllMethods(cfg, "b", new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam p) {
                        if (p.args.length == 2 && "DisableSendTyping".equals(p.args[0])) {
                            p.setResult(1);
                        }
                    }
                });
            }
            // 兜底：直接掐掉上报出口
            Class<?> sg = XposedHelpers.findClassIfExists(
                    "com.tencent.mm.ui.chatting.component.xm", cl);
            if (sg != null) {
                XposedHelpers.findAndHookMethod(sg, "l0", int.class,
                        new XC_MethodReplacement() {
                            @Override
                            protected Object replaceHookedMethod(MethodHookParam p) {
                                return null;
                            }
                        });
            }
            XposedBridge.log(TAG + ": 正在输入已关闭");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": 正在输入初始化失败 " + t);
        }
    }
}
