package ttm_explore;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
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
	
	private static int lastSignal = 3000;
	
	private static void signalAboutEnemies() throws GameActionException {
		lastSignal += 1;
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
			lastSignal = 0;
			return;
		}
	}
	
	private static Direction[] goodDir = { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST };
	private static Direction[] badDir = { Direction.NORTH_EAST, Direction.SOUTH_EAST, Direction.NORTH_WEST, Direction.SOUTH_WEST };
	
	private static void tryMoveAround() throws GameActionException {
		if (!rc.isCoreReady()) return;
		int rdn = FastMath.rand256();
		for (int i = 0; i < 4; ++i) {
			Direction dir = goodDir[(rdn + i) % 4];
			if (DBug.tryMoveClearDir(dir)) {
				return;
			}
		}
		for (int i = 0; i < 4; ++i) {
			Direction dir = badDir[(rdn + i) % 4];
			if (DBug.tryMoveClearDir(dir)) {
				return;
			}
		}
	}
	
	private static Direction avoidEnemyDirection() {
		Direction escapeDir = null;
		if (escapeDir == null) {
			RobotInfo[] enemies = rc.senseNearbyRobots(mySensorRadiusSquared, Team.ZOMBIE);
			for (RobotInfo e : enemies) {
//				System.out.println("Got zombie in sight!");
				if (e.location.distanceSquaredTo(here) <= e.type.attackRadiusSquared) {
//					System.out.println("Got zombie in range!");
					escapeDir = e.location.directionTo(here);
				}
			}
		}
		if (escapeDir == null) {
			RobotInfo[] enemies = rc.senseNearbyRobots(mySensorRadiusSquared, them);
			for (RobotInfo e : enemies) {
//				System.out.println("Got enemy in sight!");
				if (e.location.distanceSquaredTo(here) <= e.type.attackRadiusSquared) {
//					System.out.println("Got enemy in range!");
					escapeDir = e.location.directionTo(here);
				}
			}
		}
		return escapeDir;
	}
	
	private static void avoidEnemy() throws GameActionException {
		if (!rc.isCoreReady()) return;
		Direction escapeDir = avoidEnemyDirection();
		if (null != escapeDir) {
//			System.out.println("Try escape!");
			if (Bug.tryMoveInDirection(escapeDir)) {
//				System.out.println("escape!");
				return;
			}
		}
	}
	
	private static int nTurret = 0;
	private static int nTurretMax = 4;
	
	private static void countTurret() throws GameActionException {
		nTurret = 0;
		RobotInfo[] infos = rc.senseNearbyRobots(2, us);
		for (RobotInfo info : infos) {
			if (info.type == RobotType.TURRET) {
				nTurret += 1;
			}
		}
		nTurretMax = nTurret;
		for (Direction dir : Direction.values()) {
			MapLocation tl = here.add(dir);
			if (0 == (tl.x + tl.y) % 2 && rc.canMove(dir)) {
				nTurretMax += 1;
			}
		}
	}
	
	private static MapLocation friendVec() {
		RobotInfo[] friend = rc.senseNearbyRobots(mySensorRadiusSquared, us);
		MapLocation vecSum = new MapLocation(0,0);
		for (RobotInfo ri : friend) {
			if (ri.type == RobotType.TURRET) {
				Direction dir = here.directionTo(ri.location);
				vecSum = vecSum.add(dir);
				continue;
			} else if (ri.type == RobotType.SCOUT) {
				Direction dir = ri.location.directionTo(here);
				vecSum = vecSum.add(dir);
				continue;
			} else if (ri.type == RobotType.ARCHON) {
				Direction dir = here.directionTo(ri.location);
				vecSum = vecSum.add(dir);
				continue;
			}
		}
		return vecSum;
	}
	
	private static void turn() throws GameActionException {
		Globals.update();
		signalAboutEnemies();
		avoidEnemy();
		if (!rc.isCoreReady()) return;
		if (0 == (here.x + here.y) % 2) {
			tryMoveAround();
			return;
		}
		if (lastSignal < 5) {
			return;
		}
		countTurret();
		if (nTurret < nTurretMax) {
			DBug.tryMoveInDirection(FastMath.dirFromVec(friendVec()));
			return;
		} else {
			int rdn = FastMath.rand256();
			for (int i = 0; i < 8; ++i) {
				Direction dir = Direction.values()[rdn % 8];
				if (rc.senseRubble(here.add(dir)) >= GameConstants.RUBBLE_SLOW_THRESH) {
					rc.clearRubble(dir);
					return;
				}
			}
			Direction dir = Direction.values()[rdn % 8];
			if (DBug.tryMoveInDirection(dir)) {
				return;
			}
			return;
		}
	}
}
