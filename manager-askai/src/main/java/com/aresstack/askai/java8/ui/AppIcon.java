package com.aresstack.askai.java8.ui;

import java.awt.Image;
import java.awt.Window;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The Ollama Manager application icon, replacing the Java default cup on every window. Loaded ONCE from
 * the bundled, pre-downsized resources (generated from branding/app-icon-original.png) and offered in
 * multiple sizes so the title bar, taskbar and Alt-Tab each pick a crisp variant.
 * A missing/broken resource logs loudly and yields an empty list — a window without an icon is
 * cosmetic, a crash at startup is not.
 */
public final class AppIcon {

    static final String RESOURCE = "/icons/app-icon-%d.png";
    private static final int[] SIZES = {16, 20, 24, 32, 40, 48, 64, 128, 256};

    private static volatile List<Image> cached;

    private AppIcon() {
    }

    /** Set the multi-size icon on a top-level window (owned dialogs inherit it anyway). */
    public static void apply(Window window) {
        List<Image> images = images();
        if (window != null && !images.isEmpty()) {
            window.setIconImages(images);
        }
    }

    /** The variant closest to (at least) {@code size} pixels, or null when the icon is unavailable. */
    public static Image sized(int size) {
        List<Image> images = images();
        if (images.isEmpty()) {
            return null;
        }
        for (int i = 0; i < SIZES.length; i++) {
            if (SIZES[i] >= size) {
                return images.get(i);
            }
        }
        return images.get(images.size() - 1);
    }

    public static List<Image> images() {
        List<Image> images = cached;
        if (images == null) {
            synchronized (AppIcon.class) {
                images = cached;
                if (images == null) {
                    images = load(RESOURCE);
                    cached = images;
                }
            }
        }
        return images;
    }

    static List<Image> load(String resource) {
        try {
            List<Image> images = new ArrayList<Image>(SIZES.length);
            for (int size : SIZES) {
                String name = String.format(resource, size);
                InputStream stream = AppIcon.class.getResourceAsStream(name);
                if (stream == null) {
                    System.err.println("[app-icon] resource missing: " + name);
                    return Collections.emptyList();
                }
                BufferedImage image;
                try {
                    image = javax.imageio.ImageIO.read(stream);
                } finally {
                    stream.close();
                }
                if (image == null) {
                    System.err.println("[app-icon] resource unreadable: " + name);
                    return Collections.emptyList();
                }
                images.add(image);
            }
            return Collections.unmodifiableList(images);
        } catch (Exception broken) {
            System.err.println("[app-icon] failed to load " + resource + ": " + broken);
            return Collections.emptyList();
        }
    }
}
