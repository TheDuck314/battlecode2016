package armageddon_26;

import battlecode.common.*;

public class PartMemory extends Globals {
	public static final int MEMORY_LENGTH = 10;
	public static PartRegion[] regions = new PartRegion[MEMORY_LENGTH];
	static int pointer = 0;
	
	public static boolean add(PartRegion newRegion) {
		for (int i = 0; i < MEMORY_LENGTH; ++i) {
			PartRegion oldRegion = regions[i];
			if (oldRegion != null 
					&& oldRegion.centralLocation.distanceSquaredTo(newRegion.centralLocation) <= 35) {
				return false; // already stored
			}
		}		
		
		regions[pointer] = newRegion;
		pointer = (pointer + 1) % MEMORY_LENGTH;
		return true;
	}
	
	public static void remove(int index) {
		regions[index] = null;
	}
}
