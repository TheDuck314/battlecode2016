package ttm_heal;

import battlecode.common.*;

public class BotScout extends Globals {
	public static void loop() {
		FastMath.initRand(rc);
		Debug.init("spotting");
		myHealth = rc.getHealth();
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

	private static int lastSignal = 3000;

	private static void signalAboutEnemies() throws GameActionException {
		lastSignal += 1;
		RobotInfo[] allies = rc.senseNearbyRobots(mySensorRadiusSquared, us);
		MapLocation[] turrets = new MapLocation[1000];
		int numTurrets = 0;
		for (RobotInfo ally : allies) {
			if (ally.type == RobotType.TURRET || ally.type == RobotType.TTM) {
				turrets[numTurrets++] = ally.location;
			}
		}

		RobotInfo[] enemies = rc.senseHostileRobots(here, mySensorRadiusSquared);
		MapLocation bestTarget = null;
		int bestScore = Integer.MIN_VALUE;
		for (RobotInfo enemy : enemies) {
			int score = 0;
			MapLocation enemyLoc = enemy.location;
			for (int i = 0; i < numTurrets; ++i) {
				int distSq = enemyLoc.distanceSquaredTo(turrets[i]);
				if (distSq > RobotType.TURRET.sensorRadiusSquared && distSq <= RobotType.TURRET.attackRadiusSquared) {
					++score;
					if (distSq <= enemy.type.attackRadiusSquared) {
						score += enemy.attackPower;
					}
				}
			}
			if (score > bestScore) {
				bestScore = score;
				bestTarget = enemyLoc;
			}
		}

		if (bestTarget != null) {
			Debug.indicate("spotting", 0, "spotting target at " + bestTarget);
			Messages.sendTurretTarget(bestTarget, 2*mySensorRadiusSquared);
			lastSignal = 0;
			return;
		}
	}

	private static Direction[] goodDir = { Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST };
	private static Direction[] badDir = { Direction.NORTH_EAST, Direction.SOUTH_EAST, Direction.NORTH_WEST, Direction.SOUTH_WEST };

	private static void tryMoveAround() throws GameActionException {
		if (!rc.isCoreReady()) return;
		int rdn = FastMath.rand256();
		for (int i = 0; i < 4; ++i) {
			Direction dir = goodDir[(rdn + i) % 4];
			if (DBug.tryMoveClearDir(dir)) {
				return;
			}
		}
		for (int i = 0; i < 4; ++i) {
			Direction dir = badDir[(rdn + i) % 4];
			if (DBug.tryMoveClearDir(dir)) {
				return;
			}
		}
	}

	private static Direction avoidEnemyDirection() {
		Direction escapeDir = null;
		if (escapeDir == null) {
			RobotInfo[] enemies = rc.senseNearbyRobots(mySensorRadiusSquared, Team.ZOMBIE);
			for (RobotInfo e : enemies) {
				//				System.out.println("Got zombie in sight!");
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

	private static int nTurret = 0;

	private static void countTurret() throws GameActionException {
		nTurret = 0;
		RobotInfo[] infos = rc.senseNearbyRobots(4, us);
		for (RobotInfo info : infos) {
			if (info.type == RobotType.TURRET) {
				nTurret += 1;
			}
		}
	}

	private static MapLocation turretArchonVec() {
		RobotInfo[] friend = rc.senseNearbyRobots(mySensorRadiusSquared, us);
		MapLocation vecSum = new MapLocation(0,0);
		for (RobotInfo ri : friend) {
			if (ri.type == RobotType.TURRET) {
				Direction dir = here.directionTo(ri.location);
				vecSum = vecSum.add(dir);
				continue;
			} else if (ri.type == RobotType.ARCHON) {
				Direction dir = here.directionTo(ri.location);
				vecSum = vecSum.add(dir);
				continue;
			}
		}
		return vecSum;
	}

	private static MapLocation scoutVec() {
		RobotInfo[] friend = rc.senseNearbyRobots(mySensorRadiusSquared, us);
		MapLocation vecSum = new MapLocation(0,0);
		for (RobotInfo ri : friend) {
			if (ri.type == RobotType.SCOUT) {
				Direction dir = here.directionTo(ri.location);
				vecSum = vecSum.add(dir);
				continue;
			}
		}
		return vecSum;
	}

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

	private static void turn() throws GameActionException {
		signalAboutEnemies();
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
		for (RobotInfo f : infos) {
			if (f.ID == myID) {
				continue;
			}
			switch (f.type) {
			case ARCHON:
			case TURRET:
				for (int i = 0; i < 9; ++i) {
					if (f.location.distanceSquaredTo(locs[i]) < 16) {
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
		}
		double[] scores = new double[9];
		for (int i = 0; i < 9; ++i) {
			scores[i] = -attacks[i] * 1000;
			if (locs[i] == dangerousLoc) {
				scores[i] -= 5000;
			}
			if (rubbles[i] >= GameConstants.RUBBLE_SLOW_THRESH) {
				scores[i] -= attacks[8] * 1000;
				scores[i] += 1000;
			}
			if (oddPos[i]) {
				scores[i] += 100;
			}
			if (nturrets[i] < 2) {
				scores[i] -= (1-nturrets[i]) * 50;
			}
			scores[i] += turrets[i] - scouts[i] * 8;
		}
		if (lastSignal > 5 || attacks[8] > 0) {
			scores[8] -= 100;
		} else {
			scores[8] += 200;
			rc.setIndicatorDot(here, 0, 0, 255);
		}
		for (int i = 0; i < 8; ++i) {
			if (rubbles[i] < GameConstants.RUBBLE_SLOW_THRESH && !cmoves[i]) {
				scores[i] = -100000;
			}
		}
		double bestScore = -100000;
		Direction bestDir = null;
		int rdn = FastMath.rand256();
		for (int i = 0; i < 9; ++i) {
			if (scores[i] <= -500) {
				rc.setIndicatorDot(locs[i], 255, 0, 0);
			}
			if (bestScore < scores[(rdn + i) % 9]) {
				bestDir = dirs[(rdn + i) % 9];
				bestScore = scores[(rdn + i) % 9];
			}
		}
		if (bestDir != null) {
			DBug.tryMoveClearDir(bestDir);
		} else if (rubbles[8] >= GameConstants.RUBBLE_SLOW_THRESH) {
			rc.clearRubble(Direction.NONE);
		}
		return;
	}
}
