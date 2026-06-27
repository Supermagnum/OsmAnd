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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import net.osmand.Location;
import net.osmand.plus.R;
import net.osmand.plus.plugins.PluginsHelper;
import net.osmand.plus.settings.fragments.BaseSettingsFragment;
import net.osmand.plus.utils.AndroidUtils;
import net.osmand.plus.utils.UiUtilities;

import java.io.File;

/**
 * Tabbed Driver Break plugin settings (General, Intervals, Overnight, Aerodynamics, Elevation).
 */
public class DriverBreakSettingsFragment extends BaseSettingsFragment {

	public static final String TAG = DriverBreakSettingsFragment.class.getName();

	private static final int[] TAB_PREFS = {
			R.xml.driver_break_settings,
			R.xml.driver_break_settings_intervals,
			R.xml.driver_break_settings_overnight,
			R.xml.driver_break_settings_aerodynamics,
			R.xml.driver_break_settings_elevation
	};

	private static final int[] TAB_TITLES = {
			R.string.driver_break_tab_general,
			R.string.driver_break_tab_intervals,
			R.string.driver_break_tab_overnight,
			R.string.driver_break_tab_aerodynamics,
			R.string.driver_break_tab_elevation
	};

	@Nullable
	private TabLayoutMediator tabLayoutMediator;
	@Nullable
	private ViewPager2 viewPager;

	@Override
	protected void setupPreferences() {
		// Preferences live in tab child fragments.
	}

	@Override
	@SuppressWarnings("RestrictedApi")
	public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
			@Nullable Bundle savedInstanceState) {
		LayoutInflater themed = UiUtilities.getInflater(requireContext(), isNightMode());
		View view = themed.inflate(R.layout.fragment_driver_break_settings_tabs, container, false);
		createToolbar(inflater, view);
		TabLayout tabLayout = view.findViewById(R.id.tab_layout);
		viewPager = view.findViewById(R.id.view_pager);
		viewPager.setAdapter(new DriverBreakSettingsPagerAdapter(this));
		tabLayoutMediator = new TabLayoutMediator(tabLayout, viewPager,
				(tab, position) -> tab.setText(getString(TAB_TITLES[position])));
		tabLayoutMediator.attach();
		view.setBackgroundColor(androidx.core.content.ContextCompat.getColor(app, getBackgroundColorRes()));
		AndroidUtils.addStatusBarPadding21v(requireActionBarActivity(), view);
		return view;
	}

	@Override
	public void onDestroyView() {
		if (tabLayoutMediator != null) {
			tabLayoutMediator.detach();
			tabLayoutMediator = null;
		}
		viewPager = null;
		super.onDestroyView();
	}

	private static final class DriverBreakSettingsPagerAdapter extends FragmentStateAdapter {

		DriverBreakSettingsPagerAdapter(@NonNull Fragment fragment) {
			super(fragment);
		}

		@NonNull
		@Override
		public Fragment createFragment(int position) {
			return DriverBreakSettingsPageFragment.newInstance(TAB_PREFS[position]);
		}

		@Override
		public int getItemCount() {
			return TAB_PREFS.length;
		}
	}

	/**
	 * One tab page inside {@link DriverBreakSettingsFragment}.
	 */
	public static class DriverBreakSettingsPageFragment extends PreferenceFragmentCompat {

		static final String ARG_PREFS_XML = "prefs_xml";

		@Nullable
		private DriverBreakPlugin plugin;

		@NonNull
		static DriverBreakSettingsPageFragment newInstance(int prefsXml) {
			DriverBreakSettingsPageFragment fragment = new DriverBreakSettingsPageFragment();
			Bundle args = new Bundle();
			args.putInt(ARG_PREFS_XML, prefsXml);
			fragment.setArguments(args);
			return fragment;
		}

		@Override
		public void onCreatePreferences(@Nullable Bundle savedInstanceState, @Nullable String rootKey) {
			Bundle args = getArguments();
			int prefsXml = args != null ? args.getInt(ARG_PREFS_XML, R.xml.driver_break_settings)
					: R.xml.driver_break_settings;
			setPreferencesFromResource(prefsXml, rootKey);
			plugin = PluginsHelper.getPlugin(DriverBreakPlugin.class);
			androidx.preference.PreferenceScreen screen = getPreferenceScreen();
			if (screen != null) {
				for (int i = 0; i < screen.getPreferenceCount(); i++) {
					androidx.preference.Preference pref = screen.getPreference(i);
					pref.setOnPreferenceChangeListener(this::onPreferenceChange);
				}
			}
			setupTravelModeList();
			refreshFromDatabase();
		}

		@Override
		public void onResume() {
			super.onResume();
			refreshFromDatabase();
		}

		@Override
		public boolean onPreferenceTreeClick(@NonNull Preference preference) {
			if ("driver_break_download_current_tile".equals(preference.getKey()) && plugin != null) {
				net.osmand.plus.OsmandApplication application =
						(net.osmand.plus.OsmandApplication) requireContext().getApplicationContext();
				Location location = application.getLocationProvider().getLastKnownLocation();
				if (location != null) {
					plugin.getElevationDownloadManager().downloadTileAsync(location.getLatitude(),
							location.getLongitude(), null);
				}
				return true;
			}
			return super.onPreferenceTreeClick(preference);
		}

		private boolean onPreferenceChange(@NonNull Preference preference, Object newValue) {
			return DriverBreakSettingsController.applyChange(plugin, preference.getKey(), newValue);
		}

		private void setupTravelModeList() {
			Preference travelMode = findPreference("driver_break_travel_mode");
			if (!(travelMode instanceof net.osmand.plus.settings.preferences.ListPreferenceEx)) {
				return;
			}
			net.osmand.plus.settings.preferences.ListPreferenceEx list =
					(net.osmand.plus.settings.preferences.ListPreferenceEx) travelMode;
			TravelMode[] modes = TravelMode.values();
			String[] entries = new String[modes.length];
			String[] entryValues = new String[modes.length];
			for (int i = 0; i < modes.length; i++) {
				entries[i] = getString(travelModeTitle(modes[i]));
				entryValues[i] = modes[i].getConfigKey();
			}
			list.setEntries(entries);
			list.setEntryValues(entryValues);
		}

		private void refreshFromDatabase() {
			DriverBreakPlugin activePlugin = plugin;
			if (activePlugin == null || getContext() == null) {
				return;
			}
			DriverBreakSettings settings = activePlugin.getSettings();
			File tileDir = activePlugin.getElevationProvider().getTileDirectory();
			net.osmand.plus.OsmandApplication application =
					(net.osmand.plus.OsmandApplication) requireContext().getApplicationContext();
			settings.getDbExecutor().execute(() -> {
				DriverBreakSettingsSnapshot snapshot = DriverBreakSettingsSnapshot.load(settings, tileDir);
				application.runInUIThread(() -> applySnapshot(snapshot));
			});
		}

		private void applySnapshot(@NonNull DriverBreakSettingsSnapshot snapshot) {
			setListValue("driver_break_travel_mode", snapshot.travelModeKey);
			setSwitch("driver_break_use_energy_routing", snapshot.useEnergyRouting);
			setSwitch("driver_break_ecu_enabled", snapshot.ecuEnabled);
			setSwitch("driver_break_adaptive_fuel", snapshot.adaptiveFuelEnabled);
			setSwitch("driver_break_water_pois_enabled", snapshot.waterPoisEnabled);
			setSwitch("driver_break_dnt_priority", snapshot.dntPriority);
			setEditText("driver_break_car_soft_limit_h", snapshot.carSoftLimitH);
			setEditText("driver_break_car_max_limit_h", snapshot.carMaxLimitH);
			setEditText("driver_break_car_break_interval_h", snapshot.carBreakIntervalH);
			setEditText("driver_break_car_break_duration_min", snapshot.carBreakDurationMin);
			setEditText("driver_break_truck_mandatory_break_h", snapshot.truckMandatoryBreakH);
			setEditText("driver_break_truck_break_duration_min", snapshot.truckBreakDurationMin);
			setEditText("driver_break_truck_max_daily_h", snapshot.truckMaxDailyH);
			setEditText("driver_break_hiking_main_dist_km", snapshot.hikingMainDistKm);
			setEditText("driver_break_cycling_main_dist_km", snapshot.cyclingMainDistKm);
			setEditText("driver_break_moto_soft_limit_min", snapshot.motoSoftLimitMin);
			setEditText("driver_break_moto_mandatory_break_min", snapshot.motoMandatoryBreakMin);
			setEditText("driver_break_poi_radius_m", snapshot.poiRadiusM);
		 setEditText("driver_break_water_radius_m", snapshot.waterRadiusM);
			setEditText("driver_break_cabin_radius_m", snapshot.cabinRadiusM);
			setEditText("driver_break_min_dist_buildings_m", snapshot.minDistBuildingsM);
			setEditText("driver_break_min_dist_glaciers_m", snapshot.minDistGlaciersM);
			setEditText("driver_break_total_weight", snapshot.totalWeightKg);
			setEditText("driver_break_energy_drag_cd", snapshot.dragCd);
			setEditText("driver_break_energy_frontal_area_sqm", snapshot.frontalAreaM2);
			Preference tilesSummary = findPreference("driver_break_srtm_tiles_summary");
			if (tilesSummary != null) {
				tilesSummary.setSummary(getString(R.string.driver_break_srtm_tiles_summary_value,
						snapshot.srtmTileCount));
			}
		}

		private void setSwitch(@NonNull String key, boolean checked) {
			SwitchPreferenceCompat pref = findPreference(key);
			if (pref != null) {
				pref.setChecked(checked);
			}
		}

		private void setListValue(@NonNull String key, @NonNull String value) {
			Preference pref = findPreference(key);
			if (pref instanceof net.osmand.plus.settings.preferences.ListPreferenceEx) {
				((net.osmand.plus.settings.preferences.ListPreferenceEx) pref).setValue(value);
			}
		}

		private void setEditText(@NonNull String key, @NonNull String value) {
			Preference pref = findPreference(key);
			if (pref instanceof androidx.preference.EditTextPreference) {
				((androidx.preference.EditTextPreference) pref).setText(value);
				pref.setSummary(value);
			}
		}

		private static int travelModeTitle(@NonNull TravelMode mode) {
			switch (mode) {
				case TRUCK:
					return R.string.driver_break_mode_truck;
				case HIKING:
					return R.string.driver_break_mode_hiking;
				case CYCLING:
					return R.string.driver_break_mode_cycling;
				case MOTORCYCLE:
					return R.string.driver_break_mode_motorcycle;
				case CAR:
				default:
					return R.string.driver_break_mode_car;
			}
		}
	}
}
