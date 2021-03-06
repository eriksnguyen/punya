package com.google.appinventor.components.runtime;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.appinventor.components.runtime.util.ErrorMessages;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
import android.os.Binder;
import android.os.IBinder;
import android.os.PowerManager.WakeLock;
import android.util.Log;


import edu.mit.media.funf.storage.FileArchive;
import edu.mit.media.funf.storage.RemoteFileArchive;
import edu.mit.media.funf.storage.UploadService;
import edu.mit.media.funf.util.EqualsUtil;
import edu.mit.media.funf.util.HashCodeUtil;
import edu.mit.media.funf.util.LockUtil;
import edu.mit.media.funf.util.LogUtil;

/*
 * This class looks almost identical to DropboxUpload Service, it extends UploadService so that 
 * it can upload both database file and regular file(s). 
 * TODO: refactoring it back to UploadService, or create another abstract class that extends UploadService
 */
public class GoogleDriveUploadService extends UploadService {

  public static final String FILE_TYPE = "filetype";
  
  public static final int
  REGULAR_FILE = 0,
  DATABASE_FILE = 1;
  
  private static final int MAX_GOOGLEDRIVE_RETRIES = 6;
  private static final String TAG = "GoogleDriveUploadService";
  
  private ConnectivityManager connectivityManager;
  private Map<String, Integer> fileFailures;
  private Map<String, Integer> remoteArchiveFailures;
  private Queue<ArchiveFile> dbFilesToUpload;
  private Queue<RegularArchiveFile> filesToUpload;
  private Thread dbUploadThread;
  private Thread regularUploadThread;
  private WakeLock lock;
  private String GoogleDriveFolderPath; //specify which folder in Google Drive 	
  
  //App Inventor specific methods for communicating error message to component
  private boolean status = true;
  private String error_message = "";
  
  // use sharedPreference to write out the result of each uploading task
  private SharedPreferences sharedPreferences;
  
  @Override
  protected RemoteFileArchive getRemoteArchive(String id) {
    Log.i(TAG, "Drivefolder: " + GoogleDriveFolderPath);
    return new GoogleDriveArchive(getApplicationContext(), GoogleDriveFolderPath);
  }
  
  
  @Override
  public void onCreate() {
    super.onCreate();
    Log.i(TAG, "Creating...");
    sharedPreferences =  getApplicationContext().getSharedPreferences(GoogleDrive.PREFS_GOOGLEDRIVE, Context.MODE_PRIVATE);
    connectivityManager =(ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
    lock = LockUtil.getWakeLock(this);
    fileFailures = new HashMap<String, Integer>();
    remoteArchiveFailures = new HashMap<String, Integer>();
    dbFilesToUpload = new ConcurrentLinkedQueue<ArchiveFile>();
    filesToUpload = new ConcurrentLinkedQueue<RegularArchiveFile>();
    // TODO: consider and add multiple upload threads
    dbUploadThread = new Thread(new Runnable() {
      @Override
      public void run() {
        Log.i(TAG, "here dbThread");
        while(Thread.currentThread().equals(dbUploadThread) && !dbFilesToUpload.isEmpty()) {
            ArchiveFile archiveFile = dbFilesToUpload.poll();
            Log.i(TAG, "now poll the archiveFile(db) from the queue");
            //runArchive method deals with uploading db archived file to Google Drive
            runArchive(archiveFile.archive, archiveFile.remoteArchive, archiveFile.file, archiveFile.network);
            Log.i(TAG, "after runArchive");
        }
        dbUploadThread = null;
        stopSelf();


      }
    });
    
    regularUploadThread = new Thread(new Runnable() {
      @Override
      public void run() {
        Log.i(TAG, "here....");
        
        while(Thread.currentThread().equals(regularUploadThread) && !filesToUpload.isEmpty()) {
          Log.i(TAG, "in thread");
          RegularArchiveFile archiveFile = filesToUpload.poll();
          Log.i(TAG, "now poll the archiveFile from the queue");
          //runUpload method deals with uploading regular files to google drive
          runUpload(archiveFile.remoteArchive, archiveFile.file, archiveFile.network);
        }
        regularUploadThread = null;        
        stopSelf();
        Log.i(TAG, "I have killed myself, so next thread can be called to run.");
       
      }
    });
    
    
  }
  
  @Override
  public int onStartCommand(Intent intent, int flags, int startId) {
    Log.i(TAG, "Starting GoogleDriveUploadService...");
    int network = intent.getIntExtra(NETWORK, NETWORK_ANY);
    int fileType = intent.getIntExtra(FILE_TYPE, REGULAR_FILE);
    Log.i(TAG, "fileType:" + fileType);
    GoogleDriveFolderPath = (intent.getStringExtra(GoogleDrive.GD_FOLDER) == null)
    		? GoogleDrive.DEFAULT_GD_FOLDER:intent.getStringExtra(GoogleDrive.GD_FOLDER) ;
    
    if (isOnline(network)) {

      if (fileType == REGULAR_FILE) {
        // just upload the stuff without all heavy-lifting backup
        Log.i(TAG, "regular file....");

        String archiveName = intent.getStringExtra(ARCHIVE_ID);
        String remoteArchiveName = intent.getStringExtra(REMOTE_ARCHIVE_ID);
        if (archiveName != null && remoteArchiveName != null) {
          RemoteFileArchive remoteArchive = getRemoteArchive(remoteArchiveName);
          if (remoteArchive != null) {
            File file = new File(archiveName);//here file name will be archiveName
            regularArchive(remoteArchive, file,network);
          }
        }

        // Start upload thread if necessary, even if no files to ensure stop
        if (regularUploadThread != null && !regularUploadThread.isAlive()) {
          Log.i(TAG, "start a new uploading thread....");
          regularUploadThread.start();
        }
      } else {
        // here we have almost identical codes as UploadService.java
        Log.i(TAG, "db file....");

        String archiveName = intent.getStringExtra(ARCHIVE_ID);
        
        String remoteArchiveName = intent.getStringExtra(REMOTE_ARCHIVE_ID);
        if (archiveName != null && remoteArchiveName != null) {
          FileArchive archive = getArchive(archiveName);
          RemoteFileArchive remoteArchive = getRemoteArchive(remoteArchiveName);
          if (archive != null && remoteArchive != null) {
            for (File file : archive.getAll()) {
              Log.i(TAG, "...loop");
              dbArchive(archive, remoteArchive, file, network);
            }
          }
        }

        // Start upload thread if necessary, even if no files to ensure stop
        if (dbUploadThread != null && !dbUploadThread.isAlive()) {
          Log.i(TAG, "start a new uploading thread(DB)....");
          dbUploadThread.start();
          
        }

      }

    }
    
    return Service.START_STICKY;
  }
  
  
  //this ArchiveFile is a place holder for keeping info about REGULAR_FILE uploading, not the db file
  protected class RegularArchiveFile {
    public final RemoteFileArchive remoteArchive;
    public final File file;
    public final int network;
    public RegularArchiveFile(RemoteFileArchive remoteArchive, File file, int network) {
 
      this.remoteArchive = remoteArchive;
      this.file = file;
      this.network = network;
    }
    @Override
    public boolean equals(Object o) {
      return o != null && o instanceof ArchiveFile 
        && EqualsUtil.areEqual(remoteArchive.getId(), ((ArchiveFile)o).remoteArchive.getId())
        && EqualsUtil.areEqual(file, ((ArchiveFile)o).file);
    }
    @Override
    public int hashCode() {
      return HashCodeUtil.hash(HashCodeUtil.hash(HashCodeUtil.SEED, file), remoteArchive.getId());
    }
    
    
  }

  /*
   * override UploadService to have our own exception handling
   */
  
  @Override
  protected void runArchive(FileArchive archive, RemoteFileArchive remoteArchive, File file, int network) {
    Integer numRemoteFailures = remoteArchiveFailures
        .get(remoteArchive.getId());
    numRemoteFailures = (numRemoteFailures == null) ? 0 : numRemoteFailures;
    Log.i(LogUtil.TAG, "numRemoteFailures:" + numRemoteFailures);
    Log.i(LogUtil.TAG, "isOnline:" + isOnline(network));
    boolean successUpload = false;

    if (numRemoteFailures < MAX_REMOTE_ARCHIVE_RETRIES && isOnline(network)) {
      Log.i(LogUtil.TAG, "Archiving..." + file.getName());

      try {
        successUpload = remoteArchive.add(file);
      } catch (Exception e) {
        // something happen that we can't successfully upload the file to
        // google drive , and we will tell the UI component
        Log.i(TAG, "exception happened, ready to call back to the AI component");
        status = successUpload;
        error_message = getErrorMessage(e);
      }
 
      if (successUpload) {
        archive.remove(file);
        saveStatusLog(file.getAbsolutePath(), true, "success");
      } else {
        Integer numFileFailures = fileFailures.get(file.getName());
        numFileFailures = (numFileFailures == null) ? 1 : numFileFailures + 1;
        numRemoteFailures += 1;
        fileFailures.put(file.getName(), numFileFailures);
        remoteArchiveFailures.put(remoteArchive.getId(), numRemoteFailures);
        // 3 Attempts
        if (numFileFailures < MAX_FILE_RETRIES) {
          dbFilesToUpload.offer(new ArchiveFile(archive, remoteArchive, file,
              network));
        } else {

          Log.i(LogUtil.TAG, "Failed to upload '" + file.getAbsolutePath()
              + "' after 3 attempts.");
          saveStatusLog(file.getAbsolutePath(), status, error_message);
        }
      }
    } else {
      Log.i(LogUtil.TAG,
          "Canceling upload.  Remote archive '" + remoteArchive.getId()
              + "' is not currently available.");
      saveStatusLog(file.getAbsolutePath(), status, error_message);
    }

  }
  

  private void runUpload(RemoteFileArchive remoteArchive, File file, int network){
    // this part of code is copied and modified from UploadService.java in funf library
    // only uses when fileType == REGULAR_FILE, else we will use UploadService.java$runArchive()
    Integer numRemoteFailures = remoteArchiveFailures.get(remoteArchive.getId());
    numRemoteFailures = (numRemoteFailures == null) ? 0 : numRemoteFailures;
    Log.i(TAG, "numRemoteFailures:" + numRemoteFailures );
    Log.i(TAG, "isOnline:" + isOnline(network) );
    boolean successUpload = false;
 
    if (numRemoteFailures < MAX_GOOGLEDRIVE_RETRIES && isOnline(network)) {
      Log.i(TAG, "uploading to google drive..." + file.getName());
      try{
        
        successUpload = remoteArchive.add(file);
        Log.i(TAG, "success?:" + successUpload);
      }catch(Exception e){
        Log.i(TAG, "something wrong in GoogleDriveArchive:" + e.toString());
        e.printStackTrace();
        // something happen that we can't successfully upload the file to Google drive
        status = successUpload;
        error_message = getErrorMessage(e);
      
      }
 
      if(successUpload) {
        Log.i(TAG, "successful upload file to google drive");
        saveStatusLog(file.getAbsolutePath(), true, "success");
        //do nothing
      } else {
        Integer numFileFailures = fileFailures.get(file.getName());
        numFileFailures = (numFileFailures == null) ? 1 : numFileFailures + 1;
        numRemoteFailures += 1;
        fileFailures.put(file.getName(), numFileFailures);
        remoteArchiveFailures.put(remoteArchive.getId(), numRemoteFailures);
        // 3 Attempts
        if (numFileFailures < MAX_FILE_RETRIES) {
          filesToUpload.offer(new RegularArchiveFile(remoteArchive, file, network));
        } else {
          Log.i(TAG, "Failed to upload '" + file.getAbsolutePath() + "' after 3 attempts.");
          saveStatusLog(file.getAbsolutePath(), status, error_message);
        }
      }
     }else {
      Log.i(TAG, "Canceling upload.  Remote archive '" + remoteArchive.getId() + "' is not currently available.");
      saveStatusLog(file.getAbsolutePath(), status, error_message);

    } 
  }

  
  @Override
  public boolean isOnline(int network) {
    NetworkInfo netInfo = connectivityManager.getActiveNetworkInfo();
    
    if (network == NETWORK_ANY && netInfo != null && netInfo.isConnectedOrConnecting()) {
        return true;
    } else if (network == NETWORK_WIFI_ONLY ) {
      Log.i(TAG,"we are in isOnline(): NETOWORK_WIFI_ONLY "+ network);
      Log.i(TAG, "Prining out debugging info of connectivityStatus: ");

      State wifiInfo = connectivityManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI).getState();
      Log.i(TAG, wifiInfo.toString());
      if (State.CONNECTED.equals(wifiInfo) || State.CONNECTING.equals(wifiInfo)) {
        return true;
      }
    }
    return false;
}
  /*
   * This is a copy of UploadService$archive(), we need to rename and replicate this because it's used in 
   * GoogleDriveUploadSerive$onStartCommand that override UploadService$onStartCommand
   */
  public void dbArchive(FileArchive archive, RemoteFileArchive remoteArchive, File file, int network) {
    ArchiveFile archiveFile = new ArchiveFile(archive, remoteArchive, file, network);
    if (!dbFilesToUpload.contains(archiveFile)) {
      Log.i(TAG, "Queuing " + file.getName());
      dbFilesToUpload.offer(archiveFile);
    }

  }
  
  public void regularArchive(RemoteFileArchive remoteArchive, File file, int network){
    RegularArchiveFile regularArchiveFile = new RegularArchiveFile(remoteArchive, file, network);
    if(!filesToUpload.contains(regularArchiveFile)){
      Log.i(TAG, "We are Queuing " + file.getName());
      filesToUpload.offer(regularArchiveFile);
      
    }
    
  }
  
  @Override
  public void onDestroy() {
    if (regularUploadThread != null && regularUploadThread.isAlive()) {
      regularUploadThread = null;
    }
    
    if (dbUploadThread != null && dbUploadThread.isAlive()) {
      dbUploadThread = null;
    }
    
    if (lock.isHeld()) {
      lock.release();
    }
  }
  
  /**
   * Binder interface to the probe
   */

  public class LocalBinder extends Binder {
    public GoogleDriveUploadService getService() {
            return GoogleDriveUploadService.this;
        }
    }
  private final IBinder mBinder = new LocalBinder();
  @Override
  public IBinder onBind(Intent intent) {
    return mBinder;
  }
  
  private String getErrorMessage(Exception e){
    String errorMsg = "";
    
    try{
      throw e;
    } catch (GoogleJsonResponseException e1) {
      GoogleJsonError error = e1.getDetails();

      Log.e(TAG, "Error code: " + error.getCode());
      Log.e(TAG, "Error message: " + error.getMessage());
      if(error.getCode() == 401){
        errorMsg = ErrorMessages.formatMessage(
            ErrorMessages.ERROR_GOOGLEDRIVE_INVALID_CREDENTIALS, null);
      }
      if (error.getCode() == 403 && (error.getErrors().get(0).getReason().equals("appAccess"))){
        errorMsg = ErrorMessages.formatMessage(
            ErrorMessages.ERROR_GOOGLEDRIVE_NOT_GRANT_PERMISSION, null);
      }
      
      if (error.getCode() == 403 && (error.getErrors().get(0).getReason().equals("appNotConfigured"))){
        errorMsg = ErrorMessages.formatMessage(
            ErrorMessages.ERROR_GOOGLEDRIVE_APP_CONFIG_ERROR, null);
        
      }
      if (error.getCode() == 403 && (error.getErrors().get(0).getReason().equals("appBlacklisted"))){
        errorMsg = ErrorMessages.formatMessage(
            ErrorMessages.ERROR_GOOGLEDRIVE_APP_BLACKLIST, null);        
      }

      // More error information can be retrieved with error.getErrors().
    }
      catch (IOException exception){
        errorMsg = ErrorMessages.formatMessage(
          ErrorMessages.ERROR_GOOGLEDRIVE_IO_EXCEPTION, null);
 

    } catch (Exception exceptfinal) {
      // TODO Auto-generated catch block
      errorMsg = ErrorMessages.formatMessage(
          ErrorMessages.ERROR_GOOGLEDRIVE_EXCEPTION, null);
    }
    
    return errorMsg;

  }
  
  private void saveStatusLog(String targetFile, boolean status, String message){
    //save to sharedPreference the latest status 
    final SharedPreferences.Editor sharedPrefsEditor = sharedPreferences.edit();
    
    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
    Date date = new Date();
    String currentDatetime = dateFormat.format(date);
    sharedPrefsEditor.putString(GoogleDrive.GOOGLEDRIVE_LASTUPLOAD_TARGET, targetFile);
    sharedPrefsEditor.putBoolean(GoogleDrive.GOOGLEDRIVE_LASTUPLOAD_STATUS, status);
    sharedPrefsEditor.putString(GoogleDrive.GOOGLEDRIVE_LASTUPLOAD_REPORT, message);
    sharedPrefsEditor.putString(GoogleDrive.GOOGLEDRIVE_LASTUPLOAD_TIME, currentDatetime);
    
    sharedPrefsEditor.commit();        
    
  }  
  
  

}
