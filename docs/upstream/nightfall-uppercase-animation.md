# 3.4.2：kusabimaru 目录下有两个大写文件名，会导致启动崩溃

> 这份是给 `EpicFight Nightfall`（`efn`）作者 `super_awesome_baby` 的报告。
> 该模组没有公开的 issue 地址，所以按发布页 / 群聊能直接粘贴的格式写。

---

**环境**

- Minecraft 1.20.1
- Forge 47.4.28
- EpicFight Nightfall 3.4.2
- 依赖：epicfight 20.14.4+、invincible、epic_fight_avalon、aaa_particles

**现象**

客户端在启动阶段崩溃，堆栈如下：

```
net.minecraft.ResourceLocationException: Non [a-z0-9/._-] character in path of location:
efn:animmodels/animations/biped/sekiro/kusabimaru/data/Ichimonji_1.json.mcmeta
    at net.minecraft.resources.ResourceLocation.<init>
    at net.minecraft.resources.ResourceLocation.tryBuild
    at net.minecraft.server.packs.resources.FallbackResourceManager.getMetadataLocation
    at net.minecraft.server.packs.resources.FallbackResourceManager.listResources
    at net.minecraft.server.packs.resources.FallbackResourceManager.listResourceStacks
```

**原因**

`animmodels/animations/biped/sekiro/kusabimaru/data/` 下有两个文件的文件名带大写 `I`：

```
assets/efn/animmodels/animations/biped/sekiro/kusabimaru/data/Ichimonji_1.json
assets/efn/animmodels/animations/biped/sekiro/kusabimaru/data/Ichimonji_2.json
```

EpicFight 加载动画目录时，会给目录里每个文件拼上 `.mcmeta` 再构造 `ResourceLocation`。  
而 `ResourceLocation` 的路径只接受 `a-z0-9/._-`，大写字母会直接抛异常，游戏在显示窗口前就崩了。

**修法**

改成小写即可：

```
ichimonji_1.json
ichimonji_2.json
```

**顺带一提：这两个文件现在大概是没生效的**

EpicFight 的约定是 `animmodels/animations/<...>/data/<同名动画>.json` 作为该动画的刀光拖尾配置。  
同目录另外 8 个 trail 文件（`dragon_flash`、`sakura_dance`、`kusabimaru_auto1`…`auto5` 等）都能与其上层的动画文件严格同名，只有这两个因为大写的 `I` 配不上 `ichimonji` 动画。

也就是说：改名之后，这两招（一字文系列）的拖尾特效才会真正挂上。

---

## 附：给整合包作者的自查脚本

```python
import zipfile, glob, re, os
bad_path = re.compile(r'[^a-z0-9/._-]')
for jar in glob.glob('mods/*.jar'):
    try:
        z = zipfile.ZipFile(jar)
    except Exception:
        continue
    for n in z.namelist():
        p = n.split('/')
        if len(p) < 3 or p[0] != 'assets':
            continue
        if n.endswith(('.png', '.json')) and bad_path.search('/'.join(p[2:])):
            print(os.path.basename(jar), '->', n)
```
