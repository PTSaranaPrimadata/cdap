/*
 * Copyright (c) 2012, Continuuity Inc. All rights reserved.
 */

package com.continuuity.gateway;

/**
 * Created by: andreas, having fun since April 2012
 */
public abstract class FlumeConnector extends Connector {

	public static final String DefaultHost = "localhost";
	public static final int DefaultPort = 8765;

	protected int port = DefaultPort;
	protected String host = DefaultHost;

	protected FlumeAdapter flumeAdapter;

	@Override
	public void setConsumer(Consumer consumer) {
		super.setConsumer(consumer);
		if (this.flumeAdapter == null) {
			this.flumeAdapter = new FlumeAdapter();
		}
		this.flumeAdapter.setConsumer(consumer);
	}

	@Override
	public Consumer getConsumer() {
		if (this.flumeAdapter == null) return null;
		return this.flumeAdapter.getConsumer();
	}

	public void setPort(int port) {
		this.port = port;
	}
	public void setHost(String host) {
		this.host = host;
	}
	protected String getAddress() {
		return this.host + ":" + this.port;
	}
}
