package com.backup;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.StringRepresentableArgument;

public class CompressionArgumentType extends StringRepresentableArgument<CompressionType> {
    protected CompressionArgumentType() {
        super(CompressionType.CODEC, CompressionType::values);
    }

    public static StringRepresentableArgument<CompressionType> compressionType() {
        return new CompressionArgumentType();
    }

    public static CompressionType getCompressionType(CommandContext<CommandSourceStack> context, String id) {
        return (CompressionType)context.getArgument(id, CompressionType.class);
    }
}
