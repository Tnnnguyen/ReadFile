package service;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.example.tuan.readfile.MainActivity;
import com.example.tuan.readfile.R;

import java.io.File;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import butterknife.BindString;

/**
 * Created by tuan on 3/3/2016.
 */
public class ReadFileService extends Service {

    private static final char FILE_EXTENSION_SEPARATOR = '.';
    private static final int MAX_COUNT_ON_SIZE = 10;
    private static final int MAX_COUNT_ON_FREQUENCY = 5;
    private final IBinder mBinder = new LocalBinder();
    private boolean mStopFileRead;

    /**
     * Flip this field to stop or start file scan in startFileRead method
     */
    public void stopFileRead(boolean isStopped) {
        mStopFileRead = isStopped;
    }

    public void runInForegroundMode(){
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0,
                intent, 0);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                .setSmallIcon(R.drawable.red_star)
                .setContentText(getResources().getString(R.string.scanning))
                .setContentIntent(pendingIntent);
        Notification notification;
        //support API 14
        if(Build.VERSION.SDK_INT < 16) {
            notification = builder.getNotification();
        }
        else {
            notification = builder.build();
        }
        startForeground(1, notification);
    }

    /**
     * Scan external directory and retrieve files information on a background thread.
     * If directory cannot be opened or is empty, broadcast a default string
     */
    @BindString(R.string.file_error_or_empty) String fileErrorOrEmpty;
    public void startFileRead() {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                //File directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File directory = Environment.getExternalStorageDirectory();
                File[] files = directory.listFiles();
                double aveFileSize = 0;

                if(files != null && files.length > 0) {
                    Map<Long, String> sortedOnSizeMap = new TreeMap<>(new ReverseComparator());
                    Map<String, Integer> occurrenceMap = new HashMap<>();
                    Map<Integer, String> sortedOccurrenceMap = new TreeMap<>(new ReverseComparator());
                    int fileCounts = files.length;
                    for (File fi : files) {
                        if(!mStopFileRead) {
                            if(!fi.isDirectory()) {
                                String[] names = getFileNameAndExtension(fi.getName());
                                if(names != null) {
                                    long fileSize = fi.length();
                                    sortedOnSizeMap.put(fileSize, names[0]);
                                    if (!occurrenceMap.containsKey(names[1])) {
                                        occurrenceMap.put(names[1], 1);
                                    } else {
                                        occurrenceMap.put(names[1], occurrenceMap.get(names[1]) + 1);
                                    }
                                    aveFileSize += fileSize;
                                }
                                else{
                                    //decrement file count for every file name that cannot be processed
                                    fileCounts--;
                                }
                            }
                        }
                        else {
                            return;
                        }
                    }
                    //handle the case where only 1 file is present in the directory and this single file
                    //cannot be processed.
                    if(fileCounts > 0) {
                        aveFileSize /= fileCounts;
                        //sort descending base on occurrences of extensions
                        for (Map.Entry e : occurrenceMap.entrySet()) {
                            sortedOccurrenceMap.put((Integer) e.getValue(), (String) e.getKey());
                        }
                        composeScanResult(aveFileSize, sortedOnSizeMap, sortedOccurrenceMap);
                    }
                    else {
                        sendBroadCast(fileErrorOrEmpty);
                    }
                }
                else {
                    sendBroadCast(fileErrorOrEmpty);
                }
            }
        });
        thread.start();
    }

    /**
     * Compose a string the contains file scan result
     * @param aveFileSize average file size of all files scanned
     * @param sizeMap Map containing file names, sorted descending on size
     * @param occurrenceMap Map containing file names, sorted descending of number of file occurence
     */
    private void composeScanResult(double aveFileSize, Map<Long, String> sizeMap, Map<Integer, String> occurrenceMap ) {
        String result = "";
        result += getResources().getString(R.string.name_size_heading);
        int count = 0;

        for(Map.Entry e : sizeMap.entrySet()) {
            result += getResources().getString(R.string.name) + ": " + e.getValue() + ",   " +
                    getResources().getString(R.string.size)+ ": " + e.getKey() + "\n";
            if(++count >= MAX_COUNT_ON_SIZE){
                break;
            }
        }
        count = 0;
        result += getResources().getString(R.string.frequency_heading);
        for(Map.Entry e : occurrenceMap.entrySet()) {
            result += getResources().getString(R.string.extension) + ": " + e.getValue() + ",   " +
                    getResources().getString(R.string.frequency) + ": " + e.getKey() + "\n";
            if(++count >= MAX_COUNT_ON_FREQUENCY) {
                break;
            }
        }
        result += getResources().getString(R.string.avg_heading);
        String aveSizeString = String.valueOf(aveFileSize);
        int position = aveSizeString.lastIndexOf(FILE_EXTENSION_SEPARATOR);
        result += aveSizeString.substring(0, position +3 );
        sendBroadCast(result);
    }

    /**
     * Sends a broadcast containing file scan result in a string
     * @param result the scan result to be broadcasted
     */
    private void sendBroadCast(String result) {
        //delay so status bar and progress bar show longer, can be removed with no harm to program
        try{
            Thread.sleep(500);
        }
        catch(InterruptedException ie){
            //do nothing
        }
        Intent intent = new Intent(MainActivity.INTENT_READ_FILE_RECEIVER_ACTION);
        intent.putExtra(MainActivity.KEY_SCAN_RESULT_BROADCAST, result);
        sendBroadcast(intent);
        stopForeground(true);
    }

    /**
     * Separate the file name and its extension
     * @param fullName full file name with extension
     * @return array of 2 strings, one for name and one for extension
     */
    private String[] getFileNameAndExtension(String fullName) {
        //if fullName's length is 2: ".p", "aa", "a.", then it's meaningless for
        //the purpose of this scan; therefore, return null
        if(fullName == null || fullName.length() == 2) {
            return null;
        }
        int position = fullName.lastIndexOf(FILE_EXTENSION_SEPARATOR);
        //if position of the period "." is first (no file name: .pdf), last (no file extension: hello.)
        //or nowhere to be found in the string, return null
        if(position <= 0 || position == (fullName.length() -1)) {
            return null;
        }
        return new String[]{fullName.substring(0, position), fullName.substring(++position)};
    }

    @Override
    public void onCreate() {
        super.onCreate();
        runInForegroundMode();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    public class LocalBinder extends Binder {
        public ReadFileService getService() {
            return ReadFileService.this;
        }
    }

    /**
     * To keep element sorted in descending order as they are inserted
     * into the TreeMap.
     * Avoid one sort extra operation.
     * Will compare sub classes of Number
     * compareTo is used since compare only takes in integer and we are comparing Long and Integer
     */
    class ReverseComparator <T extends Number & Comparable> implements Comparator<T>{
        @Override
        public int compare(T lhs, T rhs) {
            return rhs.compareTo(lhs) ;
        }
    }
}
