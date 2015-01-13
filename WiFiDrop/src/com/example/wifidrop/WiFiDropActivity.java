package com.example.wifidrop;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

import android.app.Activity;
import android.app.ActionBar;
import android.app.Fragment;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.app.TaskStackBuilder;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.net.Uri;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.ActionListener;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ChannelListener;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.net.wifi.p2p.WifiP2pManager.DnsSdServiceResponseListener;
import android.net.wifi.p2p.WifiP2pManager.DnsSdTxtRecordListener;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;
import android.provider.MediaStore;

import com.example.wifidrop.WiFiDropDnsServicesList.WiFiDevicesAdapter;
import com.example.wifidrop.WiFiDropService.FileServerAsyncTask;

public class WiFiDropActivity extends Activity implements
		ConnectionInfoListener, WiFiDropConnectionListener, ChannelListener {

	public final static String TAG = "WiFiDrop";
	
	public static final String FRAGMENT_TAG = "devices";
	private static final int SOCKET_TIMEOUT = 5000;
	static final int PORT = 8988;
	
	private int mId;
	private final IntentFilter intentFilter = new IntentFilter();
	private Channel channel;
	private BroadcastReceiver receiver = null;
	private WifiP2pDnsSdServiceRequest serviceRequest;
	private WifiP2pManager manager;
	private WiFiDropDnsServicesList servicesList;
	private WifiP2pDnsSdServiceInfo service;
	private Map<String, String> profiles = new HashMap<String, String>();
	private Uri uri = null;
    private int size;

    private ProgressDialog progress;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		Log.d(TAG, "on create");
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_wi_fi_drop);


        intentFilter.addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION);
		intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
		intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
		intentFilter
				.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
		intentFilter
				.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

		manager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
		channel = manager.initialize(this, getMainLooper(), this);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.wi_fi_drop, menu);
		return true;
	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();

		Intent intent = getIntent();
		String action = intent.getAction();
		// Log.d(TAG, intent.toString());
		// Who initiates WiFi Drop listen to service
		if (action != null && action.equals(Intent.ACTION_SEND)) {
			// registering multiple receivers causes multiple Intents to be
			// dispatched.
			// make sure single receiver is registered at a time
			if (receiver == null) {
				Log.d(TAG, "Broadcast receiver registered");
				receiver = new WiFiDropBroadcastReceiver(manager, channel, this, "(Act)");
				registerReceiver(receiver, intentFilter);
			}

			ClipData clip = intent.getClipData();
			// Log.d(TAG, clip.toString());
			uri = clip.getItemAt(0).getUri();
			String type = intent.getType();
			String sizeStr = getMediaInfo(uri, MediaStore.Images.ImageColumns.SIZE);
            size = Integer.valueOf(sizeStr);

			Log.d(TAG, uri.toString());
			Log.d(TAG, type);
			Log.d(TAG, getMediaInfo(uri, MediaStore.Images.ImageColumns.DATA));
            Log.d(TAG, sizeStr + "bytes");

			servicesList = new WiFiDropDnsServicesList();
			getFragmentManager().beginTransaction()
					.add(R.id.container, servicesList, FRAGMENT_TAG).commit();
			startDiscovery();
			// Who receives WiFi Drop advertise service
		} else {
			getFragmentManager().beginTransaction()
					.add(R.id.container, new PlaceholderFragment()).commit();
		}
	}

	private String getMediaInfo(final Uri uri, final String column) {
		String title = "";
		final ContentResolver cr = getContentResolver();
		Cursor cursor = null;
		try {
			cursor = cr.query(uri, null, null, null, null);
			if (null != cursor) {
				if (1 == cursor.getCount()) {
					cursor.moveToFirst();
					title = cursor.getString(cursor
							.getColumnIndexOrThrow(column));
				}
			}
		} catch (Exception e) {

		} finally {
			cursor.close();
		}
		return title;
	}

	@Override
	protected void onPause() {
		// TODO Auto-generated method stub
        if (serviceRequest != null) {
            manager.removeServiceRequest(channel, serviceRequest,
                    new ActionListener() {

                        @Override
                        public void onSuccess() {
                            Log.d(TAG, "Removed Service request.");
                        }

                        @Override
                        public void onFailure(int arg0) {
                            Log.d(TAG, "Failed to remove Service request.");
                        }
                    });
        }
		if (receiver != null) {
            appendStatus("unregister receiver");
			unregisterReceiver(receiver);
			receiver = null;
		}
		super.onPause();
	}

	@Override
	protected void onRestart() {
        Log.d(TAG, "onRestart");
		Fragment frag = getFragmentManager().findFragmentByTag(FRAGMENT_TAG);
		if (frag != null) {
            Log.d(TAG, "removed Fragment");
			getFragmentManager().beginTransaction().remove(frag).commit();
		}
		super.onRestart();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	private void startDiscovery() {
		manager.setDnsSdResponseListeners(channel,
				new DnsSdServiceResponseListener() {

					@Override
					public void onDnsSdServiceAvailable(String instanceName,
							String registrationType, WifiP2pDevice srcDevice) {

						// A service has been discovered. Is this our app?

						if (instanceName
								.equalsIgnoreCase(WiFiDropDnsService.SERVICE_INSTANCE)) {

							// update the UI and add the item the discovered
							// device.
							WiFiDropDnsServicesList fragment = (WiFiDropDnsServicesList) getFragmentManager()
									.findFragmentByTag(FRAGMENT_TAG);
							if (fragment != null) {
								WiFiDevicesAdapter adapter = ((WiFiDevicesAdapter) fragment
										.getListAdapter());
								WiFiDropDnsService service = new WiFiDropDnsService();
								service.device = srcDevice;
								service.instanceName = instanceName;
								service.serviceRegistrationType = registrationType;
								Log.d(TAG, "recrod=" + profiles.toString());
								String userName = profiles
										.get(srcDevice.deviceName);
								if (userName != null) {
									service.userName = userName;
								}
								adapter.add(service);
								adapter.notifyDataSetChanged();
								Log.d(TAG, "onBonjourServiceAvailable "
										+ instanceName);
							} else {
                                Log.d(TAG, "?? no fragment to add device");
                            }
						}

					}
				}, new DnsSdTxtRecordListener() {

					/**
					 * A new TXT record is available. Pick up the advertised
					 * buddy name.
					 */
					@Override
					public void onDnsSdTxtRecordAvailable(
							String fullDomainName, Map<String, String> record,
							WifiP2pDevice device) {
                        Log.d(TAG, "TxtRecord");
						Log.d(TAG,
								device.deviceName
										+ " is "
										+ record.get(WiFiDropDnsService.TXTRECORD_PROP_AVAILABLE));
						String userName = record
								.get(WiFiDropDnsService.USER_NAME);
						Log.d(TAG, "user = " + userName);
						if (userName != null) {
							profiles.put(device.deviceName, userName);
						}
						Log.d(TAG, "recrod=" + record.toString());
					}
				});

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

	/**
	 * A placeholder fragment containing a simple view.
	 */
	public static class PlaceholderFragment extends Fragment {

		public PlaceholderFragment() {
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			View rootView = inflater.inflate(R.layout.fragment_wi_fi_drop,
					container, false);
			return rootView;
		}
	}

	@Override
	public void connectP2p(WiFiDropDnsService service) {
		WifiP2pConfig config = new WifiP2pConfig();
		config.deviceAddress = service.device.deviceAddress;
		// set least inclination to become Group owner for sender
		// to make receiver as a Group owner where Socket Server will be started
		config.groupOwnerIntent = 0;
		config.wps.setup = WpsInfo.PBC;
		if (serviceRequest != null) {
			manager.removeServiceRequest(channel, serviceRequest,
					new ActionListener() {

						@Override
						public void onSuccess() {
							Log.d(TAG, "Removed Service request.");
						}

						@Override
						public void onFailure(int arg0) {
							Log.d(TAG, "Failed to remove Service request.");
						}
					});
		}
        //Toast.makeText(this, "WiFi接続中", Toast.LENGTH_LONG).show();
        progress = ProgressDialog.show(this, TAG, "WiFi接続中", false, false);
		manager.connect(channel, config, new ActionListener() {

			@Override
			public void onSuccess() {

                appendStatus("Connecting to service");
			}

			@Override
			public void onFailure(int errorCode) {
				appendStatus("Failed connecting to service:" + errorCode);
			}
		});
	}

	@Override
	public void disconnectP2p() {
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

	@Override
	public void onConnectionInfoAvailable(WifiP2pInfo info) {
		// TODO Auto-generated method stub
		Thread handler = null;
		/*
		 * The group owner accepts connections using a server socket and then
		 * spawns a client socket for every client. This is handled by {@code
		 * GroupOwnerSocketHandler}
		 */

		Log.d(TAG, "groupFormed=" + info.groupFormed);
		Log.d(TAG, "isGroupOwner=" + info.isGroupOwner);
		Log.d(TAG, "addr=" + info.groupOwnerAddress);
		if (!info.groupFormed)
			return;

		if (!info.isGroupOwner) {
		    Log.d(TAG, "Connected as peer. Going to send an image file.");
		//Object[] array = {this.uri, info.groupOwnerAddress.getHostAddress(), Integer.valueOf(PORT)};
		//AsyncTask<Object, Void, Void> task = new FileTransferAsyncTask(this)
        //.execute(array);
            if (progress != null) {
                progress.dismiss();
            }
		    startFileTransferService(info);
		}
	}

	@Override
	public void onChannelDisconnected() {
		// TODO Auto-generated method stub
		if (manager != null) {
			Toast.makeText(this, "Channel lost. Trying again",
					Toast.LENGTH_LONG).show();

			channel = manager.initialize(this, getMainLooper(), this);
			unregisterReceiver(receiver);
			receiver = new WiFiDropBroadcastReceiver(manager, channel, this, "(Act)");
			registerReceiver(receiver, intentFilter);
		}
	}

	private void startFileTransferService(WifiP2pInfo info) {
		Log.d(TAG, "Intent----------- " + uri);
		Intent serviceIntent = new Intent(this, FileTransferService.class);
		serviceIntent.setAction(FileTransferService.ACTION_SEND_FILE);
		serviceIntent.putExtra(FileTransferService.EXTRAS_FILE_PATH,
				uri.toString());
        serviceIntent.putExtra(FileTransferService.EXTRAS_FILE_SIZE, size);
		serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_ADDRESS,
				info.groupOwnerAddress.getHostAddress());
		serviceIntent.putExtra(FileTransferService.EXTRAS_GROUP_OWNER_PORT,
				8988);
		this.startService(serviceIntent);
		finish();
	}


	public static boolean copyFile(InputStream inputStream, OutputStream out) {
		byte buf[] = new byte[1024];
        int count = 0;
		int len;
		try {
			while ((len = inputStream.read(buf)) != -1) {
				out.write(buf, 0, len);
                count += len;
                //Log.d(TAG, count + " bytes written");
			}
            out.flush();
			out.close();
			inputStream.close();
		} catch (IOException e) {
			Log.d(TAG, e.toString());
			return false;
		}
		return true;
	}

	public void onStartButtonClick(View view) {
		postNotification();
		Intent serviceIntent = new Intent(this, WiFiDropService.class);
		startService(serviceIntent);
		finish();
	}

	public void onStopButtonClick(View view) {
		cancelNotification();
		Intent serviceIntent = new Intent(this, WiFiDropService.class);
		stopService(serviceIntent);
		finish();
	}

	private void postNotification() {
		NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(
				this).setSmallIcon(R.drawable.ic_launcher)
                .setOngoing(true)
				.setContentTitle("WiFiDrop起動中")
				.setContentText("停止は、設定で行ってください。");
		// Creates an explicit intent for an Activity in your app
		Intent resultIntent = new Intent(this, WiFiDropActivity.class);

		// The stack builder object will contain an artificial back stack for
		// the
		// started Activity.
		// This ensures that navigating backward from the Activity leads out of
		// your application to the Home screen.
		TaskStackBuilder stackBuilder = TaskStackBuilder.create(this);
		// Adds the back stack for the Intent (but not the Intent itself)
		stackBuilder.addParentStack(WiFiDropActivity.class);
		// Adds the Intent that starts the Activity to the top of the stack
		stackBuilder.addNextIntent(resultIntent);
		PendingIntent resultPendingIntent = stackBuilder.getPendingIntent(0,
				PendingIntent.FLAG_UPDATE_CURRENT);
		mBuilder.setContentIntent(resultPendingIntent);
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		// mId allows you to update the notification later on.
		mNotificationManager.notify(mId, mBuilder.build());
	}

	private void cancelNotification() {
		NotificationManager mNotificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		// mId allows you to update the notification later on.
		mNotificationManager.cancel(mId);
	}

}
