package com.darkframe.app;

/**
 * Framework-free test: javac + java, no dependencies.
 * To run: see README.md.
 */
public class RatioMaskTest {
    private static int failures = 0;

    public static void main(String[] args) {
        // 1200x800 viewfinder (3:2, square pixels): the image fills the whole view.
        RatioMask.Frame f = RatioMask.compute(1200, 800, 1f, RatioMask.SENSOR_RATIO, 2.35f);
        check("3:2 image fills the view", f.image.toString(), "[0,0 - 1200,800]");
        check("2.35: 145 px top/bottom bars", f.visible.toString(), "[0,145 - 1200,655]");
        check("2.35 is a letterbox", f.isLetterbox(), true);

        f = RatioMask.compute(1200, 800, 1f, RatioMask.SENSOR_RATIO, 4f / 3f);
        check("4:3: 67 px left/right bars", f.visible.toString(), "[67,0 - 1133,800]");
        check("4:3 is not a letterbox", f.isLetterbox(), false);

        f = RatioMask.compute(1200, 800, 1f, RatioMask.SENSOR_RATIO, 1.5f);
        check("3:2: no bars", f.visible.toString(), "[0,0 - 1200,800]");

        // 640x480 screen, physically 4:3: the 3:2 image itself is letterboxed.
        f = RatioMask.compute(640, 480, 1f, RatioMask.SENSOR_RATIO, 1.5f);
        check("3:2 image on a 4:3 screen", f.image.toString(), "[0,27 - 640,453]");

        // 640x480 framebuffer shown on a 16:9 panel: wide pixels (aspect 4/3).
        f = RatioMask.compute(640, 480, (16f / 9f) / (640f / 480f), RatioMask.SENSOR_RATIO, 1.5f);
        check("3:2 image on a 16:9 panel", f.image.toString(), "[50,0 - 590,480]");

        // Formats: strictly increasing order, 2.35:1 by default, wrapping at both ends.
        for (int i = 1; i < RatioMask.FORMATS.length; i++)
            check("increasing order " + RatioMask.FORMATS[i].name, RatioMask.FORMATS[i].ratio > RatioMask.FORMATS[i - 1].ratio, true);
        check("default format", RatioMask.FORMATS[RatioMask.DEFAULT_FORMAT].name, "2.35:1");
        check("next format", RatioMask.FORMATS[RatioMask.nextFormat(RatioMask.DEFAULT_FORMAT, 1)].name, "65x24");
        check("previous format", RatioMask.FORMATS[RatioMask.nextFormat(RatioMask.DEFAULT_FORMAT, -1)].name, "6x12");
        check("6x12 format found by name", RatioMask.FORMATS[RatioMask.indexOf("6x12")].ratio, 2f);
        check("2.39:1 removed", RatioMask.indexOf("2.39:1"), -1);
        check("wraps after the last", RatioMask.nextFormat(RatioMask.FORMATS.length - 1, 1), 0);
        check("wraps before the first", RatioMask.nextFormat(0, -1), RatioMask.FORMATS.length - 1);
        check("C2 next", RatioMask.FORMATS[RatioMask.cycleFormat(RatioMask.DEFAULT_FORMAT)].name, "65x24");
        check("C2 wraps", RatioMask.FORMATS[RatioMask.cycleFormat(RatioMask.FORMATS.length - 1)].name, "9:16");
        check("4:5 vertical: full height", XmpCrop.forRatio(RatioMask.SENSOR_RATIO, 4f / 5f).top == 0 && XmpCrop.forRatio(RatioMask.SENSOR_RATIO, 4f / 5f).bottom == 1, true);
        check("4:5 vertical: side bands", Math.abs(XmpCrop.forRatio(RatioMask.SENSOR_RATIO, 4f / 5f).left - 7.0 / 30) < 1e-6, true);
        check("MP 4:5", RatioMask.megapixelText(4f / 5f), "19.3 MP");
        check("9:16 is first", RatioMask.FORMATS[0].name, "9:16");
        check("saved index out of range", RatioMask.clampIndex(42), RatioMask.FORMATS.length - 1);

        // 65x24 (XPan) on a 1200x800 viewfinder: 178 px top/bottom bars.
        f = RatioMask.compute(1200, 800, 1f, RatioMask.SENSOR_RATIO, 65f / 24f);
        check("65x24: top/bottom bars", f.visible.toString(), "[0,178 - 1200,622]");

        // XMP crop (fractions of the 3:2 image).
        XmpCrop c = XmpCrop.forRatio(1.5, 2.35);
        check("XMP 2.35 top", String.format(java.util.Locale.US, "%.4f", c.top), "0.1809");
        check("XMP 2.35 bottom", String.format(java.util.Locale.US, "%.4f", c.bottom), "0.8191");
        check("XMP 2.35 sides", c.left == 0 && c.right == 1, true);
        c = XmpCrop.forRatio(1.5, 1.0);
        check("XMP 1:1 left", String.format(java.util.Locale.US, "%.4f", c.left), "0.1667");
        check("XMP 3:2 no crop", XmpCrop.forRatio(1.5, 1.5).isFull(), true);
        check("XMP contains the crop", c.toXmp("1:1").contains("crs:CropLeft=\"0.166667\""), true);

        // Displayed settings.
        check("shutter 1/125", ExposureText.shutter(1, 125), "1/125");
        check("shutter 2\"", ExposureText.shutter(2, 1), "2\"");
        check("shutter 0.5\"", ExposureText.shutter(1, 2), "0.5\"");
        check("shutter 1.3\"", ExposureText.shutter(13, 10), "1.3\"");
        check("bulb", ExposureText.shutter(65535, 1), "BULB");
        check("aperture", ExposureText.aperture(560), "f5.6");
        check("unknown aperture", ExposureText.aperture(0), "f--");
        check("ISO set", ExposureText.iso(400, 0), "ISO 400");
        check("ISO auto", ExposureText.iso(0, 800), "ISO 800 A");
        check("compensation 0", ExposureText.ev(0f), "\u00B10.0");
        check("compensation -0.7", ExposureText.ev(-2 * (1f / 3)), "-0.7");
        check("compensation +1.3", ExposureText.ev(4 * (1f / 3)), "+1.3");
        check("grids: thirds after none", RatioMask.GRIDS[RatioMask.nextGrid(RatioMask.GRID_NONE)], "Thirds");
        check("grids wrap", RatioMask.nextGrid(RatioMask.GRIDS.length - 1), RatioMask.GRID_NONE);
        check("thirds lines", RatioMask.gridFractions(1).length, 2);
        check("no lines", RatioMask.gridFractions(RatioMask.GRID_NONE).length, 0);
        java.util.List<GoldenSpiral.Step> spiral = GoldenSpiral.steps(GoldenSpiral.DEFAULT_STEPS);
        check("spiral: first square", spiral.get(0).size, 1f);
        check("spiral: second square", Math.round(spiral.get(1).size * 1000), 618);
        check("spiral: starts at bottom-left corner", Math.round(spiral.get(0).startX() * 1000) == 0 && Math.round(spiral.get(0).startY() * 1000) == 1000 ? 1 : 0, 1);
        int joined = 1;
        for (int i = 0; i + 1 < spiral.size(); i++)
            if (Math.abs(spiral.get(i).endX() - spiral.get(i + 1).startX()) > 1e-4f
                    || Math.abs(spiral.get(i).endY() - spiral.get(i + 1).startY()) > 1e-4f) joined = 0;
        check("spiral is continuous", joined, 1);
        check("spiral grid", RatioMask.gridSpiral(3) == 1 && RatioMask.gridSpiral(4) == -1 && RatioMask.gridSpiral(1) == 0 ? 1 : 0, 1);
        check("MP 2.35:1", RatioMask.megapixelText(2.35f), "23.1 MP");
        check("MP 1:1", RatioMask.megapixelText(1f), "24.1 MP");
        check("MP 65x24", RatioMask.megapixelText(65f / 24f), "20.0 MP");
        check("format read from XMP", XmpCrop.formatFromXmp(XmpCrop.forRatio(1.5, 2.35).toXmp("2.35:1")), "2.35:1");
        check("6x12 format read from XMP", XmpCrop.formatFromXmp(XmpCrop.forRatio(1.5, 2).toXmp("6x12")), "6x12");
        check("XMP without format", XmpCrop.formatFromXmp("<x:xmpmeta/>") == null ? 1 : 0, 1);
        check("±3 dial at 0", ExposureText.evDialThirds(0), 0);
        check("±3 dial at +1", ExposureText.evDialThirds(3), 3);
        check("±3 dial at +3", ExposureText.evDialThirds(9), 9);
        check("±3 dial at -1/3", ExposureText.evDialThirds(513), -1);
        check("±3 dial at -1", ExposureText.evDialThirds(515), -3);
        check("±3 dial at -3", ExposureText.evDialThirds(521), -9);
        check("±3 dial unknown", ExposureText.evDialThirds(600), Integer.MIN_VALUE);

        // Films: 10 films, well-formed XMP with settings, curve, keyword, and format still readable.
        check("10 films", FilmLook.FILMS.length, 10);
        for (FilmLook film : FilmLook.FILMS) {
            String xmp = XmpCrop.forRatio(RatioMask.SENSOR_RATIO, RatioMask.FORMATS[RatioMask.indexOf("2.35:1")].ratio).toXmp("2.35:1", film);
            check("XMP " + film.name + " well-formed", wellFormed(xmp), true);
            check("XMP " + film.name + " keeps the format", XmpCrop.formatFromXmp(xmp), "2.35:1");
            check("XMP " + film.name + " has its curve", xmp.contains("<crs:ToneCurvePV2012>"), true);
            check("XMP " + film.name + " has its keyword", xmp.contains("<rdf:li>Film " + film.name + "</rdf:li>"), true);
            check("film found by name " + film.name, FilmLook.byName(film.name), film);
            check("XMP " + film.name + " has its vignette", xmp.contains("crs:PostCropVignetteAmount=\"-"), true);
            check("XMP " + film.name + " has its grain", xmp.contains("crs:GrainAmount="), true);
        }
        String full = XmpCrop.forRatio(RatioMask.SENSOR_RATIO, RatioMask.SENSOR_RATIO).toXmp("3:2", FilmLook.FILMS[0]);
        check("3:2 + film: no crop", full.contains("crs:HasCrop=\"False\""), true);
        check("3:2 + film well-formed", wellFormed(full), true);
        check("Tri-X is black and white", FilmLook.byName("Tri-X 400").setting("ConvertToGrayscale"), "True");
        check("no film: XMP unchanged", XmpCrop.forRatio(RatioMask.SENSOR_RATIO, 2.35).toXmp("2.35:1").contains("Film "), false);

        if (failures > 0) {
            System.out.println(failures + " failure(s)");
            System.exit(1);
        }
        System.out.println("All tests pass");
    }

    private static boolean wellFormed(String xml) {
        try {
            javax.xml.parsers.DocumentBuilderFactory f = javax.xml.parsers.DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(true);
            f.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml.getBytes("UTF-8")));
            return true;
        } catch (Exception e) {
            System.out.println("Invalid XML: " + e);
            return false;
        }
    }

    private static void check(String name, Object actual, Object expected) {
        boolean ok = actual instanceof Float
                ? Math.abs((Float) actual - (Float) expected) < 0.0001f
                : actual.equals(expected);
        System.out.println((ok ? "OK   " : "FAIL ") + name + (ok ? "" : ": got " + actual + ", expected " + expected));
        if (!ok) failures++;
    }
}
