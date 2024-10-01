package com.backup;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.serialization.Codec;
import net.minecraft.command.argument.BlockMirrorArgumentType;
import net.minecraft.command.argument.EnumArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.util.BlockMirror;

import java.util.function.Supplier;

public class CompressionArgumentType extends EnumArgumentType<CompressionType> {
    protected CompressionArgumentType() {
        super(CompressionType.CODEC, CompressionType::values);
    }

    public static EnumArgumentType<CompressionType> compressionType() {
        return new CompressionArgumentType();
    }

    public static CompressionType getCompressionType(CommandContext<ServerCommandSource> context, String id) {
        return (CompressionType)context.getArgument(id, CompressionType.class);
    }
}
