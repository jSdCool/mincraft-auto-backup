package com.backup;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.command.argument.ArgumentTypes;
import net.minecraft.command.argument.EnumArgumentType;
import net.minecraft.command.argument.serialize.ConstantArgumentSerializer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.PlainTextContent.Literal;
import net.minecraft.text.MutableText;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static net.minecraft.server.command.CommandManager.literal;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;

public class Main implements ModInitializer, ServerTickEvents.EndTick {
    // This logger is used to write text to the console and the log file.
    // It is considered best practice to use your mod id as the logger's name.
    // That way, it's clear which mod wrote info, warnings, and errors.
    public static final Logger LOGGER = LoggerFactory.getLogger("backup");
    static String worldFolder="",destinationFolder="";
    static boolean savingWasDisabled=false,tenSeccondWarning=false;
    static long nextBackupTime;
    static float timeBetweenBackups;
    static boolean flush=false,enabled=true;

    static CompressionType compressionType;

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
            enabled=false;
            //don't do anything else
            return;
        }

        ArgumentTypeRegistry.registerArgumentType(
                Identifier.of("backup","compression_type"),
                CompressionArgumentType.class,
                ConstantArgumentSerializer.of(CompressionArgumentType::new)
        );

        CommandRegistrationCallback.EVENT.register((dispatcher, commandRegistryAccess, registrationEnvironment) ->
                dispatcher.register(literal("backup").requires(source -> source.hasPermissionLevel(3)).executes(context -> {
            backup("manual",compressionType);
            return 1;
        })
                .then(literal("enable").executes(context -> {
                    enabled=true;
                            context.getSource().sendFeedback(()->MutableText.of(new Literal("auto backups enabled")),true);
                    return 1;
                }))
                .then(literal("disable").executes(context -> {
                    enabled=false;
                    context.getSource().sendFeedback(()->MutableText.of(new Literal("auto backups disabled")),true);
                    return 1;
                }))
                .then(literal("enable_flush").executes(context -> {
                    flush=true;
                    try {
                        FileWriter mr = new FileWriter("config/backup.cfg");
                        mr.write("backup destination folder=" + destinationFolder + "\nhours between backups=" + timeBetweenBackups + "\nflush=true");
                        mr.close();
                    } catch (IOException i){
                        context.getSource().sendError(MutableText.of(new Literal("IOException see server logs for more info")));
                        LOGGER.error("Exception while attempting to wright to config file! ",i);
                        return 0;
                    }
                    context.getSource().sendFeedback(()->MutableText.of(new Literal("save flushing enabled")),true);
                    return 1;
                }))
                .then(literal("disable_flush").executes(context -> {
                    flush=false;
                    try {
                        FileWriter mr = new FileWriter("config/backup.cfg");
                        mr.write("backup destination folder=" + destinationFolder + "\nhours between backups=" + timeBetweenBackups + "\nflush=false");
                        mr.close();
                    } catch (IOException i){
                        context.getSource().sendError(MutableText.of(new Literal("IOException see server logs for more info")));
                        LOGGER.error("Exception while attempting to wright to config file! ",i);
                        return 0;
                    }
                    context.getSource().sendFeedback(()->MutableText.of(new Literal("save flushing disabled")),true);
                    return 1;
                }))
                .then(literal("using").then(CommandManager.argument("compression", new CompressionArgumentType()))).executes(context -> {
                    CompressionType compression = context.getArgument("compression",CompressionType.class);
                    backup("manual",compression);
                    return 1;
                })
        ));

        File config;
        Scanner cfs;
        try {
            config = new File("config/backup.cfg");
            cfs = new Scanner(config);
        } catch (Throwable e) {
            try {
                //noinspection ResultOfMethodCallIgnored
                new File("config").mkdir();
                FileWriter mr = new FileWriter("config/backup.cfg");
                mr.write("backup destination folder=\nhours between backups=6\nflush=true");
                mr.close();
                System.out.println("config file created.");

            } catch (IOException ee) {
                System.out.println("\n\n\nAn error occurred while creating config file. please try again\n\n\n");
                LOGGER.error("IOException",ee);

                throw new RuntimeException("could not create config file");
            }
            System.out.println("\n\n\nconfig file created. populate the fields and then restart this server.\n\n\n");
            throw new RuntimeException("config file created, please fill out the config file");
        }
        boolean hasFlush=false;
        boolean hasCompressionType =false;
        while(cfs.hasNextLine()) {
            String cfgLine=cfs.nextLine();
            if(cfgLine.startsWith("backup destination folder=")){
                destinationFolder=cfgLine.substring(26);
                LOGGER.info("backup destination set to: "+destinationFolder);
                continue;
            }
            if(cfgLine.startsWith("hours between backups=")){
                timeBetweenBackups=Float.parseFloat(cfgLine.substring(22));
                nextBackupTime=(long)(curMillisTime()+3600000*timeBetweenBackups);
                LOGGER.info("time between backups set to: "+timeBetweenBackups+" hours");
                continue;
            }
            if(cfgLine.startsWith("flush=")) {
                flush = cfgLine.substring("flush=".length()).equals("true") || cfgLine.substring("flush=".length()).equals("True") || cfgLine.substring("flush=".length()).equals("TRUE");
                hasFlush = true;
                LOGGER.info("flush set to: "+flush);
            }
            if(cfgLine.startsWith("compression=")){
                hasCompressionType=true;
                String typeString  = cfgLine.substring("compression=".length());
                typeString = typeString.trim();
                typeString = typeString.toLowerCase();
                switch (typeString) {
                    case "zip" -> compressionType = CompressionType.ZIP;
                    case "gzip" -> compressionType = CompressionType.GZIP;
                    case "lz4" -> compressionType = CompressionType.LZ4;
                    case "xz" -> compressionType = CompressionType.XZ;
                    case "lzma" -> compressionType = CompressionType.LZMA;
                    default -> compressionType = CompressionType.NONE;
                }
            }

        }
        if(!hasFlush){
            try {
                FileWriter mr = new FileWriter("config/backup.cfg");
                mr.write("backup destination folder=" + destinationFolder + "\nhours between backups=" + timeBetweenBackups + "\nflush=true\ncompression=NONE");
                mr.close();
            } catch (IOException ignored){

            }
        }
        if(!hasCompressionType){
            compressionType = CompressionType.NONE;
            try {
                FileWriter mr = new FileWriter("config/backup.cfg");
                mr.write("backup destination folder=" + destinationFolder + "\nhours between backups=" + timeBetweenBackups + "\nflush="+flush+"\ncompression=NONE");
                mr.close();
            } catch (IOException ignored){

            }
        }
        cfs.close();

        ServerTickEvents.END_SERVER_TICK.register( this);
    }//end of on initialize

    static MinecraftServer ms;
    static PlayerManager pm;

    static  void sendChatMessage(String message){
        MutableText chatMessage=MutableText.of(new Literal(message));
        pm.broadcast(chatMessage, false);
    }

    static  void sendChatErrorMessage(String message){
        MutableText chatMessage=MutableText.of(new Literal(message));
        chatMessage.withColor(0xFFFF0000);//red
        pm.broadcast(chatMessage, false);
    }

    static void backup(String cause,CompressionType compression){
        if(!cause.isEmpty())
            sendChatMessage("server backup started ("+cause+")");
        else
            sendChatMessage("server backup started");
        disableAutoSave();
        if(flush) {
            LOGGER.info("if the server freezes for too long then disable flush");
        }
        ms.saveAll(true, flush, true);
        Date date = new Date();
        SimpleDateFormat formatter1 = new SimpleDateFormat("yy/MM/dd"),formatter2=new SimpleDateFormat("ddMMyy");
        String dateFolder = formatter1.format(date),folderName=formatter2.format(date)+"-"+System.currentTimeMillis();
        Backup backup=new Backup(worldFolder,destinationFolder+"/"+dateFolder+"/"+folderName,compression);
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
        long timeLeft=nextBackupTime-curMillisTime();
        if(timeLeft<10000&&!tenSeccondWarning){
            sendChatMessage("server backup starting in 10 seconds..");
            tenSeccondWarning=true;
        }
        if(timeLeft<0){
            tenSeccondWarning=false;
            nextBackupTime=(long)(curMillisTime()+3600000*timeBetweenBackups);
            backup("",compressionType);
            //System.out.println("end of backup tick");
        }
    }

    long curMillisTime(){
        return System.nanoTime()/1000000;
    }

}
