package com.example.stagemon

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ChannelRoutingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Log.d("ChannelRoutingReceiver", "🔥 BROADCAST RECEIVED!")

        if (intent?.action == "UPDATE_CHANNEL_ROUTING") {
            val fohPair = intent.getIntExtra("foh_pair", -1)
            val monPair = intent.getIntExtra("mon_pair", -1)

            Log.d("ChannelRoutingReceiver", "Values: FOH=$fohPair, MON=$monPair")

            // Отправляем в MainActivity через LocalBroadcast или другое решение
            val localIntent = Intent("LOCAL_UPDATE_CHANNEL_ROUTING")
            localIntent.putExtra("foh_pair", fohPair)
            localIntent.putExtra("mon_pair", monPair)
            androidx.localbroadcastmanager.content.LocalBroadcastManager.getInstance(context!!)
                .sendBroadcast(localIntent)
        }
    }
}