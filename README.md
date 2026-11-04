# NFC UID 模拟器（Sony SOG10 / Xperia 1 V）

用一份被覆盖的 NXP NFC 配置，让手机在卡模拟模式下使用**指定的 NFCID1（UID）**，
从而复现 UID 类门禁卡、打卡卡。切换 UID **不需要重启**，也不需要重启 NFC 服务之外的操作。

> 仅用于你自己的设备与你自己有权使用的卡片。请勿用于未授权复制他人卡片。

---

## 功能

- 预设 / 手动输入 8 位十六进制 UID
- 卡片列表，一键切换（约 15 秒生效）
- 每张卡可自定义封面图（1.585:1 银行卡比例，自动居中裁剪）
- 单独显示与修改 6 条 NFC 路由
- 「恢复默认配置」一键回到原厂状态
- 读卡通知：贴卡时通知栏显示卡号，可直接保存 / 切换
- 所有修改持久化（重启后仍生效）

## 界面

| 主界面 | 我的卡 | 设置 | 关于 |
|---|---|---|---|
| 当前卡 + 已保存列表 | 上拉面板，卡片封面 | 通知 / UID 与路由 / 恢复 / 关于 | 版本与声明 |

---

## 原理

### 1. UID 来自哪里

NXP NFC HAL 从 `/vendor/etc/libnfc-nxp.conf` 读取 NCI 参数，其中 `0x33` 是
`NFCID1`（卡模拟时对外呈现的 UID）：

```
33, 04, 11, 22, 33, 44,
```

- `33` = NCI parameter ID
- `04` = 长度（4 字节 UID）
- 后面 4 字节就是 UID

修改这一行、重启 NFC 服务后，卡模拟就会使用新的 UID。

### 2. 为什么只改 UID 会随机

卡模拟的 UID 走哪条通道，由路由决定：

| 路由 | 含义 |
|---|---|
| `DEFAULT_AID_ROUTE` | 按 AID 选卡 |
| `DEFAULT_ISODEP_ROUTE` | ISO-DEP（APDU） |
| `DEFAULT_MIFARE_CLT_ROUTE` | MIFARE Classic |
| `DEFAULT_OFFHOST_ROUTE` | 离卡（HCE） |

`0x00` = host（本机 HCE），`0x01` = eSE，`0x02/0x03` = UICC1/2。

**只有当 AID / ISODEP / MIFARE_CLT / OFFHOST 四条都是 `0x00` 时，UID 才是固定的。**
只改其中一两条，其余请求仍然落到 eSE，读卡器会看到 eSE 的随机 UID。

保留 `FELICA_CLT_ROUTE` 与 `DEFAULT_SYS_CODE_ROUTE` 为 `0x01`（eSE），可以继续使用 Suica 等 FeliCa 卡。

### 3. 挂载层叠的坑

模块用 `mount --bind` 把改好的配置挂到 `/vendor/etc/libnfc-nxp.conf`。

如果多次切换而没有卸载干净，会叠加 2～3 层 bind mount，**HAL 读到的是旧层**，
表现就是「改了但 UID 没变」或者「时而正确时而随机」。

正确做法：

1. 循环 `umount` 直到没有挂载（6 次 + `umount -l` 兜底）
2. 用 `nsenter -t 1 -m` 进入 **init 命名空间**再挂载，否则 App 和 NFC 进程看不到
3. 先 `svc nfc disable`，挂载完成后再 `svc nfc enable`

---

## 构建模块

模块由 `tools/build_uid_module.py` 生成。它需要一份原厂配置作为安全副本：

```bash
# 从设备取出原厂 NFC 配置
adb shell su -c "cat /vendor/etc/libnfc-nxp.conf" > stock.conf

# 生成 nfc-uid.zip
python tools/build_uid_module.py
```

## 安装

### 前置条件

- Sony SOG10（Xperia 1 V，PDX234），Android 15
- **KernelSU**（或 Magisk）已 root
- NFC 硬件正常

### 1. 模块

```bash
# 解压后得到 nfc-uid.zip
ksud module install /sdcard/nfc-uid.zip
```

模块会创建：

```
/data/adb/nfc-uid/
├── stock.conf     原厂配置备份
├── active.conf    当前生效配置（切换时生成）
└── set.sh         控制脚本
```

### 2. App

```bash
adb install -r app-debug.apk
```

首次打开会请求通知权限（用于读卡通知）。

### 3. 手动使用（不装 App 也可以）

```bash
# 切换 UID（自动生成 active.conf + 挂载 + 重启 NFC）
su -c "/data/adb/nfc-uid/set.sh 11223344"

# 同时指定 6 条路由（AID ISODEP MIFARE FELICA SYSCODE OFFHOST）
su -c "/data/adb/nfc-uid/set.sh route 11223344 0 0 0 1 1 0"

# 完全恢复原厂（删除 active.conf + 卸载挂载 + 重启 NFC）
su -c "/data/adb/nfc-uid/set.sh reset"
```

---

## 排错

### UID 还是随机

```bash
# 看挂了几层
su -c "mount | grep libnfc-nxp.conf"
```

正常只有 **1 行**。多行就是层叠，执行一次 `set.sh reset` 再重新切换。

### 切换后完全没反应

```bash
su -c "svc nfc disable; sleep 1; svc nfc enable"
```

或者去系统设置里关闭再打开 NFC。

### App 提示 su 失败

KernelSU 里确认 App 已授权（超级用户 → 卡号模拟 → 允许）。

### 读卡器读不到

- 确认读卡器支持 13.56 MHz 的 Type A / MIFARE
- 部分门禁只认 CPU 卡（APDU），UID 模拟无效
- 手机 NFC 天线在背面偏上，贴的位置要对准

---

## 已知限制

- **只模拟 UID 层**，不复制加密扇区数据
- **MIFARE Classic 加密卡无法完整模拟**：Crypto1 认证需要实时响应，
  主机侧没有可用的 NCI listen 转发通道，时序上做不到
- **CPU 卡 / 加密卡**需要原卡参与，UID 模拟无效
- 支付宝「碰一碰」支付走 **HCE（host）**，实测在 UID 模式下仍可正常使用，无需恢复默认。
- 少数依赖 eSE 的银行/交通 App 如遇异常，用「恢复默认配置」后重试。

---

## 项目结构

```
.
├── app/                          Android App（Java，Material 3 Expressive）
│   └── src/main/
│       ├── java/com/local/nfcuid/
│       │   ├── MainActivity.java          主界面 + 上拉卡片面板
│       │   ├── SettingsActivity.java      设置
│       │   ├── SettingsRouteActivity.java UID 与路由
│       │   ├── SettingsNotificationsActivity.java
│       │   ├── AboutActivity.java
│       │   ├── NotificationHelper.java    通知
│       │   └── Prefs.java                 偏好
│       └── res/
├── tools/
│   └── build_uid_module.py      模块构建脚本
├── docs/screenshots/            界面截图
└── README.md
```

---

## 声明

- 本项目的**代码与文档由 AI 辅助生成**（作者 Penika175），经过在真机上反复验证与修正。
- 仅用于学习研究与**自有设备、自有卡片**的合法场景。
- 禁止用于未授权复制他人门禁卡、考勤卡或其它访问凭证。
- 使用本工具产生的一切后果由使用者自行承担。

## License

MIT
