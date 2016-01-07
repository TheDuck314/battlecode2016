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
	private static MapLocationHashSet knownZombieDens = new MapLocationHashSet();
	private static MapLocation[] denAttackQueue = new MapLocation[1000];
	private static int denAttackQueueHead = 0;
	private static int denAttackQueueTail = 0;
	
	public static void loop() {
		Debug.init("dens");
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
		//Debug.indicate("edges", 0, String.format("map X = [%d, %d], mapY = [%d, %d]", MapEdges.minX, MapEdges.maxX, MapEdges.minY, MapEdges.maxY));
		
		if (rc.getRoundNum() > 200) {
			if(rc.getRoundNum() % 50 == 0) {
				Messages.sendAttackTarget(here, MapEdges.maxBroadcastDistSq());
			} else if (rc.getRoundNum() % 10 == 0) {
				Messages.sendAttackTarget(here, 9 * mySensorRadiusSquared);
			}
		}
		
		//countTurret();
		/*if (denAttackQueue[denAttackQueueHead] == null 
				|| here.distanceSquaredTo(denAttackQueue[denAttackQueueHead]) <= mySensorRadiusSquared) {
			trySpawn();
		}*/
		trySpawn();
		
		
		tryRepairAlly();
		tryConvertNeutrals();
		
		//exploreForNeutralsAndParts();
		
		Debug.indicate("dens", 2, "den queue: ");
		for (int i = denAttackQueueHead; i < denAttackQueueTail; ++i) {
			Debug.indicateAppend("dens", 2, denAttackQueue[i].toString() + ", ");
		}
		
		tryAttackZombieDen();		
	}
	
	private static void tryAttackZombieDen() throws GameActionException {
		if (!rc.isCoreReady()) return;
		
		MapLocation target = denAttackQueue[denAttackQueueHead];
		if (target == null) {
			Debug.indicate("dens", 0, "target is null :(");
			return;
		}

		if (rc.canSenseLocation(target)) {
			RobotInfo botAtTarget = rc.senseRobotAtLocation(target);
			if (botAtTarget == null || botAtTarget.type != RobotType.ZOMBIEDEN) {
				// this den has been destroyed. Move on to the next one.
				++denAttackQueueHead;
				Debug.indicate("dens", 0, "den at " + target + " seems to be destroyed. now head = " + denAttackQueueHead);
				return;
			}
		}
		
		if (here.distanceSquaredTo(target) <= 20) {
			Debug.indicate("dens", 0, "stationed near den at " + target);
			//Messages.sendAttackTarget(target, 100*mySensorRadiusSquared);
			return;
		}
		
		Debug.indicate("dens", 0, "going to den at " + target);
		//Bug.goTo(target);
		DirectNav.goTo(target);
		//Messages.sendAttackTarget(target, 2*mySensorRadiusSquared);
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

		RobotType spawnType = (spawnCount == 0 ? RobotType.SCOUT : RobotType.SOLDIER);

		if (!rc.hasBuildRequirements(spawnType)) return;

		Direction dir = Direction.values()[FastMath.rand256() % 8];
		for (int i = 0; i < 8; ++i) {
			if (rc.canBuild(dir, spawnType)) {
				rc.build(dir, spawnType);
				++spawnCount;
				if (spawnType == RobotType.SCOUT) {
					Messages.sendKnownMapEdges(2); // tell scout known map edges
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
					if (knownZombieDens.add(zombieDenLoc)) {
						denAttackQueue[denAttackQueueTail++] = zombieDenLoc;
					} else {
						Debug.indicateAppend("explore", 1, "; but we already knew about it");
					}
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
					
				default:
				}
			} else {
				// simple signal with no message
			}
		}
	}
}
