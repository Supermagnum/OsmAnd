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

import net.osmand.PlatformUtil;
import net.osmand.plus.OsmAndTaskManager;
import net.osmand.plus.OsmandApplication;
import net.osmand.util.Algorithms;

import org.apache.commons.logging.Log;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.HashSet;
import java.util.Set;

/**
 * Typed accessors for Driver Break SQLite config and session state.
 * All synchronous methods must be called from {@link #getDbExecutor()} only.
 */
public class DriverBreakSettings implements BreakTimer.ThresholdProvider {

	private static final Log LOG = PlatformUtil.getLog(DriverBreakSettings.class);

	/** Validation bounds from driver_break_db.c / driver_break_osd.c. */
	private static final double MIN_DRAG_CD = 0.01;
	private static final double MAX_DRAG_CD = 1.5;
	private static final double MIN_FRONTAL_AREA_SQM = 0.05;
	private static final double MAX_FRONTAL_AREA_SQM = 20.0;
	private static final double MIN_TOTAL_WEIGHT_KG = 1.0;
	private static final double MAX_TOTAL_WEIGHT_KG = 50000.0;

	private final DriverBreakDatabase database;
	private final ExecutorService dbExecutor;

	public DriverBreakSettings(@NonNull OsmandApplication app) {
		this.database = new DriverBreakDatabase(app, app.getAppPath("driver_break/driver_break.db"));
		this.dbExecutor = Executors.newSingleThreadExecutor(r -> {
			Thread t = new Thread(r, "driver-break-db");
			t.setDaemon(true);
			return t;
		});
	}

	@NonNull
	public ExecutorService getDbExecutor() {
		return dbExecutor;
	}

	@NonNull
	public DriverBreakDatabase getDatabase() {
		return database;
	}

	// --- Synchronous (background thread only) ---

	@NonNull
	public TravelMode getTravelModeSync() {
		return TravelMode.fromConfigKey(database.getConfig("travel_mode"));
	}

	public void setTravelModeSync(@NonNull TravelMode mode) {
		database.setConfig("travel_mode", mode.getConfigKey());
	}

	public boolean isUseEnergyRoutingSync() {
		return "1".equals(database.getConfig("use_energy_routing"));
	}

	public void setUseEnergyRoutingSync(boolean enabled) {
		database.setConfig("use_energy_routing", enabled ? "1" : "0");
	}

	public boolean isEcuEnabledSync() {
		return "1".equals(database.getConfig("ecu_enabled"));
	}

	public void setEcuEnabledSync(boolean enabled) {
		database.setConfig("ecu_enabled", enabled ? "1" : "0");
	}

	public boolean isAdaptiveFuelEnabledSync() {
		return "1".equals(database.getConfig("adaptive_fuel_enabled"));
	}

	public void setAdaptiveFuelEnabledSync(boolean enabled) {
		database.setConfig("adaptive_fuel_enabled", enabled ? "1" : "0");
	}

	@NonNull
	public EnergyParams getEnergyParamsSync() {
		double mass = clamp(parseDouble(database.getConfig("total_weight"), 80.0),
				MIN_TOTAL_WEIGHT_KG, MAX_TOTAL_WEIGHT_KG);
		double cd = clamp(parseDouble(database.getConfig("energy_drag_cd"), 0.30), MIN_DRAG_CD, MAX_DRAG_CD);
		double area = clamp(parseDouble(database.getConfig("energy_frontal_area_sqm"), 2.2),
				MIN_FRONTAL_AREA_SQM, MAX_FRONTAL_AREA_SQM);
		return new EnergyParams(mass, cd, area, 0.0, 0.7);
	}

	public void setEnergyParamsSync(double massKg, double dragCd, double frontalAreaM2) {
		database.setConfig("total_weight", String.valueOf(clamp(massKg, MIN_TOTAL_WEIGHT_KG, MAX_TOTAL_WEIGHT_KG)));
		database.setConfig("energy_drag_cd", String.valueOf(clamp(dragCd, MIN_DRAG_CD, MAX_DRAG_CD)));
		database.setConfig("energy_frontal_area_sqm",
				String.valueOf(clamp(frontalAreaM2, MIN_FRONTAL_AREA_SQM, MAX_FRONTAL_AREA_SQM)));
	}

	public double getLearnedRateLPerKmSync() {
		return parseDouble(database.getConfig("adaptive_learned_lkm"), 0.0);
	}

	public void setLearnedRateLPerKmSync(double rate) {
		database.setConfig("adaptive_learned_lkm", String.valueOf(rate));
	}

	public void persistBreakTimerStateSync(long lastBreakElapsedMs, long accumulatedDistanceM, boolean breakInProgress) {
		database.setConfig("timer_last_break_elapsed_ms", String.valueOf(lastBreakElapsedMs));
		database.setConfig("timer_accumulated_distance_m", String.valueOf(accumulatedDistanceM));
		database.setConfig("timer_break_in_progress", breakInProgress ? "1" : "0");
	}

	public void restoreBreakTimerSync(@NonNull BreakTimer timer) {
		long lastBreak = parseLong(database.getConfig("timer_last_break_elapsed_ms"), 0L);
		long distance = parseLong(database.getConfig("timer_accumulated_distance_m"), 0L);
		boolean inBreak = "1".equals(database.getConfig("timer_break_in_progress"));
		timer.restoreState(lastBreak, distance, inBreak, getTravelModeSync());
	}

	@Override
	public int getCarSoftLimitHours() {
		return parseInt(database.getConfig("car_soft_limit_h"), 7);
	}

	@Override
	public int getCarBreakIntervalHours() {
		return parseInt(database.getConfig("car_break_interval_h"), 4);
	}

	@Override
	public int getTruckMandatoryBreakHours() {
		return parseInt(database.getConfig("truck_mandatory_break_h"), 4);
	}

	@Override
	public int getMotorcycleSoftLimitMinutes() {
		return parseInt(database.getConfig("moto_soft_limit_min"), 120);
	}

	@Override
	public int getMotorcycleMandatoryBreakMinutes() {
		return parseInt(database.getConfig("moto_mandatory_break_min"), 210);
	}

	@Override
	public double getHikingMainDistKm() {
		return parseDouble(database.getConfig("hiking_main_dist_km"), 11.295);
	}

	@Override
	public double getCyclingMainDistKm() {
		return parseDouble(database.getConfig("cycling_main_dist_km"), 28.24);
	}

	public int getPoiRadiusM() {
		return parseInt(database.getConfig("poi_radius_m"), 15000);
	}

	public int getMinDistBuildingsM() {
		return parseInt(database.getConfig("min_dist_buildings_m"), 150);
	}

	public int getMinDistGlaciersM() {
		return parseInt(database.getConfig("min_dist_glaciers_m"), 300);
	}

	public boolean isDntPrioritySync() {
		return "1".equals(database.getConfig("dnt_priority"));
	}

	public boolean isHikingPilgrimageSync() {
		return "1".equals(database.getConfig("hiking_pilgrimage"));
	}

	public boolean isWaterPoisEnabledSync() {
		return "1".equals(database.getConfig("water_pois_enabled"));
	}

	public void setWaterPoisEnabledSync(boolean enabled) {
		database.setConfig("water_pois_enabled", enabled ? "1" : "0");
	}

	public void setDntPrioritySync(boolean enabled) {
		database.setConfig("dnt_priority", enabled ? "1" : "0");
	}

	public int getWaterRadiusM() {
		return parseInt(database.getConfig("water_radius_m"), 2000);
	}

	public int getCabinRadiusM() {
		return parseInt(database.getConfig("cabin_radius_m"), 5000);
	}

	public int getIntConfig(@NonNull String key, int defaultValue) {
		return parseInt(database.getConfig(key), defaultValue);
	}

	public void setIntConfig(@NonNull String key, int value) {
		database.setConfig(key, String.valueOf(value));
	}

	public double getDoubleConfig(@NonNull String key, double defaultValue) {
		return parseDouble(database.getConfig(key), defaultValue);
	}

	public void setDoubleConfig(@NonNull String key, double value) {
		database.setConfig(key, String.valueOf(value));
	}

	public void setConfigSync(@NonNull String key, @NonNull String value) {
		database.setConfig(key, value);
	}

	public boolean isElevationFirstEnablePromptDoneSync() {
		return "1".equals(database.getConfig("elevation_first_enable_prompt_done"));
	}

	public void setElevationFirstEnablePromptDoneSync(boolean done) {
		database.setConfig("elevation_first_enable_prompt_done", done ? "1" : "0");
	}

	public boolean isElevationRegionDeclinedSync(@NonNull String regionId) {
		return getElevationDeclinedRegionIdsSync().contains(regionId);
	}

	@NonNull
	public Set<String> getElevationDeclinedRegionIdsSync() {
		String raw = database.getConfig("elevation_declined_regions");
		Set<String> ids = new HashSet<>();
		if (Algorithms.isEmpty(raw)) {
			return ids;
		}
		for (String part : raw.split(",")) {
			if (!Algorithms.isEmpty(part)) {
				ids.add(part.trim());
			}
		}
		return ids;
	}

	public void addElevationDeclinedRegionSync(@NonNull String regionId) {
		Set<String> ids = getElevationDeclinedRegionIdsSync();
		if (ids.add(regionId)) {
			database.setConfig("elevation_declined_regions", String.join(",", ids));
		}
	}

	@NonNull
	public String getConfigSync(@NonNull String key, @NonNull String defaultValue) {
		String raw = database.getConfig(key);
		return Algorithms.isEmpty(raw) ? defaultValue : raw;
	}

	// --- Async wrappers for main-thread callers ---

	public interface ConfigCallback<T> {
		void onResult(@NonNull T value);
	}

	public void getTravelModeAsync(@NonNull ConfigCallback<TravelMode> callback) {
		runAsync(() -> callback.onResult(getTravelModeSync()), callback);
	}

	public void getEnergyParamsAsync(@NonNull ConfigCallback<EnergyParams> callback) {
		runAsync(() -> callback.onResult(getEnergyParamsSync()), callback);
	}

	private <T> void runAsync(@NonNull Runnable task, @NonNull ConfigCallback<T> callback) {
		OsmAndTaskManager.executeTask(new AsyncTask<Void, Void, Void>() {
			@Override
			protected Void doInBackground(Void... voids) {
				try {
					task.run();
				} catch (RuntimeException e) {
					LOG.error("DriverBreak: settings async task failed", e);
				}
				return null;
			}
		}, dbExecutor);
	}

	private static double parseDouble(@NonNull String raw, double defaultValue) {
		try {
			return Double.parseDouble(raw);
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	private static int parseInt(@NonNull String raw, int defaultValue) {
		try {
			return Integer.parseInt(raw);
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	private static long parseLong(@NonNull String raw, long defaultValue) {
		try {
			return Long.parseLong(raw);
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}
}
