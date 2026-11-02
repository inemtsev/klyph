package klyph.app

import klyph.audio.MusicTrack
import klyph.audio.Sound
import klyph.platform.AudioException
import klyph.platform.AudioSamples
import klyph.platform.FileException
import klyph.platform.Files
import klyph.platform.GpuDevice
import klyph.platform.Image
import klyph.platform.ImageException
import klyph.platform.SamplerFilter
import klyph.platform.createTexture
import klyph.render.SpriteSheet
import klyph.render.Texture
import klyph.render.TextureRegion
import klyph.render.fromGrid
import klyph.text.BuiltInFont
import klyph.text.Font
import klyph.text.FontException
import klyph.text.parseBmFont

/**
 * Loads images, textures, sprite sheets, fonts, sounds and music from the files under [root], and
 * owns every texture it creates until [close].
 *
 * A relative path resolves under [root]; an absolute path is used as given. [root] defaults to
 * the `assets/` directory next to the executable and is set through [AppConfig.assetRoot]. A relative [root] resolves against [Files.basePath].
 *
 * Textures, fonts, sounds and music tracks loaded from a file are cached by resolved path, so
 * loading the same file twice returns the same object. Textures and fonts are cached by path and
 * [SamplerFilter], so a file loaded with both filters is uploaded twice, as two textures. [runApp] creates the one instance, available as [App.assets], and closes it
 * after every [Phase.SHUTDOWN] system has run. Do not close the textures it returns yourself.
 *
 * [toString] gives the root and the number of textures, fonts, sounds and music tracks held, for
 * example `Assets(root=/games/shooter/assets/, textures=3, fonts=1, sounds=4, music=1)`. The
 * built-in font's texture counts as one.
 *
 * Not thread-safe; call it from systems only.
 *
 * Samples live in the sandbox because they need a display.
 */
public class Assets internal constructor(private val device: GpuDevice, root: String) : AutoCloseable {
    /**
     * The directory relative paths resolve under. A separator is appended when the configured
     * directory lacks one; an empty root means [Files.basePath] itself.
     */
    public val root: String = if (root.isEmpty() || root.endsWith('/') || root.endsWith('\\')) root else "$root/"

    private val cached = FileCache<Texture>()
    private val uploaded = ArrayList<Texture>()
    private val cachedSounds = HashMap<String, Sound>()
    private val cachedMusic = HashMap<String, MusicTrack>()
    private val cachedFonts = FileCache<Font>()
    private var isClosed = false

    /**
     * The built-in font and white pixel, rasterised and uploaded as one texture when the assets
     * open. It is sampled [SamplerFilter.NEAREST], so text drawn at scale 2 keeps sharp edges.
     */
    private val builtIn: BuiltInFont = BuiltInFont.over(texture(BuiltInFont.rasterize(), SamplerFilter.NEAREST))

    /**
     * The engine's built-in font: 8 by 8 monospace glyphs for printable ASCII, from the space to
     * `'~'`, with a line height and advance of 8 and no kerning. Other characters draw as `'?'`.
     * It needs no files, so text can be drawn before any asset is loaded; draw it at scale 2 or
     * more on a high-density display. Its texture is sampled [SamplerFilter.NEAREST], so whole
     * scales stay crisp. The glyphs are font8x8 by Daniel Hepper, which is in the
     * public domain.
     */
    public val defaultFont: Font get() = builtIn.font

    /** One white texel of the built-in font's atlas, which `Canvas.drawRect` stretches into rectangles. */
    internal val whitePixel: TextureRegion get() = builtIn.whitePixel

    /**
     * Reads and decodes the PNG or JPEG file at [path] into a new [Image].
     *
     * Images are not cached: each call reads the file again and returns an image the caller may
     * change freely.
     *
     * @throws FileException if the file does not exist or cannot be read.
     * @throws ImageException if the file is not a PNG or JPEG image, or is corrupt.
     * @throws IllegalStateException if these assets are closed.
     */
    public fun image(path: String): Image {
        checkOpen()
        return decodeFile(resolve(path))
    }

    /**
     * Reads and decodes the PNG or JPEG file at [path] into a new [Image], or returns `null` if
     * the file does not exist, cannot be read, or is not a decodable image.
     *
     * @throws IllegalStateException if these assets are closed.
     */
    public fun imageOrNull(path: String): Image? {
        checkOpen()
        return decodeFileOrNull(resolve(path))
    }

    /**
     * Returns the texture of the PNG or JPEG file at [path], sampled with [filter], loading and
     * uploading it on the first call for that file and filter and returning the same [Texture] on
     * every later call with both. Use [SamplerFilter.NEAREST] for pixel art.
     *
     * @throws FileException if the file does not exist or cannot be read.
     * @throws ImageException if the file is not a PNG or JPEG image, or is corrupt.
     * @throws klyph.platform.SdlException if the GPU texture cannot be created.
     * @throws IllegalStateException if these assets are closed.
     */
    public fun texture(path: String, filter: SamplerFilter = SamplerFilter.LINEAR): Texture {
        checkOpen()
        return textureAt(resolve(path), filter)
    }

    /** Returns the texture of the file at the already [resolved] path with [filter], loading it on first use. */
    private fun textureAt(resolved: String, filter: SamplerFilter): Texture {
        cached.getOrNull(resolved, filter)?.let { return it }
        val texture = upload(decodeFile(resolved), filter)
        cached.put(resolved, filter, texture)
        return texture
    }

    /**
     * Uploads [image] into a new texture sampled with [filter], such as one drawn in code. Each
     * call creates a new texture, which these assets own and close at shutdown. Later changes to [image] do not
     * reach the texture.
     *
     * @throws klyph.platform.SdlException if the GPU texture cannot be created.
     * @throws IllegalStateException if these assets are closed.
     */
    public fun texture(image: Image, filter: SamplerFilter = SamplerFilter.LINEAR): Texture {
        checkOpen()
        val texture = upload(image, filter)
        uploaded.add(texture)
        return texture
    }

    /**
     * Returns the texture of the PNG or JPEG file at [path] sampled with [filter] as [texture]
     * does, or `null` if the
     * file does not exist, cannot be read, or is not a decodable image. A failed load is not
     * cached, so a later call tries the file again.
     *
     * @throws klyph.platform.SdlException if the GPU texture cannot be created.
     * @throws IllegalStateException if these assets are closed.
     */
    public fun textureOrNull(path: String, filter: SamplerFilter = SamplerFilter.LINEAR): Texture? {
        checkOpen()
        return textureOrNullAt(resolve(path), filter)
    }

    /** Returns the texture of the file at the already [resolved] path with [filter], or `null` if it cannot be loaded. */
    private fun textureOrNullAt(resolved: String, filter: SamplerFilter): Texture? {
        cached.getOrNull(resolved, filter)?.let { return it }
        val image = decodeFileOrNull(resolved) ?: return null
        val texture = upload(image, filter)
        cached.put(resolved, filter, texture)
        return texture
    }

    /**
     * Returns a sprite sheet that cuts the texture of the file at [path], loaded with [filter] as
     * [texture] does, into a grid of [columns] by [rows] equal cells, indexed row by row from the top-left
     * cell. See [SpriteSheet.Companion.fromGrid] for margins, spacing and explicit cell sizes.
     *
     * @throws FileException if the file does not exist or cannot be read.
     * @throws ImageException if the file is not a PNG or JPEG image, or is corrupt.
     * @throws IllegalArgumentException if [columns] or [rows] is not positive, or the texture is
     *   smaller than the grid.
     * @throws klyph.platform.SdlException if the GPU texture cannot be created.
     * @throws IllegalStateException if these assets are closed.
     */
    public fun spriteSheet(path: String, columns: Int, rows: Int, filter: SamplerFilter = SamplerFilter.LINEAR): SpriteSheet =
        SpriteSheet.fromGrid(texture(path, filter), columns, rows)

    /**
     * Returns the font in the BMFont text file at [path] (usually a `.fnt` file), with its pages
     * sampled with [filter], reading it on the first call for that file and filter and returning
     * the same [Font] on every later call with both. Use [SamplerFilter.NEAREST] for a pixel font
     * drawn at whole scales.
     *
     * The file's `page` lines name the PNG or JPEG pages relative to the folder the font file is
     * in; they load as [texture] does with [filter], so a page shared with other code is uploaded
     * once per filter. The file
     * is checked fully before any page loads. BMFont's binary format is not supported; export
     * the text format from the generator instead.
     *
     * Has no sample because it needs a GPU device and a font file.
     *
     * @throws FileException if the font file or a page file does not exist or cannot be read.
     * @throws FontException if the file is a binary BMFont or is not a well-formed BMFont text
     *   file, or a glyph lies outside its page.
     * @throws ImageException if a page is not a PNG or JPEG image, or is corrupt.
     * @throws klyph.platform.SdlException if a page's GPU texture cannot be created.
     * @throws IllegalStateException if these assets are closed.
     */
    public fun font(path: String, filter: SamplerFilter = SamplerFilter.LINEAR): Font {
        checkOpen()
        val resolved = resolve(path)
        cachedFonts.getOrNull(resolved, filter)?.let { return it }
        val font = parseFont(Files.readBytes(resolved), resolved) { page -> textureAt(page, filter) }
        cachedFonts.put(resolved, filter, font)
        return font
    }

    /**
     * Returns the font in the BMFont text file at [path] with its pages sampled with [filter] as
     * [font] does, or `null` if the file or
     * one of its pages does not exist, cannot be read or decoded, or the file is not a well-formed
     * BMFont text file. A failed load is not cached, so a later call tries the file again; pages
     * loaded before the failure stay loaded.
     *
     * Has no sample because it needs a GPU device and a font file.
     *
     * @throws klyph.platform.SdlException if a page's GPU texture cannot be created.
     * @throws IllegalStateException if these assets are closed.
     */
    public fun fontOrNull(path: String, filter: SamplerFilter = SamplerFilter.LINEAR): Font? {
        checkOpen()
        val resolved = resolve(path)
        cachedFonts.getOrNull(resolved, filter)?.let { return it }
        val bytes = Files.readBytesOrNull(resolved) ?: return null
        val font = try {
            parseFont(bytes, resolved) { page ->
                textureOrNullAt(page, filter) ?: throw FontException("Font page '$page' could not be loaded.")
            }
        } catch (e: FontException) {
            return null
        }
        cachedFonts.put(resolved, filter, font)
        return font
    }

    /**
     * Parses [bytes], the font file at the already [resolved] path, loading each page through
     * [loadPage] with the page's resolved path: its `file` value under the font file's folder, or
     * the value itself if it is absolute.
     */
    private inline fun parseFont(bytes: ByteArray, resolved: String, crossinline loadPage: (String) -> Texture): Font {
        // parseBmFont rejects a binary BMFont with a message naming the path.
        val folder = resolved.substring(0, maxOf(resolved.lastIndexOf('/'), resolved.lastIndexOf('\\')) + 1)
        return parseBmFont(bytes.decodeToString(), resolved) { file -> loadPage(if (isAbsolute(file)) file else folder + file) }
    }

    /**
     * Returns the sound effect in the WAV or Ogg Vorbis file at [path], decoding it fully on the
     * first call for that file and returning the same [Sound] on every later call. A WAV file
     * with more than two channels is mixed down to stereo.
     *
     * Play it with `App.audio.play`.
     *
     * @throws FileException if the file does not exist or cannot be read.
     * @throws AudioException if the file is not a WAV or Ogg Vorbis file, is corrupt, or an Ogg
     *   file has more than two channels.
     * @throws IllegalStateException if these assets are closed.
     */
    public fun sound(path: String): Sound {
        checkOpen()
        val resolved = resolve(path)
        cachedSounds[resolved]?.let { return it }
        val bytes = Files.readBytes(resolved)
        val samples = try {
            AudioSamples.decode(bytes)
        } catch (e: AudioException) {
            throw AudioException("Sound file '$resolved' could not be decoded.", e)
        }
        val sound = Sound(samples)
        cachedSounds[resolved] = sound
        return sound
    }

    /**
     * Returns the sound effect in the file at [path] as [sound] does, or `null` if the file does
     * not exist, cannot be read, or is not a decodable WAV or Ogg Vorbis file. A failed load is
     * not cached, so a later call tries the file again.
     *
     * @throws IllegalStateException if these assets are closed.
     */
    public fun soundOrNull(path: String): Sound? {
        checkOpen()
        val resolved = resolve(path)
        cachedSounds[resolved]?.let { return it }
        val bytes = Files.readBytesOrNull(resolved) ?: return null
        val samples = AudioSamples.decodeOrNull(bytes) ?: return null
        val sound = Sound(samples)
        cachedSounds[resolved] = sound
        return sound
    }

    /**
     * Returns the music track in the Ogg Vorbis file at [path], reading the file on the first call
     * for that file and returning the same [MusicTrack] on every later call. The track keeps the
     * encoded file in memory and decodes it a block at a time while it plays.
     *
     * Play it with `App.audio.music.play`.
     *
     * @throws FileException if the file does not exist or cannot be read.
     * @throws AudioException if the file is not an Ogg Vorbis file, is corrupt, or has more than
     *   two channels.
     * @throws IllegalStateException if these assets are closed.
     */
    public fun music(path: String): MusicTrack {
        checkOpen()
        val resolved = resolve(path)
        cachedMusic[resolved]?.let { return it }
        val bytes = Files.readBytes(resolved)
        val track = try {
            MusicTrack.fromOgg(bytes)
        } catch (e: AudioException) {
            throw AudioException("Music file '$resolved' could not be opened.", e)
        }
        cachedMusic[resolved] = track
        return track
    }

    /**
     * Returns the music track in the file at [path] as [music] does, or `null` if the file does
     * not exist, cannot be read, or is not a mono or stereo Ogg Vorbis file. A failed load is not
     * cached, so a later call tries the file again.
     *
     * @throws IllegalStateException if these assets are closed.
     */
    public fun musicOrNull(path: String): MusicTrack? {
        checkOpen()
        val resolved = resolve(path)
        cachedMusic[resolved]?.let { return it }
        val bytes = Files.readBytesOrNull(resolved) ?: return null
        val track = MusicTrack.fromOggOrNull(bytes) ?: return null
        cachedMusic[resolved] = track
        return track
    }

    /**
     * Closes every texture these assets created, from files and from images, including the
     * pages of every font and of [defaultFont], and forgets the fonts, sounds and music tracks
     * they loaded. Later calls to the loading functions throw [IllegalStateException]. Calling it
     * again has no effect.
     */
    override fun close() {
        if (isClosed) return
        isClosed = true
        cached.values.forEach { it.close() }
        uploaded.forEach { it.close() }
        cached.clear()
        uploaded.clear()
        cachedFonts.clear()
        cachedSounds.clear()
        cachedMusic.clear()
    }

    /** Returns `Assets(root=/games/shooter/assets/, textures=3, fonts=1, sounds=4, music=1)`. The format may change. */
    override fun toString(): String =
        "Assets(root=$root, textures=${cached.size + uploaded.size}, fonts=${cachedFonts.size}, " +
            "sounds=${cachedSounds.size}, music=${cachedMusic.size})"

    private fun checkOpen() {
        check(!isClosed) { "Assets under '$root' are closed." }
    }

    /** Reads and decodes the file at the already [resolved] path, naming it in a decoding failure. */
    private fun decodeFile(resolved: String): Image {
        val bytes = Files.readBytes(resolved)
        return try {
            Image.decode(bytes)
        } catch (e: ImageException) {
            throw ImageException("Image file '$resolved' could not be decoded.", e)
        }
    }

    /** Reads and decodes the file at the already [resolved] path, or returns `null` on any failure. */
    private fun decodeFileOrNull(resolved: String): Image? {
        val bytes = Files.readBytesOrNull(resolved) ?: return null
        return Image.decodeOrNull(bytes)
    }

    private fun upload(image: Image, filter: SamplerFilter): Texture {
        val handle = device.createTexture(image)
        return Texture(device, handle, image.width, image.height, filter)
    }

    /** Returns [path] under [root], or [path] itself if it is absolute. */
    private fun resolve(path: String): String = if (isAbsolute(path)) path else root + path

    private fun isAbsolute(path: String): Boolean =
        path.startsWith('/') ||
            path.startsWith('\\') ||
            (path.length >= 2 && path[1] == ':' && path[0].isLetter())
}

/**
 * Objects loaded from files, keyed by resolved path and [SamplerFilter], so one file loaded with
 * both filters is held as two objects.
 */
internal class FileCache<T : Any> {
    private val items = HashMap<Key, T>()

    /** Number of objects held. */
    val size: Int get() = items.size

    /** The objects held, in no particular order. */
    val values: Collection<T> get() = items.values

    /** Returns the object loaded from [path] with [filter], or `null` if there is none. */
    fun getOrNull(path: String, filter: SamplerFilter): T? = items[Key(path, filter)]

    /** Holds [value] as the object loaded from [path] with [filter], replacing any earlier one. */
    fun put(path: String, filter: SamplerFilter, value: T) {
        items[Key(path, filter)] = value
    }

    /** Forgets every object held. */
    fun clear() {
        items.clear()
    }

    override fun toString(): String = "FileCache(size=$size)"

    private class Key(val path: String, val filter: SamplerFilter) {
        override fun equals(other: Any?): Boolean = other is Key && other.path == path && other.filter == filter

        override fun hashCode(): Int = 31 * path.hashCode() + filter.hashCode()
    }
}
