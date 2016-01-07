package ttm_scout_move;

import battlecode.common.*;;

public class BotViper extends Globals {
	
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
	
	public static void shootEnemies() throws GameActionException {
		MapLocation enemyArchonLocation = null;
		RobotInfo[] infos = rc.senseNearbyRobots(myAttackRadiusSquared, them);
		for (RobotInfo info : infos) {
			if (info.type == RobotType.ARCHON) {
				enemyArchonLocation = info.location;
			} else if (info.viperInfectedTurns == 0) {
				rc.attackLocation(info.location);
				return;
			}
		}
		infos = rc.senseNearbyRobots(myAttackRadiusSquared, Team.ZOMBIE);
		for (RobotInfo info : infos) {
			 rc.attackLocation(info.location);
			 return;
		}
		if (enemyArchonLocation != null) {
			rc.attackLocation(enemyArchonLocation);
			return;
		}
	}
	
	private static void turn() throws GameActionException {
		update();
		updateMotherLocation();
		if (rc.isWeaponReady()) {
			shootEnemies();
		}
		if (rc.isCoreReady()) {
			Bug.goTo(motherLocation);
		}
	}
}
