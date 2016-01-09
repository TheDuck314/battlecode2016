package supercowpowers_archonmove8;

import battlecode.common.*;

public class Messages extends Globals {
	public static final int CHANNEL_MASK = 0xf0000000;
	public static final int CHANNEL_MASK_INVERSE = ~CHANNEL_MASK;
	public static final int CHANNEL_TURRET_TARGET = 0x10000000;
	public static final int CHANNEL_ZOMBIE_DEN = 0x20000000;
	public static final int CHANNEL_FOUND_PARTS = 0x30000000;
	public static final int CHANNEL_ENEMY_TURRET_WARNING = 0x40000000;
	public static final int CHANNEL_ATTACK_TARGET = 0x90000000;
	public static final int CHANNEL_ARCHON_LOCATION = 0xa0000000;
	
	public static final int CHANNEL_MAP_MIN_X = 0x50000000;
	public static final int CHANNEL_MAP_MAX_X = 0x60000000;
	public static final int CHANNEL_MAP_MIN_Y = 0x70000000;
	public static final int CHANNEL_MAP_MAX_Y = 0x80000000;
	
	
	public static int intFromMapLocation(MapLocation loc) {
		return ((loc.x + 16000) << 16) | (loc.y + 16000);
	}
	
	public static MapLocation mapLocationFromInt(int data) {
		int x = ((data & 0xffff0000) >>> 16) - 16000;
		int y = (data & 0x0000ffff) - 16000;
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
	}
	
	public static MapLocation parseTurretTarget(int[] data) {
		return parseMapLocation(data);
	}

	public static void sendZombieDenLocation(MapLocation loc, int radiusSq) throws GameActionException {
		sendMapLocation(CHANNEL_ZOMBIE_DEN, loc, radiusSq);
	}
	
	public static MapLocation parseZombieDenLocation(int[] data) {
		return parseMapLocation(data);
	}
	
	public static void sendEnemyTurretWarning(MapLocation loc, int radiusSq) throws GameActionException {
		sendMapLocation(CHANNEL_ENEMY_TURRET_WARNING, loc, radiusSq);		
	}
	
	public static MapLocation parseEnemyTurretWarning(int[] data) {
		return parseMapLocation(data);
	}
	
	public static void sendAttackTarget(MapLocation loc, int radiusSq) throws GameActionException {
		sendMapLocation(CHANNEL_ATTACK_TARGET, loc, radiusSq);
	}
	
	public static MapLocation parseAttackTarget(int[] data) {
		return parseMapLocation(data);
	}
	
	public static void sendArchonLocation(MapLocation loc, int radiusSq) throws GameActionException {
		sendMapLocation(CHANNEL_ARCHON_LOCATION, loc, radiusSq);
	}
	
	public static MapLocation parseArchonLocation(int[] data) {
		return parseMapLocation(data);
	}

	public static void sendPartsLocation(MapLocation loc, int numParts, int radiusSq) throws GameActionException {
		int data0 = CHANNEL_FOUND_PARTS | (CHANNEL_MASK_INVERSE & numParts);
		int data1 = intFromMapLocation(loc);
		rc.broadcastMessageSignal(data0, data1, radiusSq);
	}
	

	// outPartsLoc is set to the MapLocation of the found parts
	// the returned value is the number of parts at that location
	public static PartsLocation parsePartsLocation(int[] data) {
		MapLocation loc = mapLocationFromInt(data[1]);
		int numParts = data[0] & CHANNEL_MASK_INVERSE;
		return new PartsLocation(loc, numParts);
	}
	
	public static void sendMapMinX(int radiusSq) throws GameActionException {
		sendInt(CHANNEL_MAP_MIN_X, MapEdges.minX, radiusSq);
	}

	public static void sendMapMaxX(int radiusSq) throws GameActionException {
		sendInt(CHANNEL_MAP_MAX_X, MapEdges.maxX, radiusSq);
	}

	public static void sendMapMinY(int radiusSq) throws GameActionException {
		sendInt(CHANNEL_MAP_MIN_Y, MapEdges.minY, radiusSq);
	}

	public static void sendMapMaxY(int radiusSq) throws GameActionException {
		sendInt(CHANNEL_MAP_MAX_Y, MapEdges.maxY, radiusSq);
	}
	
	public static void sendKnownMapEdges(int radiusSq) throws GameActionException {
		if (MapEdges.minX != MapEdges.UNKNOWN) {
			sendMapMinX(radiusSq);
		}
		if (MapEdges.maxX != MapEdges.UNKNOWN) {
			sendMapMaxX(radiusSq);
		}
		if (MapEdges.minY != MapEdges.UNKNOWN) {
			sendMapMinY(radiusSq);
		}
		if (MapEdges.maxY != MapEdges.UNKNOWN) {
			sendMapMaxY(radiusSq);
		}
	}

	
	public static void processMapMinX(int[] data) {
		MapEdges.minX = parseInt(data);
	}

	public static void processMapMaxX(int[] data) {
		MapEdges.maxX = parseInt(data);
	}
	
	public static void processMapMinY(int[] data) {
		MapEdges.minY = parseInt(data);
	}

	public static void processMapMaxY(int[] data) {
		MapEdges.maxY = parseInt(data);
	}
	
}

