package app.steadmo

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import app.steadmo.core.CamapsGoal
import app.steadmo.core.CamapsParser
import app.steadmo.core.CamapsTask
import app.steadmo.core.CamapsStep
import app.steadmo.core.RepeatGuard
import app.steadmo.core.ScreenNode
import app.steadmo.core.SteadmoLog

/**
 * CamAPS FX shortcuts (SPEC.md §4.6): after a meal, Boost or Ease-off button
 * opens CamAPS FX, taps through to that screen, and types a meal's grams. [CamapsTask] decides every
 * step; this service only reads CamAPS FX's screen and does what it says.
 * The service config limits it to CamAPS FX's packages.
 */
class CamapsShortcutsService : AccessibilityService() {
    private val handler = Handler(Looper.getMainLooper())

    public override fun onServiceConnected() {
        SteadmoLog.event("shortcuts: connected")
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        SteadmoLog.event("shortcuts: disconnected")
        return super.onUnbind(intent)
    }

    override fun onInterrupt() {
        SteadmoLog.event("shortcuts: interrupted")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.packageName?.toString()?.startsWith(CamapsParser.PACKAGE_PREFIX) != true) return
        val task = CamapsShortcuts.pending ?: return
        if (timed !== task) {
            // Gives up on time even if CamAPS FX's screen never changes again.
            timed = task
            val late = task.startedAt + CamapsTask.TIMEOUT_MILLIS + 1
            val due = late - System.currentTimeMillis()
            handler.postDelayed({ step(task, newScreen = true, now = late) }, due.coerceAtLeast(0))
        }
        step(task, newScreen = event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
    }

    private var timed: CamapsTask? = null

    /** Reads the screen and does what [task] says. [newScreen] logs the screen's structure. */
    internal fun step(task: CamapsTask, newScreen: Boolean, now: Long = System.currentTimeMillis()) {
        if (CamapsShortcuts.pending !== task) return
        if (now - task.startedAt > CamapsTask.TIMEOUT_MILLIS) {
            finish(task, "gave up: not there within ${CamapsTask.TIMEOUT_MILLIS / 1000} s", fail = true)
            return
        }
        // The service config limits events to CamAPS FX, not the active window:
        // if the user has left CamAPS FX, never read, log or tap what's there.
        val root = rootInActiveWindow
        if (root?.packageName?.toString()?.startsWith(CamapsParser.PACKAGE_PREFIX) != true) {
            if (newScreen) SteadmoLog.event("shortcuts: waiting; CamAPS FX isn't in front")
            return
        }
        val screen = CamapsShortcuts.read(root)
        val step = task.next(screen, now)
        if (newScreen || step !is CamapsStep.Wait) {
            SteadmoLog.event("shortcuts: screen\n%s", screen.describe().trimEnd())
        }
        when (step) {
            CamapsStep.Wait -> if (newScreen) SteadmoLog.event("shortcuts: waiting; nothing to do on this screen")
            is CamapsStep.Tap -> {
                val ok = CamapsShortcuts.find(root, step.path, screen)?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
                SteadmoLog.event("shortcuts: tapped \"%s\" at %s: %s", step.label, step.path, if (ok) "done" else "failed")
                when {
                    !ok -> finish(task, "gave up: couldn't tap \"${step.label}\"", fail = true)
                    task.finished -> {
                        finish(task, "tapped ${step.label}, done", fail = false)
                        toast(
                            when (val goal = task.goal) {
                                is CamapsGoal.Mode -> "Set ${goal.name} in CamAPS FX, then confirm."
                                // A meal's last step is typing, never a tap: only Other gets here.
                                else -> "Enter the grams in CamAPS FX, then confirm."
                            },
                        )
                    }
                }
            }
            is CamapsStep.Fill -> {
                val field = CamapsShortcuts.find(root, step.path, screen)
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, step.text)
                }
                field?.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                val ok = field?.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args) == true
                if (!ok) {
                    finish(task, "gave up: couldn't type into the field at ${step.path}", fail = true, entered = false)
                } else {
                    // Prefill only: Confirm stays with the user.
                    finish(task, "typed ${step.text} g at ${step.path}; done, the user confirms", fail = false)
                    toast("${step.text} g entered. Check it, then confirm in CamAPS FX.")
                }
            }
            is CamapsStep.Done -> {
                finish(task, "done: ${step.reason}", fail = false)
                toast("Enter the grams in CamAPS FX, then confirm.")
            }
            is CamapsStep.GiveUp -> finish(task, "gave up: ${step.reason}", fail = true)
        }
    }

    /** [entered] is whether the grams reached CamAPS FX: then the user may confirm them. */
    private fun finish(task: CamapsTask, outcome: String, fail: Boolean, entered: Boolean = task.entered) {
        CamapsShortcuts.clear(task)
        if (fail) {
            SteadmoLog.warning("shortcuts: %s", outcome)
            // Once grams are typed the user may confirm them, so the one-minute
            // guard stays; before that, trying again is fine.
            if (!entered) CamapsShortcuts.forget(this, task)
            val goal = task.goal
            toast(
                when {
                    goal is CamapsGoal.Meal -> "Couldn't find CamAPS FX's meal entry. ${goal.grams} g is copied to paste."
                    goal is CamapsGoal.Mode -> "Couldn't find ${goal.name} in CamAPS FX."
                    else -> "Couldn't find CamAPS FX's meal entry."
                },
            )
        } else {
            SteadmoLog.event("shortcuts: %s", outcome)
        }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()
}

/** The pending task, and how Steadmo reads and finds views on CamAPS FX's screen. */
object CamapsShortcuts {
    @Volatile var pending: CamapsTask? = null
        private set

    /** Whether the user has turned on Steadmo's accessibility service. */
    fun enabled(context: Context): Boolean {
        val ours = ComponentName(context, CamapsShortcutsService::class.java)
        val list = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return list.orEmpty().split(':').any { ComponentName.unflattenFromString(it) == ours }
    }

    fun start(goal: CamapsGoal, now: Long = System.currentTimeMillis()) {
        SteadmoLog.event("shortcuts: starting %s", goal)
        pending = CamapsTask(goal, now)
    }

    /**
     * Starts [goal] unless the same shortcut ran in the last minute
     * ([RepeatGuard]), which survives the process restarting. Returns whether
     * it started.
     */
    fun begin(context: Context, goal: CamapsGoal, now: Long = System.currentTimeMillis()): Boolean {
        val key = RepeatGuard.key(goal)
        val prefs = prefs(context)
        val last = prefs.getLong(key, 0L).takeIf { it > 0 }
        if (!RepeatGuard.allows(last, now)) {
            SteadmoLog.event("shortcuts: not starting %s; the last ran %s ms ago", goal, now - last!!)
            return false
        }
        prefs.edit().putLong(key, now).apply()
        start(goal, now)
        return true
    }

    /** Lets [task]'s shortcut run again at once, when it failed before entering anything. */
    fun forget(context: Context, task: CamapsTask) {
        val key = RepeatGuard.key(task.goal)
        val prefs = prefs(context)
        if (prefs.getLong(key, 0L) == task.startedAt) prefs.edit().remove(key).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences("camaps_shortcuts", Context.MODE_PRIVATE)

    fun clear(task: CamapsTask? = pending) {
        if (pending === task) pending = null
    }

    fun read(node: AccessibilityNodeInfo): ScreenNode = ScreenNode(
        // An empty field reports its hint as its text; that's not what's typed in it.
        text = node.text?.toString()?.takeUnless { node.isShowingHintText },
        description = node.contentDescription?.toString(),
        hint = node.hintText?.toString(),
        viewId = node.viewIdResourceName,
        className = node.className?.toString(),
        clickable = node.isClickable,
        editable = node.isEditable,
        children = visibleChildren(node).map(::read),
    )

    /**
     * The view at [path], counting only children [read] saw, if it's still the
     * one [screen] had there: the screen can change between reading it and
     * acting, and Steadmo must never tap or type into a different view.
     */
    fun find(root: AccessibilityNodeInfo, path: List<Int>, screen: ScreenNode): AccessibilityNodeInfo? {
        val node = path.fold(root as AccessibilityNodeInfo?) { node, i -> node?.let { visibleChildren(it).getOrNull(i) } }
            ?: return null
        val expected = path.fold(screen as ScreenNode?) { s, i -> s?.children?.getOrNull(i) } ?: return null
        val now = read(node)
        val same = now.copy(children = emptyList()) == expected.copy(children = emptyList())
        if (!same) SteadmoLog.warning("shortcuts: the view at %s changed before acting on it", path)
        return node.takeIf { same }
    }

    /** Children on screen: a pager keeps its other pages in the tree, and they must not be tapped. */
    private fun visibleChildren(node: AccessibilityNodeInfo) =
        (0 until node.childCount).mapNotNull { node.getChild(it) }.filter { it.isVisibleToUser }
}
