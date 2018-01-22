package tarehart.rlbot.steps.challenge

import tarehart.rlbot.AgentInput
import tarehart.rlbot.AgentOutput
import tarehart.rlbot.input.BallTouch
import tarehart.rlbot.intercept.AirTouchPlanner
import tarehart.rlbot.math.vector.Vector3
import tarehart.rlbot.planning.GoalUtil
import tarehart.rlbot.planning.Plan
import tarehart.rlbot.planning.SteerUtil
import tarehart.rlbot.planning.TacticsTelemetry
import tarehart.rlbot.steps.NestedPlanStep
import tarehart.rlbot.steps.strikes.InterceptStep
import tarehart.rlbot.tuning.BotLog.println
import java.util.*

class ChallengeStep: NestedPlanStep() {

    private var originalTouch: BallTouch? = null

    override fun getLocalSituation(): String {
        return  "Working on challenge"
    }

    override fun doComputationInLieuOfPlan(input: AgentInput): Optional<AgentOutput> {

        val car = input.myCarData

        if (originalTouch == null) {
            originalTouch = input.latestBallTouch.orElse(null)
        } else {

            if (originalTouch?.position ?: Vector3() != input.latestBallTouch.map({it.position}).orElse(Vector3())) {
                // There has been a new ball touch.
                println("Ball has been touched, quitting challenge", input.playerIndex)
                return Optional.empty()
            }
        }

        val tacticalSituation = TacticsTelemetry.get(input.playerIndex) ?: return Optional.empty()
        val ballAdvantage = tacticalSituation.ballAdvantage
        if (ballAdvantage.seconds > 2.0) {
            return Optional.empty() // We can probably go for a shot now.
        }

        val enemyContact = tacticalSituation.expectedEnemyContact ?: return Optional.empty()

        if (enemyContact.space.z > AirTouchPlanner.NEEDS_AERIAL_THRESHOLD) {
            return Optional.empty()
        }

        val enemyShotLine = GoalUtil.getOwnGoal(input.team).center - enemyContact.space

        val flatPosition = car.position.flatten()
        val contactDistance = flatPosition.distance(enemyContact.space.flatten())
        val defensiveNode = enemyContact.space.flatten() + enemyShotLine.flatten().scaledToMagnitude(Math.min(15.0, contactDistance / 2))

        val defensiveNodeDistance = flatPosition.distance(defensiveNode)

        if (defensiveNodeDistance < 15) {
            startPlan(
                    Plan(Plan.Posture.DEFENSIVE)
                            .withStep(InterceptStep(enemyShotLine.scaledToMagnitude(1.5))),
                    input)
        }

        val sensibleFlip = SteerUtil.getSensibleFlip(car, defensiveNode)
        if (sensibleFlip.isPresent) {
            return startPlan(sensibleFlip.get(), input)
        }

        return Optional.of(SteerUtil.steerTowardGroundPosition(car, input.boostData, defensiveNode))
    }
}
