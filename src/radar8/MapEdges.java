package radar8;

import battlecode.common.GameActionException;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;

public class MapEdges extends Globals {
	public static int UNKNOWN = -999999999;
	
	public static int minX = UNKNOWN;
	public static int maxX = UNKNOWN;
	public static int minY = UNKNOWN;
	public static int maxY = UNKNOWN;
	
	// returns the maximum possible length-squared of the diagonal
	// of the map, given what we currently know about the locations
	// of the boundaries
	public static int maxBroadcastDistSq() {
		int maxWidth;
		if (minX == UNKNOWN || maxX == UNKNOWN) {
			maxWidth = GameConstants.MAP_MAX_WIDTH;
		} else {
			maxWidth = maxX - minX;
		}
		
		int maxHeight;
		if (minY == UNKNOWN || maxY == UNKNOWN) {
			maxHeight = GameConstants.MAP_MAX_WIDTH;
		} else {
			maxHeight = maxY - minY;
		}
		
		return (maxWidth * maxWidth) + (maxHeight * maxHeight);
	}
	
	public static boolean isOffMap(MapLocation loc) {
		return (minX != UNKNOWN && loc.x < minX)
				|| (maxX != UNKNOWN && loc.x > maxX) 
				|| (minY != UNKNOWN && loc.y < minY) 
				|| (maxY != UNKNOWN && loc.y > maxY);
	}
	
	// visionRange should be (int)Math.sqrt(sensorRadiusSquared)
	public static void detectAndBroadcastMapEdges(int visionRange) throws GameActionException {
		if (minX == UNKNOWN) {
			if (!rc.onTheMap(here.add(-visionRange, 0))) {
				for (int r = 1; r <= visionRange; ++r) {
					if (!rc.onTheMap(here.add(-r, 0))) {
						minX = here.x - r + 1;
						Messages.sendMapMinX(maxBroadcastDistSq());
						break;
					}
				}
			}
        }
		
		if (maxX == UNKNOWN) {
			if (!rc.onTheMap(here.add(visionRange, 0))) {
				for (int r = 1; r <= visionRange; ++r) {
					if (!rc.onTheMap(here.add(r, 0))) {
						maxX = here.x + r - 1;
						Messages.sendMapMaxX(maxBroadcastDistSq());
						break;
				    }
				}
			}
        }
		
		if (minY == UNKNOWN) {
			if (!rc.onTheMap(here.add(0, -visionRange))) {
				for (int r = 1; r <= visionRange; ++r) {
					if (!rc.onTheMap(here.add(0, -r))) {
						minY = here.y - r + 1;
						Messages.sendMapMinY(maxBroadcastDistSq());
						break;
					}
				}
			}
        }
		
		if (maxY == UNKNOWN) {
			if (!rc.onTheMap(here.add(0, visionRange))) {
				for (int r = 1; r <= visionRange; ++r) {
					if (!rc.onTheMap(here.add(0, r))) {
						maxY = here.y + r - 1;
						Messages.sendMapMaxY(maxBroadcastDistSq());
						break;
					}
				}
			}
        }
	}
}
