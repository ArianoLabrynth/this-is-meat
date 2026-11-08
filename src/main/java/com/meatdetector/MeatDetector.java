package com.meatdetector;

import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Mod(MeatDetector.MODID)
public class MeatDetector {

    public static final String MODID = "meatdetector";
    public static final Logger LOGGER = LoggerFactory.getLogger("MeatDetector");

    public MeatDetector() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener(this::onLoadComplete);
        MinecraftForge.EVENT_BUS.addListener(this::onServerStarted);
        MinecraftForge.EVENT_BUS.addListener(this::onRegisterCommands);
    }

    private void onLoadComplete(FMLLoadCompleteEvent event) {
        if (!Config.enabled()) {
            LOGGER.info("[MeatDetector] Disabled by config, doing nothing.");
            return;
        }
        if (!MeatScanner.isAvailable()) {
            LOGGER.error("[MeatDetector] Food properties are not reachable on this Minecraft build - mod inactive.");
            return;
        }
        try {
            MeatScanner.scanRegistryIds();
        } catch (Throwable t) {
            LOGGER.error("[MeatDetector] Stage 1 (item id) crashed", t);
        }
    }

    private void onServerStarted(ServerStartedEvent event) {
        if (!Config.enabled() || !MeatScanner.isAvailable()) {
            return;
        }
        try {
            MeatScanner.scanRecipes(event.getServer());
        } catch (Throwable t) {
            LOGGER.error("[MeatDetector] Stage 2 (recipes) crashed", t);
        }
    }

    private void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal(MODID)
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("list").executes(this::printReport))
                .then(Commands.literal("status").executes(this::printStatus)));
    }

    private int printStatus(CommandContext<CommandSourceStack> ctx) {
        int marked = MeatScanner.markedCount();
        int detailed = MeatScanner.report().size();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Meat Detector: " + marked + " food items are flagged as meat"
                        + " (" + detailed + " of them changed by this mod)."), false);
        return marked;
    }

    private int printReport(CommandContext<CommandSourceStack> ctx) {
        List<String> report = MeatScanner.report();
        if (report.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("Meat Detector: nothing flagged yet (join a world first)."), false);
            return 0;
        }
        int limit = Math.min(report.size(), 40);
        ctx.getSource().sendSuccess(() -> Component.literal("Meat Detector: " + report.size()
                + " items flagged (showing " + limit + ")"), false);
        for (int i = 0; i < limit; i++) {
            String line = report.get(i);
            ctx.getSource().sendSuccess(() -> Component.literal("  " + line), false);
        }
        return report.size();
    }
}
