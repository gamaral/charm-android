package com.kdab.charm;

import android.os.Bundle;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

class CharmClientCommunicator {
    private final Messenger mMessenger;

    CharmClientCommunicator(Messenger messenger) {
        super();
        mMessenger = messenger;
    }

    void connectionInformation(String hostname, int port) {
        Message msg = Message.obtain(null, CharmClientService.CHARM_CONNECTION_INFORMATION_MSG, port, 0);

        Bundle bundle = new Bundle();
        bundle.putString(CharmClientService.CHARM_CONNECTION_HOSTNAME, hostname);
        msg.setData(bundle);

        try {
            mMessenger.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    void start(long task) {
        Message msg = Message.obtain(null, CharmClientService.CHARM_START_MSG, (int) task, 0);

        try {
            mMessenger.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    void stop(long task) {
        Message msg = Message.obtain(null, CharmClientService.CHARM_STOP_MSG, (int) task, 0);

        try {
            mMessenger.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    void recent(int index, int count) {
        Message msg = Message.obtain(null, CharmClientService.CHARM_RECENT_MSG, index, count);

        try {
            mMessenger.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }

    void status() {
        Message msg = Message.obtain(null, CharmClientService.CHARM_STATUS_MSG);

        try {
            mMessenger.send(msg);
        } catch (RemoteException e) {
            e.printStackTrace();
        }
    }
}
