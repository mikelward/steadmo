package app.steadmo.core

/** One view on another app's screen, as Android accessibility reports it. */
data class ScreenNode(
    val text: String? = null,
    val description: String? = null,
    val hint: String? = null,
    /** e.g. `com.camdiab.fx_alert.mmoll:id/carbs`. */
    val viewId: String? = null,
    val className: String? = null,
    val clickable: Boolean = false,
    val editable: Boolean = false,
    val children: List<ScreenNode> = emptyList(),
) {
    /**
     * The screen's structure for the debug log, one view per line, with every
     * digit masked so no glucose or dose value is written down (SPEC.md §4.6).
     */
    fun describe(): String = buildString { describe(this, 0) }

    private fun describe(out: StringBuilder, depth: Int) {
        val parts = listOfNotNull(
            className?.substringAfterLast('.'),
            viewId?.substringAfter(":id/")?.let { "#$it" },
            text?.let { "\"${mask(it)}\"" },
            description?.let { "[${mask(it)}]" },
            hint?.let { "{${mask(it)}}" },
            "clickable".takeIf { clickable },
            "editable".takeIf { editable },
        )
        out.append("  ".repeat(depth)).append(parts.joinToString(" ")).append('\n')
        children.forEach { it.describe(out, depth + 1) }
    }

    private companion object {
        const val MAX_TEXT = 40

        fun mask(text: String) = text.replace(Regex("""\d"""), "#").replace('\n', ' ').take(MAX_TEXT)
    }
}

/** What [CamapsTask] wants done next. A path is the child indices from the root. */
sealed interface CamapsStep {
    /** Type [text] into the field at [path]. Typing only: nothing is pressed after it. */
    data class Fill(val path: List<Int>, val text: String) : CamapsStep

    /** Tap the button at [path], which reads [label], on the way to the task's screen. */
    data class Tap(val path: List<Int>, val label: String) : CamapsStep

    /** There: nothing more to do. */
    data class Done(val reason: String) : CamapsStep

    /** Nothing to do on this screen yet; it may still be loading. */
    data object Wait : CamapsStep

    data class GiveUp(val reason: String) : CamapsStep
}

/** What a Steadmo button wants done in CamAPS FX. */
sealed interface CamapsGoal {
    /** Enter [grams] of carbs in the meal entry. */
    data class Meal(val grams: Int) : CamapsGoal

    /** Open the meal entry, for the user to type the grams (the Other button). */
    data object MealEntry : CamapsGoal

    /** Open Boost, or Ease-off, from the home screen, for the user to set and confirm. */
    data class Mode(val name: String) : CamapsGoal

    companion object {
        val BOOST = Mode("Boost")
        val EASE_OFF = Mode("Ease-off")
    }
}

/**
 * Takes CamAPS FX from wherever it opens to the screen [goal] needs
 * (SPEC.md §4.6), and stops before anything that doses.
 *
 * A meal: in CamAPS FX v1.4 that is the meal tab (an unlabeled icon, see
 * [tab]), then the Carbs "---" cell, then typing the grams into the
 * Carbohydrates dialog and, up to [MAX_CONFIRM_GRAMS], tapping its Confirm,
 * which only enters the carbs; Next and delivering stay with the user. Boost or Ease-off:
 * the home tab if needed, then its button, which opens CamAPS FX's own
 * dialog to set and confirm.
 *
 * It only taps buttons that read like the way there, never one that reads
 * like confirming, delivering or changing a setting, nothing on a form, and
 * nothing after its last step. Unsure means stop: two candidate fields or
 * buttons, a form with no carbs field, or not there after [MAX_TAPS] taps or
 * [TIMEOUT_MILLIS] all give up, and a meal's grams stay on the clipboard to
 * paste instead. A field that mentions insulin is never a carbs field.
 */
class CamapsTask(val goal: CamapsGoal, val startedAt: Long) {
    private val tapped = mutableSetOf<String>()

    /** Set once a meal's grams are typed, while waiting to tap the dialog's Confirm. */
    private var typed = false

    /** Whether this task has entered anything in CamAPS FX (a meal's grams). */
    val entered: Boolean get() = typed

    var finished = false
        private set

    fun next(screen: ScreenNode, now: Long): CamapsStep {
        if (finished) return CamapsStep.Wait
        if (now - startedAt > TIMEOUT_MILLIS) return giveUp("not there within ${TIMEOUT_MILLIS / 1000} s")
        return when (goal) {
            is CamapsGoal.Meal -> meal(goal.grams, screen)
            is CamapsGoal.Mode -> mode(goal.name, screen)
            CamapsGoal.MealEntry -> mealEntry(screen)
        }
    }

    /** Like [meal], but stops once the carbs field is open, leaving the typing to the user. */
    private fun mealEntry(screen: ScreenNode): CamapsStep {
        if (carbFields(screen).isNotEmpty()) {
            finished = true
            return CamapsStep.Done("the carbs field is open")
        }
        if (hasField(screen)) return giveUp("a form with no field for carbs")
        return tapOnce(
            buttons(screen).ifEmpty { listOfNotNull(tab(screen, 1)) }
                .sortedBy { (_, label) -> NAVIGATION.indexOfFirst { it.containsMatchIn(label) } },
        )
    }

    private fun mode(name: String, screen: ScreenNode): CamapsStep {
        val wanted = Regex("""^\s*${name.replace("-", """[\s-]*""")}\s*$""", RegexOption.IGNORE_CASE)
        val found = mutableListOf<List<Int>>()
        walk(screen, emptyList(), null) { node, path, _ ->
            if (node.clickable && !node.editable && !CONTROLS.containsMatchIn(node.className.orEmpty()) &&
                wanted.matches(texts(node).joinToString(" ").trim())
            ) {
                found += path
            }
        }
        if (found.size > 1) return giveUp("${found.size} buttons read $name")
        // Boost's and Ease-off's own dialogs are where they're started: on a form or a
        // dialog, a button reading the mode's name may start it, so Steadmo taps nothing.
        if (hasField(screen)) return giveUp("a form, not the home screen")
        if (clickableLabels(screen).any { DISMISS.containsMatchIn(it) }) {
            return giveUp("a dialog, not the home screen")
        }
        found.singleOrNull()?.let {
            finished = true
            return CamapsStep.Tap(it, name)
        }
        return tapOnce(listOfNotNull(tab(screen, 0)))
    }

    private fun meal(grams: Int, screen: ScreenNode): CamapsStep {
        val fields = carbFields(screen)
        if (fields.size > 1) return giveUp("${fields.size} fields look like carbs")
        fields.singleOrNull()?.let { field ->
            if (!typed) {
                typed = true
                finished = grams > MAX_CONFIRM_GRAMS || !isCarbsDialog(screen)
                return CamapsStep.Fill(field, grams.toString())
            }
            // Confirm only enters the carbs into the bolus calculator; Next and
            // delivering stay with the user. Only once the field shows the grams.
            if (!isCarbsDialog(screen)) return giveUp("not the carbs dialog any more")
            val shown = nodeAt(screen, field).text?.trim()
            // An event from focusing the field can arrive before ACTION_SET_TEXT updates it.
            if (shown.isNullOrEmpty()) return CamapsStep.Wait
            if (shown != grams.toString()) return giveUp("the carbs field shows \"$shown\", not $grams")
            val confirm = mutableListOf<List<Int>>()
            walk(screen, emptyList(), null) { node, path, _ ->
                if (node.clickable && !node.editable && texts(node).joinToString(" ").trim().equals("Confirm", ignoreCase = true)) {
                    confirm += path
                }
            }
            if (confirm.size != 1) return giveUp("${confirm.size} Confirm buttons by the carbs field")
            finished = true
            return CamapsStep.Tap(confirm.single(), "Confirm")
        }
        if (typed) return CamapsStep.Wait

        // A form's buttons are where confirming lives: on a screen with any
        // field, Steadmo types into the carbs field or does nothing.
        if (hasField(screen)) return giveUp("a form with no field for carbs")

        return tapOnce(
            buttons(screen).ifEmpty { listOfNotNull(tab(screen, 1)) }
                .sortedBy { (_, label) -> NAVIGATION.indexOfFirst { it.containsMatchIn(label) } },
        )
    }

    /** Taps the first of [buttons] not tapped already, or waits. */
    private fun tapOnce(buttons: List<Pair<List<Int>, String>>): CamapsStep {
        val button = buttons.firstOrNull { (_, label) -> label !in tapped } ?: return CamapsStep.Wait
        if (tapped.size >= MAX_TAPS) return giveUp("not there after $MAX_TAPS taps")
        tapped += button.second
        return CamapsStep.Tap(button.first, button.second)
    }

    private fun giveUp(reason: String): CamapsStep {
        finished = true
        return CamapsStep.GiveUp(reason)
    }

    companion object {
        const val TIMEOUT_MILLIS = 20_000L
        const val MAX_TAPS = 3

        /** Carbs above this are typed but left for the user to confirm. */
        const val MAX_CONFIRM_GRAMS = 60

        private val CARBS = Regex("""carb|meal|\bcho\b|\bgrams?\b|\(g\)""", RegexOption.IGNORE_CASE)

        /** Anything about insulin rules a field out: typing grams into a dose would be dangerous. */
        private val INSULIN = Regex("""insulin|bolus|dose|basal|\bunits?\b|\bu\b|\(u\)|iob""", RegexOption.IGNORE_CASE)

        /** The way to the meal screen, best first. */
        private val NAVIGATION = listOf(
            Regex("""meal|carb""", RegexOption.IGNORE_CASE),
            Regex("""bolus\s*(calculator|wizard)|calculator""", RegexOption.IGNORE_CASE),
            Regex("""(?<![a-z])bolus(?![a-z])""", RegexOption.IGNORE_CASE),
        )

        /**
         * Never tapped, even if it also reads like the way to the meal screen:
         * anything that acts, and anything about a dose ("Bolus 2 U", "Give
         * bolus", "Bolus now"). A bolus button with any number on it is [DOSED].
         */
        private val NEVER = Regex(
            """deliver|confirm|start|send|\bok\b|yes|apply|accept|save|done|next|continue|submit|""" +
                """boost|ease|stop|suspend|resume|cancel|delete|remove|auto\s*mode|prime|fill|""" +
                """basal|temp|alarm|alert|setting|pair|sensor|pump|units?\b|\bi?u\b|""" +
                """give|inject|dose|insulin|\bnow\b""",
            RegexOption.IGNORE_CASE,
        )

        /** A bolus button showing an amount ("Bolus 2.5") may deliver it. */
        private val DOSED = Regex("""bolus.*\d|\d.*bolus""", RegexOption.IGNORE_CASE)

        /** A button that closes a dialog: its presence means the screen is one. */
        private val DISMISS = Regex("""^\s*(cancel|close|not now|no|dismiss)\s*$""", RegexOption.IGNORE_CASE)

        /** Controls that change a value when tapped, never navigation. */
        private val CONTROLS = Regex("""SeekBar|Slider|Switch|CheckBox|RadioButton|ToggleButton|Spinner""")

        /** Editable fields that are about carbs and not about insulin. */
        fun carbFields(screen: ScreenNode): List<List<Int>> {
            val found = mutableListOf<List<Int>>()
            walk(screen, emptyList(), null) { node, path, parent ->
                if (!node.editable) return@walk
                val own = listOfNotNull(node.hint, node.description, node.viewId?.substringAfter(":id/"))
                val siblings = parent?.children.orEmpty().filter { it !== node }
                val labels = siblings.filter { !it.editable }.flatMap { texts(it) }
                val onlyField = siblings.none { it.editable }
                val context = own + labels
                if (context.any { INSULIN.containsMatchIn(it) }) return@walk
                val carbs = own.any { CARBS.containsMatchIn(it) } ||
                    (onlyField && labels.any { CARBS.containsMatchIn(it) })
                if (carbs) found += path
            }
            if (found.isEmpty()) {
                // A dialog with one field, titled for carbs, such as CamAPS FX's
                // "Carbohydrates" with its field hinted "Value". Anything about
                // insulin anywhere on screen rules it out.
                val fields = mutableListOf<List<Int>>()
                walk(screen, emptyList(), null) { node, path, _ -> if (node.editable) fields += path }
                val field = fields.singleOrNull()?.let { nodeAt(screen, it) }
                val all = texts(screen) + listOfNotNull(field?.hint, field?.viewId?.substringAfter(":id/"))
                if (fields.size == 1 && all.any { CARBS.containsMatchIn(it) } && all.none { INSULIN.containsMatchIn(it) }) {
                    found += fields.single()
                }
            }
            return found
        }

        /**
         * Only a dialog like CamAPS FX's Carbohydrates may be confirmed: one field,
         * carbs on it, and nothing about insulin anywhere, ids included.
         */
        fun isCarbsDialog(screen: ScreenNode): Boolean {
            var fields = 0
            val all = mutableListOf<String>()
            walk(screen, emptyList(), null) { node, _, _ ->
                if (node.editable) fields++
                all += listOfNotNull(node.text, node.description, node.hint, node.viewId?.substringAfter(":id/"))
            }
            return fields == 1 && all.any { CARBS.containsMatchIn(it) } && all.none { INSULIN.containsMatchIn(it) }
        }

        private fun nodeAt(screen: ScreenNode, path: List<Int>) = path.fold(screen) { node, i -> node.children[i] }

        /** Clickable views and what they read, minus any that must never be tapped. */
        fun buttons(screen: ScreenNode): List<Pair<List<Int>, String>> {
            val found = mutableListOf<Pair<List<Int>, String>>()
            walk(screen, emptyList(), null) { node, path, parent ->
                if (!node.clickable || node.editable) return@walk
                if (CONTROLS.containsMatchIn(node.className.orEmpty())) return@walk
                var label = (texts(node) + listOfNotNull(node.viewId?.substringAfter(":id/")))
                    .joinToString(" ").trim()
                if (label.isNotEmpty() && !label.any { it.isLetter() } && parent != null) {
                    // A value cell such as CamAPS FX's Carbs "---" reads as the label before it.
                    val before = parent.children.take(parent.children.indexOfFirst { it === node })
                        .lastOrNull { !it.clickable && texts(it).any { t -> t.any(Char::isLetter) } }
                    if (before != null) label = (texts(before) + label).joinToString(" ").trim()
                }
                if (label.isEmpty() || NEVER.containsMatchIn(label) || DOSED.containsMatchIn(label)) return@walk
                if (NAVIGATION.any { it.containsMatchIn(label) }) found += path to label
            }
            return found
        }

        /**
         * CamAPS FX v1.4's tabs are icons with no label: home, meal, bolus. With
         * exactly three unlabeled tabs, [index] 0 is home and 1 is meal.
         */
        fun tab(screen: ScreenNode, index: Int): Pair<List<Int>, String>? {
            val tabs = mutableListOf<Pair<List<Int>, ScreenNode>>()
            walk(screen, emptyList(), null) { node, path, _ ->
                if (node.className?.endsWith("ActionBar.Tab") == true) tabs += path to node
            }
            if (tabs.size != 3 || tabs.any { (_, tab) -> texts(tab).any { it.isNotBlank() } }) return null
            return tabs[index].first to "tab ${index + 1} of 3"
        }

        private fun clickableLabels(screen: ScreenNode): List<String> {
            val found = mutableListOf<String>()
            walk(screen, emptyList(), null) { node, _, _ ->
                if (node.clickable && !node.editable) found += texts(node).joinToString(" ").trim()
            }
            return found
        }

        private fun hasField(node: ScreenNode): Boolean = node.editable || node.children.any { hasField(it) }

        private fun texts(node: ScreenNode): List<String> =
            listOfNotNull(node.text, node.description) + node.children.flatMap { texts(it) }

        private fun walk(
            node: ScreenNode,
            path: List<Int>,
            parent: ScreenNode?,
            visit: (ScreenNode, List<Int>, ScreenNode?) -> Unit,
        ) {
            visit(node, path, parent)
            node.children.forEachIndexed { i, child -> walk(child, path + i, node, visit) }
        }
    }
}

/**
 * Stops the same shortcut running twice within [WINDOW_MILLIS] (SPEC.md §4.6):
 * a double tap, or a second meal entered by mistake. Any meal counts as the
 * same shortcut, whatever its grams.
 */
object RepeatGuard {
    const val WINDOW_MILLIS = 60_000L

    fun key(goal: CamapsGoal): String = when (goal) {
        is CamapsGoal.Meal -> "meal"
        is CamapsGoal.Mode -> goal.name.lowercase()
        CamapsGoal.MealEntry -> "meal-entry"
    }

    /** Whether a shortcut last run at [last] may run again at [now]. A clock set back allows it. */
    fun allows(last: Long?, now: Long): Boolean = last == null || now < last || now - last >= WINDOW_MILLIS
}
