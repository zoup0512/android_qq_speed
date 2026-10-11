# 飞车 3D · QQ飞车同人还原（Android 原生版）

参考 `E:\workspace\_other\_Origin\claude-opus-5-5-demo\qq-speed`（Three.js 网页版）1:1 移植的原生 Android 3D 竞速游戏。
**零第三方依赖**：纯 Kotlin + OpenGL ES 2.0，所有模型、贴图、音乐均为程序实时生成。

## 功能

- **4 张赛道**：11城（跨河塔桥/摩天楼）、情迷爱琴海（山城发卡弯/隧道）、法老金字塔（沙漠飞跃/绿洲）、雪地大冒险（8字立交/冰雪隧道）
- **两种模式**：竞速（漂移集气/小喷/双喷/氮气/落地喷/起步加速）、道具（导弹/香蕉皮/天使/磁铁/氮气）
- **完整比赛流程**：倒计时信号灯、6 车混战（AI 赛车线+橡皮筋难度）、圈数/最佳单圈/实时排名、冲线倒计时、结算统计
- **还原端游手感**：Shift+方向漂移、出弯小喷窗口（完美判定）、小喷+氮气=双喷、加速带、跳台腾空、护栏刮蹭
- **操控**：触屏（自动油门 + 虚拟按键）、键盘（↑↓←→/Shift/Ctrl/W/R/C/Esc）、手柄（A油门/B漂移/X氮气/Start暂停）
- **音频**：AudioTrack 软件合成 —— 引擎声（模拟 6 档换挡）、漂移胎噪、氮气呼啸、20 余种音效、4 首步进音序器 BGM
- 画质三档、菜单演示模式（AI 自动跑图环绕镜头）、设置持久化

## 构建

```
gradle assembleDebug    # app/build/outputs/apk/debug/app-debug.apk
gradle assembleRelease  # 已配自签名 keystore（debug.keystore，密码 qqspeed）
```

要求：JDK 17、Android SDK（compileSdk 36）、minSdk 24（Android 7.0+）。
仓库若被墙，`settings.gradle.kts` 已优先走阿里云镜像。

## 代码结构

```
app/src/main/java/com/feiche/qqspeed/
├── MainActivity.kt          # 菜单/暂停/结算 UI、键盘手柄输入
├── gl/
│   ├── GL.kt                # 着色器（光照/天空/水面/粒子/轮胎印）、纹理库、网格
│   ├── Renderer.kt          # 相机、视锥剔除、多通道绘制、精灵批
│   └── GameRenderer.kt      # GLSurfaceView 桥
└── game/
    ├── Util.kt              # 数学工具、mulberry32、value noise
    ├── Geom.kt              # 图元/耳切三角化/轮廓挤出/合批
    ├── TexGen.kt            # 全部程序化纹理（路面/护栏/地面/ facade/云/文字）
    ├── Track.kt             # Catmull-Rom 赛道、采样投影、路面网格、地图配置
    ├── World.kt             # 地形高度场/山脉/云/天空球
    ├── CarModel.kt          # 挤出式车身（8 涂装共享几何）
    ├── Vehicle.kt           # 玩家物理（漂移/集气/小喷）与 AI 车手
    ├── Fx.kt                # 粒子池、轮胎印环形缓冲
    ├── Props.kt             # 四图装饰物件（楼群/风车/金字塔/雪林/看台）
    ├── Items.kt             # 道具箱/导弹追踪/香蕉皮
    ├── Audio.kt             # 软件合成音频引擎
    └── Game.kt              # 状态机、圈数/排名/碰撞、相机、特效调度
```

对应关系与原版 `src/*.js` 一一对应（main.js→Game.kt、vehicle.js→Vehicle.kt、track.js→Track.kt …）。

## 已知限制

- 阴影用假阴影面片代替 shadow map（性能考量）
- 后处理 bloom 未移植（低端机帧率优先）
- 部分远景装饰为简化版（帆船/飞艇保留，内部结构简化）
