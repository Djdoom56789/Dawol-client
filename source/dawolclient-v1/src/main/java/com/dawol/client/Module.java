package com.dawol.client;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for every feature.
 *
 * <p>Each feature is a named module belonging to exactly one {@link Category}, which is
 * what drives the tabbed UI. Modules hold their own enabled state and nothing else; the
 * flags in {@link Toggles} remain the shared state that render and tick code reads.
 *
 * <p>No module here modifies game code. The mod ships <b>zero mixins and zero class
 * transformers</b>: every feature is built from public vanilla APIs and Fabric events.
 * That is a deliberate constraint, not an accident.
 */
public abstract class Module {

    /** Feature groups, in the order the tabs appear. */
    public enum Category {
        COMBAT("Combat"),
        RENDER("Render"),
        WORLD("World"),
        PLAYER("Player"),
        MISC("Misc");

        private final String label;

        Category(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final String name;
    private final Category category;
    private final String description;

    protected Module(String name, Category category, String description) {
        this.name = name;
        this.category = category;
        this.description = description;
    }

    public String name() {
        return name;
    }

    public Category category() {
        return category;
    }

    public String description() {
        return description;
    }

    /** Whether this module is currently active. */
    public abstract boolean isEnabled();

    /** Flips the module on or off. */
    public abstract void setEnabled(boolean enabled);

    public void toggle() {
        setEnabled(!isEnabled());
    }

    /** Whether this module can be turned on at all right now. */
    public boolean isAvailable() {
        return true;
    }

    // --- Registry ---

    private static final List<Module> MODULES = new ArrayList<>();
    private static final Map<Category, List<Module>> BY_CATEGORY = new EnumMap<>(Category.class);

    static <T extends Module> T register(T module) {
        MODULES.add(module);
        BY_CATEGORY.computeIfAbsent(module.category(), c -> new ArrayList<>()).add(module);
        return module;
    }

    public static List<Module> all() {
        return List.copyOf(MODULES);
    }

    public static List<Module> inCategory(Category category) {
        return List.copyOf(BY_CATEGORY.getOrDefault(category, List.of()));
    }

    public static int countEnabled() {
        int count = 0;
        for (Module module : MODULES) {
            if (module.isEnabled()) {
                count++;
            }
        }
        return count;
    }
}
