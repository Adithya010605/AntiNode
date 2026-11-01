package dev.circuiteq.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import dev.circuiteq.core.data.EqRepository
import dev.circuiteq.core.data.EqState
import dev.circuiteq.core.data.Setup
import dev.circuiteq.core.model.Chain
import dev.circuiteq.core.model.OutputDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Foreground service that keeps the user's chain applied to whatever output is playing media. */
class EngineService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var repo: EqRepository
    private lateinit var monitor: OutputMonitor
    private lateinit var engine: EffectEngine

    private val sessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE)
            if (id <= 0) return
            val pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: "unknown"
            when (intent.action) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> engine.open(id, pkg)
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> engine.close(id)
            }
            monitor.refresh()
        }
    }

    private data class Target(val setup: Setup?, val output: OutputDevice?, val chain: Chain?, val power: Boolean)

    override fun onCreate() {
        super.onCreate()
        createChannel()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification("Starting…", null),
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
        )
        repo = EqRepository.get(this)
        monitor = OutputMonitor(this)
        engine = EffectEngine(EngineController.mode(this))
        EngineState.mutable.value = EngineState.mutable.value.copy(running = true)

        engine.start()
        monitor.start(Handler(Looper.getMainLooper()))
        ContextCompat.registerReceiver(
            this, sessionReceiver,
            IntentFilter().apply {
                addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
                addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
            },
            ContextCompat.RECEIVER_EXPORTED,
        )

        scope.launch { repo.ensureSeeded() }
        scope.launch {
            monitor.current.filterNotNull().collect { device ->
                EngineState.mutable.value = EngineState.mutable.value.copy(output = device)
                repo.onOutputConnected(device)
            }
        }
        scope.launch {
            combine(repo.state.filterNotNull(), monitor.current) { st, device -> resolve(st, device) }
                .distinctUntilChanged()
                .collect { t ->
                    engine.apply(t.chain)
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, notification(title(t), t.output?.format))
                }
        }
        // Routing can change without a device being added or removed (output switcher); re-check periodically.
        scope.launch {
            while (isActive) { delay(3_000); monitor.refresh() }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getStringExtra(EXTRA_MODE)?.let { engine.setMode(EngineMode.valueOf(it)); engine.apply(currentChain()) }
        return START_STICKY
    }

    private fun currentChain(): Chain? = repo.state.value?.let { resolve(it, monitor.current.value).chain }

    private fun resolve(st: EqState, device: OutputDevice?): Target {
        val setup = device?.let { st.setupFor(it.key) }
        val chain = if (st.power && setup != null && !setup.bypass) setup.chain else null
        return Target(setup, device, chain, st.power)
    }

    private fun title(t: Target): String {
        val name = t.setup?.name ?: t.output?.name ?: "No output"
        return when {
            !t.power -> "Paused · $name"
            t.chain == null -> "Bypassed · $name"
            else -> "$name · ${t.chain.enabled.size} effects"
        }
    }

    override fun onDestroy() {
        scope.cancel()
        runCatching { unregisterReceiver(sessionReceiver) }
        monitor.stop()
        engine.releaseAll()
        EngineState.mutable.value = EngineStatus()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val channel = NotificationChannel(CHANNEL, getString(R.string.channel_engine), NotificationManager.IMPORTANCE_MIN)
        channel.description = getString(R.string.channel_engine_desc)
        channel.setShowBadge(false)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun notification(title: String, text: String?): Notification {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val pi = launch?.let { PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE) }
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_circuit)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    companion object {
        private const val CHANNEL = "engine"
        private const val NOTIFICATION_ID = 1
        internal const val EXTRA_MODE = "mode"
    }
}
