# 净读 TXT 1.0.0 交付说明

状态：签名候选产物已生成并通过 API 36 模拟器 Release smoke；Android 真机 P0 矩阵与 Play Console 上架材料待验收。

## 产物

- Debug：`android-prototype/dist/android/1.0.0/jingdu-1.0.0-debug.apk`，包名 `com.junchen.jingdu.debug`；
- Release APK：`android-prototype/dist/android/1.0.0/jingdu-1.0.0-release.apk`，包名 `com.junchen.jingdu`；
- Play AAB：`android-prototype/dist/android/1.0.0/jingdu-1.0.0-release.aab`；
- R8 映射：`mapping-1.0.0.txt`；
- 完整性：`SHA256SUMS`。

本次候选 SHA-256：

- Debug APK：`9682e5ac90f5c4e8f38b467c38c416af52dc2493c2f15a5de9631dc99257e667`；
- Release APK：`072e78c5341968d30c57885045551aec3060b1a6928082da7090f523b3c005a6`；
- Release AAB：`f7e6099ab7eafcc415662da163d18757c1a49a6349a972ed3cc29a724241fe6a`。

Debug 用于崩溃日志定位；测试用户应使用签名 Release APK 或 Play 内部测试。

## 启动闪退修复

旧 Debug 在 `readerAppearance` 加载前配置 edge-to-edge 系统栏，图标外观更新解引用空状态，造成首屏确定性崩溃。当前版本改为先加载阅读外观，再配置系统栏，并保留空安全默认。

Android ICU 的 `CharsetDecoder` 在报告错误时会推进输入位置，桌面 JDK 则把位置保留在错误范围起点；旧恢复循环在 Android 上二次前移，可能跳过错误字节后的一个合法字符。当前版本通过独立运行时探针校准游标语义，并严格恢复到报告错误范围末端。主机 42 场景与 API 36 七编码 instrumentation 均通过；130 处损坏 UTF-8 Release 夹具确认首偏移 2091、正文 `ERROR-001=>�<=ERROR-001`，外部源文件未被修改。

## 兼容边界

- minSdk 26（Android 8.0），targetSdk 36；
- 新 Debug 与 Release 可共存；
- 旧 `com.jingdu.txt.w0` 与新包不是同一升级链，私有书架数据不会自动迁移；
- 上传密钥已生成，但注册 Play App Signing 前必须先离线备份。

## API 36 模拟器验收

- Release APK 经真实 `ACTION_OPEN_DOCUMENT` 导入 269,521 字节夹具，完整统计 130 处替换并保留前 128 处定位；
- 自动滚动从锚点 2060 推进到 2160，触摸后在 2198 停止；进入后台后强制停止，冷启动恢复到 2060；
- 系统 Google TTS 接收合成请求，朗读与自动滚动互斥，应用进程无 `AndroidRuntime` 崩溃；
- APK 为 `com.junchen.jingdu`、versionCode 1、versionName 1.0.0、minSdk 26、targetSdk 36，无权限声明；APK v2 与 AAB JAR 签名验证通过。

该证据可放行内部工程测试候选，不替代 API 26–35、不同 OEM 真机、TalkBack、100/300MiB、功耗与后台音频矩阵，也不替代 Play Console 的 Data safety、内容分级、隐私政策、商店截图和测试轨道验收。

## 真机启动复验

```bash
adb install -r android-prototype/dist/android/1.0.0/jingdu-1.0.0-debug.apk
adb logcat -c
adb shell am start -W -n com.junchen.jingdu.debug/com.junchen.jingdu.MainActivity
adb logcat -d -v threadtime AndroidRuntime:E '*:S'
```

验收：`am start` 返回 `Status: ok`，首屏可见，`AndroidRuntime` 无 `FATAL EXCEPTION`。随后安装 Release APK 重复同样检查，并执行 `MVP_TEST_GUIDE.md`。
