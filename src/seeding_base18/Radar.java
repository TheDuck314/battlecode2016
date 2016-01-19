package seeding_base18;

import battlecode.common.*;

public class Radar extends Globals {	
	public static MapLocation[] enemyTurretLocationById = new MapLocation[32001];
	public static boolean[] haveSeenTurretId = new boolean[32001];
	public static int[] enemyTurretIds = new int[1000];
	public static int numEnemyTurrets = 0;
	
	public static void addEnemyTurret(int id, MapLocation loc) {
		if (!haveSeenTurretId[id]) {
			enemyTurretIds[numEnemyTurrets++] = id;
			haveSeenTurretId[id] = true;
		}
		enemyTurretLocationById[id] = loc;
	}
	
	public static void removeEnemyTurret(int id) {
		enemyTurretLocationById[id] = null;
	}
	
	public static FastTurretInfo findClosestEnemyTurret() {
		FastTurretInfo ret = null;
		int bestDistSq = Integer.MAX_VALUE;
		for (int i = 0; i < numEnemyTurrets; ++i) {
			int turretId = enemyTurretIds[i];
			MapLocation turretLoc = enemyTurretLocationById[turretId];
			if (turretLoc != null) {
				int distSq = here.distanceSquaredTo(turretLoc);
				if (distSq < bestDistSq) {
					bestDistSq = distSq;
					ret = new FastTurretInfo(turretId, turretLoc);
				}
			}
		}
		return ret;
	}
	
	public static void removeDistantEnemyTurrets(int radiusSq) {
		for (int i = 0; i < numEnemyTurrets; ++i) {
			int turretId = enemyTurretIds[i];
			MapLocation turretLoc = enemyTurretLocationById[turretId];
			if (turretLoc != null) {
				int distSq = here.distanceSquaredTo(turretLoc);
				if (distSq > radiusSq) {
					removeEnemyTurret(turretId);
				}
			}
		}
	}
	
	public static boolean turretIsKnown(int id, MapLocation loc) {
		MapLocation storedLoc = enemyTurretLocationById[id];
		if (storedLoc == null) return false;
		return storedLoc.equals(loc);
	}
	
	public static int[] theirArchonIdList = new int[30];
	public static int theirArchonIdListLength = 0;
	public static int[] ourArchonIdList = new int[30];
	public static int ourArchonIdListLength = 0;
	
	public static BigRobotInfo[] bigRobotInfoById = new BigRobotInfo[32001];
	
	public static BigRobotInfo addRobot(int id, RobotType type, Team team, MapLocation loc, int round) {
		Debug.indicate("archon", 1, "Radar.addRobot id=" + id + " type=" + type + " team=" + team + " loc=" + loc + " round=" + round);
		if (round < 0) round = 0;
		if (bigRobotInfoById[id] == null) {
			if (team == us) {
				switch (type) {
				case ARCHON:
					ourArchonIdList[ourArchonIdListLength] = id;
					ourArchonIdListLength += 1;
					break;
				default:
				}
			} else if (team == them){
				switch (type) {
				case ARCHON:
					theirArchonIdList[theirArchonIdListLength] = id;
					theirArchonIdListLength += 1;
					break;
				default:
				}
			}
			Debug.println("archon", "theirArchonIdListLength=" + theirArchonIdListLength + " Radar.addRobot id=" + id + " type=" + type + " team=" + team + " loc=" + loc + " round=" + round);
		}
		BigRobotInfo bri = bigRobotInfoById[id];
		if (bri != null && (loc.distanceSquaredTo(bri.location) <= 2 && bri.round > round - 100 || bri.round >= round)) {
			return null;
		}
		bigRobotInfoById[id] = new BigRobotInfo(id, loc, type, team, round);
		Debug.indicateAppend("archon", 2, "f");
		return bigRobotInfoById[id];
	}
	
	public static BigRobotInfo addRobot(int id, Team team, MapLocation loc, int round) {
		Debug.indicate("archon", 1, "Radar.addRobot id=" + id + " team=" + team + " loc=" + loc + " round=" + round);
		if (round < 0) round = 0;
		// We need to know this robot id already.
		if (bigRobotInfoById[id] == null) {
			return null;
		}
		BigRobotInfo bri = bigRobotInfoById[id];
		if (loc.distanceSquaredTo(bri.location) <= 2 && bri.round > round - 100 || bri.round >= round) {
			return null;
		}
		bigRobotInfoById[id] = new BigRobotInfo(id, loc, bri.type, team, round);
		Debug.indicateAppend("archon", 2, "e");
		return bigRobotInfoById[id];
	}
	
	// store the index in enemyCache, but plus one
	public static int[][] haveSeenEnemyLoc = new int[100][100];
	public static FastRobotInfo[] enemyCache = new FastRobotInfo[1000];
	public static int numCachedEnemies = 0;
	
	public static boolean addEnemyToCache(FastRobotInfo info) {
		MapLocation loc = info.location;
		int x = (loc.x + 32000) % 100;
		int y = (loc.y + 32000) % 100;
		int index = haveSeenEnemyLoc[x][y];
		if (index == 0) {
			haveSeenEnemyLoc[x][y] = numCachedEnemies + 1; // Note, we plus one to differ from zero
			enemyCache[numCachedEnemies++] = info;
		} else {
			if (enemyCache[index-1].type == info.type) return false;
			enemyCache[index-1] = info;
		}
		return true;
	}
	
	public static boolean addEnemyToCache(RobotInfo info) {
		return addEnemyToCache(new FastRobotInfo(info.location, info.type, rc.getRoundNum()));
	}
	
	public static void clearEnemyCache() {
		haveSeenEnemyLoc = new int[100][100];
		enemyCache = new FastRobotInfo[1000];
		numCachedEnemies = 0;
	}
}
