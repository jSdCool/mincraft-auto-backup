package com.backup;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.server.jsonrpc.OutgoingRpcMethod;
import net.minecraft.server.jsonrpc.api.ParamInfo;
import net.minecraft.server.jsonrpc.api.Schema;
import net.minecraft.server.jsonrpc.internalapi.MinecraftApi;
import net.minecraft.server.jsonrpc.methods.ClientInfo;
import org.jetbrains.annotations.NotNull;

public class BackupRpcDispatcher {

//    private static int testId = 0;

    public static Holder.Reference<@NotNull OutgoingRpcMethod<@NotNull Void, @NotNull Void>> BACKUP_STARTED;
    public static Holder.Reference<@NotNull OutgoingRpcMethod<@NotNull Long,@NotNull Void>> BACKUP_COMPLETED;

    public static Boolean run(@SuppressWarnings("unused") MinecraftApi dispatcher){
        Main.backup("manual, management server", Main.config.getCompressionType(),Main.config.getFlush());
        return true;
    }

    public static String runUsing(@SuppressWarnings("unused")MinecraftApi dispatcher, IncomingRpcRunInfo entry,@SuppressWarnings("unused") ClientInfo remote){
        boolean flush;
        CompressionType compressionType = Main.config.getCompressionType();
        String warning = "true\n";

        flush = entry.flush().orElse(Main.config.getFlush());
        String compressionString = entry.compressionType().orElse(Main.config.getCompressionType().getSerializedName());
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

    public static Boolean getFlush(@SuppressWarnings("unused")MinecraftApi dispatcher){
        return Main.config.getFlush();
    }
    //flush/set
    public static Boolean setFlush(@SuppressWarnings("unused")MinecraftApi dispatcher, Boolean entry,@SuppressWarnings("unused") ClientInfo remote){
        Main.config.setFlush(entry);
        return entry;
    }
    //enabled
    public static Boolean getEnabled(@SuppressWarnings("unused")MinecraftApi dispatcher){
        return Main.config.isEnabled();
    }
    //enabled/set
    public static Boolean setEnabled(@SuppressWarnings("unused")MinecraftApi dispatcher, Boolean entry,@SuppressWarnings("unused") ClientInfo remote){
        Main.config.setEnabled(entry);
        return entry;
    }

    public static List<String> getCompressionTypes(@SuppressWarnings("unused")MinecraftApi dispatcher){
        return Arrays.stream(CompressionType.values()).map(CompressionType::getSerializedName).toList();
    }


    public static final Schema<@NotNull IncomingRpcRunInfo> USING_SCHEMA = Schema.record(IncomingRpcRunInfo.CODEC.codec()).withField("flush",Schema.BOOL_SCHEMA).withField("compressionType",Schema.STRING_SCHEMA);

    public record IncomingRpcRunInfo(Optional<Boolean> flush, Optional<String> compressionType){
        public static final MapCodec<IncomingRpcRunInfo> CODEC = RecordCodecBuilder.mapCodec( (instance) -> instance.group(
                Codec.BOOL.optionalFieldOf("flush").forGetter(IncomingRpcRunInfo::flush),
                Codec.STRING.optionalFieldOf("compressionType").forGetter(IncomingRpcRunInfo::compressionType)
            ).apply(instance, IncomingRpcRunInfo::new));
    }

    public static void register(){
        BACKUP_STARTED = OutgoingRpcMethod.notification().description("Server backup started").register("backup/started");
        BACKUP_COMPLETED = OutgoingRpcMethod.<Long>notificationWithParams().param("time", Schema.ofType("long",Codec.LONG)).description("Backup completed").register("backup/completed");
    }

}
