package app.steadmo.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeliveryTest {
    @Test fun `knows the delivery apps by package`() {
        assertEquals(DeliveryApp.DELIVEROO, DeliveryApp.of("com.deliveroo.orderapp"))
        assertEquals(DeliveryApp.UBER_EATS, DeliveryApp.of("com.ubercab.eats"))
        assertEquals(DeliveryApp.JUST_EAT, DeliveryApp.of("com.justeat.app.uk"))
        assertEquals(DeliveryApp.JUST_EAT, DeliveryApp.of("com.menulog.m"))
        assertEquals(DeliveryApp.DOORDASH, DeliveryApp.of("com.dd.doordash"))
        assertNull(DeliveryApp.of("com.ubercab"))
        assertNull(DeliveryApp.of("com.camdiab.fx_alert.mmoll"))
    }

    @Test fun `the rider is nearby, in the words delivery apps use`() {
        listOf(
            listOf("Your rider is nearby"),
            listOf("Rider nearby"),
            listOf("Deliveroo", "Sam is nearby with your order"),
            listOf("NEARBY: your food is almost here"),
            listOf("Your courier is near by"),
            listOf("Arriving in 4 minutes"),
            listOf("Your rider is 1 min away"),
            listOf("Your driver is 3 mins away"),
            listOf("Almost there!"),
            listOf("Sam is around the corner"),
            listOf("Your Dasher is approaching"),
        ).forEach { assertTrue(it.toString(), DeliveryPrompt.nearby(it)) }
    }

    @Test fun `not while it's only on its way, nor once it's here`() {
        listOf(
            listOf("The restaurant is preparing your order"),
            listOf("Your order is on its way", "Sam is heading to you"),
            listOf("Your rider has picked up your order"),
            listOf("Out for delivery"),
            listOf("Arriving in 25 minutes", "25 mins away"),
            listOf("Arriving in 5 minutes"),
            listOf("Your driver is 5 mins away"),
            listOf("Arriving in 10 minutes"),
            listOf("14 mins away"),
            listOf("The restaurant is closed. Your order has been cancelled."),
            listOf("Your order was cancelled", "Your rider is nearby"),
            listOf("We've refunded your order"),
            listOf("Nearbyville Pizza has your order"),
            listOf("14 mins away", "41 mins away"),
            listOf("Your order has been delivered", "Enjoy your meal"),
            listOf("Your rider is outside", "Almost there"),
        ).forEach { assertFalse(it.toString(), DeliveryPrompt.nearby(it)) }
    }

    @Test fun `only fresh posts count`() {
        assertTrue(DeliveryPrompt.fresh(0, 15 * 60_000L))
        assertFalse(DeliveryPrompt.fresh(0, 16 * 60_000L))
        assertFalse(DeliveryPrompt.fresh(10, 0))
    }

    @Test fun `one prompt an hour per app`() {
        assertTrue(DeliveryPrompt.due(null, 0))
        assertFalse(DeliveryPrompt.due(0, 59 * 60_000L))
        assertTrue(DeliveryPrompt.due(0, 60 * 60_000L))
        assertTrue(DeliveryPrompt.due(10, 0))
    }
}
