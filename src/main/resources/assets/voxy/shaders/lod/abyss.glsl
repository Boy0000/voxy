vec3 abyss_offset(vec3 pos, uint lvl, int lod_y) {
    if (lod_y >= 0) return pos;

    int delta = 16 >> lvl;
    int start = -8 >> lvl;

    vec3 ret = vec3(pos);
    for (int i = 0; i < 16; i++) {
        if (lod_y < start - delta * i) {
            ret.y = ret.y + 32;
        }
    }
    return ret;
}