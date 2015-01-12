package com.kdab.charm;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.Messenger;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

class TaskAdapter extends BaseAdapter {
    private final List<Task> mTasks = new ArrayList<>();
    private final Map<Long, Task> mTasksById = new HashMap<>();
    private final ServiceConnection mCharmServiceConnection;
    private final Context mContext;
    private final BroadcastReceiver mCharmServiceReceiver = new CharmBroadcastReceiver();

    private Messenger mServiceMessenger;
    private CharmClientCommunicator mServiceCommunicator;
    private int mRecentCount = 10;
    private boolean mOpened = false;
    private Timer mRefreshTimer;
    private String mHostname;
    private int mPort;

    public TaskAdapter(Context context) {
        mContext = context;

        mServiceMessenger = null;
        mCharmServiceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                mServiceMessenger = new Messenger(service);
                mServiceCommunicator = new CharmClientCommunicator(mServiceMessenger);
                mServiceCommunicator.connectionInformation(mHostname, mPort);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                if (mRefreshTimer != null) {
                    mRefreshTimer.cancel();
                    mRefreshTimer.purge();
                    mRefreshTimer = null;
                }
                mServiceCommunicator = null;
                mServiceMessenger = null;
            }
        };
    }

    @Override
    public int getCount() {
        return mTasks.size();
    }

    @Override
    public Object getItem(int position) {
        return mTasks.get(position);
    }

    @Override
    public long getItemId(int position) {
        return mTasks.get(position).id;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (convertView == null) {
            LayoutInflater inflater =
                    (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            view = inflater.inflate(R.layout.task_item_view, parent, false);
        }

        TextView taskId = (TextView) view.findViewById(R.id.task_id);
        TextView taskName = (TextView) view.findViewById(R.id.task_name);
        TextView taskRunning = (TextView) view.findViewById(R.id.task_running);

        Task task = mTasks.get(position);

        taskId.setText(String.format("%04d", task.id));
        taskName.setText(task.name);
        if (task.active)
            taskRunning.setText(String.format("%02d:%02d", task.seconds / 60, task.seconds % 60));
        else
            taskRunning.setText("");

        return view;
    }

    public void open() {
        if (mOpened)
            return;

        Intent service = new Intent(mContext, CharmClientService.class);
        mContext.bindService(service, mCharmServiceConnection, Context.BIND_AUTO_CREATE);

        LocalBroadcastManager.getInstance(mContext).registerReceiver(mCharmServiceReceiver,
                new IntentFilter(CharmClientService.CHARM_TASK_ACTIVATED_ACTION));
        LocalBroadcastManager.getInstance(mContext).registerReceiver(mCharmServiceReceiver,
                new IntentFilter(CharmClientService.CHARM_TASK_DEACTIVATED_ACTION));
        LocalBroadcastManager.getInstance(mContext).registerReceiver(mCharmServiceReceiver,
                new IntentFilter(CharmClientService.CHARM_RECENT_TASK_ACTION));
        LocalBroadcastManager.getInstance(mContext).registerReceiver(mCharmServiceReceiver,
                new IntentFilter(CharmClientService.CHARM_TASK_STATUS_ACTION));
        LocalBroadcastManager.getInstance(mContext).registerReceiver(mCharmServiceReceiver,
                new IntentFilter(CharmClientService.CHARM_CONNECTION_ESTABLISHED_ACTION));
        LocalBroadcastManager.getInstance(mContext).registerReceiver(mCharmServiceReceiver,
                new IntentFilter(CharmClientService.CHARM_CONNECTION_CLOSED_ACTION));
        LocalBroadcastManager.getInstance(mContext).registerReceiver(mCharmServiceReceiver,
                new IntentFilter(CharmClientService.CHARM_CONNECTION_DISCOVERED_ACTION));

        mOpened = true;
    }

    public void close() {
        if (!mOpened)
            return;

        LocalBroadcastManager.getInstance(mContext).unregisterReceiver(mCharmServiceReceiver);
        mContext.unbindService(mCharmServiceConnection);

        cleanConnection();

        mOpened = false;
    }

    public boolean isOpen() {
        return mOpened;
    }

    public Messenger getServiceMessenger() {
        return mServiceMessenger;
    }

    public void setRecentCount(int count) {
        this.mRecentCount = count;

        if (isOpen()) {
            close();
            open();
        }
    }

    public void setHostname(String hostname) {
        mHostname = hostname;

        if (isOpen())
            mServiceCommunicator.connectionInformation(mHostname, mPort);
    }

    public void setPort(int port) {
        mPort = port;

        if (isOpen())
            mServiceCommunicator.connectionInformation(mHostname, mPort);
    }

    private void prepareConnection() {
        mServiceCommunicator.recent(0, mRecentCount);

        mRefreshTimer = new Timer();
        mRefreshTimer.scheduleAtFixedRate(new RefreshTimerTask(), 0, 10000);
    }

    private void cleanConnection() {
        if (mRefreshTimer != null) {
            mRefreshTimer.cancel();
            mRefreshTimer.purge();
            mRefreshTimer = null;
        }

        mTasks.clear();
        mTasksById.clear();

        notifyDataSetInvalidated();
    }

    private class RefreshTimerTask extends TimerTask {
        @Override
        public void run() {
            mServiceCommunicator.status();
        }
    }

    private class CharmBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(CharmClientService.CHARM_TASK_ACTIVATED_ACTION)) {
                Task task;

                long task_id = intent.getLongExtra(CharmClientService.CHARM_TASK_ID, 0);

                if (mTasksById.containsKey(task_id))
                    task = mTasksById.get(task_id);
                else {
                    task = new Task(task_id, intent.getStringExtra(CharmClientService.CHARM_TASK_NAME));
                    mTasksById.put(task_id, task);
                }

                mTasks.remove(task);
                task.active = true;
                task.seconds = 0;
                mTasks.add(0, task);

                notifyDataSetChanged();
            } else if (intent.getAction().equals(CharmClientService.CHARM_TASK_DEACTIVATED_ACTION)) {
                Task task;

                long task_id = intent.getLongExtra(CharmClientService.CHARM_TASK_ID, 0);

                if (!mTasksById.containsKey(task_id))
                    return;

                task = mTasksById.get(task_id);
                task.active = false;
                task.seconds = 0;

                notifyDataSetChanged();
            } else if (intent.getAction().equals(CharmClientService.CHARM_RECENT_TASK_ACTION)) {
                Task task;

                long task_id = intent.getLongExtra(CharmClientService.CHARM_TASK_ID, 0);

                if (mTasksById.containsKey(task_id))
                    task = mTasksById.get(task_id);
                else {
                    task = new Task(task_id, intent.getStringExtra(CharmClientService.CHARM_TASK_NAME));
                    mTasksById.put(task_id, task);
                }

                mTasks.remove(task);
                mTasks.add(task);
                notifyDataSetChanged();
            } else if (intent.getAction().equals(CharmClientService.CHARM_TASK_STATUS_ACTION)) {
                Task task;

                long task_id = intent.getLongExtra(CharmClientService.CHARM_TASK_ID, 0);

                if (!mTasksById.containsKey(task_id))
                    return;

                task = mTasksById.get(task_id);
                task.active = true;
                task.seconds = intent.getIntExtra(CharmClientService.CHARM_EVENT_DURATION, 0);

                notifyDataSetChanged();
            } else if (intent.getAction().equals(CharmClientService.CHARM_CONNECTION_ESTABLISHED_ACTION)) {
                prepareConnection();
            } else if (intent.getAction().equals(CharmClientService.CHARM_CONNECTION_CLOSED_ACTION)) {
                cleanConnection();
            }
        }
    }
}
