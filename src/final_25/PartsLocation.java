package final_25;

import battlecode.common.*;

// Result of parsing a part location signal.
public class PartsLocation {
	public MapLocation location;
	public int numParts;
	public PartsLocation(MapLocation location, int numParts) {
		this.location = location;
		this.numParts = numParts;
	}
}

