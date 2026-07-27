package atopos.destiny2.client.font;

import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import com.mojang.blaze3d.platform.NativeImage;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.awt.image.Raster;
import java.io.InputStream;
import java.util.function.Function;

/**
 * Lazily rasterizes the bundled Source Han Sans font without using Minecraft's
 * eager JSON TrueType provider. Only glyphs that are actually rendered are
 * converted to atlas pixels; unsupported or malformed glyphs return null so
 * the next Minecraft font provider can supply a fallback.
 */
public final class SourceHanGlyphProvider implements GlyphProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger("destiny2-mod/source-han-font");
    private static final String FONT_RESOURCE =
            "/assets/destiny2-mod/font/source_han_sans_cn_semibold.ttf";
    private static final float OVERSAMPLE = 4.0F;
    private static final float LOGICAL_SIZE = 9.0F;
    private static final int RASTER_SIZE = Math.round(LOGICAL_SIZE * OVERSAMPLE);
    private static final FontRenderContext FONT_CONTEXT =
            new FontRenderContext(null, RenderingHints.VALUE_TEXT_ANTIALIAS_ON,
                    RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    private static final Font FONT = loadFont();

    private static Font loadFont() {
        try (InputStream stream = SourceHanGlyphProvider.class.getResourceAsStream(FONT_RESOURCE)) {
            if (stream == null) {
                LOGGER.error("Bundled Source Han font is missing: {}", FONT_RESOURCE);
                return null;
            }
            return Font.createFont(Font.TRUETYPE_FONT, stream).deriveFont(Font.PLAIN, RASTER_SIZE);
        } catch (Throwable error) {
            LOGGER.error("Unable to initialize bundled Source Han font; Minecraft fallback will be used", error);
            return null;
        }
    }

    public static SourceHanGlyphProvider create() {
        return FONT == null ? null : new SourceHanGlyphProvider();
    }

    private SourceHanGlyphProvider() {
    }

    @Override
    public GlyphInfo getGlyph(int codePoint) {
        if (!Character.isValidCodePoint(codePoint) || !FONT.canDisplay(codePoint)) {
            return null;
        }
        try {
            String text = new String(Character.toChars(codePoint));
            GlyphVector vector = FONT.createGlyphVector(FONT_CONTEXT, text);
            if (vector.getNumGlyphs() == 0 || vector.getGlyphCode(0) == FONT.getMissingGlyphCode()) {
                return null;
            }

            float advance = (float) vector.getGlyphPosition(vector.getNumGlyphs()).getX() / OVERSAMPLE;
            Rectangle bounds = vector.getPixelBounds(FONT_CONTEXT, 0.0F, 0.0F);
            if (bounds.width <= 0 || bounds.height <= 0) {
                return (GlyphInfo.SpaceGlyphInfo) () -> advance;
            }
            return rasterize(vector, bounds, advance);
        } catch (Throwable error) {
            // A single malformed outline must never abort the resource reload or UI render.
            LOGGER.debug("Falling back for Source Han glyph U+{}", Integer.toHexString(codePoint), error);
            return null;
        }
    }

    private static GlyphInfo rasterize(GlyphVector vector, Rectangle bounds, float advance) {
        BufferedImage image = new BufferedImage(bounds.width, bounds.height, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setFont(FONT);
            graphics.setColor(Color.WHITE);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                    RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                    RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            graphics.drawGlyphVector(vector, -bounds.x, -bounds.y);
        } finally {
            graphics.dispose();
        }

        byte[] luminance = copyLuminance(image.getRaster(), bounds.width, bounds.height);
        float bearingLeft = bounds.x / OVERSAMPLE;
        float bearingTop = -bounds.y / OVERSAMPLE;
        return new RasterGlyph(advance, bounds.width, bounds.height, bearingLeft, bearingTop, luminance);
    }

    private static byte[] copyLuminance(Raster raster, int width, int height) {
        byte[] pixels = new byte[width * height];
        int index = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                pixels[index++] = (byte) raster.getSample(x, y, 0);
            }
        }
        return pixels;
    }

    @Override
    public IntSet getSupportedGlyphs() {
        // This provider is injected after FontSet's eager provider selection. Keep this
        // set deliberately tiny as a guard if another caller inspects it in the future.
        IntOpenHashSet seed = new IntOpenHashSet(1);
        seed.add('A');
        return seed;
    }

    private record RasterGlyph(
            float advance,
            int pixelWidth,
            int pixelHeight,
            float bearingLeft,
            float bearingTop,
            byte[] luminance
    ) implements GlyphInfo {
        @Override
        public float getAdvance() {
            return advance;
        }

        @Override
        public BakedGlyph bake(Function<SheetGlyphInfo, BakedGlyph> stitcher) {
            return stitcher.apply(new SheetGlyphInfo() {
                @Override
                public int getPixelWidth() {
                    return pixelWidth;
                }

                @Override
                public int getPixelHeight() {
                    return pixelHeight;
                }

                @Override
                public void upload(int x, int y) {
                    NativeImage nativeImage = new NativeImage(
                            NativeImage.Format.LUMINANCE, pixelWidth, pixelHeight, false);
                    try {
                        int index = 0;
                        for (int py = 0; py < pixelHeight; py++) {
                            for (int px = 0; px < pixelWidth; px++) {
                                nativeImage.setPixelLuminance(px, py, luminance[index++]);
                            }
                        }
                        nativeImage.upload(0, x, y, 0, 0, pixelWidth, pixelHeight, false, true);
                    } catch (Throwable error) {
                        nativeImage.close();
                        throw error;
                    }
                }

                @Override
                public boolean isColored() {
                    return false;
                }

                @Override
                public float getOversample() {
                    return OVERSAMPLE;
                }

                @Override
                public float getBearingLeft() {
                    return bearingLeft;
                }

                @Override
                public float getBearingTop() {
                    return bearingTop;
                }
            });
        }
    }
}
