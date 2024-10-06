package com.backup;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;

public class Config {

    private String backupDestinationFolder;
    private float hoursBetweenBackups;
    private boolean flush,enabled;
    private CompressionType compressionType;

    Config(){
        backupDestinationFolder = "";
        hoursBetweenBackups = 6;
        flush = true;
        enabled = true;
        compressionType = CompressionType.NONE;
        reloadConfig();
    }

    public void reloadConfig(){
        File config = new File("config/backup.cfg");
        try( Scanner cfs = new Scanner(config)){
            boolean[] hasComponents = new boolean[5];
            while (cfs.hasNextLine()){
                String cfgLine = cfs.nextLine();
                if(cfgLine.startsWith("backup destination folder=")){
                    backupDestinationFolder = cfgLine.substring(26);
                    hasComponents[0]=true;
                    Main.LOGGER.info("backup destination set to: "+backupDestinationFolder);
                }else if(cfgLine.startsWith("hours between backups=")){
                    hoursBetweenBackups = Float.parseFloat(cfgLine.substring(22));
                    Main.LOGGER.info("time between backups set to: "+hoursBetweenBackups+" hours");
                    hasComponents[1]=true;
                    Main.nextBackupTime=(long)(Main.curMillisTime()+3600000*hoursBetweenBackups);
                }else if(cfgLine.startsWith("flush=")){
                    flush =  cfgLine.substring("flush=".length()).equals("true") || cfgLine.substring("flush=".length()).equals("True") || cfgLine.substring("flush=".length()).equals("TRUE");
                    hasComponents[2]=true;
                }else if(cfgLine.startsWith("compression=")){
                    String typeString  = cfgLine.substring("compression=".length());
                    typeString = typeString.trim();
                    typeString = typeString.toUpperCase();
                    for(CompressionType type : CompressionType.values()){
                        if(type.toString().equals(typeString)){
                            compressionType = type;
                            hasComponents[3] = true;
                            break;
                        }
                    }
                } else if (cfgLine.startsWith("enabled=")) {
                    String enabledLine = cfgLine.substring("enabled=".length());
                    enabled = enabledLine.equals("true") || enabledLine.equals("True") || enabledLine.equals("TRUE");
                    hasComponents[4]=true;
                }
            }

            //check if all configs were found in the file
            for(boolean b: hasComponents){
                if(!b){
                    //if not then save the file to create all the required configs
                    Main.LOGGER.warn("1 or more config components were not present, please check the config file to configure them!");
                    save();
                    break;
                }
            }

        }catch (IOException e){
            Main.LOGGER.error("error while loading config file",e);
            enabled=false;
            save();
        }
    }

    private void save(){
        try( FileWriter mr = new FileWriter("config/backup.cfg")){
            ArrayList<String> configLines = new ArrayList<>();
            configLines.add("backup destination folder=" + backupDestinationFolder);
            configLines.add("hours between backups=" + hoursBetweenBackups);
            configLines.add("flush="+flush);
            configLines.add("compression="+compressionType.toString());
            configLines.add("enabled="+enabled);

            String configContent = String.join("\n",configLines.toArray(new String[]{}));

            mr.write(configContent);

            Main.LOGGER.info("Config File Updated");
        } catch (IOException e) {
           Main.LOGGER.error("An error occurred while righting config file",e);
        }
    }

    public CompressionType getCompressionType() {
        return compressionType;
    }

    public float getHoursBetweenBackups() {
        return hoursBetweenBackups;
    }

    public String getBackupDestinationFolder() {
        return backupDestinationFolder;
    }

    public boolean getFlush() {
        return flush;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setBackupDestinationFolder(String backupDestinationFolder) {
        this.backupDestinationFolder = backupDestinationFolder;
        save();
    }

    public void setCompressionType(CompressionType compressionType) {
        this.compressionType = compressionType;
        save();
    }

    public void setFlush(boolean flush) {
        this.flush = flush;
        save();
    }

    public void setHoursBetweenBackups(float hoursBetweenBackups) {
        this.hoursBetweenBackups = hoursBetweenBackups;
        save();
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        save();
    }
}
