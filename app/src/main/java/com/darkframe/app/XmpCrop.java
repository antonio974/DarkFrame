package com.darkframe.app;

import java.util.Locale;

/**
 * Lightroom / Camera Raw crop as an XMP sidecar file (DSC01234.xmp next to DSC01234.ARW).
 * Pure Java so it can be tested off the camera.
 *
 * Lightroom reads this file on import: the photo opens already cropped, the RAW is untouched,
 * and you can always go back to the full 3:2 frame.
 */
public final class XmpCrop {
    /** Crop edges, as fractions of the image (0 to 1), in sensor orientation. */
    public final double top, left, bottom, right;

    private XmpCrop(double top, double left, double bottom, double right) {
        this.top = top;
        this.left = left;
        this.bottom = bottom;
        this.right = right;
    }

    /** Centered crop to the target ratio in an image of ratio imageRatio (3:2 for the a7R). */
    public static XmpCrop forRatio(double imageRatio, double targetRatio) {
        if (targetRatio > imageRatio) {
            double band = (1 - imageRatio / targetRatio) / 2;
            return new XmpCrop(band, 0, 1 - band, 1);
        } else {
            double band = (1 - targetRatio / imageRatio) / 2;
            return new XmpCrop(0, band, 1, 1 - band);
        }
    }

    /** True if the crop changes nothing (3:2 format). */
    public boolean isFull() {
        return top < 0.0005 && left < 0.0005 && bottom > 0.9995 && right > 0.9995;
    }

    /** XMP file content, with the keyword "Format 2.35:1" for filtering in Lightroom. */
    public String toXmp(String formatName) {
        return toXmp(formatName, null);
    }

    /** XMP for the crop, plus the film settings when there is one (null film: crop only). */
    public String toXmp(String formatName, FilmLook film) {
        boolean crop = !isFull();
        return "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\" x:xmptk=\"DarkFrame\">\n"
                + " <rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\">\n"
                + "  <rdf:Description rdf:about=\"\"\n"
                + "    xmlns:crs=\"http://ns.adobe.com/camera-raw-settings/1.0/\"\n"
                + "    xmlns:dc=\"http://purl.org/dc/elements/1.1/\"\n"
                + "   crs:HasSettings=\"True\"\n"
                + (crop
                    ? "   crs:HasCrop=\"True\"\n"
                    + "   crs:CropTop=\"" + num(top) + "\"\n"
                    + "   crs:CropLeft=\"" + num(left) + "\"\n"
                    + "   crs:CropBottom=\"" + num(bottom) + "\"\n"
                    + "   crs:CropRight=\"" + num(right) + "\"\n"
                    + "   crs:CropAngle=\"0\"\n"
                    + "   crs:CropConstrainToWarp=\"0\"\n"
                    : "   crs:HasCrop=\"False\"\n")
                + (film != null ? film.xmpAttributes() : "")
                + "   crs:AlreadyApplied=\"False\">\n"
                + (film != null ? film.xmpCurve() : "")
                + "   <dc:subject>\n"
                + "    <rdf:Bag>\n"
                + "     <rdf:li>Format " + escape(formatName) + "</rdf:li>\n"
                + (film != null ? "     <rdf:li>Film " + escape(film.name) + "</rdf:li>\n" : "")
                + "    </rdf:Bag>\n"
                + "   </dc:subject>\n"
                + "  </rdf:Description>\n"
                + " </rdf:RDF>\n"
                + "</x:xmpmeta>\n";
    }

    /** Format name ("2.35:1") stored in an XMP written by the app, or null if there is none. */
    public static String formatFromXmp(String text) {
        if (text == null) return null;
        String tag = "<rdf:li>Format ";
        int start = text.indexOf(tag);
        if (start < 0) return null;
        start += tag.length();
        int end = text.indexOf('<', start);
        if (end < 0) return null;
        return text.substring(start, end).replace("&lt;", "<").replace("&gt;", ">").replace("&amp;", "&");
    }

    private static String num(double value) {
        return String.format(Locale.US, "%.6f", value);
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
