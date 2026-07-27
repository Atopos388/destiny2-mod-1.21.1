package de.tomalbrc.sandstorm;

import gg.moonflower.molangcompiler.api.MolangCompiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Compatibility host for the LGPL Sandstorm parser sources bundled in this mod.
 * The original Polymer renderer is intentionally not included.
 */
public final class Sandstorm {
    public static final String MOD_ID = "sandstorm";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final MolangCompiler MOLANG = MolangCompiler.create(
            MolangCompiler.DEFAULT_FLAGS,
            Sandstorm.class.getClassLoader()
    );
    public static final float TIME_SCALE = 1.0f / 20.0f;

    private Sandstorm() {}
}
