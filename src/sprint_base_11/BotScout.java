package sprint_base_11;

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
	private static int lastRadarBroadcastRound = 0;
	
	private static int turretFollowId = -1;
	private static int archonFollowId = -1;
	private static final boolean enableArchonFollowing = true;
	private static int lastTurretOwnershipBroadcastRound = -999999;
	private static int[] turretOwnershipReceiveRoundById = new int[32001];
	
	private static boolean broadCastWithInterval = false;
	
	private static MapLocation closestEnemyTurretLocation = null;
	
	private static MapLocationHashSet knownZombieDens = new MapLocationHashSet();
	
	public static void loop() {
    	Debug.init("kill");
    	origin = here;
    	exploredGrid[50][50] = true;   
    	Debug.indicate("dens", 2, "dens received at birth: ");
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
		Globals.visibleHostiles = rc.senseHostileRobots(here, mySensorRadiusSquared);
		Globals.visibleAllies = rc.senseNearbyRobots(mySensorRadiusSquared, us);
		
		processSignals();		
		MapEdges.detectAndBroadcastMapEdges(7); // visionRange = 7

		if (rc.getRoundNum() % Globals.checkUnpairedScoutInterval == 0) {
			broadCastWithInterval = false;
		} else if (!rc.canSenseRobot(turretFollowId)) {
			if (!broadCastWithInterval && visibleHostiles.length == 0) {
				Messages.sendUnpairedScoutReport(30 * mySensorRadiusSquared);
				broadCastWithInterval = true;
				Debug.indicate("unpaired", 0, "sent unpaired message");
			} else {
				Debug.indicate("unpaired", 0, "sent unpaired message = " + broadCastWithInterval);
			}
		} else {
			Debug.indicate("unpaired", 0, "I am paired!");
		}
		
		sendRadarInfo();
		sendTurretWarning();
		
		if (rc.isCoreReady()) {
			FastTurretInfo closestEnemyTurret = Radar.findClosestEnemyTurret();
			if (closestEnemyTurret != null) {
				closestEnemyTurretLocation = closestEnemyTurret.location;
			} else {
				closestEnemyTurretLocation = null;
			}
		}

		trySendPartsOrNeutralLocation();
		trySendZombieDenLocations();
		
		if (tryFollowTurret()) {
			return;
		}
		
		moveAround();
	}
	
	private static boolean tryFollowTurret() throws GameActionException {
		if (!rc.isCoreReady()) return false;
	
		if (rc.canSenseRobot(turretFollowId)) {
			MapLocation turretLoc = rc.senseRobot(turretFollowId).location;
			if (here.isAdjacentTo(turretLoc)) {
				Nav.goToDirectSafelyAvoidingTurret(turretLoc, closestEnemyTurretLocation);
				if (rc.getRoundNum() - lastTurretOwnershipBroadcastRound > 40) {
					Messages.sendTurretOwnershipClaim(turretFollowId, 2*mySensorRadiusSquared);
					lastTurretOwnershipBroadcastRound = rc.getRoundNum();
				}
			} else {
				Nav.goToDirect(turretLoc);
			}
			Debug.indicateDot("unpaired", turretLoc, 0, 255, 0);
			return true;
		}

		for (RobotInfo ally : visibleAllies) {
			if (ally.type == RobotType.TTM || ally.type == RobotType.TURRET) {
				if (rc.getRoundNum() - turretOwnershipReceiveRoundById[ally.ID] > 80) {
					turretFollowId = ally.ID;
					Nav.goToDirect(ally.location);
					Debug.indicateDot("unpaired", ally.location, 0, 255, 0);
					return true;
				}
			}
		}
		
		if (enableArchonFollowing && rc.getRoundNum() > 300) {
			if (rc.canSenseRobot(archonFollowId)) {
				MapLocation archonLoc = rc.senseRobot(archonFollowId).location;
				if (here.isAdjacentTo(archonLoc)) {
					Nav.goToDirectSafelyAvoidingTurret(archonLoc, closestEnemyTurretLocation);
					if (rc.getRoundNum() - lastTurretOwnershipBroadcastRound > 40) {
						Messages.sendTurretOwnershipClaim(archonFollowId, 2*mySensorRadiusSquared);
						lastTurretOwnershipBroadcastRound = rc.getRoundNum();
					}
				} else {
					Nav.goToDirect(archonLoc);
				}
				Debug.indicateDot("unpaired", archonLoc, 0, 255, 0);
				return true;
			}
			for (RobotInfo ally : visibleAllies) {
				if (ally.type == RobotType.ARCHON) {
					if (rc.getRoundNum() - turretOwnershipReceiveRoundById[ally.ID] > 80) {
						archonFollowId = ally.ID;
						Nav.goToDirect(ally.location);
						Debug.indicateDot("unpaired", ally.location, 0, 255, 0);
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

		MapLocation[] partLocs = rc.sensePartLocations(mySensorRadiusSquared);
		if (partLocs.length > 0) {
			MapLocation partLoc = partLocs[0];
			Messages.sendPartsLocation(partLoc, (int)rc.senseParts(partLoc), rangeSq);
			lastPartsOrNeutralSignalRound = rc.getRoundNum();			
			return;
		}
		
		RobotInfo[] nearbyNeutrals = rc.senseNearbyRobots(mySensorRadiusSquared, Team.NEUTRAL);
		if (nearbyNeutrals.length > 0) {
			MapLocation neutralLoc = nearbyNeutrals[0].location;
			Messages.sendNeutralLocation(neutralLoc, rangeSq);
			lastPartsOrNeutralSignalRound = rc.getRoundNum();
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
					Debug.indicate("dens", 0, "sending message that den at " + denLoc + " was destroyed!");
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
					Debug.indicate("dens", 0, "sending message about new den at " + denLoc);
					knownZombieDens.add(denLoc);
					Messages.sendZombieDenLocation(denLoc, MapEdges.maxBroadcastDistSq());
				}
			}
		}		
		
		for (int i = 0; i < knownZombieDens.size; ++i) {
			Debug.indicateLine("dens", here, knownZombieDens.locations[i], 0, 0, 255);
		}
	}
	
	private static void sendRadarInfo() throws GameActionException {
		Debug.indicate("radar", 0, "sendRaderInfo: hostiles.length = " + visibleHostiles.length);
		if (visibleHostiles.length == 0) return;
		
		int radarRangeSq = 4*mySensorRadiusSquared;
		if (visibleAllies.length == 0) {
			if (rc.getRoundNum()-lastRadarBroadcastRound < 10) {
				return;
			}
		}
		if (rc.getRoundNum() - lastLongRangeRadarBroadcastRound > 50) {
			radarRangeSq = 30 * mySensorRadiusSquared;
			lastLongRangeRadarBroadcastRound = rc.getRoundNum();
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
		Messages.sendRadarData(hostilesToSend, numberHostilesToSend, radarRangeSq);
		lastRadarBroadcastRound = rc.getRoundNum();
	}
	
	private static void sendTurretWarning() throws GameActionException {		
		int turretWarningRangeSq = 9*mySensorRadiusSquared;
		boolean first = true;
		for (RobotInfo hostile : visibleHostiles) {
			if (hostile.type == RobotType.TURRET) {
				if (!Radar.turretIsKnown(hostile.ID, hostile.location)) {
					if (first) {
						Debug.indicate("turret", 0, "");
						first = false;
					}
					Debug.indicateAppend("turret", 0, "found turret with id " + hostile.ID + ", loc = " + hostile.location + "; ");
					Radar.addEnemyTurret(hostile.ID, hostile.location);
					Messages.sendEnemyTurretWarning(hostile.ID, hostile.location, turretWarningRangeSq);
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
						Debug.indicate("turret", 1, "sending warning that turret with id " + closestTurretId 
								+ " has moved from " + closestTurret.location + " to " + actualLocation);
						Radar.addEnemyTurret(closestTurretId, actualLocation);
						Messages.sendEnemyTurretWarning(closestTurretId, actualLocation, turretWarningRangeSq);
					}
				} else {
					// We can't sense the turret, so it is not where we thought
					// it was. Tell people that the turret is gone.
					Debug.indicate("turret", 1, "sending warning that turret with id " + closestTurretId 
							+ " has gone missing from " + closestTurret.location);
					Radar.removeEnemyTurret(closestTurretId);
					Messages.sendEnemyTurretMissing(closestTurretId, turretWarningRangeSq);
				}
			}
		}
	}

	private static void processSignals() {
		Radar.clearEnemyCache();
		
		Signal[] signals = rc.emptySignalQueue();
		for (Signal sig : signals) {
			if (sig.getTeam() != us) continue;
			
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
						if (knownZombieDens.remove(denLoc)) {
						    Debug.indicate("dens", 1, "heard that the den at " + denLoc + " was destroyed");
						} else {
						    Debug.indicate("dens", 1, "heard that den at " + denLoc + " was destroyed, but it wasn't in my list");							
						}
					} else {
						if (knownZombieDens.add(denLoc)) {
						    Debug.indicate("dens", 1, "heard about a new den at " + denLoc);
						} else {
							Debug.indicate("dense", 1, "heard about a new den at " + denLoc + ", but I already knew about it");
						}
					}
					break;
					
				case Messages.CHANNEL_ZOMBIE_DEN_LIST:
					receiveZombieDenList(data, sig.getLocation());
					break;

				case Messages.CHANNEL_RADAR:
					Messages.addRadarDataToEnemyCache(data, sig.getLocation(), myAttackRadiusSquared);
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
				Debug.indicate("kill", 0, "got kill message. signalOrigin = " + signalOrigin + ", killedDen = " + killedDen);
				if (killedDen != null 
						&& killedDen.distanceSquaredTo(signalOrigin) <= RobotType.SOLDIER.attackRadiusSquared) {
					knownZombieDens.remove(killedDen);
				}
			}
		}
		Debug.indicate("edges", 0, "MinX=" + MapEdges.minX + " MaxX=" + MapEdges.maxX + " MinY=" + MapEdges.minY + " MaxY=" + MapEdges.maxY);
	}
	
	private static void receiveZombieDenList(int[] data, MapLocation origin) {
		MapLocation[] denList = new MapLocation[3];
		int numDens = Messages.parseUpToThreeZombieDens(data, origin, denList);
		for (int i = 0; i < numDens; ++i) {
			knownZombieDens.add(denList[i]);
			Debug.indicateAppend("dens", 2, ", " + denList[i]);
		}
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
			myHealth = rc.getHealth();
		} else if (dangerousLoc != null) {
			if (dangerousTurn >= 200) {
				dangerousLoc = null;
				dangerousTurn = 0;
			}
			dangerousTurn += 1;
		}
	}

	private static void moveAround() throws GameActionException {
		updateDangerousLoc();
		if (!rc.isCoreReady()) return;
		Direction[] dirs = new Direction[9];
		boolean[] cmoves = new boolean[9];
		MapLocation[] locs = new MapLocation[9];
//		boolean[] oddPos = new boolean[9];
		double[] rubbles = new double[9];
		double[] attacks = new double[9];
		double[] nfriends = new double[9];
		double[] friends = new double[9];
		double[] scouts  = new double[9];
		double[] archons  = new double[9];
		dirs[8] = null;
		cmoves[8] = true;
		locs[8] = here;
		for (int i = 0; i < 8; ++i) {
			dirs[i] = Direction.values()[i];
			locs[i] = here.add(dirs[i]);
			cmoves[i] = rc.canMove(dirs[i]);
		}
		for (int i = 0; i < 9; ++i) {
//			oddPos[i] = !isGoodTurretLocation(locs[i]);
			rubbles[i] = rc.senseRubble(locs[i]);
		}
		RobotInfo[] infos;
		infos = visibleHostiles;
		for (RobotInfo e : infos) {
			if (!e.type.canAttack()) continue;
			for (int i = 0; i < 9; ++i) {
				int distSq = e.location.distanceSquaredTo(locs[i]);
				if (distSq <= e.type.attackRadiusSquared) {
					attacks[i] += e.attackPower;
				} else {
					attacks[i] += e.attackPower / (5 * distSq / (e.type.attackRadiusSquared+1));
				}
			}
		}
		infos = visibleAllies;
		MapLocation friendVec = new MapLocation(0,0);
		MapLocation scoutVec = new MapLocation(0,0);
		MapLocation archonVec = new MapLocation(0,0);
		for (RobotInfo f : infos) {
			if (f.ID == myID) {
				continue;
			}
			switch (f.type) {
			case ARCHON:
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
				break;
			case SCOUT:
				scoutVec = scoutVec.add(here.directionTo(f.location));
				break;
			default:
			}
		}
//		Debug.indicateLine("explore", here, FastMath.addVec(friendVec, FastMath.addVec(here, FastMath.multiplyVec(-5,scoutVec))), 0, 255, 0);
		for (int i = 0; i < 9; ++i) {
			friends[i] = FastMath.dotVec(dirs[i], friendVec);
			scouts[i] = FastMath.dotVec(dirs[i], scoutVec);
			archons[i] = FastMath.dotVec(dirs[i], archonVec);
		}
		double[] scores = new double[9];
		for (int i = 0; i < 9; ++i) {
			scores[i] = -attacks[i] * 1000;
			if (locs[i] == dangerousLoc) {
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
			scores[i] += friends[i] - scouts[i] * 50;
			scores[i] += archons[i] * 10;
			int disEdge = 100;
			disEdge = Math.min(disEdge, Math.abs(locs[i].x - MapEdges.minX));
			disEdge = Math.min(disEdge, Math.abs(locs[i].x - MapEdges.maxX));
			disEdge = Math.min(disEdge, Math.abs(locs[i].y - MapEdges.minY));
			disEdge = Math.min(disEdge, Math.abs(locs[i].y - MapEdges.maxY));
			if (disEdge < 4) {
				scores[i] -= (4-disEdge) * 1000;
			}
		}
		for (int i = 0; i < 8; ++i) {
			if (dirs[i].isDiagonal()) {
				scores[i] += FastMath.rand256() / 25 - 12;
			}
			if (dirs[i] == lastDir) {
				scores[i] += 100;
				scores[(i+1)%8] += 50;
				scores[(i+7)%8] += 50;
			}
		}
		scores[8] -= 128;
		for (int i = 0; i < 8; ++i) {
			if (!cmoves[i]) {
				scores[i] = -10000000;
			}
		}
		double bestScore = -100000;
		Direction bestDir = null;
		int bestI = 8;
		int rdn = FastMath.rand256();
		for (int i = 0; i < 9; ++i) {
			if (bestScore < scores[(rdn + i) % 9]) {
				bestDir = dirs[(rdn + i) % 9];
				bestScore = scores[(rdn + i) % 9];
				bestI = i;
			}
		}
		nFriend = (int)nfriends[8];
		if (bestDir != null) {
			if (rc.canMove(bestDir)) {
				nFriend = (int)nfriends[bestI];
				rc.move(bestDir);
			}
		} else if (rubbles[8] >= GameConstants.RUBBLE_SLOW_THRESH) {
			rc.clearRubble(Direction.NONE);
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
    	int retX = loc.x;
    	int retY = loc.y;
    	if (MapEdges.minX != MapEdges.UNKNOWN && retX < MapEdges.minX) {
    		retX = MapEdges.minX;
    	}
    	if (MapEdges.maxX != MapEdges.UNKNOWN && retX > MapEdges.maxX) {
    		retX = MapEdges.maxX;
    	}
    	if (MapEdges.minY != MapEdges.UNKNOWN && retY < MapEdges.minY) {
    		retY = MapEdges.minY;
    	}
    	if (MapEdges.maxY != MapEdges.UNKNOWN && retY > MapEdges.maxY) {
    		retY = MapEdges.maxY;
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
            				Debug.indicateDot("explore", gridLoc, 0, 0, 255);
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
            	Debug.indicateDot("explore", exploreDest, 0, 255, 0);
				return;
            }
        }
		
		exploreDest = null;
		finishedExploring = true;
	}
	
	
	
	private static boolean[] checkWhichSquaresAreSafe() {
		boolean[] isSquareSafe = new boolean[8];
		
		Direction[] dirs = Direction.values();		
		MapLocation[] dirLocs = new MapLocation[8];
		for (int d = 0; d < 8; ++d) {
			Direction dir = dirs[d];
			isSquareSafe[d] = rc.canMove(dir);
			dirLocs[d] = here.add(dir);
			if (isSquareSafe[d]) {
				for (int i = 0; i < DANGER_MEMORY_LENGTH; ++i) {
					if (dirLocs[d].equals(dangerMemory[i])) {
						isSquareSafe[d] = false;
						break;
					}
				}
			}
		}

		RobotInfo[] hostiles = rc.senseHostileRobots(here, mySensorRadiusSquared);
		Debug.indicate("safebug", 2, "hostiles.length = " + hostiles.length);
		for (RobotInfo hostile : hostiles) {
			RobotType hostileType = hostile.type;
			if (!hostileType.canAttack()) {
				continue;				
			}
			int hostileRangeSq = Math.max(hostileType.attackRadiusSquared, 8);
			MapLocation hostileLoc = hostile.location;
			for (int d = 0; d < 8; ++d) {
				if (isSquareSafe[d]) {
					if (dirLocs[d].distanceSquaredTo(hostileLoc) <= hostileRangeSq) {
						isSquareSafe[d] = false;
						Debug.indicateDot("explore", dirLocs[d], 255, 0, 0);
					}
				}
			}
		}
		
		return isSquareSafe;
	}
	
	private static boolean retreatIfNecessary() throws GameActionException {
		if (!rc.isCoreReady()) return false;
		
		RobotInfo[] hostiles = rc.senseHostileRobots(here, mySensorRadiusSquared);
		boolean mustRetreat = false;
		for (RobotInfo hostile : hostiles) {
			RobotType hostileType = hostile.type;
			if (!hostileType.canAttack()) {
				continue;				
			}
			int distSq = here.distanceSquaredTo(hostile.location);
			if (distSq <= Math.max(hostileType.attackRadiusSquared, 8)) {
				mustRetreat = true;
				if (hostileType == RobotType.TURRET && distSq >= 36) {
					Debug.indicate("safebug", 1, "dangerMemory[" + dangerMemoryPointer + "] = " + here);
					dangerMemory[dangerMemoryPointer] = here;
					dangerMemoryPointer = (dangerMemoryPointer + 1) % DANGER_MEMORY_LENGTH;
				}
				break;
			}
		}
		
		if (mustRetreat) {
			Direction[] dirs = Direction.values();		
			MapLocation[] dirLocs = new MapLocation[8];
			int[] penalties = new int[8];
			boolean[] canMove = new boolean[8];
			for (int d = 0; d < 8; ++d) {
				dirLocs[d] = here.add(dirs[d]);
				canMove[d] = rc.canMove(dirs[d]);
			}
			
			for (RobotInfo hostile : hostiles) {
				RobotType hostileType = hostile.type;
				if (!hostileType.canAttack()) {
					continue;				
				}
				MapLocation hostileLoc = hostile.location;
				int hostileRange = Math.max(hostileType.attackRadiusSquared, 8);
				for (int d = 0; d < 8; ++d) {
					if (canMove[d] && hostileLoc.distanceSquaredTo(dirLocs[d]) <= hostileRange) {
						penalties[d]++;
					}
				}
			}

			Direction bestDir = null;
			int minPenalty = 999999;
			for (int d = 0; d < 8; ++d) {
				if (canMove[d] && penalties[d] < minPenalty) {
					minPenalty = penalties[d];
					bestDir = dirs[d];
				}
			}
			if (bestDir != null) {
				rc.move(bestDir);
				return true;
			}
		}
		
		return false;
	}
	
	private static void explore() throws GameActionException {
		if (finishedExploring) return;
		
		Debug.indicate("explore", 0, "exploreDest = " + exploreDest);
			
		if (exploreDest != null && 
				(here.equals(exploreDest) || 
				here.isAdjacentTo(exploreDest) && rc.senseRobotAtLocation(exploreDest) != null)) {
			// we reached the exploreDest
			exploredGrid[exploreDestGridX][exploreDestGridY] = true;
			//Debug.indicate("explore", 2, String.format("set exploredGrid[%d][%d] = true", exploreDestGridX, exploreDestGridY));
			exploreDest = null; // pick a new explore dest
		}
		
		if (exploreDest == null || MapEdges.isOffMap(exploreDest)) {
			pickNewExploreDest();
			Debug.indicate("explore", 1, "picked new exploreDest = " + exploreDest);
			return;
		}

		
		Debug.indicateDot("explore", exploreDest, 0, 255, 0);
		if (rc.isCoreReady()) {
			Debug.indicate("explore", 1, "going to exploreDest");
			Nav.goToBug(exploreDest/*, checkWhichSquaresAreSafe()*/);
		}
	}
}
