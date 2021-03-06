package com.qiscus.qiscusmultichannel

import android.app.Application
import android.content.Context
import android.content.Intent
import com.google.firebase.messaging.RemoteMessage
import com.qiscus.jupuk.Jupuk
import com.qiscus.nirmana.Nirmana
import com.qiscus.qiscusmultichannel.data.model.UserProperties
import com.qiscus.qiscusmultichannel.ui.chat.ChatRoomActivity
import com.qiscus.qiscusmultichannel.ui.loading.LoadingActivity
import com.qiscus.qiscusmultichannel.util.PNUtil
import com.qiscus.qiscusmultichannel.util.QiscusChatLocal
import com.qiscus.sdk.chat.core.custom.QiscusCore
import com.qiscus.sdk.chat.core.custom.data.model.*
import com.qiscus.sdk.chat.core.custom.data.remote.QiscusApi
import com.qiscus.sdk.chat.core.custom.util.QiscusFirebaseMessagingUtil
import org.json.JSONObject
import rx.android.schedulers.AndroidSchedulers
import rx.schedulers.Schedulers

/**
 * Created on : 05/08/19
 * Author     : Taufik Budi S
 * GitHub     : https://github.com/tfkbudi
 */
class MultichannelWidget constructor(val component: MultichannelWidgetComponent) {

    companion object {

        @Volatile
        private var INSTANCE: MultichannelWidget? = null

        var config: MultichannelWidgetConfig = MultichannelWidgetConfig

        lateinit var application: Application
        private var appId: String = ""

        @JvmStatic
        val instance: MultichannelWidget
            get() {
                if (INSTANCE == null) {
                    synchronized(MultichannelWidget::class.java) {
                        if (INSTANCE == null) {
                            throw RuntimeException("Please init Qiscus Chat first!")
                        }
                    }
                }

                return INSTANCE!!
            }

        @JvmStatic
        fun setup(application: Application, applicationId: String) {
            setup(application, applicationId, MultichannelWidgetConfig)
        }

        @JvmStatic
        fun setup(
            application: Application,
            applicationId: String,
            config: MultichannelWidgetConfig
        ) {
            INSTANCE = MultichannelWidget(MultichannelWidgetComponent())
            QiscusCore.setup(application, applicationId)
            QiscusCore.getChatConfig()
                .setEnableFcmPushNotification(true)
                .setNotificationListener { context, qiscusComment ->

                    if (!config.isEnableNotification()) {
                        return@setNotificationListener
                    }

                    if (config.multichannelNotificationListener != null) {
                        config.getNotificationListener()
                            ?.handleMultichannelListener(context, qiscusComment)
                        return@setNotificationListener
                    }

                    if (context != null && qiscusComment != null) {
                        PNUtil.showPn(context, qiscusComment)
                    }
                }
                .enableDebugMode(config.isEnableLog())
            this.config = config
            this.application = application
            Nirmana.init(application)
            Jupuk.init(application)
            this.appId = applicationId
        }

        @JvmStatic
        fun updateConfig(config: MultichannelWidgetConfig) {
            this.config = config
        }
    }

    fun loginMultiChannel(
        name: String,
        userId: String,
        avatar: String?,
        extras: String,
        userProperties: List<UserProperties>,
        onSuccess: (QiscusAccount) -> Unit,
        onError: (Throwable) -> Unit
    ) {

        instance.component.chatroomRepository.initiateChat(name, userId, avatar, extras, userProperties, {
            it.data.roomId?.toLong()?.let { id ->
                QiscusChatLocal.setRoomId(id)
            }
            it.data.identityToken?.let {
                QiscusCore.setUserWithIdentityToken(it,
                    object : QiscusCore.SetUserListener {
                        override fun onSuccess(qiscusAccount: QiscusAccount) {
                            onSuccess(qiscusAccount)
                        }

                        override fun onError(throwable: Throwable) {
                            onError(throwable)
                        }
                    })
            }
        }) {
            onError(it)
        }
    }

    /**
     *
     */
    fun getNonce(onSuccess: (QiscusNonce) -> Unit, onError: (Throwable) -> Unit) {
        QiscusApi.getInstance()
            .jwtNonce
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(onSuccess, onError)
    }

    /**
     *
     */
    fun setUser(token: String, onSuccess: (QiscusAccount) -> Unit, onError: (Throwable) -> Unit) {
        QiscusCore.setUserWithIdentityToken(token)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(onSuccess, onError)
    }

    /**
     * abaikan
     */
    fun setUser(
        userId: String,
        userKey: String,
        username: String,
        onSuccess: (QiscusAccount) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        QiscusCore.setUser(userId, userKey)
            .withUsername(username)
            .save()
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                onSuccess(it)
            }, onError)


    }

    fun updateUser(
        username: String, avatarUrl: String, extras: JSONObject?,
        onSuccess: (QiscusAccount?) -> Unit,
        onError: (Throwable?) -> Unit
    ) {
        QiscusCore.updateUser(username, avatarUrl, extras,
            object : QiscusCore.SetUserListener {
                override fun onSuccess(qiscusAccount: QiscusAccount?) {
                    onSuccess(qiscusAccount)
                }

                override fun onError(throwable: Throwable?) {
                    onError(throwable)
                }
            })
    }

    /**
     *
     */

    private fun hasSetupUser(): Boolean = QiscusCore.hasSetupUser()


    fun openChatRoomById(
        context: Context,
        roomId: Long,
        clearTaskActivity: Boolean,
        onError: (Throwable) -> Unit
    ) {
        if (!hasSetupUser()) {
            onError(Throwable("Please set user first"))
        }

        openChatRoomById(roomId, {
            val intent = ChatRoomActivity.generateIntent(context, it)
            if (clearTaskActivity) {
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }, {
            onError(it)
        })
    }

    fun openChatRoomById(
        roomId: Long,
        onSuccess: (QiscusChatRoom) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        if (!hasSetupUser()) {
            onError(Throwable("Please set user first"))
        }
        QiscusApi.getInstance().getChatRoomInfo(roomId)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe({
                onSuccess(it)
            }, {
                onError(it)
            })
    }

    fun getQiscusAccount(): QiscusAccount {
        return QiscusCore.getQiscusAccount()
    }

    fun openChatRoomMultichannel(clearTaskActivity: Boolean) {
        openChatRoomById(
            application.applicationContext,
            QiscusChatLocal.getRoomId(),
            clearTaskActivity
        ) {
            it
        }
    }

    fun initiateChat(context: Context, name: String, userId: String, avatar: String, extras: JSONObject?, userProperties: Map<String, String>?) {
        var userProp: MutableList<UserProperties> = ArrayList()
        userProperties?.let {
             for ((k,v) in it) {
                 val obj = UserProperties(k, v)
                 userProp.add(obj)
             }
         }

        LoadingActivity.generateIntent(context, name, userId, avatar, extras, userProp)
    }

    fun firebaseMessagingUtil(remoteMessage: RemoteMessage) {
        QiscusFirebaseMessagingUtil.handleMessageReceived(remoteMessage)
    }

    fun getAppId(): String {
        return QiscusCore.getAppId()
    }

    fun isMultichannelMessage(remoteMessage: RemoteMessage): Boolean {
        try {
            val msg = JSONObject(remoteMessage.data.get("payload")).get("room_options").toString()
            if (JSONObject(msg).get("app_code") == appId) {
                QiscusFirebaseMessagingUtil.handleMessageReceived(remoteMessage)
                return true
            }

        } catch (e: Exception) {
            return false
        }

        return false

    }

    fun registerDeviceToken(token: String) {
        QiscusCore.registerDeviceToken(token)
    }

    fun clearUser() {
        QiscusCore.clearUser()
        QiscusChatLocal.clearPreferences()
    }
}
