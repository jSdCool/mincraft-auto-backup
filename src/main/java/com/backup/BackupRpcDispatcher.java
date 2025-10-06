package com.backup;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.dedicated.management.OutgoingRpcMethod;
import net.minecraft.server.dedicated.management.RpcRequestParameter;
import net.minecraft.server.dedicated.management.dispatch.ManagementHandlerDispatcher;
import net.minecraft.server.dedicated.management.network.ManagementConnectionId;
import net.minecraft.server.dedicated.management.schema.RpcSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BackupRpcDispatcher {

    private static int testId = 0;

    public static RegistryEntry.Reference<OutgoingRpcMethod.Simple> BACKUP_STARTED;
    public static RegistryEntry.Reference<OutgoingRpcMethod.Notification<Long>> BACKUP_COMPLETED;

    public static List<TestData> test(ManagementHandlerDispatcher dispatcher) {
        List<TestData> tl = new ArrayList<>();
        tl.add(new TestData(testId++));
        Main.LOGGER.info("TEST!!!");
        return tl;
    }

    public static List<BooleanResult> run(ManagementHandlerDispatcher dispatcher){
        Main.backup("manual, management server", Main.config.getCompressionType(),Main.config.getFlush());
        return List.of(new BooleanResult(true));
    }

    public static List<StringResult> runUsing(ManagementHandlerDispatcher dispatcher, IncomingRpcRunInfo entry, ManagementConnectionId remote){
        boolean flush;
        CompressionType compressionType = Main.config.getCompressionType();
        String warning = "true\n";

        flush = entry.flush().orElse(Main.config.getFlush());
        String compressionString = entry.compressionType().orElse(Main.config.getCompressionType().asString());
        CompressionType tmp = CompressionType.of(compressionString);
        if(tmp != null){
            compressionType = tmp;
        } else {
            warning += "Supplied compression type was not valid: ("+compressionString+")\n";
        }


        Main.backup("manual, management server",compressionType,flush);
        return List.of(new StringResult(Optional.of(warning)));
    }


    public static final RpcSchema USING_SCHEMA = RpcSchema.ofObject().withProperty("flush",RpcSchema.BOOLEAN).withProperty("compressionType",RpcSchema.STRING);

    public record IncomingRpcRunInfo(Optional<Boolean> flush, Optional<String> compressionType){
        public static final MapCodec<IncomingRpcRunInfo> CODEC = RecordCodecBuilder.mapCodec( (instance) -> instance.group(
                Codec.BOOL.optionalFieldOf("flush").forGetter(IncomingRpcRunInfo::flush),
                Codec.STRING.optionalFieldOf("compressionType").forGetter(IncomingRpcRunInfo::compressionType)
            ).apply(instance, IncomingRpcRunInfo::new));
    }

    public record BooleanResult(boolean value){
        public static final MapCodec<BooleanResult> CODEC = RecordCodecBuilder.mapCodec( (instance) -> instance.group(
                Codec.BOOL.fieldOf("result").forGetter(BooleanResult::value)
        ).apply(instance,BooleanResult::new));
    }

    public record StringResult(Optional<String> value){
        public static final MapCodec<StringResult> CODEC = RecordCodecBuilder.mapCodec( (instance) -> instance.group(
                Codec.STRING.optionalFieldOf("result").forGetter(StringResult::value)
        ).apply(instance, StringResult::new));
    }

    public record TestData(int d) {
        public static final MapCodec<TestData> CODEC = RecordCodecBuilder.mapCodec((instance) -> instance.group(
                Codec.INT.fieldOf("d").forGetter(TestData::d)
            ).apply(instance, TestData::new)
        );
    }

    public static void register(){
        BACKUP_STARTED = OutgoingRpcMethod.createSimpleBuilder().description("Server backup started").buildAndRegisterVanilla("backup/started");
        BACKUP_COMPLETED = OutgoingRpcMethod.createNotificationBuilder(Codec.LONG).requestParameter(new RpcRequestParameter("time", RpcSchema.INTEGER)).description("Backup completed").buildAndRegisterVanilla("backup/completed");
    }

}
