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
                    for (File fi : files) {
                        if(!mStopFileRead) {
                            if(!fi.isDirectory()) {
                                String[] names = getFileNameAndExtension(fi.getName());
                                long fileSize = fi.length();
                                sortedOnSizeMap.put(fileSize, names[0]);
                                if(!occurrenceMap.containsKey(names[1])) {
                                    occurrenceMap.put(names[1], 1);
                                } else {
                                    occurrenceMap.put(names[1], occurrenceMap.get(names[1]) + 1);
                                }
                                aveFileSize += fileSize;
                            }
                        }
                        else {
                            return;
                        }
                    }
                    aveFileSize /= files.length;
                    //sort ascending base on occurrences of extensions
                    for(Map.Entry e : occurrenceMap.entrySet()) {
                        sortedOccurrenceMap.put((Integer) e.getValue(), (String) e.getKey());
                    }
                    composeScanResult(aveFileSize, sortedOnSizeMap, sortedOccurrenceMap);
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
     * @param sizeMap Map containing file names, sorted ascending on size
     * @param occurrenceMap Map containing file names, sorted ascending of number of file occurence
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
        stopForegroundMode(true);
    }

    /**
     * Separate the file name and its extension
     * @param fullName full file name with extension
     * @return array of 2 strings, one for name and one for extension
     */
    private String[] getFileNameAndExtension(String fullName) {
        int position = fullName.lastIndexOf(FILE_EXTENSION_SEPARATOR);
        return new String[]{fullName.substring(0, position), fullName.substring(++position)};
    }

    /**
     * Stop the foreground mode of the service
     * @param removeNotification status bar notification is removed if true
     */
    public void stopForegroundMode(boolean removeNotification) {
        stopForeground(removeNotification);
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
     * To keep element sorted in ascending order as they are inserted
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
