package explore;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapLocation;
import battlecode.common.RobotInfo;
import battlecode.common.RobotType;
import battlecode.common.Signal;
import battlecode.common.Team;
import explore.Messages.PartsLocation;

public class BotArchon extends Globals {
	public static void loop() {
		Debug.init("explore");
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
		processSignals();
		MapEdges.detectAndBroadcastMapEdges(5); // visionRange = 5
		Debug.indicate("edges", 0, String.format("map X = [%d, %d], mapY = [%d, %d]", MapEdges.minX, MapEdges.maxX, MapEdges.minY, MapEdges.maxY));
		
		countTurret();
		trySpawn();
		tryRepairAlly();
		tryConvertNeutrals();
		
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
			// Bug.goTo(bestLoc);
		}
	}

	private static int spawnCount = 0;

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
		if(!(myID == 333 && spawnCount == 0)) return;
		
		rc.setIndicatorString(2, "trySpawn: turn " + rc.getRoundNum());
		
		RobotType spawnType = RobotType.SCOUT; //(spawnCount % 5 == 4 ? RobotType.SCOUT : RobotType.TURRET);

		if (!rc.hasBuildRequirements(spawnType)) return;

		// scouts can probably have different spawning conditions
		double parts = rc.getTeamParts();
		if (nTurret <= 1 || parts > 180 || parts > FastMath.rand256()) {
			for (Direction dir : Direction.values()) {
				MapLocation tl = here.add(dir);
				if (0 == (tl.x + tl.y) % 2 && rc.canBuild(dir, spawnType)) {
					rc.build(dir, spawnType);
					++spawnCount;
					if (spawnType == RobotType.SCOUT) {
						Messages.sendKnownMapEdges(2); // tell scout known map edges
					}
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
	
	private static void processSignals() {
		Signal[] signals = rc.emptySignalQueue();
		for (Signal sig : signals) {
			if (sig.getTeam() != us) continue;
			
			int[] data = sig.getMessage();
			if (data != null) {
				switch(data[0] & Messages.CHANNEL_MASK) {
				case Messages.CHANNEL_FOUND_PARTS:
					PartsLocation partsLoc = Messages.parsePartsLocation(data);
					Debug.indicate("explore", 0, "heard about " + partsLoc.numParts + " parts at " + partsLoc.location);
					break;
				case Messages.CHANNEL_ZOMBIE_DEN:
					MapLocation zombieDenLoc = Messages.parseZombieDenLocation(data);
					Debug.indicate("explore", 1, "heard about a zombie den at " + zombieDenLoc);
					break;
				case Messages.CHANNEL_ENEMY_TURRET_WARNING:
					MapLocation enemyTurretLoc = Messages.parseEnemyTurretWarning(data);
					Debug.indicate("explore", 2, "heard about an enemy turret at " + enemyTurretLoc);
					break;
					
				case Messages.CHANNEL_MAP_MIN_X:
					Messages.processMapMinX(data);
					break;
				case Messages.CHANNEL_MAP_MAX_X:
					Messages.processMapMaxX(data);
					break;
				case Messages.CHANNEL_MAP_MIN_Y:
					Messages.processMapMinY(data);
					break;
				case Messages.CHANNEL_MAP_MAX_Y:
					Messages.processMapMaxY(data);
					break;
				}
			}
		}
	}
}
