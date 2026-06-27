/*
 * OsmAnd, OsmAnd BV <info@osmand.net>, 2010–present
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package net.osmand.plus.plugins.driverbreak;

import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.PlatformUtil;
import net.osmand.data.Amenity;
import net.osmand.data.LatLon;
import net.osmand.data.QuadRect;
import net.osmand.plus.OsmAndTaskManager;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.poi.PoiUIFilter;
import net.osmand.util.Algorithms;
import net.osmand.util.MapUtils;

import org.apache.commons.logging.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Map-based POI search with Overpass API fallback. Port of driver_break_poi.c / driver_break_poi_map.c.
 */
public class PoiDiscovery {

	private static final Log LOG = PlatformUtil.getLog(PoiDiscovery.class);

	private static final long OVERPASS_CACHE_MS = 10L * 60L * 1000L;

	/** Minimum map POI hits before skipping Overpass; driver_break_poi.c threshold. */
	private static final int MIN_MAP_POIS_BEFORE_OVERPASS = 2;

	private static final String[] DNT_NETWORK_MARKERS = {
			"dnt", "stf", "dav", "sac", "oeav", "metsähallitus", "den norske turistforening",
			"svenska turistföreningen"
	};

	private final OsmandApplication app;
	private final DriverBreakSettings settings;
	private final ExecutorService downloadExecutor;
	private final Map<String, CachedOverpassResult> overpassCache = new HashMap<>();

	public interface PoiResultListener {
		void onResult(@NonNull List<RestStop.NearbyPoi> pois);
	}

	public PoiDiscovery(@NonNull OsmandApplication app, @NonNull DriverBreakSettings settings) {
		this.app = app;
		this.settings = settings;
		this.downloadExecutor = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "driver-break-poi");
			t.setDaemon(true);
			return t;
		});
	}

	/**
	 * Find nearby POIs for a rest-stop candidate.
	 */
	public void findNearby(double lat, double lon, @NonNull TravelMode mode, @NonNull PoiResultListener listener) {
		OsmAndTaskManager.executeTask(new AsyncTask<Void, Void, List<RestStop.NearbyPoi>>() {
			@Override
			protected List<RestStop.NearbyPoi> doInBackground(Void... voids) {
				int minBuildingsM = settings.getMinDistBuildingsM();
				int minGlaciersM = settings.getMinDistGlaciersM();
				if (!PoiLocationValidator.isFarEnoughFromBuildings(lat, lon, minBuildingsM)) {
					LOG.debug("DriverBreak: POI search skipped — too close to buildings");
					return Collections.emptyList();
				}
				if (!PoiLocationValidator.isFarEnoughFromGlaciers(lat, lon, minGlaciersM, false)) {
					LOG.debug("DriverBreak: POI search skipped — too close to glacier");
					return Collections.emptyList();
				}
				List<RestStop.NearbyPoi> mapResults = searchMap(lat, lon, mode);
				List<RestStop.NearbyPoi> merged;
				if (mapResults.size() >= MIN_MAP_POIS_BEFORE_OVERPASS) {
					merged = mapResults;
				} else {
					List<RestStop.NearbyPoi> overpass = searchOverpass(lat, lon, mode);
					merged = mergeDistinct(mapResults, overpass);
				}
				return sortPois(merged, lat, lon, settings.isDntPrioritySync());
			}

			@Override
			protected void onPostExecute(List<RestStop.NearbyPoi> result) {
				app.runInUIThread(() -> listener.onResult(result != null ? result : Collections.emptyList()));
			}
		}, downloadExecutor);
	}

	@NonNull
	private List<RestStop.NearbyPoi> searchMap(double lat, double lon, @NonNull TravelMode mode) {
		int radiusM = radiusForMode(mode);
		QuadRect rect = MapUtils.calculateLatLonBbox(lat, lon, radiusM);
		List<RestStop.NearbyPoi> results = new ArrayList<>();
		Set<Long> seenIds = new HashSet<>();
		for (PoiUIFilter filter : app.getPoiFilters().getTopDefinedPoiFilters()) {
			if (!matchesModeFilter(filter, mode)) {
				continue;
			}
			List<Amenity> amenities;
			try {
				amenities = filter.searchAmenities(rect.top, rect.left, rect.bottom, rect.right, -1, null, true);
			} catch (RuntimeException e) {
				LOG.warn("DriverBreak: map POI search failed: " + e.getMessage());
				continue;
			}
			if (amenities == null) {
				continue;
			}
			for (Amenity amenity : amenities) {
				if (!matchesAmenityTags(amenity, mode)) {
					continue;
				}
				long id = amenity.getId();
				if (!seenIds.add(id)) {
					continue;
				}
				double dist = MapUtils.getDistance(lat, lon, amenity.getLocation().getLatitude(),
						amenity.getLocation().getLongitude());
				if (dist > radiusM) {
					continue;
				}
				boolean networkPriority = isNetworkPriorityPoi(amenity);
				results.add(new RestStop.NearbyPoi(amenity.getLocation(), amenity.getName(),
						amenity.getSubType(), dist, networkPriority));
			}
		}
		return results;
	}

	@NonNull
	private List<RestStop.NearbyPoi> searchOverpass(double lat, double lon, @NonNull TravelMode mode) {
		String cacheKey = String.format(Locale.US, "%.2f,%.2f,%s", lat, lon, mode.getConfigKey());
		CachedOverpassResult cached = overpassCache.get(cacheKey);
		long now = System.currentTimeMillis();
		if (cached != null && now - cached.timestampMs < OVERPASS_CACHE_MS) {
			return cached.pois;
		}
		String query = buildOverpassQuery(lat, lon, mode, radiusForMode(mode));
		try {
			String response = PoiLocationValidator.executeOverpassWithRetry(query);
			if (Algorithms.isEmpty(response)) {
				return Collections.emptyList();
			}
			List<RestStop.NearbyPoi> parsed = parseOverpassJson(response, lat, lon);
			overpassCache.put(cacheKey, new CachedOverpassResult(now, parsed));
			return parsed;
		} catch (JSONException e) {
			LOG.warn("DriverBreak: Overpass query failed: " + e.getMessage());
			return Collections.emptyList();
		}
	}

	@NonNull
	static String buildOverpassQuery(double lat, double lon, @NonNull TravelMode mode, int radiusM) {
		String[] tags = overpassTagsForMode(mode);
		StringBuilder query = new StringBuilder("[out:json][timeout:25];(");
		for (int i = 0; i < tags.length; i++) {
			if (i > 0) {
				query.append(';');
			}
			String[] kv = tags[i].split("=", 2);
			if (kv.length == 2) {
				query.append(String.format(Locale.US, "node[\"%s\"=\"%s\"](around:%d,%.6f,%.6f);",
						kv[0], kv[1], radiusM, lat, lon));
			}
		}
		query.append(");out body;");
		return query.toString();
	}

	@NonNull
	private static String[] overpassTagsForMode(@NonNull TravelMode mode) {
		switch (mode) {
			case HIKING:
				return new String[] {
						"amenity=drinking_water", "amenity=fountain", "natural=spring",
						"tourism=wilderness_hut", "tourism=alpine_hut", "tourism=hostel",
						"tourism=camp_site", "amenity=place_of_worship"
				};
			case CYCLING:
				return new String[] {
						"amenity=drinking_water", "shop=bicycle", "amenity=bicycle_repair_station",
						"amenity=charging_station", "tourism=alpine_hut", "amenity=fuel"
				};
			case MOTORCYCLE:
				return new String[] {
						"amenity=fuel", "amenity=cafe", "amenity=restaurant", "tourism=viewpoint"
				};
			case TRUCK:
			case CAR:
			default:
				return new String[] {
						"amenity=fuel", "amenity=cafe", "amenity=restaurant", "tourism=museum",
						"tourism=viewpoint"
				};
		}
	}

	@NonNull
	static List<RestStop.NearbyPoi> parseOverpassJson(@NonNull String json, double lat, double lon)
			throws JSONException {
		List<RestStop.NearbyPoi> pois = new ArrayList<>();
		JSONObject root = new JSONObject(json);
		JSONArray elements = root.optJSONArray("elements");
		if (elements == null) {
			return pois;
		}
		for (int i = 0; i < elements.length(); i++) {
			JSONObject node = elements.getJSONObject(i);
			if (!node.has("lat") || !node.has("lon")) {
				continue;
			}
			double nLat = node.getDouble("lat");
			double nLon = node.getDouble("lon");
			JSONObject tags = node.optJSONObject("tags");
			String name = tags != null ? tags.optString("name", "") : "";
			String category = categoryFromTags(tags);
			double dist = MapUtils.getDistance(lat, lon, nLat, nLon);
			boolean networkPriority = tags != null && isNetworkTag(tags.optString("operator"), tags.optString("network"));
			pois.add(new RestStop.NearbyPoi(new LatLon(nLat, nLon), name, category, dist, networkPriority));
		}
		return pois;
	}

	@Nullable
	private static String categoryFromTags(@Nullable JSONObject tags) {
		if (tags == null) {
			return "overpass";
		}
		if (tags.has("amenity")) {
			return tags.optString("amenity");
		}
		if (tags.has("tourism")) {
			return tags.optString("tourism");
		}
		if (tags.has("natural")) {
			return tags.optString("natural");
		}
		if (tags.has("shop")) {
			return tags.optString("shop");
		}
		return "overpass";
	}

	private int radiusForMode(@NonNull TravelMode mode) {
		switch (mode) {
			case HIKING:
				if (settings.isWaterPoisEnabledSync()) {
					return settings.getWaterRadiusM();
				}
				return settings.getCabinRadiusM();
			case CYCLING:
				return settings.getPoiRadiusM();
			default:
				return settings.getPoiRadiusM();
		}
	}

	private static boolean matchesModeFilter(@NonNull PoiUIFilter filter, @NonNull TravelMode mode) {
		String id = filter.getFilterId().toLowerCase(Locale.US);
		switch (mode) {
			case HIKING:
			case CYCLING:
				return id.contains("water") || id.contains("accommodation") || id.contains("camp")
						|| id.contains("hut") || id.contains("hostel") || id.contains("worship")
						|| id.contains("bicycle") || id.contains("charging");
			case MOTORCYCLE:
				return id.contains("fuel") || id.contains("food") || id.contains("cafe")
						|| id.contains("viewpoint");
			case TRUCK:
			case CAR:
			default:
				return id.contains("fuel") || id.contains("food") || id.contains("cafe")
						|| id.contains("restaurant") || id.contains("viewpoint");
		}
	}

	private static boolean matchesAmenityTags(@NonNull Amenity amenity, @NonNull TravelMode mode) {
		String subType = amenity.getSubType();
		if (subType == null) {
			return false;
		}
		switch (mode) {
			case HIKING:
				return "drinking_water".equals(subType) || "spring".equals(subType) || "fountain".equals(subType)
						|| "wilderness_hut".equals(subType) || "alpine_hut".equals(subType)
						|| "hostel".equals(subType) || "camp_site".equals(subType)
						|| "place_of_worship".equals(subType);
			case CYCLING:
				return "drinking_water".equals(subType) || "spring".equals(subType) || "bicycle".equals(subType)
						|| "bicycle_repair_station".equals(subType) || "charging_station".equals(subType)
						|| "alpine_hut".equals(subType) || "fuel".equals(subType);
			case MOTORCYCLE:
				return "fuel".equals(subType) || "cafe".equals(subType) || "restaurant".equals(subType)
						|| "viewpoint".equals(subType);
			case TRUCK:
			case CAR:
			default:
				return "fuel".equals(subType) || "cafe".equals(subType) || "restaurant".equals(subType)
						|| "viewpoint".equals(subType) || "museum".equals(subType);
		}
	}

	static boolean isNetworkPriorityPoi(@NonNull Amenity amenity) {
		String operator = amenity.getAdditionalInfo(Amenity.OPERATOR);
		String network = amenity.getAdditionalInfo("network");
		if (isNetworkTag(operator, network)) {
			return true;
		}
		String name = amenity.getName();
		return name != null && isNetworkTag(name, null);
	}

	static boolean isNetworkTag(@Nullable String operator, @Nullable String network) {
		if (containsNetworkMarker(operator)) {
			return true;
		}
		return containsNetworkMarker(network);
	}

	private static boolean containsNetworkMarker(@Nullable String value) {
		if (Algorithms.isEmpty(value)) {
			return false;
		}
		String lower = value.toLowerCase(Locale.US);
		for (String marker : DNT_NETWORK_MARKERS) {
			if (lower.contains(marker)) {
				return true;
			}
		}
		return false;
	}

	@NonNull
	private static List<RestStop.NearbyPoi> mergeDistinct(@NonNull List<RestStop.NearbyPoi> first,
			@NonNull List<RestStop.NearbyPoi> second) {
		List<RestStop.NearbyPoi> merged = new ArrayList<>(first);
		Set<String> keys = new HashSet<>();
		for (RestStop.NearbyPoi poi : first) {
			keys.add(poiKey(poi));
		}
		for (RestStop.NearbyPoi poi : second) {
			if (keys.add(poiKey(poi))) {
				merged.add(poi);
			}
		}
		return merged;
	}

	@NonNull
	private static String poiKey(@NonNull RestStop.NearbyPoi poi) {
		LatLon loc = poi.getLocation();
		return String.format(Locale.US, "%.4f,%.4f", loc.getLatitude(), loc.getLongitude());
	}

	@NonNull
	static List<RestStop.NearbyPoi> sortPois(@NonNull List<RestStop.NearbyPoi> pois, double lat, double lon,
			boolean dntPriority) {
		pois.sort((a, b) -> {
			if (dntPriority) {
				if (a.isNetworkPriority() && !b.isNetworkPriority()) {
					return -1;
				}
				if (!a.isNetworkPriority() && b.isNetworkPriority()) {
					return 1;
				}
			}
			double da = MapUtils.getDistance(lat, lon, a.getLocation().getLatitude(), a.getLocation().getLongitude());
			double db = MapUtils.getDistance(lat, lon, b.getLocation().getLatitude(), b.getLocation().getLongitude());
			return Double.compare(da, db);
		});
		return pois;
	}

	private static final class CachedOverpassResult {
		final long timestampMs;
		final List<RestStop.NearbyPoi> pois;

		CachedOverpassResult(long timestampMs, @NonNull List<RestStop.NearbyPoi> pois) {
			this.timestampMs = timestampMs;
			this.pois = pois;
		}
	}
}
