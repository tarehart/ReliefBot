package tarehart.rlbot.steps;

import mikera.vectorz.Vector2;
import mikera.vectorz.Vector3;
import tarehart.rlbot.AgentInput;
import tarehart.rlbot.AgentOutput;
import tarehart.rlbot.CarData;
import tarehart.rlbot.math.SpaceTime;
import tarehart.rlbot.physics.BallPath;
import tarehart.rlbot.planning.*;

import java.time.Duration;
import java.util.Optional;

public class CatchBallStep implements Step {

    private boolean isComplete = false;
    private int confusionLevel = 0;
    private SpaceTime latestCatchLocation;
    private boolean firstFrame = true;

    public CatchBallStep(SpaceTime initialCatchLocation) {
        latestCatchLocation = initialCatchLocation;
    }

    public Optional<AgentOutput> getOutput(AgentInput input) {

        CarData car = input.getMyCarData();

        if (firstFrame) {
            firstFrame = false;
            return Optional.of(playCatch(car, latestCatchLocation));
        }

        double distance = car.position.distance(input.ballPosition);

        if (distance < 2.5 || confusionLevel > 3) {
            isComplete = true;
            // We'll still get one last frame out output though
        }

        BallPath ballPath = SteerUtil.predictBallPath(input, input.time, Duration.ofSeconds(3));
        Optional<SpaceTime> catchOpportunity = SteerUtil.getCatchOpportunity(car, ballPath, AirTouchPlanner.getBoostBudget(car));

        // Weed out any intercepts after a catch opportunity. Should just catch it.
        if (catchOpportunity.isPresent()) {
            latestCatchLocation = catchOpportunity.get();
            confusionLevel = 0;
        } else {
            confusionLevel++;
        }

        return Optional.of(playCatch(car, latestCatchLocation));
    }

    private AgentOutput playCatch(CarData car, SpaceTime catchLocation) {
        Vector3 enemyGoal = GoalUtil.getEnemyGoal(car.team).navigationSpline.getLocation();
        Vector3 awayFromEnemyGoal = (Vector3) catchLocation.space.subCopy(enemyGoal);
        awayFromEnemyGoal.z = 0;
        awayFromEnemyGoal.normalise();
        awayFromEnemyGoal.scale(.85);
        Vector3 target = catchLocation.space.addCopy(awayFromEnemyGoal);

        return SteerUtil.getThereOnTime(car, new SpaceTime(target, catchLocation.time));
    }

    @Override
    public boolean isBlindlyComplete() {
        return isComplete;
    }

    @Override
    public void begin() {
    }

    @Override
    public String getSituation() {
        return "Catching ball";
    }
}
