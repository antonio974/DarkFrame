package com.darkframe.app;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Adds an XMP block to a JPEG without touching the image: existing segments and compressed data
 * are copied byte for byte. Lightroom reads this block on import.
 * Pure Java so it can be tested off the camera.
 */
public final class JpegXmp {
    private static final String XMP_HEADER = "http://ns.adobe.com/xap/1.0/\0";
    private static final int MAX_SEGMENT = 65533;

    private JpegXmp() {}

    /** Full XMP packet, with the xpacket headers recommended by Adobe. */
    public static String packet(String xmpmeta) {
        return "<?xpacket begin=\"﻿\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>\n" + xmpmeta + "<?xpacket end=\"w\"?>";
    }

    /** Length of the XMP APP1 segment (marker excluded), as inserted by insert(). */
    public static int segmentLength(String xmpPacket) throws IOException {
        return 2 + XMP_HEADER.length() + xmpPacket.getBytes("UTF-8").length;
    }

    /** True if the JPEG already has an XMP block (it is not replaced). */
    public static boolean hasXmp(InputStream jpeg) throws IOException {
        DataInputStream in = new DataInputStream(jpeg);
        if (in.readUnsignedShort() != 0xFFD8) throw new IOException("not a JPEG");
        while (true) {
            int marker = readMarker(in);
            if (marker < 0xFFE0 || marker > 0xFFEF) return false;
            byte[] data = readSegment(in);
            if (marker == 0xFFE1 && startsWith(data, XMP_HEADER)) return true;
        }
    }

    /**
     * Copies the JPEG from in to out, inserting the XMP block right after the leading APPn segments
     * (after the Exif). Everything else is copied as is.
     */
    public static void insert(InputStream jpeg, OutputStream out, String xmpPacket) throws IOException {
        byte[] header = XMP_HEADER.getBytes("ISO-8859-1");
        byte[] body = xmpPacket.getBytes("UTF-8");
        int length = segmentLength(xmpPacket);
        if (length > MAX_SEGMENT) throw new IOException("XMP too long");

        DataInputStream in = new DataInputStream(jpeg);
        if (in.readUnsignedShort() != 0xFFD8) throw new IOException("not a JPEG");
        out.write(0xFF);
        out.write(0xD8);

        int marker;
        while (true) {
            marker = readMarker(in);
            if (marker < 0xFFE0 || marker > 0xFFEF) break;
            byte[] data = readSegment(in);
            writeSegment(out, marker, data);
        }

        out.write(0xFF);
        out.write(0xE1);
        out.write(length >> 8);
        out.write(length & 0xFF);
        out.write(header);
        out.write(body);

        // First non-APPn segment (DQT, SOF...) then everything else, without parsing.
        out.write(marker >> 8);
        out.write(marker & 0xFF);
        byte[] buffer = new byte[64 * 1024];
        int n;
        while ((n = in.read(buffer)) > 0)
            out.write(buffer, 0, n);
    }

    private static int readMarker(DataInputStream in) throws IOException {
        int b = in.readUnsignedByte();
        if (b != 0xFF) throw new IOException("JPEG marker expected");
        int m;
        do {
            m = in.readUnsignedByte(); // optional 0xFF fill bytes
        } while (m == 0xFF);
        return 0xFF00 | m;
    }

    private static byte[] readSegment(DataInputStream in) throws IOException {
        int length = in.readUnsignedShort();
        if (length < 2) throw new EOFException("invalid JPEG segment");
        byte[] data = new byte[length - 2];
        in.readFully(data);
        return data;
    }

    private static void writeSegment(OutputStream out, int marker, byte[] data) throws IOException {
        int length = data.length + 2;
        out.write(marker >> 8);
        out.write(marker & 0xFF);
        out.write(length >> 8);
        out.write(length & 0xFF);
        out.write(data);
    }

    private static boolean startsWith(byte[] data, String prefix) {
        if (data.length < prefix.length()) return false;
        for (int i = 0; i < prefix.length(); i++)
            if (data[i] != (byte) prefix.charAt(i)) return false;
        return true;
    }
}
