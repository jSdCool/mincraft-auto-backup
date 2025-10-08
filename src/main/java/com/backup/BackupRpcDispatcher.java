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

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class BackupRpcDispatcher {

//    private static int testId = 0;

    public static RegistryEntry.Reference<OutgoingRpcMethod.Simple> BACKUP_STARTED;
    public static RegistryEntry.Reference<OutgoingRpcMethod.Notification<Long>> BACKUP_COMPLETED;

//    public static List<TestData> test(ManagementHandlerDispatcher dispatcher) {
//        List<TestData> tl = new ArrayList<>();
//        tl.add(new TestData(testId++));
//        Main.LOGGER.info("TEST!!!");
//        return tl;
//    }

    public static Boolean run(@SuppressWarnings("unused") ManagementHandlerDispatcher dispatcher){
        Main.backup("manual, management server", Main.config.getCompressionType(),Main.config.getFlush());
        return true;
    }

    public static String runUsing(@SuppressWarnings("unused")ManagementHandlerDispatcher dispatcher, IncomingRpcRunInfo entry,@SuppressWarnings("unused") ManagementConnectionId remote){
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
        return warning;
    }

    //flush

    public static Boolean getFlush(@SuppressWarnings("unused")ManagementHandlerDispatcher dispatcher){
        return Main.config.getFlush();
    }
    //flush/set
    public static Boolean setFlush(@SuppressWarnings("unused")ManagementHandlerDispatcher dispatcher, Boolean entry,@SuppressWarnings("unused") ManagementConnectionId remote){
        Main.config.setFlush(entry);
        return entry;
    }
    //enabled
    public static Boolean getEnabled(@SuppressWarnings("unused")ManagementHandlerDispatcher dispatcher){
        return Main.config.isEnabled();
    }
    //enabled/set
    public static Boolean setEnabled(@SuppressWarnings("unused")ManagementHandlerDispatcher dispatcher, Boolean entry,@SuppressWarnings("unused") ManagementConnectionId remote){
        Main.config.setEnabled(entry);
        return entry;
    }

    public static List<String> getCompressionTypes(@SuppressWarnings("unused")ManagementHandlerDispatcher dispatcher){
        return Arrays.stream(CompressionType.values()).map(CompressionType::asString).toList();
    }


    public static final RpcSchema USING_SCHEMA = RpcSchema.ofObject().withProperty("flush",RpcSchema.BOOLEAN).withProperty("compressionType",RpcSchema.STRING);

    public record IncomingRpcRunInfo(Optional<Boolean> flush, Optional<String> compressionType){
        public static final MapCodec<IncomingRpcRunInfo> CODEC = RecordCodecBuilder.mapCodec( (instance) -> instance.group(
                Codec.BOOL.optionalFieldOf("flush").forGetter(IncomingRpcRunInfo::flush),
                Codec.STRING.optionalFieldOf("compressionType").forGetter(IncomingRpcRunInfo::compressionType)
            ).apply(instance, IncomingRpcRunInfo::new));
    }

//    public record TestData(int d) {
//        public static final MapCodec<TestData> CODEC = RecordCodecBuilder.mapCodec((instance) -> instance.group(
//                Codec.INT.fieldOf("d").forGetter(TestData::d)
//            ).apply(instance, TestData::new)
//        );
//    }

    public static void register(){
        BACKUP_STARTED = OutgoingRpcMethod.createSimpleBuilder().description("Server backup started").buildAndRegisterVanilla("backup/started");
        BACKUP_COMPLETED = OutgoingRpcMethod.createNotificationBuilder(Codec.LONG).requestParameter(new RpcRequestParameter("time", RpcSchema.INTEGER)).description("Backup completed").buildAndRegisterVanilla("backup/completed");
    }

}
