package me.cortex.voxy.client.core.util;

public class AbyssUtil {
    private static int abyss_wy = -256;
    private static int abyss_wh = 512;
    private static int abyss_dx = 16384;
    private static int abyss_dy = 480;
    private static int abyss_overlap = 32;
    private static int abyss_sections = 15;
    
    private static String[] section_names = {
        "L0/L1S1",
        "L1S2",
        "L1S3",
        "L1S4/L2S1",
        "L2S2",
        "L2S3/L3S1",
        "L3S2",
        "L3S3",
        "L3S4/L4S1",
        "L4S2",
        "L4S3",
        "L4S4",
        "L4S5/L5S1",
        "L5S2",
        "L5S3",
    };

    // accept world x
    public static int getSection(double x) {
        return (int)(x / abyss_dx + 0.5);
    }
    
    public static String getSectionName(int s) {
        if (s < 0 || s > section_names.length - 1) return "";
        return section_names[s];
    }

    public static class Coords {
        public double x, y;
        public Coords(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    public static Coords toAbyss(double x, double y) {
        int section = getSection(x);
        double _x = abyss_dx * ((x / abyss_dx + 0.5) % 1 - 0.5);
        double _y = y - section * abyss_dy;
        return new Coords(_x, _y);
    }

    private static boolean belongsToAbyssSection(int s, int y) {
        if (s == 0 && y > 0) return true;
        int min_y = abyss_wy - s * abyss_dy;
        int max_y = abyss_wy + abyss_wh - s * abyss_dy;
        return y >= min_y && y < max_y;
    }

    public static int getAbyssSection(int y) {
        for (int s = 0; s < abyss_sections; s++) {
            if (belongsToAbyssSection(s, y)) return s;
        }
        return 0;
    }

    public static Coords toWorld(double x, double y) {
        int section = getAbyssSection((int)y);
        double _x = section * abyss_dx + x;
        double _y = y - section * abyss_dy;
        return new Coords(_x, _y);
    }
}
