# 关系图谱

一个运行在 Android 上的离线人际关系和家庭关系管理应用。

## 当前功能

- 创建、编辑和删除人物资料
- 记录电话、生日、地址、标签、备注和头像
- 记录家庭与社交关系，并允许同一对人物存在多种关系
- 总关系图浏览、缩放、拖动、搜索和分类筛选
- 家谱、社交、全部三种视图，家谱按辈分分层排列
- 设置唯一的“我”，作为辈分和图谱排列基准
- 图谱节点显示头像和完整姓名，并根据家庭、社交、混合关系显示不同颜色
- 配偶、兄弟姐妹和社交关系使用不同颜色与线型
- 关系文字默认隐藏，点选人物或放大图谱后显示
- 内置可关闭的图谱图例
- 6 位以上数字密码，可配合指纹或面容解锁
- 离开 1 分钟后重新锁定，熄屏立即锁定
- 使用单独密码导出和恢复加密备份
- 不申请网络权限，不包含账号、云同步或分享功能

## 安装 APK

可直接安装的 APK 位于：

```text
dist/relationship-graph-v1.2.0.apk
```

把该文件传到 Android 手机后打开。系统首次提示时，允许当前文件管理器“安装未知应用”。

## 数据安全

- 数据保存在应用私有目录的加密数据库中。
- 密钥由 Android Keystore 管理。
- App 不申请网络权限。
- 系统云备份和设备迁移已关闭。
- 导出的 `.rgbackup` 文件使用单独设置的密码加密。
- App 密码和备份密码均无法联网找回，请妥善保管并定期导出备份。

## 使用 Android Studio

1. 使用 Android Studio 打开本目录。
2. 等待 Gradle 同步完成。
3. 连接 Android 8.0 及以上手机。
4. 点击 Run 运行，或执行 `assembleDebug` 构建调试版 APK。

开发环境要求 JDK 17、Android SDK 35 和 Android Build Tools 35。

## 本地构建命令

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug
```

正式构建文件会生成在：

```text
app/build/outputs/apk/debug/app-debug.apk
```
