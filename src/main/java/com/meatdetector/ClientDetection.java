package com.meatdetector;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Client only: the translated name is the last piece of evidence, and language files are only
 * available once the client is fully up, so this runs lazily on the first tick of a loaded world.
 */
@Mod.EventBusSubscriber(modid = MeatDetector.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientDetection {

    private static boolean namesDone;
    private static boolean recipesDone;

    private ClientDetection() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (namesDone && recipesDone) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }
        if (!recipesDone) {
            recipesDone = true;
            try {
                MeatScanner.scanRecipes(minecraft.level.getRecipeManager(), minecraft.level.registryAccess());
            } catch (Throwable t) {
                MeatDetector.LOGGER.warn("[MeatDetector] Client recipe scan failed: {}", t.toString());
            }
        }
        if (!namesDone) {
            namesDone = true;
            scanNames();
        }
    }

    private static void scanNames() {
        if (!Config.SPEC.isLoaded() || !Config.MATCH_DISPLAY_NAME.get()) {
            return;
        }
        int added = 0;
        for (Item item : BuiltInRegistries.ITEM) {
            String name;
            try {
                name = I18n.get(item.getDescriptionId());
            } catch (Throwable t) {
                continue;
            }
            if (name == null || name.isEmpty()) {
                continue;
            }
            added += MeatScanner.scanDisplayName(name, item);
        }
        MeatDetector.LOGGER.info("[MeatDetector] Stage 3 (display name): {} more flagged, {} total",
                added, MeatScanner.markedCount());
    }
}
