package armageddon_26;

import battlecode.common.*;

enum DestinationType {
	PARTS, PARTREGION, NEUTRAL, NEUTRALARCHON
}

public class BotArchon extends Globals {
	private static int spawnCount = 0;

	private static int lastArchonLocationMessageRound = 0;
	private static int lastGlobalArchonLocationMessageRound = 0;

	private static MapLocation startingLocation = null;
	
	private static MapLocation currentDestination = null;
	private static DestinationType currentDestinationType;
	
	private static MapLocationHashSet knownNeutralArchons = new MapLocationHashSet();
	
	private static int lastUnpairedScoutCount = 0;
	private static int nextUnpairedScoutCount = 0;
	
	private static MapLocationHashSet knownZombieDens = new MapLocationHashSet();
	private static int lastGlobalZombieDenBroadcastRound = 0;
	private static int GLOBAL_ZOMBIE_DEN_BROADCAST_INTERVAL = 100;
	private static MapLocation lastDenTarget = null;
	
	private static int lastFleeZombiesRound = -99999;
	private static int lastFleeOtherTeamRound = -99999;
	
	private static int scheduledEducationRound = 999999;
	private static RobotType scheduledEducationType = null;

	//private static boolean pullMode = false;
	
	public static void loop() throws GameActionException {
//		Debug.init("robotinfo");
		
		rc.setIndicatorString(0, "2b2f762a5f7c5c4647f846268c52e396370cdffc");
		
		FastMath.initRand(rc);
		
		// nArchons = rc.getRobotCount();
		lastGlobalZombieDenBroadcastRound = (int)(FastMath.rand256() * GLOBAL_ZOMBIE_DEN_BROADCAST_INTERVAL / 256.0);
		startingLocation = here;
		// Clock.yield();

		while (true) {
			int startTurn = rc.getRoundNum();
			try {
				Globals.update();
			    turn();
//				Radar.indicateEnemyTurretLocation(0, 200, 200);
			} catch (Exception e) {
				e.printStackTrace();
			}
			int endTurn = rc.getRoundNum();
			if (startTurn != endTurn) {
				System.out.println("OVER BYTECODE LIMIT");
			}
			Clock.yield();
		}
	}
	
//	private static void initArchons() throws GameActionException {
//		Globals.update();
//		archonsLoc[nArchons] = here;
//		archonsId[nArchons] = myID;
//		nArchons += 1;
//		Messages.sendArchonLocation(here, MapEdges.maxBroadcastDistSq());
//		Clock.yield();
//		Signal[] signals = rc.emptySignalQueue();
//		for (Signal sig : signals) {
//			if (sig.getTeam() != us) continue;
//			int[] data = sig.getMessage();
//			if (data != null) {
//				if ((data[0] & Messages.CHANNEL_MASK) == Messages.CHANNEL_ARCHON_LOCATION) {
//					archonsLoc[nArchons] = sig.getLocation();
//					archonsId[nArchons] = sig.getID();
//					nArchons += 1;
//				}
//			}
//		}
//		rallyPoint = here;
//		for (int i = 1; i < nArchons; ++i) {
//			rallyPoint = FastMath.addVec(rallyPoint, archonsLoc[i]);
//		}
//		rallyPoint = FastMath.multiplyVec(1.0/(double)nArchons, rallyPoint);
//		rallyPoint = rallyPoint.add(rallyPoint.directionTo(here), 0);
////		Debug.indicateDot("rally", rallyPoint, 255, 0, 0);
//		for (int i = 0; i < nArchons; ++i) {
//			if (archonsId[i] < myID) {
//				archonOrder += 1;
//			}
//		}
//	}
	
	private static void turn() throws GameActionException {
		processSignals();
		
		if (rc.getRoundNum() >= scheduledEducationRound) {
			Radar.updateClosestEnemyTurretInfo();
			educateBaby(scheduledEducationType);
//			Debug.indicate("education", 0, "educated a " + scheduledEducationType + " as scheduled");
			scheduledEducationRound = 999999;
		} else if (rc.isCoreReady()) {
			Radar.updateClosestEnemyTurretInfo();
		}
		
		if (rc.getRoundNum() >= 40) {
			MapEdges.detectAndBroadcastMapEdges(5); // visionRange = 5
		}

		if (rc.getRoundNum() % Globals.checkUnpairedScoutInterval == Globals.checkUnpairedScoutInterval - 1) {
			lastUnpairedScoutCount = nextUnpairedScoutCount;
			nextUnpairedScoutCount = 0;
//			Debug.indicate("unpaired", 2, "unpaired scout ids: ");
		}
		
//		Debug.indicate("unpaired", 0, "lastUnpairedScoutCount = " + lastUnpairedScoutCount);
//		Debug.indicate("unpaired", 1, "nextUnpairedScoutCount = " + nextUnpairedScoutCount);

		visibleHostiles = rc.senseHostileRobots(here, mySensorRadiusSquared);
		visibleAllies = rc.senseNearbyRobots(mySensorRadiusSquared, us);

		tryRepairAlly();
		tryConvertNeutrals();		
		
		boolean isSendingRadar = true;
		if (Globals.roundNum - lastFleeOtherTeamRound < 10 || Globals.roundNum - lastFleeZombiesRound < 10 ) {
			if (Globals.roundNum % 5 != 0) {
				isSendingRadar = false;
			}
		}
		if (isSendingRadar) {
			sendRadarInfo();
		}
		
//		Radar.indicateEnemyArchonLocation(0, 100, 100);

		if (rc.isCoreReady()) {
//			Radar.removeDistantEnemyTurrets(9 * RobotType.SCOUT.sensorRadiusSquared);
			//Radar.removeOldEnemyTurrets(Radar.TURRET_MEMORY_ROUNDS);
			
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
			Messages.sendDenAttackCommand(closestDen, MapEdges.maxRangeSq);
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
//				if (!Radar.turretIsKnown(hostile.ID, hostile.location)) {
//					Radar.addEnemyTurret(hostile.ID, hostile.location);
//					Messages.sendEnemyTurretWarning(hostile.ID, hostile.location, 9*mySensorRadiusSquared);
//				}
//				boolean isNewID = Radar.bigRobotInfoById[hostile.ID] == null;
				BigRobotInfo bri = Radar.addRobot(hostile.ID, hostile.type, hostile.team, hostile.location, Globals.roundNum);
				if (bri != null) {
					Messages.sendRobotLocation(bri, Globals.broadCastRangeSqWhenSeenByArchon);
//					Debug.indicate("turret", 0, "sent turret discover message");
				}
			}
		}
	}
	
	private static void trySendArchonLocationMessage() throws GameActionException {
		if (lastArchonLocationMessageRound < rc.getRoundNum() - 60) {
			int rangeSq = 900;
			if (lastGlobalArchonLocationMessageRound < rc.getRoundNum() - 150
					&& visibleHostiles.length == 0
					&& rc.getTeamParts() < 50) {
				rangeSq = MapEdges.maxBroadcastDistSq();
				lastGlobalArchonLocationMessageRound = rc.getRoundNum();
			}
			Messages.sendArchonLocation(here, rangeSq);
//			Debug.indicate("archonloc", 0, "sending archon location message with rangesq = " + rangeSq);
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
			// decide how often to build vipers
			if (rc.getRoundNum() > 1000 && rc.getRobotCount() <= 20) {
				// if we are losing, build a lot of vipers to hold off
				// pursuers and to make the game more random
				if (spawnCount % 4 == 0) {
					spawnType = RobotType.VIPER;
					//if (rc.hasBuildRequirements(RobotType.VIPER)) System.out.println("built a viper because we are losing");
				} 
			} else {
				if (spawnCount % 15 == 14) {
					spawnType = RobotType.VIPER;
				}
			}
		}
		/*if (pullMode && rc.getRoundNum() <= 500) {
			if (spawnCount % 2 == 0) {
				spawnType = RobotType.SCOUT;
			}
		}*/
		
		if (rc.getRoundNum() - lastFleeZombiesRound < 100) {
			if (spawnType == RobotType.VIPER || spawnType == RobotType.TURRET
					|| spawnType == RobotType.SCOUT) {
//				Debug.indicate("convert", 0, "converted " + spawnType + " spawn into soldier");
				//System.out.println("converted " + spawnType + " spawn into soldier");
				spawnType = RobotType.SOLDIER;
			}
		} else if (rc.getRoundNum() - lastFleeOtherTeamRound < 100) {
			if (spawnType == RobotType.TURRET) {
//				Debug.indicate("convert", 0, "converted " + spawnType + " spawn into soldier");
				//System.out.println("converted " + spawnType + " spawn into soldier");
				spawnType = RobotType.SOLDIER;
			}
		}
		if (rc.getRobotCount() < 15) {
			if (spawnType == RobotType.TURRET) {
//				Debug.indicate("convert", 0, "converted " + spawnType + " spawn into soldier");
				//System.out.println("converted " + spawnType + " spawn into soldier");
				spawnType = RobotType.SOLDIER;
			}
		}
		
//		if (spawnType == RobotType.VIPER) {
//			spawnType = RobotType.TURRET;
//		}
		if (rc.getRobotCount() <= 270) {
			spawnType = RobotType.SOLDIER;
		} else {
			spawnType = RobotType.SCOUT;
		}
		
		if (!rc.hasBuildRequirements(spawnType)) return;

		Direction dir = Direction.values()[FastMath.rand256() % 8];
		for (int i = 0; i < 8; ++i) {
			if (rc.canBuild(dir, spawnType)) {
				rc.build(dir, spawnType);
				scheduledEducationRound = rc.getRoundNum() + spawnType.buildTurns;
				scheduledEducationType = spawnType;
//				Debug.indicate("education", 1, "scheduling education of " + scheduledEducationType + " for " + scheduledEducationRound);
				++spawnCount;
				return;
			}
			dir = dir.rotateRight();
		}
	}
	
	private static void educateBabyAboutAntiTurtleCharge() throws GameActionException {
		if (AntiTurtleCharge.enemyMightBeATurtle) {
			if (AntiTurtleCharge.chargeCenter != null && rc.getRoundNum() < AntiTurtleCharge.endRound) {
				Messages.proposeAntiTurtleChargePlan(AntiTurtleCharge.chargeCenter, AntiTurtleCharge.chargeRound, 2);
			}
		} else {
			Messages.sendNotATurtle(2);
		}
	}
	
	private static void educateBabyScoutOrArchon() throws GameActionException {
		// tell baby known map edges
		Messages.sendKnownMapEdges(2);
		
		// tell baby known zombie dens
		for (int i = 0; i < knownZombieDens.size; i += 3) {
			MapLocation[] denList = new MapLocation[3];
			int listLen = 0;
			for (int j = 0; j < 3 && i+j < knownZombieDens.size; j += 1) {
				denList[j] = knownZombieDens.locations[i+j];				
				listLen += 1;
			}
			Messages.sendUpToThreeZombieDens(denList, listLen, 2);
		}
		
		// tell baby known enemy archon
		for (int i = 0; i < Radar.theirArchonIdListLength; ++i) {
			BigRobotInfo bri = Radar.bigRobotInfoById[Radar.theirArchonIdList[i]];
			Messages.sendRobotLocation(bri, 2);
		}
		// tell baby closest known enemy turret
		Messages.sendRobotLocation(Radar.closestEnemyTurretInfo, 2);
	
		// tell baby known neutral archon locations
		for (int i = 0; i < knownNeutralArchons.size; ++i) {
			MapLocation archonLoc = knownNeutralArchons.locations[i];
			Messages.sendNeutralLocation(archonLoc, RobotType.ARCHON, 2);
		}

		educateBabyAboutAntiTurtleCharge();
	}
	
	private static void educateBabySoldierOrViper() throws GameActionException {
		// tell soldirs known enemy archon
		for (int i = 0; i < Radar.theirArchonIdListLength; ++i) {
			BigRobotInfo bri = Radar.bigRobotInfoById[Radar.theirArchonIdList[i]];
			Messages.sendRobotLocation(bri, 2);
		}
		// tell scout closest known enemy turret
		Messages.sendRobotLocation(Radar.closestEnemyTurretInfo, 2);
		educateBabyAboutAntiTurtleCharge();
	}
	
	private static void educateBaby(RobotType babyType) throws GameActionException {
		Messages.sendBeginEducation(2);
		
		switch (babyType) {
		case SCOUT:
		case ARCHON:
			educateBabyScoutOrArchon();
			break;
			
		case SOLDIER:
		case VIPER:
			educateBabySoldierOrViper();
			break;
			
		default:
		}
	}
	
	private static double repairScore(RobotInfo ally) {
		switch (ally.type) {
		case TURRET: 
			return 1000000.0 - ally.health; // note sign: try to heal turrets which are under attack
		case TTM:
			return 100000.0 + ally.health;			
		case VIPER:
			return 10000.0 + ally.health;
			
		default:
			return ally.health;
		}
	}
	
	private static void tryRepairAlly() throws GameActionException {
		RobotInfo[] healableAllies = rc.senseNearbyRobots(RobotType.ARCHON.attackRadiusSquared, us);
		MapLocation bestLoc = null;
		double bestRepairScore = 0;
		for (RobotInfo ally : healableAllies) {
			if (ally.type == RobotType.ARCHON || ally.health >= ally.type.maxHealth) continue;
			
			double score = repairScore(ally);
			if (score > bestRepairScore) {
				bestRepairScore = score;
				bestLoc = ally.location;
			}
		}
//		Debug.indicate("heal", 0, "bestLoc = " + bestLoc);
		if (bestLoc != null) {
			rc.repair(bestLoc);
		}
	}
	
	private static void tryConvertNeutrals() throws GameActionException {
		if (!rc.isCoreReady()) return;
		RobotInfo[] adjacentNeutrals = rc.senseNearbyRobots(2, Team.NEUTRAL);
		for (RobotInfo neutral : adjacentNeutrals) {
			rc.activate(neutral.location);
			educateBaby(neutral.type);

			if (neutral.type == RobotType.ARCHON) {
				int rangeSq = MapEdges.maxRangeSq;
				if (rc.senseHostileRobots(here, mySensorRadiusSquared).length > 0) {
					rangeSq = 30 * mySensorRadiusSquared;
				}
				Messages.sendNeutralWasActivated(neutral.location, neutral.type, rangeSq);
//				Debug.indicate("archons", 2, "sending message that I activated a neutral archon! rangeSq = " + rangeSq);
			}
			return;
		}
	}
	
	private static boolean pathToLocationIsFreeOfRubble(MapLocation dest) {
		MapLocation loc = dest.add(dest.directionTo(here));
		while (!loc.equals(here)) {
			if (rc.senseRubble(loc) >= GameConstants.RUBBLE_OBSTRUCTION_THRESH) {
				return false;
			}
			loc = loc.add(loc.directionTo(here));
		}
		return true;
	}
	
	private static void considerDestination(MapLocation loc, DestinationType type) {
		if (currentDestination != null) {
			if (currentDestinationType == DestinationType.NEUTRALARCHON) {
				return; // neutral archon has priority
			}
			if (currentDestinationType == DestinationType.NEUTRAL) {
				if (rc.canSenseLocation(currentDestination)) {
					if (type == DestinationType.PARTREGION || type == DestinationType.PARTS) {
						if (pathToLocationIsFreeOfRubble(currentDestination)) {
							return; // prefer neutrals to parts
						}
					}
				}
			}
		}
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
//					Debug.indicate("regions", 0, "got region: parts=" + region.totalParts + ", loc=" + region.centralLocation + ", avgTurnsToUncover=" + region.avgTurnsToUncover);;
					PartMemory.add(region);
					break;
					
				case Messages.CHANNEL_FOUND_NEUTRAL:
					NeutralRobotInfo neutral = Messages.parseNeutralLocation(data);
					if (Messages.parseNeutralWasActivated(data)) {
//						Debug.indicate("archons",  1, "heard that " + neutral.type + " at " + neutral.location + " was activated");
						knownNeutralArchons.remove(neutral.location);
						if (neutral.location.equals(currentDestination)) {
							currentDestination = null;
						}
					} else {
//						Debug.indicate("archons", 1, "heard about " + neutral.type + " at " + neutral.location);
						considerDestination(neutral.location, DestinationType.NEUTRAL);
						if (neutral.type == RobotType.ARCHON) {
							knownNeutralArchons.add(neutral.location);
						}
					}
					break;
					
//				case Messages.CHANNEL_ENEMY_TURRET_WARNING:
//					Messages.processEnemyTurretWarning(data);
//					break;
					
				case Messages.CHANNEL_UNPAIRED_SCOUT_REPORT:
					nextUnpairedScoutCount += 1;
//					Debug.indicateAppend("unpaired", 2, ", " + sig.getID());
//					Debug.indicateLine("unpaired", here, sig.getLocation(), 0, 255, 0);
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
					
				case Messages.CHANNEL_ZOMBIE_DEN_LIST:
					receiveZombieDenList(data, sig.getLocation());
					break;

				case Messages.CHANNEL_ROBOT_LOCATION:
					Messages.processRobotLocation(sig, data);
					break;
					
				case Messages.CHANNEL_ANTI_TURTLE_CHARGE:
					AntiTurtleCharge.processAntiTurtleChargeMessage(data);
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
		
	private static void receiveZombieDenList(int[] data, MapLocation origin) {
		MapLocation[] denList = new MapLocation[3];
		int numDens = Messages.parseUpToThreeZombieDens(data, origin, denList);
		for (int i = 0; i < numDens; ++i) {
			knownZombieDens.add(denList[i]);
//			Debug.indicateAppend("dens", 2, ", " + denList[i]);
		}
	}
	
	private static void pickDestination() throws GameActionException {
		// check if the thing we are going for is gone
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
		
		// prioritize neutral archons
		if (knownNeutralArchons.size > 0) {
			MapLocation closestNeutralArchon = knownNeutralArchons.findClosestMemberToLocation(here);
			boolean stillExists = true;
			if (rc.canSense(closestNeutralArchon)) {
				RobotInfo robot = rc.senseRobotAtLocation(closestNeutralArchon);
				if (robot == null || robot.team != Team.NEUTRAL) {
					knownNeutralArchons.remove(closestNeutralArchon);
					stillExists = false;
					if (closestNeutralArchon.equals(currentDestination)) {
						currentDestination = null;
					}
				}
			}
			if (stillExists) {
//				Debug.indicate("archons", 0, "dest = neutral archon at " + closestNeutralArchon);
				currentDestination = closestNeutralArchon;
				currentDestinationType = DestinationType.NEUTRALARCHON;
				return;
			}
		}
		
		RobotInfo[] nearbyNeutrals = rc.senseNearbyRobots(mySensorRadiusSquared, Team.NEUTRAL);
		for (RobotInfo neutral : nearbyNeutrals) {
			if (!neutral.location.equals(here)) {
				considerDestination(neutral.location, DestinationType.NEUTRAL);
			}
		}	
		if (currentDestination != null && currentDestinationType == DestinationType.NEUTRAL) {
			if (rc.canSenseLocation(currentDestination)) {
				// prefer to go for neutrals when they are in sight and the path to
				// them is clear
				if (pathToLocationIsFreeOfRubble(currentDestination)) {
					return;
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
//					Debug.indicate("regions", 1, "considering " + region.centralLocation);
					considerDestination(region.centralLocation, DestinationType.PARTREGION);
				}
			}
		}
		
//		Debug.indicate("regions", 2, "destination = " + currentDestination);
		if (currentDestination != null) {
//			Debug.indicateLine("regions", here, currentDestination, 255, 0, 0);
		}
	}
	
	private static void goToDestination() throws GameActionException {
//		Debug.indicate("parts", 1, "destination = " + currentDestination);
		if (currentDestination != null) {
			Nav.goToDirectSafelyAvoidingTurret(currentDestination, Radar.closestEnemyTurretLocation);
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
			if (ally.type == RobotType.GUARD) continue;
			avgX += ally.location.x;
			avgY += ally.location.y;			
			N += 1;
		}
		if (N != 0) {
			avgX /= N;
			avgY /= N;
			Nav.goToDirectSafelyAvoidingTurret(new MapLocation(avgX, avgY), Radar.closestEnemyTurretLocation);
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
		if (Radar.closestEnemyTurretLocation != null) {
			if (here.distanceSquaredTo(Radar.closestEnemyTurretLocation) <= RobotType.TURRET.attackRadiusSquared) {
				mustRetreat = true;
				retreatTarget = retreatTarget.add(Radar.closestEnemyTurretLocation.directionTo(here));
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
		int closestFastEnemyDistSq = Integer.MAX_VALUE;
		int closestSlowEnemyDistSq = Integer.MAX_VALUE;
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
				if (hostile.type.movementDelay <= 2) {
					if (distSq < closestFastEnemyDistSq) {
						closestFastEnemyDistSq = distSq;
					}
				} else {
					if (distSq < closestSlowEnemyDistSq) {
						closestSlowEnemyDistSq = distSq;
					}
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
		
//		Debug.indicate("retreat", 0, "numDangerousHostiles = " + numDangerousHostiles + ", numHelpfulAllies = " + numHelpfulAllies);
//		Debug.indicateAppend("retreat", 0, "; minY = " + MapEdges.minY + ", maxY = " + MapEdges.maxY);
		
		if (numHelpfulAllies >= numDangerousHostiles && !fastZombieIsAdjacent) {
			return false; // we are hopefully safe
		}			
		
		if (numZombies * 2 >= numDangerousHostiles) {
			lastFleeZombiesRound = rc.getRoundNum();
//			Debug.indicate("convert", 1, "lastFleeZombiesRound = " + lastFleeZombiesRound);
		} else {
			lastFleeOtherTeamRound = rc.getRoundNum();
		}
		
//		Debug.indicate("retreat", 1, "must flee overwhelming enemies!!!!! ");
		
		MapLocation closestCorner = MapEdges.getClosestKnownMapCorner();
//		Debug.indicateAppend("retreat", 1, "; closestCorner = " + closestCorner);
		boolean[] directionIsDiscouraged = new boolean[8];
		if ((closestCorner != null) && (here.distanceSquaredTo(closestCorner) <= 100)
				&& !here.equals(closestCorner)) {
			// if near a corner, forbid retreating in directions toward that corner
			int toCornerOrdinal = here.directionTo(closestCorner).ordinal();
//			Debug.indicateAppend("retreat", 1, "; toCorner = " + here.directionTo(closestCorner)); 
			directionIsDiscouraged[toCornerOrdinal] = true;
			//directionIsDiscouraged[(toCornerOrdinal+1)%8] = true;
			//directionIsDiscouraged[(toCornerOrdinal+7)%8] = true;
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
//		Debug.indicateAppend("retreat", 1, "; bestRetreatDir = " + bestRetreatDir); 
		
		if (bestRetreatDir == null) {
			return false;
		}
		
		// consider building a guard to protect us
		if (rc.getTeamParts() >= RobotType.GUARD.partCost) {
			if (closestSlowEnemyDistSq > 2 && closestFastEnemyDistSq < closestSlowEnemyDistSq) {
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
//							Debug.indicate("retreat", 2, "building guard!"); 
							return true;
						}
					}
				}
			}
		}

		rc.move(bestRetreatDir);
//		Debug.indicate("retreat", 2, "moving!"); 
		return true;
	}
}
