int abyss_offset32(uint lvl, int lod_y) {
    int start = -8 >> lvl;
    if (lod_y >= start) return 0;
    int delta = 16 >> lvl;
    return (start - lod_y + delta - 1) / delta;
}

vec3 abyss_offset(vec3 pos, uint lvl, int lod_y) {
    vec3 ret = vec3(pos);
    ret.y = ret.y + 32 * abyss_offset32(lvl, lod_y);
    return ret;
}

ivec3 abyss_offset32(ivec3 baseSectionPos, uint lvl, int lod_y) {
    ivec3 ret = ivec3(baseSectionPos);
    ret.y = ret.y - abyss_offset32(lvl, lod_y);
    return ret;
}