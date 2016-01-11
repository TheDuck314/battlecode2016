package team028;

import battlecode.common.*;

public class BotArchon extends Globals {
	private static int spawnCount = 0;

	private static int lastArchonLocationMessageRound = 0;

	private static int nArchons = 0;
	private static MapLocation[] archonsLoc = new MapLocation[10];
	private static int[] archonsId = new int[10];
	private static int archonOrder = 0;
	private static MapLocation rallyPoint = null;
	
	private static MapLocation currentDestination = null;
	
	private static MapLocation closestEnemyTurretLocation = null;
	
	private static int lastUnpairedScoutCount = 0;
	private static int nextUnpairedScoutCount = 0;
	
	public static void loop() throws GameActionException {
		rc.setIndicatorString(0, "73161402733dfa8b6c39a80ea5686a2e840e61d4");
		Debug.init("msg");
		FastMath.initRand(rc);
		//initArchons();
		
		nArchons = rc.getRobotCount();
		Debug.indicate("unpaired", 2, "nArchons = " + nArchons);
		Clock.yield();

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
		/*if (rc.getRoundNum() <= 100 && rallyPoint != null && here.distanceSquaredTo(rallyPoint) > 2) {
			if (rc.isCoreReady()) {
			    Nav.goToDirectSafely(rallyPoint);
			}
			return;
		}*/
		
		processSignals();
		
		if (rc.getRoundNum() >= 20) {
			MapEdges.detectAndBroadcastMapEdges(5); // visionRange = 5
		}
		
		if (rc.getRoundNum() % 50 == 25) {
			lastUnpairedScoutCount = nextUnpairedScoutCount;
			nextUnpairedScoutCount = 0;
		}
		
		Debug.indicate("unpaired", 0, "lastUnpairedScoutCount = " + lastUnpairedScoutCount);
		Debug.indicate("unpaired", 1, "nextUnpairedScoutCount = " + nextUnpairedScoutCount);
		
		//trySendAttackTarget();
		sendRadarInfo();
		
		trySendArchonLocationMessage();

		tryRepairAlly();

		if (rc.isCoreReady()) {
			Debug.indicate("turret", 0, "numEnemyTurrets = " + Radar.numEnemyTurrets);
			FastTurretInfo closestEnemyTurret = Radar.findClosestEnemyTurret();
			if (closestEnemyTurret != null) {
				closestEnemyTurretLocation = closestEnemyTurret.location;
			} else {
				closestEnemyTurretLocation = null;
			}
			Debug.indicate("turret", 1, "closest enemy turret = " + (closestEnemyTurret == null ? null : closestEnemyTurret.location));
			
			if (retreatIfNecessary()) {
				return;
			}
		}
		
		trySpawn();
		
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
	
	private static void sendRadarInfo() throws GameActionException {
		RobotInfo[] hostiles = rc.senseHostileRobots(here, mySensorRadiusSquared);
		Debug.indicate("radar", 0, "sendRaderInfo: hostiles.length = " + hostiles.length);
		if (hostiles.length == 0) return;
		
		int rangeSq = 9*mySensorRadiusSquared;
		
		if (hostiles.length <= 5) {
			Messages.sendRadarData(hostiles, rangeSq);
		} else {
			Messages.sendRadarData(Util.truncateArray(hostiles, 5), rangeSq);
		}
		
		for (RobotInfo hostile : hostiles) {
			if (hostile.type == RobotType.TURRET) {
				if (!Radar.turretIsKnown(hostile.ID, hostile.location)) {
					Radar.addEnemyTurret(hostile.ID, hostile.location);
					Messages.sendEnemyTurretWarning(hostile.ID, hostile.location, 9*mySensorRadiusSquared);
				}
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
	
	private static void trySpawn() throws GameActionException {
		if (!rc.isCoreReady()) return;

		/*RobotType spawnType = RobotType.SOLDIER;
		if (rc.getRoundNum() > 400) {
			if (spawnCount % 4 == 0) {
				spawnType = RobotType.TURRET;
			}
		}
		if (spawnCount % 10 == 0) {
			spawnType = RobotType.SCOUT;
		}*/
		
		RobotType spawnType;
		if (rc.getRoundNum() < 250) {
			if (spawnCount % 8 == 0) {
				spawnType = RobotType.SCOUT;
			} else {
				spawnType = RobotType.SOLDIER;			
			}
		} else {
			if (spawnCount % 2 == 0) {
				spawnType = RobotType.SOLDIER;
			} else {
				spawnType = RobotType.TURRET;
			}
			if (lastUnpairedScoutCount < 2 + 0.1 * rc.getRobotCount()) {
				spawnType = RobotType.SCOUT;
			}
		}
		
		if (!rc.hasBuildRequirements(spawnType)) return;

		Direction dir = Direction.values()[FastMath.rand256() % 8];
		for (int i = 0; i < 8; ++i) {
			if (rc.canBuild(dir, spawnType)) {
				rc.build(dir, spawnType);
				++spawnCount;
				if (spawnType == RobotType.SCOUT) {
					Messages.sendKnownMapEdges(2); // tell scout known map edges
					lastUnpairedScoutCount += nArchons;
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
				case Messages.CHANNEL_MAP_EDGES:
					Messages.processMapEdges(data);
					break;
				case Messages.CHANNEL_FOUND_PARTS:
					PartsLocation partsLoc = Messages.parsePartsLocation(data);
					Debug.indicate("parts", 0, "parts at " + partsLoc.location);
					considerDestination(partsLoc.location);
					break;
				case Messages.CHANNEL_FOUND_NEUTRAL:
					MapLocation neutralLoc = Messages.parseNeutralLocation(data);
					considerDestination(neutralLoc);
					break;
					
				case Messages.CHANNEL_ENEMY_TURRET_WARNING:
					Messages.processEnemyTurretWarning(data);
					break;
					
				case Messages.CHANNEL_UNPAIRED_SCOUT_REPORT:
					nextUnpairedScoutCount += 1;
					break;
					
				default:
				}
			} else {
				// simple signal with no message
			}
		}
		Debug.indicate("edges", 0, "MinX=" + MapEdges.minX + " MaxX=" + MapEdges.maxX + " MinY=" + MapEdges.minY + " MaxY=" + MapEdges.maxY);
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
			Nav.goToDirectSafelyAvoidingTurret(currentDestination, closestEnemyTurretLocation);
		}
	}
	
	private static void goToCenterOfMass() throws GameActionException {
		RobotInfo[] allies = rc.senseNearbyRobots(mySensorRadiusSquared, us);
		if (allies.length == 0) return;
		int avgX = 0;
		int avgY = 0;
		int N = 0;
		for (RobotInfo ally : allies) {
			if (ally.type == RobotType.SCOUT) continue;
			avgX += ally.location.x;
			avgY += ally.location.y;			
			N += 1;
		}
		if (N != 0) {
			avgX /= N;
			avgY /= N;
			Nav.goToDirectSafelyAvoidingTurret(new MapLocation(avgX, avgY), closestEnemyTurretLocation);
		}
	}
	
	private static boolean retreatIfNecessary() throws GameActionException {
		RobotInfo[] hostiles = rc.senseHostileRobots(here, mySensorRadiusSquared);
		boolean mustRetreat = false;
		MapLocation retreatTarget = here;
		for (RobotInfo hostile : hostiles) {
			RobotType hostileType = hostile.type;
			if (!hostileType.canAttack()) continue;			
			mustRetreat = true;
			retreatTarget = retreatTarget.add(hostile.location.directionTo(here));
		}
		if (closestEnemyTurretLocation != null) {
			if (here.distanceSquaredTo(closestEnemyTurretLocation) <= RobotType.TURRET.attackRadiusSquared) {
				mustRetreat = true;
				retreatTarget = retreatTarget.add(closestEnemyTurretLocation.directionTo(here));
			}
		}
		if (mustRetreat) {
			if (!here.equals(retreatTarget)) {
				Direction retreatDir = here.directionTo(retreatTarget);
				Nav.tryHardMoveInDirection(retreatDir);
				return true;
			}
		}
		return false;
	}
}
