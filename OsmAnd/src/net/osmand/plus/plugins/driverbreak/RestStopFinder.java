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
import net.osmand.data.LatLon;
import net.osmand.plus.OsmAndTaskManager;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.routing.RouteCalculationResult;
import net.osmand.router.RouteSegmentResult;
import net.osmand.util.MapUtils;

import org.apache.commons.logging.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Walks a calculated route and proposes rest stops at mode-specific intervals.
 * Port of driver_break_finder.c and hiking/cycling stop placement in driver_break.c.
 */
public class RestStopFinder {

	private static final Log LOG = PlatformUtil.getLog(RestStopFinder.class);

	private final OsmandApplication app;
	private final DriverBreakSettings settings;
	private final PoiDiscovery poiDiscovery;
	private final SRTMElevationProvider elevationProvider;
	private final ExecutorService restStopExecutor;

	public interface RestStopListener {
		void onRestStopsFound(@NonNull List<RestStop> stops);
	}

	public RestStopFinder(@NonNull OsmandApplication app, @NonNull DriverBreakSettings settings,
			@NonNull PoiDiscovery poiDiscovery, @NonNull SRTMElevationProvider elevationProvider) {
		this.app = app;
		this.settings = settings;
		this.poiDiscovery = poiDiscovery;
		this.elevationProvider = elevationProvider;
		this.restStopExecutor = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "driver-break-rest-stop");
			t.setDaemon(true);
			return t;
		});
	}

	/**
	 * Analyze route and find rest stop candidates on a background thread.
	 */
	public void findRestStops(@NonNull RouteCalculationResult route, @NonNull TravelMode mode,
			@NonNull RestStopListener listener) {
		OsmAndTaskManager.executeTask(new AsyncTask<Void, Void, List<RestStop>>() {
			@Override
			protected List<RestStop> doInBackground(Void... voids) {
				return computeStops(route, mode);
			}

			@Override
			protected void onPostExecute(List<RestStop> restStops) {
				app.runInUIThread(() -> listener.onRestStopsFound(
						restStops != null ? restStops : Collections.emptyList()));
			}
		}, restStopExecutor);
	}

	@NonNull
	List<RestStop> computeStops(@NonNull RouteCalculationResult route, @NonNull TravelMode mode) {
		List<RouteSegmentResult> segments = route.getOriginalRoute();
		if (segments.isEmpty()) {
			return Collections.emptyList();
		}
		double totalDistanceM = 0.0;
		for (RouteSegmentResult segment : segments) {
			totalDistanceM += segment.getDistance();
		}
		List<Double> breakDistancesM = intervalDistancesM(mode, totalDistanceM);
		List<RestStop> stops = new ArrayList<>();
		for (Double targetM : breakDistancesM) {
			LatLon coord = coordinateAtDistance(segments, targetM);
			if (coord == null) {
				continue;
			}
			List<RestStop.NearbyPoi> pois = searchPoisSync(coord.getLatitude(), coord.getLongitude(), mode);
			stops.add(new RestStop(coord, mode, pois, 0.0));
		}
		return stops;
	}

	@NonNull
	private List<Double> intervalDistancesM(@NonNull TravelMode mode, double totalDistanceM) {
		List<Double> targets = new ArrayList<>();
		double intervalM;
		switch (mode) {
			case HIKING:
				intervalM = settings.getHikingMainDistKm() * 1000.0;
				break;
			case CYCLING:
				intervalM = settings.getCyclingMainDistKm() * 1000.0;
				break;
			case CAR:
			case TRUCK:
			case MOTORCYCLE:
			default:
				int hours = mode == TravelMode.TRUCK
						? settings.getTruckMandatoryBreakHours()
						: settings.getCarBreakIntervalHours();
				double avgSpeedMs = 25.0;
				intervalM = hours * 3600.0 * avgSpeedMs;
				break;
		}
		if (intervalM <= 0.0 || totalDistanceM <= intervalM) {
			return targets;
		}
		for (double d = intervalM; d < totalDistanceM; d += intervalM) {
			targets.add(d);
		}
		return targets;
	}

	@Nullable
	private LatLon coordinateAtDistance(@NonNull List<RouteSegmentResult> segments, double targetDistanceM) {
		double accumulated = 0.0;
		for (RouteSegmentResult segment : segments) {
			double segLen = segment.getDistance();
			if (accumulated + segLen >= targetDistanceM) {
				double fraction = (targetDistanceM - accumulated) / segLen;
				LatLon start = segment.getStartPoint();
				LatLon end = segment.getEndPoint();
				if (start == null || end == null) {
					return null;
				}
				double lat = start.getLatitude() + fraction * (end.getLatitude() - start.getLatitude());
				double lon = start.getLongitude() + fraction * (end.getLongitude() - start.getLongitude());
				return new LatLon(lat, lon);
			}
			accumulated += segLen;
		}
		return null;
	}

	@NonNull
	private List<RestStop.NearbyPoi> searchPoisSync(double lat, double lon, @NonNull TravelMode mode) {
		final List<RestStop.NearbyPoi>[] holder = new List[] {Collections.emptyList()};
		final Object lock = new Object();
		poiDiscovery.findNearby(lat, lon, mode, pois -> {
			synchronized (lock) {
				holder[0] = pois;
				lock.notifyAll();
			}
		});
		synchronized (lock) {
			try {
				lock.wait(5000);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				LOG.warn("DriverBreak: POI search interrupted");
			}
		}
		return holder[0];
	}
}
