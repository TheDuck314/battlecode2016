package ttm_dense_9;

import battlecode.common.*;

public class BotTurret extends Globals {

	public static int motherId = 0;
	public static MapLocation motherLocation = null;
	public static boolean isHappyShooting = false;

	private static Signal[] currentSignals;

	public static void loop() {
		FastMath.initRand(rc);
		Debug.init("position");
		initTurret();
		while (true) {
			try {
				turn();
			} catch (Exception e) {
				e.printStackTrace();
			}
			Clock.yield();
		}
	}

	public static void updateMotherId(int range) {
		RobotInfo[] infos = rc.senseNearbyRobots(range, us);
		for (RobotInfo info : infos) {
			if (info.type == RobotType.ARCHON) {
				motherId = info.ID;
				motherLocation = info.location;
				break;
			}
		}
	}

	public static void updateMotherLocation() throws GameActionException {
		if (rc.canSenseRobot(motherId)) {
			motherLocation = rc.senseRobot(motherId).location;
		} else {
			updateMotherId(mySensorRadiusSquared);
		}
	}

	public static boolean shootEnemy() throws GameActionException {
		MapLocation bestLoc = null;
		double bestScore = Double.MAX_VALUE;
		RobotInfo[] enemies = rc.senseNearbyRobots(mySensorRadiusSquared, them);
		for (RobotInfo enemy : enemies) {
			if (!rc.canAttackLocation(enemy.location)) {
				continue;
			}
			if (enemy.health < bestScore) {
				bestLoc = enemy.location;
				bestScore = enemy.health;
			}
		}
		if (bestLoc != null) {
			rc.attackLocation(bestLoc);
			return true;
		}
		RobotInfo[] zombies = rc.senseNearbyRobots(mySensorRadiusSquared, Team.ZOMBIE);
		for (RobotInfo zombie : zombies) {
			if (!rc.canAttackLocation(zombie.location)) {
				continue;
			}
			double score = zombie.health / zombie.attackPower;
			if (score < bestScore) {
				bestLoc = zombie.location;
				bestScore = zombie.health;
			}
		}
		if (bestLoc != null) {
			rc.attackLocation(bestLoc);
			return true;
		}

		Debug.indicate("spotting", 0, "going to check for spotting messages; message queue length = " + currentSignals.length);
		for (Signal sig : currentSignals) {
			if (sig.getTeam() != us) continue;
			int[] data = sig.getMessage();
			if (data != null) {
				switch(data[0] & Messages.CHANNEL_MASK) {
				case Messages.CHANNEL_TURRET_TARGET:
					MapLocation target = Messages.parseTurretTarget(data);
					Debug.indicate("spotting", 1, "got target = " + target);
					if (target != null && rc.canAttackLocation(target)) {
						Debug.indicate("spotting", 2, "attacking spotting target!");
						rc.attackLocation(target);
						return true;
					}
				}
			}
		}
		return false;
	}



	public static MapLocation turretCenter = null;
	public static int turretRadiusSq = 0;
	
	private static void processRallyPointSignals() {
		for (Signal sig : currentSignals) {
			if (sig.getTeam() != us) continue;
			int[] data = sig.getMessage();
			if (data != null) {
				switch(data[0] & Messages.CHANNEL_MASK) {
				case Messages.CHANNEL_RALLY_POINT:
					turretCenter = Messages.parseRallyPoint(data);
					turretRadiusSq = Messages.parseRallyPointRSq(data);
					Debug.indicate("position", 2, "got center = " + turretCenter + " rSq = " + turretRadiusSq);
				}
			}
		}
	}

	public static double turretLocationScore(MapLocation a) {
		if (turretCenter == null) {
			turretCenter = here;
		}
		int distSq = turretCenter.distanceSquaredTo(a);
		if (distSq > turretRadiusSq) {
			return -distSq;
		} else {
			return distSq;
		}
	}

	public static Direction betterDirection() throws GameActionException {
		double bestScore = turretLocationScore(here);
		Direction bestDir = null;
		for (Direction dir : Direction.values()) {
			MapLocation loc = here.add(dir);
			if (!rc.onTheMap(loc)) continue;
			if (rc.isLocationOccupied(loc)) continue;
			if (rc.senseRubble(loc) >= GameConstants.RUBBLE_OBSTRUCTION_THRESH) continue;
			double score = turretLocationScore(loc);
			if (score > bestScore) {
				bestDir = dir;
				bestScore = score;
			}
		}
		return bestDir;
	}
	
	public static int nearByTTMs() {
		int count = 0;
		RobotInfo[] infos = rc.senseNearbyRobots(9, us);
		for (RobotInfo r : infos) {
			if (r.type == RobotType.TTM) {
				count += 1;
			}
		}
		return count;
	}

	private static void turnTurret() throws GameActionException {
//		RobotInfo[] infos;
//		infos = rc.senseHostileRobots(here, mySensorRadiusSquared);
//		for (RobotInfo e : infos) {
//			if (e.type == RobotType.BIGZOMBIE
//					&& e.location.distanceSquaredTo(here) <= e.type.attackRadiusSquared
//					&& rc.getRoundNum() >= 2000
//					&& !rc.isInfected()) {
//				rc.disintegrate();
//			}
//		}
//		if (!isGoodTurretLocation(here)) {
//			rc.pack();
//			isTTM = true;
//			return;
//		}
//		double ap = computeArmyPos(friendVec());
//		if (ap > 20) {
//			rc.pack();
//			isTTM = true;
//			return;
//		}
		if (rc.isWeaponReady()) {
			if (shootEnemy()) {
				isHappyShooting = true;
			} else {
				isHappyShooting = false;
			}
		}
		if (!rc.isCoreReady()) return;
		if (!isHappyShooting
				&& betterDirection() != null
				&& nearByTTMs() < 1) {
			rc.pack();
			isTTM = true;
			return;
		}
	}

	private static int readyUnpackTurn = 0;
	
	private static void turnTTM() throws GameActionException {
		if (!rc.isCoreReady()) return;
//		double ap = computeArmyPos(friendVec());
//		if (ap > 5) {
//			tryMoveToTarget();
//			return;
//		}
//		if (isGoodTurretLocation(here)) {
//			rc.unpack();
//			initTurret();
//			isTTM = false;
//			return;
//		}
		Direction dir = betterDirection();
		if (dir != null) {
			rc.move(dir);
			readyUnpackTurn = 0;
			return;
		} else if (readyUnpackTurn == 0) {
			readyUnpackTurn = rc.getRoundNum();
			return;
		} else if (rc.getRoundNum() - readyUnpackTurn >= 5) {
			rc.unpack();
			initTurret();
			isTTM = false;
			return;
		}
//		if (ap > 0) {
//			tryMoveToTarget();
//			return;
//		}
//		trySettle();
	}
	
	
	private static void initTurret() {
		rc.emptySignalQueue();
	}

	public static boolean isTTM = false;

	private static void turn() throws GameActionException {
		Globals.update();
		currentSignals = rc.emptySignalQueue();
		processRallyPointSignals();
		if (isTTM) {
			turnTTM();
		} else {
			turnTurret();
		}
	}
}
