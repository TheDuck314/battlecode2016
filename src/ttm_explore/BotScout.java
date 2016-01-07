package ttm_explore;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Team;

public class BotScout extends Globals {
	public static void loop() {
		FastMath.initRand(rc);
    	Debug.init("spotting");
		while (true) {
			try {
			    turn();
			} catch (Exception e) {
				e.printStackTrace();
			}
			Clock.yield();
		}
	}
	
	private static void signalAboutEnemies() throws GameActionException {
		RobotInfo[] allies = rc.senseNearbyRobots(mySensorRadiusSquared, us);
		MapLocation[] turrets = new MapLocation[1000];
		int numTurrets = 0;
		for (RobotInfo ally : allies) {
			if (ally.type == RobotType.TURRET || ally.type == RobotType.TTM) {
				turrets[numTurrets++] = ally.location;
			}
		}
		
		RobotInfo[] enemies = rc.senseNearbyRobots(mySensorRadiusSquared, them);
		RobotInfo[] zombies = rc.senseNearbyRobots(mySensorRadiusSquared, Team.ZOMBIE);
		MapLocation bestTarget = null;
		int bestScore = Integer.MIN_VALUE;
		for (RobotInfo enemy : enemies) {
			int score = 0;
			MapLocation enemyLoc = enemy.location;
			for (int i = 0; i < numTurrets; ++i) {
				int distSq = enemyLoc.distanceSquaredTo(turrets[i]);
				if (distSq > RobotType.TURRET.sensorRadiusSquared && distSq <= RobotType.TURRET.attackRadiusSquared) {
					++score;
				}
			}
			if (score > bestScore) {
				bestScore = score;
				bestTarget = enemyLoc;
			}
		}
		for (RobotInfo enemy : zombies) {
			int score = 0;
			MapLocation enemyLoc = enemy.location;
			for (int i = 0; i < numTurrets; ++i) {
				int distSq = enemyLoc.distanceSquaredTo(turrets[i]);
				if (distSq > RobotType.TURRET.sensorRadiusSquared && distSq <= RobotType.TURRET.attackRadiusSquared) {
					++score;
				}
			}
			if (score > bestScore) {
				bestScore = score;
				bestTarget = enemyLoc;
			}
		}
		
		if (bestTarget != null) {
			Debug.indicate("spotting", 0, "spotting target at " + bestTarget);
			Messages.sendTurretTarget(bestTarget, 2*mySensorRadiusSquared);
		}
	}
	
	private static Direction[] goodDir = { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST };
	private static Direction[] badDir = { Direction.NORTH_EAST, Direction.SOUTH_EAST, Direction.NORTH_WEST, Direction.SOUTH_WEST };
	
	private static void tryMoveAround() throws GameActionException {
		if (!rc.isCoreReady()) return;
		int rdn = FastMath.rand256();
		for (int i = 0; i < 4; ++i) {
			Direction dir = goodDir[(rdn + i) % 4];
			if (rc.canMove(dir)) {
				rc.move(dir);
				return;
			}
		}
		for (int i = 0; i < 4; ++i) {
			Direction dir = badDir[(rdn + i) % 4];
			if (rc.canMove(dir)) {
				rc.move(dir);
				return;
			}
		}
	}
	
	private static void turn() throws GameActionException {
		Globals.update();
		signalAboutEnemies();
		if (0 != (here.x + here.y) % 2) {
			tryMoveAround();
		}
	}
}
