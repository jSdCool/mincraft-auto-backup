package com.backup;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringIdentifiable;

public enum CompressionType implements StringIdentifiable {
    NONE,
    ZIP,
    GZIP,
    LZ4,
    XZ,
    LZMA;

    @Override
    public String asString() {
        return this.toString();
    }

    public static final Codec<CompressionType> CODEC = StringIdentifiable.createCodec(CompressionType::values);
}
