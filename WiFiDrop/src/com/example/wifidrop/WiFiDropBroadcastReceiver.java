
package com.example.wifidrop;

/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


import java.util.Collection;
import java.util.Iterator;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;
import android.util.Log;

/**
 * A BroadcastReceiver that notifies of important wifi p2p events.
 */
public class WiFiDropBroadcastReceiver extends BroadcastReceiver implements PeerListListener {

	private static final String TAG = WiFiDropActivity.TAG + "(rec)";
    private WifiP2pManager manager;
    private Channel channel;
    private Context context;
    private static String SIDE = "";

    /**
     * @param manager WifiP2pManager system service
     * @param channel Wifi p2p channel
     */
    public WiFiDropBroadcastReceiver(WifiP2pManager manager, Channel channel,
            Context context, String side) {
        super();
        this.manager = manager;
        this.channel = channel;
        this.context = context;
        SIDE = side;
    }

    /*
     * (non-Javadoc)
     * @see android.content.BroadcastReceiver#onReceive(android.content.Context,
     * android.content.Intent)
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        Log.d(TAG+SIDE, action);
        if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {

            if (manager == null) {
                return;
            }

            NetworkInfo networkInfo = (NetworkInfo) intent
                    .getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);

            if (networkInfo.isConnected()) {

                // we are connected with the other device, request connection
                // info to find group owner IP
                Log.d(TAG+SIDE,
                        "Connected to p2p network. Requesting network details");
                manager.requestConnectionInfo(channel,
                        (ConnectionInfoListener) context);
            } else {
                // It's a disconnect
            	Log.d(TAG+SIDE,
                        "Disconnected from p2p network.");
            	manager.requestConnectionInfo(channel,
                        (ConnectionInfoListener) context);
            }
        } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION
                .equals(action)) {

            WifiP2pDevice device = (WifiP2pDevice) intent
                    .getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
            Log.d(TAG+SIDE, "Device status = " + WiFiDropDnsServicesList.getDeviceStatus(device.status));

        } else if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
        	int state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1);
        	String stateMsg = (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) ? "Enabled" : "Disabled";
        	Log.d(TAG+SIDE, "P2P state changed - " + stateMsg);
        } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
            WifiP2pDeviceList deviceList = (WifiP2pDeviceList) intent
                    .getParcelableExtra(WifiP2pManager.EXTRA_P2P_DEVICE_LIST);
            Collection<WifiP2pDevice> devices = deviceList.getDeviceList();
            for (WifiP2pDevice device: devices) {
                Log.d(TAG+SIDE, "P2P Device: " + device.deviceName + " " + WiFiDropDnsServicesList.getDeviceStatus(device.status));
            }
        } else if (WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION.equals(action)) {
            int state = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE, -1);
            if (state == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED) {
                Log.d(TAG+SIDE, "P2P discovery started");
            } else if (state == WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED) {
                Log.d(TAG+SIDE, "P2P discovery stopped");
            } else {
                Log.d(TAG+SIDE, "P2P discovery unknown state");
            }
        }
    }

	@Override
	public void onPeersAvailable(WifiP2pDeviceList peers) {
		// TODO Auto-generated method stub
		Collection<WifiP2pDevice> devices = peers.getDeviceList();
		
		for (Iterator<WifiP2pDevice> i=devices.iterator(); i.hasNext();) {
			WifiP2pDevice device = (WifiP2pDevice) i.next();
			Log.d(TAG+SIDE, device.toString());
		}
	}
}
