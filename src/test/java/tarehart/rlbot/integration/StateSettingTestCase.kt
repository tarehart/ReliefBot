package tarehart.rlbot.integration

import rlbot.cppinterop.RLBotDll
import rlbot.gamestate.GameState
import tarehart.rlbot.TacticalBundle
import tarehart.rlbot.bots.BundleListener
import tarehart.rlbot.integration.asserts.AssertStatus
import tarehart.rlbot.integration.asserts.PacketAssert
import tarehart.rlbot.integration.metrics.TimeMetric
import tarehart.rlbot.planning.Plan
import tarehart.rlbot.time.GameTime

class StateSettingTestCase(private val initialState: GameState, val conditions: Set<PacketAssert>,
                           val initialPlan: Plan? = null): BundleListener {

    private var previousBundle: TacticalBundle? = null
    var isComplete = false
    lateinit var startTime: GameTime

    fun setState() {
        RLBotDll.setGameState(initialState.buildPacket())
    }

    override fun processBundle(bundle: TacticalBundle) {

        if (!::startTime.isInitialized) {
            startTime = bundle.agentInput.time
            System.out.println("Test begins at $startTime")
        }

        var hasPendingAsserts = false

        for (packetAssert: PacketAssert in conditions.filter { it.status == AssertStatus.PENDING }) {

            if (packetAssert.hasExpired(bundle, startTime)) {
                if (packetAssert.negated) {
                    packetAssert.status = AssertStatus.SUCCEEDED
                    packetAssert.message = "Deadtime Elapsed!"
                } else {
                    packetAssert.status = AssertStatus.FAILED
                    packetAssert.message = "Timed out!"
                }
            } else {
                packetAssert.checkBundle(bundle, previousBundle)
                if (packetAssert.status == AssertStatus.SUCCEEDED ||
                        packetAssert.status == AssertStatus.FAILED && packetAssert.negated) {
                    val duration = bundle.agentInput.time - startTime
                    packetAssert.addMetric(TimeMetric(duration, "Elapsed Time"))
                }
            }

            hasPendingAsserts = hasPendingAsserts || packetAssert.status == AssertStatus.PENDING
        }
        previousBundle = bundle
        isComplete = !hasPendingAsserts
    }



}
