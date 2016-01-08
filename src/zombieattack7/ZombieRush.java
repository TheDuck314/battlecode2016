package zombieattack7;

import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Team;

public class ZombieRush extends Globals {
	public static MapLocation enemyCenterOfMass = new MapLocation(542, 29);

	private static boolean tryToGetInfected() throws GameActionException {
		// find nearest viper or zombie
		MapLocation infectionSource = null;
		RobotInfo[] zombies = rc.senseNearbyRobots(mySensorRadiusSquared, Team.ZOMBIE);
		if (zombies.length > 0) {
			infectionSource = Util.closest(zombies).location;
		}
		if (infectionSource == null) {
			RobotInfo[] allies = rc.senseNearbyRobots(mySensorRadiusSquared, us);
			int bestDistSq = Integer.MAX_VALUE;
			for (RobotInfo ally : allies) {
				if (ally.type == RobotType.VIPER) {
					int distSq = here.distanceSquaredTo(ally.location);
					if (distSq < bestDistSq) {
						bestDistSq = distSq;
						infectionSource = ally.location;
					}
				}
			}
		}
		if (infectionSource != null) {
			Bug.tryMoveInDirection(here.directionTo(infectionSource));
			return true;
		}
		return false;
	}
	
	public static void suicideZombieRushRegular(MapLocation lastKnownArchonLocation) throws GameActionException {
		if (enemyCenterOfMass == null) return;
		if (lastKnownArchonLocation == null) return;
				
		if (rc.getInfectedTurns() > 0) {
			rc.disintegrate();
			return;
		}
		
		int enemyDist = FastMath.floorSqrt(here.distanceSquaredTo(enemyCenterOfMass));
		int archonDist = FastMath.floorSqrt(here.distanceSquaredTo(lastKnownArchonLocation));
		
		
		
		if (rc.getRoundNum() > 2700) {
			if (enemyDist > archonDist * 0.9) {
				rc.disintegrate();
			}
			if (enemyDist < archonDist) {
				if (rc.isCoreReady() && tryToGetInfected()) {
				    return;
				}
			} 
		}
		
		
		if (enemyDist > archonDist / 2) {
			if (rc.isCoreReady()) {
				Bug.goTo(enemyCenterOfMass);
			}
		}
	}
	

	private static void tryToInfectAllies() throws GameActionException {
		if (!rc.isWeaponReady()) return;
		
		RobotInfo[] allies = rc.senseNearbyRobots(myAttackRadiusSquared, us);		
		for (RobotInfo ally : allies) {
			if (ally.viperInfectedTurns > 0 || ally.zombieInfectedTurns > 0) continue;
			rc.attackLocation(ally.location);
			return;
		}
		rc.attackLocation(here);
	}

	public static void suicideZombieRushViper(MapLocation lastKnownArchonLocation) throws GameActionException {
		if (enemyCenterOfMass == null) return;
		if (lastKnownArchonLocation == null) return;
		
		if (rc.getInfectedTurns() == 1) {
			rc.disintegrate();
			return;
		}
		
		if (!rc.isCoreReady()) return;

		int enemyDist = FastMath.floorSqrt(here.distanceSquaredTo(enemyCenterOfMass));
		int archonDist = FastMath.floorSqrt(here.distanceSquaredTo(lastKnownArchonLocation));
		
		if (rc.getRoundNum() < 2700) {
		    if (enemyDist > archonDist / 2) {
			    Bug.goTo(enemyCenterOfMass);
		    }
		    return;
		}
		
		tryToInfectAllies();
	}
}
