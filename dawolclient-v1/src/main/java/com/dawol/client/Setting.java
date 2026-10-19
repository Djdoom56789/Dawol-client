package com.dawol.client;

/**
 * A single configurable setting, described once and rendered generically by the settings
 * screen. Adding a setting means appending one factory call in {@link Settings#all()};
 * no UI code changes.
 */
public abstract class Setting {

    private final String label;
    private final String description;

    protected Setting(String label, String description) {
        this.label = label;
        this.description = description;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    /** Current value rendered on the right of the row. */
    public abstract String valueText();

    /** Applies a click. Left-click advances forward, right-click reverses. */
    public abstract void click(boolean backwards);

    /** True when the setting is in a non-default "on" state, for colouring. */
    public boolean isActive() {
        return true;
    }

    /** A true/false row. */
    public static Setting bool(String label, String description,
                               java.util.function.BooleanSupplier getter,
                               java.util.function.Consumer<Boolean> setter) {
        return new Setting(label, description) {
            @Override
            public String valueText() {
                return getter.getAsBoolean() ? "ON" : "OFF";
            }

            @Override
            public void click(boolean backwards) {
                setter.accept(!getter.getAsBoolean());
            }

            @Override
            public boolean isActive() {
                return getter.getAsBoolean();
            }
        };
    }

    /** A numeric row stepped by a fixed increment. */
    public static Setting number(String label, String description, double step, double min, double max,
                                 java.util.function.DoubleSupplier getter,
                                 java.util.function.DoubleConsumer setter,
                                 java.util.function.Function<Double, String> format) {
        return new Setting(label, description) {
            @Override
            public String valueText() {
                return format.apply(getter.getAsDouble());
            }

            @Override
            public void click(boolean backwards) {
                double next = getter.getAsDouble() + (backwards ? -step : step);
                setter.accept(Math.max(min, Math.min(max, next)));
            }

            @Override
            public boolean isActive() {
                return true;
            }
        };
    }

    /** A long-backed numeric row, for millisecond and millimetre windows. */
    public static Setting numberLong(String label, String description, long step, long min, long max,
                                 java.util.function.LongSupplier getter,
                                 java.util.function.LongConsumer setter,
                                 java.util.function.LongFunction<String> format) {
        return new Setting(label, description) {
            @Override
            public String valueText() {
                return format.apply(getter.getAsLong());
            }

            @Override
            public void click(boolean backwards) {
                long next = getter.getAsLong() + (backwards ? -step : step);
                setter.accept(Math.max(min, Math.min(max, next)));
            }

            @Override
            public boolean isActive() {
                return true;
            }
        };
    }
}