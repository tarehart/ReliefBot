package tarehart.rlbot.steps.challenge

import tarehart.rlbot.AgentOutput
import tarehart.rlbot.TacticalBundle
import tarehart.rlbot.math.Plane
import tarehart.rlbot.math.vector.Vector2
import tarehart.rlbot.physics.ArenaModel
import tarehart.rlbot.planning.GoalUtil
import tarehart.rlbot.planning.Plan
import tarehart.rlbot.planning.Posture
import tarehart.rlbot.planning.SteerUtil
import tarehart.rlbot.rendering.RenderUtil
import tarehart.rlbot.steps.NestedPlanStep
import tarehart.rlbot.steps.strikes.FlexibleKickStep
import tarehart.rlbot.steps.strikes.InterceptStep
import tarehart.rlbot.steps.strikes.KickAwayFromOwnGoal
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Graphics2D
import java.awt.geom.Line2D
import kotlin.math.max
import kotlin.math.min

class ChallengeStep: NestedPlanStep() {

    private var latestDefensiveNode: Vector2? = null

    override fun getLocalSituation(): String {
        return  "Working on challenge"
    }

    override fun doComputationInLieuOfPlan(bundle: TacticalBundle): AgentOutput? {

        val car = bundle.agentInput.myCarData

        val tacticalSituation = bundle.tacticalSituation
        val ballAdvantage = tacticalSituation.ballAdvantage

        val enemyContact = tacticalSituation.expectedEnemyContact ?:
            return null

        val enemyShotLine = GoalUtil.getOwnGoal(bundle.agentInput.team).center - enemyContact.space

        val defensiveNodeDistance = max(ballAdvantage.seconds * -20F, MIN_DEFENSIVE_NODE_DISTANCE)

        val flatPosition = car.position.flatten()
        val defensiveNode = ArenaModel.clampPosition(
                enemyContact.space.flatten() + enemyShotLine.flatten().scaledToMagnitude(defensiveNodeDistance), 3.0)

        latestDefensiveNode = defensiveNode

        val carDistanceToDefensiveNode = flatPosition.distance(defensiveNode)

        if (tacticalSituation.distanceBallIsBehindUs > 0 && ballAdvantage.seconds > -.2) {
            startPlan(
                    Plan(Posture.DEFENSIVE)
                            .withStep(FlexibleKickStep(KickAwayFromOwnGoal())),
                    bundle)
        }

        // TODO: also attack aggressively if the enemy appears to be dribbling

        if (carDistanceToDefensiveNode < MIN_DEFENSIVE_NODE_DISTANCE + 15 && ballAdvantage.seconds > -.3) { // Don't set ball advantage too low or you'll break kickoffs.
            startPlan(
                    Plan(Posture.DEFENSIVE)
                            .withStep(InterceptStep(enemyShotLine.scaledToMagnitude(1.5))),
                    bundle)
        }

        SteerUtil.getSensibleFlip(car, defensiveNode)?.let {
            if (car.boost < 1 && tacticalSituation.distanceBallIsBehindUs > 0) { // Use more boost and less flipping during challenges.
                return startPlan(it, bundle)
            }
        }

        val renderer = car.renderer
        RenderUtil.drawSquare(renderer, Plane(enemyShotLine.normaliseCopy(), enemyContact.space), 1.0, Color(0.8f, 0.0f, 0.8f))
        RenderUtil.drawSquare(renderer, Plane(enemyShotLine.normaliseCopy(), enemyContact.space), 1.5, Color(0.8f, 0.0f, 0.8f))
        RenderUtil.drawSphere(renderer, defensiveNode.withZ(1.0), 1.5, Color.BLUE)

        // If we're too greedy, we'll be late to kickoffs
        return SteerUtil.steerTowardGroundPosition(car, defensiveNode,
                detourForBoost = carDistanceToDefensiveNode > 50 && car.boost < 50)
    }

    override fun drawDebugInfo(graphics: Graphics2D) {
        super.drawDebugInfo(graphics)

        latestDefensiveNode?.let {
            graphics.color = Color(73, 111, 73)
            graphics.stroke = BasicStroke(1f)

            val (x, y) = it
            val crossSize = 2
            graphics.draw(Line2D.Float(x - crossSize, y - crossSize, x + crossSize, y + crossSize))
            graphics.draw(Line2D.Float(x - crossSize, y + crossSize, x + crossSize, y - crossSize))
        }
    }

    companion object {

        const val MIN_DEFENSIVE_NODE_DISTANCE = 18.0F
    }
}
