package guard;

import battlecode.common.Clock;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Team;

public class BotTurret extends Globals {

	public static int motherId = 0;
	public static MapLocation motherLocation = null;
	public static boolean isHappyShooting = false;

	public static void loop() {
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
		RobotInfo[] infos = rc.senseNearbyRobots(mySensorRadiusSquared, them);
		for (RobotInfo info : infos) {
			if (!rc.canAttackLocation(info.location)) {
				continue;
			}
			if (info.type == RobotType.ARCHON && bestLoc == null) {
				if (bestLoc == null) {
					bestLoc = info.location;
				}
			} else {
				rc.attackLocation(info.location);
				return true;
			}
		}
		infos = rc.senseNearbyRobots(mySensorRadiusSquared, Team.ZOMBIE);
		for (RobotInfo info : infos) {
			if (!rc.canAttackLocation(info.location)) {
				continue;
			}
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

	private static void turn() throws GameActionException {
		update();
		if (rc.isWeaponReady()) {
			if (shootEnemy()) {
				isHappyShooting = true;
			} else {
				isHappyShooting = false;
			}
		}
	}
}
