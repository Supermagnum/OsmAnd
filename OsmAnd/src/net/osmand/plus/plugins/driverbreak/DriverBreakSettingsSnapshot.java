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

import java.io.File;

/**
 * Point-in-time snapshot of Driver Break SQLite settings for UI binding.
 */
final class DriverBreakSettingsSnapshot {

	final String travelModeKey;
	final boolean useEnergyRouting;
	final boolean ecuEnabled;
	final boolean adaptiveFuelEnabled;
	final boolean waterPoisEnabled;
	final boolean dntPriority;
	final String carSoftLimitH;
	final String carMaxLimitH;
	final String carBreakIntervalH;
	final String carBreakDurationMin;
	final String truckMandatoryBreakH;
	final String truckBreakDurationMin;
	final String truckMaxDailyH;
	final String hikingMainDistKm;
	final String cyclingMainDistKm;
	final String motoSoftLimitMin;
	final String motoMandatoryBreakMin;
	final String poiRadiusM;
	final String waterRadiusM;
	final String cabinRadiusM;
	final String minDistBuildingsM;
	final String minDistGlaciersM;
	final String totalWeightKg;
	final String dragCd;
	final String frontalAreaM2;
	final int srtmTileCount;

	private DriverBreakSettingsSnapshot(@NonNull String travelModeKey, boolean useEnergyRouting,
			boolean ecuEnabled, boolean adaptiveFuelEnabled, boolean waterPoisEnabled, boolean dntPriority,
			@NonNull String carSoftLimitH, @NonNull String carMaxLimitH, @NonNull String carBreakIntervalH,
			@NonNull String carBreakDurationMin, @NonNull String truckMandatoryBreakH,
			@NonNull String truckBreakDurationMin, @NonNull String truckMaxDailyH,
			@NonNull String hikingMainDistKm, @NonNull String cyclingMainDistKm,
			@NonNull String motoSoftLimitMin, @NonNull String motoMandatoryBreakMin,
			@NonNull String poiRadiusM, @NonNull String waterRadiusM, @NonNull String cabinRadiusM,
			@NonNull String minDistBuildingsM, @NonNull String minDistGlaciersM,
			@NonNull String totalWeightKg, @NonNull String dragCd, @NonNull String frontalAreaM2,
			int srtmTileCount) {
		this.travelModeKey = travelModeKey;
		this.useEnergyRouting = useEnergyRouting;
		this.ecuEnabled = ecuEnabled;
		this.adaptiveFuelEnabled = adaptiveFuelEnabled;
		this.waterPoisEnabled = waterPoisEnabled;
		this.dntPriority = dntPriority;
		this.carSoftLimitH = carSoftLimitH;
		this.carMaxLimitH = carMaxLimitH;
		this.carBreakIntervalH = carBreakIntervalH;
		this.carBreakDurationMin = carBreakDurationMin;
		this.truckMandatoryBreakH = truckMandatoryBreakH;
		this.truckBreakDurationMin = truckBreakDurationMin;
		this.truckMaxDailyH = truckMaxDailyH;
		this.hikingMainDistKm = hikingMainDistKm;
		this.cyclingMainDistKm = cyclingMainDistKm;
		this.motoSoftLimitMin = motoSoftLimitMin;
		this.motoMandatoryBreakMin = motoMandatoryBreakMin;
		this.poiRadiusM = poiRadiusM;
		this.waterRadiusM = waterRadiusM;
		this.cabinRadiusM = cabinRadiusM;
		this.minDistBuildingsM = minDistBuildingsM;
		this.minDistGlaciersM = minDistGlaciersM;
		this.totalWeightKg = totalWeightKg;
		this.dragCd = dragCd;
		this.frontalAreaM2 = frontalAreaM2;
		this.srtmTileCount = srtmTileCount;
	}

	@NonNull
	static DriverBreakSettingsSnapshot load(@NonNull DriverBreakSettings settings, @NonNull File srtmTileDir) {
		int tileCount = 0;
		if (srtmTileDir.isDirectory()) {
			File[] files = srtmTileDir.listFiles();
			if (files != null) {
				for (File file : files) {
					if (file.isFile() && (file.getName().endsWith(".hgt") || file.getName().endsWith(".tif"))) {
						tileCount++;
					}
				}
			}
		}
		return new DriverBreakSettingsSnapshot(
				settings.getTravelModeSync().getConfigKey(),
				settings.isUseEnergyRoutingSync(),
				settings.isEcuEnabledSync(),
				settings.isAdaptiveFuelEnabledSync(),
				settings.isWaterPoisEnabledSync(),
				settings.isDntPrioritySync(),
				String.valueOf(settings.getIntConfig("car_soft_limit_h", 7)),
				String.valueOf(settings.getIntConfig("car_max_limit_h", 10)),
				String.valueOf(settings.getIntConfig("car_break_interval_h", 4)),
				String.valueOf(settings.getIntConfig("car_break_duration_min", 30)),
				String.valueOf(settings.getIntConfig("truck_mandatory_break_h", 4)),
				String.valueOf(settings.getIntConfig("truck_break_duration_min", 45)),
				String.valueOf(settings.getIntConfig("truck_max_daily_h", 9)),
				String.valueOf(settings.getDoubleConfig("hiking_main_dist_km", 11.295)),
				String.valueOf(settings.getDoubleConfig("cycling_main_dist_km", 28.24)),
				String.valueOf(settings.getIntConfig("moto_soft_limit_min", 120)),
				String.valueOf(settings.getIntConfig("moto_mandatory_break_min", 210)),
				String.valueOf(settings.getPoiRadiusM()),
				String.valueOf(settings.getWaterRadiusM()),
				String.valueOf(settings.getCabinRadiusM()),
				String.valueOf(settings.getMinDistBuildingsM()),
				String.valueOf(settings.getMinDistGlaciersM()),
				settings.getConfigSync("total_weight", "80.0"),
				settings.getConfigSync("energy_drag_cd", "0.30"),
				settings.getConfigSync("energy_frontal_area_sqm", "2.2"),
				tileCount);
	}
}
