package com.ajz.ereader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.util.Locale
import androidx.compose.material3.Slider
import android.content.Intent
import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import androidx.activity.result.contract.ActivityResultContracts
import android.os.Build
import android.content.Context
import androidx.core.text.HtmlCompat
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import android.provider.OpenableColumns
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayOutputStream

private val Context.dataStore by preferencesDataStore(name = "settings")

private object PrefKeys {
    val epubUri = stringPreferencesKey("epub_uri")
    val epubName = stringPreferencesKey("epub_name")
    val playbackChapterIndex = intPreferencesKey("playback_chapter_index")
    val playbackSentenceIndex = intPreferencesKey("playback_sentence_index")
}

private data class OpfData(
    val spineFiles: List<String>,
    val navPath: String?,
    val ncxPath: String?
)

class MainActivity : ComponentActivity() {
    private var selectedUri: String? by mutableStateOf(null)
    private var selectedName: String? by mutableStateOf(null)
    private var chapterTitles: List<String> by
    mutableStateOf(emptyList())
    private var chapterSource: String by mutableStateOf("unknown")
    private var chapterFiles: List<String> by
    mutableStateOf(emptyList())
    private var chapterText: String by mutableStateOf("")
    private var resumeChapterIndex: Int by mutableStateOf(0)
    private var resumeSentenceIndex: Int by mutableStateOf(0)
    private var currentChapterIndex: Int by mutableStateOf(0)
    private var currentSentenceIndex: Int by mutableStateOf(0)
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
    private var ttsService: com.ajz.ereader.tts.TtsService? = null
    private var isServiceBound = false
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? com.ajz.ereader.tts.TtsService.TtsBinder
            ttsService = binder?.getService()
            isServiceBound = ttsService != null
            ttsService?.setSentenceIndexListener { index ->
                runOnUiThread { currentSentenceIndex = index }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            ttsService?.setSentenceIndexListener(null)
            ttsService = null
            isServiceBound = false
        }
    }

    private val pickEpub = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
            val uriString = uri.toString()
            selectedUri = uriString
            selectedName = getDisplayName(uriString)
            loadEpubChapters(uriString, 0, 0)

            lifecycleScope.launch {
                dataStore.edit { prefs ->
                    prefs[PrefKeys.epubUri] = uriString
                    prefs[PrefKeys.epubName] = selectedName ?: ""
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycleScope.launch {
            val prefs = dataStore.data.first()
            selectedUri = prefs[PrefKeys.epubUri]
            selectedName = prefs[PrefKeys.epubName]
            if (selectedUri != null) {
                resumeChapterIndex = prefs[PrefKeys.playbackChapterIndex] ?: 0
                resumeSentenceIndex = prefs[PrefKeys.playbackSentenceIndex] ?: 0
                loadEpubChapters(
                    selectedUri!!,
                    resumeChapterIndex,
                    resumeSentenceIndex
                )
            }
        }

        setContent {
            MaterialTheme {
                Surface {
                    TtsScreen(
                        onPlay = { text, rate, index ->
                            startTtsService()
                            ttsService?.playAll(listOf(text), rate, index)
                        },
                        onPlayAll = { sentences, rate, startIndex ->
                            startTtsService()
                            ttsService?.playAll(sentences, rate, startIndex)
                        },
                        currentSentenceIndex = currentSentenceIndex,
                        onSentenceIndexChange = {
                            currentSentenceIndex = it
                            savePlaybackState(currentChapterIndex, it)
                        },
                        onPause = {
                            ttsService?.stop()
                            stopTtsService()
                        },
                        onImportEpub = {
                            pickEpub.launch(arrayOf("application/epub+zip"))
                        },
                        selectedUri = selectedUri,
                        selectedName = selectedName,
                        chapterTitles = chapterTitles,
                        chapterFiles = chapterFiles,
                        chapterSource = chapterSource,
                        chapterText = chapterText,
                        currentChapterIndex = currentChapterIndex,
                        onLoadChapter = { index ->
                            val uri = selectedUri
                            if (uri != null) {
                                loadChapterAtIndex(uri, chapterFiles, index)
                            }
                        },
                        resumeChapterIndex = resumeChapterIndex,
                        resumeSentenceIndex = resumeSentenceIndex,
                        onResume = {
                            val uri = selectedUri
                            if (uri != null) {
                                loadChapterAtIndex(
                                    uri,
                                    chapterFiles,
                                    resumeChapterIndex,
                                    resumeSentenceIndex
                                )
                            }
                        },
                        onBackToStart = {
                            ttsService?.stop()
                            stopTtsService()
                            chapterText = ""
                        },
                    )
                }
            }
        }
    }

    private fun startTtsService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notificationPermission.launch(
                    android.Manifest.permission.POST_NOTIFICATIONS
                )
                return
            }
        }
        val intent = Intent(this, com.ajz.ereader.tts.TtsService::class.java)
        ContextCompat.startForegroundService(this, intent)
    }

    private fun stopTtsService() {
        val intent = Intent(this, com.ajz.ereader.tts.TtsService::class.java)
        stopService(intent)
    }

    private fun getDisplayName(uriString: String): String? {
        val uri = android.net.Uri.parse(uriString)
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) {
                return cursor.getString(nameIndex)
            }
        }
        return null
    }

    private fun loadEpubChapters(
        uriString: String,
        resumeChapterIndex: Int,
        resumeSentenceIndex: Int
    ) {
        lifecycleScope.launch {
            val (files, source, titles) = withContext(Dispatchers.IO) {
                val opfPath = findOpfPath(uriString)
                val opfData = if (opfPath != null) {
                    parseOpfData(uriString, opfPath)
                } else {
                    OpfData(emptyList(), null, null)
                }

                if (opfData.spineFiles.isNotEmpty()) {
                    val tocMap = parseTocTitles(
                        uriString,
                        opfData.navPath,
                        opfData.ncxPath
                    )
                    val mappedTitles = opfData.spineFiles.map { path ->
                        tocMap[path] ?: path.substringAfterLast('/')
                    }
                    Triple(opfData.spineFiles, "spine", mappedTitles)
                } else {
                    val uri = android.net.Uri.parse(uriString)
                    val fallback =
                        contentResolver.openInputStream(uri)?.use{ input ->
                        ZipInputStream(input).use { zip ->
                            val list =
                                mutableListOf<String>()
                            var entry = zip.nextEntry
                            while (entry != null) {
                                val name = entry.name
                                val lower =
                                    name.lowercase(Locale.US)
                                if (!entry.isDirectory &&
                                    (lower.endsWith(".xhtml")
                                            ||

                                            lower.endsWith(".html") ||

                                            lower.endsWith(".htm"))
                                ) {
                                    list.add(name)
                                }
                                entry = zip.nextEntry
                            }
                            list.sorted()
                        }
                    } ?: emptyList()
                    Triple(
                        fallback,
                        "fallback",
                        fallback.map { it.substringAfterLast('/') }
                    )
                }
            }
            chapterFiles = files
            chapterTitles = titles
            chapterSource = source
            currentChapterIndex = if (files.isNotEmpty()) {
                resumeChapterIndex.coerceIn(0, files.lastIndex)
            } else {
                0
            }
            currentSentenceIndex = resumeSentenceIndex
        }
    }

    private fun readZipEntry(uriString: String, entryName:
    String): ByteArray? {
        val uri = android.net.Uri.parse(uriString)
        contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (entry.name == entryName) {
                        val buffer = ByteArrayOutputStream()
                        val data = ByteArray(8_192)
                        var count = zip.read(data)
                        while (count != -1) {
                            buffer.write(data, 0, count)
                            count = zip.read(data)
                        }
                        return buffer.toByteArray()
                    }
                    entry = zip.nextEntry
                }
            }
        }
        return null
    }

    private fun loadChapterAtIndex(
        uriString: String,
        entries: List<String>,
        index: Int,
        sentenceIndex: Int = 0
    ) {
        if (index < 0 || index >= entries.size) return
        lifecycleScope.launch {
            val text = withContext(Dispatchers.IO) {
                val entryName = entries[index]
                val bytes = readZipEntry(uriString, entryName) ?: return@withContext ""
                val html = bytes.toString(Charsets.UTF_8)
                extractTextFromHtml(html)
            }
            chapterText = if (text.isBlank()) "No readable text found" else text
            currentChapterIndex = index
            currentSentenceIndex = sentenceIndex
            savePlaybackState(currentChapterIndex, currentSentenceIndex)
        }
    }

    private fun findOpfPath(uriString: String): String? {
        val bytes = readZipEntry(uriString, "META-INF/container.xml") ?: return null
        val parser = Xml.newPullParser()
        parser.setInput(bytes.inputStream(), null)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name ==
                "rootfile") {
                val path = getAttr(parser, "full-path")
                return path?.removePrefix("/")
            }
            event = parser.next()
        }
        return null
    }

    private fun parseOpfData(uriString: String, opfPath: String): OpfData {
        val normalizedPath = opfPath.removePrefix("/")
        val bytes = readZipEntry(uriString, normalizedPath) ?: return OpfData(
            emptyList(),
            null,
            null
        )
        val parser = Xml.newPullParser()
        parser.setInput(bytes.inputStream(), null)

        val manifest = mutableMapOf<String, String>()
        val properties = mutableMapOf<String, String>()
        val mediaTypes = mutableMapOf<String, String>()
        val spine = mutableListOf<String>()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "item" -> {
                        val id = getAttr(parser, "id")
                        val href = getAttr(parser, "href")
                        val props = getAttr(parser, "properties")
                        val mediaType = getAttr(parser, "media-type")
                        if (id != null && href != null) {
                            manifest[id] = href
                            if (props != null) properties[id] = props
                            if (mediaType != null) mediaTypes[id] = mediaType
                        }
                    }
                    "itemref" -> {
                        val idref = getAttr(parser, "idref")
                        if (idref != null) {
                            spine.add(idref)
                        }
                    }
                }
            }
            event = parser.next()
        }

        val baseDir = normalizedPath.substringBeforeLast('/', "")
        val spineFiles = spine.mapNotNull { id ->
            val href = manifest[id] ?: return@mapNotNull null
            resolvePath(baseDir, href)
        }
        val navId = properties.entries.firstOrNull { it.value.contains("nav") }?.key
        val navPath = navId?.let { id ->
            manifest[id]?.let { href -> resolvePath(baseDir, href) }
        }
        val ncxId = mediaTypes.entries.firstOrNull {
            it.value == "application/x-dtbncx+xml"
        }?.key
        val ncxPath = ncxId?.let { id ->
            manifest[id]?.let { href -> resolvePath(baseDir, href) }
        }
        return OpfData(spineFiles, navPath, ncxPath)
    }

    private fun getAttr(parser: XmlPullParser, name: String):
            String? {
        for (i in 0 until parser.attributeCount) {
            if (parser.getAttributeName(i) == name) {
                return parser.getAttributeValue(i)
            }
        }
        return null
    }

    private fun extractTextFromHtml(html: String): String {
        val withoutHead = html
            .replace(Regex("(?is)<head.*?>.*?</head>"), " ")
            .replace(Regex("(?is)<style.*?>.*?</style>"), " ")
            .replace(Regex("(?is)<script.*?>.*?</script>"), " ")
        val spanned = HtmlCompat.fromHtml(
            withoutHead,
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )
        val text = spanned.toString()
        val noFootnotes = text.replace(Regex("\\[(\\d{1,3})\\]"), "")
        return noFootnotes
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun parseTocTitles(
        uriString: String,
        navPath: String?,
        ncxPath: String?
    ): Map<String, String> {
        val navMap = navPath?.let { parseNavTitles(uriString, it) }.orEmpty()
        if (navMap.isNotEmpty()) return navMap
        val ncxMap = ncxPath?.let { parseNcxTitles(uriString, it) }.orEmpty()
        return ncxMap
    }

    private fun parseNavTitles(uriString: String, navPath: String): Map<String, String> {
        val bytes = readZipEntry(uriString, navPath) ?: return emptyMap()
        val parser = Xml.newPullParser()
        parser.setInput(bytes.inputStream(), null)
        val baseDir = navPath.substringBeforeLast('/', "")

        val map = mutableMapOf<String, String>()
        var event = parser.eventType
        var inTocNav = false
        var inLink = false
        var currentHref: String? = null
        val textBuilder = StringBuilder()

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "nav") {
                        inTocNav = hasTocType(parser)
                    } else if (inTocNav && parser.name == "a") {
                        currentHref = getAttr(parser, "href")
                        if (currentHref != null) {
                            inLink = true
                            textBuilder.setLength(0)
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inLink) {
                        textBuilder.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "a" && inLink) {
                        val title = textBuilder.toString().trim()
                        val href = currentHref?.let { stripFragment(it) }
                        if (!title.isBlank() && href != null) {
                            val resolved = resolvePath(baseDir, href)
                            map[resolved] = title
                        }
                        inLink = false
                        currentHref = null
                    } else if (parser.name == "nav" && inTocNav) {
                        inTocNav = false
                    }
                }
            }
            event = parser.next()
        }
        return map
    }

    private fun parseNcxTitles(uriString: String, ncxPath: String): Map<String, String> {
        val bytes = readZipEntry(uriString, ncxPath) ?: return emptyMap()
        val parser = Xml.newPullParser()
        parser.setInput(bytes.inputStream(), null)
        val baseDir = ncxPath.substringBeforeLast('/', "")

        val map = mutableMapOf<String, String>()
        var event = parser.eventType
        var inNavPoint = false
        var inText = false
        var currentSrc: String? = null
        val textBuilder = StringBuilder()

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "navPoint") {
                        inNavPoint = true
                        currentSrc = null
                        textBuilder.setLength(0)
                    } else if (inNavPoint && parser.name == "content") {
                        currentSrc = getAttr(parser, "src")
                    } else if (inNavPoint && parser.name == "text") {
                        inText = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inText) {
                        textBuilder.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "text") {
                        inText = false
                    } else if (parser.name == "navPoint" && inNavPoint) {
                        val title = textBuilder.toString().trim()
                        val href = currentSrc?.let { stripFragment(it) }
                        if (!title.isBlank() && href != null) {
                            val resolved = resolvePath(baseDir, href)
                            map[resolved] = title
                        }
                        inNavPoint = false
                    }
                }
            }
            event = parser.next()
        }
        return map
    }

    private fun hasTocType(parser: XmlPullParser): Boolean {
        for (i in 0 until parser.attributeCount) {
            val name = parser.getAttributeName(i)
            val value = parser.getAttributeValue(i)
            if ((name == "type" || name == "epub:type") && value == "toc") {
                return true
            }
        }
        return false
    }

    private fun stripFragment(href: String): String {
        val hash = href.indexOf('#')
        return if (hash >= 0) href.substring(0, hash) else href
    }

    private fun resolvePath(baseDir: String, href: String): String {
        val cleaned = href.removePrefix("/")
        return if (baseDir.isEmpty()) cleaned else "$baseDir/$cleaned"
    }

    private fun savePlaybackState(chapterIndex: Int, sentenceIndex: Int) {
        lifecycleScope.launch {
            dataStore.edit { prefs ->
                prefs[PrefKeys.playbackChapterIndex] = chapterIndex
                prefs[PrefKeys.playbackSentenceIndex] = sentenceIndex
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, com.ajz.ereader.tts.TtsService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
        }
        super.onStop()
    }

    override fun onDestroy() {
        ttsService?.setSentenceIndexListener(null)
        super.onDestroy()
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TtsScreen(
    onPlay: (String, Float, Int) -> Unit,
    onPlayAll: (List<String>, Float, Int) -> Unit,
    currentSentenceIndex: Int,
    onSentenceIndexChange: (Int) -> Unit,
    onPause: () -> Unit,
    onImportEpub: () -> Unit,
    selectedUri: String?,
    selectedName: String?,
    chapterTitles: List<String>,
    chapterFiles: List<String>,
    chapterText: String,
    currentChapterIndex: Int,
    onLoadChapter: (Int) -> Unit,
    resumeChapterIndex: Int,
    resumeSentenceIndex: Int,
    onResume: () -> Unit,
    onBackToStart: () -> Unit,
    chapterSource: String
) {
    var text by remember(chapterText) {
        mutableStateOf(if (chapterText.isBlank()) "No readable text found" else chapterText)
    }
    var rate by remember { mutableStateOf(1.0f) }
    var showChapterList by remember { mutableStateOf(false) }
    val showStartScreen = chapterText.isBlank()

    fun splitSentences(input: String): List<String> {
        return input
            .split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    fun buildSentencePreview(sentences: List<String>, index: Int): String {
        if (sentences.isEmpty()) return ""
        val start = (index - 1).coerceAtLeast(0)
        val end = (index + 1).coerceAtMost(sentences.size - 1)
        val lines = mutableListOf<String>()
        for (i in start..end) {
            val prefix = if (i == index) "▶ " else ""
            lines.add(prefix + sentences[i])
        }
        return lines.joinToString("\n\n")
    }

    val sentences = splitSentences(text)
    if (sentences.isNotEmpty() && currentSentenceIndex >= sentences.size) {
        onSentenceIndexChange(0)
    }
    val canGoBack = currentSentenceIndex > 0
    val canGoForward = currentSentenceIndex < sentences.size - 1

            if (showChapterList) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Chapters",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    val currentTitle =
                        chapterTitles.getOrNull(currentChapterIndex) ?: "None"
                    Text(
                        text = "Current: $currentTitle",
                        style = MaterialTheme.typography.bodyMedium
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(chapterTitles) { index, title ->
                            val label = if (index == currentChapterIndex) {
                                "▶ $title"
                            } else {
                                title
                            }
                            Button(
                                onClick = {
                                    onLoadChapter(index)
                                    showChapterList = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(label)
                            }
                        }
                        item {
                            OutlinedButton(onClick = { showChapterList = false }) {
                                Text("Back")
                            }
                        }
                    }
                }
            } else if (showStartScreen) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Ereader",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Button(onClick = onImportEpub) {
                        Text("Import EPUB")
                    }
                    val nameText = selectedName?.takeIf { it.isNotBlank() } ?: "No EPUB selected"
                    Text(
                        text = nameText,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    val canResume = chapterTitles.isNotEmpty()
                    OutlinedButton(
                        onClick = onResume,
                        enabled = canResume
                    ) {
                        Text("Resume")
                    }
                    val resumeTitle =
                        chapterTitles.getOrNull(resumeChapterIndex) ?: "Unknown chapter"
                    Text(
                        text = "Resume: $resumeTitle (sentence ${resumeSentenceIndex + 1})",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedButton(onClick = { showChapterList = true }) {
                        Text("Chapter List")
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
        Text(
            text = "Text to Speech",
            style =
                MaterialTheme.typography.headlineMedium
        )
                OutlinedButton(onClick = onBackToStart) {
                    Text("Back to Start")
                }
                OutlinedButton(onClick = { showChapterList = true }) {
                    Text("Chapter List")
                }

                val canPrev = currentChapterIndex > 0
                val canNext = currentChapterIndex < chapterTitles.size - 1
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { if (canPrev) onLoadChapter(currentChapterIndex - 1) },
                        enabled = canPrev
                    ) {
                        Text("Prev Chapter")
                    }
                    OutlinedButton(
                        onClick = { if (canNext) onLoadChapter(currentChapterIndex + 1) },
                        enabled = canNext
                    ) {
                        Text("Next Chapter")
                    }
                }

                Text(
                    text = "Chapter: ${currentChapterIndex + 1} / ${chapterTitles.size}",
                    style = MaterialTheme.typography.bodySmall
                )
                val currentTitle =
                    chapterTitles.getOrNull(currentChapterIndex) ?: "No chapter"
                Text(
                    text = currentTitle,
                    style = MaterialTheme.typography.bodyMedium
                )

                val selectedTitle =
                    chapterTitles.getOrNull(currentChapterIndex) ?: "Select chapter"
                var menuExpanded by remember { mutableStateOf(false) }

                ExposedDropdownMenuBox(
                    expanded = menuExpanded,
                    onExpandedChange = { menuExpanded = !menuExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedTitle,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Chapter") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(
                                expanded = menuExpanded
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        chapterTitles.forEachIndexed { index, title ->
                            DropdownMenuItem(
                                text = { Text(title) },
                                onClick = {
                                    menuExpanded = false
                                    onLoadChapter(index)
                                }
                            )
                        }
                    }
                }

                val nameText = selectedName?.takeIf { it.isNotBlank() } ?: "No EPUB selected"
                Text(
                    text = nameText,
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = "Chapter files found: ${chapterTitles.size}",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text(
                    text = "Source: $chapterSource",
                    style = MaterialTheme.typography.bodySmall
                )

                val firstFile = chapterFiles.firstOrNull() ?: "none"
                Text(
                    text = "First file: $firstFile",
                    style = MaterialTheme.typography.bodySmall
                )

                val chapterPreview = chapterTitles.take(5).joinToString(", ")
                if (chapterPreview.isNotBlank()) {
                    Text(
                        text = chapterPreview,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                val preview = buildSentencePreview(sentences, currentSentenceIndex)
                OutlinedTextField(
                    value = preview,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Text to speak (preview)") }
                )
        Text(text = "Rate: ${"%.1f".format(rate)}")

        Slider(
            value = rate,
            onValueChange = { rate = it },
            valueRange = 0.5f..2.0f
                )

                Row(horizontalArrangement =
                    Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = { if (canGoBack) onSentenceIndexChange(currentSentenceIndex - 1) },
                        enabled = canGoBack
                    ) {
                        Text("Back")
                    }

                    Button(onClick = onPause) {
                        Text("Stop")
                    }

                    Button(onClick = { onPlayAll(sentences, rate, currentSentenceIndex) }) {
                        Text("Read")
                    }
                    OutlinedButton(
                        onClick = { if (canGoForward)
                            onSentenceIndexChange(currentSentenceIndex + 1) },
                        enabled = canGoForward
                    ) {
                        Text("Forward")
                    }

                    OutlinedButton(onClick = onPause) {
                        Text("Pause")
                    }
                }

            }
        }
    }
