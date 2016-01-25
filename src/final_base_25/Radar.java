package final_base_25;

import scala.Int;
import battlecode.common.*;

public class Radar extends Globals {	
//	public static MapLocation[] enemyTurretLocationById = new MapLocation[32001];
//	public static boolean[] haveSeenTurretId = new boolean[32001];
//	public static int[] enemyTurretIds = new int[1000];
//	public static int numEnemyTurrets = 0;
	
//	public static MapLocation closestEnemyTurretLocation = null;

//	public static void addEnemyTurret(int id, MapLocation loc) {
//		if (!haveSeenTurretId[id]) {
//			enemyTurretIds[numEnemyTurrets++] = id;
//			haveSeenTurretId[id] = true;
//		}
//		enemyTurretLocationById[id] = loc;
//	}
//	
//	public static void removeEnemyTurret(int id) {
//		enemyTurretLocationById[id] = null;
//	}
	
//	public static FastTurretInfo findClosestEnemyTurret() {
//		FastTurretInfo ret = null;
//		int bestDistSq = Integer.MAX_VALUE;
//		for (int i = 0; i < numEnemyTurrets; ++i) {
//			int turretId = enemyTurretIds[i];
//			MapLocation turretLoc = enemyTurretLocationById[turretId];
//			if (turretLoc != null) {
//				int distSq = here.distanceSquaredTo(turretLoc);
//				if (distSq < bestDistSq) {
//					bestDistSq = distSq;
//					ret = new FastTurretInfo(turretId, turretLoc);
//				}
//			}
//		}
//		return ret;
//	}
//	
//	public static void updateClosestEnemyTurretLocation() {
//		closestEnemyTurretLocation = null;
//		int bestDistSq = Integer.MAX_VALUE;
//		for (int i = 0; i < numEnemyTurrets; ++i) {
//			int turretId = enemyTurretIds[i];
//			MapLocation turretLoc = enemyTurretLocationById[turretId];
//			if (turretLoc != null) {
//				int distSq = here.distanceSquaredTo(turretLoc);
//				if (distSq < bestDistSq) {
//					bestDistSq = distSq;
//					closestEnemyTurretLocation = turretLoc;
//				}
//			}
//		}		
//	}
//	
//	public static void removeDistantEnemyTurrets(int radiusSq) {
//		for (int i = 0; i < numEnemyTurrets; ++i) {
//			int turretId = enemyTurretIds[i];
//			MapLocation turretLoc = enemyTurretLocationById[turretId];
//			if (turretLoc != null) {
//				int distSq = here.distanceSquaredTo(turretLoc);
//				if (distSq > radiusSq) {
//					removeEnemyTurret(turretId);
//				}
//			}
//		}
//	}
	
//	public static boolean turretIsKnown(int id, MapLocation loc) {
//		MapLocation storedLoc = enemyTurretLocationById[id];
//		if (storedLoc == null) return false;
//		return storedLoc.equals(loc);
//	}
	
	public static int[] theirArchonIdList = new int[30];
	public static int theirArchonIdListLength = 0;
	public static int[] ourArchonIdList = new int[30];
	public static int ourArchonIdListLength = 0;
	
	public static int[] theirTurretIdList = new int[1000];
	public static int theirTurretIdListLength = 0;
	
	public static BigRobotInfo[] bigRobotInfoById = new BigRobotInfo[32001];
	
	public static BigRobotInfo addRobot(int id, RobotType type, Team team, MapLocation loc, int round) {
//		Debug.indicate("archon", 1, "Radar.addRobot id=" + id + " type=" + type + " team=" + team + " loc=" + loc + " round=" + round);
		if (round < 0) round = 0;
		BigRobotInfo bri = bigRobotInfoById[id];
		if (bri == null) {
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
				case TURRET:
					theirTurretIdList[theirTurretIdListLength] = id;
					theirTurretIdListLength += 1;
					break;
				default:
				}
			}
//			Debug.println("archon", "theirArchonIdListLength=" + theirArchonIdListLength + " Radar.addRobot id=" + id + " type=" + type + " team=" + team + " loc=" + loc + " round=" + round);
			bigRobotInfoById[id] = new BigRobotInfo(id, type, team, loc, round);
			return bigRobotInfoById[id];
		}
		if (bri.round >= round) {
			return null;
		}
		if (loc == null) {
			// loc == null means the robot disappeared from where it was discovered,
			// in which case round = 1 + (the round it was discovered)
			if (bri.location == null) return null;
			bri.location = null;
			bri.round = round;
			return bri;
		} else {
			if (bri.round > round - Globals.rebroadCastUpdateInterval && loc.equals(bri.location)) {
				return null;
			}
			bri.location = loc;
			bri.round = round;
			// Debug.indicateAppend("archon", 2, "f");
			return bri;
		}
	}
	
	public static BigRobotInfo addRobot(int id, Team team, MapLocation loc, int round) {
//		Debug.indicate("archon", 1, "Radar.addRobot id=" + id + " team=" + team + " loc=" + loc + " round=" + round);
		if (round < 0) round = 0;
		// We need to know this robot id already.
		if (bigRobotInfoById[id] == null) {
			return null;
		}
		BigRobotInfo bri = bigRobotInfoById[id];
		if (loc.equals(bri.location) && bri.round > round - Globals.rebroadCastUpdateInterval || bri.round >= round) {
			return null;
		}
		bri.location = loc;
		bri.round = round;
//		Debug.indicateAppend("archon", 2, "e");
		return bigRobotInfoById[id];
	}
	
	public static BigRobotInfo closestEnemyTurretInfo = null;
	public static BigRobotInfo closestEnemyArchonInfo = null;
	
	public static MapLocation closestEnemyTurretLocation = null;
	
	public static BigRobotInfo updateClosestEnemyArchonInfo() {
		BigRobotInfo bestInfo = null;
		double bestDistSq = Double.MAX_VALUE;
		int roundDelay = Int.MaxValue();
		int round = Globals.roundNum;
		for (int i = 0; i < theirArchonIdListLength; ++i) {
			BigRobotInfo bri = bigRobotInfoById[theirArchonIdList[i]];
			if (bri.location == null) continue;
			if (roundDelay <= Globals.infoOutOfDateIntervalArchon) {
				if (round - bri.round <= Globals.infoOutOfDateIntervalArchon) {
					int distSq = bri.location.distanceSquaredTo(here);
					if (distSq < bestDistSq) {
						bestInfo = bri;
						bestDistSq = distSq;
						roundDelay = round - bri.round;
					}
				}
			} else {
				if (round - bri.round < roundDelay) {
					bestInfo = bri;
					bestDistSq = bri.location.distanceSquaredTo(here);
					roundDelay = round - bri.round;
				}
			}
		}
		closestEnemyArchonInfo = bestInfo;
		return bestInfo;
	}

	public static BigRobotInfo updateClosestEnemyTurretInfo() {
		BigRobotInfo bestInfo = null;
		double bestDistSq = Double.MAX_VALUE;
		int roundDelay = Int.MaxValue();
		int round = Globals.roundNum;
		for (int i = 0; i < theirTurretIdListLength; ++i) {
			BigRobotInfo bri = bigRobotInfoById[theirTurretIdList[i]];
			if (bri.location == null) continue;
			int distSq = bri.location.distanceSquaredTo(here);
			if (distSq > Globals.infoOutOfRangeSqTurret) {
				bri.location = null;
				continue;
			}
			if (roundDelay <= Globals.infoOutOfDateIntervalTurret) {
				if (round - bri.round <= Globals.infoOutOfDateIntervalTurret) {
					if (distSq < bestDistSq) {
						bestInfo = bri;
						bestDistSq = distSq;
						roundDelay = round - bri.round;
					}
				}
			} else {
				if (round - bri.round < roundDelay) {
					bestInfo = bri;
					bestDistSq = distSq;
					roundDelay = round - bri.round;
				}
			}
		}
		closestEnemyTurretInfo = bestInfo;
		if (bestInfo != null && round - bestInfo.round <= Globals.infoOutOfDateIntervalTurret) {
			closestEnemyTurretLocation = bestInfo.location;
		} else {
			closestEnemyTurretLocation = null;
		}
		return bestInfo;
	}
	
	public static void indicateEnemyArchonLocation(int red, int green, int blue) {
		Debug.indicate("robotinfo", 2, "");
		for (int i = 0; i < Radar.theirArchonIdListLength; ++i) {
			BigRobotInfo bri = Radar.bigRobotInfoById[Radar.theirArchonIdList[i]];
			if (bri.location == null) continue;
			if (bri.round <= Globals.roundNum - Globals.infoOutOfDateIntervalArchon) continue;
			Debug.indicateLine("robotinfo", here, bri.location, red, green, blue);
			Debug.indicateAppend("robotinfo", 2, " id=" + bri.id + " loc=" + bri.location);
		}
	}
	
	public static void indicateEnemyTurretLocation(int red, int green, int blue) {
		Debug.indicate("robotinfo", 2, "");
		for (int i = 0; i < Radar.theirTurretIdListLength; ++i) {
			BigRobotInfo bri = Radar.bigRobotInfoById[Radar.theirTurretIdList[i]];
			if (bri.location == null) continue;
			Debug.indicateDot("robotinfo", bri.location, 200, 0, 0);
			if (bri.round <= Globals.roundNum - Globals.infoOutOfDateIntervalTurret) continue;
//			Debug.indicateLine("robotinfo", here, bri.location, red, green, blue);
			Debug.indicateDot("robotinfo", bri.location, red, green, blue);
//			Debug.indicateAppend("robotinfo", 2, " id=" + bri.id + " loc=" + bri.location);
		}
	}
	
	// store the index in enemyCache, but plus one
	public static int[][] haveSeenEnemyLoc = new int[100][100];
	public static FastRobotInfo[] enemyCache = new FastRobotInfo[1000];
	public static int numCachedEnemies = 0;
	
	public static boolean addEnemyToCache(FastRobotInfo info) {
		MapLocation loc = info.location;
		int x = loc.x % 100;
		int y = loc.y % 100;
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
