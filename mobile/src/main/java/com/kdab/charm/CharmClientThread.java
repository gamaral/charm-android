package com.kdab.charm;

import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

class CharmClientThread extends Thread {
    public static final int CHARM_TASK_ACTIVATED_MSG         = 1;
    public static final int CHARM_TASK_DEACTIVATED_MSG       = 2;
    public static final int CHARM_TASK_RECENT_MSG            = 3;
    public static final int CHARM_TASK_STATUS_MSG            = 4;
    public static final int CHARM_CONNECTION_ESTABLISHED_MSG = 5;
    public static final int CHARM_CONNECTION_CLOSED_MSG      = 6;
    public static final int CHARM_CONNECTION_LOST_MSG        = 7;
    public static final int CHARM_CONNECTION_DISCOVERY_MSG   = 8;
    public static final int CHARM_CONNECTION_DISCOVERED_MSG  = 9;

    private static final int DISCONNECTED_STATE = 0;
    private static final int HANDSHAKE_STATE    = 1;
    private static final int COMMAND_STATE      = 2;

    private int mState = DISCONNECTED_STATE;
    private final BlockingQueue<Message> mWork = new ArrayBlockingQueue<>(20);
    private Handler mThreadToService;
    private String mHostname = "localhost";
    private Integer mPort = 5323;
    private Socket mSocket;
    private InputStreamReader mReader;
    private OutputStreamWriter mWriter;

    public void setConnectionInformation(String hostname, int port) {
        mHostname = hostname;
        mPort = port;
        setState(DISCONNECTED_STATE);
    }

    public void setServiceHandler(Handler handler) {
        mThreadToService = handler;
    }

    public void post(Message message) {
        if (!isAlive() || isInterrupted())
            return;

        Message copy = new Message();
        copy.copyFrom(message);
        mWork.add(copy);
    }

    @Override
    public void run() {
        Log.d("CHARM", "Running thread...");
        do {
            switch (mState) {
                case DISCONNECTED_STATE:
                    runInDisconnectedState();
                    break;

                case HANDSHAKE_STATE:
                    try {
                        runInHandshakeState();
                    } catch (ClientException e) {
                        setState(DISCONNECTED_STATE);
                        notifyService(CHARM_CONNECTION_LOST_MSG);
                    }
                    break;

                case COMMAND_STATE:
                    try {
                        runInCommandState();
                    } catch (ClientException e) {
                        setState(DISCONNECTED_STATE);
                        notifyService(CHARM_CONNECTION_LOST_MSG);
                    }
                    break;
            }
        }
        while (!Thread.currentThread().isInterrupted());

        setState(DISCONNECTED_STATE);
    }

    private boolean discoverConnection() {
        byte buffer[] = new byte[16];

        DatagramSocket discovery;

        try {
            discovery = new DatagramSocket(mPort);
            discovery.setBroadcast(true);
        } catch (SocketException e) {
            return false;
        }

        notifyService(CHARM_CONNECTION_DISCOVERY_MSG);

        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        try {
            discovery.setSoTimeout(3000);
            discovery.receive(packet);
        } catch (IOException e) {
            discovery.close();
            return false;
        }

        discovery.close();

        mHostname = packet.getAddress().getCanonicalHostName();

        Message msg = mThreadToService.obtainMessage(CHARM_CONNECTION_DISCOVERED_MSG);
        Bundle data = new Bundle();
        data.putString(CharmClientService.CHARM_CONNECTION_HOSTNAME, mHostname);
        msg.setData(data);

        mThreadToService.sendMessage(msg);

        return true;
    }

    private boolean setupConnection() {
        try {
            mSocket = new Socket(mHostname, mPort);
        } catch (IOException e) {
            mSocket = null;
            return false;
        }

        try {
            mReader = new InputStreamReader(mSocket.getInputStream());
        } catch (IOException e) {
            cleanupConnection();
            return false;
        }

        try {
            mWriter = new OutputStreamWriter(mSocket.getOutputStream());
        } catch (IOException e) {
            cleanupConnection();
            return false;
        }

        setState(HANDSHAKE_STATE);

        return mSocket.isConnected();
    }

    private void cleanupConnection() {
        if (mSocket == null)
            return;

        try {
            mWriter.write("BYE\n");
            mWriter.flush();
            mWriter.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mWriter = null;

        try {
            mReader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        mReader = null;

        if (mSocket.isConnected()) {
            try {
                mSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        setState(DISCONNECTED_STATE);
    }

    private void setState(int state) {
        if (mState == state)
            return;

        Log.d("CHARM", String.format("Charm client changing state from %d to %d.", mState, state));

        switch (mState) {
            case HANDSHAKE_STATE:
                if (state == COMMAND_STATE)
                    notifyService(CHARM_CONNECTION_ESTABLISHED_MSG);
                break;
            case COMMAND_STATE:
                notifyService(CHARM_CONNECTION_CLOSED_MSG);
                break;
        }

        mState = state;

        switch (mState) {
            case DISCONNECTED_STATE:
                cleanupConnection();
                break;
        }
    }

    private void notifyService(int what) {
        mThreadToService.sendEmptyMessage(what);
    }

    private void runInDisconnectedState() {
        int interval = 500;
        do {
            if (setupConnection())
                break;
            else discoverConnection();

            Log.d("CHARM", String.format("Failed to connect. Retrying in %d milliseconds.", interval));

            try {
                sleep(interval);
            } catch (InterruptedException e) {
                currentThread().interrupt();
                return;
            }

            interval += 500;
        } while (mState == DISCONNECTED_STATE && !isInterrupted());
    }

    private void runInHandshakeState() throws ClientException {
        String[] lines;
        char[] buffer;
        int read;

        buffer = new char[512];

        do {
            try {
                read = mReader.read(buffer);
            } catch (IOException e) {
                throw new ClientException();
            }

            if (read == -1)
                throw new ClientException();

            lines = new String(buffer, 0, read).split("\n");
            for (String line : lines) {
                /* Session Start */
                if (line.startsWith("HELLO")) {
                    try {
                        mWriter.write("READY\n");
                        mWriter.flush();
                    } catch (IOException e) {
                        throw new ClientException();
                    }

                    try {
                        read = mReader.read(buffer);
                    } catch (IOException e) {
                        throw new ClientException();
                    }

                    if (read == -1)
                        throw new ClientException();

                    String answer = new String(buffer, 0, read);

                    if (answer.startsWith("ACK")) {
                        Log.d("CHARM", "Received handshake from Charm, starting command session...");
                        setState(COMMAND_STATE);
                    }
                }
            }
        } while (mState == HANDSHAKE_STATE && !isInterrupted());
    }

    private void runInCommandState() throws ClientException {
        Message msg;
        String[] lines;
        char[] buffer;
        int read;
        boolean canRead;

        buffer = new char[2048];

        do {
            try {
                canRead = mReader.ready();
            } catch (IOException e) {
                throw new ClientException();
            }

            if (canRead) {
                try {
                    read = mReader.read(buffer);
                } catch (IOException e) {
                    throw new ClientException();
                }

                if (read == -1)
                    throw new ClientException();

                lines = new String(buffer, 0, read).split("\n");

                for (String line : lines) {
                    /* Task Activated */
                    if (line.startsWith("TASK ACTIVATED")) {
                        Integer task_id;
                        try {
                            task_id = Integer.parseInt(line.substring(15, 19));
                        } catch (NumberFormatException e) {
                            Log.d("CHARM", "Unable to parse: " + line);
                            continue;
                        }

                        msg = mThreadToService.obtainMessage(CHARM_TASK_ACTIVATED_MSG, task_id, 0);
                        Bundle data = new Bundle();
                        data.putString(CharmClientService.CHARM_TASK_NAME, line.substring(20));
                        msg.setData(data);

                        mThreadToService.sendMessage(msg);
                    }

                    /* Task Deactivated */
                    else if (line.startsWith("TASK DEACTIVATED")) {
                        Integer task_id;
                        try {
                            task_id = Integer.parseInt(line.substring(17, 21));
                        } catch (NumberFormatException e) {
                            Log.d("CHARM", "Unable to parse: " + line);
                            continue;
                        }

                        msg = mThreadToService.obtainMessage(CHARM_TASK_DEACTIVATED_MSG, task_id, 0);
                        Bundle data = new Bundle();
                        data.putString(CharmClientService.CHARM_TASK_NAME, line.substring(22));
                        msg.setData(data);

                        mThreadToService.sendMessage(msg);
                    }
                }
            }

            try {
                msg = mWork.poll(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                currentThread().interrupt();
                return;
            }

            if (msg == null)
                continue;

            switch (msg.what) {
                case CharmClientService.CHARM_START_MSG:
                    try {
                        mWriter.write(String.format("START %d\n", msg.arg1));
                        mWriter.flush();
                    } catch (IOException e) {
                        throw new ClientException();
                    }
                    break;

                case CharmClientService.CHARM_STOP_MSG:
                    try {
                        mWriter.write(String.format("STOP %d\n", msg.arg1));
                        mWriter.flush();
                    } catch (IOException e) {
                        throw new ClientException();
                    }
                    break;

                case CharmClientService.CHARM_RECENT_MSG: {
                    try {
                        mWriter.write(String.format("RECENT %d %d\n", msg.arg1, msg.arg2));
                        mWriter.flush();
                    } catch (IOException e) {
                        throw new ClientException();
                    }

                    try {
                        read = mReader.read(buffer);
                    } catch (IOException e) {
                        throw new ClientException();
                    }

                    if (read == -1)
                        throw new ClientException();

                    String[] tasks = new String(buffer, 0, read).split("\n");
                    if (tasks.length == 1 && tasks[0].startsWith("NAK"))
                        continue;

                    for (int i = 0; i < tasks.length; ++i) {
                        String task = tasks[i];

                        Integer task_id;
                        try {
                            task_id = Integer.parseInt(task.substring(0, 4));
                        } catch (NumberFormatException e) {
                            Log.d("CHARM", "Unable to parse: " + task);
                            continue;
                        }

                        msg = mThreadToService.obtainMessage(CHARM_TASK_RECENT_MSG, task_id, i);
                        Bundle data = new Bundle();
                        data.putString(CharmClientService.CHARM_TASK_NAME, task.substring(5));
                        msg.setData(data);

                        mThreadToService.sendMessage(msg);
                    }
                }
                break;

                case CharmClientService.CHARM_STATUS_MSG: {
                    try {
                        mWriter.write("STATUS\n");
                        mWriter.flush();
                    } catch (IOException e) {
                        throw new ClientException();
                    }

                    try {
                        read = mReader.read(buffer);
                    } catch (IOException e) {
                        throw new ClientException();
                    }

                    if (read == -1)
                        throw new ClientException();

                    String[] tasks = new String(buffer, 0, read).split("\n");
                    if (tasks.length == 1 && tasks[0].startsWith("NAK"))
                        continue;

                    for (String task : tasks) {
                        Integer task_id;
                        Integer task_seconds;

                        try {
                            task_id = Integer.parseInt(task.substring(0, 4));
                            task_seconds = Integer.parseInt(task.substring(5));
                        } catch (NumberFormatException e) {
                            Log.d("CHARM", "Unable to parse: " + task);
                            continue;
                        }

                        msg = mThreadToService.obtainMessage(CHARM_TASK_STATUS_MSG, task_id, task_seconds);
                        mThreadToService.sendMessage(msg);
                    }
                }
                break;
            }
        }
        while (mState == COMMAND_STATE && !isInterrupted());
    }

    private class ClientException extends Exception {
    }
}
