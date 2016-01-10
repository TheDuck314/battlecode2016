package ttm_dense_9;

import battlecode.common.*;

public class BotArchon extends Globals {
	
	
	public static void loop() throws GameActionException {
		FastMath.initRand(rc);
		initArchons();
		while (true) {
			try {
				numTurns += 1;
				Globals.update();
			    turn();
			} catch (Exception e) {
				e.printStackTrace();
			}
			Clock.yield();
		}
	}
	
	public static int numTurns = 0;
	
	private static void turn() throws GameActionException {
		exploreForNeutralsAndParts();
		trySpawn();
		tryRepairAlly();
		tryConvertNeutrals();
	}
	
	public static int nArchons = 0;
	public static MapLocation[] archonsLoc = new MapLocation[10];
	public static int[] archonsId = new int[10];
	public static int archonOrder = 0;
	
	public static MapLocation rallyPoint = null;
	
	public static void initArchons() throws GameActionException {
		Globals.update();
		archonsLoc[nArchons] = here;
		archonsId[nArchons] = myID;
		nArchons += 1;
		Messages.sendInitialArchonLocation();
		Clock.yield();
		numTurns += 1;
		Signal[] signals = rc.emptySignalQueue();
		for (Signal sig : signals) {
			if (sig.getTeam() != us) continue;
			int[] data = sig.getMessage();
			if (data != null) {
				if (data[0] == 0) {
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
	
	private static void exploreForNeutralsAndParts() throws GameActionException {
		if (!rc.isCoreReady()) return;
		
//		MapLocation[] nearbyLocs = MapLocation.getAllMapLocationsWithinRadiusSq(here, RobotType.ARCHON.sensorRadiusSquared);
		
		MapLocation bestLoc = null;
//		int bestDistSq = Integer.MAX_VALUE;
//		for (MapLocation loc : nearbyLocs) {
//			double parts = rc.senseParts(loc);
//			if (parts == 0) {
//				RobotInfo info = rc.senseRobotAtLocation(loc);
//				if (info == null || info.team != Team.NEUTRAL) continue;
//			}
//			// there are either parts or a neutral at this location
//			int distSq = here.distanceSquaredTo(loc);
//			if (distSq < bestDistSq) {
//				bestDistSq = distSq;
//				bestLoc = loc;
//			}			
//		}
//		if (nTurret >= nTurretMax) {
//			int rdn = FastMath.rand256();
//			Direction dir = Direction.values()[rdn % 8];
//			if (Bug.tryMoveInDirection(dir)) {
//				return;
//			}
//		} else {
//			Direction dir = saferDir();
//			if (dir != null) {
//				rc.move(dir);
//				return;
//			}
//		}
//		if (bestLoc != null) {
//			DBug.goTo(bestLoc);
//		}
		if (numTurns <= 60 && rallyPoint != null && here.distanceSquaredTo(rallyPoint) > 8) {
			avoidEnemy();
			if (!rc.isCoreReady()) return;
			DBug.goTo(rallyPoint);
		} else {
			moveAround();
		}
	}
	
	private static void avoidEnemy() throws GameActionException {
		if (!rc.isCoreReady()) return;
		Direction escapeDir = avoidEnemyDirection();
		if (null != escapeDir) {
//			System.out.println("Try escape!");
			if (Bug.tryMoveInDirection(escapeDir)) {
//				System.out.println("escape!");
				return;
			}
		}
	}

	private static Direction avoidEnemyDirection() {
		Direction escapeDir = null;
		if (escapeDir == null) {
			RobotInfo[] enemies = rc.senseNearbyRobots(mySensorRadiusSquared, Team.ZOMBIE);
			for (RobotInfo e : enemies) {
//				 System.out.println("Got zombie in sight!");
				if (e.location.distanceSquaredTo(here) <= e.type.attackRadiusSquared) {
//					System.out.println("Got zombie in range!");
					escapeDir = e.location.directionTo(here);
				}
			}
		}
		if (escapeDir == null) {
			RobotInfo[] enemies = rc.senseNearbyRobots(mySensorRadiusSquared, them);
			for (RobotInfo e : enemies) {
//				System.out.println("Got enemy in sight!");
				if (e.location.distanceSquaredTo(here) <= e.type.attackRadiusSquared) {
//					System.out.println("Got enemy in range!");
					escapeDir = e.location.directionTo(here);
				}
			}
		}
		return escapeDir;
	}

	private static int nFriend = 0;
	
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
//		rc.setIndicatorLine(here, FastMath.addVec(friendVec, FastMath.addVec(here, FastMath.multiplyVec(-5,scoutVec))), 0, 255, 0);
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
			if (rubbles[i] >= GameConstants.RUBBLE_SLOW_THRESH) {
				scores[i] -= attacks[8] * 1000;
				scores[i] += 500;
			}
//			scores[i] += nfriends[i] * 50;
//			scores[i] += friends[i] * 5 + scouts[i];
//			scores[i] += archons[i] * 10;
			scores[i] -= locs[i].distanceSquaredTo(rallyPoint);
		}
		scores[8] += 128;
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
		nFriend = (int)nfriends[8];
		if (bestDir != null) {
			if (rc.canMove(bestDir)) {
				nFriend = (int)nfriends[bestI];
			}
			DBug.tryMoveClearDir(bestDir);
		} else if (rubbles[8] >= GameConstants.RUBBLE_SLOW_THRESH) {
			rc.clearRubble(Direction.NONE);
		}
		return;
	}
	
	private static int spawnCount = 0;
	
	private static void trySpawn() throws GameActionException {
		if (!rc.isCoreReady()) return;
		
		rc.setIndicatorString(2, "trySpawn: turn " + rc.getRoundNum());
		
		RobotType spawnType = ((spawnCount - archonOrder - 0) % 5 == 0 ? RobotType.SCOUT : RobotType.TURRET);

		if (!rc.hasBuildRequirements(spawnType)) return;

		// scouts can probably have different spawning conditions
		double parts = rc.getTeamParts();
		if (nFriend <= 1 || parts > 250 || parts > 125 + FastMath.rand256()) {
			
			RobotInfo[] infos = rc.senseNearbyRobots(mySensorRadiusSquared, us);
			
			int nscout = 0;
			int nturrets = 0;
			int nsoldies = 0;
			
			for (RobotInfo f : infos) {
				switch (f.type) {
				case SCOUT:
					nscout += 1;
					break;
				case SOLDIER:
					nsoldies += 1;
					break;
				case TTM:
				case TURRET:
					nturrets += 1;
					break;
				default:
				}
			}
			
			spawnType = RobotType.TURRET;
			if (nsoldies < 12 && rc.getRobotCount() < 15) {
				spawnType = RobotType.SOLDIER;
			}
			if (nturrets > 1 && (nscout == 0 || (spawnCount - archonOrder - 0) % 5 == 0)) {
				spawnType = RobotType.SCOUT;
			}
			
			for (Direction dir : Direction.values()) {
				if (rc.canBuild(dir, spawnType)) {
					rc.build(dir, spawnType);
					++spawnCount;
					return;
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
			rc.setIndicatorDot(bestLoc, 255, 0, 0);
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
}
