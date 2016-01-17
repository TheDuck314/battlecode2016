package more_soldier_into_turret16;

import battlecode.common.*;

public class Messages extends Globals {
	
	private static int currentMsgDebugIndicatorLineNumber = 2;
	private static int msgDILN() {
		currentMsgDebugIndicatorLineNumber += 1;
		currentMsgDebugIndicatorLineNumber %= 3;
		return currentMsgDebugIndicatorLineNumber;
	}
	
	public static final int CHANNEL_MASK = 0xf0000000;
	public static final int CHANNEL_MASK_INVERSE = ~CHANNEL_MASK;
	public static final int CHANNEL_TURRET_TARGET = 0x10000000;
	public static final int CHANNEL_ZOMBIE_DEN = 0x20000000;
	public static final int CHANNEL_FOUND_PARTS = 0x30000000;
	public static final int CHANNEL_ENEMY_TURRET_WARNING = 0x40000000;
	public static final int CHANNEL_UNPAIRED_SCOUT_REPORT = 0x50000000;
	public static final int CHANNEL_DEN_ATTACK_COMMAND = 0x60000000;
	public static final int CHANNEL_ZOMBIE_DEN_LIST = 0x70000000;
	public static final int CHANNEL_PART_REGIONS = 0x80000000;
	
	public static final int CHANNEL_ARCHON_LOCATION = 0xa0000000;
	public static final int CHANNEL_RADAR = 0xb0000000;
	public static final int CHANNEL_FOUND_NEUTRAL = 0xc0000000;
	public static final int CHANNEL_TURRET_OWNERSHIP = 0xd0000000;
	public static final int CHANNEL_MAP_EDGES = 0xe0000000;
	
	// used by CHANNEL_ENEMY_TURRET_WARNING
	public static final int ENEMY_TURRET_MISSING_VALUE = 0xffffffff;

	// used by CHANNEL_ZOMBIE_DEN
	public static final int ZOMBIE_DEN_DESTROYED_FLAG = 0x00000001;
	
	public static int intFromMapLocation(MapLocation loc) {
		return (loc.x << 10) | (loc.y);
	}
	
	public static MapLocation mapLocationFromInt(int data) {
		int x = ((data & 0xffc00) >>> 10);
		int y = (data & 0x003ff);
		return new MapLocation(x, y);
	}

	private static void sendMapLocation(int channel, MapLocation loc, int radiusSq) throws GameActionException {
		rc.broadcastMessageSignal(channel, intFromMapLocation(loc), radiusSq);
	}
	
	private static MapLocation parseMapLocation(int[] data) {
		return mapLocationFromInt(data[1]);
	}
	
	private static void sendInt(int channel, int data, int radiusSq) throws GameActionException {
		rc.broadcastMessageSignal(channel, data, radiusSq);
	}
	
	private static int parseInt(int[] data) {
		return data[1];
	}
	
	public static void sendTurretTarget(MapLocation loc, int radiusSq) throws GameActionException {
		sendMapLocation(CHANNEL_TURRET_TARGET, loc, radiusSq);
//		Debug.indicate("msg", msgDILN(), "sendTurretTarget " + radiusSq);
	}
	
	public static MapLocation parseTurretTarget(int[] data) {
		return parseMapLocation(data);
	}

	public static void sendZombieDenLocation(MapLocation loc, int radiusSq) throws GameActionException {
		rc.broadcastMessageSignal(CHANNEL_ZOMBIE_DEN, intFromMapLocation(loc), radiusSq);
//		Debug.indicate("msg", msgDILN(), "sendZombieDenLocation " + radiusSq);
	}
	
	public static void sendZombieDenDestroyed(MapLocation loc, int radiusSq) throws GameActionException {
		rc.broadcastMessageSignal(CHANNEL_ZOMBIE_DEN | ZOMBIE_DEN_DESTROYED_FLAG, 
				intFromMapLocation(loc), radiusSq);
//		Debug.indicate("msg", msgDILN(), "sendZombieDenDestroyed " + radiusSq);
	}
	
	public static boolean parseZombieDenWasDestroyed(int[] data) {
		return (data[0] & ZOMBIE_DEN_DESTROYED_FLAG) != 0;
	}
	
	public static MapLocation parseZombieDenLocation(int[] data) {
		return mapLocationFromInt(data[1]);
	}
	
	public static void sendUpToThreeZombieDens(MapLocation[] locations, int numLocations, int radiusSq) throws GameActionException {
		if (numLocations > 3) numLocations = 3;
		long data = 0;
		for (int i = 0; i < numLocations; ++i) {
			data *= 200;
			data += 100 + locations[i].x - here.x;
			data *= 200;
			data += 100 + locations[i].y - here.y;
		}
		int data0 = (int)(data >> 32);
		int data1 = (int)(data & 0x00000000ffffffffL);
		rc.broadcastMessageSignal(CHANNEL_ZOMBIE_DEN_LIST | data0, data1, radiusSq);
//		Debug.indicate("msg", msgDILN(), "sendUpToThreeZombieDens " + radiusSq);
	}
	
	// locationsOut should have length at least three.
	// It will be filled with the parsed locations.
	// The return value is the number of locations parsed.
	public static int parseUpToThreeZombieDens(int[] intData, MapLocation origin, MapLocation[] locationsOut) {
		long data = (((long)(CHANNEL_ZOMBIE_DEN_LIST ^ intData[0])) << 32) 
				| (((long)intData[1]) & 0x00000000ffffffffL);

		int numLocations = 0;
		while (data != 0) {
			int y = (int)(data % 200) + origin.y - 100;
			data /= 200;
			int x = (int)(data % 200) + origin.x - 100;
			data /= 200;
			locationsOut[numLocations] = new MapLocation(x, y);
			numLocations += 1;
		}
		return numLocations;
	}
	

	
	public static void sendDenAttackCommand(MapLocation loc, int radiusSq) throws GameActionException {
		sendMapLocation(CHANNEL_DEN_ATTACK_COMMAND, loc, radiusSq);
//		Debug.indicate("msg", msgDILN(), "sendUpToThreeZombieDens " + radiusSq);
	}
	
	public static MapLocation parseDenAttackCommand(int[] data) {
		return parseMapLocation(data);
	}
	
	public static void sendArchonLocation(MapLocation loc, int radiusSq) throws GameActionException {
		sendMapLocation(CHANNEL_ARCHON_LOCATION, loc, radiusSq);
//		Debug.indicate("msg", msgDILN(), "sendArchonLocation " + radiusSq);
	}
	
	public static MapLocation parseArchonLocation(int[] data) {
		return parseMapLocation(data);
	}

	public static void sendPartsLocation(MapLocation loc, int numParts, int radiusSq) throws GameActionException {
		int data0 = CHANNEL_FOUND_PARTS | (CHANNEL_MASK_INVERSE & numParts);
		int data1 = intFromMapLocation(loc);
		rc.broadcastMessageSignal(data0, data1, radiusSq);
//		Debug.indicate("msg", msgDILN(), "sendPartsLocation " + radiusSq);
	}
	
	// outPartsLoc is set to the MapLocation of the found parts
	// the returned value is the number of parts at that location
	public static PartsLocation parsePartsLocation(int[] data) {
		MapLocation loc = mapLocationFromInt(data[1]);
		int numParts = data[0] & CHANNEL_MASK_INVERSE;
		return new PartsLocation(loc, numParts);
	}
	
	public static void sendNeutralLocation(MapLocation loc, int radiusSq) throws GameActionException {
		sendMapLocation(CHANNEL_FOUND_NEUTRAL, loc, radiusSq);
//		Debug.indicate("msg", msgDILN(), "sendNeutralLocation " + radiusSq);
	}
	
	public static MapLocation parseNeutralLocation(int[] data) {
		return parseMapLocation(data);
	}
	
	private static int compressMapEdges(int original) {
		if (original == MapEdges.UNKNOWN) {
			return 127;
		} else {
			// max map size is 100 < 110
			return (original + 33000) % 110;
		}
	}
	
	private static int parseMapEdgesMax(int compressed, int reference) {
		if (compressed == 127) return MapEdges.UNKNOWN;
		int original = (reference / 110 + 1) * 110 + compressed;
		while (original >= reference) {
			original -= 110;
		}
		return original + 110;
	}
	
	private static int parseMapEdgesMin(int compressed, int reference) {
		if (compressed == 127) return MapEdges.UNKNOWN;
		int original = (reference / 110 + 1) * 110 + compressed;
		while (original > reference) {
			original -= 110;
		}
		return original;
	}
	
	public static void sendKnownMapEdges(int radiusSq) throws GameActionException {
		int minX = compressMapEdges(MapEdges.minX);
		int maxX = compressMapEdges(MapEdges.maxX);
		int minY = compressMapEdges(MapEdges.minY);
		int maxY = compressMapEdges(MapEdges.maxY);
		int value = minX << 24 | maxX << 16 | minY << 8 | maxY;
		sendInt(CHANNEL_MAP_EDGES, value, radiusSq);
//		Debug.indicate("edges", 1, "send: value=" + Integer.toHexString(value) + " MinX=" + MapEdges.minX + " MaxX=" + MapEdges.maxX + " MinY=" + MapEdges.minY + " MaxY=" + MapEdges.maxY);
//		Debug.indicate("msg", msgDILN(), "sendKnownMapEdges " + radiusSq);
	}
	
	public static void processMapEdges(int[] data) {
		int value = parseInt(data);
		int maxY = value & 0x000000ff;
		value >>>= 8;
		int minY = value & 0x000000ff;
		value >>>= 8;
		int maxX = value & 0x000000ff;
		value >>>= 8;
		int minX = value & 0x000000ff;
		if (MapEdges.minX == MapEdges.UNKNOWN) MapEdges.minX = parseMapEdgesMin(minX, here.x);
		if (MapEdges.maxX == MapEdges.UNKNOWN) MapEdges.maxX = parseMapEdgesMax(maxX, here.x);
		if (MapEdges.minY == MapEdges.UNKNOWN) MapEdges.minY = parseMapEdgesMin(minY, here.y);
		if (MapEdges.maxY == MapEdges.UNKNOWN) MapEdges.maxY = parseMapEdgesMax(maxY, here.y);
//		Debug.indicate("edges", 2, "receive: value=" + Integer.toHexString(parseInt(data)) + " MinX=" + MapEdges.minX + " MaxX=" + MapEdges.maxX + " MinY=" + MapEdges.minY + " MaxY=" + MapEdges.maxY);
	}
	
	public static void sendEnemyTurretWarning(int id, MapLocation loc, int radiusSq) throws GameActionException {
		int data0 = id;
		int data1 = intFromMapLocation(loc);
		rc.broadcastMessageSignal(CHANNEL_ENEMY_TURRET_WARNING | data0, data1, radiusSq);
//		Debug.indicate("msg", msgDILN(), "sendEnemyTurretWarning " + radiusSq);
	}
	
	public static void sendEnemyTurretMissing(int id, int radiusSq) throws GameActionException {
		int data0 = id;
		int data1 = ENEMY_TURRET_MISSING_VALUE;
		rc.broadcastMessageSignal(CHANNEL_ENEMY_TURRET_WARNING | data0, data1, radiusSq);
//		Debug.indicate("msg", msgDILN(), "sendEnemyTurretMissing " + radiusSq);
	}
	
	public static void processEnemyTurretWarning(int[] data) {
		int id = data[0] ^ CHANNEL_ENEMY_TURRET_WARNING;
		int locInt = data[1];
		if (locInt == ENEMY_TURRET_MISSING_VALUE) {
			Radar.removeEnemyTurret(id);
		} else {
			MapLocation loc = mapLocationFromInt(data[1]);
			Radar.addEnemyTurret(id, loc);
		}
	}
	
	public static void sendRadarData(RobotInfo[] infos, int size, int radiusSq) throws GameActionException {
		long data = 0;
		size = Math.min(size, infos.length);
		size = Math.min(size, 5);
		for (int i = 0; i < size; ++i) {
			RobotInfo info = infos[i];
			data <<= 12;
			data |= ((1 + info.type.ordinal()) << 8)
					| ((info.location.x - here.x + 8) << 4)
					| (info.location.y - here.y + 8);
		}
		int data0 = (int)(data >> 32);
		int data1 = (int)(data & 0x00000000ffffffffL);
		rc.broadcastMessageSignal(CHANNEL_RADAR | (CHANNEL_MASK_INVERSE & data0), data1, radiusSq);
//		Debug.indicate("msg", msgDILN(), "sendRadarData " + radiusSq);
	}
	
	public static void addRadarDataToEnemyCache(int[] intData, MapLocation origin, int maxDistSq) {
		long data = (((long)(CHANNEL_RADAR ^ intData[0])) << 32) 
				| (((long)intData[1]) & 0x00000000ffffffffL);
		
		int round = rc.getRoundNum();
		RobotType[] types = RobotType.values();		
		while (data != 0) {
			int y = origin.y - 8 + (int)(data & 0xfL);
			int x = origin.x - 8 + (int)((data >> 4) & 0xfL);
			MapLocation loc = new MapLocation(x, y);
			if (here.distanceSquaredTo(loc) <= maxDistSq) {
				int typeOrdinal = (int)((data >> 8) & 0xfL) - 1;		
				RobotType type = types[typeOrdinal];
				FastRobotInfo info = new FastRobotInfo(loc, type, round);
				Radar.addEnemyToCache(info);
			}
			data >>= 12;
		}
	}
	
	public static MapLocation getClosestRadarHit(int[] intData, MapLocation origin) {
		MapLocation closest = null;
		int bestDistSq = Integer.MAX_VALUE;
		
		long data = (((long)(CHANNEL_RADAR ^ intData[0])) << 32) 
				| (((long)intData[1]) & 0x00000000ffffffffL);
		
		while (data != 0) {
			int y = origin.y - 8 + (int)(data & 0xfL);
			int x = origin.x - 8 + (int)((data >> 4) & 0xfL);
			MapLocation loc = new MapLocation(x, y);
			int distSq = here.distanceSquaredTo(loc);
			if (distSq < bestDistSq) {
				bestDistSq = distSq;
				closest = loc;
			}
			data >>= 12;
		}
		
		return closest;
	}
	
	public static void sendTurretOwnershipClaim(int turretId, int radiusSq) throws GameActionException {
		sendInt(CHANNEL_TURRET_OWNERSHIP, turretId, radiusSq);
//		Debug.indicate("msg", msgDILN(), "sendTurretOwnershipClaim " + radiusSq);
	}
	
	public static int parseTurretOwnershipClaim(int[] data) {
		return parseInt(data);
	}
	
	public static void sendUnpairedScoutReport(int radiusSq) throws GameActionException {
		rc.broadcastMessageSignal(CHANNEL_UNPAIRED_SCOUT_REPORT, 0, radiusSq);
//		Debug.indicate("msg", msgDILN(), "sendUnpairedScoutReport " + radiusSq);
	}	
	
	public static void sendPartRegion(MapLocation partsCenter, int totalParts, int avgTurnsToUncover,
			int radiusSq) throws GameActionException {
		int locInt = intFromMapLocation(partsCenter);
		if (avgTurnsToUncover > 2047) avgTurnsToUncover = 2047;
		int data1 = (avgTurnsToUncover << 20) | locInt;
		int data0 = totalParts;
		rc.broadcastMessageSignal(CHANNEL_PART_REGIONS | data0, data1, radiusSq);
	}
	
	public static PartRegion parsePartRegion(int[] data) {
		int totalParts = data[0] & CHANNEL_MASK_INVERSE;
		int locInt = data[1] & 0x000fffff;
		int avgTurnsToUncover = (data[1] & 0xfff00000) >>> 20;
		return new PartRegion(totalParts, avgTurnsToUncover, mapLocationFromInt(locInt));
	}
}
