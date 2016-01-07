package ttm_heal;

import battlecode.common.*;;

public class BotGuard extends Globals {
	
	public static int motherId;
	public static MapLocation motherLocation;

	public static void loop() {
		updateMotherId(8);
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
	
	private static boolean tryAttack(RobotInfo[] infos) throws GameActionException {
		if (infos.length == 0) return false;
		rc.attackLocation(infos[0].location);
		return true;
	}
	

//	private static boolean tryMoveToward(RobotInfo[] infos1, ) {
//		if (infos.length == 0) return false;
//		RobotInfo closest = null;
//		int bestDistSq = Integer.MAX_VALUE;
//		for (RobotInfo info : infos) {
//			int distSq = here.distanceSquaredTo(info.location);
//			if (distSq < )
//		}
//	}
	
	public static boolean tryShootEnemies() throws GameActionException {
		RobotInfo[] adjacentEnemies = rc.senseNearbyRobots(myAttackRadiusSquared, them);
		if(tryAttack(adjacentEnemies)) return true;
		
		RobotInfo[] adjacentZombies = rc.senseNearbyRobots(myAttackRadiusSquared, Team.ZOMBIE);
		if(tryAttack(adjacentZombies)) return true;
		
		return false;
	}
	
	private static boolean tryChargeEnemies() throws GameActionException {
		RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(mySensorRadiusSquared, them);
		RobotInfo[] nerabyZombies = rc.senseNearbyRobots(mySensorRadiusSquared, Team.ZOMBIE);
		RobotInfo closest = null;
		int bestDistSq = Integer.MAX_VALUE;
		for (RobotInfo info : nearbyEnemies) {
			int distSq = here.distanceSquaredTo(info.location);
			if (distSq < bestDistSq) {
				bestDistSq = distSq;
				closest = info;
			}
		}
		for (RobotInfo info : nerabyZombies) {
			int distSq = here.distanceSquaredTo(info.location);
			if (distSq < bestDistSq) {
				bestDistSq = distSq;
				closest = info;
			}
		}
		if (closest != null) {
			Bug.tryMoveInDirection(here.directionTo(closest.location));
			return true;
		}
		return false;
	}
	
	private static void turn() throws GameActionException {
		update();
		updateMotherLocation();
		if (rc.isWeaponReady()) {
			if(tryShootEnemies()) return;
		}
		if (rc.isCoreReady()) {
			if (tryChargeEnemies()) return;
			Bug.goTo(motherLocation);
		}
	}
}
