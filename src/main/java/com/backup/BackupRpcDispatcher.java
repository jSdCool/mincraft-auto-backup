package com.backup;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.dedicated.management.dispatch.ManagementHandlerDispatcher;

import java.util.ArrayList;
import java.util.List;

public class BackupRpcDispatcher {

    static int testId = 0;

    public static List<TestData> test(ManagementHandlerDispatcher dispatcher) {
        List<TestData> tl = new ArrayList<>();
        tl.add(new TestData(testId++));
        Main.LOGGER.info("TEST!!!");
        return tl;
    }

    public static List<BooleanResult> run(ManagementHandlerDispatcher dispatcher){
        Main.backup("manual, management server", Main.config.getCompressionType());
        return List.of(new BooleanResult(true));
    }


    public record BooleanResult(boolean value){
        public static final MapCodec<BooleanResult> CODEC = RecordCodecBuilder.mapCodec( (instance) -> instance.group(
                Codec.BOOL.fieldOf("result").forGetter(BooleanResult::value)
        ).apply(instance,BooleanResult::new));
    }

    public record TestData(int d) {
        public static final MapCodec<TestData> CODEC = RecordCodecBuilder.mapCodec((instance) -> instance.group(
                Codec.INT.fieldOf("d").forGetter(TestData::d)
            ).apply(instance, TestData::new)
        );
    }

}
