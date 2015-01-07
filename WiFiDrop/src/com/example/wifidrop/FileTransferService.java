// Copyright 2011 Google Inc. All Rights Reserved.

package com.example.wifidrop;

import android.app.IntentService;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.app.TaskStackBuilder;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import android.os.Handler;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * A service that process each file transfer request i.e Intent by opening a
 * socket connection with the WiFi Direct Group Owner and writing the file
 */
public class FileTransferService extends IntentService {

    private static final int SOCKET_TIMEOUT = 5000;
    public static final String ACTION_SEND_FILE = "com.example.wifidrop.SEND_FILE";
    public static final String EXTRAS_FILE_PATH = "file_url";
    public static final String EXTRAS_GROUP_OWNER_ADDRESS = "go_host";
    public static final String EXTRAS_GROUP_OWNER_PORT = "go_port";
    private int mId;
    private ProgressDialog progress;

    public FileTransferService(String name) {
        super(name);
    }

    public FileTransferService() {
        super("FileTransferService");
    }

    @Override
    public void onDestroy() {
    	Log.d(WiFiDropActivity.TAG, "Service onDestroy");
    }
    
    /*
     * (non-Javadoc)
     * @see android.app.IntentService#onHandleIntent(android.content.Intent)
     */
    @Override
    protected void onHandleIntent(Intent intent) {

        final Context context = getApplicationContext();
        if (intent.getAction().equals(ACTION_SEND_FILE)) {
            final String fileUri = intent.getExtras().getString(EXTRAS_FILE_PATH);
            final String host = intent.getExtras().getString(EXTRAS_GROUP_OWNER_ADDRESS);
            final Socket socket = new Socket();
            final int port = intent.getExtras().getInt(EXTRAS_GROUP_OWNER_PORT);
            progress = new ProgressDialog(this);
            progress.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ERROR);
            progress.setTitle(WiFiDropActivity.TAG);
            progress.setMessage("送信中");
            progress.setCancelable(false);
            progress.show();
            //Toast.makeText(this, "送信中", Toast.LENGTH_LONG).show();
            Thread thread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try

                    {
                        Log.d(WiFiDropActivity.TAG, "Opening client socket - ");
                        socket.bind(null);
                        socket.connect((new InetSocketAddress(host, port)), SOCKET_TIMEOUT);

                        Log.d(WiFiDropActivity.TAG, "Client socket - " + socket.isConnected());
                        OutputStream stream = socket.getOutputStream();
                        ContentResolver cr = context.getContentResolver();
                        InputStream is = null;
                        try {
                            is = cr.openInputStream(Uri.parse(fileUri));
                        } catch (FileNotFoundException e) {
                            Log.d(WiFiDropActivity.TAG, e.toString());
                        }
                        WiFiDropActivity.copyFile(is, stream);
                        stream.flush();
                        progress.dismiss();
                        //Toast.makeText(context, "送信完了", Toast.LENGTH_LONG).show();
                        Log.d(WiFiDropActivity.TAG, "Client: Data written");
                    }

                    catch(
                            IOException e
                            )

                    {
                        //Toast.makeText(context, "送信失敗", Toast.LENGTH_LONG).show();
                        Log.e(WiFiDropActivity.TAG, e.getMessage());
                    }

                    finally

                    {
                        if (socket != null) if (socket.isConnected()) {
                            try {
                                socket.close();
                            } catch (IOException e) {
                                // Give up
                                e.printStackTrace();
                            }
                        }

                    }

                }
            });
            thread.start();
//            try
//
//            {
//                    Log.d(WiFiDropActivity.TAG, "Opening client socket - ");
//                    socket.bind(null);
//                    socket.connect((new InetSocketAddress(host, port)), SOCKET_TIMEOUT);
//
//                    Log.d(WiFiDropActivity.TAG, "Client socket - " + socket.isConnected());
//                    OutputStream stream = socket.getOutputStream();
//                    ContentResolver cr = context.getContentResolver();
//                    InputStream is = null;
//                    try {
//                        is = cr.openInputStream(Uri.parse(fileUri));
//                    } catch (FileNotFoundException e) {
//                        Log.d(WiFiDropActivity.TAG, e.toString());
//                    }
//                    WiFiDropActivity.copyFile(is, stream);
//                    stream.flush();
//                    progress.dismiss();
//                    Toast.makeText(this, "送信完了", Toast.LENGTH_LONG).show();
//                    Log.d(WiFiDropActivity.TAG, "Client: Data written");
//                }
//
//                catch(
//                IOException e
//                )
//
//                {
//                    Toast.makeText(this, "送信失敗", Toast.LENGTH_LONG).show();
//                    Log.e(WiFiDropActivity.TAG, e.getMessage());
//                }
//
//                finally
//
//                {
//                    if (socket != null) if (socket.isConnected()) {
//                        try {
//                            socket.close();
//                        } catch (IOException e) {
//                            // Give up
//                            e.printStackTrace();
//                        }
//                    }
//
//                }
//
            }
    }


}
