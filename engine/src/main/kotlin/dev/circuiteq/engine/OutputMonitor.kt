package dev.circuiteq.engine

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import dev.circuiteq.core.data.EqRepository
import dev.circuiteq.core.model.OutputDevice
import dev.circuiteq.core.model.OutputKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Tracks which output media is currently routed to. */
class OutputMonitor(context: Context) {
    private val am = context.getSystemService(AudioManager::class.java)
    private val _current = MutableStateFlow<OutputDevice?>(null)
    val current: StateFlow<OutputDevice?> = _current

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) = refresh()
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) = refresh()
    }

    fun start(handler: Handler) {
        am.registerAudioDeviceCallback(callback, handler)
        refresh()
    }

    fun stop() = am.unregisterAudioDeviceCallback(callback)

    fun refresh() {
        val next = pick()?.let(::describe)
        if (next != _current.value) _current.value = next
    }

    /**
     * Android has no public "where is media playing" query, so pick the connected output media would use:
     * USB, then wired, then Bluetooth, then HDMI, then the speaker.
     */
    private fun pick(): AudioDeviceInfo? {
        val outputs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        for (type in PRIORITY) outputs.firstOrNull { it.type == type }?.let { return it }
        return null
    }

    private fun describe(d: AudioDeviceInfo): OutputDevice {
        val kind = kindOf(d.type) ?: OutputKind.OTHER
        val product = d.productName?.toString()?.trim().orEmpty()
        val name = when {
            kind == OutputKind.SPEAKER -> "Phone speaker"
            product.isEmpty() || product == Build.MODEL -> kind.label
            else -> product
        }
        val key = when (kind) {
            OutputKind.SPEAKER -> EqRepository.SPEAKER_KEY
            OutputKind.BLUETOOTH -> listOf(kind.name, name, d.address.orEmpty()).joinToString(":")
            else -> "${kind.name}:$name"
        }
        return OutputDevice(key = key, kind = kind, name = name, format = format(d))
    }

    private fun format(d: AudioDeviceInfo): String? {
        val enc = d.encodings.toSet()
        val bits = when {
            AudioFormat.ENCODING_PCM_32BIT in enc || AudioFormat.ENCODING_PCM_FLOAT in enc -> "32-bit"
            ENCODING_PCM_24BIT_PACKED in enc -> "24-bit"
            AudioFormat.ENCODING_PCM_16BIT in enc -> "16-bit"
            else -> null
        }
        val rate = d.sampleRates.maxOrNull()?.let { r -> if (r % 1000 == 0) "${r / 1000} kHz" else "%.1f kHz".format(r / 1000.0) }
        return listOfNotNull(bits, rate).joinToString(" / ").ifEmpty { null }
    }

    companion object {
        private const val ENCODING_PCM_24BIT_PACKED = 21

        private val PRIORITY = intArrayOf(
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
            26 /* TYPE_BLE_HEADSET */, 27 /* TYPE_BLE_SPEAKER */, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_LINE_DIGITAL, AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        )

        fun kindOf(type: Int): OutputKind? = when (type) {
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_ACCESSORY -> OutputKind.USB
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_LINE_ANALOG -> OutputKind.WIRED
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, 26, 27, 30 /* TYPE_BLE_BROADCAST */,
            AudioDeviceInfo.TYPE_HEARING_AID -> OutputKind.BLUETOOTH
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, 24 /* TYPE_BUILTIN_SPEAKER_SAFE */ -> OutputKind.SPEAKER
            AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_LINE_DIGITAL,
            AudioDeviceInfo.TYPE_DOCK -> OutputKind.HDMI
            else -> null
        }
    }
}
