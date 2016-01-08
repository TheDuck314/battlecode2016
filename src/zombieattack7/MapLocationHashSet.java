package zombieattack7;

import battlecode.common.MapLocation;

public class MapLocationHashSet {
	private int size = 97;
	private MapLocation[][] buckets = new MapLocation[size][100];
	private int[] bucketSizes = new int[size];
	
	public boolean contains(MapLocation loc) {
		int bucketIndex = loc.hashCode() % size;
		int bucketSize = bucketSizes[bucketIndex];
		MapLocation[] bucket = buckets[bucketIndex];
		for (int i = 0; i < bucketSize; ++i) {
			if (bucket[i].equals(loc)) return true;
		}
		return false;
	}
	
	// returns true if the given location was added
	// (that is, if it wasn't already in the set)
	public boolean add(MapLocation loc) {
		int bucketIndex = loc.hashCode() % size;
		int bucketSize = bucketSizes[bucketIndex];
		MapLocation[] bucket = buckets[bucketIndex];
		for (int i = 0; i < bucketSize; ++i) {
			if (bucket[i].equals(loc)) return false;
		}
		if (bucketSize < 100) {
			bucket[bucketSize] = loc;
			++bucketSizes[bucketIndex];
		}
		return true;
	}
}
