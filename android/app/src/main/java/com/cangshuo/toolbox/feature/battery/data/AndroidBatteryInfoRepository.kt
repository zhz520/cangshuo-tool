package com.cangshuo.toolbox.feature.battery.data

import android.content.*
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.cangshuo.toolbox.feature.battery.domain.*
import com.cangshuo.toolbox.feature.deviceinfo.domain.DeviceInfoFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*

class AndroidBatteryInfoRepository(context: Context) : BatteryInfoRepository {
    private val context = context.applicationContext
    override fun observe(): Flow<BatterySnapshot> = callbackFlow {
        fun emit(intent: Intent?) { try { trySend(snapshot(intent)) } catch (e: Exception) { close(e) } }
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                emit(if (intent.action == Intent.ACTION_BATTERY_CHANGED) intent else sticky())
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        }
        var registered = false
        try {
            val first = if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                else context.registerReceiver(receiver, filter)
            registered = true
            emit(first ?: sticky())
        } catch (e: Exception) { close(e) }
        awaitClose { if (registered) runCatching { context.unregisterReceiver(receiver) } }
    }.conflate().flowOn(Dispatchers.IO)

    private fun sticky() = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    private fun snapshot(intent: Intent?): BatterySnapshot {
        fun extra(key: String): Int? = intent?.takeIf { it.hasExtra(key) }?.getIntExtra(key, Int.MIN_VALUE)
        val battery = context.getSystemService(BatteryManager::class.java)
        fun property(key: Int): Int? = BatteryPolicy.property(runCatching { battery?.getIntProperty(key) }.getOrNull())
        val present = intent?.takeIf { it.hasExtra(BatteryManager.EXTRA_PRESENT) }?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false)
        return BatterySnapshot(intent != null, present,
            if (present == false) null else BatteryPolicy.percent(extra(BatteryManager.EXTRA_LEVEL), extra(BatteryManager.EXTRA_SCALE)),
            extra(BatteryManager.EXTRA_STATUS), extra(BatteryManager.EXTRA_HEALTH), extra(BatteryManager.EXTRA_PLUGGED),
            BatteryPolicy.temperature(extra(BatteryManager.EXTRA_TEMPERATURE)), extra(BatteryManager.EXTRA_VOLTAGE)?.takeIf { it > 0 },
            DeviceInfoFormat.text(intent?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY)),
            property(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW), property(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)?.takeIf { it >= 0 },
            runCatching { context.getSystemService(PowerManager::class.java)?.isPowerSaveMode }.getOrNull())
    }
}
