
/***************************************************************************
 *   Copyright 2006-2016 by Christian Ihle                                 *
 *   contact@kouchat.net                                                   *
 *                                                                         *
 *   This file is part of KouChat.                                         *
 *                                                                         *
 *   KouChat is free software; you can redistribute it and/or modify       *
 *   it under the terms of the GNU Lesser General Public License as        *
 *   published by the Free Software Foundation, either version 3 of        *
 *   the License, or (at your option) any later version.                   *
 *                                                                         *
 *   KouChat is distributed in the hope that it will be useful,            *
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of        *
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU      *
 *   Lesser General Public License for more details.                       *
 *                                                                         *
 *   You should have received a copy of the GNU Lesser General Public      *
 *   License along with KouChat.                                           *
 *   If not, see <http://www.gnu.org/licenses/>.                           *
 ***************************************************************************/

package org.dynamicsoft.VertoChat.android.service;

import org.dynamicsoft.VertoChat.android.chatwindow.AndroidUserInterface;
import org.dynamicsoft.VertoChat.android.notification.NotificationService;
import org.dynamicsoft.VertoChat.android.notification.ServiceNotificationService;
import org.dynamicsoft.VertoChat.android.settings.AndroidSettings;
import org.dynamicsoft.VertoChat.android.settings.AndroidSettingsLoader;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;

/**
 * Service for accessing the chat operations in lower layers.
 * <p>
 * <p>Starts as a foreground service to avoid being killed randomly.
 * This means a notification is available at all times.</p>
 *
 * @author Christian Ihle
 */
public class ChatService extends Service {

    private AndroidUserInterface androidUserInterface;
    private NotificationService notificationService;
    private LockHandler lockHandler;
    private ChatServiceBinder chatServiceBinder;

    /**
     * To avoid logging on more than once, which will throw an exception.
     */
    private boolean started;

    @Override
    public void onCreate() {
        final AndroidSettings settings = new AndroidSettings();
        final AndroidSettingsLoader androidSettingsLoader = new AndroidSettingsLoader();
        androidSettingsLoader.loadStoredSettings(this, settings);

        notificationService = new NotificationService(this, settings);
        androidUserInterface = new AndroidUserInterface(this, settings, notificationService);

        final WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        final PowerManager powerManager = (PowerManager) this.getSystemService(POWER_SERVICE);
        lockHandler = new LockHandler(androidUserInterface, settings, wifiManager, powerManager);

        chatServiceBinder = new ChatServiceBinder(androidUserInterface);

        super.onCreate();
    }

    /**
     * This method may execute every time the main chat is shown.
     */
    @Override
    public void onStart(final Intent intent, final int startId) {
        if (!started) {
            started = true;
            androidUserInterface.logOn();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager mNotificationManager =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel = new NotificationChannel("default",
                    "Message notifications",
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Message Notification");
            mNotificationManager.createNotificationChannel(channel);
        } else {
            startForeground(ServiceNotificationService.SERVICE_NOTIFICATION_ID, notificationService.createServiceNotification());
        }

        super.onStart(intent, startId);
    }

    @Override
    public IBinder onBind(final Intent intent) {
        return chatServiceBinder;
    }

    @Override
    public void onDestroy() {
        androidUserInterface.logOff();
        notificationService.onDestroy();
        lockHandler.releaseAllLocks();
        chatServiceBinder.onDestroy();

        androidUserInterface = null;
        notificationService = null;
        lockHandler = null;
        chatServiceBinder = null;

        super.onDestroy();
    }
}
