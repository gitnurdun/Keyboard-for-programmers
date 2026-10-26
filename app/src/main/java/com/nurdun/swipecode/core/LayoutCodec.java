package com.nurdun.swipecode.core;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Properties;

/** Versioned, Unicode-safe persistence with no Android dependency. */
public final class LayoutCodec {
    private LayoutCodec() {}

    public static String encode(KeyboardLayout layout) {
        Properties properties = new Properties();
        properties.setProperty("version", "4");
        for (String key : KeyboardLayout.KEY_IDS)
            for (Direction direction : Direction.values()) {
                String prefix = key + "." + direction.name();
                KeyBinding binding = layout.binding(key, direction);
                properties.setProperty(prefix + ".type", binding.kind.name());
                properties.setProperty(prefix + ".value", binding.value);
                properties.setProperty(prefix + ".modifiers", String.valueOf(binding.modifiers));
            }
        StringWriter writer = new StringWriter();
        try { properties.store(writer, "SwipeCode keyboard layout"); }
        catch (IOException impossible) { throw new IllegalStateException(impossible); }
        return writer.toString();
    }

    public static KeyboardLayout decode(String encoded) {
        KeyboardLayout result = KeyboardLayout.defaults();
        if (encoded == null || encoded.isEmpty()) return result;
        Properties properties = new Properties();
        try { properties.load(new StringReader(encoded)); }
        catch (IOException | IllegalArgumentException invalid) { return result; }
        String version = properties.getProperty("version");
        if (!"1".equals(version) && !"2".equals(version) && !"3".equals(version) && !"4".equals(version)) return result;
        boolean allKeys = "3".equals(version) || "4".equals(version);
        for (String key : KeyboardLayout.KEY_IDS)
            for (Direction direction : Direction.values()) {
                if (!allKeys && (!KeyboardLayout.isLetter(key) || direction == Direction.CENTER)) continue;
                String prefix = key + "." + direction.name();
                if ("1".equals(version)) {
                    String symbol = properties.getProperty(prefix);
                    // Add new actions only where the old format had an unused direction.
                    if (KeyboardLayout.validSymbol(symbol) && (!symbol.isEmpty() || result.binding(key, direction).kind != KeyBinding.Kind.KEY)) result.bind(key, direction, KeyBinding.text(symbol));
                } else try {
                    String type = properties.getProperty(prefix + ".type");
                    String value = properties.getProperty(prefix + ".value");
                    if ("TEXT".equals(type)) result.bind(key, direction, KeyBinding.text(value));
                    else if ("KEY".equals(type)) result.bind(key, direction, KeyBinding.key(value, Integer.parseInt(properties.getProperty(prefix + ".modifiers", "0"))));
                    else if (allKeys && "COMMAND".equals(type)) result.bind(key, direction, KeyBinding.command(value));
                } catch (IllegalArgumentException invalid) { /* Keep this direction's preset. */ }
            }
        if (!"4".equals(version)) result.removeLegacyLetterPresets();
        return result;
    }
}
