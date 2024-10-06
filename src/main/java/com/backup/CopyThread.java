package com.backup;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
/**the thread class responsible for actually copying the files
 * 
 * @author jSdCool
 *
 */
public class CopyThread extends Thread {
	static int numOfinstances = 0;
	int threadNumber;

	CopyThread() {
		super("copying Thread " + (numOfinstances + 1));
		threadNumber = numOfinstances + 1;
		numOfinstances++;
	}

	ArrayList<String> toCopy = new ArrayList<>();
	boolean shouldRun = true, working = false, endReaddy = false;

	@SuppressWarnings("ResultOfMethodCallIgnored")
	public void run() {
		while (shouldRun) {
			Math.random();//prevent this thread from being put to sleep for being "inactive"
			if (!toCopy.isEmpty()) {

				copyFile(0);
				Backup.completed++;//increase the number of copies completed
				//double precent = ((int) ((Backup.completed * 0.1 / Backup.total) * 10000)) / 10.0;//calculate the completion percent
				toCopy.removeFirst();


			} else {
				if (endReaddy)//if there are not more files that need to be coppied then kill the thread
					return;
			}
			if (working && toCopy.isEmpty())
				working = false;
		}

	}

	@SuppressWarnings("ResultOfMethodCallIgnored")
	void copyFile(int numOfTries) {
		if(numOfTries>50) {
			Main.LOGGER.error("failed to copy file " + toCopy.getFirst());
			return;
		}
		try {
			String[] newDir = (Backup.destination + "/" + toCopy.getFirst()).split("\\\\|/");
			StringBuilder destDir = new StringBuilder();
			for (int i = 0; i < newDir.length - 1; i++) {//get the path to the current file
				destDir.append(newDir[i]).append("/");
			}
			new File(destDir.toString()).mkdirs();//make the parent folder if it doesn't exist
			File dest = new File(Backup.destination + "/" + toCopy.getFirst());
			if (dest.exists()) {//if the file already exists in the new location then delete the current version
				dest.delete();
			}
			java.nio.file.Files.copy(new File(Backup.source + "/" + toCopy.getFirst()).toPath(), dest.toPath());//copy the file `
		} catch (IOException e) {//if it fails
			copyFile(numOfTries+1);
		}
	}


}
