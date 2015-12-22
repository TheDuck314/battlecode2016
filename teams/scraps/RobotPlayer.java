package scraps;

import battlecode.common.*;

public class RobotPlayer {
	static MapLocation ourHQ;
	static MapLocation theirHQ;
	static RobotController rc;
	static java.util.Random rand;
	
	public static void run(RobotController theRC) {
		rc = theRC;
		ourHQ = rc.senseHQLocation();
		theirHQ = rc.senseEnemyHQLocation();
		rand = new java.util.Random(theRC.getID());
		FastMath.initRand(rc);
		
		while(true) {
			runTests();
			rc.yield();
		}
	}

	static void runTests() {
		int timer0 = Clock.getBytecodeNum();
		int slowDist = (int)Math.sqrt(ourHQ.distanceSquaredTo(theirHQ));
		int timer1 = Clock.getBytecodeNum();
		int fastDist = FastMath.floorSqrt(ourHQ.distanceSquaredTo(theirHQ));
		int timer2 = Clock.getBytecodeNum();
		int slowRandomRand = rand.nextInt(256);
		int timer3 = Clock.getBytecodeNum();
		int slowMathRand = (int)(256 * Math.random());
		int timer4 = Clock.getBytecodeNum();
		int fastRand = FastMath.rand256();
		int timer5 = Clock.getBytecodeNum();
		
		rc.setIndicatorString(0, String.format("slowDist = %d, fastDist = %d, slowDist time = %d, fastDistTime = %d",
				slowDist, fastDist, timer1 - timer0, timer2 - timer1));
		
		rc.setIndicatorString(1, String.format("slowRandomRand = %d, slowMathRand = %d, fastRand = %d", 
				slowRandomRand, slowMathRand, fastRand));
		rc.setIndicatorString(2, String.format("slowRandomRand time = %d, slowMathRand time = %d, fastRand time = %d", 
				timer3 - timer2, timer4 - timer3, timer5 - timer4));
	}
}
