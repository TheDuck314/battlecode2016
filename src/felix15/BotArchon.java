package felix15;

import battlecode.common.*;

public class BotArchon extends Globals {
	private static int spawnCount = 0;

	private static int lastArchonLocationMessageRound = 0;
	
	private static MapLocation currentDestination = null;
	
	private static MapLocation closestEnemyTurretLocation = null;
	
	private static int lastUnpairedScoutCount = 0;
	private static int nextUnpairedScoutCount = 0;
	
	private static MapLocationHashSet knownZombieDens = new MapLocationHashSet();
	private static int lastGlobalZombieDenBroadcastRound = 0;
	private static int GLOBAL_ZOMBIE_DEN_BROADCAST_INTERVAL = 100;
	private static MapLocation lastDenTarget = null;
	
	private static MapLocation[] initialArchonLocations;
	private static int nArchons;
	private static MapLocation startingLocation;
	
	public static void loop() throws GameActionException {
//		rc.setIndicatorString(0, "41bd9daf1997dbe55d320f76267c8be1064eab87");
		Debug.init("dens");
		FastMath.initRand(rc);
		
		initArchons();
		
		lastGlobalZombieDenBroadcastRound = (int)(FastMath.rand256() * GLOBAL_ZOMBIE_DEN_BROADCAST_INTERVAL / 256.0);

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
		initialArchonLocations = rc.getInitialArchonLocations(us);
		nArchons = initialArchonLocations.length;
		
		// choose the "starting location" to be the initial archon
		// location which is closest to the overall center of mass 
		// of the archons. Maybe we should also give a bonus for 
		// being far away from the enemy archons?
		int archonCenterX = 0;
		int archonCenterY = 0;
		for (MapLocation archonLocation : initialArchonLocations) {
			archonCenterX += archonLocation.x;
			archonCenterY += archonLocation.y;
		}
		archonCenterX /= nArchons;
		archonCenterY /= nArchons;
	    /*MapLocation archonCenter = new MapLocation(archonCenterX, archonCenterY);
		startingLocation = initialArchonLocations[0];
		int bestDistSq = startingLocation.distanceSquaredTo(archonCenter);
		for (int i = 1; i < nArchons; ++i) {
			int distSq = initialArchonLocations[i].distanceSquaredTo(archonCenter);
			if (distSq < bestDistSq) {
				bestDistSq = distSq;
				startingLocation = initialArchonLocations[i];
			}
		}*/
		startingLocation = new MapLocation(archonCenterX, archonCenterY);
	}
	
	private static void turn() throws GameActionException {
		Debug.indicate("turn", 0, "turn start; ");
		
		processSignals();

		Debug.indicateAppend("turn", 0, "after processSignals; ");

		if (rc.getRoundNum() >= 40) {
			MapEdges.detectAndBroadcastMapEdges(5); // visionRange = 5
		}

		Debug.indicateAppend("turn", 0, "after MapEdges; ");

		if (rc.getRoundNum() % Globals.checkUnpairedScoutInterval == Globals.checkUnpairedScoutInterval - 1) {
			lastUnpairedScoutCount = nextUnpairedScoutCount;
			nextUnpairedScoutCount = 0;
		}
		
		sendRadarInfo();
		Debug.indicateAppend("turn", 0, "after sendRadarInfo; ");
		
		trySendArchonLocationMessage();

		Debug.indicateAppend("turn", 0, "after trySALM; ");

		if (rc.getRoundNum() >= 40) {
			trySendGlobalZombieDenBroadcast();
		}

		Debug.indicateAppend("turn", 0, "after trySGZDB; ");

		tryRepairAlly();

		Debug.indicateAppend("turn", 0, "after tryRA; ");

		if (rc.isCoreReady()) {
			Radar.removeDistantEnemyTurrets(9 * RobotType.SCOUT.sensorRadiusSquared);
			
			FastTurretInfo closestEnemyTurret = Radar.findClosestEnemyTurret();
			if (closestEnemyTurret != null) {
				closestEnemyTurretLocation = closestEnemyTurret.location;
			} else {
				closestEnemyTurretLocation = null;
			}
			
			Debug.indicateAppend("turn", 0, "before retreat; ");
			if (retreatIfNecessary()) {
				return;
			}
			Debug.indicateAppend("turn", 0, "after retreat; ");
		}
		
		Debug.indicateAppend("turn", 0, "before trySpawn; ");
		trySpawn();
		Debug.indicateAppend("turn", 0, "after trySpawn; ");
		
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
	
	private static void trySendGlobalZombieDenBroadcast() throws GameActionException {		
		Debug.indicate("dens", 2, "known dens: ");
		for (int i = 0; i < knownZombieDens.size; ++i) {
			Debug.indicateAppend("dens", 2, knownZombieDens.locations[i].toString() + "; ");
			Debug.indicateLine("dens", here, knownZombieDens.locations[i], 0, 0, 255);
		}
		if (lastDenTarget != null) {
			Debug.indicateLine("dens", here, lastDenTarget, 255, 0, 0);
		}
		
		// send global broadcasts at a certain interval
		if (lastDenTarget != null && 
				rc.getRoundNum() - lastGlobalZombieDenBroadcastRound < GLOBAL_ZOMBIE_DEN_BROADCAST_INTERVAL) {
			return;
		}
		
		// don't send global broadcasts when in danger
		if (rc.senseHostileRobots(here, myAttackRadiusSquared).length > 0) {
			return;
		}
		
		MapLocation closestDen = knownZombieDens.findClosestMemberToLocation(startingLocation);
		if (closestDen != null) {
			Debug.indicate("dens", 0, "sending command to attack den at " + closestDen);
			Messages.sendDenAttackCommand(closestDen, MapEdges.maxBroadcastDistSq());
			lastGlobalZombieDenBroadcastRound = rc.getRoundNum();
			lastDenTarget = closestDen;
		}
	}
	
	private static void sendRadarInfo() throws GameActionException {
		RobotInfo[] hostiles = rc.senseHostileRobots(here, mySensorRadiusSquared);
//		Debug.indicate("radar", 0, "sendRaderInfo: hostiles.length = " + hostiles.length);
		if (hostiles.length == 0) return;
		
		int rangeSq = 4*mySensorRadiusSquared;
		
		RobotInfo[] hostilesToSend = new RobotInfo[5];
		int numberHostilesToSend = 0;
		for (RobotInfo h: hostiles) {
			if (Radar.addEnemyToCache(h)) {
				hostilesToSend[numberHostilesToSend] = h;
				numberHostilesToSend += 1;
				if (numberHostilesToSend == 5) {
					break;
				}
			}
		}
		if (numberHostilesToSend != 0) {
			Messages.sendRadarData(hostilesToSend, numberHostilesToSend, rangeSq);
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
			Messages.sendArchonLocation(here, 30*mySensorRadiusSquared);
			lastArchonLocationMessageRound = rc.getRoundNum();
//			Debug.indicate("heal", 0, "sent archon location");
		}
	}
	
	private static void trySpawn() throws GameActionException {
		Debug.indicate("turn", 1, "trySpawn start; ");
		if (!rc.isCoreReady()) return;

		RobotType spawnType = RobotType.SOLDIER;
		if (spawnCount % 20 == 0) {
			spawnType = RobotType.SCOUT;
		}
		if (spawnCount % 13 == 0 && spawnCount > 0) {
			spawnType = RobotType.VIPER;
		}
		
		if (!rc.hasBuildRequirements(spawnType)) {
			Debug.indicateAppend("turn", 1, "don't have build requirements");
			return;
		}

		Debug.indicateAppend("turn", 1, "going to try to build");
		Direction dir = Direction.values()[FastMath.rand256() % 8];
		for (int i = 0; i < 8; ++i) {
			if (rc.canBuild(dir, spawnType)) {
				rc.build(dir, spawnType);
				++spawnCount;
				if (spawnType == RobotType.SCOUT) {
					sendInfoToBabyScout();
					lastUnpairedScoutCount += nArchons;
				}
				return;
			}
			dir = dir.rotateRight();
		}
	}
	
	private static void sendInfoToBabyScout() throws GameActionException {
		// tell scout known map edges
		Messages.sendKnownMapEdges(2); 
		
		// tell scout known zombie dens
		for (int i = 0; i < knownZombieDens.size; i += 3) {
			MapLocation[] denList = new MapLocation[3];
			int listLen = 0;
			for (int j = 0; j < 3 && i+j < knownZombieDens.size; j += 1) {
				denList[j] = knownZombieDens.locations[i+j];				
				listLen += 1;
			}
			Messages.sendUpToThreeZombieDens(denList, listLen, 2);
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
		if (!loc.equals(here)) {
			if (currentDestination == null || here.distanceSquaredTo(loc) < here.distanceSquaredTo(currentDestination)) {
				currentDestination = loc;
			}
		}
	}
	
	private static void processSignals() throws GameActionException {
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
//					Debug.indicate("parts", 0, "parts at " + partsLoc.location);
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
					
				case Messages.CHANNEL_ZOMBIE_DEN:
					MapLocation denLoc = Messages.parseZombieDenLocation(data);
					if (Messages.parseZombieDenWasDestroyed(data)) {
						if (knownZombieDens.remove(denLoc)) {
//						    Debug.indicate("dens", 1, "heard that the den at " + denLoc + " was destroyed");
						    if (denLoc.equals(lastDenTarget)) {
//							    Debug.indicate("dens", 2, "that was our lastDenTarget!");
						    	lastDenTarget = null;
						    }
						} else {
//						    Debug.indicate("dens", 1, "heard that den at " + denLoc + " was destroyed, but it wasn't in my list");							
						}
					} else {
						if (knownZombieDens.add(denLoc)) {
//						    Debug.indicate("dens", 1, "heard about a new den at " + denLoc);
						} else {
//							Debug.indicate("dense", 1, "heard about a new den at " + denLoc + ", but I already knew about it");
						}
					}
					break;
					
				default:
				}
			} else {
				// simple signal with no message
				// for now these are only sent by soldiers who have just killed
				// a zombie den. Check to see if we know of a zombie den within
				// the soldier attack radius of the message origin.
				MapLocation signalOrigin = sig.getLocation();
				MapLocation killedDen = knownZombieDens.findClosestMemberToLocation(signalOrigin);
//				Debug.indicate("kill", 0, "got kill message. signalOrigin = " + signalOrigin + ", killedDen = " + killedDen);
				if (killedDen != null 
						&& killedDen.distanceSquaredTo(signalOrigin) <= RobotType.SOLDIER.attackRadiusSquared) {
					knownZombieDens.remove(killedDen);
					if (killedDen.equals(lastDenTarget)) {
						lastDenTarget = null;
					}
				}
			}
		}
//		Debug.indicate("edges", 0, "MinX=" + MapEdges.minX + " MaxX=" + MapEdges.maxX + " MinY=" + MapEdges.minY + " MaxY=" + MapEdges.maxY);
	}
	
	private static MapLocation chooseBestPartsLocation(MapLocation[] partLocations) {
		MapLocation bestLoc = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (MapLocation loc : partLocations) {
			double score = 0.25 * rc.senseParts(loc) 
					- 20 * FastMath.floorSqrt(here.distanceSquaredTo(loc))
					- 10 * Util.estimateRubbleClearTurns(rc.senseRubble(loc));
			//System.out.println("loc = " + loc + ", score = " + (int)score);
			if (score > bestScore) {
				bestScore = score;
				bestLoc = loc;
			}
		}
		//System.out.println("best = " + bestLoc + ", score = " + bestScore);
		return bestLoc;
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
	
		MapLocation[] partLocs = rc.sensePartLocations(mySensorRadiusSquared);
		if (partLocs.length > 0) {
			considerDestination(chooseBestPartsLocation(partLocs));
		}
			
		RobotInfo[] nearbyNeutrals = rc.senseNearbyRobots(mySensorRadiusSquared, Team.NEUTRAL);
		for (RobotInfo neutral : nearbyNeutrals) {
			if (!neutral.location.equals(here)) {
				considerDestination(neutral.location);
			}
		}	
	}
	
	private static void goToDestination() throws GameActionException {
//		Debug.indicate("parts", 1, "destination = " + currentDestination);
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
			if (ally.type == RobotType.SOLDIER && ally.health < ally.maxHealth / 2) continue;
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
				return Nav.tryHardMoveInDirection(retreatDir);
			}
		}
		return false;
	}
}
