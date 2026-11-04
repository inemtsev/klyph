import java.io.File
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.html.HtmlGenerator
import org.intellij.markdown.parser.MarkdownParser

// The guide: Markdown pages in docs/ whose Kotlin snippets come from compiled sources in
// samples/src/nativeTest/kotlin/samples/guide/. A page names its snippet source on its first
// line, `<!-- Snippets: <path from the repository root> -->`, and marks each snippet with
// `<!-- snippet: <name> -->` on the line before its code fence. In the source file the snippet is
// the lines between `// snippet: <name>` and `// end snippet`, with their common indentation
// removed. CheckGuideSnippetsTask fails when a page's code differs from its source;
// RenderGuideTask turns the pages into HTML pages of the Dokka site.

/** Matches the first-line comment that names a page's snippet source. */
private val SNIPPET_SOURCE = Regex("""^<!-- Snippets: (\S+) -->$""")

/** Matches a snippet marker line in a page. */
private val SNIPPET_MARKER = Regex("""^<!-- snippet: ([\w-]+) -->$""")

/** Matches the start of a snippet in a Kotlin source. */
private val REGION_START = Regex("""^\s*// snippet: ([\w-]+)$""")

/** Matches the end of a snippet in a Kotlin source. */
private val REGION_END = Regex("""^\s*// end snippet$""")

/** Returns the snippets of [source] by name, each dedented, failing on a malformed region. */
private fun readRegions(source: File): Map<String, String> {
    val regions = LinkedHashMap<String, String>()
    var name: String? = null
    val lines = ArrayList<String>()
    source.readLines().forEachIndexed { index, line ->
        val start = REGION_START.find(line)
        when {
            start != null -> {
                if (name != null) throw GradleException("${source.path}:${index + 1}: snippet '${start.groupValues[1]}' starts inside snippet '$name'.")
                name = start.groupValues[1]
                lines.clear()
            }
            REGION_END.matches(line) -> {
                val current = name ?: throw GradleException("${source.path}:${index + 1}: '// end snippet' has no matching start.")
                if (current in regions) throw GradleException("${source.path}: snippet '$current' is defined twice.")
                regions[current] = dedent(lines)
                name = null
            }
            name != null -> lines.add(line)
        }
    }
    if (name != null) throw GradleException("${source.path}: snippet '$name' has no '// end snippet'.")
    return regions
}

/** Returns [lines] joined with their common leading whitespace removed and trailing blank lines dropped. */
private fun dedent(lines: List<String>): String {
    val indent = lines.filter { it.isNotBlank() }.minOfOrNull { line -> line.takeWhile { it == ' ' }.length } ?: 0
    return lines.map { if (it.isBlank()) "" else it.substring(indent) }.dropLastWhile { it.isEmpty() }.joinToString("\n")
}

/** A snippet marker of a page and the code of the fence after it. */
private class PageSnippet(val name: String, val line: Int, val code: String?)

/** Returns the snippet source a page names on its first line, or `null` if it names none. */
private fun snippetSourceOf(page: File): String? = page.useLines { lines -> lines.firstOrNull()?.let { SNIPPET_SOURCE.find(it)?.groupValues?.get(1) } }

/** Returns the snippets marked in [page], with the code of the fence that follows each marker. */
private fun pageSnippets(page: File): List<PageSnippet> {
    val lines = page.readLines()
    val snippets = ArrayList<PageSnippet>()
    for ((index, line) in lines.withIndex()) {
        val marker = SNIPPET_MARKER.find(line) ?: continue
        val fence = lines.getOrNull(index + 1)
        val code = if (fence != null && fence.startsWith("```")) {
            val end = (index + 2 until lines.size).firstOrNull { lines[it] == "```" }
            end?.let { lines.subList(index + 2, it).joinToString("\n") }
        } else {
            null
        }
        snippets.add(PageSnippet(marker.groupValues[1], index + 1, code))
    }
    return snippets
}

/**
 * Checks that every Kotlin snippet in the guide pages is a copy of its compiled source, so that
 * compiling the samples module proves the guide's code. Part of the samples module's `check`.
 */
abstract class CheckGuideSnippetsTask : DefaultTask() {
    /** The guide pages. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val pages: ConfigurableFileCollection

    /** The Kotlin files the snippets come from. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    /** The directory the paths in the pages' first lines are relative to: the repository root. */
    @get:Internal
    abstract val rootDirectory: DirectoryProperty

    @TaskAction
    fun check() {
        val root = rootDirectory.get().asFile
        val problems = ArrayList<String>()
        for (page in pages.files.sortedBy { it.name }) {
            val sourcePath = snippetSourceOf(page)
            val snippets = pageSnippets(page)
            if (sourcePath == null) {
                if (snippets.isNotEmpty()) problems.add("${page.name}: marks snippets but its first line names no '<!-- Snippets: <path> -->' source.")
                continue
            }
            val source = root.resolve(sourcePath)
            if (!source.isFile) {
                problems.add("${page.name}: snippet source $sourcePath does not exist.")
                continue
            }
            val regions = readRegions(source)
            for (snippet in snippets) {
                val expected = regions[snippet.name]
                when {
                    snippet.code == null -> problems.add("${page.name}:${snippet.line}: snippet '${snippet.name}' is not followed by a closed code fence.")
                    expected == null -> problems.add("${page.name}:${snippet.line}: $sourcePath has no snippet '${snippet.name}'.")
                    expected != snippet.code -> problems.add("${page.name}:${snippet.line}: snippet '${snippet.name}' differs from $sourcePath.")
                }
            }
            val unused = regions.keys - snippets.map { it.name }.toSet()
            if (unused.isNotEmpty()) problems.add("${page.name}: $sourcePath has snippets the page does not show: ${unused.joinToString()}.")
        }
        if (problems.isNotEmpty()) throw GradleException("The guide's snippets do not match their sources:\n" + problems.joinToString("\n") { "  $it" })
    }
}

/**
 * Renders the guide pages into `<name>.html` pages for the `guide` folder of the Dokka site, plus a
 * contents page, `index.html`, all written to [outputDirectory], outside the site, so that no
 * other task's output overlaps Dokka's; a `Sync` task copies them into the site. Each page reuses
 * the header, navigation and footer of [template], a Dokka page one folder below the site root, so
 * the guide has the site's look, search and theme switch. Links to other pages, `name.md`, become
 * `name.html`; after rendering, every relative link of the guide and of the landing page,
 * [landing], must point at a file of the site, counting the guide as if it were already in place,
 * or the task fails.
 */
abstract class RenderGuideTask : DefaultTask() {
    /** The guide pages, in reading order. */
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val pages: ConfigurableFileCollection

    /** A Dokka page in a folder directly below the site root, such as `klyph/index.html`. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val template: RegularFileProperty

    /** The Markdown of the site's landing page, `docs/index.md`, whose relative links are checked. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val landing: RegularFileProperty

    /** Where the guide's HTML goes; its contents become the `guide` folder of the site. */
    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    /** The site's root folder, which the links are checked against. */
    @get:Internal
    abstract val siteDirectory: DirectoryProperty

    @TaskAction
    fun render() {
        val templateHtml = template.get().asFile.readText()
        val contentStart = templateHtml.indexOf("<div class=\"main-content\"")
        val footerStart = templateHtml.indexOf("    <div class=\"footer\">")
        if (contentStart < 0 || footerStart < contentStart) {
            throw GradleException("${template.get().asFile}: found no main-content and footer to place the guide between.")
        }
        val output = outputDirectory.get().asFile
        output.mkdirs()
        val files = pages.files.toList()
        val names = files.map { it.nameWithoutExtension }.toSet()
        val rendered = files.map { renderPage(it, names) }

        fun write(fileName: String, title: String, body: String) {
            val html = templateHtml.substring(0, contentStart) +
                "<div class=\"main-content guide\" id=\"content\">\n" + body + "\n</div>\n" +
                templateHtml.substring(footerStart)
            output.resolve(fileName).writeText(
                html.replaceFirst(Regex("<title>.*?</title>"), "<title>${escape(title)} | klyph</title>")
                    .replaceFirst("</head>", GUIDE_STYLE + "</head>"),
            )
        }

        rendered.forEachIndexed { index, page ->
            val previous = rendered.getOrNull(index - 1)
            val next = rendered.getOrNull(index + 1)
            val nav = buildString {
                append("<nav class=\"guide-pager\">")
                append(previous?.let { "<a href=\"${it.name}.html\">Previous: ${escape(it.title)}</a>" } ?: "<span></span>")
                append("<a href=\"index.html\">Guide contents</a>")
                append(next?.let { "<a href=\"${it.name}.html\">Next: ${escape(it.title)}</a>" } ?: "<span></span>")
                append("</nav>")
            }
            write("${page.name}.html", page.title, "<div class=\"breadcrumbs\"><a href=\"index.html\">Guide</a></div>\n" + page.body + "\n" + nav)
        }
        val contents = buildString {
            append("<div class=\"breadcrumbs\"><a href=\"../index.html\">klyph</a></div>\n<h1>Guide</h1>\n")
            append("<p>The guide walks through making a game with klyph, one topic per page, in reading order. ")
            append("The <a href=\"../index.html\">API reference</a> has every declaration.</p>\n<ol>\n")
            for (page in rendered) append("<li><a href=\"${page.name}.html\">${escape(page.title)}</a>: ${page.summary}</li>\n")
            append("</ol>")
        }
        write("index.html", "Guide", contents)

        val site = siteDirectory.get().asFile.normalize()
        val guide = site.resolve("guide")
        val broken = ArrayList<String>()
        for (file in output.listFiles().orEmpty().filter { it.extension == "html" }) {
            val content = file.readText().let { it.substring(it.indexOf("<div class=\"main-content guide\"")) }
            for (target in HREF.findAll(content).map { it.groupValues[1] }) {
                if (!resolves(site, guide, output, target)) broken.add("guide/${file.name} links to $target")
            }
        }
        // Dokka keeps a Markdown link only when it resolves to a declaration or has a scheme; a link to
        // a guide page would come out as plain text, so the landing page writes those as <a href>.
        val landingText = landing.get().asFile.readText()
        for (target in MARKDOWN_LINK.findAll(landingText).map { it.groupValues[1] }) {
            if (!target.contains("://")) broken.add("docs/index.md has the Markdown link ($target), which Dokka drops; write <a href=\"$target\">")
        }
        for (target in HREF.findAll(landingText).map { it.groupValues[1] }) {
            if (!resolves(site, site, output, target)) broken.add("docs/index.md links to $target")
        }
        if (broken.isNotEmpty()) throw GradleException("Broken links in the guide:\n" + broken.joinToString("\n") { "  $it" })
    }

    /** One rendered page: its file name without extension, its title, the first paragraph and the HTML. */
    private class Page(val name: String, val title: String, val summary: String, val body: String)

    /** Renders [file] to HTML, turning links to the other guide pages in [names] into `.html` links. */
    private fun renderPage(file: File, names: Set<String>): Page {
        val markdown = file.readText()
        val title = markdown.lineSequence().firstOrNull { it.startsWith("# ") }?.removePrefix("# ")?.trim()
            ?: throw GradleException("${file.name}: the page has no '# Title' line.")
        val flavour = GFMFlavourDescriptor()
        val tree = MarkdownParser(flavour).buildMarkdownTreeFromString(markdown)
        var html = HtmlGenerator(markdown, tree, flavour).generateHtml().removePrefix("<body>").removeSuffix("</body>")
        html = html.replace(LOCAL_MARKDOWN_LINK) { match ->
            val (name, fragment) = match.destructured
            when (name) {
                in names -> "href=\"$name.html$fragment\""
                "index" -> "href=\"../index.html$fragment\""
                else -> match.value
            }
        }
        html = html.replace(CODE_BLOCK) { match ->
            val language = match.groupValues[1].ifEmpty { "text" }
            "<div class=\"sample-container\"><pre><code class=\"monospace block lang-$language\" theme=\"idea\">${match.groupValues[2]}</code></pre>" +
                "<span class=\"copy-tooltip\"><div class=\"copy-popup-wrapper popup-to-left\"><span class=\"copy-popup-icon\"></span>" +
                "<span>Content copied to clipboard</span></div><span class=\"copy-icon\"></span></span></div>"
        }
        html = html.replace("<code>", "<code class=\"lang-kotlin\">")
        val paragraph = FIRST_PARAGRAPH.find(html.substringAfter("</h1>"))?.groupValues?.get(1)
            ?: throw GradleException("${file.name}: the page has no paragraph after its title.")
        // The contents page shows the first sentence of the page's first paragraph.
        val summary = paragraph.replace('\n', ' ').let { if (". " in it) it.substringBefore(". ") + "." else it }
        return Page(file.nameWithoutExtension, title, summary, html)
    }

    /**
     * Returns whether the relative link [target] from [directory], a folder of [site], points at an
     * existing file; other links count as resolved. A file in the site's `guide` folder is looked up
     * in [output], which becomes that folder once it is copied in.
     */
    private fun resolves(site: File, directory: File, output: File, target: String): Boolean {
        if (target.startsWith("#") || target.contains("://") || target.startsWith("mailto:")) return true
        val path = target.substringBefore('#')
        if (path.isEmpty()) return true
        val file = directory.resolve(path).normalize()
        val guide = site.resolve("guide")
        return if (file.startsWith(guide)) output.resolve(file.relativeTo(guide)).exists() else file.exists()
    }

    private companion object {
        val HREF = Regex("""href="([^"]+)"""")
        val MARKDOWN_LINK = Regex("""\]\(([^)\s]+)\)""")
        val LOCAL_MARKDOWN_LINK = Regex("""href="([\w-]+)\.md(#[^"]*)?"""")
        val CODE_BLOCK = Regex("""<pre><code(?: class="language-([\w-]+)")?>(.*?)</code></pre>""", RegexOption.DOT_MATCHES_ALL)
        val FIRST_PARAGRAPH = Regex("""<p>(.*?)</p>""", RegexOption.DOT_MATCHES_ALL)

        /** Styles for what Dokka's own pages lack: tables and the previous and next links. */
        const val GUIDE_STYLE = """<style>
.guide table { border-collapse: collapse; margin: 16px 0; }
.guide th, .guide td { border: 1px solid #8888; padding: 4px 10px; text-align: left; vertical-align: top; }
.guide th { font-weight: 600; }
.guide li { margin: 4px 0; }
.guide-pager { display: flex; justify-content: space-between; gap: 16px; margin: 48px 0 24px; flex-wrap: wrap; }
</style>
"""

        fun escape(text: String): String = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }
}
