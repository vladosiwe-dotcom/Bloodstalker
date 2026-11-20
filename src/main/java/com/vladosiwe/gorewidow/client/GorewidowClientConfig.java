package com.vladosiwe.gorewidow.client;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-only settings (config/gorewidow-client.toml). */
public final class GorewidowClientConfig {
    /** How the rider's camera follows the mount's body orientation. */
    public enum CameraMode {
        /** Level horizon, always. */
        OFF,
        /** Banks with the body, limited to {@link #GENTLE_LIMIT} degrees, never flips. */
        GENTLE,
        /** Follows the body all the way, upside down on a ceiling (up to 180 degrees). */
        FULL;

        public CameraMode next() {
            return values()[(this.ordinal() + 1) % values().length];
        }
    }

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.EnumValue<CameraMode> CAMERA;
    public static final ModConfigSpec.DoubleValue GENTLE_LIMIT;
    public static final ModConfigSpec.BooleanValue TURN_RELATIVE_TO_VIEW;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("camera");
        CAMERA = b.comment("How the rider's view follows the Gorewidow's body: OFF (level horizon), GENTLE (limited bank),",
                        "FULL (turns with it, upside down on ceilings). The N key cycles this in game.")
                .defineEnum("bankWithMount", CameraMode.FULL);
        GENTLE_LIMIT = b.comment("Largest bank angle in GENTLE mode, degrees.")
                .defineInRange("gentleLimitDegrees", 50.0, 0.0, 90.0);
        TURN_RELATIVE_TO_VIEW = b.comment("While the view is banked, mouse movement turns relative to what you see",
                        "(mouse right always turns toward the right of the screen, also upside down).")
                .define("turnRelativeToView", true);
        b.pop();
        SPEC = b.build();
    }

    private GorewidowClientConfig() {}
}
