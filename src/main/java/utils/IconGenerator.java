package utils;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

public class IconGenerator {

    public static void main(String[] args) {
        try {
            generateIco();
            System.out.println("✅ Windows ICO file generated successfully.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void generateIco() throws Exception {
        File srcFile = new File("src/main/resources/images/logo.png");
        if (!srcFile.exists()) {
            throw new IOException("Source logo.png not found at: " + srcFile.getAbsolutePath());
        }

        BufferedImage source = ImageIO.read(srcFile);
        int[] sizes = {16, 24, 32, 48, 64, 128, 256};
        List<byte[]> pngs = new ArrayList<>();

        for (int size : sizes) {
            BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(source, 0, 0, size, size, null);
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(scaled, "png", baos);
            pngs.add(baos.toByteArray());
        }

        File destFile = new File("src/main/resources/images/app_icon.ico");
        try (FileOutputStream fos = new FileOutputStream(destFile)) {
            int count = pngs.size();

            // 1. Write Header (6 bytes)
            fos.write(new byte[]{0, 0}); // Reserved
            fos.write(new byte[]{1, 0}); // Type (1 = ICO)
            fos.write(new byte[]{(byte) (count & 0xFF), (byte) ((count >> 8) & 0xFF)}); // Image Count

            // 2. Write Entries (16 bytes per image)
            int offset = 6 + count * 16;
            for (int i = 0; i < count; i++) {
                int size = sizes[i];
                byte[] data = pngs.get(i);
                int dataSize = data.length;

                fos.write(size == 256 ? 0 : size); // Width
                fos.write(size == 256 ? 0 : size); // Height
                fos.write(0); // Color count (0 if >= 8bpp)
                fos.write(0); // Reserved
                fos.write(new byte[]{1, 0}); // Color planes (1)
                fos.write(new byte[]{32, 0}); // Bits per pixel (32)

                // Data size (4 bytes, little endian)
                ByteBuffer bufSize = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(dataSize);
                fos.write(bufSize.array());

                // Offset (4 bytes, little endian)
                ByteBuffer bufOffset = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(offset);
                fos.write(bufOffset.array());

                offset += dataSize;
            }

            // 3. Write Image Data (PNG byte arrays)
            for (byte[] data : pngs) {
                fos.write(data);
            }
        }
    }
}
