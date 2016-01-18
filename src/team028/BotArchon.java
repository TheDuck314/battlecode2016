package team028;

import battlecode.common.*;

enum DestinationType {
	PARTS, PARTREGION, NEUTRAL
}

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
	private static DestinationType currentDestinationType;
	
	private static MapLocation closestEnemyTurretLocation = null;
	
	private static int lastUnpairedScoutCount = 0;
	private static int nextUnpairedScoutCount = 0;
	
	private static MapLocationHashSet knownZombieDens = new MapLocationHashSet();
	private static int lastGlobalZombieDenBroadcastRound = 0;
	private static int GLOBAL_ZOMBIE_DEN_BROADCAST_INTERVAL = 100;
	private static MapLocation lastDenTarget = null;
	
	private static int lastFleeZombiesRound = -99999;
	private static int lastFleeOtherTeamRound = -99999;
	
	public static void loop() throws GameActionException {
		rc.setIndicatorString(0, "0155e2feecf4e92de5abf6ee7d2a3d5405fabf1f");
		Debug.init("convert");
		FastMath.initRand(rc);
		
		nArchons = rc.getRobotCount();
		lastGlobalZombieDenBroadcastRound = (int)(FastMath.rand256() * GLOBAL_ZOMBIE_DEN_BROADCAST_INTERVAL / 256.0);
		startingLocation = here;
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
		processSignals();
		
		/*for (int i = 0; i < PartMemory.MEMORY_LENGTH; ++i) {
			if (PartMemory.regions[i] != null) {
				Debug.indicateLine("regions", here, PartMemory.regions[i].centralLocation, 0, 0, 255);
				Debug.indicateDot("regions", PartMemory.regions[i].centralLocation, 0, 0, 255);
			}
		}*/
		
		if (rc.getRoundNum() >= 40) {
			MapEdges.detectAndBroadcastMapEdges(5); // visionRange = 5
		}

		if (rc.getRoundNum() % Globals.checkUnpairedScoutInterval == Globals.checkUnpairedScoutInterval - 1) {
			lastUnpairedScoutCount = nextUnpairedScoutCount;
			nextUnpairedScoutCount = 0;
		}

		visibleHostiles = rc.senseHostileRobots(here, mySensorRadiusSquared);
		visibleAllies = rc.senseNearbyRobots(mySensorRadiusSquared, us);

		tryRepairAlly();
		tryConvertNeutrals();		
		
		
		sendRadarInfo();		

		if (rc.isCoreReady()) {
			Radar.removeDistantEnemyTurrets(9 * RobotType.SCOUT.sensorRadiusSquared);
			
			FastTurretInfo closestEnemyTurret = Radar.findClosestEnemyTurret();
			if (closestEnemyTurret != null) {
				closestEnemyTurretLocation = closestEnemyTurret.location;
			} else {
				closestEnemyTurretLocation = null;
			}
			
			if (fleeOverwhelmingEnemies()) {
				return;
			}
			
			if (retreatIfNecessary()) {
				return;
			}
		}
		
		trySendArchonLocationMessage();

		if (rc.getRoundNum() >= 40) {
			trySendGlobalZombieDenBroadcast();
		}
		
		trySpawn();	
		
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
		if (visibleHostiles.length > 0) {
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
//		Debug.indicate("radar", 0, "sendRaderInfo: hostiles.length = " + hostiles.length);
		if (visibleHostiles.length == 0) return;
		
		int rangeSq = 4*mySensorRadiusSquared;
		
		RobotInfo[] hostilesToSend = new RobotInfo[5];
		int numberHostilesToSend = 0;
		for (RobotInfo h: visibleHostiles) {
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
		
		for (RobotInfo hostile : visibleHostiles) {
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
			if (spawnCount % 15 == 14) {
				spawnType = RobotType.VIPER;
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
			if (spawnCount % 15 == 14) {
				spawnType = RobotType.VIPER;
			}
		}
		
		if (rc.getRoundNum() - lastFleeZombiesRound < 100) {
			if (spawnType == RobotType.VIPER || spawnType == RobotType.TURRET
					|| spawnType == RobotType.SCOUT) {
				Debug.indicate("convert", 0, "converted " + spawnType + " spawn into soldier");
				//System.out.println("converted " + spawnType + " spawn into soldier");
				spawnType = RobotType.SOLDIER;
			}
		} else if (rc.getRoundNum() - lastFleeOtherTeamRound < 100) {
			if (spawnType == RobotType.TURRET) {
				Debug.indicate("convert", 0, "converted " + spawnType + " spawn into soldier");
				//System.out.println("converted " + spawnType + " spawn into soldier");
				spawnType = RobotType.SOLDIER;
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
	
	private static void considerDestination(MapLocation loc, DestinationType type) {
		if (!loc.equals(here)) {
			if (currentDestination == null || here.distanceSquaredTo(loc) < here.distanceSquaredTo(currentDestination)) {
				currentDestination = loc;
				currentDestinationType = type;
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
					considerDestination(partsLoc.location, DestinationType.PARTS);
					break;
				case Messages.CHANNEL_PART_REGIONS:
					PartRegion region = Messages.parsePartRegion(data);
					Debug.indicate("regions", 0, "got region: parts=" + region.totalParts + ", loc=" + region.centralLocation + ", avgTurnsToUncover=" + region.avgTurnsToUncover);;
					PartMemory.add(region);
					break;
					
				case Messages.CHANNEL_FOUND_NEUTRAL:
					MapLocation neutralLoc = Messages.parseNeutralLocation(data);
					considerDestination(neutralLoc, DestinationType.NEUTRAL);
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
		if (partLocs.length > 0) {
			MapLocation bestPartLoc = null;
			double bestScore = Double.NEGATIVE_INFINITY;
			for (MapLocation partLoc : partLocs) {
				double numParts = rc.senseParts(partLoc);
				double score = numParts
						- 20 * FastMath.floorSqrt(here.distanceSquaredTo(partLoc))
						- 10 * Util.estimateRubbleClearTurns(rc.senseRubble(partLoc));
				if (rc.senseRobotAtLocation(partLoc) != null) {
					score -= 100;
				}
				if (score > bestScore) {
					bestScore = score;
					bestPartLoc = partLoc;
				}
			}			
			if (currentDestinationType == DestinationType.PARTS) {
				// we just found the best part destination. so if the destination is
				// already a parts destination, just set the current destination to
				// the best one
				currentDestination = bestPartLoc;
			} else {
				// otherwise just switch to a part destination if it's closer than
				// our current destination
				considerDestination(bestPartLoc, DestinationType.PARTS);				
			}
		}
		
		for (int i = 0; i < PartMemory.MEMORY_LENGTH; ++i) {
			PartRegion region = PartMemory.regions[i];
			if (region != null) {
				if (here.distanceSquaredTo(region.centralLocation) <= 13) {
					PartMemory.remove(i);
				} else {
					Debug.indicate("regions", 1, "considering " + region.centralLocation);
					considerDestination(region.centralLocation, DestinationType.PARTREGION);
				}
			}
		}
			
		RobotInfo[] nearbyNeutrals = rc.senseNearbyRobots(mySensorRadiusSquared, Team.NEUTRAL);
		for (RobotInfo neutral : nearbyNeutrals) {
			if (!neutral.location.equals(here)) {
				considerDestination(neutral.location, DestinationType.NEUTRAL);
			}
		}	
		
		Debug.indicate("regions", 2, "destination = " + currentDestination);
		if (currentDestination != null) {
			Debug.indicateLine("regions", here, currentDestination, 255, 0, 0);
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
		boolean mustRetreat = false;
		MapLocation retreatTarget = here;
		for (RobotInfo hostile : visibleHostiles) {
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
	
	private static boolean fleeOverwhelmingEnemies() throws GameActionException {
		int numDangerousHostiles = 0;
		int hostileCenterX = 0;
		int hostileCenterY = 0;
		int minHostileDistSq = 999999;
		boolean fastZombieIsAdjacent = false;
		int numZombies = 0;
		for (RobotInfo hostile : visibleHostiles) {
			if (hostile.type.canAttack()) {
				numDangerousHostiles += 1;
				hostileCenterX += hostile.location.x;
				hostileCenterY += hostile.location.y;
				int distSq = here.distanceSquaredTo(hostile.location);
				if (distSq < minHostileDistSq) {
					minHostileDistSq = distSq;
				}
				if (hostile.type == RobotType.FASTZOMBIE && distSq <= 2) {
					fastZombieIsAdjacent = true;
				}
				if (hostile.team == Team.ZOMBIE) {
					numZombies += 1;
				}
			}
		}
		if (numDangerousHostiles == 0) {
			return false;
		}
		hostileCenterX /= numDangerousHostiles;
		hostileCenterY /= numDangerousHostiles;
		MapLocation hostileCenter = new MapLocation(hostileCenterX, hostileCenterY);

		int numHelpfulAllies = 0;
		int minAllyDistSq = 999999;
		for (RobotInfo ally : visibleAllies) {
			if (ally.type.canAttack()) {
				numHelpfulAllies += 1;
				int distSq = here.distanceSquaredTo(ally.location);
				if (distSq < minAllyDistSq) {
					minAllyDistSq = distSq;
				}
			}
		}
		
		Debug.indicate("retreat", 0, "numDangerousHostiles = " + numDangerousHostiles + ", numHelpfulAllies = " + numHelpfulAllies);
		Debug.indicateAppend("retreat", 0, "; minY = " + MapEdges.minY + ", maxY = " + MapEdges.maxY);
		
		if (numHelpfulAllies >= numDangerousHostiles && !fastZombieIsAdjacent) {
			return false; // we are hopefully safe
		}			
		
		if (numZombies * 2 >= numDangerousHostiles) {
			lastFleeZombiesRound = rc.getRoundNum();
			Debug.indicate("convert", 1, "lastFleeZombiesRound = " + lastFleeZombiesRound);
		} else {
			lastFleeOtherTeamRound = rc.getRoundNum();
		}
		
		Debug.indicate("retreat", 1, "must flee overwhelming enemies!!!!! ");
		
		MapLocation closestCorner = MapEdges.getClosestKnownMapCorner();
		Debug.indicateAppend("retreat", 1, "; closestCorner = " + closestCorner);
		boolean[] directionIsDiscouraged = new boolean[8];
		if ((closestCorner != null) && (here.distanceSquaredTo(closestCorner) <= 100)
				&& !here.equals(closestCorner)) {
			// if near a corner, forbid retreating in directions toward that corner
			int toCornerOrdinal = here.directionTo(closestCorner).ordinal();
			Debug.indicateAppend("retreat", 1, "; toCorner = " + here.directionTo(closestCorner)); 
			directionIsDiscouraged[toCornerOrdinal] = true;
			directionIsDiscouraged[(toCornerOrdinal+1)%8] = true;
			directionIsDiscouraged[(toCornerOrdinal+7)%8] = true;
		}
		
		
		Direction bestRetreatDir = null;
		int bestDistSq = Integer.MIN_VALUE;
		Direction[] allDirs = Direction.values();
		for (int i = 0; i < 8; ++i) {
			Direction dir = allDirs[i];				
			if (!rc.canMove(dir)) continue;			
			int distSq = here.add(dir).distanceSquaredTo(hostileCenter);
			if (directionIsDiscouraged[i]) distSq -= 10000; // punish discouraged directions
			if (distSq > bestDistSq) {
				bestDistSq = distSq;
				bestRetreatDir = dir;
			}
		}
		Debug.indicateAppend("retreat", 1, "; bestRetreatDir = " + bestRetreatDir); 
		
		if (bestRetreatDir == null) {
			return false;
		}
		
		// consider building a guard to protect us
		if (rc.getTeamParts() >= RobotType.GUARD.partCost) {
			if (numHelpfulAllies == 0 || minHostileDistSq < minAllyDistSq || fastZombieIsAdjacent) {
				// try to build a guard to defend us.
				// but we are not allow to build a guard that blocks the
				// best retreat direction		
				Direction oppositeDir = bestRetreatDir.opposite();			
				Direction[] buildDirs = { oppositeDir, oppositeDir.rotateLeft(), oppositeDir.rotateRight(),
						oppositeDir.rotateLeft().rotateLeft(), oppositeDir.rotateRight().rotateRight() };
				for (Direction buildDir : buildDirs) {
					if (rc.canBuild(buildDir, RobotType.GUARD)) {
						rc.build(buildDir, RobotType.GUARD);
						Debug.indicate("retreat", 2, "building guard!"); 
						return true;
					}
				}
			}
		}

		rc.move(bestRetreatDir);
		Debug.indicate("retreat", 2, "moving!"); 
		return true;
	}
}
