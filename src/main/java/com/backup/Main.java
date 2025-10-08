package com.backup;

import com.backup.mixin.CompositeManagementListenerAccessor;
import com.backup.mixin.NotificationManagementListenerAccessor;
import com.backup.mixin.NotificationManagementListenerAccessor2;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.serialization.Codec;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.dedicated.MinecraftDedicatedServer;
import net.minecraft.server.dedicated.management.IncomingRpcMethod;
import net.minecraft.server.dedicated.management.OutgoingRpcMethod;
import net.minecraft.server.dedicated.management.RpcRequestParameter;
import net.minecraft.server.dedicated.management.RpcResponseResult;
import net.minecraft.server.dedicated.management.listener.CompositeManagementListener;
import net.minecraft.server.dedicated.management.listener.ManagementListener;
import net.minecraft.server.dedicated.management.schema.RpcSchema;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.PlainTextContent.Literal;
import net.minecraft.text.MutableText;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static net.minecraft.server.command.CommandManager.literal;

import java.io.File;
import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Scanner;

public class Main implements ModInitializer, ServerTickEvents.EndTick {
    // This logger is used to write text to the console and the log file.
    // It is considered best practice to use your mod id as the logger's name.
    // That way, it's clear which mod wrote info, warnings, and errors.
    public static final String MODID = "backup";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);
    static String worldFolder=""/*,destinationFolder=""*/;
    static boolean savingWasDisabled=false,tenSeccondWarning=false;
    static long nextBackupTime;
   // static float timeBetweenBackups;
   // static boolean flush=false,enabled=true;

    //static CompressionType compressionType;

    public static Config config;

    @Override
    public void onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.

        LOGGER.info("auto backup loaded");
        ServerLifecycleEvents.SERVER_STARTED.register((server) -> {
            ms=server;
            pm = ms.getPlayerManager();

        });

        config = new Config();//load the config file

        try {//scan the server.properties file for the world folder
            Scanner serverProperties=new Scanner(new File("server.properties"));
            while(serverProperties.hasNextLine()){
                String properties=serverProperties.nextLine();
                if(properties.startsWith("level-name=")){
                    worldFolder=properties.substring(11);
                    break;
                }
            }

            serverProperties.close();
        } catch (FileNotFoundException e) {
            //if server.properties is not found then this must be a client where backup is not supported
            LOGGER.error("Server Properties not Found! ",e);
            config.setEnabled(false);
            //don't do anything else
            return;
        }

        //create each separate option for the backup using command
        ArgumentBuilder<ServerCommandSource, ?> usingOptions = literal("using");
        for(CompressionType type :CompressionType.values()){
            usingOptions=usingOptions.then(literal(type.asString()).executes(context -> {
                backup("manual",type,config.getFlush());
                return 1;
            }));
        }

        final ArgumentBuilder<ServerCommandSource, ?> finalUsingOptions = usingOptions;

        //noinspection CodeBlock2Expr
        CommandRegistrationCallback.EVENT.register((dispatcher, commandRegistryAccess, registrationEnvironment) -> {
                dispatcher.register(literal("backup").requires(source -> source.hasPermissionLevel(3)).executes(context -> {
            backup("manual",config.getCompressionType(),config.getFlush());
            return 1;
        })
                .then(literal("enable").executes(context -> {
                    config.setEnabled(true);
                    context.getSource().sendFeedback(()->MutableText.of(new Literal("auto backups enabled")),true);
                    return 1;
                }))
                .then(literal("disable").executes(context -> {
                    config.setEnabled(false);
                    context.getSource().sendFeedback(()->MutableText.of(new Literal("auto backups disabled")),true);
                    return 1;
                }))
                .then(literal("enable_flush").executes(context -> {
                    config.setFlush(true);
                    context.getSource().sendFeedback(()->MutableText.of(new Literal("save flushing enabled")),true);
                    return 1;
                }))
                .then(literal("disable_flush").executes(context -> {
                    config.setFlush(false);
                    context.getSource().sendFeedback(()->MutableText.of(new Literal("save flushing disabled")),true);
                    return 1;
                }))
                .then(finalUsingOptions)
                /*.then(literal("using").then(CommandManager.argument("compression", new CompressionArgumentType()).executes(context -> {//the old system that requires everyone to have this mode, it was bad
                    CompressionType compression = context.getArgument("compression",CompressionType.class);
                    backup("manual",compression);
                    return 1;
                })))*/
        );
        });

        ServerTickEvents.END_SERVER_TICK.register( this);



//        IncomingRpcMethod.Parameterless<List<BackupRpcDispatcher.TestData>> testRpcCommand =  IncomingRpcMethod.createParameterlessBuilder(BackupRpcDispatcher::test,BackupRpcDispatcher.TestData.CODEC.codec().listOf())
//                .description("PIss off this is a test")
//                .result(new RpcResponseResult("test", RpcSchema.NUMBER))
//                .build();
//
//        Registry.register(Registries.INCOMING_RPC_METHOD, Identifier.of(MODID,"test"),testRpcCommand);

        IncomingRpcMethod.Parameterless<Boolean> rawBackupCommand = IncomingRpcMethod.createParameterlessBuilder(BackupRpcDispatcher::run,
                Codec.BOOL)
                .description("Make a new backup with the configured settings")
                .result(new RpcResponseResult("result",RpcSchema.BOOLEAN))
                .build();
        Registry.register(Registries.INCOMING_RPC_METHOD, Identifier.of(MODID, "run"),rawBackupCommand);


        IncomingRpcMethod.Parameterized<BackupRpcDispatcher.IncomingRpcRunInfo,String> paramBackupCommand = IncomingRpcMethod.createParameterizedBuilder(BackupRpcDispatcher::runUsing,
                        BackupRpcDispatcher.IncomingRpcRunInfo.CODEC.codec(),
                        Codec.STRING)
                .description("Make a new backup with the provided settings")
                .parameter(new RpcRequestParameter("using",BackupRpcDispatcher.USING_SCHEMA))
                .result(new RpcResponseResult("result",RpcSchema.STRING))
                .build();
        Registry.register(Registries.INCOMING_RPC_METHOD, Identifier.of(MODID, "run/using"),paramBackupCommand);

        IncomingRpcMethod.Parameterless<Boolean> getFlushCommand = IncomingRpcMethod.createParameterlessBuilder(BackupRpcDispatcher::getFlush,
                Codec.BOOL)
                .description("Get weather flush is currently enabled for backups")
                .result(new RpcResponseResult("enabled",RpcSchema.BOOLEAN))
                .build();
        Registry.register(Registries.INCOMING_RPC_METHOD, Identifier.of(MODID,"flush"),getFlushCommand);

        IncomingRpcMethod.Parameterized<Boolean,Boolean> setFlushCommand = IncomingRpcMethod.createParameterizedBuilder(BackupRpcDispatcher::setFlush,
                Codec.BOOL,
                Codec.BOOL)
                .description("Set weather flush should be used for backups")
                .parameter(new RpcRequestParameter("enabled",RpcSchema.BOOLEAN))
                .result(new RpcResponseResult("enabled",RpcSchema.BOOLEAN))
                .build();
        Registry.register(Registries.INCOMING_RPC_METHOD, Identifier.of(MODID,"flush/set"),setFlushCommand);

        IncomingRpcMethod.Parameterless<Boolean> getEnabledCommand = IncomingRpcMethod.createParameterlessBuilder(BackupRpcDispatcher::getEnabled,
                        Codec.BOOL)
                .description("Get weather auto backups are currently enabled")
                .result(new RpcResponseResult("enabled",RpcSchema.BOOLEAN))
                .build();
        Registry.register(Registries.INCOMING_RPC_METHOD, Identifier.of(MODID,"enabled"),getEnabledCommand);

        IncomingRpcMethod.Parameterized<Boolean,Boolean> setEnabledCommand = IncomingRpcMethod.createParameterizedBuilder(BackupRpcDispatcher::setEnabled,
                        Codec.BOOL,
                        Codec.BOOL)
                .description("Set weather automatic backups should be made")
                .parameter(new RpcRequestParameter("enabled",RpcSchema.BOOLEAN))
                .result(new RpcResponseResult("enabled",RpcSchema.BOOLEAN))
                .build();
        Registry.register(Registries.INCOMING_RPC_METHOD, Identifier.of(MODID,"enabled/set"),setEnabledCommand);

        IncomingRpcMethod.Parameterless<List<String>> getCompressionTypesCommand = IncomingRpcMethod.createParameterlessBuilder(BackupRpcDispatcher::getCompressionTypes,
                Codec.STRING.listOf())
                .description("Get the available compression types")
                .result(new RpcResponseResult("compressionTypes",RpcSchema.ofArray(RpcSchema.STRING)))
                .build();
        Registry.register(Registries.INCOMING_RPC_METHOD,Identifier.of(MODID,"compression_types"),getCompressionTypesCommand);




        BackupRpcDispatcher.register();
    }//end of on initialize

    static MinecraftServer ms;
    static PlayerManager pm;

    static  void sendChatMessage(String message){
        MutableText chatMessage=MutableText.of(new Literal(message));
        pm.broadcast(chatMessage, false);
    }

    @SuppressWarnings("all")
    static  void sendChatErrorMessage(String message){
        MutableText chatMessage=MutableText.of(new Literal(message));
        chatMessage.withColor(0xFFFF0000);//red
        pm.broadcast(chatMessage, false);
    }

    static void backup(String cause,CompressionType compression, boolean flush){
        if(!cause.isEmpty())
            sendChatMessage("server backup started ("+cause+")");
        else
            sendChatMessage("server backup started");

        sendManagementNotification(BackupRpcDispatcher.BACKUP_STARTED);

        disableAutoSave();
        if(flush) {
            LOGGER.info("if the server freezes for too long then disable flush");
        }
        ms.saveAll(true, flush, true);
        Date date = new Date();
        SimpleDateFormat formatter1 = new SimpleDateFormat("yy/MM/dd"),formatter2=new SimpleDateFormat("ddMMyy");
        String dateFolder = formatter1.format(date),folderName=formatter2.format(date)+"-"+System.currentTimeMillis();
        //noinspection ResultOfMethodCallIgnored
        new File(config.getBackupDestinationFolder()+"/"+dateFolder).mkdirs();
        //NOTE: you may get the following error here while testing in the IDE, I have no idea why it happens and the exported jar works just fine
        //java.lang.NoClassDefFoundError: org/apache/commons/compress/compressors/lzma/LZMACompressorOutputStream
        Backup backup=new Backup(worldFolder, config.getBackupDestinationFolder()+"/"+dateFolder+"/"+folderName,compression);
        backup.start();
        //System.out.println("end of backup function");
    }

    static void disableAutoSave(){

        for (ServerWorld serverWorld : ms.getWorlds()) {
            if (serverWorld.savingDisabled)
                savingWasDisabled = true;

            if (!serverWorld.savingDisabled) {
                serverWorld.savingDisabled = true;
                savingWasDisabled = false;
            }

        }
    }
    static void enableAutoSave(){

        for (ServerWorld serverWorld : ms.getWorlds()) {
            if (serverWorld != null && serverWorld.savingDisabled && !savingWasDisabled) {
                serverWorld.savingDisabled = false;
            }

        }
    }


    @Override
    public void onEndTick(MinecraftServer server) {
        if(config.isEnabled()) {
            long timeLeft = nextBackupTime - curMillisTime();
            if (timeLeft < 10000 && !tenSeccondWarning) {
                sendChatMessage("server backup starting in 10 seconds..");
                tenSeccondWarning = true;
            }
            if (timeLeft < 0) {
                tenSeccondWarning = false;
                nextBackupTime = (long) (curMillisTime() + 3600000 * config.getHoursBetweenBackups());
                backup("", config.getCompressionType(),config.getFlush());
                //System.out.println("end of backup tick");
            }
        }
    }

    static long curMillisTime(){
        return System.nanoTime()/1000000;
    }

    public static void sendManagementNotification(RegistryEntry.Reference<? extends OutgoingRpcMethod<Void, ?>> method){
        if(ms instanceof MinecraftDedicatedServer dms){
            CompositeManagementListener listener = dms.getManagementListener();

            List<ManagementListener> managementListeners = ((CompositeManagementListenerAccessor)listener).getListeners();

            managementListeners.forEach( (managementListener -> {
                NotificationManagementListenerAccessor notificationManagementListener = (NotificationManagementListenerAccessor) managementListener;
                notificationManagementListener.callNotifyAll(method);
            }));
        }
    }

    public static <Params> void sendManagementNotification(RegistryEntry.Reference<? extends OutgoingRpcMethod<Params, ?>> method, Params params){
        if(ms instanceof MinecraftDedicatedServer dms){
            CompositeManagementListener listener = dms.getManagementListener();

            List<ManagementListener> managementListeners = ((CompositeManagementListenerAccessor)listener).getListeners();

            managementListeners.forEach( (managementListener -> {
                NotificationManagementListenerAccessor2 notificationManagementListener = (NotificationManagementListenerAccessor2) managementListener;
                notificationManagementListener.callNotifyAll(method,params);
            }));
        }
    }


}
