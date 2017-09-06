package tarehart.rlbot.steps.strikes;

import mikera.vectorz.Vector3;
import tarehart.rlbot.AgentInput;
import tarehart.rlbot.AgentOutput;
import tarehart.rlbot.math.SpaceTime;
import tarehart.rlbot.physics.BallPath;
import tarehart.rlbot.planning.*;
import tarehart.rlbot.steps.Step;
import tarehart.rlbot.tuning.BotLog;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

public class FlipHitStep implements Step {

    private Plan plan;
    private boolean isComplete;
    private boolean startedStrike;
    private Vector3 originalIntercept;

    public FlipHitStep(Vector3 originalIntercept) {
        this.originalIntercept = originalIntercept;
    }

    public AgentOutput getOutput(AgentInput input) {

        if (plan != null) {
            if (plan.isComplete()) {
                if (startedStrike) {
                    isComplete = true;
                    return new AgentOutput();
                }
                plan = null;
            } else {
                return plan.getOutput(input);
            }
        }

        if (input.getMyPosition().z > 5) {
            isComplete = true;
            return new AgentOutput();
        }

        BallPath ballPath = SteerUtil.predictBallPath(input, input.time, Duration.ofSeconds(3));

        Optional<SpaceTime> currentIntercepts = SteerUtil.getInterceptOpportunityAssumingMaxAccel(input, ballPath, input.getMyBoost());
        if (currentIntercepts.isPresent()) {

            SpaceTime intercept = currentIntercepts.get();

            if (intercept.space.distance(originalIntercept) > 10 && Duration.between(input.time, intercept.time).toMillis() > 1000) {
                BotLog.println("FlipHitStep failing because we lost sight of the original plan.", input.team);
                isComplete = true;
                return new AgentOutput();
            }

            if (intercept.space.z > AirTouchPlanner.NEEDS_JUMP_HIT_THRESHOLD) {
                BotLog.println("FlipHitStep failing because ball will be too high!", input.team);
                isComplete = true;
                return new AgentOutput();
            }

            // Strike the ball such that it goes toward the enemy goal
            Vector3 fromGoal = (Vector3) intercept.space.subCopy(GoalUtil.getEnemyGoal(input.team).getNearestEntrance(intercept.space, 2));
            intercept.space.add(fromGoal.normaliseCopy());
            double distance = input.getMyPosition().distance(intercept.space);

            LaunchChecklist checklist = AirTouchPlanner.checkFlipHitReadiness(input, intercept);

            if (checklist.readyToLaunch()) {
                startedStrike = true;
                plan = SetPieces.frontFlip();
                plan.begin();
                return plan.getOutput(input);
            } else {
                return getThereAsap(input, intercept);
            }
        } else {
            BotLog.println("FlipHitStep failing because there are no max speed intercepts", input.team);
            isComplete = true;
        }

        return new AgentOutput();
    }

    private AgentOutput getThereAsap(AgentInput input, SpaceTime groundPosition) {

        Optional<Plan> sensibleFlip = SteerUtil.getSensibleFlip(input, groundPosition.space);
        if (sensibleFlip.isPresent()) {
            BotLog.println("Front flip to approach FlipHit", input.team);
            this.plan = sensibleFlip.get();
            this.plan.begin();
            return this.plan.getOutput(input);
        }

        return SteerUtil.steerTowardPosition(input, groundPosition.space);
    }

    @Override
    public boolean isComplete() {
        return isComplete;
    }

    @Override
    public void begin() {

    }

    @Override
    public String getSituation() {
        return "Preparing for FlipHit";
    }
}
