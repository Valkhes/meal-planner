package dev.valkhes.mealplanner.architecture

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The rules of README "Architecture", checked on the sources of every module. If one fails, fix the
 * dependency; loosening a rule is the owner's decision (CLAUDE.md).
 */
class ArchitectureTest {
    private val root: File = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "settings.gradle.kts").exists() }

    private fun sources(
        module: String,
        sourceSet: String,
    ): List<File> = File(root, "$module/src/$sourceSet/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun imports(file: File): List<String> = file.readLines().filter { it.startsWith("import ") }.map { it.removePrefix("import ").trim() }

    private fun commonMain(): List<File> = listOf("domain", "application", "infrastructure", "ui").flatMap { sources(it, "commonMain") }

    private fun check(
        violations: List<String>,
        rule: String,
    ) {
        if (violations.isNotEmpty()) fail("$rule\n" + violations.joinToString("\n") { "  $it" })
    }

    @Test
    fun the_sources_are_found() {
        assertTrue(commonMain().size > 10, "no sources found under $root")
    }

    /** Common code must compile for iOS one day: no JVM or Android API in it. */
    @Test
    fun common_code_is_portable() {
        val allowedAndroidx = listOf("androidx.compose.", "androidx.sqlite.", "androidx.lifecycle.")
        val violations =
            commonMain().flatMap { file ->
                imports(file)
                    .filter {
                        it.startsWith("java.") ||
                            it.startsWith("javax.") ||
                            it.startsWith("android.") ||
                            (it.startsWith("androidx.") && allowedAndroidx.none(it::startsWith))
                    }.map { "${file.relativeTo(root)}: $it" }
            }
        check(violations, "commonMain must stay portable (no java.*, javax.*, android.*, platform androidx.*):")
    }

    /** The domain is pure Kotlin: dates, its own types, nothing else. */
    @Test
    fun the_domain_depends_on_nothing() {
        val allowed = listOf("kotlin.", "kotlinx.datetime.", "dev.valkhes.mealplanner.domain.")
        val violations =
            sources("domain", "commonMain").flatMap { file ->
                imports(file).filter { imp -> allowed.none(imp::startsWith) }.map { "${file.relativeTo(root)}: $it" }
            }
        check(violations, "domain may only import kotlin.*, kotlinx.datetime.* and itself:")
    }

    /** Time and randomness come from ports (Clock, IdGenerator), so every rule can be tested and replayed. */
    @Test
    fun only_infrastructure_reads_the_system() {
        val forbidden = listOf("Clock.System", "currentSystemDefault", "Random.Default", "Random.next")
        val violations =
            (sources("domain", "commonMain") + sources("application", "commonMain") + sources("ui", "commonMain")).flatMap { file ->
                val text = file.readText()
                forbidden.filter { text.contains(it) }.map { "${file.relativeTo(root)}: $it" }
            }
        check(violations, "only infrastructure reads the system clock, time zone or randomness:")
    }

    /**
     * Bounded contexts talk only through what they are allowed to use. `server` is the facade that
     * assembles them all; `events` is the schema registry every context's codecs plug into.
     */
    @Test
    fun contexts_only_use_what_they_are_allowed_to() {
        val violations = mutableListOf<String>()
        for (module in listOf("domain", "application")) {
            for (file in sources(module, "commonMain")) {
                val context = contextOf(file.readLines().first { it.startsWith("package ") }.removePrefix("package ")) ?: continue
                for (imp in imports(file)) {
                    val used = contextOf(imp) ?: continue
                    if (used != context && used !in ALLOWED.getValue(context)) violations += "${file.relativeTo(root)}: $context uses $used ($imp)"
                }
            }
        }
        check(violations, "a context uses another it is not allowed to (ALLOWED in ArchitectureTest):")
    }

    @Test
    fun every_context_is_declared() {
        val packages =
            listOf("domain", "application").flatMap { sources(it, "commonMain") }.mapNotNull { file ->
                contextOf(file.readLines().first { it.startsWith("package ") }.removePrefix("package "))
            }
        check(packages.distinct().filter { it !in ALLOWED }, "a context with no entry in ALLOWED:")
    }

    /** Every user-facing text is a string resource, translated (README, constraint 8). */
    @Test
    fun the_ui_has_no_hard_coded_text() {
        val literal = Regex("""Text\(\s*"[\p{L}]""")
        val violations =
            sources("ui", "commonMain").flatMap { file ->
                file
                    .readLines()
                    .withIndex()
                    .filter { literal.containsMatchIn(it.value) }
                    .map { "${file.relativeTo(root)}:${it.index + 1}: ${it.value.trim()}" }
            }
        check(violations, "user-facing text must come from composeResources strings:")
    }

    private fun contextOf(name: String): String? = Regex("""^dev\.valkhes\.mealplanner\.(?:domain|application)\.([a-z]+)""").find(name)?.groupValues?.get(1)

    private companion object {
        val ALLOWED: Map<String, Set<String>> =
            mapOf(
                "shared" to emptySet(),
                "events" to setOf("shared", "journal", "cookbook"),
                "journal" to setOf("shared", "events"),
                "cookbook" to setOf("shared", "events"),
                "habits" to setOf("shared", "journal", "cookbook"),
                "assistant" to setOf("shared", "journal", "cookbook", "server"),
                "server" to setOf("shared", "events", "journal", "cookbook", "habits"),
            )
    }
}
