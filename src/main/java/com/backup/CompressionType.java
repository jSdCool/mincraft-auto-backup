package com.backup;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringIdentifiable;

public enum CompressionType implements StringIdentifiable {
    NONE(false),
    ZIP(false),
    GZIP(false),
    LZ4(false),
    XZ(true),
    LZMA(true);

    CompressionType(boolean slow){
        this.slow=slow;
    }
    private final boolean slow;

    public boolean slow(){
        return slow;
    }

    @Override
    public String asString() {
        return this.toString();
    }

    public static final Codec<CompressionType> CODEC = StringIdentifiable.createCodec(CompressionType::values);
}
