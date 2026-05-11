package com.codewithkael.productionwebrtc.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.Intent.ACTION_MAIN
import android.content.Intent.CATEGORY_LAUNCHER
import com.codewithkael.productionwebrtc.ui.MainActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CallBroadcastReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        intent?.action?.let { action ->
            if (intent.action.equals(Intent.ACTION_BOOT_COMPLETED)) {
                context?.let { reOpenTheApplication(it) }
            }
            if (action == "ACTION_EXIT") {
                context?.let { noneNullContext ->
                    CallService.stopService(noneNullContext)
                    noneNullContext.startActivity(Intent(noneNullContext, MainActivity::class.java)
                        .apply {
                            putExtra("close_app",true)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        })
                }
            }
        }
    }

    private fun reOpenTheApplication(context: Context) {
        val activityIntent = Intent(context, MainActivity::class.java)
        activityIntent.action= ACTION_MAIN
        activityIntent.addCategory(CATEGORY_LAUNCHER)
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        activityIntent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        context.startActivity(activityIntent)
    }
}