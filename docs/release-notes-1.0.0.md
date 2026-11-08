## 这是肉食 1.0.0 / This Is Meat 1.0.0

使食肉类的起源（Origins / Apoli 的 `origins:meat`）正确识别其他模组添加的肉食。

Makes carnivore-style origins recognise meat added by other mods.

### 这个版本做了什么

- 启动时扫描物品注册表，把“看起来是肉”的食物的 `FoodProperties.isMeat` 标志位写回物品本身
- 三段判定：注册 ID 关键词 → 配方图传播（两轮，生肉→熟肉→肉菜）→ 客户端显示名
- 完全独立：**不需要 Origins、Hell's Gate 或任何前置库**，装了才生效，删掉无残留
- 内置中英关键词表，含水产（鱼虾蟹贝）与仿荤排除项（素肉、植物肉、vegan、seitan…）
- 配置文件可加关键词、白名单、黑名单（支持 `modid:*`）
- 调试命令 `/thisismeat list`、`/thisismeat status`

### 安装

把 `thisismeat-1.20.1-1.0.0.jar` 放进 `mods/`。单机直接生效；服务器装服务端即可，客户端可选。

### 环境

- Minecraft 1.20.1
- Forge 47.x
- 前置模组：无

### 已验证

对着 1.20.1 整合包的语言文件离线跑过 3233 个物品，命中 306 个，`artifacts:eternal_steak`（永恒牛排）、
`artifacts:everlasting_beef`（持久牛肉）均在第一时间命中；仿荤食品与刷怪蛋正确排除。

### 已知限制

- 判定是启发式的：靠名字和配方推断“这是不是肉”，宁可多判不漏判。若某个物品被误判，加进 `blacklist`
- 副作用：任何读取 `isMeat` 的模组都会看到这些食物是肉（这在语义上是正确的）
- 只做了 1.20.1 Forge

### 附：本整合包遇到的两个上游崩溃

与本模组无关，但都是别的模组 jar 里的大写文件名导致的启动崩溃，修法见 README 的「已知上游问题」。

- EpicFight Nightfall 3.4.2：`animmodels/.../kusabimaru/data/Ichimonji_1.json`、`Ichimonji_2.json`
- Prefab 1.10.0.1：`textures/item/item_start_house_Old.png`

---

MIT License · by ArianoLabrynth
