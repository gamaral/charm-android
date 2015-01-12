package com.kdab.charm;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

public class CharmClientService extends Service {
    public static final String CHARM_TASK_ACTIVATED_ACTION         = "com.kdab.charm.TASK_ACTIVATED";
    public static final String CHARM_TASK_DEACTIVATED_ACTION       = "com.kdab.charm.TASK_DEACTIVATED";
    public static final String CHARM_TASK_STATUS_ACTION            = "com.kdab.charm.TASK_STATUS";
    public static final String CHARM_RECENT_TASK_ACTION            = "com.kdab.charm.RECENT_TASK";
    public static final String CHARM_CONNECTION_ESTABLISHED_ACTION = "com.kdab.charm.CONNECTION_ESTABLISHED";
    public static final String CHARM_CONNECTION_CLOSED_ACTION      = "com.kdab.charm.CONNECTION_CLOSED";
    public static final String CHARM_CONNECTION_LOST_ACTION        = "com.kdab.charm.CONNECTION_LOST";
    public static final String CHARM_CONNECTION_DISCOVERED_ACTION  = "com.kdab.charm.CONNECTION_DISCOVERED";

    public static final int CHARM_CONNECTION_INFORMATION_MSG = 0;
    public static final int CHARM_START_MSG                  = 1;
    public static final int CHARM_STOP_MSG                   = 2;
    public static final int CHARM_RECENT_MSG                 = 3;
    public static final int CHARM_STATUS_MSG                 = 4;

    public static final String CHARM_TASK_ID             = "task_id";
    public static final String CHARM_TASK_NAME           = "task_name";
    public static final String CHARM_EVENT_DURATION      = "event_duration";
    public static final String CHARM_RECENT_TASK_INDEX   = "recent_task_index";
    public static final String CHARM_CONNECTION_HOSTNAME = "connection_hostname";

    private final Handler mThreadToServiceHandler = new ThreadToServiceHandler();
    private CharmClientThread mThread;
    private Messenger mMessenger;

    @Override
    public void onCreate() {
        super.onCreate();

        mMessenger = new Messenger(new ActivityToServiceHandler());
    }

    @Override
    public void onDestroy() {
        cleanupThread();
        mMessenger = null;

        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger.getBinder();
    }

    private void cleanupThread(){
        if (mThread == null)
            return;

        mThread.interrupt();

        try {
            mThread.join();
            Log.d("CHARM", "Joining Thread");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        mThread = null;
    }

    private class ThreadToServiceHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            Intent intent;

            switch (msg.what) {
                case CharmClientThread.CHARM_TASK_ACTIVATED_MSG:
                    intent = new Intent(CHARM_TASK_ACTIVATED_ACTION);
                    intent.putExtra(CHARM_TASK_ID, (long) msg.arg1);
                    intent.putExtra(CHARM_TASK_NAME, msg.getData().getString(CHARM_TASK_NAME));
                    LocalBroadcastManager.getInstance(getBaseContext()).sendBroadcast(intent);
                    break;
                case CharmClientThread.CHARM_TASK_DEACTIVATED_MSG:
                    intent = new Intent(CHARM_TASK_DEACTIVATED_ACTION);
                    intent.putExtra(CHARM_TASK_ID, (long) msg.arg1);
                    intent.putExtra(CHARM_TASK_NAME, msg.getData().getString(CHARM_TASK_NAME));
                    LocalBroadcastManager.getInstance(getBaseContext()).sendBroadcast(intent);
                    break;
                case CharmClientThread.CHARM_TASK_RECENT_MSG:
                    intent = new Intent(CHARM_RECENT_TASK_ACTION);
                    intent.putExtra(CHARM_TASK_ID, (long) msg.arg1);
                    intent.putExtra(CHARM_RECENT_TASK_INDEX, msg.arg2);
                    intent.putExtra(CHARM_TASK_NAME, msg.getData().getString(CHARM_TASK_NAME));
                    LocalBroadcastManager.getInstance(getBaseContext()).sendBroadcast(intent);
                    break;
                case CharmClientThread.CHARM_TASK_STATUS_MSG:
                    intent = new Intent(CHARM_TASK_STATUS_ACTION);
                    intent.putExtra(CHARM_TASK_ID, (long) msg.arg1);
                    intent.putExtra(CHARM_EVENT_DURATION, msg.arg2);
                    LocalBroadcastManager.getInstance(getBaseContext()).sendBroadcast(intent);
                    break;
                case CharmClientThread.CHARM_CONNECTION_ESTABLISHED_MSG:
                    intent = new Intent(CHARM_CONNECTION_ESTABLISHED_ACTION);
                    LocalBroadcastManager.getInstance(getBaseContext()).sendBroadcast(intent);
                    break;
                case CharmClientThread.CHARM_CONNECTION_CLOSED_MSG:
                    intent = new Intent(CHARM_CONNECTION_CLOSED_ACTION);
                    LocalBroadcastManager.getInstance(getBaseContext()).sendBroadcast(intent);
                    break;
                case CharmClientThread.CHARM_CONNECTION_LOST_MSG:
                    Toast.makeText(getBaseContext(), "Connection Lost", Toast.LENGTH_LONG).show();
                    intent = new Intent(CHARM_CONNECTION_LOST_ACTION);
                    LocalBroadcastManager.getInstance(getBaseContext()).sendBroadcast(intent);
                    break;
                case CharmClientThread.CHARM_CONNECTION_DISCOVERY_MSG:
                    Toast.makeText(getBaseContext(), "Searching for Charm...", Toast.LENGTH_SHORT).show();
                    break;
                case CharmClientThread.CHARM_CONNECTION_DISCOVERED_MSG:
                    intent = new Intent(CHARM_CONNECTION_DISCOVERED_ACTION);
                    intent.putExtra(CHARM_CONNECTION_HOSTNAME, msg.getData().getString(CHARM_CONNECTION_HOSTNAME));
                    LocalBroadcastManager.getInstance(getBaseContext()).sendBroadcast(intent);
                    break;
            }
        }
    }

    private class ActivityToServiceHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what == CHARM_CONNECTION_INFORMATION_MSG) {
                if (mThread != null)
                    cleanupThread();

                mThread = new CharmClientThread();
                mThread.setServiceHandler(mThreadToServiceHandler);
                mThread.setConnectionInformation(msg.getData().
                        getString(CHARM_CONNECTION_HOSTNAME), msg.arg1);
                mThread.start();
            }
            else if (mThread != null)
                mThread.post(msg);
        }
    }
}
