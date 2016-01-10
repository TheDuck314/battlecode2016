package team028;

import battlecode.common.*;

public class FastRobotInfo {
	public MapLocation location;
	public RobotType type;
	public int round;
	
	public FastRobotInfo(MapLocation location, RobotType type, int round) {
		this.location = location;
		this.type = type;
		this.round = round;
	}
}
