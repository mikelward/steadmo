package app.steadmo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CamapsTaskTest {
    private fun screen(vararg children: ScreenNode) = ScreenNode(className = "FrameLayout", children = children.toList())
    private fun button(text: String, id: String? = null) = ScreenNode(text = text, viewId = id, clickable = true)
    private fun field(hint: String? = null, id: String? = null) = ScreenNode(hint = hint, viewId = id, editable = true)
    private fun label(text: String) = ScreenNode(text = text)
    private fun row(vararg children: ScreenNode) = ScreenNode(className = "LinearLayout", children = children.toList())

    private val home = screen(label("Auto mode"), button("Boost"), button("Ease-off"), button("Bolus"))
    private val mealForm = screen(
        row(label("Carbohydrates (g)"), field()),
        row(label("Insulin (U)"), field()),
        button("Deliver"),
    )

    @Test fun `types the grams into a form and leaves the rest to the user`() {
        val fill = CamapsTask(CamapsGoal.Meal(61), startedAt = 0)
        assertEquals(CamapsStep.Fill(listOf(0, 1), "61"), fill.next(mealForm, now = 1_000))
        assertTrue(fill.finished)
        assertEquals(CamapsStep.Wait, fill.next(mealForm, now = 2_000))
    }

    @Test fun `finds the field by its own hint or id`() {
        assertEquals(listOf(listOf(1)), CamapsTask.carbFields(screen(label("Meal"), field(hint = "Carbs"))))
        assertEquals(listOf(listOf(0)), CamapsTask.carbFields(screen(field(id = "pkg:id/meal_grams"), field(id = "pkg:id/note"))))
    }

    @Test fun `never takes a field about insulin`() {
        assertEquals(emptyList<List<Int>>(), CamapsTask.carbFields(screen(row(label("Meal bolus (U)"), field()))))
        assertEquals(emptyList<List<Int>>(), CamapsTask.carbFields(screen(field(hint = "Carbs"), label("units"))))
    }

    @Test fun `a label shared by two fields marks neither`() {
        assertEquals(emptyList<List<Int>>(), CamapsTask.carbFields(screen(label("Carbs"), field(), field())))
    }

    @Test fun `gives up when two fields look like carbs`() {
        val fill = CamapsTask(CamapsGoal.Meal(20), startedAt = 0)
        val step = fill.next(screen(field(hint = "Carbs"), field(hint = "Meal")), now = 0)
        assertEquals(CamapsStep.GiveUp("2 fields look like carbs"), step)
        assertTrue(fill.finished)
    }

    @Test fun `taps through to the meal screen, best match first`() {
        val fill = CamapsTask(CamapsGoal.Meal(40), startedAt = 0)
        val menu = screen(button("History"), button("Bolus"), button("Meal"))
        assertEquals(CamapsStep.Tap(listOf(2), "Meal"), fill.next(menu, now = 0))
        assertEquals(CamapsStep.Tap(listOf(1), "Bolus"), fill.next(menu, now = 100))
        assertEquals(CamapsStep.Fill(listOf(0, 1), "40"), fill.next(mealForm, now = 200))
    }

    @Test fun `reads a button's label from its children and id`() {
        val icon = ScreenNode(clickable = true, viewId = "pkg:id/btn_bolus", children = listOf(ScreenNode(description = "icon")))
        assertEquals(listOf(listOf(0) to "icon btn_bolus"), CamapsTask.buttons(screen(icon)))
        val calculator = ScreenNode(clickable = true, children = listOf(label("Bolus calculator")))
        assertEquals(listOf(listOf(0) to "Bolus calculator"), CamapsTask.buttons(screen(calculator)))
    }

    @Test fun `never taps anything that confirms, doses or changes a setting`() {
        val dangerous = listOf(
            "Deliver bolus", "Confirm bolus", "Start bolus", "OK", "Boost", "Ease-off", "Stop insulin",
            "Bolus settings", "Meal: save", "Next", "Bolus 2 units", "Auto mode",
            "Bolus 2U", "Bolus 2.5", "2 U bolus", "Give bolus", "Bolus now", "Inject", "Insulin",
        )
        dangerous.forEach { text ->
            assertEquals(text, emptyList<Pair<List<Int>, String>>(), CamapsTask.buttons(screen(button(text))))
        }
        assertEquals(emptyList<Pair<List<Int>, String>>(), CamapsTask.buttons(screen(label("Bolus"), field(hint = "Bolus"))))
    }

    @Test fun `never taps on a form, even one with a meal button`() {
        val fill = CamapsTask(CamapsGoal.Meal(20), startedAt = 0)
        val form = screen(row(label("Insulin"), field()), button("Meal"))
        assertEquals(CamapsStep.GiveUp("a form with no field for carbs"), fill.next(form, now = 0))
    }

    @Test fun `taps each button once, then at most three`() {
        val fill = CamapsTask(CamapsGoal.Meal(20), startedAt = 0)
        val menu = screen(button("Meal"))
        assertTrue(fill.next(menu, 0) is CamapsStep.Tap)
        assertEquals(CamapsStep.Wait, fill.next(menu, 10))
        assertFalse(fill.finished)

        val many = CamapsTask(CamapsGoal.Meal(20), startedAt = 0)
        listOf("Meal", "Carbs", "Bolus").forEach { assertTrue(many.next(screen(button(it)), 0) is CamapsStep.Tap) }
        assertEquals(CamapsStep.GiveUp("not there after 3 taps"), many.next(screen(button("Bolus calculator")), 0))
    }

    @Test fun `gives up after the timeout`() {
        val fill = CamapsTask(CamapsGoal.Meal(20), startedAt = 0)
        assertEquals(CamapsStep.Wait, fill.next(home.copy(children = emptyList()), CamapsTask.TIMEOUT_MILLIS))
        assertEquals(CamapsStep.GiveUp("not there within 20 s"), fill.next(home, CamapsTask.TIMEOUT_MILLIS + 1))
    }

    @Test fun `logs the screen's structure with every digit masked`() {
        val node = ScreenNode(
            className = "android.widget.FrameLayout",
            children = listOf(
                ScreenNode(text = "6.4\nmmol/L", viewId = "pkg:id/bg"),
                ScreenNode(description = "Bolus 2U", hint = "Carbs 45", clickable = true, editable = true),
            ),
        )
        assertEquals(
            "FrameLayout\n  #bg \"#.# mmol/L\"\n  [Bolus #U] {Carbs ##} clickable editable\n",
            node.describe(),
        )
    }

    // CamAPS FX v1.4, from the maintainer's phone: home, the meal tab, and the
    // Carbohydrates dialog its Carbs cell opens.
    private fun tab() = ScreenNode(className = "androidx.appcompat.app.ActionBar.Tab", clickable = true,
        children = listOf(ScreenNode(className = "android.widget.ImageView")))
    private val tabs = row(tab(), tab(), tab())
    private val camapsHome = screen(
        ScreenNode(description = "Open navigation drawer", clickable = true), label("CamAPS FX"), tabs,
        label("5.8"), label("mmol/L"), button("Boost"), button("Ease-off"),
        row(label("Auto mode"), label("On"), ScreenNode(className = "android.widget.Switch", clickable = true)),
    )
    private val camapsMeal = screen(
        label("CamAPS FX"), tabs, label("Meal size"),
        ScreenNode(className = "android.widget.SeekBar", clickable = true, description = "Meal size"),
        row(
            row(label("Glucose"), label("mmol/L")), button("5.8"), label("="), label("0.0"),
            row(label("Carbs"), label("g CHO")), button("---"), label("="), label("0.0"),
        ),
        label("0.0"), label("Total Units"), button("Cancel"), button("Next"),
    )
    private val camapsDialog = screen(
        label("Carbohydrates"), ScreenNode(description = "Close", clickable = true),
        row(field(hint = "Value")), label("g CHO"), button("Cancel"), button("Confirm"),
    )

    @Test fun `CamAPS FX v1_4 - meal tab, Carbs cell, then types and leaves Confirm`() {
        val fill = CamapsTask(CamapsGoal.Meal(45), startedAt = 0)
        assertEquals(CamapsStep.Tap(listOf(2, 1), "tab 2 of 3"), fill.next(camapsHome, 0))
        assertEquals(CamapsStep.Tap(listOf(4, 5), "Carbs g CHO ---"), fill.next(camapsMeal, 100))
        assertEquals(CamapsStep.Fill(listOf(2, 0), "45"), fill.next(camapsDialog, 200))
        assertTrue(fill.finished)
    }

    private fun typed(text: String) = camapsDialog.copy(
        children = camapsDialog.children.toMutableList().also { it[2] = row(field(hint = "Value").copy(text = text)) },
    )

    @Test fun `prefill only - never taps Confirm, whatever the grams`() {
        for (grams in listOf(20, 60, 61)) {
            val fill = CamapsTask(CamapsGoal.Meal(grams), startedAt = 0)
            assertEquals(CamapsStep.Fill(listOf(2, 0), "$grams"), fill.next(camapsDialog, 0))
            assertTrue(fill.finished)
            assertTrue(fill.entered)
            assertEquals(CamapsStep.Wait, fill.next(typed("$grams"), 10))
            assertEquals(CamapsStep.Wait, fill.next(camapsDialog, 20))
        }
    }

    @Test fun `never taps a slider or switch, or a tab once labeled`() {
        assertEquals(emptyList<Pair<List<Int>, String>>(), CamapsTask.buttons(camapsHome))
        val labeled = screen(row(tab(), tab().copy(description = "Meal"), tab()))
        assertEquals(null, CamapsTask.tab(labeled, 1))
        assertEquals(null, CamapsTask.tab(screen(row(tab(), tab())), 1))
    }

    @Test fun `a one-field dialog counts only if nothing on it is about insulin`() {
        val units = camapsDialog.copy(children = camapsDialog.children + label("Total Units"))
        assertEquals(emptyList<List<Int>>(), CamapsTask.carbFields(units))
        val doseId = screen(label("Carbohydrates"), row(field(id = "pkg:id/insulin_dose")))
        assertEquals(emptyList<List<Int>>(), CamapsTask.carbFields(doseId))
        val glucose = screen(label("Glucose"), row(field(hint = "Value")), label("mmol/L"))
        assertEquals(emptyList<List<Int>>(), CamapsTask.carbFields(glucose))
    }

    @Test fun `Boost taps the home screen's Boost button and stops there`() {
        val boost = CamapsTask(CamapsGoal.BOOST, startedAt = 0)
        assertEquals(CamapsStep.Tap(listOf(5), "Boost"), boost.next(camapsHome, 0))
        assertTrue(boost.finished)
        assertEquals(CamapsStep.Wait, boost.next(camapsHome, 10))
        assertEquals(CamapsStep.Tap(listOf(6), "Ease-off"), CamapsTask(CamapsGoal.EASE_OFF, 0).next(camapsHome, 0))
    }

    @Test fun `Boost goes home from another tab first`() {
        val boost = CamapsTask(CamapsGoal.BOOST, startedAt = 0)
        assertEquals(CamapsStep.Tap(listOf(1, 0), "tab 1 of 3"), boost.next(camapsMeal.copy(children = camapsMeal.children.take(3)), 0))
        assertEquals(CamapsStep.Tap(listOf(5), "Boost"), boost.next(camapsHome, 100))
    }

    @Test fun `Boost never taps a button that only mentions it, or on a form`() {
        val stop = screen(button("Stop Boost"), button("Boost settings"))
        assertEquals(CamapsStep.Wait, CamapsTask(CamapsGoal.BOOST, 0).next(stop, 0))
        assertEquals(
            CamapsStep.GiveUp("2 buttons read Boost"),
            CamapsTask(CamapsGoal.BOOST, 0).next(screen(button("Boost"), button("Boost")), 0),
        )
        assertEquals(
            CamapsStep.GiveUp("a form, not the home screen"),
            CamapsTask(CamapsGoal.EASE_OFF, 0).next(camapsDialog, 0),
        )
    }

    @Test fun `Boost never taps on a dialog, whose Boost button would start it`() {
        val dialog = screen(label("Boost for 2 hours?"), button("Cancel"), button("Boost"))
        assertEquals(CamapsStep.GiveUp("a dialog, not the home screen"), CamapsTask(CamapsGoal.BOOST, 0).next(dialog, 0))
    }

    @Test fun `Other goes to the meal entry and stops, typing nothing`() {
        val entry = CamapsTask(CamapsGoal.MealEntry, startedAt = 0)
        assertEquals(CamapsStep.Tap(listOf(2, 1), "tab 2 of 3"), entry.next(camapsHome, 0))
        assertEquals(CamapsStep.Tap(listOf(4, 5), "Carbs g CHO ---"), entry.next(camapsMeal, 100))
        assertEquals(CamapsStep.Done("the carbs field is open"), entry.next(camapsDialog, 200))
        assertTrue(entry.finished)
        assertEquals(
            CamapsStep.GiveUp("a form with no field for carbs"),
            CamapsTask(CamapsGoal.MealEntry, 0).next(screen(row(label("Insulin"), field())), 0),
        )
        assertEquals("meal-entry", RepeatGuard.key(CamapsGoal.MealEntry))
    }
}
