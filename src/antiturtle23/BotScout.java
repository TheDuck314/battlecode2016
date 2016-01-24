package antiturtle23;

import battlecode.common.*;

public class BotScout extends Globals {
	private static MapLocation origin;
	private static boolean[][] exploredGrid = new boolean[100][100];
	private static MapLocation exploreDest = null;
	private static int exploreDestGridX;
	private static int exploreDestGridY;
	private static boolean finishedExploring = false;
	private static final int gridSpacing = 10;
	private static final int DANGER_MEMORY_LENGTH = 5;
	private static MapLocation[] dangerMemory = new MapLocation[DANGER_MEMORY_LENGTH];
	private static int dangerMemoryPointer = 0;

	private static int lastPartsOrNeutralSignalRound = -999999;
	
	private static int lastLongRangeRadarBroadcastRound = 0;
	private static int lastGlobalRadarBroadcastRound = 0;
	private static int lastRadarBroadcastRound = 0;
	
	private static int turretFollowId = -1;
	private static int archonFollowId = -1;
	private static final boolean enableArchonFollowing = true;
	private static int lastTurretOwnershipBroadcastRound = -999999;
	private static int[] turretOwnershipReceiveRoundById = new int[32001];
	//private static int lastFollowRound = -999999;
	
	private static boolean broadCastedWithinInterval = false;
	
	private static MapLocationHashSet knownZombieDens = new MapLocationHashSet();
	
	private static MapLocationHashSet knownNeutralArchons = new MapLocationHashSet();
	
	private static int sameDirectionSteps = 0;
	
	//private static boolean pullMode = false;
	//private static int birthRound;
	
	public static void loop() {
		Debug.init("chaseArchon");

    	origin = here;
    	exploredGrid[50][50] = true;   
    	try {
    		processSignals(true);
    	} catch (Exception e) {
    		System.out.println("SCOUT EXCEPTION IN INITIAL PROCESSSIGNALS:");
			e.printStackTrace();    		
    	}
		while (true) {
			int startTurn = rc.getRoundNum();
			try {
				Globals.update();
			    turn();
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
	
	private static void turn() throws GameActionException {	
		Globals.updateRobotInfos();		
				
		processSignals(false);		
		MapEdges.detectAndBroadcastMapEdges(7); // visionRange = 7

		trySuicide();
		
		sendRadarInfo();
		sendRobotInfo();
		manageAntiTurtleChargeProposals();		
		//Radar.indicateEnemyArchonLocation(0, 200, 200);
		if (rc.isCoreReady()) {
			Radar.removeDistantEnemyTurrets(9 * RobotType.SCOUT.sensorRadiusSquared);			
			Radar.updateClosestEnemyTurretLocation();
		}

		trySendPartsOrNeutralLocation();
		trySendZombieDenLocations();
		
		if (rc.isCoreReady()) {
			if (retreatIfNecessary()) {
				return;
			}
			if (tryFollowHelplessEnemyArchon()) {
				return;
			}
			if (tryFollowTurret()) {
				return;
			} else {
				tryBroadcastUnpairedScoutSignal();
			}

		}
		exploreTheMap();
	}
	
	private static void manageAntiTurtleChargeProposals() throws GameActionException {
		// consider proposing a charge
		// don't propose charges too often
		if (AntiTurtleCharge.chargeCenter == null 
				&& rc.getRoundNum() > AntiTurtleCharge.lastProposalRound + 100) {
			// wait to build up enough units before breaking turtles
			if (rc.getRoundNum() > 500 && rc.getRobotCount() > 40 && rc.getRoundNum() % 10 == 0) {
				// need to have lots of allies gathered with us
				if (visibleAllies.length >= 10) {
					// only a scout that can actually see the enemy should propose a charge
					if (Radar.closestEnemyTurretLocation != null 
							&& rc.canSenseLocation(Radar.closestEnemyTurretLocation)) {
						MapLocation proposalCenter = Radar.closestEnemyTurretLocation.add(
								here.directionTo(Radar.closestEnemyTurretLocation), 3);

						// don't propose a charge if we have seen enemy turrets in several
						// distinct locations, because then we are not facing a turtle
						boolean haveSeenDistantTurret = false;
						for (int i = 0; i < Radar.numEnemyTurrets; ++i) {
							MapLocation enemyTurretLocation = Radar.enemyTurretLocationById[Radar.enemyTurretIds[i]];
							if (enemyTurretLocation != null 
									&& proposalCenter.distanceSquaredTo(enemyTurretLocation) > 100) {
								haveSeenDistantTurret = true;
								break;
							}
						}
						if (!haveSeenDistantTurret) {
							// propose a charge
							AntiTurtleCharge.proposeCharge(proposalCenter);
							Debug.indicate("charge", 2, "proposed anti turtle charge at " + proposalCenter);
						} else {
							Debug.indicate("charge", 1, "have seen distant turret");
						}
					} else {
						Debug.indicate("charge", 1, "no enemy turret visible");
					}
				} else {
					Debug.indicate("charge", 1, "not enough friends near");
				}
			} else {
				Debug.indicate("charge", 1, "too early, or not enough gathered, or not %10");
			}
		} else {
			Debug.indicate("charge", 1, "last proposal too recent");
		}
		
		// veto a charge if we see a turret too far away from the charge center
		if (AntiTurtleCharge.chargeCenter != null && rc.getRoundNum() < AntiTurtleCharge.gatherRound) {
			for (int i = 0; i < Radar.numEnemyTurrets; ++i) {
				MapLocation enemyTurretLocation = Radar.enemyTurretLocationById[Radar.enemyTurretIds[i]];
				if (enemyTurretLocation != null 
						&& AntiTurtleCharge.chargeCenter.distanceSquaredTo(enemyTurretLocation) > 150) {
					AntiTurtleCharge.vetoCharge();
					Debug.indicate("charge", 2, "vetoed anti turtle charge b/c of turret at " + enemyTurretLocation);
					break;
				}
			}
		}
		
		if (AntiTurtleCharge.chargeCenter != null) {
			Debug.indicateLine("charge", here, AntiTurtleCharge.chargeCenter, 0, 255, 0);
		}
	}

	private static void tryBroadcastUnpairedScoutSignal() throws GameActionException {
		if (rc.getRoundNum() % Globals.checkUnpairedScoutInterval == 0) {
			broadCastedWithinInterval = false;
		} else if (!rc.canSenseRobot(turretFollowId)) {
			if (!broadCastedWithinInterval && visibleHostiles.length == 0) {
				Messages.sendUnpairedScoutReport(30 * mySensorRadiusSquared);
				broadCastedWithinInterval = true;
//				Debug.indicate("unpaired", 0, "sent unpaired message");
			} else {
//				Debug.indicate("unpaired", 0, "sent unpaired message = " + broadCastWithInterval);
			}
		} else {
//			Debug.indicate("unpaired", 0, "I am paired!");
		}
	}
	
	private static void trySuicide() {
		if (rc.getInfectedTurns() == 0 
				&& rc.getRoundNum() > 1000 
				&& rc.senseNearbyRobots(2, Team.ZOMBIE).length > 0) {
			//System.out.println("suiciding");
			rc.disintegrate();
		}
	}
	
	private static boolean tryFollowTurret() throws GameActionException {
		if (!rc.isCoreReady()) return false;
	
		if (rc.canSenseRobot(turretFollowId)) {
			MapLocation turretLoc = rc.senseRobot(turretFollowId).location;
			if (here.isAdjacentTo(turretLoc)) {
				Nav.goToDirectSafelyAvoidingTurret(turretLoc, Radar.closestEnemyTurretLocation);
				if (rc.getRoundNum() - lastTurretOwnershipBroadcastRound > 40) {
					Messages.sendTurretOwnershipClaim(turretFollowId, 2*mySensorRadiusSquared);
					lastTurretOwnershipBroadcastRound = rc.getRoundNum();
				}
			} else {
				Nav.goToDirect(turretLoc);
			}
//			Debug.indicateDot("unpaired", turretLoc, 0, 255, 0);
			return true;
		}

		for (RobotInfo ally : visibleAllies) {
			if (ally.type == RobotType.TTM || ally.type == RobotType.TURRET) {
				if (rc.getRoundNum() - turretOwnershipReceiveRoundById[ally.ID] > 80) {
					turretFollowId = ally.ID;
					Nav.goToDirect(ally.location);
//					Debug.indicateDot("unpaired", ally.location, 0, 255, 0);
					return true;
				}
			}
		}
		
		if (enableArchonFollowing && rc.getRoundNum() > 300) {
			if (rc.canSenseRobot(archonFollowId)) {
				MapLocation archonLoc = rc.senseRobot(archonFollowId).location;
				if (here.isAdjacentTo(archonLoc)) {
					Nav.goToDirectSafelyAvoidingTurret(archonLoc, Radar.closestEnemyTurretLocation);
					if (rc.getRoundNum() - lastTurretOwnershipBroadcastRound > 40) {
						Messages.sendTurretOwnershipClaim(archonFollowId, 2*mySensorRadiusSquared);
						lastTurretOwnershipBroadcastRound = rc.getRoundNum();
					}
				} else {
					Nav.goToDirect(archonLoc);
				}
//				Debug.indicateDot("unpaired", archonLoc, 0, 255, 0);
				return true;
			}
			for (RobotInfo ally : visibleAllies) {
				if (ally.type == RobotType.ARCHON) {
					if (rc.getRoundNum() - turretOwnershipReceiveRoundById[ally.ID] > 80) {
						archonFollowId = ally.ID;
						Nav.goToDirect(ally.location);
//						Debug.indicateDot("unpaired", ally.location, 0, 255, 0);
						return true;
					}
				}
			}
		}
		
		return false;
	}
	
	private static void trySendPartsOrNeutralLocation() throws GameActionException {
		if (lastPartsOrNeutralSignalRound > rc.getRoundNum() - 10) return;
		
		int rangeSq = 9*mySensorRadiusSquared;

		RobotInfo[] nearbyNeutrals = rc.senseNearbyRobots(mySensorRadiusSquared, Team.NEUTRAL);
		// look for neutral archons
		for (RobotInfo neutral : nearbyNeutrals) {
			if (neutral.type == RobotType.ARCHON) {
				if (!knownNeutralArchons.contains(neutral.location)) {
					Debug.indicate("archons", 0, "found new neutral archon at " + neutral.location);
					rangeSq = MapEdges.maxBroadcastDistSq();
					Messages.sendNeutralLocation(neutral.location, neutral.type, rangeSq);
					knownNeutralArchons.add(neutral.location);
					lastPartsOrNeutralSignalRound = rc.getRoundNum();
					return;				
				} else {
					Debug.indicate("archons", 0, "already know about neutral archon at " + neutral.location);
				}
			}
		}
		
		if (nearbyNeutrals.length > 0) {
			RobotInfo neutralToSend = nearbyNeutrals[0];
			Messages.sendNeutralLocation(neutralToSend.location, neutralToSend.type, rangeSq);
			lastPartsOrNeutralSignalRound = rc.getRoundNum();
			return;
		}	
		

		MapLocation[] partLocs = rc.sensePartLocations(mySensorRadiusSquared);
		if (partLocs.length > 0) {
			MapLocation sentPartLoc = partLocs[0];
			Messages.sendPartsLocation(sentPartLoc, (int)rc.senseParts(sentPartLoc), rangeSq);
			lastPartsOrNeutralSignalRound = rc.getRoundNum();			

			double totalParts = 0;
			double partsCenterX = 0;
			double partsCenterY = 0;
			double avgTurnsToUncover = 0;
			for (MapLocation partLoc : partLocs) {
				double parts = rc.senseParts(partLoc);
				totalParts += parts;
				partsCenterX += parts * partLoc.x;
				partsCenterY += parts * partLoc.y;		
				avgTurnsToUncover += parts * Util.estimateRubbleClearTurns(rc.senseRubble(partLoc));
			}
			partsCenterX /= totalParts;
			partsCenterY /= totalParts;
			MapLocation partsCenter = new MapLocation((int)partsCenterX, (int)partsCenterY);
			avgTurnsToUncover /= totalParts;
			
//			Debug.indicate("regions", 0, "totalParts = " + totalParts + ", partsCenter = " + partsCenter + ", avgTurnsToUncover = " + avgTurnsToUncover);
			
			if (totalParts >= 180) {
				PartRegion region = new PartRegion((int)totalParts, (int)avgTurnsToUncover, partsCenter);			
				if (PartMemory.add(region)) {
					// TODO: REMEMBER ARCHON LOCATION AND REDUCE RANGE
//					Debug.indicate("regions", 1, "sending region!!");
					Messages.sendPartRegion(partsCenter, (int)totalParts, (int)avgTurnsToUncover,
							30*mySensorRadiusSquared);
				}
			}
			
			return;
		}
	}
	
	private static void trySendZombieDenLocations() throws GameActionException {
		// check nearby zombie dens to see if they were destroyed
		for (int i = 0; i < knownZombieDens.size; ++i) {
			MapLocation denLoc = knownZombieDens.locations[i];
			if (rc.canSenseLocation(denLoc)) {
				RobotInfo robot = rc.senseRobotAtLocation(denLoc);
				if (robot == null || robot.type != RobotType.ZOMBIEDEN) {
//					Debug.indicate("dens", 0, "sending message that den at " + denLoc + " was destroyed!");
					knownZombieDens.remove(denLoc);
					Messages.sendZombieDenDestroyed(denLoc, MapEdges.maxBroadcastDistSq());
				}
			}
		}
		
		// broadcast new zombie dens
		RobotInfo[] zombies = rc.senseNearbyRobots(mySensorRadiusSquared, Team.ZOMBIE);
		for (RobotInfo zombie : zombies) {
			if (zombie.type == RobotType.ZOMBIEDEN) {
				if (!knownZombieDens.contains(zombie.location)) {
					MapLocation denLoc = zombie.location;
//					Debug.indicate("dens", 0, "sending message about new den at " + denLoc);
					knownZombieDens.add(denLoc);
					Messages.sendZombieDenLocation(denLoc, MapEdges.maxBroadcastDistSq());
				}
			}
		}		
		
		for (int i = 0; i < knownZombieDens.size; ++i) {
//			Debug.indicateLine("dens", here, knownZombieDens.locations[i], 0, 0, 255);
		}
	}
	
	private static void sendRadarInfo() throws GameActionException {
//		Debug.indicate("radar", 0, "sendRaderInfo: hostiles.length = " + visibleHostiles.length);
		if (visibleHostiles.length == 0) return;
//		Debug.indicate("radar", 0, "lastGlobalRadarBroadcastRound = " + lastGlobalRadarBroadcastRound);
		
		int radarRangeSq = 4*mySensorRadiusSquared;
		if (visibleAllies.length == 0) {
			if (rc.getRoundNum()-lastRadarBroadcastRound < 10) {
				return;
			}
		}
		if (rc.getRoundNum() - lastLongRangeRadarBroadcastRound > 50) {
			radarRangeSq = 30 * mySensorRadiusSquared;
			lastLongRangeRadarBroadcastRound = rc.getRoundNum();
			Radar.clearEnemyCache();
//			Debug.indicate("radar", 1, "setting range to medium = " + radarRangeSq);
		}
		
		if (rc.getRoundNum() - lastGlobalRadarBroadcastRound > 400) {
			radarRangeSq = MapEdges.maxBroadcastDistSq();
			lastGlobalRadarBroadcastRound = rc.getRoundNum();
			lastLongRangeRadarBroadcastRound = rc.getRoundNum();
			Radar.clearEnemyCache();
//			Debug.indicate("radar", 1, "setting range to global = " + radarRangeSq);
		}
		
		RobotInfo[] hostilesToSend = new RobotInfo[5];
		int numberHostilesToSend = 0;
		for (RobotInfo h: visibleHostiles) {
			if (Radar.addEnemyToCache(h)) {
				hostilesToSend[numberHostilesToSend] = h;
				numberHostilesToSend += 1;
				if (numberHostilesToSend >= 5) {
					break;
				}
			}
		}
		if (numberHostilesToSend == 0) return;
		Messages.sendRadarData(hostilesToSend, numberHostilesToSend, radarRangeSq);
		lastRadarBroadcastRound = rc.getRoundNum();
	}
	
	private static void sendRobotInfo() throws GameActionException {
		int turretWarningRangeSq = 9*mySensorRadiusSquared;
		boolean first = true;
		for (RobotInfo hostile : visibleHostiles) {
			if (hostile.type == RobotType.TURRET) {
				if (!Radar.turretIsKnown(hostile.ID, hostile.location)) {
					if (first) {
//						Debug.indicate("turret", 0, "");
						first = false;
					}
//					Debug.indicateAppend("turret", 0, "found turret with id " + hostile.ID + ", loc = " + hostile.location + "; ");
					Radar.addEnemyTurret(hostile.ID, hostile.location);
					Messages.sendEnemyTurretWarning(hostile.ID, hostile.location, turretWarningRangeSq);
				}
			} else if (hostile.type == RobotType.ARCHON) {
				boolean isNewID = Radar.bigRobotInfoById[hostile.ID] == null;
				BigRobotInfo bri = Radar.addRobot(hostile.ID, hostile.type, hostile.team, hostile.location, Globals.roundNum);
				if (Globals.isSendingEnemyArchonLocation && bri != null) {
					int rangeSq = Globals.broadCastRangeSqWhenSeen;
					if (isNewID) rangeSq = MapEdges.maxBroadcastDistSq();
					Messages.sendRobotLocation(bri, rangeSq);
					Debug.indicate("archon", 0, "sent archon discover message");
				}
			}
		}
		
		// Check to see whether the closest turret to us has moved or gone missing.
		FastTurretInfo closestTurret = Radar.findClosestEnemyTurret();
		if (closestTurret != null) {
			int distSq = here.distanceSquaredTo(closestTurret.location);
			if (distSq <= mySensorRadiusSquared) {
				int closestTurretId = closestTurret.ID;
				if (rc.canSenseRobot(closestTurretId)) {
					MapLocation actualLocation = rc.senseRobot(closestTurret.ID).location;
					if (!actualLocation.equals(closestTurret.location)) {
						// The turret is not where we thought it was.
						// Tell people that the turret has moved.
//						Debug.indicate("turret", 1, "sending warning that turret with id " + closestTurretId + " has moved from " + closestTurret.location + " to " + actualLocation);
						Radar.addEnemyTurret(closestTurretId, actualLocation);
						Messages.sendEnemyTurretWarning(closestTurretId, actualLocation, turretWarningRangeSq);
					}
				} else {
					// We can't sense the turret, so it is not where we thought
					// it was. Tell people that the turret is gone.
//					Debug.indicate("turret", 1, "sending warning that turret with id " + closestTurretId + " has gone missing from " + closestTurret.location);
					Radar.removeEnemyTurret(closestTurretId);
					Messages.sendEnemyTurretMissing(closestTurretId, turretWarningRangeSq);
				}
			}
		}
		
		for (int i = 0; i < Radar.theirArchonIdListLength; ++i) {
			int id = Radar.theirArchonIdList[i];
			BigRobotInfo bri = Radar.bigRobotInfoById[id];
			if (bri.location == null) continue;
			if (bri.location.distanceSquaredTo(here) <= mySensorRadiusSquared) {
				if (rc.canSenseRobot(id)) continue;
				bri.location = null;
				// bri.round is the round we learned the original location
				bri.round += 1;
				if (Globals.isSendingEnemyArchonLocation) {
					Messages.sendRobotLocation(bri, Globals.broadCastRangeSqWhenDisappear);
				}
			}
		}
	}

	private static void processSignals(boolean justBorn) throws GameActionException {
		if (justBorn) {
			processSignalsJustBorn();
			return;
		}
		
		Radar.clearEnemyCache();
		
//		Debug.indicate("archon", 2, "");
		
		Signal[] signals = rc.emptySignalQueue();
		int length = signals.length;
		for (int i = length - 1; i >= 0; --i) {
			Signal sig = signals[i];
			
			if (sig.getTeam() != us) {
				if (Globals.isSendingEnemyArchonLocation && Radar.bigRobotInfoById[sig.getID()] != null) {
					Messages.processRobotLocation(sig);
				}
				continue;
			}
			
			int[] data = sig.getMessage();
			if (data != null) {
				switch(data[0] & Messages.CHANNEL_MASK) {
				case Messages.CHANNEL_MAP_EDGES:
					Messages.processMapEdges(data);
					break;
				
				case Messages.CHANNEL_TURRET_OWNERSHIP:
					int turretId = Messages.parseTurretOwnershipClaim(data);
					if (turretId == turretFollowId) {
						turretFollowId = -1;
					} else if (turretId == archonFollowId) {
						archonFollowId = -1;
					}
					turretOwnershipReceiveRoundById[turretId] = rc.getRoundNum();
					break;
					
				case Messages.CHANNEL_ZOMBIE_DEN:
					MapLocation denLoc = Messages.parseZombieDenLocation(data);
					if (Messages.parseZombieDenWasDestroyed(data)) {
						knownZombieDens.remove(denLoc);
					} else {
						knownZombieDens.add(denLoc);
					}
					break;

				case Messages.CHANNEL_RADAR:
					Messages.addRadarDataToEnemyCacheAndReturnClosestHit(data, sig.getLocation(), mySensorRadiusSquared);
					break;
					
				// TODO: Maybe we need this ???
				case Messages.CHANNEL_ENEMY_TURRET_WARNING:
					Messages.processEnemyTurretWarning(data);
					break;
					
				case Messages.CHANNEL_ROBOT_LOCATION:
					Messages.processRobotLocation(sig, data);
					break;
					
				case Messages.CHANNEL_FOUND_NEUTRAL:
					NeutralRobotInfo neutral = Messages.parseNeutralLocation(data);
					if (neutral.type == RobotType.ARCHON) {
						if (Messages.parseNeutralWasActivated(data)) {
							Debug.indicate("archons",  1, "heard that " + neutral.type + " at " + neutral.location + " was activated");
							knownNeutralArchons.remove(neutral.location);
						} else {
							knownNeutralArchons.add(neutral.location);
							Debug.indicate("archons", 1, "heard about a neutral archon at " + neutral.location);
						}
					}
					break;
					
				case Messages.CHANNEL_ANTI_TURTLE_CHARGE:
					AntiTurtleCharge.processAntiTurtleChargeMessage(data);
					break;
					
				default:
				}
			} else {
				// ` signal with no message
				// for now these are only sent by soldiers who have just killed
				// a zombie den. Check to see if we know of a zombie den within
				// the soldier attack radius of the message origin.
				MapLocation signalOrigin = sig.getLocation();
				MapLocation killedDen = knownZombieDens.findClosestMemberToLocation(signalOrigin);
//				Debug.indicate("kill", 0, "got kill message. signalOrigin = " + signalOrigin + ", killedDen = " + killedDen);
				if (killedDen != null 
						&& killedDen.distanceSquaredTo(signalOrigin) <= RobotType.SOLDIER.attackRadiusSquared) {
					knownZombieDens.remove(killedDen);
				}
			}
		}
//		Debug.indicate("edges", 0, "MinX=" + MapEdges.minX + " MaxX=" + MapEdges.maxX + " MinY=" + MapEdges.minY + " MaxY=" + MapEdges.maxY);
	}

	private static void processSignalsJustBorn() throws GameActionException {
		Radar.clearEnemyCache();
		
//		Debug.indicate("archon", 2, "");
		
		Signal[] signals = rc.emptySignalQueue();
		int length = signals.length;
		for (int i = 0; i < length; ++i) {
			Signal sig = signals[i];

			if (sig.getTeam() != us) continue;
			
			int[] data = sig.getMessage();
			if (data != null) {
				switch(data[0] & Messages.CHANNEL_MASK) {
				case Messages.CHANNEL_MAP_EDGES:
					Messages.processMapEdges(data);
					break;
					
				case Messages.CHANNEL_ZOMBIE_DEN_LIST:
					receiveZombieDenList(data, sig.getLocation());
					break;
					
				case Messages.CHANNEL_ROBOT_LOCATION:
					Messages.processRobotLocation(sig, data);
					break;
					
				case Messages.CHANNEL_FLUSH_SIGNAL_QUEUE:
					return;
					
				default:
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
	
	private static boolean tryMicro() throws GameActionException {
		if (tryLuringZombie()) {
			return true;
		}
		return false;
	}
	
	private static boolean squareIsAttackedByAZombie(MapLocation loc, RobotInfo[] zombies) {
		for (RobotInfo zombie : zombies) {
			if (!zombie.type.canAttack()) continue;
			if (zombie.location.distanceSquaredTo(loc) <= zombie.type.attackRadiusSquared) {
				return true;
			}
		}
		return false;
	}
	
	private static boolean tryLuringZombie() throws GameActionException {
		RobotInfo[] visibleZombies = rc.senseNearbyRobots(mySensorRadiusSquared, Team.ZOMBIE);
		if (visibleZombies.length == 0) return false;
//		Debug.indicate("lure", 0, "hello from tryLuringZombie!");
				
		RobotInfo closestZombie = null;
		int bestDistSq = Integer.MAX_VALUE;
		boolean fastZombieIsAdjacent = false;
		int zombieScore = 0;
		for (RobotInfo zombie : visibleZombies) {
			if (zombie.type == RobotType.ZOMBIEDEN) continue;
			int distSq = here.distanceSquaredTo(zombie.location);
			if (distSq < bestDistSq) {
				bestDistSq = distSq;
				closestZombie = zombie;
			}
			if (zombie.type == RobotType.FASTZOMBIE && zombie.location.isAdjacentTo(here)) {
				fastZombieIsAdjacent = true;
			}
			switch (zombie.type) {
			case STANDARDZOMBIE: zombieScore += 1; break;
			case RANGEDZOMBIE: zombieScore += 2; break;
			case FASTZOMBIE: zombieScore += 5; break;
			case BIGZOMBIE: zombieScore += 10; break;
			default:
			}
		}
		if (zombieScore < 10) return false;
		if (closestZombie == null) return false;
		System.out.println("luring zombie!");
//		Debug.indicate("lure", 1, "closestZombie = " + closestZombie.location);
		
		// if we are near the target, and we see an enemy,
		// get infected and suicide
		MapLocation target = Radar.closestEnemyArchonLocation();
		if (target == null) {
			target = centerOfTheirInitialArchons;
		}
//		Debug.indicateLine("lure", here, target, 255, 0, 0);
		if (here.distanceSquaredTo(target) < here.distanceSquaredTo(centerOfOurInitialArchons)) {
			if (rc.senseNearbyRobots(35, them).length > 0) {
				if (rc.getInfectedTurns() > 0) {
					rc.disintegrate();
				} else {
					Nav.goToDirect(closestZombie.location);
				}
				return true;
			}			
		} 

		Direction lureDir = closestZombie.location.directionTo(target);
		MapLocation lureLoc = closestZombie.location.add(lureDir, 2);
		while (squareIsAttackedByAZombie(lureLoc, visibleZombies) && !lureLoc.equals(target)) {
			lureLoc = lureLoc.add(lureDir);
		}
//		Debug.indicateLine("lure", here, lureLoc, 0, 0, 255);
		//if (fastZombieIsAdjacent) {
			Nav.goToDirect(lureLoc);
		//} else {
		//	goToCirclingZombies(lureLoc, visibleZombies);
		//}
//		Debug.indicate("lure", 2, "lureLoc = " + lureLoc);
//		Debug.indicateLine("lure", here, lureLoc, 255, 0, 0);
		return true;
	}
	
	private static boolean circleLeft = true;
	
	private static void goToCirclingZombies(MapLocation dest, RobotInfo[] visibleZombies) throws GameActionException {
		if (here.equals(dest)) {
			return;
		}
		
		Direction dir = here.directionTo(dest);
		for (int i = 0; i < 8; ++i) {
		    MapLocation dirLoc = here.add(dir);
			if (rc.canMove(dir)) {
			    if (!squareIsAttackedByAZombie(dirLoc, visibleZombies)) {
			    	rc.move(dir);
			    	return;
			    }
			} else {
				if (!rc.onTheMap(dirLoc)) {
					circleLeft = !circleLeft;
				}
			}
			if (circleLeft) {
				dir = dir.rotateLeft();
			} else {
				dir = dir.rotateRight();
			}
		}
		// couldn't circle
		Nav.goToDirect(dest);
	}

	public static int numberOfVisialCanAttackEnemies = 0;
	
	private static boolean retreatIfNecessary() throws GameActionException {
		numberOfVisialCanAttackEnemies = 0;
		
		// if any enemy is too close, try to get farther away
		if (visibleHostiles.length == 0) return false;
		
		boolean mustRetreat = false;
		int bestClosestDistSq = 999999;
		for (RobotInfo hostile : visibleHostiles) {			
			if (hostile.type.canAttack()) {
				if (hostile.team == them) numberOfVisialCanAttackEnemies += 1;
				int distSq = here.distanceSquaredTo(hostile.location);
				if (distSq <= 24) {
					mustRetreat = true;
					if (distSq < bestClosestDistSq) {
						bestClosestDistSq = distSq;
					}
				}
			}
		}
		if (!mustRetreat) return false;
		
		Direction bestDir = null;
		Direction[] dirs = Direction.values();
		for (int i = 0; i < 8; ++i) {
			Direction dir = dirs[i];
			if (!rc.canMove(dir)) continue;
			MapLocation dirLoc = here.add(dir);
			int dirClosestDistSq = 999999;
			for (RobotInfo hostile : visibleHostiles) {
				if (hostile.type.canAttack()) {
					int distSq = dirLoc.distanceSquaredTo(hostile.location);
					if (distSq < dirClosestDistSq) {
						dirClosestDistSq = distSq;						
						if (dirClosestDistSq <= bestClosestDistSq) break;
					}
				}
			}
			if (dirClosestDistSq > bestClosestDistSq) {
				bestClosestDistSq = dirClosestDistSq;
				bestDir = dir;
			}
		}
		
		if (bestDir != null) {
//			Debug.indicate("safety", 0, "retreating!");
			rc.move(bestDir);
			lastDir = bestDir;
			return true;
		}
		return false;
	}
	
	private static boolean tryFollowHelplessEnemyArchon() throws GameActionException {
		// Check if there are too many enemies
		if (numberOfVisialCanAttackEnemies > 2) return false;

		// Check if there is enemy archon nearby
		MapLocation loc = Radar.closestEnemyArchonLocation();
		if (loc == null) return false;
		int distSq = loc.distanceSquaredTo(here);
		if (distSq > mySensorRadiusSquared) return false;
		
		// Check if there is already scout following it
		RobotInfo[] allies = rc.senseNearbyRobots(loc, distSq, us);
		for (RobotInfo ally : allies) {
			if (ally.type == RobotType.SCOUT) {
				return false;
			}
		}
		
		// Follow it
		if (Nav.goToDirectSafely(loc)) {
			Debug.indicateLine("chaseArchon", here, loc, 30, 30, 30);
			Debug.indicate("chaseArchon", 0, "chasing from=" + here + " to loc=" + loc);
			Debug.println("chaseArchon", "chasing from=" + here + " to loc=" + loc);
			return true;
		} else {
			// If it is too far away, maybe we are not yet following it?
			return here.distanceSquaredTo(loc) <= 24;
		}
	}
	
	private static void exploreTheMap() throws GameActionException {
		moveAround();
	}

	private static int nFriend = 0;
	
	public static MapLocation dangerousLoc = null;
	public static int dangerousTurn = 0;
	public static double myHealth;
	
	public static Direction lastDir = null;

	public static void updateDangerousLoc() {
		if (rc.getHealth() < myHealth) {
			dangerousLoc = here;
			dangerousTurn = 0;
		} else if (dangerousLoc != null) {
			if (dangerousTurn >= 200) {
				dangerousLoc = null;
				dangerousTurn = 0;
			}
			dangerousTurn += 1;
		}
		myHealth = rc.getHealth();
	}

	private static void moveAround() throws GameActionException {
		updateDangerousLoc();
		if (!rc.isCoreReady()) return;
		Direction[] dirs = new Direction[9];
		boolean[] cmoves = new boolean[9];
		MapLocation[] locs = new MapLocation[9];
//		boolean[] oddPos = new boolean[9];
		//double[] rubbles = new double[9];
		double[] attacks = new double[9];
		//double[] nfriends = new double[9];
		//double[] friends = new double[9];
		double[] scouts  = new double[9];
//		double[] archons  = new double[9];
		double[] scores = new double[9];
		dirs[0] = null;
		cmoves[0] = true;
		locs[0] = here;
		int ndirs = 1;
		for (int i = 0; i < 8; ++i) {
			dirs[ndirs] = Direction.values()[i];
			cmoves[ndirs] = rc.canMove(dirs[ndirs]);
			if (cmoves[ndirs]) {
				locs[ndirs] = here.add(dirs[ndirs]);
				ndirs += 1;
			}
		}
		/*for (int i = 0; i < 9; ++i) {
//			oddPos[i] = !isGoodTurretLocation(locs[i]);
			//rubbles[i] = rc.senseRubble(locs[i]);
		}*/
		RobotInfo[] infos;
		infos = visibleHostiles;
		for (RobotInfo e : infos) {
			if (!e.type.canAttack()) continue;
			int hereDistSq = e.location.distanceSquaredTo(here);
			int safeDistSq = 0;
			switch (e.type) {
			case STANDARDZOMBIE:
			case BIGZOMBIE:
			case GUARD:
				safeDistSq = 9;
				break;
			case FASTZOMBIE:
				safeDistSq = 17; // larger because it is fast
				break;
			case RANGEDZOMBIE:
			case SOLDIER:
				safeDistSq = 26;
				break;
			case VIPER:
				safeDistSq = 35;
				break;
			case TURRET:
				safeDistSq = 54; // Cannot be safe from TURRET
				break;
			default:
			}
			int attackRadiusSquared = e.type.attackRadiusSquared;
			if (hereDistSq >= safeDistSq) continue;
			for (int i = 0; i < ndirs; ++i) {
				int distSq = e.location.distanceSquaredTo(locs[i]);
				if (distSq <= attackRadiusSquared) {
					attacks[i] += e.attackPower;
				} else if (distSq <= attackRadiusSquared * 2){
					attacks[i] += e.attackPower / (5 * distSq / (attackRadiusSquared+1));
				}
			}
		}
		infos = visibleAllies;
		// MapLocation friendVec = new MapLocation(0,0);
		MapLocation scoutVec = new MapLocation(0,0);
		// MapLocation archonVec = new MapLocation(0,0);
		for (RobotInfo f : infos) {
			if (f.ID == myID) {
				continue;
			}
			switch (f.type) {
			/*case ARCHON:
				archonVec = archonVec.add(here.directionTo(f.location));
//				if (here.distanceSquaredTo(f.location) < 4) {
//					archonVec = archonVec.add(here.directionTo(f.location));
//				}
				break;
			// case SOLDIER:
			case TURRET:
				for (int i = 0; i < 9; ++i) {
					if (f.location.distanceSquaredTo(locs[i]) < 9) {
						nfriends[i] += 1;
					}
				}
				friendVec = friendVec.add(here.directionTo(f.location));
				break;*/
			case SCOUT:
				scoutVec = scoutVec.add(here.directionTo(f.location));
				break;
			default:
			}
		}
//		Debug.indicateLine("explore", here, FastMath.addVec(friendVec, FastMath.addVec(here, FastMath.multiplyVec(-5,scoutVec))), 0, 255, 0);
		for (int i = 0; i < ndirs; ++i) {
//			friends[i] = FastMath.dotVec(dirs[i], friendVec);
			scouts[i] = FastMath.dotVec(dirs[i], scoutVec);
//			archons[i] = FastMath.dotVec(dirs[i], archonVec);
		}
		for (int i = 0; i < ndirs; ++i) {
			scores[i] = -attacks[i] * 1000;
			if (locs[i].equals(dangerousLoc)) {
				scores[i] -= 2000;
			}
//			if (rubbles[i] >= GameConstants.RUBBLE_SLOW_THRESH) {
//				scores[i] -= attacks[8] * 1000;
//				scores[i] += 500;
//			}
//			if (oddPos[i]) {
//				scores[i] += 100;
//			}
//			scores[i] += nfriends[i] * 50;
//			scores[i] += friends[i];
			scores[i] -= scouts[i] * 50;
//			scores[i] += archons[i] * 10;
			int disEdge = 100;
			disEdge = Math.min(disEdge, Math.abs(locs[i].x - MapEdges.minX));
			disEdge = Math.min(disEdge, Math.abs(locs[i].x - MapEdges.maxX));
			disEdge = Math.min(disEdge, Math.abs(locs[i].y - MapEdges.minY));
			disEdge = Math.min(disEdge, Math.abs(locs[i].y - MapEdges.maxY));
			if (disEdge < 4) {
				scores[i] -= (4-disEdge) * 1000;
			}
		}
		/*double isDiagonalScore = FastMath.rand256() / 10 - 12;
		for (int i = 0; i < 8; ++i) {
			if (dirs[i].isDiagonal()) {
				scores[i] += isDiagonalScore;
			}
		}*/

		if (sameDirectionSteps > 25) {
			sameDirectionSteps = 0;
			lastDir = null;
//			Debug.indicate("explore", 0, "Do not keep sameDirection");
		} else if (Util.isGoodDirection(lastDir)) {
			Direction lastDirLeft = lastDir.rotateLeft();
			Direction lastDirRight = lastDir.rotateRight();
			for (int i = 1; i < ndirs; ++i) {
				if (dirs[i] == lastDir) {
					scores[i] += 100;
				} else if (dirs[i] == lastDirRight || dirs[i] == lastDirLeft) {
					scores[i] += 50;
				}
			}
		}
		scores[0] -= 128;

		double bestScore = -100000;
		Direction bestDir = null;
		int rdn = FastMath.rand256();
		for (int i = 0; i < ndirs; ++i) {
			if (bestScore < scores[(rdn + i) % ndirs]) {
				bestDir = dirs[(rdn + i) % ndirs];
				bestScore = scores[(rdn + i) % ndirs];
			}
		}
		if (bestDir != null) {
			if (rc.canMove(bestDir)) {
				//nFriend = (int)nfriends[bestI];
				rc.move(bestDir);
			}
		} /*else if (rubbles[8] >= GameConstants.RUBBLE_SLOW_THRESH) {
			rc.clearRubble(Direction.NONE);
		}*/
		
		if (lastDir == bestDir) {
			sameDirectionSteps += 1;
		}
		
		lastDir = bestDir;
		return;
	}
	
	private static MapLocation gridLocation(int gridX, int gridY) {
		return new MapLocation(origin.x + gridSpacing*(gridX-50), origin.y + gridSpacing*(gridY-50));
	}
	
	private static int nearestGridX(MapLocation loc) {
		return 50 + (int)Math.round((here.x - origin.x)/((double)gridSpacing));
	}
	
	private static int nearestGridY(MapLocation loc) {
		return 50 + (int)Math.round((here.y - origin.y)/((double)gridSpacing));
	}
	
	private static int[] legDX = { 0, 0, 0, -1, 0, 0, 0, 1 };
    private static int[] legDY = { 0, 1, 0, 0, 0, -1, 0, 0 };
    
    private static boolean isFarOffMap(MapLocation loc) {
    	return (MapEdges.minX != MapEdges.UNKNOWN && loc.x < MapEdges.minX - gridSpacing/2)
				|| (MapEdges.maxX != MapEdges.UNKNOWN && loc.x > MapEdges.maxX + gridSpacing/2) 
				|| (MapEdges.minY != MapEdges.UNKNOWN && loc.y < MapEdges.minY - gridSpacing/2) 
				|| (MapEdges.maxY != MapEdges.UNKNOWN && loc.y > MapEdges.maxY + gridSpacing/2);
    }
    
    private static MapLocation moveOntoMap(MapLocation loc) {
    	final int edgeDistance = 4;
    	int retX = loc.x;
    	int retY = loc.y;
    	if (MapEdges.minX != MapEdges.UNKNOWN && retX < MapEdges.minX) {
    		retX = MapEdges.minX + edgeDistance;
    	}
    	if (MapEdges.maxX != MapEdges.UNKNOWN && retX > MapEdges.maxX) {
    		retX = MapEdges.maxX - edgeDistance;
    	}
    	if (MapEdges.minY != MapEdges.UNKNOWN && retY < MapEdges.minY) {
    		retY = MapEdges.minY + edgeDistance;
    	}
    	if (MapEdges.maxY != MapEdges.UNKNOWN && retY > MapEdges.maxY) {
    		retY = MapEdges.maxY - edgeDistance;
    	}
    	return new MapLocation(retX, retY);
    }
    
	private static void pickNewExploreDest() {
		int centerX = nearestGridX(here);
		int centerY = nearestGridY(here);
		if (!exploredGrid[centerX][centerY]) {
			MapLocation centerLoc = gridLocation(centerX, centerY);
			if (!isFarOffMap(centerLoc)) {
				exploreDest = moveOntoMap(centerLoc);
				exploreDestGridX = centerX;
				exploreDestGridY = centerY;
				return;
			}
		}
		
        Direction startDiag = here.directionTo(origin);
        if (startDiag == Direction.NONE || startDiag == Direction.OMNI) startDiag = Direction.NORTH_EAST;
        if (!startDiag.isDiagonal()) startDiag = startDiag.rotateLeft();
        
		for (int radius = 1; radius <= 10; radius++) {
            MapLocation bestLoc = null;
            int bestGridX = -1;
            int bestGridY = -1;
            int bestDistSq = 999999;

            int gridX = centerX + radius * startDiag.dx;
            int gridY = centerY + radius * startDiag.dy;
            int diag = startDiag.ordinal();
            for (int leg = 0; leg < 4; leg++) {
                for (int i = 0; i < 2 * radius; i++) {
                	if (!exploredGrid[gridX][gridY]) {
            			MapLocation gridLoc = gridLocation(gridX, gridY);
            			if (!isFarOffMap(gridLoc)) {
            				gridLoc = moveOntoMap(gridLoc);
//            				Debug.indicateDot("explore", gridLoc, 0, 0, 255);
            				int distSq = origin.distanceSquaredTo(gridLoc);
                            if (distSq < bestDistSq) {
                                bestDistSq = distSq;
                                bestLoc = gridLoc;
                                bestGridX = gridX;
                                bestGridY = gridY;
                            }
                        }
                    }

                    gridX += legDX[diag];
                    gridY += legDY[diag];
                }

                diag = (diag + 2) % 8;
            }

            if (bestLoc != null) {
            	exploreDest = bestLoc;
            	exploreDestGridX = bestGridX;
            	exploreDestGridY = bestGridY;
//            	Debug.indicateDot("explore", exploreDest, 0, 255, 0);
				return;
            }
        }
		
		exploreDest = null;
		finishedExploring = true;
	}

	private static void explore() throws GameActionException {
		if (finishedExploring) return;
		
//		Debug.indicate("explore", 0, "exploreDest = " + exploreDest);
			
		if (exploreDest != null && 
				(here.equals(exploreDest) || 
				here.isAdjacentTo(exploreDest) && rc.senseRobotAtLocation(exploreDest) != null)) {
			// we reached the exploreDest
			exploredGrid[exploreDestGridX][exploreDestGridY] = true;
//			Debug.indicate("explore", 2, String.format("set exploredGrid[%d][%d] = true", exploreDestGridX, exploreDestGridY));
			exploreDest = null; // pick a new explore dest
		}
		
		if (exploreDest == null || MapEdges.isOffMap(exploreDest)) {
			pickNewExploreDest();
//			Debug.indicate("explore", 1, "picked new exploreDest = " + exploreDest);
			return;
		}

		
//		Debug.indicateDot("explore", exploreDest, 0, 255, 0);
		if (rc.isCoreReady()) {
//			Debug.indicate("explore", 1, "going to exploreDest");
			Nav.goToBug(exploreDest/*, checkWhichSquaresAreSafe()*/);
		}
	}
}
