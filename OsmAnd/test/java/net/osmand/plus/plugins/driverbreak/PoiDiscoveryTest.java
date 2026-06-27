package net.osmand.plus.plugins.driverbreak;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import net.osmand.data.LatLon;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class PoiDiscoveryTest {

	@Test
	public void dntPrioritySortsNetworkCabinsFirst() {
		List<RestStop.NearbyPoi> pois = new ArrayList<>();
		pois.add(new RestStop.NearbyPoi(new LatLon(60.0, 10.0), "Far hut", "alpine_hut", 5000.0, false));
		pois.add(new RestStop.NearbyPoi(new LatLon(60.01, 10.01), "DNT hut", "wilderness_hut", 8000.0, true));
		List<RestStop.NearbyPoi> sorted = PoiDiscovery.sortPois(pois, 60.0, 10.0, true);
		Assert.assertTrue(sorted.get(0).isNetworkPriority());
	}

	@Test
	public void isNetworkTagDetectsDntOperator() {
		Assert.assertTrue(PoiDiscovery.isNetworkTag("DNT Oslo", null));
		Assert.assertTrue(PoiDiscovery.isNetworkTag(null, "SAC"));
		Assert.assertFalse(PoiDiscovery.isNetworkTag("Private owner", null));
	}

	@Test
	public void hikingOverpassQueryIncludesWaterAndCabins() {
		String query = PoiDiscovery.buildOverpassQuery(61.0, 9.0, TravelMode.HIKING, 2000);
		Assert.assertTrue(query.contains("drinking_water"));
		Assert.assertTrue(query.contains("wilderness_hut"));
	}
}
