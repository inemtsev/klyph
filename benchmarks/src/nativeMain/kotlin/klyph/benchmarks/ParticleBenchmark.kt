package klyph.benchmarks

import klyph.app.Phase
import klyph.app.runApp
import klyph.entity.Transform
import klyph.particle.ParticleEmitter
import klyph.platform.Color
import klyph.render.asRegion
import kotlin.math.ceil
import kotlin.math.sqrt
import kotlin.random.Random

/** Particles each emitter of the particles benchmark holds. */
private const val PARTICLES_PER_EMITTER = 1000

/** Shortest particle lifetime in seconds; the rate refills the pool faster than particles die. */
private const val MIN_LIFETIME = 0.8f

/** Width of a particle at emission in physical pixels; it shrinks to half by the end of its life. */
private const val PARTICLE_PIXELS = 12f

/**
 * Runs `KLYPH_SPRITES / 1000` particle emitters of 1000 particles each, at least one, spread over
 * the window in a grid. Each starts with a full pool and a rate that refills it faster than its
 * particles die, so the count stays near `KLYPH_SPRITES`. Every particle shows the same texture in
 * one layer, so the whole benchmark is one draw call. Reports the engine's particle advance as the
 * "particles" stage; drawing them counts in "extract".
 */
internal val particleBenchmark: Benchmark = Benchmark(
    name = "particles",
    description = "Runs KLYPH_SPRITES / 1000 emitters of 1000 particles each, kept full by their rate, and reports advance and extraction ms.",
) { settings ->
    val emitterCount = (settings.spriteCount / PARTICLES_PER_EMITTER).coerceAtLeast(1)
    var particles = 0
    val report = StatsReport(settings.frameLimit, extraStages = listOf("particles" to { stats -> stats.particleAdvance })) {
        "emitters=$emitterCount particles=$particles"
    }
    runApp(settings.appConfig("klyph particles benchmark")) {
        addBenchmarkSystems(settings, report)
        addSystem(Phase.STARTUP) { app ->
            val region = app.assets.texture(circleImage(16)).asRegion()
            val width = app.window.width.toFloat()
            val height = app.window.height.toFloat()
            // A grid with about as many columns per unit of width as rows per unit of height.
            val columns = ceil(sqrt(emitterCount * width / height)).toInt().coerceAtLeast(1)
            val rows = (emitterCount + columns - 1) / columns
            val scale = PARTICLE_PIXELS / region.width / app.window.pixelDensity
            val random = Random(42)
            for (i in 0 until emitterCount) {
                val emitter = ParticleEmitter(region, maxParticles = PARTICLES_PER_EMITTER).apply {
                    rate = PARTICLES_PER_EMITTER / MIN_LIFETIME * 1.25f
                    speed = 20f..80f
                    lifetime = MIN_LIFETIME..1.2f
                    angularVelocity = -90f..90f
                    gravityY = 40f
                    startColor = Color(red = 0.5f + random.nextFloat() * 0.5f, green = 0.5f + random.nextFloat() * 0.5f, blue = 0.5f + random.nextFloat() * 0.5f)
                    startScale = scale
                    endScale = scale / 2
                    this.random = Random(i)
                }
                emitter.emit(PARTICLES_PER_EMITTER)
                val entity = app.world.create()
                val x = (i % columns + 0.5f) * width / columns
                val y = (i / columns + 0.5f) * height / rows
                app.world.add(entity, Transform, Transform(x = x, y = y))
                app.world.add(entity, ParticleEmitter, emitter)
            }
            println("emitters=$emitterCount particlesPerEmitter=$PARTICLES_PER_EMITTER")
        }
        addSystem(Phase.UPDATE) { app -> particles = app.stats.particleCount }
    }
}
