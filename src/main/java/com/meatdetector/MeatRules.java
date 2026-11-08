package com.meatdetector;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pure keyword engine. No Minecraft classes on purpose, so it can be unit tested offline
 * against the language files of every mod in a pack.
 */
public final class MeatRules {

    /** If any of these appear in the candidate text the item is never treated as meat. */
    private static final String[] EXCLUDE = {
            "素肉", "植物肉", "人造肉", "假肉", "蛋白肉",
            "plant_meat", "fake_meat", "mock_meat", "imitation_meat", "seitan", "vegan", "vegetarian"
    };

    private static final String[] DEFAULT_EN = {
            "meat", "beef", "pork", "mutton", "chicken", "steak", "bacon", "ham", "sausage",
            "salami", "pepperoni", "prosciutto", "chorizo", "brisket", "tenderloin", "cutlet",
            "drumstick", "ribs", "rib", "wing", "jerky", "venison", "rabbit", "hare", "boar",
            "poultry", "turkey", "duck", "quail", "pheasant",
            "fish", "salmon", "cod", "trout", "bass", "tuna", "sardine", "herring", "anchovy",
            "mackerel", "eel", "pike", "carp", "tilapia", "catfish", "roe",
            "squid", "calamari", "octopus", "shrimp", "prawn", "crab", "lobster", "crayfish",
            "clam", "oyster", "mussel", "scallop", "abalone", "urchin",
            "liver", "offal", "tripe", "gizzard", "kidney", "tongue", "marrow",
            "hotdog", "hamburger", "burger", "nugget", "kebab", "schnitzel", "gyro", "tartare",
            "carpaccio", "terrine", "flesh", "mince"
    };

    private static final String[] DEFAULT_ZH = {
            "肉", "鱼", "虾", "蟹", "鱿", "章鱼", "贝", "蛤", "牡蛎", "扇贝", "鲍", "海胆",
            "火腿", "培根", "香肠", "腊肠", "肉丸", "牛排", "猪排", "羊排", "排骨", "鸡排",
            "鸡腿", "鸡翅", "鸭腿", "鸭翅", "肝", "骨髓", "肠", "肚", "蹄",
            "烤肉", "炖肉", "烧肉", "卤肉", "熏肉", "腊肉", "肉饼", "肉松", "肉串", "肉干",
            "三文鱼", "金枪鱼", "鳕鱼", "鳗", "鱼丸", "鱼干", "鱼片", "生鱼", "熟鱼",
            "鹿肉", "野猪", "熊掌", "臊子"
    };

    private static final Set<String> EXTRA = ConcurrentHashMap.newKeySet();

    private MeatRules() {
    }

    public static void setExtraKeywords(Collection<? extends String> values) {
        EXTRA.clear();
        if (values == null) {
            return;
        }
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String trimmed = value.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.isEmpty()) {
                EXTRA.add(trimmed);
            }
        }
    }

    public static boolean isExcluded(String text) {
        if (text == null) {
            return true;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String token : EXCLUDE) {
            if (lower.contains(token)) {
                return true;
            }
        }
        return false;
    }

    /** True when the given id path / display name looks like meat. */
    public static boolean matches(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (isExcluded(lower)) {
            return false;
        }
        for (String token : EXTRA) {
            if (token.codePointAt(0) > 0x7F) {
                if (lower.contains(token)) {
                    return true;
                }
            } else if (containsWord(lower, token)) {
                return true;
            }
        }
        for (String token : DEFAULT_ZH) {
            if (lower.contains(token)) {
                return true;
            }
        }
        for (String token : DEFAULT_EN) {
            if (containsWord(lower, token)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Left-boundary word match so "meatballs"/"ribeye" hit but "encode" does not hit "cod".
     */
    private static boolean containsWord(String haystack, String needle) {
        if (needle.isEmpty() || needle.length() > haystack.length()) {
            return false;
        }
        int index = 0;
        while ((index = haystack.indexOf(needle, index)) >= 0) {
            boolean leftOk = index == 0 || !isAsciiLetter(haystack.charAt(index - 1));
            if (leftOk) {
                return true;
            }
            index++;
        }
        return false;
    }

    private static boolean isAsciiLetter(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    /** Convenience for tooling: the full default token list. */
    public static List<String> allDefaultTokens() {
        List<String> out = new ArrayList<>();
        out.addAll(List.of(DEFAULT_EN));
        out.addAll(List.of(DEFAULT_ZH));
        return out;
    }
}
