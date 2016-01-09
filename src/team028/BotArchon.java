package team028;

import battlecode.common.*;

public class BotArchon extends Globals {
	private static MapLocationHashSet knownZombieDens = new MapLocationHashSet();
	private static MapLocation[] denAttackQueue = new MapLocation[1000];
	private static int denAttackQueueHead = 0;
	private static int denAttackQueueTail = 0;

	private static int spawnCount = 0;

	private static int lastArchonLocationMessageRound = 0;

	private static int nArchons = 0;
	private static MapLocation[] archonsLoc = new MapLocation[10];
	private static int[] archonsId = new int[10];
	private static int archonOrder = 0;
	private static MapLocation rallyPoint = null;
	
	public static void loop() throws GameActionException {
		Debug.init("heal");
		FastMath.initRand(rc);
		initArchons();

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
		
		moveAround();
		//exploreForNeutralsAndParts();	
	}
	
	private static int nTurret = 0;
	
	public static MapLocation dangerousLoc = null;
	public static int dangerousTurn = 0;
	public static double myHealth;

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
		boolean[] oddPos = new boolean[9];
		double[] rubbles = new double[9];
		double[] attacks = new double[9];
		double[] nturrets = new double[9];
		double[] turrets = new double[9];
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
			oddPos[i] = (locs[i].x + locs[i].y) % 2 != 0;
			rubbles[i] = rc.senseRubble(locs[i]);
		}
		RobotInfo[] infos;
		infos = rc.senseHostileRobots(here, mySensorRadiusSquared);
		for (RobotInfo e : infos) {
			for (int i = 0; i < 9; ++i) {
				if (e.location.distanceSquaredTo(locs[i]) <= e.type.attackRadiusSquared) {
					attacks[i] += e.attackPower;
				}
			}
		}
		infos = rc.senseNearbyRobots(mySensorRadiusSquared, us);
		MapLocation turretVec = new MapLocation(0,0);
		MapLocation scoutVec = new MapLocation(0,0);
		MapLocation archonVec = new MapLocation(0,0);
		for (RobotInfo f : infos) {
			if (f.ID == myID) {
				continue;
			}
			switch (f.type) {
			case ARCHON:
				if (here.distanceSquaredTo(f.location) < 4) {
					archonVec = archonVec.add(here.directionTo(f.location));
				}
				break;
			case TURRET:
				for (int i = 0; i < 9; ++i) {
					if (f.location.distanceSquaredTo(locs[i]) < 9) {
						nturrets[i] += 1;
					}
				}
				turretVec = turretVec.add(here.directionTo(f.location));
				break;
			case SCOUT:
				scoutVec = scoutVec.add(here.directionTo(f.location));
				break;
			default:
			}
		}
//		rc.setIndicatorLine(here, FastMath.addVec(turretVec, FastMath.addVec(here, FastMath.multiplyVec(-5,scoutVec))), 0, 255, 0);
		for (int i = 0; i < 9; ++i) {
			turrets[i] = FastMath.dotVec(dirs[i], turretVec);
			scouts[i] = FastMath.dotVec(dirs[i], scoutVec);
			archons[i] = FastMath.dotVec(dirs[i], archonVec);
		}
		double[] scores = new double[9];
		for (int i = 0; i < 9; ++i) {
			scores[i] = -attacks[i] * 1000;
			if (locs[i] == dangerousLoc) {
				scores[i] -= 2000;
			}
			if (rubbles[i] >= GameConstants.RUBBLE_SLOW_THRESH) {
				scores[i] -= attacks[8] * 1000;
				scores[i] += 500;
			}
//			if (oddPos[i]) {
//				scores[i] += 100;
//			}
//			scores[i] += nturrets[i] * 50;
//			scores[i] += turrets[i] + scouts[i];
//			scores[i] -= archons[i] * 50;
		}
		scores[8] += 500;
		for (int i = 0; i < 8; ++i) {
			if (rubbles[i] < GameConstants.RUBBLE_SLOW_THRESH && !cmoves[i]) {
				scores[i] = -100000;
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
		nTurret = (int)nturrets[8];
		if (bestDir != null) {
			if (rc.canMove(bestDir)) {
				nTurret = (int)nturrets[bestI];
			}
			DBug.tryMoveClearDir(bestDir);
		} else if (rubbles[8] >= GameConstants.RUBBLE_SLOW_THRESH) {
			rc.clearRubble(Direction.NONE);
		}
		return;
	}
	
	private static void trySendArchonLocationMessage() throws GameActionException {
		if (lastArchonLocationMessageRound < rc.getRoundNum() - 40) {
			Messages.sendArchonLocation(here, MapEdges.maxBroadcastDistSq());
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

	private static void trySpawn() throws GameActionException {
		if (!rc.isCoreReady()) return;

		RobotType spawnType = (spawnCount % 10 == (2 + archonOrder) % 10 ? RobotType.SCOUT : RobotType.SOLDIER);

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
