package com.backup;

import net.jpountz.lz4.LZ4BlockOutputStream;
import net.jpountz.lz4.LZ4FrameOutputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.lzma.LZMACompressorOutputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorOutputStream;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Backup extends Thread{
    static ArrayList<String> fileIndex=new ArrayList<>();
    static String source,destination;
    static int completed=0,total=0,numOfThreads=4,batchSize=10;
    static ArrayList<CopyThread> threads=new ArrayList<>();
    boolean success =true;

    CompressionType compressionType;

    Backup(String worldFolder,String dest,CompressionType compression){
        super("backup controller thread");
        completed=0;
        total=0;
        source=worldFolder;
        destination=dest;
        threads=new ArrayList<>();
        compressionType = compression;
    }


    public void run(){
        //System.out.println("backup thread started");
        long programStart = System.nanoTime();//note the program start time
        //Main.sendChatMessage("starting backup");
        scanForFiles(source,"");//Discover all the file that need to be copied
        total=fileIndex.size();//note how many file there are


        //System.out.println("indexing started");

        //System.out.println("indexing finished starting copy");
        long copyStart = System.nanoTime();//note the time that copying starts

        switch(compressionType){
            case NONE -> {
                //if using no compression. use the old simple file copying method

                for(int i=0;i<numOfThreads;i++) {//create all the requested threads
                    threads.add(new CopyThread());
                    threads.get(i).start();
                }

                backupNoCompression();
            }
            case ZIP -> //if using ZIP compression. use the zip backup method
                    backupZipCompression();

            case GZIP -> //if using GZIP compression
                    backupGZipCompression();
            case LZ4 -> backupLZ4Compression();
            case XZ -> backupXzCompression();
            case LZMA -> backupLzmaCompression();
        }
        if(!success){
            Main.sendChatErrorMessage("Backup Failed! see server logs for mor details");
            Main.enableAutoSave();
            return;
        }

        long programEndTime=System.nanoTime();//note the time at witch the copying finished
        long totalTime=(programEndTime-programStart)/1000000,indexTime=(copyStart-programStart)/1000000,copyTime=(programEndTime-copyStart)/1000000;//calculates the time things took
        Main.sendChatMessage("backup completed in " +totalTime+"ms");
        Main.LOGGER.info("backup completed in: "+totalTime+"ms index time: "+indexTime+"ms copy time: "+copyTime+"ms");
        Main.enableAutoSave();
    }

    /**Recursively scan folders for files to copy
     *
     * @param parentPath the root path of the folder that is being copied
     * @param subPath the path of the current sub folder that is being looked through
     */
    public static void scanForFiles(String parentPath,String subPath) {
        String[] files=new File(parentPath+"/"+subPath).list();//get a list of all things in the current folder
        for (String file : files) {//loop through all the things in the current folder

            if (new File(parentPath + "/" + subPath + "/" + file).list() != null) {//check weather the current thing is a folder or a file
                scanForFiles(parentPath, subPath + "/" + file);//if it is a folder then scan through that folder for more files

            } else {//if it is a file
                //add this file to the to copy index
                if (subPath.isEmpty()) {
                    fileIndex.add(file);
                } else {
                    fileIndex.add(subPath + "/" + file);
                }
            }
        }

    }

    private void backupLzmaCompression(){
        try(OutputStream output = new LZMACompressorOutputStream(new FileOutputStream(destination+".tar.lzma"))){
            backupWithTarBasedOutput(output,true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


    private void backupXzCompression(){//565,390
        try(OutputStream output = new XZCompressorOutputStream(new FileOutputStream(destination+".tar.xz"))){
            backupWithTarBasedOutput(output,true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    private void backupLZ4Compression(){
        try(OutputStream output = new LZ4FrameOutputStream(new FileOutputStream(destination+".tar.lz4"))){
            backupWithTarBasedOutput(output,false);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** backup the world by compressing its files into a tar.gz file
     */
    private void backupGZipCompression(){

        try(OutputStream output = new GZIPOutputStream(new FileOutputStream(destination+".tar.gz"))) {
            backupWithTarBasedOutput(output,false);
        } catch (IOException e) {
            Main.LOGGER.error("Exception while attempting to create the backup output stream",e);
            success=false;
        }

    }

    private void backupWithTarBasedOutput(OutputStream out,boolean print){
        String currFile = "";
        try{
            TarArchiveOutputStream output = new TarArchiveOutputStream(out);
            for(String file :fileIndex){
                currFile = file;
                if(file.equals("session.lock")){
                    continue;
                }
                String entryName = file;
                if(entryName.startsWith("/")){
                    entryName = entryName.substring(1);
                }
                TarArchiveEntry te = new TarArchiveEntry(new File(source+"/"+file),entryName);
                output.putArchiveEntry(te);
                if(print)
                    Main.LOGGER.info("Compressing: "+file);
                Files.copy(Path.of(source+"/"+file),output);
                output.closeArchiveEntry();
            }
        }catch (IOException e){
            Main.LOGGER.error("Exception while compressing world! last attempted file: "+currFile,e);
            success=false;
        }
    }

    /** backup the world by compressing its files into a zip file
     */
    private void backupZipCompression(){
        //System.out.println("sourceFolder: "+source);
        //System.out.println("dest folder: "+destination);
        String currFile = "";
        try(ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(destination+".zip"))){

            for(String file :fileIndex){
                currFile = file;
                //locked file is not good for backing up
                //explicitly don nothing if encountering this file
                if(file.equals("session.lock")){
                    continue;
                }
                String entryName = file;
                //remove the leading / in case of a folder so the compressed folder shows up correctly in the resultant file
                if(entryName.startsWith("/")){
                    entryName = entryName.substring(1);
                }

                //create the next file entry
                ZipEntry ze = new ZipEntry(entryName);
                //add it ti the archive
                zipOut.putNextEntry(ze);
                //read the file content into the archive

                FileInputStream contentIn = new FileInputStream(source+"/"+file);
                byte []buffer = new byte[1024];
                int len;
                while((len = contentIn.read(buffer)) >0){
                    zipOut.write(buffer,0,len);
                }
                //end the data for this entry
                zipOut.closeEntry();
                //close the input stream
                contentIn.close();

            }
        } catch (IOException e) {
            Main.LOGGER.error("Exception while compressing world! last attempted file: "+currFile,e);
            success=false;
        }
    }

    /**back up the world by simply copying all of its files to a new location
     */
    private static void backupNoCompression() {
        while(!fileIndex.isEmpty()) {//while there are still more unassigned files that need to be copied
            for(int i=0;i<threads.size();i++) {//check all the threads
                if(!threads.get(i).isAlive()) {//restart the thread if it died
                    threads.set(i, new CopyThread());
                    threads.get(i).start();
                }
                if(!threads.get(i).working) {//if the thread needs more work to do than give it more work
                    threads.get(i).toCopy=createNextJob();
                    threads.get(i).working=true;
                }
            }
        }
        for (CopyThread thread : threads) {//tell all threads that there will be no more work once they finish
            thread.endReaddy = true;
        }
        while(threadsRunning()) {//wait for all the threads to finish copying files
            Math.random();//prevent the thread from being put to sleep for being inactive
        }
    }

    /**gets a list of files that need to be copied to send to a thread
     *
     * @return an array list of file paths that need to be copied
     */
    static ArrayList<String> createNextJob(){
        ArrayList<String> batch=new ArrayList<>();
        for(int i = 0; i<batchSize&& !fileIndex.isEmpty(); i++) {//use the batch size to determine the number of items to send to each thread
            batch.add(fileIndex.removeFirst());
        }

        return batch;

    }

    /**
     *
     * @return weather any thread is still running
     */
    static boolean threadsRunning() {
        for (CopyThread thread : threads) {
            if (thread.isAlive())
                return true;
        }
        return false;
    }
}
