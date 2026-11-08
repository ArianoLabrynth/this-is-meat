package com.meatdetector;

import com.mojang.datafixers.util.Pair;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Finds food items that are actually meat and rewrites their {@link FoodProperties} so the
 * vanilla "is meat" flag is set. Origins/Apoli's {@code origins:meat} condition reads exactly
 * that flag, so every carnivore style check starts accepting modded meat for free.
 */
public final class MeatScanner {

    private static final Logger LOGGER = MeatDetector.LOGGER;

    /** Item -> human readable reason, for logging and for the debug command. */
    private static final Set<Item> MEAT = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final List<String> REPORT = new ArrayList<>();

    /** Resolved once: the single FoodProperties field on Item. Found by type so SRG names do not matter. */
    private static final Field FOOD_FIELD = findFoodField();

    /** Fallback writer, only resolved when the reflective write is refused. */
    private static Object UNSAFE;
    private static long UNSAFE_OFFSET = -1L;
    private static boolean UNSAFE_RESOLVED;
    private static boolean WRITE_FAILED;
    private static boolean STAGE1_DONE;

    private MeatScanner() {
    }

    private static Field findFoodField() {
        for (Field field : Item.class.getDeclaredFields()) {
            if (field.getType() == FoodProperties.class) {
                try {
                    field.setAccessible(true);
                    return field;
                } catch (Throwable t) {
                    LOGGER.error("[MeatDetector] Could not access {}.{}: {}",
                            Item.class.getName(), field.getName(), t.toString());
                    return null;
                }
            }
        }
        LOGGER.error("[MeatDetector] No FoodProperties field found on Item - mod disabled.");
        return null;
    }

    public static boolean isAvailable() {
        return FOOD_FIELD != null;
    }

    public static FoodProperties foodOf(Item item) {
        if (FOOD_FIELD == null) {
            return null;
        }
        try {
            return (FoodProperties) FOOD_FIELD.get(item);
        } catch (Throwable t) {
            return null;
        }
    }

    public static boolean isMarked(Item item) {
        return MEAT.contains(item);
    }

    public static int markedCount() {
        return MEAT.size();
    }

    /** Whether the registry scan has run successfully at least once. */
    public static boolean stage1Done() {
        return STAGE1_DONE;
    }

    public static List<String> report() {
        return REPORT;
    }

    // ---------------------------------------------------------------- stages

    /** Stage 1: registry id + config lists. Called before the game window shows up. */
    public static int scanRegistryIds() {
        if (!isAvailable()) {
            return 0;
        }
        if (STAGE1_DONE) {
            // Already done. Safe to call again but pointless, and re-running would only spam the log.
            return 0;
        }
        if (!Config.SPEC.isLoaded()) {
            LOGGER.warn("[MeatDetector] Config is not loaded yet - stage 1 skipped.");
            return 0;
        }
        MeatRules.setExtraKeywords(Config.EXTRA_KEYWORDS.get());
        int added = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            if (id == null) {
                continue;
            }
            if (isBlacklisted(id)) {
                continue;
            }
            if (isWhitelisted(id)) {
                if (mark(item, "whitelist")) {
                    added++;
                }
                continue;
            }
            if (!Config.MATCH_ITEM_ID.get()) {
                continue;
            }
            if (markIfMeatName(item, id.toString(), "item id")) {
                added++;
            }
        }
        LOGGER.info("[MeatDetector] Stage 1 (item id): {} food items flagged, {} total", added, MEAT.size());
        STAGE1_DONE = true;
        return added;
    }

    /** Stage 2: recipe graph. Needs a loaded recipe manager, so it runs once a world exists. */
    public static int scanRecipes(RecipeManager recipeManager, net.minecraft.core.RegistryAccess registryAccess) {
        if (!isAvailable() || recipeManager == null || !Config.SPEC.isLoaded() || !Config.MATCH_RECIPES.get()) {
            return 0;
        }
        int added = 0;
        // Two propagation passes: raw meat -> cooked meat -> meat dish.
        for (int pass = 0; pass < 2; pass++) {
            int passAdded = 0;
            Collection<Recipe<?>> recipes;
            try {
                recipes = recipeManager.getRecipes();
            } catch (Throwable t) {
                LOGGER.warn("[MeatDetector] Could not read recipes: {}", t.toString());
                return added;
            }
            for (Recipe<?> recipe : recipes) {
                ItemStack result;
                try {
                    result = recipe.getResultItem(registryAccess);
                } catch (Throwable t) {
                    continue;
                }
                if (result == null || result.isEmpty()) {
                    continue;
                }
                Item out = result.getItem();
                if (isMarked(out)) {
                    continue;
                }
                ResourceLocation outId = BuiltInRegistries.ITEM.getKey(out);
                if (outId != null && (isBlacklisted(outId) || !isFood(out))) {
                    continue;
                }
                if (hasMeatIngredient(recipe)) {
                    if (mark(out, "recipe " + recipe.getId())) {
                        added++;
                        passAdded++;
                    }
                }
            }
            if (passAdded == 0) {
                break;
            }
        }
        LOGGER.info("[MeatDetector] Stage 2 (recipes): {} more food items flagged, {} total", added, MEAT.size());
        return added;
    }

    /** Stage 3: translated display name. Client only, handled by {@link ClientDetection}. */
    public static int scanDisplayName(String name, Item item) {
        if (!isAvailable() || !Config.SPEC.isLoaded() || !Config.MATCH_DISPLAY_NAME.get() || name == null) {
            return 0;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        if (id != null && (isBlacklisted(id) || isMarked(item) || !isFood(item))) {
            return 0;
        }
        if (markIfMeatName(item, name, "display name")) {
            LOGGER.info("[MeatDetector] Stage 3 (display name): {} total", MEAT.size());
            return 1;
        }
        return 0;
    }

    // ---------------------------------------------------------------- helpers

    private static boolean isFood(Item item) {
        FoodProperties props = foodOf(item);
        return props != null;
    }

    private static boolean markIfMeatName(Item item, String text, String source) {
        if (!isFood(item)) {
            return false;
        }
        if (!MeatRules.matches(text)) {
            return false;
        }
        return mark(item, source + ": " + text);
    }

    private static boolean hasMeatIngredient(Recipe<?> recipe) {
        try {
            for (Ingredient ingredient : recipe.getIngredients()) {
                for (ItemStack stack : ingredient.getItems()) {
                    if (stack.isEmpty()) {
                        continue;
                    }
                    Item candidate = stack.getItem();
                    if (isMarked(candidate) || MeatRules.matches(idPath(candidate))) {
                        return true;
                    }
                }
            }
        } catch (Throwable ignored) {
            // Some custom ingredients explode when the tag is not bound yet; skip them.
        }
        return false;
    }

    private static String idPath(Item item) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
        return id == null ? "" : id.getPath();
    }

    /**
     * Rewrites the item's food properties with {@code meat = true}.
     *
     * @return true when the item was newly flagged
     */
    public static boolean mark(Item item, String reason) {
        if (FOOD_FIELD == null || item == null) {
            return false;
        }
        if (MEAT.contains(item)) {
            return false;
        }
        FoodProperties current = foodOf(item);
        if (current == null) {
            return false;
        }
        if (current.isMeat()) {
            // Already meat by its own author - nothing to do, but remember it for recipe propagation.
            MEAT.add(item);
            return false;
        }
        FoodProperties patched = withMeat(current);
        if (!writeFood(item, patched)) {
            return false;
        }
        MEAT.add(item);
        String id = String.valueOf(ForgeRegistries.ITEMS.getKey(item));
        REPORT.add(id + "  <-  " + reason);
        if (Config.SPEC.isLoaded() && Config.LOG_RESULTS.get()) {
            LOGGER.debug("[MeatDetector] flagged {} ({})", id, reason);
        }
        return true;
    }

    private static FoodProperties withMeat(FoodProperties src) {
        FoodProperties.Builder builder = new FoodProperties.Builder()
                .nutrition(src.getNutrition())
                .saturationMod(src.getSaturationModifier())
                .meat();
        if (src.canAlwaysEat()) {
            builder.alwaysEat();
        }
        if (src.isFastFood()) {
            builder.fast();
        }
        for (Pair<MobEffectInstance, Float> effect : src.getEffects()) {
            builder.effect(effect.getFirst(), effect.getSecond());
        }
        return builder.build();
    }

    /**
     * Writes the replacement properties back onto the item and verifies the write really landed.
     * <p>
     * Normal reflection is tried first. If the field is refused (or the write silently does not
     * stick, which can happen when a mod re-declares the field), the same slot is written through
     * {@code sun.misc.Unsafe}. The whole mod hinges on this one write, so it is verified instead of
     * assumed, and a hard failure is reported once loudly rather than once per item.
     */
    private static boolean writeFood(Item item, FoodProperties patched) {
        try {
            FOOD_FIELD.set(item, patched);
        } catch (Throwable t) {
            if (!unsafeWrite(item, patched)) {
                reportWriteFailure(item, t);
                return false;
            }
        }
        if (foodOf(item) != patched) {
            if (!unsafeWrite(item, patched)) {
                reportWriteFailure(item, null);
                return false;
            }
        }
        return true;
    }

    private static void reportWriteFailure(Item item, Throwable cause) {
        if (WRITE_FAILED) {
            return;
        }
        WRITE_FAILED = true;
        LOGGER.error("[MeatDetector] Could not change the food properties of {}. This mod cannot work "
                + "on this setup; please report it together with the line below.",
                String.valueOf(ForgeRegistries.ITEMS.getKey(item)));
        if (cause != null) {
            LOGGER.error("[MeatDetector] Cause:", cause);
        }
    }

    /** Last-resort write, used only when reflective field assignment is not allowed. */
    private static boolean unsafeWrite(Item item, FoodProperties patched) {
        Object unsafe = unsafe();
        if (unsafe == null) {
            return false;
        }
        try {
            Method put = unsafe.getClass().getMethod("putObject", Object.class, long.class, Object.class);
            put.invoke(unsafe, item, UNSAFE_OFFSET, patched);
            return true;
        } catch (Throwable t) {
            LOGGER.debug("[MeatDetector] Unsafe write failed: {}", t.toString());
            return false;
        }
    }

    private static Object unsafe() {
        if (UNSAFE_RESOLVED) {
            return UNSAFE;
        }
        UNSAFE_RESOLVED = true;
        try {
            Class<?> clazz = Class.forName("sun.misc.Unsafe");
            Field field = clazz.getDeclaredField("theUnsafe");
            field.setAccessible(true);
            UNSAFE = field.get(null);
            if (UNSAFE != null && FOOD_FIELD != null) {
                Method offset = clazz.getMethod("objectFieldOffset", Field.class);
                UNSAFE_OFFSET = ((Number) offset.invoke(UNSAFE, FOOD_FIELD)).longValue();
            }
        } catch (Throwable t) {
            LOGGER.debug("[MeatDetector] sun.misc.Unsafe unavailable: {}", t.toString());
            UNSAFE = null;
        }
        return UNSAFE;
    }

    private static boolean isWhitelisted(ResourceLocation id) {
        return matchesList(Config.WHITELIST.get(), id);
    }

    private static boolean isBlacklisted(ResourceLocation id) {
        return matchesList(Config.BLACKLIST.get(), id);
    }

    private static boolean matchesList(List<? extends String> list, ResourceLocation id) {
        if (list == null || list.isEmpty()) {
            return false;
        }
        String full = id.toString();
        String namespace = id.getNamespace();
        for (String raw : list) {
            if (raw == null) {
                continue;
            }
            String entry = raw.trim();
            if (entry.isEmpty() || entry.startsWith("#")) {
                continue;
            }
            if (entry.endsWith(":*")) {
                if (namespace.equals(entry.substring(0, entry.length() - 2))) {
                    return true;
                }
            } else if (entry.equals(full)) {
                return true;
            }
        }
        return false;
    }

    /** Server side entry point, keeps the server reference out of the callers. */
    public static int scanRecipes(MinecraftServer server) {
        if (server == null) {
            return 0;
        }
        try {
            return scanRecipes(server.getRecipeManager(), server.registryAccess());
        } catch (Throwable t) {
            LOGGER.warn("[MeatDetector] Recipe scan failed: {}", t.toString());
            return 0;
        }
    }
}
