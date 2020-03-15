package tarehart.rlbot.planning

import tarehart.rlbot.AgentOutput
import tarehart.rlbot.TacticalBundle
import tarehart.rlbot.steps.UnfailingStep
import tarehart.rlbot.time.Duration
import tarehart.rlbot.time.GameTime

/**
 * Composed of:
 * 1. A list of possible steps which can complete but might fail, in order of preference
 * 2. A fallback step which cannot fail
 *
 * We will find the first step capable of returning output and run it until:
 * - It succeeds (the plan will terminate)
 * - It returns null (we will begin running the fallback step)
 * - It fails and asks the plan to cancel (the plan will terminate)
 *
 * When the fallback step is running, we will also be attempting to re-engage one of the preferred steps
 * periodically.
 */
class RetryableViableStepPlan(
        posture: Posture,
        private val fallback: UnfailingStep,
        private val stillValid: (TacticalBundle) -> Boolean = {false}) : Plan(posture) {

    override val situation: String
        get() {
            if (isComplete()) {
                return "Dead plan"
            }
            return "${posture.name} RetryableViable (${currentStepIndex + 1}/${steps.size}) - ${currentStep.situation}"
        }

    private val fallbackDuration = Duration.ofMillis(500)
    private var fallbackExpiration: GameTime? = null

    override fun getOutput(bundle: TacticalBundle): AgentOutput? {

        if (isComplete() || !stillValid(bundle) && currentStep.canInterrupt()) {
            return null
        }

        val expiration = fallbackExpiration
        val time = bundle.agentInput.time
        if (expiration != null) {
            if (time > expiration) {
                fallbackExpiration = null
            } else {
                return fallback.getUnfailingOutput(bundle)
            }
        }

        while (currentStepIndex < steps.size) {
            val currentStep = currentStep

            val output = currentStep.getOutput(bundle)
            if (output != null) {
                return output
            }

            when (currentStep.getPlanGuidance()) {
                PlanGuidance.CANCEL, PlanGuidance.STEP_SUCCEEDED -> {
                    canceled = true
                    return null
                }
            }

            nextStep()
        }

        currentStepIndex = 0
        fallbackExpiration = time.plus(fallbackDuration)

        return fallback.getUnfailingOutput(bundle)
    }
}