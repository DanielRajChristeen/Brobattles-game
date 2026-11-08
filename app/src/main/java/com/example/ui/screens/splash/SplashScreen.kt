package com.example.ui.screens.splash

import android.content.res.AssetFileDescriptor
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.R
import kotlinx.coroutines.delay

/**
 * Immersive full-screen video splash launcher screen.
 * Plays the official BroBattle animated launcher intro edge-to-edge.
 * Automatically transitions to the Home Screen when the video finishes.
 */
@Composable
fun SplashScreen(
    soundEnabled: Boolean = true,
    onSplashComplete: () -> Unit
) {
    val context = LocalContext.current
    var isCompleted by remember { mutableStateOf(false) }
    val currentOnComplete = rememberUpdatedState(onSplashComplete)

    val completeOnce = remember {
        {
            if (!isCompleted) {
                isCompleted = true
                currentOnComplete.value()
            }
        }
    }

    // Intercept back button during splash playback to avoid exiting to a half-loaded state
    BackHandler {
        // Splash plays uninterrupted
    }

    // Safety fallback: Video duration is ~7.46s.
    // If playback completes earlier or encounters a system hitch, this guarantees navigation.
    LaunchedEffect(Unit) {
        delay(8200)
        completeOnce()
    }

    var mediaPlayerRef by remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mediaPlayerRef?.stop()
                mediaPlayerRef?.release()
            } catch (_: Exception) {
            }
            mediaPlayerRef = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                TextureView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        private var player: MediaPlayer? = null
                        private var viewW = 0
                        private var viewH = 0

                        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                            viewW = width
                            viewH = height
                            val androidSurface = Surface(surface)

                            try {
                                val mp = MediaPlayer().apply {
                                    setSurface(androidSurface)
                                    isLooping = false

                                    val volume = if (soundEnabled) 1.0f else 0.0f
                                    setVolume(volume, volume)

                                    val afd: AssetFileDescriptor? = try {
                                        ctx.resources.openRawResourceFd(R.raw.app_launcher)
                                    } catch (_: Exception) {
                                        null
                                    }

                                    if (afd != null) {
                                        setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                                        afd.close()
                                    } else {
                                        val uri = android.net.Uri.parse("android.resource://${ctx.packageName}/${R.raw.app_launcher}")
                                        setDataSource(ctx, uri)
                                    }

                                    setOnVideoSizeChangedListener { _, vWidth, vHeight ->
                                        if (vWidth > 0 && vHeight > 0 && viewW > 0 && viewH > 0) {
                                            adjustAspectRatio(viewW, viewH, vWidth, vHeight)
                                        }
                                    }

                                    setOnPreparedListener { preparedPlayer ->
                                        if (preparedPlayer.videoWidth > 0 && preparedPlayer.videoHeight > 0 && viewW > 0 && viewH > 0) {
                                            adjustAspectRatio(viewW, viewH, preparedPlayer.videoWidth, preparedPlayer.videoHeight)
                                        }
                                        preparedPlayer.start()
                                    }

                                    setOnCompletionListener {
                                        completeOnce()
                                    }

                                    setOnErrorListener { _, _, _ ->
                                        completeOnce()
                                        true
                                    }

                                    prepareAsync()
                                }
                                player = mp
                                mediaPlayerRef = mp
                            } catch (e: Exception) {
                                completeOnce()
                            }
                        }

                        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
                            viewW = width
                            viewH = height
                            player?.let { mp ->
                                if (mp.videoWidth > 0 && mp.videoHeight > 0) {
                                    adjustAspectRatio(width, height, mp.videoWidth, mp.videoHeight)
                                }
                            }
                        }

                        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                            try {
                                player?.stop()
                                player?.release()
                            } catch (_: Exception) {
                            }
                            player = null
                            mediaPlayerRef = null
                            return true
                        }

                        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                            // Frame rendered
                        }

                        private fun adjustAspectRatio(vWidth: Int, vHeight: Int, vidWidth: Int, vidHeight: Int) {
                            if (vWidth <= 0 || vHeight <= 0 || vidWidth <= 0 || vidHeight <= 0) return

                            val videoAspect = vidWidth.toFloat() / vidHeight.toFloat()
                            val viewAspect = vWidth.toFloat() / vHeight.toFloat()

                            val scaleX: Float
                            val scaleY: Float
                            if (viewAspect > videoAspect) {
                                scaleX = 1f
                                scaleY = (vWidth.toFloat() / vidWidth.toFloat()) / (vHeight.toFloat() / vidHeight.toFloat())
                            } else {
                                scaleX = (vHeight.toFloat() / vidHeight.toFloat()) / (vWidth.toFloat() / vidWidth.toFloat())
                                scaleY = 1f
                            }

                            val matrix = Matrix()
                            matrix.setScale(scaleX, scaleY, vWidth / 2f, vHeight / 2f)
                            setTransform(matrix)
                        }
                    }
                }
            }
        )
    }
}
