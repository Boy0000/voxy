int abyss_section(uint lvl, int lod_y) {
    int start = -8 >> lvl;
    if (lod_y >= start) return 0;
    int delta = 16 >> lvl;
    return (start - lod_y + delta - 1) / delta;
}
