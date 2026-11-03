# v2.0

## 下载

| 文件 | 说明 |
|---|---|
| [`nfc-uid-module-v2.0.zip`](release/nfc-uid-module-v2.0.zip) | KernelSU 模块，先装这个 |
| [`nfc-uid-sog10-v2.0.apk`](release/nfc-uid-sog10-v2.0.apk) | 控制 App，装完模块再装 |

## 安装顺序

```bash
# 1. 模块（会创建 /data/adb/nfc-uid/）
adb push nfc-uid-module-v2.0.zip /sdcard/
adb shell su -c "ksud module install /sdcard/nfc-uid-module-v2.0.zip"

# 2. App
adb install -r nfc-uid-sog10-v2.0.apk

# 3. 重启一次，让模块的 post-fs-data 生效
adb reboot
```

重启后在 KernelSU 里给「卡号模拟」授权 su。

## 这个版本有什么

- **UID 模拟**：通过 NCI `0x33` 参数 + 四条路由覆盖，让卡模拟使用指定 UID
- **不重启切换**：改配置 → 卸载旧挂载 → 重新 bind → 重启 NFC 服务
- **卡片管理**：列表、手动输入、删除、6 条路由单独调整
- **卡片封面**：每张卡可设封面图，自动裁剪为银行卡比例 1.585:1
- **读卡通知**：贴卡时通知栏显示卡号，可直接保存或切换
- **恢复默认**：一键回到原厂 NFC 配置
- **Material 3 Expressive**：大圆角、莫奈取色、底部操作面板

## 已验证

| 项目 | 结果 |
|---|---|
| 切换 UID | ✅ 读卡器识别正确 |
| 多次切换 | ✅ 不再随机（修复了挂载层叠） |
| 恢复默认 | ✅ 回到原厂状态 |
| FeliCa / Suica | ✅ 保留在 eSE，不受影响 |
| 读卡通知 | ✅ |
| 真人刷门禁 | ✅ 用户实测通过 |

## 已知限制

- 只模拟 **UID 层**，不含加密扇区
- **MIFARE Classic 加密卡**无法完整模拟（Crypto1 需要实时响应）
- 部分 App（支付宝碰一碰等）依赖 eSE，切到 UID 模式后会不可用

## 排错速查

```bash
# UID 随机 → 看挂载层数，正常只有 1 行
su -c "mount | grep libnfc-nxp.conf"

# 层叠了 → 重置后重切
su -c "/data/adb/nfc-uid/set.sh reset"
```

---

**完整文档见 [README](../README.md)。**

代码与文档由 AI 辅助生成（作者 Penika175），已在真机反复验证。
