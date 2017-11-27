package tarehart.rlbot.steps.strikes;

import tarehart.rlbot.math.BallSlice;
import tarehart.rlbot.math.vector.Vector2;
import tarehart.rlbot.math.vector.Vector3;
import tarehart.rlbot.physics.BallPath;
import tarehart.rlbot.physics.DistancePlot;
import tarehart.rlbot.ui.ArenaDisplay;

import java.awt.*;
import java.awt.geom.Line2D;

public class DirectedKickPlan {
    public BallPath ballPath;
    public DistancePlot distancePlot;
    public BallSlice ballAtIntercept;
    public Vector3 interceptModifier;
    public Vector3 desiredBallVelocity;
    public Vector3 plannedKickForce;

    public Vector3 getCarPositionAtIntercept() {
        return ballAtIntercept.getSpace().plus(interceptModifier);
    }

    public void drawDebugInfo(Graphics2D graphics) {
        graphics.setColor(new Color(73, 111, 73));
        ArenaDisplay.drawBall(ballAtIntercept.space, graphics, graphics.getColor());
        graphics.setStroke(new BasicStroke(1));

        Vector2 carAtOffset = getCarPositionAtIntercept().flatten();
        int crossSize = 2;
        graphics.draw(new Line2D.Double(carAtOffset.x - crossSize, carAtOffset.y - crossSize, carAtOffset.x + crossSize, carAtOffset.y + crossSize));
        graphics.draw(new Line2D.Double(carAtOffset.x - crossSize, carAtOffset.y + crossSize, carAtOffset.x + crossSize, carAtOffset.y - crossSize));
    }
}
