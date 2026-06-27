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
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import net.osmand.plus.R;
import net.osmand.plus.base.MenuBottomSheetDialogFragment;
import net.osmand.plus.base.bottomsheetmenu.BaseBottomSheetItem;
import net.osmand.plus.settings.backend.ApplicationMode;
import net.osmand.plus.utils.AndroidUtils;

/**
 * Informs the user when a lower-energy route variant is available.
 */
public class EnergyRouteBottomSheet extends MenuBottomSheetDialogFragment {

	public static final String TAG = EnergyRouteBottomSheet.class.getName();

	private static final String ARG_SAVING_PERCENT = "saving_percent";
	private static final String ARG_DISTANCE_INCREASE_PERCENT = "distance_increase_percent";

	@Override
	public void createMenuItems(Bundle savedInstanceState) {
		Bundle args = getArguments();
		int savingPercent = args != null ? args.getInt(ARG_SAVING_PERCENT, 0) : 0;
		int distanceIncreasePercent = args != null ? args.getInt(ARG_DISTANCE_INCREASE_PERCENT, 0) : 0;

		View root = inflate(R.layout.bottom_sheet_icon_title_description);
		android.widget.TextView title = root.findViewById(R.id.title);
		android.widget.TextView description = root.findViewById(R.id.description);
		title.setText(R.string.driver_break_energy_route_title);
		description.setText(getString(R.string.driver_break_energy_route_message, savingPercent,
				distanceIncreasePercent));
		items.add(new BaseBottomSheetItem.Builder().setCustomView(root).create());
	}

	@Override
	protected int getRightBottomButtonTextId() {
		return R.string.shared_string_ok;
	}

	@Override
	protected void onRightBottomButtonClick() {
		dismiss();
	}

	/**
	 * Shows the energy-saving route hint when a cheaper alternative exists.
	 */
	public static void showInstance(@NonNull FragmentManager fragmentManager, @Nullable Fragment target,
			@Nullable ApplicationMode appMode, boolean usedOnMap, double energySavingFraction,
			double distanceIncreaseFraction) {
		if (!AndroidUtils.isFragmentCanBeAdded(fragmentManager, TAG)) {
			return;
		}
		EnergyRouteBottomSheet sheet = new EnergyRouteBottomSheet();
		Bundle args = new Bundle();
		args.putInt(ARG_SAVING_PERCENT, (int) Math.round(energySavingFraction * 100.0));
		args.putInt(ARG_DISTANCE_INCREASE_PERCENT, (int) Math.round(distanceIncreaseFraction * 100.0));
		sheet.setArguments(args);
		sheet.setUsedOnMap(usedOnMap);
		sheet.setTargetFragment(target, 0);
		sheet.setAppMode(appMode);
		sheet.show(fragmentManager, TAG);
	}
}
