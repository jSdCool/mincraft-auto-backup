package com.backup;

import java.io.File;
import java.util.ArrayList;

public class Backup extends Thread{
    static ArrayList<String> fileIndex=new ArrayList<>(),stackTraces=new ArrayList<>();
    static String source,destination;
    static int completed=0,total=0,numOfThreads=4,batchSize=10;
    static ArrayList<CopyThread> threads=new ArrayList<>();

    Backup(String worldFolder,String dest){
        super("backup controller thread");
        completed=0;
        total=0;
        source=worldFolder;
        destination=dest;
        threads=new ArrayList<>();
    }


    public void run(){
        long programStart = System.nanoTime();//note the program start time
        //Main.sendChatMessage("starting backup");
        scanForFiles(source,"");//Discover all the file that need to be copied
        total=fileIndex.size();//note how many file there are
        for(int i=0;i<numOfThreads;i++) {//create all the requested threads
            threads.add(new CopyThread());
            threads.get(i).start();
        }
        long copyStart = System.nanoTime();//note the time that copying starts
        while(fileIndex.size()>0) {//while there are still more unassigned files that need to be copied
            for(int i=0;i<threads.size();i++) {//check all the threads
                if(!threads.get(i).isAlive()) {//restart the thread if it died
                    threads.set(i, new CopyThread());
                    threads.get(i).start();
                }
                if(!threads.get(i).working) {//if the thread needs more work to do then give it more work
                    threads.get(i).toCopy=createNextJob();
                    threads.get(i).working=true;
                }
            }
        }
        for(int i=0;i<threads.size();i++) {//tell all threads that there will be no more work once they finish
            threads.get(i).endReaddy=true;
        }
        while(threadsRunning()) {//wait for all the threads to finish copying files
        Math.random();//prevent the thread from being put to sleep for being inactive
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
        for(int i=0;i<files.length;i++) {//loop through all the things in the current folder

            if(new File(parentPath+"/"+subPath+"/"+files[i]).list()!=null) {//check weather the current thing is a folder or a file
                scanForFiles(parentPath,subPath+"/"+files[i]);//if it is a folder then scan through that folder for more files

            }else {//if it is a file
                //add this file to the to copy index
                if(subPath.equals("")) {
                    fileIndex.add(files[i]);
                }else {
                    fileIndex.add(subPath+"/"+files[i]);
                }
            }
        }

    }

    /**gets a list of files that need to be copied to send to a thread
     *
     * @return an array list of file paths that need to be copied
     */
    static ArrayList<String> createNextJob(){
        ArrayList<String> batch=new ArrayList<>();
        for(int i=0;i<batchSize&&fileIndex.size()>0;i++) {//use the batch size to determine the number of items to send to each thread
            batch.add(fileIndex.remove(0));
        }

        return batch;

    }

    /**
     *
     * @return weather any thread is sill running
     */
    static boolean threadsRunning() {
        for(int i=0;i<threads.size();i++) {
            if(threads.get(i).isAlive())
                return true;
        }
        return false;
    }
}
