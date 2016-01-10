package ttm_dense_9;

import battlecode.common.*;

// Result of parsing a part location signal.
class PartsLocation {
	public MapLocation location;
	public int numParts;
	public PartsLocation(MapLocation location, int numParts) {
		this.location = location;
		this.numParts = numParts;
	}
}

