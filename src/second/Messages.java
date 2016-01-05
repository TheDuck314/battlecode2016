package second;

import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;

public class Messages extends Globals {
	public static int intFromMapLocation(MapLocation loc) {
		return ((loc.x + 16000) << 16) & (loc.y + 16000);
	}
	
	public static MapLocation mapLocationFromInt(int data) {
		int x = ((data & 0xffff0000) >>> 16) - 16000;
		int y = (data & 0x0000ffff) - 16000;
		return new MapLocation(x, y);
	}
	
	// signal distance-squared = 
	//    (sensorRadiusSquared * (GameConstants.BROADCAST_RANGE_MULTIPLIER + signalStrength))
	public static int computeSignalStrength(int radiusSq) {
		double strength = radiusSq / mySensorRadiusSquared - GameConstants.BROADCAST_RANGE_MULTIPLIER;
		return (int)Math.ceil(strength);
	}
	
	public static void sendSignal(int radiusSq) throws GameActionException {
		rc.broadcastSignal(computeSignalStrength(radiusSq));
	}
	
	public static void sendMessageSignal(int data1, int data2, int radiusSq) {
		rc.broadcastMessageSignal(arg0, arg1, arg2);
	}
}
