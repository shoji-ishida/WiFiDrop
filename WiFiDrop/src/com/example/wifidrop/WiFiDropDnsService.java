
package com.example.wifidrop;

import android.net.wifi.p2p.WifiP2pDevice;

/**
 * A structure to hold service information.
 */
public class WiFiDropDnsService {
    public static final String TXTRECORD_PROP_AVAILABLE = "available";
    public static final String USER_NAME = "userName";
    public static final String SERVICE_INSTANCE = "WiFiDrop";
    public static final String SERVICE_REG_TYPE = "_wifidrop._tcp";
	
    WifiP2pDevice device;
    String instanceName = null;
    String serviceRegistrationType = null;
    String userName = "No name";
}
