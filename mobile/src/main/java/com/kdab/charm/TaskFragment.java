package com.kdab.charm;

import android.app.Activity;
import android.app.ListFragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Messenger;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.view.View;
import android.widget.ListView;

public class TaskFragment extends ListFragment {
    private final BroadcastReceiver mCharmServiceReceiver = new CharmBroadcastReceiver();
    private final SharedPreferences.OnSharedPreferenceChangeListener mPreferenceChangeListener =
            new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            TaskAdapter adapter = (TaskAdapter) getListAdapter();

            switch (key) {
                case SettingsActivity.CHARM_CONNECTION_HOSTNAME:
                    adapter.setHostname(sharedPreferences.getString(key, "localhost"));
                    break;
                case SettingsActivity.CHARM_CONNECTION_PORT:
                    adapter.setPort(Integer.parseInt(sharedPreferences.getString(key, "5323")));
                    break;
                case SettingsActivity.CHARM_RECENT_COUNT:
                    adapter.setRecentCount(Integer.parseInt(sharedPreferences.getString(key, "10")));
                    break;
            }
        }
    };

    private OnTaskClickedListener mTaskClickedListener;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TaskAdapter adapter = new TaskAdapter(getActivity());
        setListAdapter(adapter);

        SharedPreferences sharedPref = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext());
        mPreferenceChangeListener.onSharedPreferenceChanged(sharedPref, SettingsActivity.CHARM_CONNECTION_HOSTNAME);
        mPreferenceChangeListener.onSharedPreferenceChanged(sharedPref, SettingsActivity.CHARM_CONNECTION_PORT);
        mPreferenceChangeListener.onSharedPreferenceChanged(sharedPref, SettingsActivity.CHARM_RECENT_COUNT);

        adapter.open();

        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mCharmServiceReceiver,
                new IntentFilter(CharmClientService.CHARM_CONNECTION_DISCOVERED_ACTION));

        PreferenceManager.getDefaultSharedPreferences(getActivity()).
                registerOnSharedPreferenceChangeListener(mPreferenceChangeListener);
    }

    @Override
    public void onDestroy() {
        PreferenceManager.getDefaultSharedPreferences(getActivity()).
                unregisterOnSharedPreferenceChangeListener(mPreferenceChangeListener);

        TaskAdapter adapter = (TaskAdapter) getListAdapter();
        adapter.close();

        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mCharmServiceReceiver);

        super.onDestroy();
    }

    @Override
    public void onResume() {
        TaskAdapter adapter = (TaskAdapter) getListAdapter();
        adapter.open();

        PreferenceManager.getDefaultSharedPreferences(getActivity()).
                registerOnSharedPreferenceChangeListener(mPreferenceChangeListener);

        super.onResume();
    }

    @Override
    public void onPause() {
        PreferenceManager.getDefaultSharedPreferences(getActivity()).
                unregisterOnSharedPreferenceChangeListener(mPreferenceChangeListener);

        TaskAdapter adapter = (TaskAdapter) getListAdapter();
        adapter.close();

        super.onPause();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        try {
            mTaskClickedListener = (OnTaskClickedListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnTaskClickedListener");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mTaskClickedListener = null;
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        if (null != mTaskClickedListener) {
            mTaskClickedListener.onTaskClicked((Task) getListAdapter().getItem(position));
        }
    }

    public Messenger getServiceMessenger() {
        TaskAdapter adapter = (TaskAdapter) getListAdapter();
        return adapter.getServiceMessenger();
    }

    public interface OnTaskClickedListener {
        public void onTaskClicked(Task task);
    }

    private class CharmBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(CharmClientService.CHARM_CONNECTION_DISCOVERED_ACTION)) {
                String hostname = intent.getStringExtra(CharmClientService.CHARM_CONNECTION_HOSTNAME);
                SharedPreferences.Editor sharedPref = PreferenceManager.getDefaultSharedPreferences(getActivity().getApplicationContext()).edit();
                sharedPref.putString(SettingsActivity.CHARM_CONNECTION_HOSTNAME, hostname);
                sharedPref.apply();
            }
        }
    }
}
