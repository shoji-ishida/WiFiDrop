package com.example.wifidrop;

interface WiFiDropConnectionListener {
	public void connectP2p(WiFiDropDnsService wiFiDropService);
	public void disconnectP2p();
}
