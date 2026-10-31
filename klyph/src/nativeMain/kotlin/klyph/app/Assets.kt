package klyph.app

import klyph.platform.FileException
import klyph.platform.Files
import klyph.platform.GpuDevice
import klyph.platform.Image
import klyph.platform.ImageException
import klyph.platform.createTexture
import klyph.render.SpriteSheet
import klyph.render.Texture
import klyph.render.fromGrid

/**
 * Loads images, textures and sprite sheets from the files under [root], and owns every texture it
 * creates until [close].
 *
 * A relative path resolves under [root]; an absolute path is used as given. [root] defaults to
 * the `assets/` directory next to the executable and is set through [AppConfig.assetRoot]. A relative [root] resolves against [Files.basePath].
 *
 * Textures loaded from a file are cached by resolved path, so loading the same file twice returns
 * the same [Texture]. [runApp] creates the one instance, available as [App.assets], and closes it
 * after every [Phase.SHUTDOWN] system has run. Do not close the textures it returns yourself.
 *
 * [toString] gives the root and the number of textures held, for example
 * `Assets(root=/games/shooter/assets/, textures=3)`.
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

    private val cached = HashMap<String, Texture>()
    private val uploaded = ArrayList<Texture>()
    private var isClosed = false

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
     * Returns the texture of the PNG or JPEG file at [path], loading and uploading it on the first
     * call for that file and returning the same [Texture] on every later call.
     *
     * @throws FileException if the file does not exist or cannot be read.
     * @throws ImageException if the file is not a PNG or JPEG image, or is corrupt.
     * @throws klyph.platform.SdlException if the GPU texture cannot be created.
     * @throws IllegalStateException if these assets are closed.
     */
    public fun texture(path: String): Texture {
        checkOpen()
        val resolved = resolve(path)
        cached[resolved]?.let { return it }
        val texture = upload(decodeFile(resolved))
        cached[resolved] = texture
        return texture
    }

    /**
     * Uploads [image] into a new texture, such as one drawn in code. Each call creates a new
     * texture, which these assets own and close at shutdown. Later changes to [image] do not
     * reach the texture.
     *
     * @throws klyph.platform.SdlException if the GPU texture cannot be created.
     * @throws IllegalStateException if these assets are closed.
     */
    public fun texture(image: Image): Texture {
        checkOpen()
        val texture = upload(image)
        uploaded.add(texture)
        return texture
    }

    /**
     * Returns the texture of the PNG or JPEG file at [path] as [texture] does, or `null` if the
     * file does not exist, cannot be read, or is not a decodable image. A failed load is not
     * cached, so a later call tries the file again.
     *
     * @throws klyph.platform.SdlException if the GPU texture cannot be created.
     * @throws IllegalStateException if these assets are closed.
     */
    public fun textureOrNull(path: String): Texture? {
        checkOpen()
        val resolved = resolve(path)
        cached[resolved]?.let { return it }
        val image = decodeFileOrNull(resolved) ?: return null
        val texture = upload(image)
        cached[resolved] = texture
        return texture
    }

    /**
     * Returns a sprite sheet that cuts the texture of the file at [path], loaded as [texture]
     * does, into a grid of [columns] by [rows] equal cells, indexed row by row from the top-left
     * cell. See [SpriteSheet.Companion.fromGrid] for margins, spacing and explicit cell sizes.
     *
     * @throws FileException if the file does not exist or cannot be read.
     * @throws ImageException if the file is not a PNG or JPEG image, or is corrupt.
     * @throws IllegalArgumentException if [columns] or [rows] is not positive, or the texture is
     *   smaller than the grid.
     * @throws klyph.platform.SdlException if the GPU texture cannot be created.
     * @throws IllegalStateException if these assets are closed.
     */
    public fun spriteSheet(path: String, columns: Int, rows: Int): SpriteSheet =
        SpriteSheet.fromGrid(texture(path), columns, rows)

    /**
     * Closes every texture these assets created, from files and from images. Later calls to the
     * loading functions throw [IllegalStateException]. Calling it again has no effect.
     */
    override fun close() {
        if (isClosed) return
        isClosed = true
        cached.values.forEach { it.close() }
        uploaded.forEach { it.close() }
        cached.clear()
        uploaded.clear()
    }

    /** Returns `Assets(root=/games/shooter/assets/, textures=3)`. The format may change. */
    override fun toString(): String = "Assets(root=$root, textures=${cached.size + uploaded.size})"

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

    private fun upload(image: Image): Texture {
        val handle = device.createTexture(image)
        return Texture(device, handle, image.width, image.height)
    }

    /** Returns [path] under [root], or [path] itself if it is absolute. */
    private fun resolve(path: String): String = if (isAbsolute(path)) path else root + path

    private fun isAbsolute(path: String): Boolean =
        path.startsWith('/') ||
            path.startsWith('\\') ||
            (path.length >= 2 && path[1] == ':' && path[0].isLetter())
}
