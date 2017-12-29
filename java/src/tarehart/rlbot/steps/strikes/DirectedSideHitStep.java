package tarehart.rlbot.steps.strikes;

import tarehart.rlbot.AgentInput;
import tarehart.rlbot.AgentOutput;
import tarehart.rlbot.input.CarData;
import tarehart.rlbot.intercept.StrikeProfile;
import tarehart.rlbot.math.VectorUtil;
import tarehart.rlbot.math.vector.Vector2;
import tarehart.rlbot.math.vector.Vector3;
import tarehart.rlbot.planning.*;
import tarehart.rlbot.routing.CircleTurnUtil;
import tarehart.rlbot.routing.SteerPlan;
import tarehart.rlbot.steps.Step;
import tarehart.rlbot.time.Duration;
import tarehart.rlbot.time.GameTime;
import tarehart.rlbot.tuning.BotLog;
import tarehart.rlbot.tuning.ManeuverMath;

import java.awt.*;
import java.util.Optional;

import static java.lang.String.format;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static tarehart.rlbot.planning.SteerUtil.steerTowardGroundPosition;
import static tarehart.rlbot.tuning.BotLog.println;

public class DirectedSideHitStep implements Step {
    private static final double MANEUVER_SECONDS_PER_RADIAN = .1;
    private static final double GAP_BEFORE_DODGE = 1.5;
    private static final double DISTANCE_AT_CONTACT = 2;
    private Plan plan;
    private Vector3 originalIntercept;
    private GameTime doneMoment;
    private KickStrategy kickStrategy;
    private Vector3 interceptModifier = null;
    private double maneuverSeconds = 0;
    private boolean finalApproach = false;
    private SteerPlan circleTurnPlan;
    private CarData car;
    private DirectedKickPlan kickPlan;

    public DirectedSideHitStep(KickStrategy kickStrategy) {
        this.kickStrategy = kickStrategy;
    }

    public Optional<AgentOutput> getOutput(AgentInput input) {

        car = input.getMyCarData();

        if (plan != null && !plan.isComplete()) {
            Optional<AgentOutput> output = plan.getOutput(input);
            if (output.isPresent()) {
                return output;
            }
        }

        if (doneMoment != null && input.time.isAfter(doneMoment)) {
            return Optional.empty();
        }

        final Optional<DirectedKickPlan> kickPlanOption;
        if (interceptModifier != null) {
            StrikeProfile strikeProfile = new StrikeProfile(maneuverSeconds, 0, 0, StrikeProfile.Style.SIDE_HIT);
            kickPlanOption = DirectedKickUtil.planKick(input, kickStrategy, true, interceptModifier, (space) -> strikeProfile, input.time);
        } else {
            kickPlanOption = DirectedKickUtil.planKick(input, kickStrategy, true);
        }

        if (!kickPlanOption.isPresent()) {
            BotLog.println("Quitting side hit due to failed kick plan.", car.playerIndex);
            return Optional.empty();
        }

        kickPlan = kickPlanOption.get();

        if (interceptModifier == null) {
            Vector3 nearSide = kickPlan.plannedKickForce.scaledToMagnitude(-(DISTANCE_AT_CONTACT + GAP_BEFORE_DODGE));
            interceptModifier = new Vector3(nearSide.getX(), nearSide.getY(), nearSide.getZ() - 1.4); // Closer to ground
        }

        if (originalIntercept == null) {
            originalIntercept = kickPlan.ballAtIntercept.getSpace();
        } else {
            if (originalIntercept.distance(kickPlan.ballAtIntercept.getSpace()) > 30) {
                println("Failed to make the directed kick", input.playerIndex);
                return empty(); // Failed to kick it soon enough, new stuff has happened.
            }
        }

        Vector2 strikeDirection = kickPlan.plannedKickForce.flatten().normalized();
        Vector3 carPositionAtIntercept = kickPlan.intercept.getSpace();

        Vector2 orthogonalPoint = carPositionAtIntercept.flatten();

        if (finalApproach) {
            return performFinalApproach(input, orthogonalPoint, kickPlan, carPositionAtIntercept, strikeDirection);
        }

        Optional<Duration> strikeTime = getStrikeTime(carPositionAtIntercept, GAP_BEFORE_DODGE);
        if (!strikeTime.isPresent()) {
            return Optional.empty();
        }
        double expectedSpeed = kickPlan.distancePlot.getMotionAfterDistance(car.position.flatten().distance(orthogonalPoint)).map(m -> m.getSpeed()).orElse(40.0);
        double backoff = expectedSpeed * strikeTime.get().getSeconds() + 1;

        if (backoff > car.position.flatten().distance(orthogonalPoint)) {
            BotLog.println("Failed the side hit.", car.playerIndex);
            return Optional.empty();
        }

        Vector2 carToIntercept = carPositionAtIntercept.minus(car.position).flatten();
        Vector2 facingForSideFlip = VectorUtil.orthogonal(strikeDirection, v -> v.dotProduct(carToIntercept) > 0).normalized();

        if (Vector2.Companion.angle(carToIntercept, facingForSideFlip) > Math.PI / 3) {
            // If we're doing more than a quarter turn, this is a waste of time.
            return Optional.empty();
        }

        Vector2 steerTarget = orthogonalPoint.minus(facingForSideFlip.scaled(backoff));

        Vector2 toOrthogonal = orthogonalPoint.minus(car.position.flatten());

        double distance = toOrthogonal.magnitude();
        Vector2 carNose = car.orientation.noseVector.flatten();
        double angle = Vector2.Companion.angle(carNose, facingForSideFlip);
        if (distance < backoff + 3 && angle < Math.PI / 8) {
            doneMoment = input.time.plus(strikeTime.get()).plusSeconds(.5);
            finalApproach = true;
            maneuverSeconds = 0;
            circleTurnPlan = null;
            // Done with the circle turn. Drive toward the orthogonal point and wait for the right moment to launch.
            return performFinalApproach(input, orthogonalPoint, kickPlan, carPositionAtIntercept, strikeDirection);
        }


        maneuverSeconds = angle * MANEUVER_SECONDS_PER_RADIAN;

        circleTurnPlan = CircleTurnUtil.getPlanForCircleTurn(car, kickPlan.distancePlot, steerTarget, facingForSideFlip);

        return getNavigation(input, circleTurnPlan);
    }

    private Optional<Duration> getStrikeTime(Vector3 carPositionAtIntercept, double approachDistance) {
        return getJumpTime(carPositionAtIntercept).map(t -> t.plusSeconds(ManeuverMath.secondsForSideFlipTravel(approachDistance)));
    }

    private Optional<AgentOutput> performFinalApproach(AgentInput input, Vector2 orthogonalPoint, DirectedKickPlan kickPlan, Vector3 carPositionAtIntercept, Vector2 strikeDirection) {

        // You're probably darn close to flip time.

        CarData car = input.getMyCarData();

        Optional<Duration> jumpTime = getJumpTime(carPositionAtIntercept);
        if (!jumpTime.isPresent()) {
            return Optional.empty();
        }
        Vector2 carAtImpact = kickPlan.ballAtIntercept.space.flatten().plus(strikeDirection.scaled(-DISTANCE_AT_CONTACT));
        Vector2 toImpact = carAtImpact.minus(car.position.flatten());
        Vector2 projectedApproach = VectorUtil.project(toImpact, car.orientation.rightVector.flatten());
        double realApproachDistance = projectedApproach.magnitude();
        Optional<Duration> strikeTime = getStrikeTime(carPositionAtIntercept, realApproachDistance);
        if (!strikeTime.isPresent()) {
            return Optional.empty();
        }
        double backoff = car.velocity.magnitude() * strikeTime.get().getSeconds();

        double distance = car.position.flatten().distance(orthogonalPoint);
        if (distance < backoff) {
            // Time to launch!
            double strikeForceCorrection = DirectedKickUtil.getAngleOfKickFromApproach(car, kickPlan);
            plan = SetPieces.jumpSideFlip(strikeForceCorrection > 0, jumpTime.get());
            return plan.getOutput(input);
        } else {
            println(format("Side flip soon. Distance: %.2f", distance), input.playerIndex);
            return of(steerTowardGroundPosition(car, orthogonalPoint));
        }
    }

    private Optional<Duration> getJumpTime(Vector3 carPositionAtIntercept) {
        return ManeuverMath.secondsForMashJumpHeight(carPositionAtIntercept.getZ()).map(Duration::ofSeconds);
    }

    private Optional<AgentOutput> getNavigation(AgentInput input, SteerPlan circleTurnOption) {
        CarData car = input.getMyCarData();

        if (car.boost == 0) {
            Optional<Plan> sensibleFlip = SteerUtil.getSensibleFlip(car, circleTurnOption.waypoint);
            if (sensibleFlip.isPresent()) {
                println("Front flip toward side hit", input.playerIndex);
                this.plan = sensibleFlip.get();
                return this.plan.getOutput(input);
            }
        }

        return Optional.of(circleTurnOption.immediateSteer);
    }

    @Override
    public boolean canInterrupt() {
        return plan == null || plan.canInterrupt();
    }

    @Override
    public String getSituation() {
        return Plan.concatSituation("Directed Side Hit", plan);
    }

    @Override
    public void drawDebugInfo(Graphics2D graphics) {

        if (Plan.activePlan(plan).isPresent()) {
            plan.getCurrentStep().drawDebugInfo(graphics);
            return;
        }

        if (circleTurnPlan != null) {
            graphics.setColor(new Color(190, 129, 200));
            circleTurnPlan.drawDebugInfo(graphics, car);
        }

        if (kickPlan != null) {
            kickPlan.drawDebugInfo(graphics);
        }
    }
}
