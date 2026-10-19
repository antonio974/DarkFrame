package com.darkframe.app;

/**
 * Film simulations: Lightroom settings written into each photo's XMP, and an approximation in the live view
 * using the camera's own settings (contrast, saturation, white balance shift, black and white).
 *
 * The settings combine published recipes (Kodachrome: imagen-ai; Portra: medialoot; Tri-X: shuttergroove)
 * with the known character of each film: they are starting points to refine.
 * Pure Java so it can be tested off the camera.
 */
public final class FilmLook {
    public final String name;
    /** Short code shown on the icon (P400, V50...). */
    public final String code;
    /** Icon colors, as 0xRRGGBB: background and text, like the film box. */
    public final int iconColor, iconTextColor;
    /** Live view preview, in camera steps (-3 to +3); mono for black and white. */
    public final int contrast, saturation, shiftAmberBlue, shiftGreenMagenta;
    public final boolean mono;
    /** crs name / value pairs, without the crs: prefix. */
    private final String[] settings;
    /** RGB tone curve: input, output pairs from 0 to 255. */
    private final int[] curve;
    /** Post-crop vignette (Lightroom, -100 to 0): it follows the chosen format. */
    private int vignette;

    /** Film vignette (-100 to 0), for the live view preview. */
    public int vignette() { return vignette; }

    private FilmLook vignette(int amount) {
        vignette = amount;
        return this;
    }

    private FilmLook(String name, String code, int iconColor, int iconTextColor,
                     int contrast, int saturation, int shiftAmberBlue, int shiftGreenMagenta, boolean mono,
                     int[] curve, String... settings) {
        this.name = name;
        this.code = code;
        this.iconColor = iconColor;
        this.iconTextColor = iconTextColor;
        this.contrast = contrast;
        this.saturation = saturation;
        this.shiftAmberBlue = shiftAmberBlue;
        this.shiftGreenMagenta = shiftGreenMagenta;
        this.mono = mono;
        this.curve = curve;
        this.settings = settings;
    }

    private static final int KODAK = 0xF5B800, KODAK_RED = 0xC8102E, FUJI = 0x00A651, WHITE = 0xFFFFFF,
            BLACK = 0x111111, CINESTILL = 0xD71920, ILFORD = 0xE8E8E8;

    /** Available films, in menu order. No film is null. */
    public static final FilmLook[] FILMS = {
        new FilmLook("Portra 400", "P400", KODAK, KODAK_RED, -1, -1, 1, 0, false,
            new int[] { 0, 12, 60, 58, 128, 130, 190, 192, 255, 248 },
            "Contrast2012", "-10", "Highlights2012", "-30", "Shadows2012", "+20", "Whites2012", "-10",
            "Blacks2012", "+5", "Clarity2012", "-5", "Vibrance", "-5", "Saturation", "-10",
            "HueAdjustmentRed", "+5", "HueAdjustmentOrange", "-4", "SaturationAdjustmentOrange", "+5",
            "LuminanceAdjustmentOrange", "+8", "HueAdjustmentYellow", "-10", "SaturationAdjustmentYellow", "-10",
            "HueAdjustmentGreen", "-30", "SaturationAdjustmentGreen", "-25", "HueAdjustmentBlue", "-10",
            "SaturationAdjustmentBlue", "-15", "LuminanceAdjustmentBlue", "+5",
            "SplitToningShadowHue", "200", "SplitToningShadowSaturation", "8",
            "SplitToningHighlightHue", "45", "SplitToningHighlightSaturation", "10", "SplitToningBalance", "+10",
            "RedHue", "+5", "RedSaturation", "+10", "BlueHue", "-10", "BlueSaturation", "-5",
            "GrainAmount", "15", "GrainSize", "25", "GrainFrequency", "50").vignette(-10),
        new FilmLook("Ektar 100", "E100", KODAK, KODAK_RED, 1, 2, 0, 0, false,
            new int[] { 0, 0, 64, 56, 128, 128, 192, 200, 255, 255 },
            "Contrast2012", "+20", "Highlights2012", "-20", "Shadows2012", "+5", "Blacks2012", "-10",
            "Clarity2012", "+5", "Vibrance", "+20", "Saturation", "+10",
            "HueAdjustmentRed", "+5", "SaturationAdjustmentRed", "+15", "SaturationAdjustmentOrange", "+5",
            "HueAdjustmentGreen", "-10", "SaturationAdjustmentGreen", "+5",
            "SaturationAdjustmentBlue", "+20", "LuminanceAdjustmentBlue", "-10",
            "RedSaturation", "+10", "BlueHue", "-10", "BlueSaturation", "+15",
            "GrainAmount", "5", "GrainSize", "15", "GrainFrequency", "50").vignette(-8),
        new FilmLook("Gold 200", "G200", KODAK, KODAK_RED, 0, 1, 2, 0, false,
            new int[] { 0, 10, 64, 66, 128, 134, 192, 200, 255, 250 },
            "Contrast2012", "+5", "Highlights2012", "-25", "Shadows2012", "+15", "Blacks2012", "+8",
            "Vibrance", "+5", "HueAdjustmentYellow", "-10", "SaturationAdjustmentYellow", "+10",
            "SaturationAdjustmentOrange", "+10", "HueAdjustmentGreen", "-25", "SaturationAdjustmentGreen", "-15",
            "HueAdjustmentBlue", "-5", "SaturationAdjustmentBlue", "-10",
            "SplitToningShadowHue", "40", "SplitToningShadowSaturation", "10",
            "SplitToningHighlightHue", "50", "SplitToningHighlightSaturation", "20", "SplitToningBalance", "+20",
            "RedHue", "+5", "RedSaturation", "+10", "BlueHue", "-5",
            "GrainAmount", "25", "GrainSize", "30", "GrainFrequency", "50").vignette(-15),
        new FilmLook("Pro 400H", "400H", FUJI, WHITE, -2, -1, -1, -1, false,
            new int[] { 0, 20, 64, 70, 128, 134, 192, 196, 255, 245 },
            "Contrast2012", "-15", "Highlights2012", "-20", "Shadows2012", "+25", "Whites2012", "-5",
            "Blacks2012", "+10", "Vibrance", "-5", "Saturation", "-10",
            "HueAdjustmentOrange", "+3", "SaturationAdjustmentOrange", "-5", "LuminanceAdjustmentOrange", "+10",
            "HueAdjustmentGreen", "+20", "SaturationAdjustmentGreen", "-15", "LuminanceAdjustmentGreen", "+10",
            "HueAdjustmentAqua", "-5", "HueAdjustmentBlue", "-10", "SaturationAdjustmentBlue", "-10",
            "SplitToningShadowHue", "180", "SplitToningShadowSaturation", "12",
            "SplitToningHighlightHue", "60", "SplitToningHighlightSaturation", "6", "SplitToningBalance", "-10",
            "GreenHue", "+15", "GreenSaturation", "-10", "BlueHue", "-5",
            "GrainAmount", "12", "GrainSize", "25", "GrainFrequency", "50").vignette(-8),
        new FilmLook("Velvia 50", "V50", FUJI, WHITE, 3, 3, 0, 1, false,
            new int[] { 0, 0, 64, 50, 128, 128, 192, 206, 255, 255 },
            "Contrast2012", "+35", "Highlights2012", "-20", "Shadows2012", "-10", "Whites2012", "+10",
            "Blacks2012", "-20", "Clarity2012", "+10", "Vibrance", "+30", "Saturation", "+20",
            "SaturationAdjustmentRed", "+15", "HueAdjustmentGreen", "+5", "SaturationAdjustmentGreen", "+20",
            "SaturationAdjustmentBlue", "+25", "LuminanceAdjustmentBlue", "-15", "SaturationAdjustmentMagenta", "+15",
            "RedSaturation", "+15", "GreenSaturation", "+15", "BlueHue", "-5", "BlueSaturation", "+20",
            "ShadowTint", "+5", "GrainAmount", "5", "GrainSize", "15", "GrainFrequency", "50").vignette(-12),
        new FilmLook("Provia 100F", "F100", FUJI, WHITE, 1, 1, 0, 0, false,
            new int[] { 0, 0, 64, 60, 128, 128, 192, 198, 255, 255 },
            "Contrast2012", "+15", "Highlights2012", "-15", "Blacks2012", "-10", "Vibrance", "+10",
            "Saturation", "+5", "HueAdjustmentGreen", "+5", "SaturationAdjustmentBlue", "+10",
            "BlueSaturation", "+10", "GrainAmount", "5", "GrainSize", "15", "GrainFrequency", "50").vignette(-6),
        new FilmLook("Kodachrome 64", "K64", KODAK, KODAK_RED, 2, 2, 0, 0, false,
            new int[] { 0, 5, 64, 56, 128, 128, 192, 200, 255, 250 },
            "Contrast2012", "+30", "Highlights2012", "-30", "Shadows2012", "+5", "Blacks2012", "-15",
            "Vibrance", "+10", "SaturationAdjustmentRed", "+15", "SaturationAdjustmentGreen", "+10",
            "SaturationAdjustmentBlue", "+15", "LuminanceAdjustmentBlue", "-10",
            "RedHue", "+5", "RedSaturation", "+20", "GreenSaturation", "+10", "BlueHue", "-8", "BlueSaturation", "+10",
            "GrainAmount", "20", "GrainSize", "25", "GrainFrequency", "35").vignette(-15),
        new FilmLook("CineStill 800T", "800T", CINESTILL, WHITE, 1, -1, -3, 0, false,
            new int[] { 0, 15, 64, 62, 128, 130, 192, 198, 255, 245 },
            "Contrast2012", "+10", "Highlights2012", "-25", "Shadows2012", "+15", "Blacks2012", "+5",
            "Vibrance", "+5", "Saturation", "-5", "HueAdjustmentRed", "-5", "SaturationAdjustmentRed", "+15",
            "HueAdjustmentOrange", "-5", "SaturationAdjustmentYellow", "-20",
            "HueAdjustmentGreen", "+40", "SaturationAdjustmentGreen", "-30",
            "HueAdjustmentBlue", "-10", "SaturationAdjustmentBlue", "+10",
            "SplitToningShadowHue", "190", "SplitToningShadowSaturation", "25",
            "SplitToningHighlightHue", "25", "SplitToningHighlightSaturation", "15", "SplitToningBalance", "-15",
            "BlueHue", "-15", "GrainAmount", "35", "GrainSize", "30", "GrainFrequency", "50").vignette(-12),
        new FilmLook("Tri-X 400", "TX400", KODAK, BLACK, 2, 0, 0, 0, true,
            new int[] { 0, 5, 64, 52, 128, 128, 192, 206, 255, 252 },
            "ConvertToGrayscale", "True", "Contrast2012", "+30", "Highlights2012", "-15", "Shadows2012", "+5",
            "Whites2012", "+10", "Blacks2012", "-20", "Clarity2012", "+15",
            "GrayMixerRed", "+10", "GrayMixerOrange", "+5", "GrayMixerYellow", "0", "GrayMixerGreen", "-10",
            "GrayMixerAqua", "-15", "GrayMixerBlue", "-20", "GrayMixerPurple", "-5", "GrayMixerMagenta", "0",
            "GrainAmount", "40", "GrainSize", "30", "GrainFrequency", "60").vignette(-20),
        new FilmLook("HP5 Plus", "HP5", ILFORD, BLACK, 0, 0, 0, 0, true,
            new int[] { 0, 12, 64, 64, 128, 128, 192, 196, 255, 246 },
            "ConvertToGrayscale", "True", "Contrast2012", "+10", "Highlights2012", "-20", "Shadows2012", "+15",
            "Blacks2012", "-5", "Clarity2012", "+5",
            "GrayMixerRed", "0", "GrayMixerOrange", "+5", "GrayMixerYellow", "+5", "GrayMixerGreen", "0",
            "GrayMixerAqua", "-5", "GrayMixerBlue", "-10", "GrayMixerPurple", "0", "GrayMixerMagenta", "0",
            "GrainAmount", "35", "GrainSize", "35", "GrainFrequency", "50").vignette(-15),
    };

    /** Film by name, or null (no film, or unknown name). */
    public static FilmLook byName(String name) {
        if (name != null)
            for (FilmLook film : FILMS)
                if (film.name.equals(name)) return film;
        return null;
    }

    /** crs:... attributes to add to the XMP description, one per line. */
    public String xmpAttributes() {
        StringBuilder out = new StringBuilder();
        out.append("   crs:ProcessVersion=\"11.0\"\n");
        for (int i = 0; i + 1 < settings.length; i += 2)
            out.append("   crs:").append(settings[i]).append("=\"").append(settings[i + 1]).append("\"\n");
        if (vignette != 0)
            out.append("   crs:PostCropVignetteAmount=\"").append(vignette).append("\"\n")
                    .append("   crs:PostCropVignetteMidpoint=\"50\"\n")
                    .append("   crs:PostCropVignetteFeather=\"60\"\n")
                    .append("   crs:PostCropVignetteRoundness=\"0\"\n")
                    .append("   crs:PostCropVignetteStyle=\"1\"\n")
                    .append("   crs:PostCropVignetteHighlightContrast=\"0\"\n");
        out.append("   crs:ToneCurveName2012=\"Custom\"\n");
        return out.toString();
    }

    /** Tone curve, as an XMP element to place in the description. */
    public String xmpCurve() {
        StringBuilder out = new StringBuilder("   <crs:ToneCurvePV2012>\n    <rdf:Seq>\n");
        for (int i = 0; i + 1 < curve.length; i += 2)
            out.append("     <rdf:li>").append(curve[i]).append(", ").append(curve[i + 1]).append("</rdf:li>\n");
        return out.append("    </rdf:Seq>\n   </crs:ToneCurvePV2012>\n").toString();
    }

    /** Value of a setting (without crs:), or null: for tests. */
    String setting(String key) {
        for (int i = 0; i + 1 < settings.length; i += 2)
            if (settings[i].equals(key)) return settings[i + 1];
        return null;
    }
}
