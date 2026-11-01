package com.pohtomc.client.texture;

import com.mojang.blaze3d.platform.NativeImage;
import com.pohtomc.PohtoMcMod;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class GifDecoder {
    public static final int MAX_FRAMES = 150;
    public static final int MAX_GIF_DIMENSION = 512;
    private static final int DEFAULT_DELAY_MS = 100;
    private static final int MIN_DELAY_MS = 20;

    public record GifFrame(NativeImage image, int delayMs, int startTimestampMs) {
    }

    public static class GifAnimation {
        public final int width;
        public final int height;
        public final List<GifFrame> frames;
        public final int totalDurationMs;

        public GifAnimation(int width, int height, List<GifFrame> frames, int totalDurationMs) {
            this.width = width;
            this.height = height;
            this.frames = frames;
            this.totalDurationMs = Math.max(MIN_DELAY_MS, totalDurationMs);
        }

        public GifFrame getFrameAt(long elapsedMs) {
            if (this.frames.isEmpty()) {
                return null;
            }
            if (this.frames.size() == 1) {
                return this.frames.get(0);
            }
            int timeInLoop = (int) (elapsedMs % (long) this.totalDurationMs);
            for (GifFrame frame : this.frames) {
                if (timeInLoop >= frame.startTimestampMs && timeInLoop < frame.startTimestampMs + frame.delayMs) {
                    return frame;
                }
            }
            return this.frames.get(this.frames.size() - 1);
        }

        public void closeAll() {
            for (GifFrame frame : this.frames) {
                if (frame.image != null) {
                    try {
                        frame.image.close();
                    } catch (Exception ignored) {
                    }
                }
            }
        }
    }

    public static GifAnimation decode(byte[] bytes) throws IOException {
        if (!PohtoMcMod.isGif(bytes)) {
            return null;
        }

        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("gif");
        if (!readers.hasNext()) {
            return null;
        }

        ImageReader reader = readers.next();
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            reader.setInput(in, false);
            int count = reader.getNumImages(true);
            if (count <= 0) {
                return null;
            }

            int frameCount = Math.min(count, MAX_FRAMES);
            int canvasW = Math.max(1, reader.getWidth(0));
            int canvasH = Math.max(1, reader.getHeight(0));

            // Compute scaling if canvas exceeds limit
            double scale = 1.0;
            if (canvasW > MAX_GIF_DIMENSION || canvasH > MAX_GIF_DIMENSION) {
                scale = Math.min((double) MAX_GIF_DIMENSION / (double) canvasW, (double) MAX_GIF_DIMENSION / (double) canvasH);
            }
            int finalW = Math.max(1, (int) Math.round((double) canvasW * scale));
            int finalH = Math.max(1, (int) Math.round((double) canvasH * scale));

            BufferedImage master = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);
            Graphics2D masterG = master.createGraphics();
            masterG.setBackground(new Color(0, 0, 0, 0));

            BufferedImage previous = null;
            List<GifFrame> decodedFrames = new ArrayList<>();
            int accumulatedMs = 0;

            try {
                for (int i = 0; i < frameCount; ++i) {
                    BufferedImage rawFrame = reader.read(i);
                    IIOMetadata metadata = reader.getImageMetadata(i);
                    FrameMetadata meta = readMetadata(metadata);

                    int delay = meta.delayMs <= 10 ? DEFAULT_DELAY_MS : meta.delayMs;
                    delay = Math.max(MIN_DELAY_MS, delay);

                    if ("restoreToPrevious".equalsIgnoreCase(meta.disposalMethod) && previous == null) {
                        previous = copyImage(master);
                    }

                    masterG.drawImage(rawFrame, meta.x, meta.y, null);

                    // Prepare scaled snapshot for this frame
                    BufferedImage snapshot;
                    if (scale < 1.0) {
                        snapshot = new BufferedImage(finalW, finalH, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D sg = snapshot.createGraphics();
                        sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        sg.drawImage(master, 0, 0, finalW, finalH, null);
                        sg.dispose();
                    } else {
                        snapshot = copyImage(master);
                    }

                    NativeImage nativeFrame = bufferedToNativeImage(snapshot);
                    decodedFrames.add(new GifFrame(nativeFrame, delay, accumulatedMs));
                    accumulatedMs += delay;

                    // Handle disposal for next iteration
                    if ("restoreToBackgroundColor".equalsIgnoreCase(meta.disposalMethod)) {
                        masterG.setComposite(AlphaComposite.Clear);
                        masterG.fillRect(meta.x, meta.y, rawFrame.getWidth(), rawFrame.getHeight());
                        masterG.setComposite(AlphaComposite.SrcOver);
                    } else if ("restoreToPrevious".equalsIgnoreCase(meta.disposalMethod) && previous != null) {
                        masterG.setComposite(AlphaComposite.Src);
                        masterG.drawImage(previous, 0, 0, null);
                        masterG.setComposite(AlphaComposite.SrcOver);
                        previous = null;
                    }
                }

                masterG.dispose();
                return new GifAnimation(finalW, finalH, decodedFrames, accumulatedMs);
            } catch (Exception e) {
                masterG.dispose();
                for (GifFrame frame : decodedFrames) {
                    if (frame.image != null) {
                        try {
                            frame.image.close();
                        } catch (Exception ignored) {
                        }
                    }
                }
                throw e;
            }
        } finally {
            reader.dispose();
        }
    }

    private static NativeImage bufferedToNativeImage(BufferedImage src) {
        int w = src.getWidth();
        int h = src.getHeight();
        boolean hasAlpha = src.getColorModel().hasAlpha();
        int[] pixels = src.getRGB(0, 0, w, h, null, 0, w);
        NativeImage nativeImage = new NativeImage(w, h, false);
        int idx = 0;
        for (int y = 0; y < h; ++y) {
            for (int x = 0; x < w; ++x) {
                int argb = pixels[idx++];
                if (!hasAlpha) {
                    argb |= 0xFF000000;
                }
                nativeImage.setPixel(x, y, argb);
            }
        }
        return nativeImage;
    }

    private static BufferedImage copyImage(BufferedImage src) {
        BufferedImage copy = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = copy.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return copy;
    }

    private record FrameMetadata(int x, int y, int delayMs, String disposalMethod) {
    }

    private static FrameMetadata readMetadata(IIOMetadata metadata) {
        int x = 0;
        int y = 0;
        int delayMs = DEFAULT_DELAY_MS;
        String disposal = "none";

        if (metadata == null) {
            return new FrameMetadata(x, y, delayMs, disposal);
        }

        String[] names = metadata.getMetadataFormatNames();
        for (String name : names) {
            if ("javax_imageio_gif_image_1.0".equalsIgnoreCase(name)) {
                Node root = metadata.getAsTree(name);
                NodeList children = root.getChildNodes();
                for (int i = 0; i < children.getLength(); ++i) {
                    Node node = children.item(i);
                    if ("ImageDescriptor".equalsIgnoreCase(node.getNodeName())) {
                        NamedNodeMap attrs = node.getAttributes();
                        if (attrs != null) {
                            Node nx = attrs.getNamedItem("imageLeftPosition");
                            Node ny = attrs.getNamedItem("imageTopPosition");
                            if (nx != null) x = parseInt(nx.getNodeValue(), 0);
                            if (ny != null) y = parseInt(ny.getNodeValue(), 0);
                        }
                    } else if ("GraphicControlExtension".equalsIgnoreCase(node.getNodeName())) {
                        NamedNodeMap attrs = node.getAttributes();
                        if (attrs != null) {
                            Node ndelay = attrs.getNamedItem("delayTime");
                            Node ndisposal = attrs.getNamedItem("disposalMethod");
                            if (ndelay != null) {
                                int cs = parseInt(ndelay.getNodeValue(), 10);
                                delayMs = cs * 10;
                            }
                            if (ndisposal != null) {
                                disposal = ndisposal.getNodeValue();
                            }
                        }
                    }
                }
            }
        }

        return new FrameMetadata(x, y, delayMs, disposal);
    }

    private static int parseInt(String val, int fallback) {
        try {
            return Integer.parseInt(val);
        } catch (Exception e) {
            return fallback;
        }
    }
}
