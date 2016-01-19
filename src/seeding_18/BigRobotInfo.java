package seeding_18;

import battlecode.common.*;

public class BigRobotInfo {
	public final int id;
	public final RobotType type;
	public final Team team;
	public MapLocation location;
	public int round;
	
	public BigRobotInfo(int id, RobotType type, Team team, MapLocation location, int round) {
		this.id = id;
		this.location = location;
		this.type = type;
		this.team = team;
		this.round = round;
	}
}
