package org.lts.litematicaplus.util;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

public final class Png {
    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private Png() {
    }

    public static byte[] encode(int width, int height, int[] argb) {
        if (width <= 0 || height <= 0 || argb.length < width * height) {
            throw new IllegalArgumentException("Image is " + width + "x" + height + " with " + argb.length + " pixels");
        }
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            out.write(SIGNATURE);
            ByteArrayOutputStream header = new ByteArrayOutputStream();
            DataOutputStream headerData = new DataOutputStream(header);
            headerData.writeInt(width);
            headerData.writeInt(height);
            headerData.writeByte(8);
            headerData.writeByte(6);
            headerData.writeByte(0);
            headerData.writeByte(0);
            headerData.writeByte(0);
            chunk(out, "IHDR", header.toByteArray());
            ByteArrayOutputStream pixels = new ByteArrayOutputStream();
            try (DeflaterOutputStream deflate = new DeflaterOutputStream(pixels, new Deflater(Deflater.BEST_COMPRESSION))) {
                byte[] row = new byte[1 + width * 4];
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        int color = argb[y * width + x];
                        int offset = 1 + x * 4;
                        row[offset] = (byte) (color >> 16);
                        row[offset + 1] = (byte) (color >> 8);
                        row[offset + 2] = (byte) color;
                        row[offset + 3] = (byte) (color >>> 24);
                    }
                    deflate.write(row);
                }
            }
            chunk(out, "IDAT", pixels.toByteArray());
            chunk(out, "IEND", new byte[0]);
            return out.toByteArray();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) throws IOException {
        DataOutputStream stream = new DataOutputStream(out);
        byte[] name = type.getBytes(StandardCharsets.US_ASCII);
        stream.writeInt(data.length);
        stream.write(name);
        stream.write(data);
        CRC32 crc = new CRC32();
        crc.update(name);
        crc.update(data);
        stream.writeInt((int) crc.getValue());
    }
}
