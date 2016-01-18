package simplesoldier18;

import battlecode.common.*;

public class BigRobotInfo {
	public final int id;
	public final MapLocation location;
	public final RobotType type;
	public final Team team;
	public final int round;
	
	public BigRobotInfo(int id, MapLocation location, RobotType type, Team team, int round) {
		this.id = id;
		this.location = location;
		this.type = type;
		this.team = team;
		this.round = round;
	}
}
