package com.thisismeat;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public final class Config {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.BooleanValue MATCH_ITEM_ID;
    public static final ForgeConfigSpec.BooleanValue MATCH_DISPLAY_NAME;
    public static final ForgeConfigSpec.BooleanValue MATCH_RECIPES;
    public static final ForgeConfigSpec.BooleanValue LOG_RESULTS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> EXTRA_KEYWORDS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> WHITELIST;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLACKLIST;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

        builder.comment("This Is Meat - recognises meat added by other mods.").push("general");

        ENABLED = builder
                .comment("Master switch. When false the mod does nothing at all.")
                .define("enabled", true);

        MATCH_ITEM_ID = builder
                .comment("Stage 1: match the registry id (e.g. artifacts:eternal_beef). Runs at game start.")
                .define("match_item_id", true);

        MATCH_RECIPES = builder
                .comment("Stage 2: if a recipe turns meat into this food, treat the result as meat too.",
                        "Runs when a world is loaded (needs the recipe manager).")
                .define("match_recipes", true);

        MATCH_DISPLAY_NAME = builder
                .comment("Stage 3: match the translated item name (client only, needs the language files).")
                .define("match_display_name", true);

        builder.pop();

        builder.comment("Tuning").push("rules");

        EXTRA_KEYWORDS = builder
                .comment("Additional keywords, matched against both the id path and the display name.")
                .defineListAllowEmpty(List.of("extra_keywords"), List::of, o -> o instanceof String);

        WHITELIST = builder
                .comment("Items that are always treated as meat, even if no rule matches.",
                        "Format: 'modid:item_id' or 'modid:*'")
                .defineListAllowEmpty(List.of("whitelist"), List::of, o -> o instanceof String);

        BLACKLIST = builder
                .comment("Items that are never treated as meat, even if a rule matches.",
                        "Format: 'modid:item_id' or 'modid:*'")
                .defineListAllowEmpty(List.of("blacklist"), List::of, o -> o instanceof String);

        LOG_RESULTS = builder
                .comment("Log every item that got flagged (INFO once for the summary, DEBUG for each item).")
                .define("log_results", true);

        builder.pop();

        SPEC = builder.build();
    }

    private Config() {
    }

    public static boolean enabled() {
        return !SPEC.isLoaded() || ENABLED.get();
    }
}
