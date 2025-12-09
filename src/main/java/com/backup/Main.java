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
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.PlainTextContents.LiteralContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.dedicated.DedicatedServer;
import net.minecraft.server.jsonrpc.IncomingRpcMethod;
import net.minecraft.server.jsonrpc.OutgoingRpcMethod;
import net.minecraft.server.jsonrpc.api.ParamInfo;
import net.minecraft.server.jsonrpc.api.ResultInfo;
import net.minecraft.server.jsonrpc.api.Schema;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.notifications.NotificationManager;
import net.minecraft.server.notifications.NotificationService;
import net.minecraft.server.players.PlayerList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static net.minecraft.commands.Commands.literal;

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
            pm = ms.getPlayerList();

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
        ArgumentBuilder<CommandSourceStack, ?> usingOptions = literal("using");
        for(CompressionType type :CompressionType.values()){
            usingOptions=usingOptions.then(literal(type.getSerializedName()).executes(context -> {
                backup("manual",type,config.getFlush());
                return 1;
            }));
        }

        final ArgumentBuilder<CommandSourceStack, ?> finalUsingOptions = usingOptions;

        //noinspection CodeBlock2Expr
        CommandRegistrationCallback.EVENT.register((dispatcher, commandRegistryAccess, registrationEnvironment) -> {
                dispatcher.register(literal("backup").requires(source -> source.hasPermission(3)).executes(context -> {
            backup("manual",config.getCompressionType(),config.getFlush());
            return 1;
        })
                .then(literal("enable").executes(context -> {
                    config.setEnabled(true);
                    context.getSource().sendSuccess(()->MutableComponent.create(new LiteralContents("auto backups enabled")),true);
                    return 1;
                }))
                .then(literal("disable").executes(context -> {
                    config.setEnabled(false);
                    context.getSource().sendSuccess(()->MutableComponent.create(new LiteralContents("auto backups disabled")),true);
                    return 1;
                }))
                .then(literal("enable_flush").executes(context -> {
                    config.setFlush(true);
                    context.getSource().sendSuccess(()->MutableComponent.create(new LiteralContents("save flushing enabled")),true);
                    return 1;
                }))
                .then(literal("disable_flush").executes(context -> {
                    config.setFlush(false);
                    context.getSource().sendSuccess(()->MutableComponent.create(new LiteralContents("save flushing disabled")),true);
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

        IncomingRpcMethod.ParameterlessMethod<Boolean> rawBackupCommand = IncomingRpcMethod.method(BackupRpcDispatcher::run,
                Codec.BOOL)
                .description("Make a new backup with the configured settings")
                .response(new ResultInfo("result",Schema.BOOL_SCHEMA))
                .build();
        Registry.register(BuiltInRegistries.INCOMING_RPC_METHOD, ResourceLocation.fromNamespaceAndPath(MODID, "run"),rawBackupCommand);


        IncomingRpcMethod.Method<BackupRpcDispatcher.IncomingRpcRunInfo,String> paramBackupCommand = IncomingRpcMethod.method(BackupRpcDispatcher::runUsing,
                        BackupRpcDispatcher.IncomingRpcRunInfo.CODEC.codec(),
                        Codec.STRING)
                .description("Make a new backup with the provided settings")
                .param(new ParamInfo("using",BackupRpcDispatcher.USING_SCHEMA))
                .response(new ResultInfo("result",Schema.STRING_SCHEMA))
                .build();
        Registry.register(BuiltInRegistries.INCOMING_RPC_METHOD, ResourceLocation.fromNamespaceAndPath(MODID, "run/using"),paramBackupCommand);

        IncomingRpcMethod.ParameterlessMethod<Boolean> getFlushCommand = IncomingRpcMethod.method(BackupRpcDispatcher::getFlush,
                Codec.BOOL)
                .description("Get weather flush is currently enabled for backups")
                .response(new ResultInfo("enabled",Schema.BOOL_SCHEMA))
                .build();
        Registry.register(BuiltInRegistries.INCOMING_RPC_METHOD, ResourceLocation.fromNamespaceAndPath(MODID,"flush"),getFlushCommand);

        IncomingRpcMethod.Method<Boolean,Boolean> setFlushCommand = IncomingRpcMethod.method(BackupRpcDispatcher::setFlush,
                Codec.BOOL,
                Codec.BOOL)
                .description("Set weather flush should be used for backups")
                .param(new ParamInfo("enabled",Schema.BOOL_SCHEMA))
                .response(new ResultInfo("enabled",Schema.BOOL_SCHEMA))
                .build();
        Registry.register(BuiltInRegistries.INCOMING_RPC_METHOD, ResourceLocation.fromNamespaceAndPath(MODID,"flush/set"),setFlushCommand);

        IncomingRpcMethod.ParameterlessMethod<Boolean> getEnabledCommand = IncomingRpcMethod.method(BackupRpcDispatcher::getEnabled,
                        Codec.BOOL)
                .description("Get weather auto backups are currently enabled")
                .response(new ResultInfo("enabled",Schema.BOOL_SCHEMA))
                .build();
        Registry.register(BuiltInRegistries.INCOMING_RPC_METHOD, ResourceLocation.fromNamespaceAndPath(MODID,"enabled"),getEnabledCommand);

        IncomingRpcMethod.Method<Boolean,Boolean> setEnabledCommand = IncomingRpcMethod.method(BackupRpcDispatcher::setEnabled,
                        Codec.BOOL,
                        Codec.BOOL)
                .description("Set weather automatic backups should be made")
                .param(new ParamInfo("enabled",Schema.BOOL_SCHEMA))
                .response(new ResultInfo("enabled",Schema.BOOL_SCHEMA))
                .build();
        Registry.register(BuiltInRegistries.INCOMING_RPC_METHOD, ResourceLocation.fromNamespaceAndPath(MODID,"enabled/set"),setEnabledCommand);

        IncomingRpcMethod.ParameterlessMethod<List<String>> getCompressionTypesCommand = IncomingRpcMethod.method(BackupRpcDispatcher::getCompressionTypes,
                Codec.STRING.listOf())
                .description("Get the available compression types")
                .response(new ResultInfo("compressionTypes",Schema.arrayOf(Schema.STRING_SCHEMA)))
                .build();
        Registry.register(BuiltInRegistries.INCOMING_RPC_METHOD,ResourceLocation.fromNamespaceAndPath(MODID,"compression_types"),getCompressionTypesCommand);




        BackupRpcDispatcher.register();
    }//end of on initialize

    static MinecraftServer ms;
    static PlayerList pm;

    static  void sendChatMessage(String message){
        MutableComponent chatMessage=MutableComponent.create(new LiteralContents(message));
        pm.broadcastSystemMessage(chatMessage, false);
    }

    @SuppressWarnings("all")
    static  void sendChatErrorMessage(String message){
        MutableComponent chatMessage=MutableComponent.create(new LiteralContents(message));
        chatMessage.withColor(0xFFFF0000);//red
        pm.broadcastSystemMessage(chatMessage, false);
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
        ms.saveEverything(true, flush, true);
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

        for (ServerLevel serverWorld : ms.getAllLevels()) {
            if (serverWorld.noSave)
                savingWasDisabled = true;

            if (!serverWorld.noSave) {
                serverWorld.noSave = true;
                savingWasDisabled = false;
            }

        }
    }
    static void enableAutoSave(){

        for (ServerLevel serverWorld : ms.getAllLevels()) {
            if (serverWorld != null && serverWorld.noSave && !savingWasDisabled) {
                serverWorld.noSave = false;
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

    public static void sendManagementNotification(Holder.Reference<? extends OutgoingRpcMethod<Void, ?>> method){
        if(ms instanceof DedicatedServer dms){
            NotificationManager listener = dms.notificationManager();

            List<NotificationService> managementListeners = ((CompositeManagementListenerAccessor)listener).getNotificationServices();

            managementListeners.forEach( (managementListener -> {
                NotificationManagementListenerAccessor notificationManagementListener = (NotificationManagementListenerAccessor) managementListener;
                notificationManagementListener.callNotifyAll(method);
            }));
        }
    }

    public static <Params> void sendManagementNotification(Holder.Reference<? extends OutgoingRpcMethod<Params, ?>> method, Params params){
        if(ms instanceof DedicatedServer dms){
            NotificationManager listener = dms.notificationManager();

            List<NotificationService> managementListeners = ((CompositeManagementListenerAccessor)listener).getNotificationServices();

            managementListeners.forEach( (managementListener -> {
                NotificationManagementListenerAccessor2 notificationManagementListener = (NotificationManagementListenerAccessor2) managementListener;
                notificationManagementListener.callNotifyAll(method,params);
            }));
        }
    }


}
