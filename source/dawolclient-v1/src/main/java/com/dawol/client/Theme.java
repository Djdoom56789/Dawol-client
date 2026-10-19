package com.dawol.client;

/**
 * Dark-green palette — deep blacks with medium/bright green accents.
 *
 * <p>UI backgrounds are near-black with a subtle green tint (Krypton / GoobaClient
 * aesthetic). ESP geometry and tracers use brighter greens so they are readable
 * against the dark environment.
 */
public final class Theme {

    // ---- Primary greens ----
    /** Primary accent: medium bright green used for active state, tracers, borders. */
    public static final int GREEN = 0xFF2ECC71;

    /** Dimmed green for secondary labels. */
    public static final int GREEN_DIM = 0xFF1A7A45;

    /** Deep green for inactive chrome and decorative borders. */
    public static final int GREEN_DEEP = 0xFF0F4D2A;

    /** High-contrast bright green for titles and active module names. */
    public static final int GREEN_BRIGHT = 0xFF57F296;

    /** Near-white with a faint green cast, for neutral text. */
    public static final int WHITE = 0xFFD9F5E4;

    // ---- Backgrounds ----
    /** Standard translucent panel — very dark. */
    public static final int PANEL = 0xCC070F09;

    /** Fully opaque deep panel for overlays. */
    public static final int PANEL_DEEP = 0xF2040A06;

    /** Sidebar background — near-black. */
    public static final int SIDEBAR = 0xF2030706;

    /** Sidebar selected category highlight. */
    public static final int SIDEBAR_SELECTED = 0xF00A2512;

    /** Module row, inactive. */
    public static final int ROW_OFF = 0x800A100C;

    /** Module row, active — dark green tint. */
    public static final int ROW_ON = 0xA0092016;

    /** Module row, hovered. */
    public static final int ROW_HOVER = 0xC0143D22;

    /** Category column body. */
    public static final int PANEL_BODY = 0xB0050D08;

    /** Chrome bar background — near pure black. */
    public static final int CHROME = 0xF2020504;

    // ---- ESP geometry ----
    /** Player ESP. */
    public static final int PLAYER = GREEN;

    /** Hostile mob ESP — warm orange so it differs from player green. */
    public static final int MOB = 0xFFFF6B4A;

    /** Dropped item ESP. */
    public static final int ITEM = 0xFFE8C547;

    /** Tracer lines. */
    public static final int TRACER = GREEN_BRIGHT;

    /** Base-finder markers — magenta, never confused with ore or stash. */
    public static final int BASE = 0xFFFF2D95;

    /** New-chunk highlight. */
    public static final int CHUNK_NEW = 0xFF4AC8FF;

    /** Confirmed stash hit. */
    public static final int STASH = 0xFFFF2D95;

    /** Tracked chunk below stash threshold. */
    public static final int STASH_WEAK = GREEN_BRIGHT;

    /** Dense-ore chunk. */
    public static final int CHUNK_DENSE = 0xFFFFC14A;

    // ---- Category accents ----
    public static final int CAT_COMBAT = 0xFFFF6B4A;
    public static final int CAT_RENDER = GREEN;
    public static final int CAT_WORLD  = 0xFF4AC8FF;
    public static final int CAT_PLAYER = 0xFFE8C547;
    public static final int CAT_MISC   = 0xFFC46BFF;

    private Theme() {
    }
}
