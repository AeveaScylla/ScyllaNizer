package com.better.scyllanizer.data

object CategoryMapper {
    const val IMAGES = "Images"
    const val VIDEOS = "Videos"
    const val AUDIO = "Audio"
    const val DOCUMENTS = "Documents"
    const val ARCHIVES = "Archives"
    const val APPS = "Apps"
    const val FONTS = "Fonts"
    const val CODE = "Code"
    const val DESIGN = "Design"
    const val OTHERS = "Others"

    val categories: List<String> = listOf(
        IMAGES, VIDEOS, AUDIO, DOCUMENTS, ARCHIVES, APPS, FONTS, CODE, DESIGN, OTHERS
    )

    private val map: Map<String, String> = buildMap {
        fun group(category: String, vararg extensions: String) {
            extensions.forEach { put(it, category) }
        }
        group(IMAGES, "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif", "svg", "tif", "tiff", "avif", "dng", "raw", "cr2", "nef", "arw", "orf")
        group(VIDEOS, "mp4", "mkv", "avi", "mov", "wmv", "flv", "webm", "3gp", "3g2", "m4v", "mpg", "mpeg", "ts", "mts", "m2ts", "ogv")
        group(AUDIO, "mp3", "wav", "flac", "aac", "ogg", "oga", "m4a", "opus", "wma", "amr", "mid", "midi", "aiff", "alac")
        group(DOCUMENTS, "pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "odt", "ods", "odp", "csv", "md", "epub", "mobi", "azw3", "pages", "numbers", "key")
        group(ARCHIVES, "zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "iso", "cab", "lz", "zst")
        group(APPS, "apk", "apks", "xapk", "aab", "jar")
        group(FONTS, "ttf", "otf", "woff", "woff2")
        group(CODE, "kt", "kts", "java", "py", "js", "mjs", "ts", "tsx", "jsx", "html", "htm", "css", "scss", "sass", "json", "xml", "yaml", "yml", "sh", "bat", "c", "cpp", "h", "hpp", "cs", "go", "rs", "rb", "php", "sql", "swift", "dart", "lua", "toml", "ini", "gradle", "properties", "log")
        group(DESIGN, "psd", "ai", "xd", "fig", "sketch", "blend", "obj", "fbx", "stl", "skp", "svgz")
    }

    fun categoryFor(extension: String): String = map[extension.lowercase()] ?: OTHERS
}
