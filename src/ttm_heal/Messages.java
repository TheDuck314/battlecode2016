package ttm_heal;

import battlecode.common.*;

public class Messages extends Globals {
	public static final int TURRET_TARGET_PREFIX = 23987;
	
	public static int intFromMapLocation(MapLocation loc) {
		return ((loc.x + 16000) << 16) | (loc.y + 16000);
	}
	
	public static MapLocation mapLocationFromInt(int data) {
		int x = ((data & 0xffff0000) >>> 16) - 16000;
		int y = (data & 0x0000ffff) - 16000;
		return new MapLocation(x, y);
	}

	public static void sendTurretTarget(MapLocation loc, int radiusSq) throws GameActionException {
		Debug.indicate("spotting", 2, "sending ints " + TURRET_TARGET_PREFIX + ", " + intFromMapLocation(loc));
		rc.broadcastMessageSignal(TURRET_TARGET_PREFIX, intFromMapLocation(loc), radiusSq);
	}
	
	public static MapLocation tryParseTurretSignal(Signal sig) {
		if (sig.getTeam() != us) return null;
		int[] data = sig.getMessage();
		if (data == null) return null;
		if (data[0] != TURRET_TARGET_PREFIX) return null;
		return mapLocationFromInt(data[1]);
	}
}
