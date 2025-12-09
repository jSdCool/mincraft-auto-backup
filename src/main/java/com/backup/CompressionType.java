package com.backup;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

public enum CompressionType implements StringRepresentable {
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
    public String getSerializedName() {
        return this.toString();
    }

    public static final Codec<CompressionType> CODEC = StringRepresentable.fromEnum(CompressionType::values);

    public static CompressionType of(String type){
        for(CompressionType value : values()){
            if(value.toString().equalsIgnoreCase(type)){
                return value;

            }
        }
        return null;
    }
}
