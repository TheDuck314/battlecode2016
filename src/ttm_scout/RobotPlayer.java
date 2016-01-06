package ttm_scout;

import battlecode.common.*;

public class RobotPlayer extends Globals {

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
     * @throws Exception 
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController theRC) throws Exception {
    	Globals.init(theRC);
    	
		switch (rc.getType()) {
		    case ARCHON:
				BotArchon.loop();
		        break;	

		    case GUARD:
		    	BotGuard.loop();
		    	break;
		    	
		    case SCOUT:
		    	BotScout.loop();
		    	break;
		    	
		    case SOLDIER:
		    	BotSoldier.loop();
		        break;
		    
		    case TURRET:
		    	BotTurret.loop();
		    	break;
		    	
		    case VIPER:
		    	BotViper.loop();
		    	break;
		    	
		    default:
		    	throw new Exception("weird robot type " + rc.getType());
		}
    }
}
