package com.feiche.qqspeed.gl

import android.graphics.Bitmap
import android.opengl.GLES20
import com.feiche.qqspeed.game.MeshTemplate
import com.feiche.qqspeed.game.TexGen
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer

// GL 网格：顶点布局 pos3 normal3 uv2 color4 = 12 floats
class GlMesh(verts: FloatArray, idx: ShortArray, val indexCount: Int, useUint: Boolean = false) {
    val vbo: Int
    val ibo: Int
    val uintIdx = useUint
    var cx = 0f; var cy = 0f; var cz = 0f; var radius = -1f // 包围球（<0 不剔除）

    init {
        val vb = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vb.put(verts).position(0)
        val ib: java.nio.Buffer
        val ibBytes: Int
        if (useUint) {
            val ints = IntArray(idx.size)
            for (i in idx.indices) ints[i] = idx[i].toInt()
            ib = ByteBuffer.allocateDirect(ints.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer().put(ints).position(0) as java.nio.Buffer
            ibBytes = ints.size * 4
        } else {
            ib = ByteBuffer.allocateDirect(idx.size * 2).order(ByteOrder.nativeOrder()).asShortBuffer().put(idx).position(0) as java.nio.Buffer
            ibBytes = idx.size * 2
        }
        val ids = IntArray(2)
        GLES20.glGenBuffers(2, ids, 0)
        vbo = ids[0]; ibo = ids[1]
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, verts.size * 4, vb, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo)
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibBytes, ib, GLES20.GL_STATIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    fun setBounds(cx: Float, cy: Float, cz: Float, r: Float) { this.cx = cx; this.cy = cy; this.cz = cz; radius = r }

    fun dispose() {
        val ids = intArrayOf(vbo, ibo)
        GLES20.glDeleteBuffers(2, ids, 0)
    }

    companion object {
        var uintSupported = true

        fun fromTemplate(t: MeshTemplate): GlMesh {
            val idx = ShortArray(t.idx.size)
            for (i in t.idx.indices) idx[i] = t.idx[i].toShort()
            val needUint = t.vertCount > 65535 && uintSupported
            val m = GlMesh(t.verts, idx, t.idx.size, needUint)
            val b = com.feiche.qqspeed.game.boundingSphere(t.verts, t.vertCount)
            m.setBounds(b[0], b[1], b[2], b[3])
            return m
        }
        fun fromArrays(verts: FloatArray, idx: IntArray): GlMesh {
            val shorts = ShortArray(idx.size)
            for (i in idx.indices) shorts[i] = idx[i].toShort()
            val needUint = verts.size / 12 > 65535 && uintSupported
            val m = GlMesh(verts, shorts, idx.size, needUint)
            val b = com.feiche.qqspeed.game.boundingSphere(verts, verts.size / 12)
            m.setBounds(b[0], b[1], b[2], b[3])
            return m
        }
    }
}

// 动态网格（粒子/轮胎印/精灵等，每帧或偶尔更新）
class DynMesh(maxVerts: Int, val floatsPerVert: Int, maxIdx: Int = maxVerts * 3) {
    val vbo: Int
    val ibo: Int
    val capV = maxVerts
    val capI = maxIdx
    var countI = 0
    val fb: FloatBuffer
    val sb: ShortBuffer

    init {
        val ids = IntArray(2)
        GLES20.glGenBuffers(2, ids, 0)
        vbo = ids[0]; ibo = ids[1]
        fb = ByteBuffer.allocateDirect(maxVerts * floatsPerVert * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        sb = ByteBuffer.allocateDirect(maxIdx * 2).order(ByteOrder.nativeOrder()).asShortBuffer()
    }

    fun upload(verts: FloatArray, nV: Int, idx: ShortArray, nI: Int) {
        countI = nI
        if (nI == 0) return
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbo)
        fb.clear(); fb.put(verts, 0, nV * floatsPerVert); fb.position(0)
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, nV * floatsPerVert * 4, fb, GLES20.GL_DYNAMIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo)
        sb.clear(); sb.put(idx, 0, nI); sb.position(0)
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, nI * 2, sb, GLES20.GL_DYNAMIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0)
    }

    fun dispose() { GLES20.glDeleteBuffers(2, intArrayOf(vbo, ibo), 0) }
}

// ---------- 材质 ----------
class Mat(
    val colorR: Float = 1f, val colorG: Float = 1f, val colorB: Float = 1f, val alpha: Float = 1f,
    val mapName: String? = null,
    val unlit: Boolean = false,
    val additive: Boolean = false,
    val doubleSide: Boolean = false,
    val polyOffset: Int = 0,
    val noFog: Boolean = false,
    val depthWrite: Boolean = true,
    val scrollUv: Boolean = false,
    val nearest: Boolean = false,
    val repeat: Boolean = true,
    val specStrength: Float = 0.22f,
) {
    var glTex: Int = 0
    var loaded = false
}

// 纹理库：按名生成/缓存 GL 纹理
object TexLib {
    private val cache = HashMap<String, Int>()
    private val loaders = HashMap<String, () -> Bitmap>()

    fun register(name: String, loader: () -> Bitmap) { loaders[name] = loader }

    fun get(name: String): Int {
        if (name.isEmpty()) return 0
        var t = cache[name]
        if (t != null) return t
        val bmp = loaders[name]?.invoke() ?: run {
            val fb = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            fb.eraseColor(0xffffffff.toInt())
            return upload(fb, repeat = true, nearest = false)
        }
        t = upload(bmp, repeat = true, nearest = false)
        cache[name] = t
        bmp.recycle()
        return t
    }

    fun upload(bmp: Bitmap, repeat: Boolean, nearest: Boolean): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        val t = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t)
        android.opengl.GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
        val wrap = if (repeat) GLES20.GL_REPEAT else GLES20.GL_CLAMP_TO_EDGE
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, wrap)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, wrap)
        val filt = if (nearest) GLES20.GL_NEAREST else GLES20.GL_LINEAR
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, filt)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        return t
    }

    fun ensureLoaded(m: Mat) {
        if (m.loaded) return
        m.glTex = if (m.mapName != null) get(m.mapName) else 0
        m.loaded = true
    }
}

// ---------- 着色器 ----------
class Shader(val prog: Int, uniforms: Array<String>, attrs: Array<String>) {
    val loc = HashMap<String, Int>()

    init {
        for (u in uniforms) loc[u] = GLES20.glGetUniformLocation(prog, u)
        for (a in attrs) loc[a] = GLES20.glGetAttribLocation(prog, a)
    }

    fun u(name: String): Int = loc[name] ?: -1
    fun a(name: String): Int = loc[name] ?: -1

    fun use() = GLES20.glUseProgram(prog)

    companion object {
        fun compile(vs: String, fs: String): Shader {
            val v = compileOne(GLES20.GL_VERTEX_SHADER, vs)
            val f = compileOne(GLES20.GL_FRAGMENT_SHADER, fs)
            val p = GLES20.glCreateProgram()
            GLES20.glAttachShader(p, v)
            GLES20.glAttachShader(p, f)
            GLES20.glLinkProgram(p)
            val st = IntArray(1)
            GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, st, 0)
            if (st[0] == 0) {
                val log = GLES20.glGetProgramInfoLog(p)
                throw RuntimeException("link failed: $log")
            }
            val sh = Shader(p, arrayOf(), arrayOf())
            // 内省所有 active uniform / attribute 并缓存位置
            val numU = IntArray(1); val len = IntArray(1); val type = IntArray(1); val size = IntArray(1)
            val nameBuf = ByteArray(96)
            GLES20.glGetProgramiv(p, GLES20.GL_ACTIVE_UNIFORMS, numU, 0)
            for (i in 0 until numU[0]) {
                GLES20.glGetActiveUniform(p, i, 96, len, 0, size, 0, type, 0, nameBuf, 0)
                val name = String(nameBuf, 0, len[0])
                if (name.endsWith("[0]")) name.substring(0, name.length - 3)
                sh.loc[name] = GLES20.glGetUniformLocation(p, name)
            }
            GLES20.glGetProgramiv(p, GLES20.GL_ACTIVE_ATTRIBUTES, numU, 0)
            for (i in 0 until numU[0]) {
                GLES20.glGetActiveAttrib(p, i, 96, len, 0, size, 0, type, 0, nameBuf, 0)
                val name = String(nameBuf, 0, len[0])
                sh.loc[name] = GLES20.glGetAttribLocation(p, name)
            }
            return sh
        }

        private fun compileOne(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src)
            GLES20.glCompileShader(s)
            val st = IntArray(1)
            GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, st, 0)
            if (st[0] == 0) {
                val log = GLES20.glGetShaderInfoLog(s)
                throw RuntimeException("compile failed: $log\n$src")
            }
            return s
        }
    }
}

object Shaders {
    lateinit var lit: Shader
    lateinit var sky: Shader
    lateinit var water: Shader
    lateinit var particle: Shader
    lateinit var skid: Shader

    fun build() {
        lit = Shader.compile(LIT_VS, LIT_FS)
        sky = Shader.compile(SKY_VS, SKY_FS)
        water = Shader.compile(WATER_VS, WATER_FS)
        particle = Shader.compile(PARTICLE_VS, PARTICLE_FS)
        skid = Shader.compile(SKID_VS, SKID_FS)
    }

    private const val LIT_VS = """
        uniform mat4 uPV; uniform mat4 uModel;
        attribute vec3 aPos; attribute vec3 aNormal; attribute vec2 aUV; attribute vec4 aColor;
        varying vec3 vN; varying vec2 vUV; varying vec4 vC; varying float vDist; varying vec3 vW;
        void main(){
            vec4 wp = uModel * vec4(aPos, 1.0);
            gl_Position = uPV * wp;
            vN = normalize(mat3(uModel) * aNormal);
            vUV = aUV; vC = aColor;
            vDist = length(wp.xyz);
            vW = wp.xyz;
        }"""

    private const val LIT_FS = """
        precision mediump float;
        uniform vec3 uCamPos;
        uniform vec3 uSunDir; uniform vec3 uSunColor;
        uniform vec3 uHemiSky; uniform vec3 uHemiGround; uniform float uHemiI;
        uniform vec3 uFogColor; uniform float uFogNear; uniform float uFogFar; uniform float uFogOn;
        uniform vec4 uColorMul;
        uniform float uHasMap; uniform sampler2D uMap; uniform vec2 uUvOff;
        uniform float uUnlit; uniform vec3 uEmissive; uniform float uSpec;
        varying vec3 vN; varying vec2 vUV; varying vec4 vC; varying float vDist; varying vec3 vW;
        vec3 aces(vec3 x){ return clamp((x*(2.51*x+0.03))/(x*(2.43*x+0.59)+0.14), 0.0, 1.0); }
        void main(){
            vec4 tex = uHasMap > 0.5 ? texture2D(uMap, vUV + uUvOff) : vec4(1.0);
            vec4 albedo = vC * uColorMul * tex;
            vec3 c;
            if (uUnlit > 0.5) {
                c = albedo.rgb;
            } else {
                vec3 n = normalize(vN);
                vec3 hemi = mix(uHemiGround, uHemiSky, n.y * 0.5 + 0.5) * uHemiI;
                float ndl = max(dot(n, uSunDir), 0.0);
                vec3 diff = albedo.rgb * (hemi + uSunColor * ndl);
                vec3 v = normalize(uCamPos - vW);
                vec3 hv = normalize(uSunDir + v);
                float sp = pow(max(dot(n, hv), 0.0), 42.0) * uSpec * step(0.0, ndl);
                c = diff + uSunColor * sp + uEmissive;
            }
            if (uFogOn > 0.5) {
                float f = clamp((vDist - uFogNear) / (uFogFar - uFogNear), 0.0, 1.0);
                c = mix(c, uFogColor, f);
            }
            c = aces(c * 0.92);
            c = pow(c, vec3(0.4545));
            gl_FragColor = vec4(c, albedo.a * uColorMul.a);
        }"""

    private const val SKY_VS = """
        uniform mat4 uPV;
        attribute vec3 aPos;
        varying vec3 vDir;
        void main(){
            vDir = aPos;
            vec4 p = uPV * vec4(aPos, 1.0);
            gl_Position = p.xyww;
        }"""

    private const val SKY_FS = """
        precision mediump float;
        uniform vec3 uTop; uniform vec3 uHorizon; uniform vec3 uBottom;
        uniform vec3 uSunDir; uniform vec3 uSunColor;
        varying vec3 vDir;
        void main(){
            vec3 d = normalize(vDir);
            float h = d.y;
            vec3 c = h > 0.0 ? mix(uHorizon, uTop, pow(clamp(h, 0.0, 1.0), 0.55))
                             : mix(uHorizon, uBottom, pow(clamp(-h, 0.0, 1.0), 0.4));
            float s = max(dot(d, uSunDir), 0.0);
            c += uSunColor * (pow(s, 900.0) * 6.0 + pow(s, 40.0) * 0.35 + pow(s, 6.0) * 0.12);
            c = clamp(c, 0.0, 1.0);
            c = pow(c, vec3(0.4545));
            gl_FragColor = vec4(c, 1.0);
        }"""

    private const val WATER_VS = """
        uniform mat4 uPV; uniform mat4 uModel;
        attribute vec3 aPos;
        varying vec3 vW;
        void main(){
            vec4 w = uModel * vec4(aPos, 1.0);
            vW = w.xyz;
            gl_Position = uPV * w;
        }"""

    private const val WATER_FS = """
        precision highp float;
        uniform float uTime; uniform vec3 uColor; uniform vec3 uDeep; uniform vec3 uSky; uniform vec3 uSun;
        uniform vec3 uCamPos;
        uniform vec3 uFogColor; uniform float uFogNear; uniform float uFogFar;
        varying vec3 vW;
        void main(){
            vec2 p = vW.xz;
            vec2 g = vec2(0.0);
            g += vec2(0.8, 0.6) * cos(dot(p, vec2(0.8, 0.6)) * 0.09 + uTime * 1.3) * 0.09;
            g += vec2(-0.5, 0.85) * cos(dot(p, vec2(-0.5, 0.85)) * 0.17 + uTime * 1.9) * 0.06;
            g += vec2(0.3, -0.95) * cos(dot(p, vec2(0.3, -0.95)) * 0.41 + uTime * 2.7) * 0.035;
            g += vec2(0.95, 0.3) * cos(dot(p, vec2(0.95, 0.3)) * 0.83 + uTime * 3.4) * 0.02;
            vec3 n = normalize(vec3(-g.x, 1.0, -g.y));
            vec3 v = normalize(uCamPos - vW);
            float fres = pow(1.0 - max(dot(n, v), 0.0), 3.0);
            vec3 c = mix(uColor, uDeep, 0.35 + 0.3 * sin(p.x * 0.01) * sin(p.y * 0.013));
            c = mix(c, uSky, clamp(fres * 0.9 + 0.08, 0.0, 1.0));
            vec3 r = reflect(-v, n);
            float sp = pow(max(dot(r, normalize(uSun)), 0.0), 180.0);
            c += vec3(1.0, 0.97, 0.9) * sp * 2.5;
            float f = clamp((length(uCamPos - vW) - uFogNear) / (uFogFar - uFogNear), 0.0, 1.0);
            c = mix(c, uFogColor, f);
            c = clamp(c, 0.0, 1.0);
            c = pow(c, vec3(0.4545));
            gl_FragColor = vec4(c, 0.93);
        }"""

    private const val PARTICLE_VS = """
        uniform mat4 uPV; uniform float uScale; uniform float uFogOn;
        uniform vec3 uFogColor; uniform float uFogNear; uniform float uFogFar;
        attribute vec3 aPos; attribute vec4 aColor; attribute float aSize;
        varying vec4 vColor;
        void main(){
            vColor = aColor;
            vec4 p = uPV * vec4(aPos, 1.0);
            gl_Position = p;
            gl_PointSize = aSize * uScale / max(0.1, p.w);
            if (uFogOn > 0.5) {
                float f = clamp((p.w - uFogNear) / (uFogFar - uFogNear), 0.0, 1.0);
                vColor.a *= (1.0 - f);
            }
        }"""

    private const val PARTICLE_FS = """
        precision mediump float;
        uniform sampler2D uMap;
        varying vec4 vColor;
        void main(){
            vec4 t = texture2D(uMap, gl_PointCoord);
            vec4 c = vec4(vColor.rgb, vColor.a * t.a);
            if (c.a < 0.01) discard;
            gl_FragColor = c;
        }"""

    private const val SKID_VS = """
        uniform mat4 uPV; uniform mat4 uModel;
        uniform vec3 uFogColor; uniform float uFogNear; uniform float uFogFar;
        attribute vec3 aPos; attribute float aAlpha;
        varying float vA; varying float vDist;
        void main(){
            vA = aAlpha;
            vec4 wp = uModel * vec4(aPos, 1.0);
            gl_Position = uPV * wp;
            vDist = length(wp.xyz);
        }"""

    private const val SKID_FS = """
        precision mediump float;
        uniform vec3 uColor;
        uniform vec3 uFogColor; uniform float uFogNear; uniform float uFogFar;
        varying float vA; varying float vDist;
        void main(){
            vec3 c = uColor;
            float f = clamp((vDist - uFogNear) / (uFogFar - uFogNear), 0.0, 1.0);
            c = mix(c, uFogColor, f);
            gl_FragColor = vec4(c, vA * 0.55);
        }"""
}
