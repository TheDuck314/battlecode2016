package team028;

import battlecode.common.*;

public class Radar extends Globals {	
	public static MapLocation[] enemyTurretLocationById = new MapLocation[32001];
	public static int[] enemyTurretIds = new int[1000];
	public static int numEnemyTurrets = 0;
	
	public static void addEnemyTurret(int id, MapLocation loc) {
		if (enemyTurretLocationById[id] == null) {
			enemyTurretIds[numEnemyTurrets++] = id;
		}
		enemyTurretLocationById[id] = loc;
	}
	
	public static MapLocation findClosestEnemyTurret() {
		MapLocation ret = null;
		int bestDistSq = Integer.MAX_VALUE;
		for (int i = 0; i < numEnemyTurrets; ++i) {
			MapLocation turretLoc = enemyTurretLocationById[enemyTurretIds[i]];
			int distSq = here.distanceSquaredTo(turretLoc);
			if (distSq < bestDistSq) {
				bestDistSq = distSq;
				ret = turretLoc;
			}
		}
		return ret;
	}
	
	public static boolean turretIsKnown(int id, MapLocation loc) {
		MapLocation storedLoc = enemyTurretLocationById[id];
		if (storedLoc == null) return false;
		return storedLoc.equals(loc);
	}
	
	
	public static FastRobotInfo[] enemyCache = new FastRobotInfo[1000];
	public static int numCachedEnemies = 0;
	
	public static void addEnemyToCache(FastRobotInfo info) {
		enemyCache[numCachedEnemies++] = info;
	}
	
	public static void clearEnemyCache() {
		enemyCache = new FastRobotInfo[1000];
		numCachedEnemies = 0;
	}
}
