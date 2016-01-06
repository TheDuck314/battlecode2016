package turret;

import turret.FastMath;
import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Team;

public class BotArchon extends Globals {
	public static void loop() {
		FastMath.initRand(rc);
		while (true) {
			try {
				Globals.update();
			    turn();
			} catch (Exception e) {
				e.printStackTrace();
			}
			Clock.yield();
		}
	}
	
	private static void turn() throws GameActionException {
		avoidEnemy();
		trySpawn();
		tryRepairAlly();
		tryConvertNeutrals();
		
		countTurret();
		exploreForNeutralsAndParts();
	}
	
	private static void exploreForNeutralsAndParts() throws GameActionException {
		if (!rc.isCoreReady()) return;
		
		MapLocation[] nearbyLocs = MapLocation.getAllMapLocationsWithinRadiusSq(here, RobotType.ARCHON.sensorRadiusSquared);
		
		MapLocation bestLoc = null;
		int bestDistSq = Integer.MAX_VALUE;
		for (MapLocation loc : nearbyLocs) {
			double parts = rc.senseParts(loc);
			if (parts == 0) {
				RobotInfo info = rc.senseRobotAtLocation(loc);
				if (info == null || info.team != Team.NEUTRAL) continue;
			}
			// there are either parts or a neutral at this location
			int distSq = here.distanceSquaredTo(loc);
			if (distSq < bestDistSq) {
				bestDistSq = distSq;
				bestLoc = loc;
			}			
		}
		if (nTurret >= nTurretMax) {
			int rdn = FastMath.rand256();
			Direction dir = Direction.values()[rdn % 8];
			if (rc.canMove(dir)) {
				rc.move(dir);
				return;
			}
		}
		if (bestLoc != null) {
//			Bug.goTo(bestLoc);
		}
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
	
	private static RobotType spawnType = RobotType.SOLDIER;
	
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
	
	private static void trySpawn() throws GameActionException {
		if (!rc.isCoreReady()) return;
		
		rc.setIndicatorString(2, "trySpawn: turn " + rc.getRoundNum());
		
		spawnType = RobotType.TURRET;

		if (!rc.hasBuildRequirements(spawnType)) return;

		double parts = rc.getTeamParts();
		if (nTurret <= 1 || parts > 180 || parts > FastMath.rand256()) {
			for (Direction dir : Direction.values()) {
				MapLocation tl = here.add(dir);
				if (0 == (tl.x + tl.y) % 2 && rc.canBuild(dir, spawnType)) {
					rc.build(dir, spawnType);
					return;
				}
			}
		}
	}
	
	private static void tryRepairAlly() throws GameActionException {
		RobotInfo[] healableAllies = rc.senseNearbyRobots(RobotType.ARCHON.attackRadiusSquared, us);
		MapLocation bestLoc = null;
		double lowestHealth = 10000;
		for (RobotInfo ally : healableAllies) {
			if (ally.type == RobotType.ARCHON) continue;
			if (ally.health < ally.maxHealth && ally.health < lowestHealth) {
				bestLoc = ally.location;
				lowestHealth = ally.health;
			}
		}
		if (bestLoc != null) {
			rc.repair(bestLoc);
		}
	}
	
	private static void tryConvertNeutrals() throws GameActionException {
		if (!rc.isCoreReady()) return;
		RobotInfo[] adjacentNeutrals = rc.senseNearbyRobots(2, Team.NEUTRAL);
		for (RobotInfo neutral : adjacentNeutrals) {
			rc.activate(neutral.location);
			return;
		}
	}
}
