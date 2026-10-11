package com.feiche.qqspeed.gl

import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GameRenderer(private val game: com.feiche.qqspeed.game.Game) : GLSurfaceView.Renderer {
    private var lastT = 0L
    private var inited = false

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        if (!inited) {
            inited = true
            game.initGL()
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        android.opengl.GLES20.glViewport(0, 0, width, height)
        game.renderer.cam.aspect = width.toFloat() / height.toFloat().coerceAtLeast(1f)
        game.renderer.particleScale = height * 0.9f
        game.renderer.cam.near = 0.3f
        game.renderer.cam.far = 9000f
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        var dt = if (lastT == 0L) 0.016f else (now - lastT) / 1e9f
        lastT = now
        game.frame(dt)
    }

    fun resetClock() { lastT = 0L }
}
