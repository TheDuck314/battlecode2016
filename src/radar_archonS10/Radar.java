package radar_archonS10;

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
