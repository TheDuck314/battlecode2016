package radar8;

import battlecode.common.*;
import radar8.Messages.PartsLocation;

public class BotArchon extends Globals {
	private static int spawnCount = 0;

	private static int lastArchonLocationMessageRound = 0;

	private static int nArchons = 0;
	private static MapLocation[] archonsLoc = new MapLocation[10];
	private static int[] archonsId = new int[10];
	private static int archonOrder = 0;
	private static MapLocation rallyPoint = null;
	
	private static MapLocation currentDestination = null;
	
	public static void loop() throws GameActionException {
		Debug.init("parts");
		FastMath.initRand(rc);
		//initArchons();

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
	
	private static void initArchons() throws GameActionException {
		Globals.update();
		archonsLoc[nArchons] = here;
		archonsId[nArchons] = myID;
		nArchons += 1;
		Messages.sendArchonLocation(here, MapEdges.maxBroadcastDistSq());
		Clock.yield();
		Signal[] signals = rc.emptySignalQueue();
		for (Signal sig : signals) {
			if (sig.getTeam() != us) continue;
			int[] data = sig.getMessage();
			if (data != null) {
				if ((data[0] & Messages.CHANNEL_MASK) == Messages.CHANNEL_ARCHON_LOCATION) {
					archonsLoc[nArchons] = sig.getLocation();
					archonsId[nArchons] = sig.getID();
					nArchons += 1;
				}
			}
		}
		rallyPoint = here;
		for (int i = 1; i < nArchons; ++i) {
			rallyPoint = FastMath.addVec(rallyPoint, archonsLoc[i]);
		}
		rallyPoint = FastMath.multiplyVec(1.0/(double)nArchons, rallyPoint);
		rallyPoint = rallyPoint.add(rallyPoint.directionTo(here), 0);
		rc.setIndicatorDot(rallyPoint, 255, 0, 0);
		for (int i = 0; i < nArchons; ++i) {
			if (archonsId[i] < myID) {
				archonOrder += 1;
			}
		}
	}
	
	private static void turn() throws GameActionException {
		if (rc.getRoundNum() <= 100 && rallyPoint != null && here.distanceSquaredTo(rallyPoint) > 2) {
			if (rc.isCoreReady()) {
			    DBug.goTo(rallyPoint);
			}
			return;
		}
		
		processSignals();
		MapEdges.detectAndBroadcastMapEdges(5); // visionRange = 5

		trySendAttackTarget();
		
		trySendArchonLocationMessage();

		
		trySpawn();
		
		tryRepairAlly();
		tryConvertNeutrals();
		
		if (rc.isCoreReady()) {
			pickDestination();
			
			if (currentDestination != null) {
			    goToDestination();
			} else {
				goToCenterOfMass();
			}
		}
	}
	
	private static void trySendArchonLocationMessage() throws GameActionException {
		if (lastArchonLocationMessageRound < rc.getRoundNum() - 60) {
			Messages.sendArchonLocation(here, 900);
			lastArchonLocationMessageRound = rc.getRoundNum();
			Debug.indicate("heal", 0, "sent archon location");
		}
	}
	
	private static void trySendAttackTarget() throws GameActionException {
		RobotInfo[] targets = rc.senseHostileRobots(here, mySensorRadiusSquared);
		int numTargets = (targets.length < 3 ? targets.length : 3);
		for (int i = 0; i < numTargets; ++i) {
			Messages.sendAttackTarget(targets[i].location, 9 * mySensorRadiusSquared);
		}
	}
	
	private static void trySpawn() throws GameActionException {
		if (!rc.isCoreReady()) return;

		RobotType spawnType = RobotType.SOLDIER;
		if (rc.getRoundNum() > 400) {
			if (spawnCount % 4 == 0) {
				spawnType = RobotType.TURRET;
			}
		}
		if (spawnCount % 10 == 0) {
			spawnType = RobotType.SCOUT;
		}
		
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
		double mostHealth = 0;
		for (RobotInfo ally : healableAllies) {
			if (ally.type == RobotType.ARCHON) continue;
			if (ally.health < ally.maxHealth && ally.health > mostHealth) {
				bestLoc = ally.location;
				mostHealth = ally.health;
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
	
	private static void considerDestination(MapLocation loc) {
		if (currentDestination == null || here.distanceSquaredTo(loc) < here.distanceSquaredTo(currentDestination)) {
			currentDestination = loc;
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
					Debug.indicate("parts", 0, "parts at " + partsLoc.location);
					considerDestination(partsLoc.location);
					break;
				case Messages.CHANNEL_FOUND_NEUTRAL:
					MapLocation neutralLoc = Messages.parseNeutralLocation(data);
					considerDestination(neutralLoc);
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
	
	private static void pickDestination() throws GameActionException {
		if (currentDestination != null) {
			if (here.equals(currentDestination)) {
				currentDestination = null;
			} else if (rc.canSenseLocation(currentDestination)) {
				if (rc.senseParts(currentDestination) == 0) {
					RobotInfo robot = rc.senseRobotAtLocation(currentDestination);
					if (robot == null || robot.team != Team.NEUTRAL) {
						currentDestination = null;
					}
				}
			}
		}
		
		MapLocation[] nearbyLocs = MapLocation.getAllMapLocationsWithinRadiusSq(here, mySensorRadiusSquared);
		
		for (MapLocation loc : nearbyLocs) {
			double numParts = rc.senseParts(loc);
			if (numParts >= 1) {
				considerDestination(loc);
				continue;
			}
			RobotInfo robot = rc.senseRobotAtLocation(loc);
			if (robot != null && robot.team == Team.NEUTRAL) {
				considerDestination(loc);
				continue;
			}
		}	
	}
	
	private static void goToDestination() throws GameActionException {
		Debug.indicate("parts", 1, "destination = " + currentDestination);
		if (currentDestination != null) {
			DirectNav.goTo(currentDestination);
		}
	}
	
	private static void goToCenterOfMass() throws GameActionException {
		RobotInfo[] allies = rc.senseNearbyRobots(mySensorRadiusSquared, us);
		if (allies.length == 0) return;
		int avgX = 0;
		int avgY = 0;
		for (RobotInfo ally : allies) {
			avgX += ally.location.x;
			avgY += ally.location.y;
		}
		avgX /= allies.length;
		avgY /= allies.length;
		DirectNav.goTo(new MapLocation(avgX, avgY));
	}
}
