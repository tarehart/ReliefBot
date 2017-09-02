package tarehart.rlbot.physics;

import com.bulletphysics.collision.broadphase.AxisSweep3;
import com.bulletphysics.collision.dispatch.CollisionConfiguration;
import com.bulletphysics.collision.dispatch.CollisionDispatcher;
import com.bulletphysics.collision.dispatch.CollisionObject;
import com.bulletphysics.collision.dispatch.DefaultCollisionConfiguration;
import com.bulletphysics.collision.shapes.*;
import com.bulletphysics.dynamics.DiscreteDynamicsWorld;
import com.bulletphysics.dynamics.DynamicsWorld;
import com.bulletphysics.dynamics.RigidBody;
import com.bulletphysics.dynamics.RigidBodyConstructionInfo;
import com.bulletphysics.dynamics.constraintsolver.SequentialImpulseConstraintSolver;
import com.bulletphysics.linearmath.DefaultMotionState;
import com.bulletphysics.linearmath.Transform;
import mikera.vectorz.Vector2;
import mikera.vectorz.Vector3;
import tarehart.rlbot.AgentInput;
import tarehart.rlbot.math.SpaceTime;
import tarehart.rlbot.math.SpaceTimeVelocity;

import javax.vecmath.Quat4f;
import javax.vecmath.Vector2f;
import javax.vecmath.Vector3f;
import java.time.Duration;
import java.time.LocalDateTime;


public class ArenaModel {

    public static final float SIDE_WALL = 81.92f;
    public static final float BACK_WALL = 102.4f;
    public static final float CEILING = 40.88f;
    public static final float BALL_ANGULAR_DAMPING = 1f;

    private static final int WALL_THICKNESS = 10;
    private static final int WALL_LENGTH = 200;
    public static final float GRAVITY = 13f;
    public static final Duration SIMULATION_STEP = Duration.ofMillis(100);
    public static final float BALL_DRAG = .0305f;
    public static final float BALL_RADIUS = 1.8555f;

    public static final Vector2f CORNER_ANGLE_CENTER = new Vector2f(70.5f, 90.2f);

    // The diagonal surfaces that merge the floor and the wall--
    // Higher = more diagonal showing.
    public static final float RAIL_HEIGHT = 1.4f;
    public static final float BALL_RESTITUTION = .6f;
    public static final float WALL_RESTITUTION = 1f;
    public static final float BALL_FRICTION = .6f;
    public static final int STEPS_PER_SECOND = 10;

    private DynamicsWorld world;
    private RigidBody ball;


    public ArenaModel() {
        world = initPhysics();
        setupWalls();
        ball = initBallPhysics();
        world.addRigidBody(ball);
    }

    public static boolean isInBoundsBall(Vector3 location) {
        return Math.abs(location.x) < SIDE_WALL - BALL_RADIUS && Math.abs(location.y) < BACK_WALL - BALL_RADIUS;
    }

    private void setupWalls() {
        addWallToWorld(new Vector3f(0, 0, 1), 0);
        addWallToWorld(new Vector3f(0, 1, 0), BACK_WALL);
        addWallToWorld(new Vector3f(0, -1, 0), BACK_WALL);
        addWallToWorld(new Vector3f(1, 0, 0), SIDE_WALL);
        addWallToWorld(new Vector3f(-1, 0, 0), SIDE_WALL);
        addWallToWorld(new Vector3f(0, 0, -1), CEILING);

        // 45 angle corners
        addWallToWorld(new Vector3f(1, 1, 0), new Vector3f(-CORNER_ANGLE_CENTER.x, -CORNER_ANGLE_CENTER.y, 0));
        addWallToWorld(new Vector3f(-1, 1, 0), new Vector3f(CORNER_ANGLE_CENTER.x, -CORNER_ANGLE_CENTER.y, 0));
        addWallToWorld(new Vector3f(1, -1, 0), new Vector3f(-CORNER_ANGLE_CENTER.x, CORNER_ANGLE_CENTER.y, 0));
        addWallToWorld(new Vector3f(-1, -1, 0), new Vector3f(CORNER_ANGLE_CENTER.x, CORNER_ANGLE_CENTER.y, 0));

        // 45 degree angle rails at floor
        addWallToWorld(new Vector3f(1, 0, 1), new Vector3f(-SIDE_WALL, 0, RAIL_HEIGHT));
        addWallToWorld(new Vector3f(-1, 0, 1), new Vector3f(SIDE_WALL, 0, RAIL_HEIGHT));
        addWallToWorld(new Vector3f(0, 1, 1), new Vector3f(0, -BACK_WALL, RAIL_HEIGHT));
        addWallToWorld(new Vector3f(0, -1, 1), new Vector3f(0, BACK_WALL, RAIL_HEIGHT));
    }

    private int normalToBoxDimension(float norm) {
        return norm == 0 ? WALL_LENGTH / 2 : WALL_THICKNESS / 2;
    }

    private void addWallToWorld(Vector3f normal, Vector3f position) {

        normal.normalize();

        // A large, flattish box laying on the ground.
        CollisionShape boxGround = new BoxShape(new Vector3f(WALL_LENGTH / 2, WALL_LENGTH / 2, WALL_THICKNESS /2));

        Transform wallTransform = new Transform();
        wallTransform.setIdentity();

        Vector3f thicknessTweak = new Vector3f(normal);
        thicknessTweak.scale(-WALL_THICKNESS / 2);

        Vector3f finalPosition = new Vector3f();
        finalPosition.add(position);
        finalPosition.add(thicknessTweak);
        wallTransform.origin.set(finalPosition);

        Vector3f straightUp = new Vector3f(0, 0, 1);
        Quat4f quat = getRotationFrom(straightUp, normal);
        wallTransform.setRotation(quat);

        RigidBodyConstructionInfo rbInfo = new RigidBodyConstructionInfo(
                0, null, boxGround, new Vector3f());
        RigidBody wall = new RigidBody(rbInfo);
        wall.setRestitution(WALL_RESTITUTION);
        wall.setWorldTransform(wallTransform);

        world.addRigidBody(wall);
    }

    // https://stackoverflow.com/questions/1171849/finding-quaternion-representing-the-rotation-from-one-vector-to-another
    private Quat4f getRotationFrom(Vector3f fromVec, Vector3f toVec) {
        Vector3f cross = new Vector3f();
        cross.cross(fromVec, toVec);
        float magnitude = (float) (Math.sqrt(fromVec.lengthSquared() * toVec.lengthSquared()) + fromVec.dot(toVec));
        Quat4f rot = new Quat4f();
        rot.set(cross.x, cross.y, cross.z, magnitude);
        rot.normalize();
        return rot;
    }

    private void addWallToWorld(Vector3f normal, float distanceFromCenter) {

        CollisionShape boxGround = new BoxShape(new Vector3f(normalToBoxDimension(normal.x), normalToBoxDimension(normal.y), normalToBoxDimension(normal.z)));

        Transform wallTransform = new Transform();
        wallTransform.setIdentity();

        float backoff = distanceFromCenter + WALL_THICKNESS / 2;
        Vector3f origin = new Vector3f(normal);
        origin.scale(-backoff);
        wallTransform.origin.set(origin);

        RigidBodyConstructionInfo rbInfo = new RigidBodyConstructionInfo(
                0, null, boxGround, new Vector3f());
        RigidBody wall = new RigidBody(rbInfo);
        wall.setRestitution(WALL_RESTITUTION);
        wall.setWorldTransform(wallTransform);
        world.addRigidBody(wall);
    }

    private Vector3 getBallPosition() {
        Transform trans = new Transform();
        ball.getWorldTransform(trans);
        return new Vector3(trans.origin.x, trans.origin.y, trans.origin.z);
    }

    public BallPath simulateBall(SpaceTimeVelocity start, Duration duration) {
        BallPath ballPath = new BallPath(start);
        simulateBall(ballPath, start.getTime().plus(duration));
        return ballPath;
    }

    public BallPath simulateBall(SpaceTimeVelocity start, LocalDateTime endTime) {
        BallPath ballPath = new BallPath(start);
        simulateBall(ballPath, endTime);
        return ballPath;
    }

    public void extendSimulation(BallPath ballPath, LocalDateTime endTime) {
        simulateBall(ballPath, endTime);
    }

    private void simulateBall(BallPath ballPath, LocalDateTime endTime) {
        SpaceTimeVelocity start = ballPath.getEndpoint();
        LocalDateTime simulationTime = LocalDateTime.from(start.getTime());
        if (simulationTime.isAfter(endTime)) {
            return;
        }

        ball.clearForces();
        ball.setLinearVelocity(toV3f(start.getVelocity()));
        Transform ballTransform = new Transform();
        ballTransform.setIdentity();
        ballTransform.origin.set(toV3f(start.getSpace()));
        ball.setWorldTransform(ballTransform);


        // Do some simulation
        while (simulationTime.isBefore(endTime)) {
            world.stepSimulation(1.0f / STEPS_PER_SECOND, 2, 0.5f / STEPS_PER_SECOND);
            simulationTime = simulationTime.plus(SIMULATION_STEP);
            Vector3 ballVelocity = getBallVelocity();
            ballPath.addSlice(new SpaceTimeVelocity(getBallPosition(), simulationTime, ballVelocity));
            double speed = ballVelocity.magnitude();
            if (speed < 10) {
                ball.setFriction(0);
                ball.setDamping(0, BALL_ANGULAR_DAMPING);
            } else {
                ball.setFriction(BALL_FRICTION);
                ball.setDamping(BALL_DRAG, BALL_ANGULAR_DAMPING);
            }
        }
    }

    private Vector3 getBallVelocity() {
        Vector3f ballVel = new Vector3f();
        ball.getLinearVelocity(ballVel);
        return toV3(ballVel);
    }

    private static Vector3f toV3f(Vector3 v) {
        return new Vector3f((float) v.x, (float) v.y, (float) v.z);
    }

    private static Vector3 toV3(Vector3f v) {
        return new Vector3(v.x, v.y, v.z);
    }

    private DynamicsWorld initPhysics() {
        // collision configuration contains default setup for memory, collision
        // setup. Advanced users can create their own configuration.
        CollisionConfiguration collisionConfiguration = new DefaultCollisionConfiguration();

        // use the default collision dispatcher. For parallel processing you
        // can use a diffent dispatcher (see Extras/BulletMultiThreaded)
        CollisionDispatcher dispatcher = new CollisionDispatcher(
                collisionConfiguration);

        // the maximum size of the collision world. Make sure objects stay
        // within these boundaries
        // Don't make the world AABB size too large, it will harm simulation
        // quality and performance
        Vector3f worldAabbMin = new Vector3f(-400, -400, -400);
        Vector3f worldAabbMax = new Vector3f(400, 400, 400);
        int maxProxies = 1024;
        AxisSweep3 overlappingPairCache =
                new AxisSweep3(worldAabbMin, worldAabbMax, maxProxies);

        SequentialImpulseConstraintSolver solver = new SequentialImpulseConstraintSolver();

        DiscreteDynamicsWorld dynamicsWorld = new DiscreteDynamicsWorld(
                dispatcher, overlappingPairCache, solver,
                collisionConfiguration);

        dynamicsWorld.setGravity(new Vector3f(0, 0, -GRAVITY));

        return dynamicsWorld;
    }

    private RigidBody initBallPhysics() {
        SphereShape collisionShape = new SphereShape(BALL_RADIUS);

        // Create Dynamic Objects
        Transform startTransform = new Transform();
        startTransform.setIdentity();

        float mass = 1f;

        Vector3f localInertia = new Vector3f(0, 0, 0);
        collisionShape.calculateLocalInertia(mass, localInertia);

        startTransform.origin.set(new Vector3f(0, 0, 0));

        RigidBodyConstructionInfo rbInfo = new RigidBodyConstructionInfo(
                mass, null, collisionShape, localInertia);
        RigidBody body = new RigidBody(rbInfo);
        body.setDamping(BALL_DRAG, BALL_ANGULAR_DAMPING);
        body.setRestitution(BALL_RESTITUTION);
        body.setFriction(BALL_FRICTION);
        body.setActivationState(CollisionObject.DISABLE_DEACTIVATION);

        return body;
    }

    public static boolean isCarNearWall(AgentInput input) {
        Vector3 position = input.getMyPosition();
        return Math.abs(position.x) > SIDE_WALL - 2 ||
                Math.abs(position.y) > BACK_WALL - 2 ||
                Math.abs(position.x) + Math.abs(position.y) > CORNER_ANGLE_CENTER.x + CORNER_ANGLE_CENTER.y - 2;
    }
}