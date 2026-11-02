package com.minhngoc.irremote.ui.atv

import androidx.compose.ui.res.stringResource
import com.minhngoc.irremote.R
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.minhngoc.irremote.atv.AtvRemote
import com.minhngoc.irremote.ui.theme.LocalPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private const val SAMPLE_RATE = 8_000 // the TV expects PCM 16-bit mono 8 kHz
private const val SEND_BYTES = 8 * 1024 // ~0.5 s per voice payload
private const val MAX_TALK_MS = 10_000L

private enum class VoiceState { Idle, Opening, Listening }

/**
 * Talk to the TV's assistant: tap to start streaming the phone microphone,
 * tap again (or wait [MAX_TALK_MS]) to finish. A tap toggle, not press-and-hold:
 * inside the scrolling remote a hold is cancelled as soon as the finger moves.
 */
@Composable
fun HoldToTalk(client: AtvRemote, accent: Color) {
    val context = LocalContext.current
    val palette = LocalPalette.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf(VoiceState.Idle) }
    var listening by remember { mutableStateOf(false) }
    val askMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) Toast.makeText(context, R.string.mic_granted, Toast.LENGTH_SHORT).show()
    }
    val pulse by rememberInfiniteTransition(label = "voice").animateFloat(
        1f, 1.05f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "pulse",
    )

    @SuppressLint("MissingPermission") // checked before calling
    suspend fun stream() = withContext(Dispatchers.IO) {
        val session = client.startVoice()
        if (session == null) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, R.string.tv_no_assistant, Toast.LENGTH_SHORT).show()
            }
            return@withContext
        }
        state = VoiceState.Listening
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val recorder = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuf, SEND_BYTES))
        val pending = ByteArrayOutputStream()
        val captured = ByteArrayOutputStream() // kept for diagnosis: files/last_voice.pcm
        val buf = ByteArray(2_048)
        var total = 0
        val started = System.currentTimeMillis()
        try {
            recorder.startRecording()
            while (listening && isActive && System.currentTimeMillis() - started < MAX_TALK_MS) {
                val n = recorder.read(buf, 0, buf.size)
                if (n > 0) {
                    pending.write(buf, 0, n)
                    captured.write(buf, 0, n)
                    total += n
                } else if (n < 0) {
                    android.util.Log.e("AtvTrace", "mic read error $n (state=${recorder.recordingState})")
                    break
                }
                if (pending.size() >= SEND_BYTES) {
                    client.sendVoice(session, pending.toByteArray())
                    pending.reset()
                }
            }
            if (pending.size() > 0) client.sendVoice(session, pending.toByteArray())
        } finally {
            android.util.Log.e("AtvTrace", "mic captured $total bytes in ${System.currentTimeMillis() - started} ms")
            runCatching { java.io.File(context.filesDir, "last_voice.pcm").writeBytes(captured.toByteArray()) }
            runCatching { recorder.stop() }
            recorder.release()
            client.endVoice(session)
            listening = false
        }
    }

    fun toggle() {
        if (state != VoiceState.Idle) {
            listening = false // the streaming loop ends and closes the session
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            askMic.launch(Manifest.permission.RECORD_AUDIO)
            return
        }
        listening = true
        state = VoiceState.Opening
        scope.launch {
            stream()
            state = VoiceState.Idle
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .scale(if (state == VoiceState.Listening) pulse else 1f)
            .clip(RoundedCornerShape(30.dp))
            .background(if (state == VoiceState.Idle) palette.key else accent)
            .clickable(onClickLabel = stringResource(R.string.talk_to_tv)) { toggle() },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Mic, null, tint = if (state == VoiceState.Idle) accent else Color.White, modifier = Modifier.size(26.dp))
            Text(
                when (state) {
                    VoiceState.Idle -> stringResource(R.string.tap_to_talk)
                    VoiceState.Opening -> stringResource(R.string.opening_assistant)
                    VoiceState.Listening -> stringResource(R.string.listening_tap_send)
                },
                style = MaterialTheme.typography.labelLarge,
                color = if (state == VoiceState.Idle) palette.ink else Color.White,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

/** Mic key that turns Vietnamese speech into text with the phone's recognizer, then types it on the TV. */
@Composable
fun DictateButton(onText: (String) -> Unit) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let(onText)
        }
    }
    IconButton(onClick = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, context.getString(R.string.dictate_prompt))
        }
        try {
            launcher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.no_speech_service, Toast.LENGTH_SHORT).show()
        }
    }) { Icon(Icons.Filled.Mic, contentDescription = stringResource(R.string.voice_typing), modifier = Modifier.size(24.dp).clip(CircleShape)) }
}
