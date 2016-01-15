package postsprint_13;

import battlecode.common.*;

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
		if (minX == UNKNOWN && maxX == UNKNOWN) {
			maxWidth = GameConstants.MAP_MAX_WIDTH;
		} else if (minX != UNKNOWN && maxX != UNKNOWN) {
			maxWidth = Math.max(maxX - here.x, here.x - minX);
		} else if (minX == UNKNOWN) {
			maxWidth = Math.max(maxX - here.x, GameConstants.MAP_MAX_WIDTH - (maxX - here.x));
		} else {
			maxWidth = Math.max(GameConstants.MAP_MAX_WIDTH - (here.x - minX), here.x - minX);
		}
		
		int maxHeight;
		if (minY == UNKNOWN && maxY == UNKNOWN) {
			maxHeight = GameConstants.MAP_MAX_WIDTH;
		} else if (minY != UNKNOWN && maxY != UNKNOWN) {
			maxHeight = Math.max(maxY - here.y, here.y - minY);
		} else if (minY == UNKNOWN) {
			maxHeight = Math.max(maxY - here.y, GameConstants.MAP_MAX_WIDTH - (maxY - here.y));
		} else {
			maxHeight = Math.max(GameConstants.MAP_MAX_WIDTH - (here.y - minY), here.y - minY);
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
		boolean shouldSend = false;
		if (minX == UNKNOWN) {
			if (!rc.onTheMap(here.add(-visionRange, 0))) {
				for (int r = 1; r <= visionRange; ++r) {
					if (!rc.onTheMap(here.add(-r, 0))) {
						minX = here.x - r + 1;
						shouldSend = true;
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
						shouldSend = true;
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
						shouldSend = true;
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
						shouldSend = true;
						break;
					}
				}
			}
		}
		if (shouldSend) {
			Messages.sendKnownMapEdges(maxBroadcastDistSq());
		};
	}
}
