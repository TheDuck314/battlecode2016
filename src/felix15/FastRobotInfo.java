package felix15;

import battlecode.common.*;

public class FastRobotInfo {
	public final MapLocation location;
	public final RobotType type;
	public final int round;
	
	public FastRobotInfo(MapLocation location, RobotType type, int round) {
		this.location = location;
		this.type = type;
		this.round = round;
	}
}
