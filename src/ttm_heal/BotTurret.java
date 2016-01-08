package ttm_heal;

import battlecode.common.*;

public class BotTurret extends Globals {

	public static int motherId = 0;
	public static MapLocation motherLocation = null;
	public static boolean isHappyShooting = false;
	
	private static Signal[] currentSignals;

	public static void loop() {
		FastMath.initRand(rc);
		Debug.init("spotting");
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
		double bestLocHealth = Double.MAX_VALUE;
		RobotInfo[] enemies = rc.senseNearbyRobots(mySensorRadiusSquared, them);
		for (RobotInfo enemy : enemies) {
			if (!rc.canAttackLocation(enemy.location)) {
				continue;
			}
			if (enemy.health < bestLocHealth) {
				bestLoc = enemy.location;
				bestLocHealth = enemy.health;
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
			if (zombie.health < bestLocHealth) {
				bestLoc = zombie.location;
				bestLocHealth = zombie.health;
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
	
	private static void initTurret() {
		rc.emptySignalQueue();
	}
	

	private static MapLocation targetPosition = new MapLocation(1000, 1000);
	
	private static Direction[] goodDir = { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST };
	private static Direction[] badDir = { Direction.NORTH_EAST, Direction.SOUTH_EAST, Direction.NORTH_WEST, Direction.SOUTH_WEST };

	private static void trySettle() throws GameActionException {
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

	private static MapLocation friendVec() {
		RobotInfo[] friend = rc.senseNearbyRobots(mySensorRadiusSquared, us);
		MapLocation vecSum = new MapLocation(0,0);
		for (RobotInfo ri : friend) {
			if (ri.type != RobotType.TURRET) {
				continue;
			}
			MapLocation vec = FastMath.minusVec(ri.location, here);
			vecSum = FastMath.addVec(vecSum, vec);
		}
		return vecSum;
	}
	
	private static double computeArmyPos(MapLocation vecSum) {
		MapLocation targetVec = FastMath.minusVec(targetPosition, here);
		double mag = FastMath.dotVec(vecSum, targetVec) / Math.pow(FastMath.dotVec(targetVec, targetVec), 0.5);
//		System.out.println("mag = " + mag);
		return mag;
	}
	
	private static void tryMoveToTarget() throws GameActionException {
		if (!rc.isCoreReady()) return;
		Bug.goTo(targetPosition);
	}

	private static void turnTTM() throws GameActionException {
//		double ap = computeArmyPos(friendVec());
//		if (ap > 5) {
//			tryMoveToTarget();
//			return;
//		}
		if (0 == (here.x + here.y) % 2) {
			rc.unpack();
			initTurret();
			isTTM = false;
			return;
		}
//		if (ap > 0) {
//			tryMoveToTarget();
//			return;
//		}
		trySettle();
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
		if (0 != (here.x + here.y) % 2) {
			rc.pack();
			isTTM = true;
			return;
		}
//		double ap = computeArmyPos(friendVec());
//		if (ap > 20) {
//			rc.pack();
//			isTTM = true;
//			return;
//		}
		currentSignals = rc.emptySignalQueue();
		if (rc.isWeaponReady()) {
			if (shootEnemy()) {
				isHappyShooting = true;
			} else {
				isHappyShooting = false;
			}
		}
	}
	
	public static boolean isTTM = false;

	private static void turn() throws GameActionException {
		Globals.update();
		if (isTTM) {
			turnTTM();
		} else {
			turnTurret();
		}
	}
}
