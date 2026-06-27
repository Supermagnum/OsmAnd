---
sidebar_position: 6
title: Driver Break
---

import Tabs from '@theme/Tabs';
import TabItem from '@theme/TabItem';
import AndroidStore from '@site/src/components/buttons/AndroidStore.mdx';
import AppleStore from '@site/src/components/buttons/AppleStore.mdx';
import LinksTelegram from '@site/src/components/_linksTelegram.mdx';
import InfoAndroidOnly from '@site/src/components/_infoAndroidOnly.mdx';

<InfoAndroidOnly />

# Driver Break {#driver-break}

## Overview {#overview}

The **Driver Break** plugin helps you plan and take rest stops while travelling by car, truck, motorcycle, on foot, or by bicycle. It tracks driving time or distance according to your travel mode, reminds you when a break is due, and can search OpenStreetMap for nearby points of interest (POIs) such as water sources, cabins, cafes, and fuel stations. Where applicable, it validates hiking and cycling routes and can compare calculated routes for predicted energy use.

When a compatible vehicle ECU is connected, the plugin reads live fuel consumption to refine range estimates and optional eco-weighted route comparison. The plugin is available on **Android** and does not require an OsmAnd Pro subscription.

## Required Setup {#required-setup}

1. Enable the Driver Break plugin via *Menu → Plugins → Driver Break*.
2. Open *Menu → Plugins → Driver Break → Settings* and select a **travel mode** on the General tab.
3. On the **Intervals** tab, configure break thresholds for that mode (hours for car/truck/motorcycle, kilometres for hiking/cycling).
4. Add the **Next break** widget via *Menu → Configure Screen → Other → Driver Break*.
5. (Optional) Enable **Use ECU fuel data** on the General tab and connect an OBD-II adapter through the [Vehicle Metrics](./vehicle-metrics.md) plugin.
6. (Optional) On the **Elevation** tab, download SRTM elevation tiles for your region when prompted, or when planning routes that cross areas without cached tiles.

## Travel Modes {#travel-modes}

### Car {#travel-mode-car}

Car mode tracks **driving time**. You can set a soft limit (default 7 hours), a maximum driving period (default 10 hours), a break interval (default every 4 hours), and break duration (default 30 minutes). Rest stops are placed along the driven route on suitable road types after a minimum distance; nearby POIs include cafes, restaurants, museums, viewpoints, and picnic areas. Enable **Water POIs** on the Overnight tab for remote or hot-climate trips to include drinking water, fountains, and springs at stops.

### Truck {#travel-mode-truck}

Truck mode applies **EU Regulation EC 561/2006**-style limits by default: mandatory break after 4 hours of driving, 45-minute break duration, and a maximum of 9 hours daily driving. Rest-stop search and POI types are similar to car mode. Water POIs for remote or arid conditions use the same Overnight setting as car.

:::tip
For truck drivers, default interval values align with EU Regulation EC 561/2006 break requirements. Adjust them only if your jurisdiction or employer rules differ.
:::

### Hiking {#travel-mode-hiking}

Hiking mode tracks **distance**. Default main rest intervals follow the historical Scandinavian **rast** unit (11.295 km main stage, 2.275 km alternative stage, 40 km suggested maximum per day). Rest stops include water (drinking water, fountain, spring) and cabins or huts (wilderness hut, alpine hut, hostel, camping). Optional SRTM elevation supports energy-aware route comparison when enabled.

### Cycling {#travel-mode-cycling}

Cycling mode uses the same **rast/vei** concept scaled for bicycles: default 28.24 km main stage, 5.69 km alternative stage, and 100 km suggested maximum per day. Water and cabin POIs match hiking. Charging stations, bicycle repair, and related service POIs are searched at rest stops when present in map data or via Overpass fallback.

### Motorcycle {#travel-mode-motorcycle}

Motorcycle mode tracks **riding time** with defaults of a 2-hour soft limit, mandatory break after 3.5 hours, and 15–30 minute break duration (configurable in minutes in OsmAnd). POI search matches car plus `amenity=motorcycle_repair` and `shop=motorcycle` when mapped. Fuel data uses OBD-II on Euro 4+ bikes where supported; otherwise adaptive fuel learning or manual values apply. Energy routing uses configurable rider-plus-bike mass (default total mass in settings should reflect roughly 250 kg rider and gear unless you change it).

:::note
**Adventure/dual-sport terrain** (where implemented in routing profiles) is limited to legally accessible ways: the plugin must not route across uncultivated land, unmapped terrain, or ways tagged `access=private` or `access=no`. Only `highway=track` and similar ways with explicit `access=yes`, `access=permissive`, or `motorcycle=yes` / `designated` / `permissive` are considered. Off-road motor traffic on uncultivated land is prohibited without a permit in many countries—including Norway (Motorferdselloven, 1977), Sweden (Terrängkörningslagen, 1975:1313), Finland (Maastoliikennelaki, 1710/1995), and similar laws elsewhere.
:::

## Rest Stop Suggestions {#rest-stop-suggestions}

The plugin suggests stops based on your travel mode:

- **Hiking and cycling** — Rest positions are placed at configured **distance intervals** along the route (main and alternative stage distances, plus a daily maximum).
- **Car, truck, and motorcycle** — The plugin walks the **route geometry**, accumulates segment length, and after a minimum distance (about 5 km) looks for suitable highway types (for example unclassified, service, track, tertiary). Each candidate is checked against building and glacier distance rules before POIs are attached.

**POI search** prefers **map-based** OsmAnd search within configurable radii (defaults: water 2 km, cabins 5 km, general POIs 15 km, network huts up to 25 km). If map data is insufficient, **Overpass API** fallback queries run in the background. **DNT/network priority** (Overnight tab) prefers Norwegian Trekking Association and similar network cabins when sorting results. **Hiking/pilgrimage priority** adds places of worship within the general POI radius on hiking and cycling stages.

**Distance from buildings** — For overnight or camping-style stops (for example under right-to-roam rules such as Norwegian *allemannsretten*), candidates closer than the configured minimum (default 150 m) to buildings or dwellings are rejected.

**Distance from glaciers** — Nightly camping positions closer than the configured minimum (default 300 m) to glaciers are rejected unless a camping building is present at the site.

:::caution Water sources and filtration
When you use water from natural sources (streams, lakes, or springs) suggested as rest-stop POIs, **always treat or filter water before drinking**. Use a portable filter with:

- A hollow-fibre or ceramic membrane rated at **0.1 micron or smaller** to remove bacteria, protozoa, and parasites such as Giardia and Cryptosporidium.
- An **activated carbon** stage (preferably carbon block) to reduce pesticides, herbicides, heavy metals, chlorine, and improve taste and odour. Prefer filters certified to **NSF/ANSI 42 and 53**.

Standard filters do **not** remove viruses. In remote wilderness (Norway, Sweden, Finland) viral risk is generally low, but near settlements, farms, or grazing land consider a **0.02 micron** filter or chemical treatment (iodine tablets, chlorine dioxide) as a second stage.

Water inside Norwegian national parks and nature reserves is often considered safe to drink directly because of protection from agricultural runoff and industry—but collect from **fast-moving or high-altitude** sources rather than slow or stagnant water, and avoid points immediately downstream of trails, campsites, or huts.

Even POIs tagged `amenity=drinking_water` on OpenStreetMap may be outdated; treat remote sources with caution.
:::

## Energy-Efficient Routing {#energy-efficient-routing}

**Energy-aware routing** (kinetic routing) compares calculated route variants using a **kinematic energy model**: rolling resistance, aerodynamic drag, grade (from elevation data), and downhill recuperation contribute to a cost per segment. After a route is calculated, OsmAnd can suggest an alternative that uses less predicted energy if it is not much longer (default thresholds: at least 5% energy saving with at most 20% distance increase).

Enable **Energy-aware routing** on the General tab. Set **total mass**, **drag coefficient Cd**, and **frontal area** on the Aerodynamics tab so they match your current mode (car laden weight, rider and bicycle, and so on). The plugin stores **one** mass/Cd/area triple at a time; changing travel mode does not load separate presets automatically.

:::note
Energy-based routing requires a **terrain profile** (per-segment elevation) along the route. Without downloaded SRTM tiles, grade terms are not meaningful and the model cannot reliably prefer flatter paths. Download elevation data for your regions on the Elevation tab or accept the prompt when enabling the plugin or when a route crosses an uncovered area.
:::

For formulas and implementation details, see the [Driver Break energy model technical doc](../../technical/build-osmand/driver-break-energy-model.md).

## Elevation Data (SRTM) {#elevation-data-srtm}

Elevation tiles are **1° × 1°** cells stored locally under app data. Downloads try sources **in order**:

1. **Copernicus DEM GLO-30** (GeoTIFF from AWS S3)
2. **Viewfinder Panoramas dem3** (HGT)
3. **NASA SRTMGL1** (HGT fallback)

When you enable the plugin or plan a route into a region without tiles, OsmAnd prompts you to download missing tiles for that **county or country** (from OsmAnd region boundaries). Declined regions are remembered; disabling the plugin **does not delete** cached tiles.

**Void / missing samples** — Source DEM uses **-32768** as void; the plugin treats missing tiles or void pixels as unknown elevation and skips grade discrimination for those segments.

<!-- SCREENSHOT: Menu → Plugins → Driver Break → Settings → Elevation tab -->
![Driver Break elevation settings](@site/static/img/plugins/driver-break/settings_elevation.png)

## ECU Fuel Data {#ecu-fuel-data}

### OBD-II {#ecu-obd-ii}

For cars and light vehicles, the plugin reads **fuel level** (PID 0x2F) and **fuel rate** (PID 0x5E) through OsmAnd’s [Vehicle Metrics](./vehicle-metrics.md) OBD-II connection (ELM327-compatible Bluetooth adapters). If PID 0x5E is unavailable, fuel rate may be estimated from **MAF** (PID 0x10) using stoichiometric air–fuel ratio and fuel density for your fuel type.

### J1939 {#ecu-j1939}

For trucks, a **USB-CAN** adapter at **500000 bps** can decode **PGN 0xF003** (engine fuel rate, SPN 183, 0.05 L/h per bit) and **PGN 0xFEF2** (fuel level, SPN 96, 0.4% per bit). Navit reference also documents PGN 65266 / 65276 with the same scaling factors.

### MegaSquirt {#ecu-megasquirt}

Support for **MegaSquirt** (MS1, MS2, MS3, MS3-Pro, MicroSquirt) is **planned** but **not yet available** in this OsmAnd release. When implemented, it will read injector pulse width and RPM over serial (115200 8N1) similar to the Navit reference backend.

### Adaptive fuel learning {#adaptive-fuel-learning}

With **Adaptive fuel learning** enabled, the plugin records fuel and distance samples in SQLite and maintains a learned **litres per kilometre** rate using an exponential moving average. This refines estimates when no live ECU is connected. Disable the toggle to stop recording new samples.

For PID tables and protocol detail, see [Driver Break ECU protocols](../../technical/build-osmand/driver-break-ecu-protocols.md).

## Widget — Next Break {#widget--next-break}

Add the widget from *Menu → Configure Screen → Other → Driver Break → Next break*. It shows **remaining time** until the next recommended break for car, truck, and motorcycle, or **remaining distance** for hiking and cycling. The value updates as you move and when break settings or travel mode change.

<!-- SCREENSHOT: Map screen with Driver Break Next break widget visible -->
![Driver Break next break widget](@site/static/img/plugins/driver-break/widget_next_break.png)

## Settings Reference {#settings-reference}

Open *Menu → Plugins → Driver Break → Settings*.

### General {#settings-general}

| Setting | Description |
|---|---|
| **Travel mode** | Selects Car, Truck, Hiking, Cycling, or Motorcycle. |
| **Energy-aware routing** | After route calculation, compare variants and suggest lower-energy alternatives when thresholds are met. |
| **Use ECU fuel data** | Use live OBD-II or J1939 fuel rate and level when connected (requires Vehicle Metrics for OBD-II). |
| **Adaptive fuel learning** | Record fuel/distance samples and maintain a learned consumption rate (litres per km). |

### Intervals {#settings-intervals}

| Setting | Description |
|---|---|
| **Car soft limit (hours)** | Hours before a soft break reminder (default 7; configurable). |
| **Car maximum driving (hours)** | Maximum continuous driving period (default 10). |
| **Car break interval (hours)** | Interval between suggested breaks (default 4; often 4–4.5 h). |
| **Car break duration (minutes)** | Suggested break length (default 30; often 15–45 min). |
| **Truck mandatory break (hours)** | Driving time before a mandatory break (default 4; EU 561/2006 uses 4.5 h max continuous driving—adjust to match your rules). |
| **Truck break duration (minutes)** | Mandatory break length (default 45). |
| **Truck max daily driving (hours)** | Daily driving cap (default 9). |
| **Hiking main stage (km)** | Main rest interval distance (default 11.295 km). |
| **Cycling main stage (km)** | Main rest interval distance (default 28.24 km). |
| **Motorcycle soft limit (minutes)** | Soft riding limit (default 120). |
| **Motorcycle mandatory break (minutes)** | Mandatory break after this riding time (default 210). |

:::note
Hiking and cycling also support **alternative stage** distances (defaults 2.275 km and 5.69 km) and **daily maximum** distances (defaults 40 km and 100 km) in the plugin database; the Intervals tab in this release exposes the main stage values. Motorcycle **break duration** defaults to 20 minutes in the database (within the documented 15–30 minute range).
:::

### Overnight {#settings-overnight}

| Setting | Description |
|---|---|
| **Water POIs** | Include drinking water, fountains, and springs at car/truck/motorcycle stops in remote or hot conditions (also affects hiking water search labelling in this build). |
| **DNT/network hut priority** | Prefer network-operated cabins (DNT, STF, DAV, SAC, and similar) when sorting hut results. |
| **General POI search radius (m)** | Radius for cafes, viewpoints, worship, and general amenities (default 15000). |
| **Water POI radius (m)** | Radius for water POIs (default 2000). |
| **Cabin search radius (m)** | Radius for huts and cabins (default 5000). |
| **Minimum distance from buildings (m)** | Camping/overnight clearance from buildings (default 150). |
| **Minimum distance from glaciers (m)** | Overnight clearance from glaciers (default 300). |

### Aerodynamics {#settings-aerodynamics}

| Setting | Description |
|---|---|
| **Total mass (kg)** | Mass for energy model: vehicle, cargo, rider, and gear (**1–50000** kg). |
| **Drag coefficient Cd** | Aerodynamic drag coefficient, dimensionless (**0.01–1.5**). |
| **Frontal area (m²)** | Projected frontal area for drag (**0.05–20** m²). |

### Elevation {#settings-elevation}

| Setting | Description |
|---|---|
| **Cached elevation tiles** | Read-only count of local HGT and GeoTIFF tiles. |
| **Download tile at current location** | Queues download of the 1° tile at your current map position or GPS location. |
