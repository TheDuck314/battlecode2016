package priorities22;

import battlecode.common.*;

public class PartRegion {
	public int totalParts;
	public int avgTurnsToUncover;
	public MapLocation centralLocation;
	
	public PartRegion(int totalParts, int avgTurnsToUncover, MapLocation centralLocation) {
		this.totalParts = totalParts;
		this.avgTurnsToUncover = avgTurnsToUncover;
		this.centralLocation = centralLocation;
	}
}
