package com.example.wifidrop;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import android.app.ProgressDialog;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ChannelListener;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.IBinder;
import android.os.Looper;
import android.os.Handler;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Toast;

public class WiFiDropService extends Service implements ChannelListener, WiFiDropConnectionListener, 
		ConnectionInfoListener {

    private static final String TAG = WiFiDropActivity.TAG + "(service)";
    private final IntentFilter intentFilter = new IntentFilter();
    private Channel channel;
    private BroadcastReceiver receiver = null;
    private WifiP2pManager manager;
    private WifiP2pDnsSdServiceInfo service;
    private WifiP2pDnsSdServiceRequest serviceRequest;

    private String userName = null;

    @Override
    public IBinder onBind(Intent intent) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void onCreate() {
        // TODO Auto-generated method stub
        super.onCreate();

        // open Cursor for Profile
        Cursor mCursor = getContentResolver().query(
                ContactsContract.Profile.CONTENT_URI, null, null, null, null);

        mCursor.moveToFirst();

        // retrieve UserName
        int nameIndex = mCursor
                .getColumnIndex(ContactsContract.Profile.DISPLAY_NAME);
        userName = mCursor.getString(nameIndex);
        mCursor.close();

        intentFilter.addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        intentFilter
                .addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        intentFilter
                .addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(this, getMainLooper(), this);

        // registering multiple receivers causes multiple Intents to be
        // dispatched.
        // make sure single receiver is registered at a time
        if (receiver == null) {
            Log.d(TAG, "Broadcast receiver registered");
            receiver = new WiFiDropBroadcastReceiver(manager, channel, this, "(Ser)");
            registerReceiver(receiver, intentFilter);
        }
        //startLocalService();
    }

    @Override
    public void onDestroy() {
        // TODO Auto-generated method stub
        manager.clearLocalServices(channel, new ActionListener() {
            @Override
            public void onSuccess() {
                appendStatus("Cleared Local Service");
				/*
				manager.addLocalService(channel, service, new ActionListener() {

					@Override
					public void onSuccess() {
						appendStatus("Added Local Service");
					}

					@Override
					public void onFailure(int error) {
						appendStatus("Failed to add a service");
					}
				});
				*/
            }

            @Override
            public void onFailure(int error) {
                appendStatus("Failed to clear a service");
            }
        });
        unregisterReceiver(receiver);
        super.onDestroy();
    }

    @Override
    public void onChannelDisconnected() {
        // TODO Auto-generated method stub
        Log.d(TAG, "Channel disconnected");
    }

    private void startLocalService() {
        Map<String, String> record = new HashMap<String, String>();
        record.put(WiFiDropDnsService.TXTRECORD_PROP_AVAILABLE, "visible");
        record.put(WiFiDropDnsService.USER_NAME, userName);

        service = WifiP2pDnsSdServiceInfo.newInstance(
                WiFiDropDnsService.SERVICE_INSTANCE,
                WiFiDropDnsService.SERVICE_REG_TYPE, record);

        manager.clearLocalServices(channel, new ActionListener() {
            @Override
            public void onSuccess() {
                appendStatus("Cleared Local Service");
                manager.addLocalService(channel, service, new ActionListener() {

                    @Override
                    public void onSuccess() {
                        appendStatus("Added Local Service");
                        startDiscovery();
                    }

                    @Override
                    public void onFailure(int error) {
                        appendStatus("Failed to add a service");
                    }
                });
            }

            @Override
            public void onFailure(int error) {
                appendStatus("Failed to clear a service");
            }
        });
    }

    private void startDiscovery() {
        serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();
        manager.addServiceRequest(channel, serviceRequest,
                new ActionListener() {

                    @Override
                    public void onSuccess() {
                        appendStatus("Added service discovery request");
                    }

                    @Override
                    public void onFailure(int arg0) {
                        appendStatus("Failed adding service discovery request");
                    }
                });

        manager.discoverServices(channel, new ActionListener() {

            @Override
            public void onSuccess() {
                appendStatus("Service discovery initiated");
            }

            @Override
            public void onFailure(int arg0) {
                appendStatus("Service discovery failed");

            }
        });
    }

    public void appendStatus(String status) {
        Log.d(TAG, status);
        // String current = statusTxtView.getText().toString();
        // statusTxtView.setText(current + "\n" + status);
    }

    @Override
    public void onConnectionInfoAvailable(WifiP2pInfo info) {
        // TODO Auto-generated method stub
        if (!info.groupFormed) {
            // disconnected
            startLocalService();
            return;
        }

        // Nexus4 can not manipulate to become Group owner or not
        // Service is to receive an image file anyway.
        if (info.isGroupOwner) {
            Log.d(TAG, "Connected. Going to receive an image file.");
            // remove local service to stop advertisement of service
            if (service != null) {
                manager.removeLocalService(channel, service, new ActionListener() {

                    @Override
                    public void onSuccess() {
                        appendStatus("Removed Local Service");
                        service = null;
                    }

                    @Override
                    public void onFailure(int error) {
                        appendStatus("Failed to remove a service");
                    }
                });
            }
            AsyncTask<Void, Void, String> task = new FileServerAsyncTask(this)
                    .execute();
        }
    }

    @Override
    public void connectP2p(WiFiDropDnsService wiFiDropService) {
        // TODO Auto-generated method stub
    }

    @Override
    public void disconnectP2p() {
        // TODO Auto-generated method stub
        toast(this, "WiFi切断中");
        if (manager != null && channel != null) {
            manager.removeGroup(channel, new ActionListener() {

                @Override
                public void onFailure(int reasonCode) {
                    Log.d(TAG, "Disconnect failed. Reason :" + reasonCode);
                }

                @Override
                public void onSuccess() {
                    Log.d(TAG, "Disconnect succeeded.");
                }

            });
        }
    }

    /**
     * A simple server socket that accepts connection and writes some data on
     * the stream.
     */
    public static class FileServerAsyncTask extends
            AsyncTask<Void, Void, String> {

        private Context context;
        private ProgressDialog progress;

        /**
         * @param context
         */
        public FileServerAsyncTask(Context context) {
            this.context = context;
        }

        @Override
        protected String doInBackground(Void... params) {
            Log.d(TAG, "doInBack");
            ServerSocket serverSocket = null;
            try {
                serverSocket = new ServerSocket(WiFiDropActivity.PORT);
                Log.d(TAG, "Server: Socket opened");
                Socket client = serverSocket.accept();
                Log.d(TAG, "Server: connection done");
                final File f = new File(
                        Environment
                                .getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                                + "/"
                                + context.getPackageName()
                                + "/wifidropshared-"
                                + System.currentTimeMillis() + ".jpg");

                File dirs = new File(f.getParent());
                if (!dirs.exists())
                    dirs.mkdirs();
                f.createNewFile();

                Log.d(TAG, "server: writing files " + f.toString());
                InputStream inputstream = client.getInputStream();
                OutputStream outputstream = new FileOutputStream(f);
                WiFiDropActivity.copyFile(inputstream, outputstream);
                outputstream.flush();
                Log.d(WiFiDropActivity.TAG, "Server: File written");
                toast(this.context, "受信完了");
                return f.getAbsolutePath();
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
                toast(this.context, "受信失敗");
                return null;
            } finally {
                if (serverSocket != null) {
                    if (serverSocket.isBound()) {
                        try {
                            serverSocket.close();
                        } catch (IOException e) {
                            // Give up
                            e.printStackTrace();
                        }
                    }
                }
            }
        }

        /*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
		 */
        @Override
        protected void onPostExecute(String result) {
            progress.dismiss();
            if (result != null) {
                Log.d(TAG, "File written - " + result);
                Intent intent = new Intent();
                intent.setAction(android.content.Intent.ACTION_VIEW);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.setDataAndType(Uri.parse("file://" + result), "image/*");
                context.startActivity(intent);
            }
            ((WiFiDropConnectionListener) this.context).disconnectP2p();
        }

        /*
		 * (non-Javadoc)
		 * 
		 * @see android.os.AsyncTask#onPreExecute()
		 */
        @Override
        protected void onPreExecute() {
            Log.d(TAG, "Opening a server socket");
            progress = new ProgressDialog(context);
            progress.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ERROR);
            progress.setTitle(WiFiDropActivity.TAG);
            progress.setMessage("受信中");
            progress.setCancelable(false);
            progress.show();
        }
    }

    private static void toast(final Context context, final String message) {
        Handler handler = new Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(context, message, Toast.LENGTH_LONG).show();
            }
        });
    }


}
