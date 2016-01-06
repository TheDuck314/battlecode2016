package explore;

import battlecode.common.*;;

public class BotSoldier extends Globals {
	
	public static int motherId = 0;
	public static MapLocation motherLocation = null;
	public static boolean isHappyShooting = false;

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
	
	public static boolean shootEnemy() throws GameActionException {
		MapLocation bestLoc = null;
		RobotInfo[] infos = rc.senseNearbyRobots(myAttackRadiusSquared, them);
		for (RobotInfo info : infos) {
			if (info.type == RobotType.ARCHON && bestLoc == null) {
				if (bestLoc == null) {
					bestLoc = info.location;
				}
			} else {
				rc.attackLocation(info.location);
				return true;
			}
		}
		infos = rc.senseNearbyRobots(myAttackRadiusSquared, Team.ZOMBIE);
		for (RobotInfo info : infos) {
			 if (info.type == RobotType.ZOMBIEDEN) {
				 if (bestLoc == null) {
					 bestLoc = info.location;
				 }
			 } else {
				 rc.attackLocation(info.location);
				 return true;
			 }
		}
		if (bestLoc != null) {
			rc.attackLocation(bestLoc);
			return true;
		}
		return false;
	}

	public static void followMother() throws GameActionException {
		if (rc.isCoreReady()) {
			updateMotherLocation();
			Bug.goTo(motherLocation);
		}
	}
	
	private static void turn() throws GameActionException {
		update();
		if (rc.isWeaponReady()) {
			if (shootEnemy()) {
				isHappyShooting = true;
			} else {
				isHappyShooting = false;
			}
		}
		if (rc.getHealth() != myType.maxHealth || !isHappyShooting) {
			followMother();
		}
	}
}
