# Uppercase file name in `textures/item` crashes resource reload

Target: https://github.com/Brian-Wuest/MC-Prefab/issues

---

**Environment**

- Minecraft 1.20.1
- Forge 47.4.28
- Prefab 1.10.0.1

**What happens**

The client crashes on startup while the resource manager enumerates `textures/item`:

```
net.minecraft.ResourceLocationException: Non [a-z0-9/._-] character in path of location:
prefab:textures/item/item_start_house_Old.png.mcmeta
    at net.minecraft.resources.ResourceLocation.<init>
    at net.minecraft.resources.ResourceLocation.tryBuild
    at net.minecraft.server.packs.resources.FallbackResourceManager.getMetadataLocation
    at net.minecraft.server.packs.resources.FallbackResourceManager.listResources
    at net.minecraft.server.packs.resources.FallbackResourceManager.listResourceStacks
```

**Cause**

`assets/prefab/textures/item/item_start_house_Old.png` contains an uppercase `O`.

When listing a resource directory, `FallbackResourceManager` appends `.mcmeta` to every file
it finds and builds a `ResourceLocation` from the result. `ResourceLocation` only allows
`a-z0-9/._-` in the path, so the uppercase letter throws before the game window even appears.

Vanilla resource packs never hit this because their file names are always lowercase, so the
constraint only becomes visible with mod-provided resources.

**Fix**

Rename the file to lowercase:

```
assets/prefab/textures/item/item_start_house_Old.png
assets/prefab/textures/item/item_start_house_old.png
```

That is the whole change — the texture is not referenced from any model or blockstate inside
the jar, so nothing else needs to be updated.

**Suggestion**

Mojang's resource loading assumes lower-case file names throughout. If you build the jar on
Windows, consider running a check over `assets/**` for `[A-Z]` in the path before release;
any `.png` / `.json` that fails it will crash the client on some setups.
