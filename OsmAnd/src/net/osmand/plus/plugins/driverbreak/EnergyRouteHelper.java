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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.osmand.data.LatLon;
import net.osmand.plus.routing.RouteCalculationResult;
import net.osmand.router.RouteSegmentResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds {@link SegmentData} lists from calculated routes and compares energy costs.
 */
public class EnergyRouteHelper {

	/** Minimum relative energy saving to suggest an alternative route (5 %). */
	public static final double MIN_ENERGY_SAVING_FRACTION = 0.05;

	/** Maximum relative distance increase for an acceptable alternative (20 %). */
	public static final double MAX_DISTANCE_INCREASE_FRACTION = 0.20;

	/** Fallback speed when a segment reports no speed (30 km/h in m/s). */
	private static final double DEFAULT_SPEED_MS = 30.0 / 3.6;

	private EnergyRouteHelper() {
	}

	/**
	 * Converts a route into energy-model segments using SRTM elevation at endpoints.
	 *
	 * @param route             calculated route; may be empty
	 * @param elevationProvider elevation tile reader
	 * @return segment list suitable for {@link EnergyModel#routeCost}
	 */
	/**
	 * Sums segment energy cost over explicit route segments.
	 */
	public static double routeCostFromSegments(@NonNull List<RouteSegmentResult> segments,
			@NonNull SRTMElevationProvider elevationProvider, @NonNull EnergyParams params) {
		return new EnergyModel().routeCost(buildSegmentsFromRoute(segments, elevationProvider), params);
	}

	/**
	 * Builds segment data from a raw segment list (e.g. junction alternative).
	 */
	@NonNull
	public static List<SegmentData> buildSegmentsFromRoute(@NonNull List<RouteSegmentResult> segments,
			@NonNull SRTMElevationProvider elevationProvider) {
		List<SegmentData> result = new ArrayList<>(segments.size());
		for (RouteSegmentResult segment : segments) {
			SegmentData data = segmentFromResult(segment, elevationProvider);
			if (data != null) {
				result.add(data);
			}
		}
		return result;
	}

	/**
	 * Total distance in metres for a raw segment list.
	 */
	public static double segmentListDistanceM(@NonNull List<RouteSegmentResult> segments) {
		double total = 0.0;
		for (RouteSegmentResult segment : segments) {
			total += segment.getDistance();
		}
		return total;
	}

	@NonNull
	public static List<SegmentData> buildSegments(@NonNull RouteCalculationResult route,
			@NonNull SRTMElevationProvider elevationProvider) {
		return buildSegmentsFromRoute(route.getOriginalRoute(), elevationProvider);
	}

	/**
	 * Computes total route distance in metres from segment lengths.
	 */
	public static double routeDistanceM(@NonNull List<SegmentData> segments) {
		double total = 0.0;
		for (SegmentData segment : segments) {
			total += segment.getDistanceM();
		}
		return total;
	}

	/**
	 * Returns true when {@code candidate} uses at least 5 % less energy than {@code baseline}
	 * while being at most 20 % longer by distance.
	 */
	public static boolean isCheaperAlternative(double baselineEnergyJ, double candidateEnergyJ,
			double baselineDistanceM, double candidateDistanceM) {
		if (baselineEnergyJ <= 0.0 || candidateEnergyJ <= 0.0 || baselineDistanceM <= 0.0) {
			return false;
		}
		double energySaving = (baselineEnergyJ - candidateEnergyJ) / baselineEnergyJ;
		double distanceIncrease = (candidateDistanceM - baselineDistanceM) / baselineDistanceM;
		return energySaving >= MIN_ENERGY_SAVING_FRACTION
				&& distanceIncrease <= MAX_DISTANCE_INCREASE_FRACTION;
	}

	@Nullable
	private static SegmentData segmentFromResult(@NonNull RouteSegmentResult segment,
			@NonNull SRTMElevationProvider elevationProvider) {
		double distanceM = segment.getDistance();
		if (distanceM <= 0.0) {
			return null;
		}
		double speedMs = segment.getSegmentSpeed();
		if (speedMs <= 0.0) {
			speedMs = DEFAULT_SPEED_MS;
		}
		LatLon start = segment.getPoint(segment.getStartPointIndex());
		LatLon end = segment.getPoint(segment.getEndPointIndex());
		double deltaH = elevationDelta(start, end, elevationProvider);
		double elevationM = meanElevation(start, end, elevationProvider);
		return SegmentData.of(distanceM, deltaH, elevationM, speedMs);
	}

	private static double elevationDelta(@NonNull LatLon start, @NonNull LatLon end,
			@NonNull SRTMElevationProvider elevationProvider) {
		int startElev = elevationProvider.getElevation(start.getLatitude(), start.getLongitude());
		int endElev = elevationProvider.getElevation(end.getLatitude(), end.getLongitude());
		if (startElev == SRTMElevationProvider.VOID_ELEVATION
				|| endElev == SRTMElevationProvider.VOID_ELEVATION) {
			return 0.0;
		}
		return endElev - startElev;
	}

	private static double meanElevation(@NonNull LatLon start, @NonNull LatLon end,
			@NonNull SRTMElevationProvider elevationProvider) {
		int startElev = elevationProvider.getElevation(start.getLatitude(), start.getLongitude());
		int endElev = elevationProvider.getElevation(end.getLatitude(), end.getLongitude());
		if (startElev == SRTMElevationProvider.VOID_ELEVATION && endElev == SRTMElevationProvider.VOID_ELEVATION) {
			return 0.0;
		}
		if (startElev == SRTMElevationProvider.VOID_ELEVATION) {
			return endElev;
		}
		if (endElev == SRTMElevationProvider.VOID_ELEVATION) {
			return startElev;
		}
		return (startElev + endElev) / 2.0;
	}
}
