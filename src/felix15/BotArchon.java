package felix15;

import battlecode.common.*;

public class BotArchon extends Globals {
	private static int spawnCount = 0;

	private static int lastArchonLocationMessageRound = 0;

	private static int nArchons = 0;
	private static MapLocation[] archonsLoc = new MapLocation[10];
	private static int[] archonsId = new int[10];
	private static int archonOrder = 0;
	private static MapLocation rallyPoint = null;
	
	private static MapLocation startingLocation = null;
	
	private static MapLocation currentDestination = null;
	
	private static MapLocation closestEnemyTurretLocation = null;
	
	private static int lastUnpairedScoutCount = 0;
	private static int nextUnpairedScoutCount = 0;
	
	private static MapLocationHashSet knownZombieDens = new MapLocationHashSet();
	private static int lastGlobalZombieDenBroadcastRound = 0;
	private static int GLOBAL_ZOMBIE_DEN_BROADCAST_INTERVAL = 100;
	private static MapLocation lastDenTarget = null;
	
	public static void loop() throws GameActionException {
		rc.setIndicatorString(0, "41bd9daf1997dbe55d320f76267c8be1064eab87");
//		Debug.init("kill");
		FastMath.initRand(rc);
//		for (int i = 0; i < 1000000; ++i) {
//			System.out.println(FastMath.rand256());
//		}
		
//		initArchons();
		
		nArchons = rc.getRobotCount();
		lastGlobalZombieDenBroadcastRound = (int)(FastMath.rand256() * GLOBAL_ZOMBIE_DEN_BROADCAST_INTERVAL / 256.0);
		startingLocation = here;
//		Debug.indicate("unpaired", 2, "nArchons = " + nArchons);
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
//		Debug.indicateDot("rally", rallyPoint, 255, 0, 0);
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
		
		if (rc.getRoundNum() >= 40) {
			MapEdges.detectAndBroadcastMapEdges(5); // visionRange = 5
		}
		
		if (rc.getRoundNum() % Globals.checkUnpairedScoutInterval == Globals.checkUnpairedScoutInterval - 1) {
			lastUnpairedScoutCount = nextUnpairedScoutCount;
			nextUnpairedScoutCount = 0;
		}
		
//		Debug.indicate("unpaired", 0, "lastUnpairedScoutCount = " + lastUnpairedScoutCount);
//		Debug.indicate("unpaired", 1, "nextUnpairedScoutCount = " + nextUnpairedScoutCount);
		
		//trySendAttackTarget();
		sendRadarInfo();
		
		trySendArchonLocationMessage();

		if (rc.getRoundNum() >= 40) {
			trySendGlobalZombieDenBroadcast();
		}

		tryRepairAlly();

		if (rc.isCoreReady()) {
			Radar.removeDistantEnemyTurrets(9 * RobotType.SCOUT.sensorRadiusSquared);
			
			FastTurretInfo closestEnemyTurret = Radar.findClosestEnemyTurret();
			if (closestEnemyTurret != null) {
				closestEnemyTurretLocation = closestEnemyTurret.location;
			} else {
				closestEnemyTurretLocation = null;
			}
			
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
	
	private static void trySendGlobalZombieDenBroadcast() throws GameActionException {		
//		Debug.indicate("dens", 2, "");
		for (int i = 0; i < knownZombieDens.size; ++i) {
			//Debug.indicateAppend("dens", 2, ", " + knownZombieDens.locations[i]);
//			Debug.indicateLine("dens", here, knownZombieDens.locations[i], 0, 0, 255);
		}
		if (lastDenTarget != null) {
//			Debug.indicateLine("dens", here, lastDenTarget, 255, 0, 0);
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
//			Debug.indicate("dens", 0, "sending command to attack den at " + closestDen);
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
			Messages.sendArchonLocation(here, 900);
			lastArchonLocationMessageRound = rc.getRoundNum();
//			Debug.indicate("heal", 0, "sent archon location");
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
		
		RobotType spawnType = RobotType.SOLDIER;
		if (rc.getRoundNum() < 250) {
			if (spawnCount % 8 == 0) {
				spawnType = RobotType.SCOUT;
			} else {
				spawnType = RobotType.SOLDIER;			
			}
		} else {
			if (lastUnpairedScoutCount < 2) {
				switch (spawnCount % 4) {
				case 0:
					spawnType = RobotType.SOLDIER;
					break;
				case 2:
					spawnType = RobotType.TURRET;
					break;
				default:
					spawnType = RobotType.SCOUT;
				}
			} else if (lastUnpairedScoutCount < 5) {
				switch (spawnCount % 3) {
				case 0:
					spawnType = RobotType.SOLDIER;
					break;
				case 1:
					spawnType = RobotType.TURRET;
					break;
				default:
					spawnType = RobotType.SCOUT;
				}
			} else {
				switch (spawnCount % 2) {
				case 0:
					spawnType = RobotType.SOLDIER;
					break;
				case 1:
					spawnType = RobotType.TURRET;
					break;
				default:
					spawnType = RobotType.SCOUT;
				}
			}
			if (spawnCount % 15 == 0) {
				spawnType = RobotType.VIPER;
			}
		}
		
		if (!rc.hasBuildRequirements(spawnType)) return;

		Direction dir = Direction.values()[FastMath.rand256() % 8];
		for (int i = 0; i < 8; ++i) {
			if (rc.canBuild(dir, spawnType)) {
				rc.build(dir, spawnType);
				++spawnCount;
				if (spawnType == RobotType.SCOUT) {
					sendInfoToBabyScout();
					lastUnpairedScoutCount += nArchons;
				}
			}
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
		for (MapLocation partLoc : partLocs) {
			if (!partLoc.equals(here)) {
				considerDestination(partLoc);
			}
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
