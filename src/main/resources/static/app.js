"use strict";

// Symbology on two axes, so nothing is arbitrary and nothing depends on colour
// alone.
//
//   SHAPE  says where the mark came from: circle = radar, triangle = AIS.
//   SIZE and COLOUR say whether it is explained: large and red = unexplained,
//   small and grey = explained.
//
// Red belongs to "no transponder" and to nothing else on this map. A second
// accent would divide the attention the headline mark is supposed to own, which
// is why a detection confirmed by AIS went from blue to grey.
const MARK = {
  dark: {
    box: 24,
    svg: '<svg class="mk mk-dark" width="24" height="24" viewBox="0 0 24 24">'
      + '<circle class="halo" cx="12" cy="12" r="9.5"/>'
      + '<circle class="body" cx="12" cy="12" r="7.5"/></svg>'
  },
  lit: {
    box: 14,
    svg: '<svg class="mk mk-lit" width="14" height="14" viewBox="0 0 14 14">'
      + '<circle class="halo" cx="7" cy="7" r="5.5"/>'
      + '<circle class="body" cx="7" cy="7" r="4"/></svg>'
  },
  ais: {
    box: 13,
    svg: '<svg class="mk mk-ais" width="13" height="13" viewBox="0 0 13 13">'
      + '<polygon class="halo" points="6.5,0.8 12.6,11.8 0.4,11.8"/>'
      + '<polygon class="body" points="6.5,2.6 11.1,10.6 1.9,10.6"/></svg>'
  }
};

// The same shapes at one size, for the vessel list and the layer panel.
const GLYPH = {
  dark: '<svg class="glyph mk mk-dark" width="12" height="12" viewBox="0 0 12 12">'
    + '<circle class="body" cx="6" cy="6" r="4.6"/></svg>',
  lit: '<svg class="glyph mk mk-lit" width="12" height="12" viewBox="0 0 12 12">'
    + '<circle class="body" cx="6" cy="6" r="3.4"/></svg>',
  ais: '<svg class="glyph mk mk-ais" width="12" height="12" viewBox="0 0 12 12">'
    + '<polygon class="body" points="6,1.4 11,10.4 1,10.4"/></svg>'
};

// The three things the map can draw, each its own switch.
//
// There used to be five rows here, one chosen at a time, of which two were
// combinations of the other three — "all detections" and "both layers". With
// independent checkboxes those combinations are just two boxes ticked, and
// keeping them as rows of their own would have been two controls doing one job.
//
// "AIS, no echo" was called "AIS only" while the rows were exclusive, where it
// meant "show only this". As a checkbox that reads as the opposite of what the
// layer is, which is an AIS position the radar looked at and saw nothing at.
//
// There used to be a fourth layer as well, the AIS point of a vessel already on
// screen as a detection a median 37 m away — a duplicate beside a duplicate.
// What it was good for was checking a pairing, which is now in the matched
// detection's tooltip and in a connector line drawn for the selected mark only.
const LAYERS = [
  { id: "dark", name: "Without transponder", glyph: "dark", pane: "layerDark", lead: true },
  { id: "lit", name: "With transponder", glyph: "lit", pane: "layerLit" },
  { id: "aisAlone", name: "AIS, no echo", glyph: "ais", pane: "layerAisAlone" }
];

/**
 * Bottom to top. Set with real Leaflet panes rather than zIndexOffset, because
 * offsets are relative to a marker's latitude and a southerly AIS dot could
 * still land on top of a northerly dark detection. The order is the importance
 * order: the mark this whole application exists to produce is never underneath
 * anything.
 */
const PANE_STACK = ["layerAisAlone", "layerLit", "layerDark"];

/**
 * One mark per vessel, not per report.
 *
 * <p>This is the fix for a real defect. The API returns AIS positions, and a
 * vessel reports several times inside the six-minute window — measured on the
 * 2026-09-07 pass, 26 ships reported once, 15 twice, 14 three times and one five
 * times. Drawing every report put up to five triangles on one ship and made the
 * layer counts disagree with the detection counts: 18 matched vessels showed as
 * 29, and 38 unmatched vessels as 60.
 *
 * <p>The report kept is the one closest in time to the acquisition, which is the
 * position the radar would have seen.
 */
function oneMarkPerVessel(features) {
  const passAt = detail && detail.summary.acquisitionTime
    ? Date.parse(detail.summary.acquisitionTime)
    : null;

  const nearest = new Map();
  for (const feature of features) {
    const mmsi = feature.properties.mmsi;
    const rival = nearest.get(mmsi);
    if (!rival || fromPass(feature, passAt) < fromPass(rival, passAt)) {
      nearest.set(mmsi, feature);
    }
  }
  return [...nearest.values()];
}

function fromPass(feature, passAt) {
  if (passAt === null || !feature.properties.timestamp) return Number.MAX_SAFE_INTEGER;
  return Math.abs(Date.parse(feature.properties.timestamp) - passAt);
}

// ------------------------------------------------------- near-shore filter

// The choices offered, in metres. Measured on the 2026-09-07 pass, where the
// without-transponder detections have a median distance of 154 m and 63 of 101
// sit inside 300 m, while only 2 of the 18 AIS-confirmed ones do. The two
// populations are that different, which is what makes this worth a control.
const SHORE_STEPS = [
  { metres: 0, label: "off" },
  { metres: 300, label: "300 m" },
  { metres: 500, label: "500 m" },
  { metres: 1000, label: "1 km" }
];

const SHORE_KEY = "shipviewer.hideNearShore";

// Off by default. Some of what it hides is real — a 250 m ship at a Burgas quay
// is a ship — so this is a question the reader answers, not one answered for
// them.
let hideNearShoreM = 0;

function loadShoreState() {
  try {
    const stored = Number(localStorage.getItem(SHORE_KEY));
    if (SHORE_STEPS.some(step => step.metres === stored)) hideNearShoreM = stored;
  } catch (ignored) {
    // Site data blocked: off, as on a first visit.
  }
}

function saveShoreState() {
  try {
    localStorage.setItem(SHORE_KEY, String(hideNearShoreM));
  } catch (ignored) {
    // The choice simply will not survive a reload.
  }
}

function describeShore(metres) {
  const step = SHORE_STEPS.find(candidate => candidate.metres === metres);
  return step ? step.label : metres + " m";
}

/**
 * Whether a detection survives the near-shore filter.
 *
 * <p>A detection from a run processed before the pipeline measured the distance
 * has none, and is kept. Hiding it would be inventing a measurement: "unknown"
 * is not "close to shore", and an old run must not quietly lose marks because a
 * later version learned to measure something.
 */
function farEnoughFromShore(properties) {
  if (hideNearShoreM === 0) return true;
  const distance = properties.distanceToShoreM;
  if (distance === null || distance === undefined) return true;
  return distance >= hideNearShoreM;
}

/** Every feature of the run, split into the three layers. */
function featuresByLayer() {
  const detections = detail ? detail.detections.features : [];
  const ais = detail ? detail.ais.features : [];

  // Only detections are filtered. An AIS position is a vessel reporting itself
  // and is never shoreline clutter, so measuring it against the coast would
  // hide real traffic in a harbour for no reason.
  const kept = detections.filter(f => farEnoughFromShore(f.properties));

  return {
    dark: kept.filter(f => !f.properties.matched)
      .map(f => ({ kind: "dark", p: f.properties })),
    lit: kept.filter(f => f.properties.matched)
      .map(f => ({ kind: "lit", p: f.properties })),
    aisAlone: oneMarkPerVessel(ais.filter(f => !f.properties.matched))
      .map(f => ({ kind: "ais", p: f.properties }))
  };
}

/**
 * The AIS position a matched detection was paired with, for the connector line.
 * Same nearest-in-time rule, so the line points at the mark the AIS layer would
 * have drawn had the vessel not been matched.
 */
function aisPositionOf(mmsi) {
  if (!detail || !mmsi) return null;
  const forVessel = detail.ais.features.filter(f => f.properties.mmsi === mmsi);
  const best = oneMarkPerVessel(forVessel);
  return best.length > 0 ? best[0].properties : null;
}

const LAYERS_KEY = "shipviewer.layers";

// All three on to begin with: a first visit should show everything the run
// found, and let the reader switch things off to simplify.
let layerOn = { dark: true, lit: true, aisAlone: true };

function loadLayerState() {
  try {
    const stored = JSON.parse(localStorage.getItem(LAYERS_KEY) || "null");
    if (!stored) return;
    for (const layer of LAYERS) {
      if (typeof stored[layer.id] === "boolean") layerOn[layer.id] = stored[layer.id];
    }
  } catch (ignored) {
    // Site data blocked, or a value left by an older shape of this panel: the
    // defaults stand.
  }
}

function saveLayerState() {
  try {
    localStorage.setItem(LAYERS_KEY, JSON.stringify(layerOn));
  } catch (ignored) {
    // The combination simply will not survive a reload.
  }
}

function layersShown() {
  return LAYERS.filter(layer => layerOn[layer.id]);
}

/** How the side panel names whatever is currently switched on. */
function describeLayers() {
  const shown = layersShown();
  if (shown.length === 0) return "nothing shown";
  if (shown.length === LAYERS.length) return "all layers";
  return shown.map(layer => layer.name.toLowerCase()).join(" + ");
}

function renderLayerPanel(byLayer) {
  const group = document.getElementById("lgroup-data");

  const row = layer => {
    const count = byLayer[layer.id].length;
    const classes = ["lrow"];
    if (count === 0) classes.push("vacant");
    if (layer.lead) classes.push("lead");
    return '<label class="' + classes.join(" ") + '">'
      + '<input type="checkbox" data-layer="' + layer.id + '"'
      + (layerOn[layer.id] ? " checked" : "") + ">"
      + '<span class="lsym">' + GLYPH[layer.glyph] + "</span>"
      + '<span class="lname">' + layer.name + "</span>"
      + '<span class="lcount">' + count + "</span></label>";
  };

  group.innerHTML = '<div class="ltitle">Layers</div>'
    + LAYERS.map(row).join("")
    + shoreControlHtml();

  for (const box of group.querySelectorAll("input[data-layer]")) {
    box.onchange = () => {
      layerOn[box.dataset.layer] = box.checked;
      saveLayerState();
      render();
    };
  }
  for (const button of group.querySelectorAll("button[data-shore]")) {
    button.onclick = () => {
      hideNearShoreM = Number(button.dataset.shore);
      saveShoreState();
      render();
    };
  }
}

/**
 * The near-shore control.
 *
 * <p>No tally underneath it. The counts in the rows above already reflect the
 * threshold, so the price of each setting is visible while choosing it, and a
 * second line restating the same numbers a different way was asked for twice to
 * be taken away. The panel is the three rows and this control.
 */
function shoreControlHtml() {
  const steps = SHORE_STEPS.map(step =>
    '<button type="button" class="schip' + (step.metres === hideNearShoreM ? " on" : "")
    + '" data-shore="' + step.metres + '">' + step.label + "</button>").join("");

  return '<div class="shore"><span class="slabel">Hide near shore</span>'
    + '<span class="schips">' + steps + "</span></div>";
}

// ------------------------------------------------------------- radar image

// The pane the radar picture is drawn in.
//
// Leaflet puts its base tiles at 200 and its overlayPane, which carries the AOI
// outline, at 400. Sitting between the two is the whole requirement: above the
// sea chart so the swath is legible, under the AOI and far under the marker
// panes at 610 and above, so a detection is never hidden by the very image it
// was found in. A pane rather than insertion order, because insertion order is
// whatever the last fetch happened to resolve first.
const RASTER_PANE = "rasterPane";

const RASTER_ON_KEY = "shipviewer.raster.on";
const RASTER_OPACITY_KEY = "shipviewer.raster.opacity";

// Off by default. It is background, not a finding, and a screen that opens with
// a SAR scene over it buries the marks the page exists to show.
let rasterOn = false;
let rasterOpacity = 0.7;
let rasterLayer = null;

/**
 * The only place that knows what kind of raster this is.
 *
 * <p>Everything else — the toggle, the pane, the opacity, the label, when it is
 * added and removed — is written once and does not care how the picture is cut.
 * Every stored raster is a pyramid: the API sends type "tiles" with a maxZoom
 * and a {z}/{x}/{y} template, and this function is the only thing in the
 * frontend that reads them.
 */
function buildRasterLayer(raster) {
  const shared = { pane: RASTER_PANE, opacity: rasterOpacity };

  if (raster.type === "tiles") {
    return L.tileLayer(raster.url, Object.assign({}, shared, {
      // The two zooms do different jobs and both are needed. maxNativeZoom is
      // the deepest that was actually cut; maxZoom is how far the map may go.
      // Past the native one Leaflet enlarges the deepest tiles it has, and
      // without that it would ask for tiles that were never written and leave
      // blank squares. Blurry is a worse picture; blank is a broken one.
      maxNativeZoom: raster.maxNativeZoom,
      maxZoom: raster.maxZoom,
      bounds: L.latLngBounds(raster.bounds)
    }));
  }
  return null;   // A kind this version predates: draw nothing rather than guess.
}

function loadRasterState() {
  try {
    rasterOn = localStorage.getItem(RASTER_ON_KEY) === "1";
    const stored = Number(localStorage.getItem(RASTER_OPACITY_KEY));
    if (stored > 0 && stored <= 1) rasterOpacity = stored;
  } catch (ignored) {
    // Site data blocked: off at 0.7, as if this were a first visit.
  }
}

function saveRasterState() {
  try {
    localStorage.setItem(RASTER_ON_KEY, rasterOn ? "1" : "0");
    localStorage.setItem(RASTER_OPACITY_KEY, String(rasterOpacity));
  } catch (ignored) {
    // The choice simply will not survive a reload.
  }
}

/**
 * Puts the map in step with the run and the toggle.
 *
 * <p>Rebuilt rather than re-pointed whenever the run changes, because a tile
 * layer is bound to its URL template and bounds at construction and the next
 * pass has a different footprint.
 */
function syncRaster() {
  if (rasterLayer) {
    map.removeLayer(rasterLayer);
    rasterLayer = null;
  }

  const raster = detail ? detail.raster : null;
  if (raster && rasterOn) {
    rasterLayer = buildRasterLayer(raster);
    if (rasterLayer) rasterLayer.addTo(map);
  }
  renderRasterPanel(raster);
}

function renderRasterPanel(raster) {
  const row = document.getElementById("raster-row");
  const box = document.getElementById("raster-on");
  const fade = document.getElementById("raster-fade");
  const note = document.getElementById("raster-note");

  // A run processed before the pipeline drew its passes has no image, and so
  // does one whose products failed to draw. The row stays, switched off and
  // saying so: removing it would read as the feature being broken.
  const present = Boolean(raster);
  row.classList.toggle("vacant", !present);
  box.disabled = !present;
  box.checked = present && rasterOn;

  fade.hidden = !(present && rasterOn);
  note.hidden = present;
  if (!present) note.textContent = "No image for this run.";

  document.getElementById("raster-opacity").value = String(Math.round(rasterOpacity * 100));
  document.getElementById("raster-pct").textContent = Math.round(rasterOpacity * 100) + "%";
}

document.getElementById("raster-on").onchange = event => {
  rasterOn = event.currentTarget.checked;
  saveRasterState();
  syncRaster();
};

document.getElementById("raster-opacity").oninput = event => {
  rasterOpacity = Number(event.currentTarget.value) / 100;
  document.getElementById("raster-pct").textContent = event.currentTarget.value + "%";
  // Adjusted in place rather than through syncRaster, so dragging the slider
  // does not tear the image down and fetch it again on every step.
  if (rasterLayer) rasterLayer.setOpacity(rasterOpacity);
  saveRasterState();
};

// Past this the vessel could be anywhere, so the count stops being evidence.
const GAP_MINUTES_THAT_STILL_MEAN_SOMETHING = 30;

const JOB_TITLES = {
  search: "Checking the catalogue",
  pipeline: "Processing the pass"
};

// ------------------------------------------------------------------ the map

// minZoom keeps the map from ever ending up at a continental view, which is
// never a useful frame for one Sentinel-1 pass.
const map = L.map("map", { zoomControl: true, minZoom: 6 }).setView([42.85, 28.6], 8);

// A grey basemap, because standard OSM is colourful enough to fight the data
// marks. Esri's Light Gray Canvas rather than CARTO Positron: as of 2026-09-10
// basemaps.cartocdn.com still answers 200 but stamps "API KEY REQUIRED" across
// every keyless tile, so Positron cannot be used without an account.
L.tileLayer(
  "https://services.arcgisonline.com/ArcGIS/rest/services/Canvas/"
    + "World_Light_Gray_Base/MapServer/tile/{z}/{y}/{x}",
  {
    maxZoom: 16,
    attribution: 'Tiles &copy; <a href="https://www.esri.com/">Esri</a> &mdash; Esri, HERE,'
      + ' Garmin, &copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
      + " contributors"
  }
).addTo(map);

const markerLayer = L.layerGroup().addTo(map);

// Panes give the four layers an explicit z-order. Leaflet numbers markerPane at
// 600 and tooltipPane at 650, so these sit between: above plain markers, below
// the tooltips that describe them.
PANE_STACK.forEach((name, height) => {
  map.createPane(name).style.zIndex = String(610 + height);
});

// Under everything. See RASTER_PANE for why 250 and not insertion order.
map.createPane(RASTER_PANE).style.zIndex = "250";

const layerPanel = document.getElementById("layers");
L.DomEvent.disableClickPropagation(layerPanel);
L.DomEvent.disableScrollPropagation(layerPanel);

// ---------------------------------------------------------------- app state

let detail = null;      // the /api/runs/{id} payload for the selected run
let newestRunTime = null;
let knownRunIds = [];
let visible = [];       // rows currently drawn, in table order
let markers = [];       // one per visible row, same indices
let aoiLayer = null;
let framed = false;

let chosenIndex = null; // index into visible, or null

let job = null;         // last /api/jobs snapshot
let jobTimer = null;

// -------------------------------------------------------------- formatting

function text(value) {
  return value === null || value === undefined ? "" : String(value);
}

function fixed(value, places) {
  return value === null || value === undefined ? "" : Number(value).toFixed(places);
}

function whole(value) {
  return value === null || value === undefined ? "" : String(Math.round(value));
}

function escapeHtml(value) {
  return text(value).replace(/[&<>]/g, ch => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;" })[ch]);
}

// The unit is always spelled out: a bare "m" beside a column of metres reads as
// the wrong quantity entirely.
function describeGap(minutes) {
  if (minutes === null || minutes === undefined) return "an unknown time";
  if (minutes < 1) return Math.round(minutes * 60) + " s";
  const total = Math.round(minutes);
  if (total < 60) return total + " min";
  return Math.floor(total / 60) + "h " + (total % 60) + "m";
}

function describeAge(iso) {
  if (!iso) return "—";
  const hours = (Date.now() - Date.parse(iso)) / 3600000;
  if (hours < 1) return Math.max(1, Math.round(hours * 60)) + " min";
  if (hours < 48) return Math.round(hours) + " h";
  return Math.round(hours / 24) + " d";
}

function describeElapsed(fromIso, toIso) {
  if (!fromIso) return "";
  const seconds = Math.max(0, Math.round(
    ((toIso ? Date.parse(toIso) : Date.now()) - Date.parse(fromIso)) / 1000));
  const minutes = Math.floor(seconds / 60);
  return minutes < 1 ? seconds + "s" : minutes + "m " + (seconds % 60) + "s";
}

// ------------------------------------------------------------------- server

// Spring hands out the CSRF token as the XSRF-TOKEN cookie and refuses a POST
// that does not send the same value back.
function csrfToken() {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : "";
}

// Every call to the server goes through here, for the two things the login
// adds: the CSRF token on anything that changes state, and an expired session.
// The server answers /api/** with 401 rather than a redirect, because fetch()
// would follow a redirect to Keycloak's login page silently and hand back HTML.
// Reloading the page makes the same redirect as a navigation, which works, and
// after logging in the user lands back here. The promise then never settles, so
// no caller goes on to read a response that is not coming.
async function api(path, options = {}) {
  const method = (options.method || "GET").toUpperCase();
  const headers = new Headers(options.headers || {});
  if (method !== "GET" && method !== "HEAD") headers.set("X-XSRF-TOKEN", csrfToken());

  const response = await fetch(path, Object.assign({}, options, { headers }));
  if (response.status === 401) {
    location.reload();
    return new Promise(() => {});
  }
  return response;
}

// ------------------------------------------------------------------ account

async function loadAccount() {
  const account = await (await api("/api/me")).json();
  document.getElementById("account-name").textContent = account.name;
  // Hidden rather than disabled: a button nobody here may press is noise. The
  // server refuses the call either way; this only keeps it out of sight.
  document.getElementById("job-actions").hidden = !account.admin;
  isAdmin = account.admin;
  renderCollector();
}

// The token is read at the moment of submitting, not when the page loads, so a
// token Spring has replaced in the meantime is not sent stale.
document.getElementById("logout-form").onsubmit = () => {
  document.getElementById("logout-csrf").value = csrfToken();
};

// ------------------------------------------------------------------ loading

async function loadAoi() {
  const response = await api("/api/aoi");
  if (!response.ok) return;   // Not exported: draw no outline.
  aoiLayer = L.geoJSON(await response.json(), {
    style: { color: "#4a4a46", weight: 1, fillColor: "#7a8a96", fillOpacity: 0.04 }
  }).addTo(map);
}

/**
 * Frames the map on the data, not on the AOI: the AOI reaches 30 E, where there
 * is nothing to see, and fitting it wastes half the canvas on empty sea. Runs
 * once, so switching runs never yanks the view out from under a comparison.
 */
function frameOnce() {
  if (framed) return;

  // Leaflet works out the zoom from the container's current size, and the CSS
  // grid has not necessarily laid out when the first run arrives. Measuring a
  // near-zero map yields a continental zoom, so the size is re-read first.
  map.invalidateSize();

  const points = [];
  if (detail) {
    for (const feature of detail.detections.features) {
      points.push([feature.properties.latitude, feature.properties.longitude]);
    }
    for (const feature of detail.ais.features) {
      points.push([feature.properties.latitude, feature.properties.longitude]);
    }
  }

  if (points.length > 0) {
    map.fitBounds(L.latLngBounds(points), { padding: [30, 30], maxZoom: 11 });
    framed = true;
  } else if (aoiLayer) {
    map.fitBounds(aoiLayer.getBounds(), { padding: [12, 12], maxZoom: 11 });
    framed = true;
  }
}

// ------------------------------------------------------------------ calendar

const WEEKDAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];
const MONTH_NAMES = ["January", "February", "March", "April", "May", "June",
  "July", "August", "September", "October", "November", "December"];

// passesByDay maps a UTC date to the passes acquired on it, earliest first.
let passesByDay = new Map();
let runsById = new Map();
let calendarCursor = null;        // { year, month } in UTC terms
let monthSpan = null;             // { first, last } as year * 12 + month
let selectedRunId = null;

function pad2(value) {
  return String(value).padStart(2, "0");
}

/**
 * The key a run is filed under.
 *
 * <p>Deliberately UTC and deliberately acquisition_time. The pipeline is often
 * run around midnight local on a scene from the previous UTC day, so the file's
 * own timestamp and the viewer's local date would both put the run on the wrong
 * square. The only field that says when the satellite actually looked is this
 * one.
 */
function utcDayKey(iso) {
  const moment = new Date(iso);
  return moment.getUTCFullYear() + "-" + pad2(moment.getUTCMonth() + 1)
    + "-" + pad2(moment.getUTCDate());
}

function utcHourMinute(iso) {
  const moment = new Date(iso);
  return pad2(moment.getUTCHours()) + ":" + pad2(moment.getUTCMinutes());
}

/**
 * Files the runs by UTC day.
 *
 * <p>Several runs can share one acquisition_time — they are reprocessings of the
 * same pass, not separate passes, and on 2026-09-07 there are three. Only the
 * latest is kept: the list arrives newest first, so the first one seen for a
 * given instant supersedes the rest.
 *
 * <p>Two genuinely different passes on one date is a different matter and stays
 * two entries. It is the normal case in the catalogue — the tracks that cover
 * this AOI come round at about 04:1x and 15:5x UTC, and five of the last
 * sixteen days carried both.
 */
function indexRuns(runs) {
  passesByDay = new Map();
  runsById = new Map();

  for (const run of runs) {
    runsById.set(run.id, run);
    if (!run.acquisitionTime) continue;

    const key = utcDayKey(run.acquisitionTime);
    if (!passesByDay.has(key)) passesByDay.set(key, []);
    const day = passesByDay.get(key);
    if (day.some(other => other.acquisitionTime === run.acquisitionTime)) continue;
    day.push(run);
  }

  for (const day of passesByDay.values()) {
    day.sort((a, b) => a.acquisitionTime.localeCompare(b.acquisitionTime));
  }
}

function monthIndexOf(year, month) {
  return year * 12 + month;
}

function computeMonthSpan() {
  let first = null;
  let last = null;
  for (const key of passesByDay.keys()) {
    const [year, month] = key.split("-").map(Number);
    const index = monthIndexOf(year, month - 1);
    if (first === null || index < first) first = index;
    if (last === null || index > last) last = index;
  }
  monthSpan = first === null ? null : { first, last };
}

function cursorFromIso(iso) {
  const moment = new Date(iso);
  return { year: moment.getUTCFullYear(), month: moment.getUTCMonth() };
}

/** True when the without-transponder figure is not backed by matched AIS. */
function isUnverified(run) {
  return !run.hasAis || run.aisGapMinutes > GAP_MINUTES_THAT_STILL_MEAN_SOMETHING;
}

function passButton(run, isMulti) {
  const classes = ["pass"];
  if (run.detectionCount === 0) classes.push("none");
  if (run.id === selectedRunId) classes.push("chosen");
  const number = '<span class="n' + (isUnverified(run) ? " unverified" : "") + '">'
    + run.withoutTransponder + "</span>";
  return '<button class="' + classes.join(" ") + '" data-run="' + escapeHtml(run.id) + '">'
    + "<span>" + utcHourMinute(run.acquisitionTime) + "</span>" + number + "</button>";
}

function cellHtml(year, month, day) {
  const key = year + "-" + pad2(month + 1) + "-" + pad2(day);
  const passes = passesByDay.get(key) || [];
  const dayNumber = '<span class="dayno">' + day + "</span>";

  // 1 — no pass. Inert, and styled to look intended: two thirds of any month
  // looks like this because the satellite simply was not overhead.
  if (passes.length === 0) {
    return '<div class="cell empty">' + dayNumber + "</div>";
  }

  // A day carrying two passes lists both, each row its own target.
  if (passes.length > 1) {
    return '<div class="cell run multi">' + dayNumber
      + '<div class="passes">' + passes.map(run => passButton(run, true)).join("") + "</div></div>";
  }

  const run = passes[0];
  const classes = ["cell", "run"];
  // 2 — a real run that found nothing, told apart from 1 by having a cell at
  // all, and from 3 by the colour of its zero.
  if (run.detectionCount === 0) classes.push("none");
  if (run.id === selectedRunId) classes.push("chosen");

  return '<button class="' + classes.join(" ") + '" data-run="' + escapeHtml(run.id) + '">'
    + dayNumber
    + '<span class="count' + (isUnverified(run) ? " unverified" : "") + '">'
    + run.withoutTransponder + "</span>"
    + (isUnverified(run) ? '<span class="flag"></span>' : "")
    + "</button>";
}

function renderCalendar() {
  const grid = document.getElementById("cal-grid");
  if (!calendarCursor) {
    grid.innerHTML = "";
    document.getElementById("cal-month").textContent = "—";
    return;
  }

  const { year, month } = calendarCursor;
  document.getElementById("cal-month").textContent = MONTH_NAMES[month] + " " + year;

  // Monday-first, worked out in UTC so the grid never shifts with the viewer's
  // own timezone.
  const leading = (new Date(Date.UTC(year, month, 1)).getUTCDay() + 6) % 7;
  const daysInMonth = new Date(Date.UTC(year, month + 1, 0)).getUTCDate();

  let html = WEEKDAYS.map(name => '<div class="calhead">' + name + "</div>").join("");
  for (let blank = 0; blank < leading; blank++) html += "<div></div>";
  for (let day = 1; day <= daysInMonth; day++) html += cellHtml(year, month, day);
  grid.innerHTML = html;

  for (const target of grid.querySelectorAll("[data-run]")) {
    target.onclick = () => chooseRun(target.dataset.run);
    target.onmouseenter = event => showCalendarTip(target.dataset.run, event.currentTarget);
    target.onmouseleave = hideCalendarTip;
  }

  const here = monthIndexOf(year, month);
  document.getElementById("cal-prev").disabled = !monthSpan || here <= monthSpan.first;
  document.getElementById("cal-next").disabled = !monthSpan || here >= monthSpan.last;
}

function stepMonth(by) {
  if (!calendarCursor || !monthSpan) return;
  const wanted = monthIndexOf(calendarCursor.year, calendarCursor.month) + by;
  if (wanted < monthSpan.first || wanted > monthSpan.last) return;
  calendarCursor = { year: Math.floor(wanted / 12), month: wanted % 12 };
  closeCalendar();
  updateRunButton();
  renderCalendar();
}

async function chooseRun(id) {
  if (!id || id === selectedRunId) return;
  selectedRunId = id;
  hideCalendarTip();
  closeCalendar();
  updateRunButton();
  renderCalendar();
  await loadRun(id);
}

// ------------------------------------------------------------- calendar tip

function showCalendarTip(id, anchor) {
  const run = runsById.get(id);
  if (!run) return;

  const rows = [
    ["raw CFAR", run.cfarRawCount === null ? "—" : run.cfarRawCount],
    ["in AOI", run.detectionCount],
    ["with transponder", run.hasAis ? run.withTransponder : "—"],
    ["without transponder", run.withoutTransponder],
    ["AIS gap", run.hasAis ? describeGap(run.aisGapMinutes) : "no AIS"]
  ];

  const tip = document.getElementById("caltip");
  tip.innerHTML = "<b>" + escapeHtml(run.acquisitionTime.replace("T", " ").replace("Z", " UTC"))
    + "</b>"
    + rows.map(([key, value]) =>
      '<div class="row"><span>' + key + "</span><span>" + value + "</span></div>").join("")
    + (isUnverified(run)
      ? '<div class="warn">' + (run.hasAis
        ? "AIS is " + describeGap(run.aisGapMinutes) + " from the pass, so this count is "
          + "an artefact of timing rather than a finding."
        : "No AIS for this run, so nothing here is confirmed dark.") + "</div>"
      : "");
  tip.hidden = false;

  const box = anchor.getBoundingClientRect();
  tip.style.left = Math.min(box.left, window.innerWidth - tip.offsetWidth - 10) + "px";
  tip.style.top = (box.bottom + 6) + "px";
}

function hideCalendarTip() {
  document.getElementById("caltip").hidden = true;
}

document.getElementById("cal-prev").onclick = () => stepMonth(-1);
document.getElementById("cal-next").onclick = () => stepMonth(1);
document.getElementById("cal-latest").onclick = () => {
  const newest = runsById.get(knownRunIds[0]);
  if (!newest) return;
  calendarCursor = cursorFromIso(newest.acquisitionTime);
  chooseRun(newest.id).then(renderCalendar);
};

// ------------------------------------------------------------------ loading

async function loadRuns(preferId) {
  const runs = await (await api("/api/runs")).json();
  knownRunIds = runs.map(run => run.id);
  indexRuns(runs);
  computeMonthSpan();

  if (runs.length === 0) {
    selectedRunId = null;
    calendarCursor = null;
    updateRunButton();
    renderCalendar();
    showMapNotice("No runs found.",
      "The database holds no runs yet. Process a pass, or check that MySQL is running.");
    setVesselNotice("No runs to show.");
    updateButtons();
    return;
  }

  newestRunTime = runs[0].acquisitionTime;
  const wanted = preferId && knownRunIds.includes(preferId) ? preferId : runs[0].id;
  selectedRunId = wanted;

  // Opens on the month of the run being shown, which is the newest unless a job
  // just asked for a particular one. Landing on the current month would show an
  // empty grid whenever the newest pass is a few days old.
  calendarCursor = cursorFromIso(runsById.get(wanted).acquisitionTime);
  updateRunButton();
  renderCalendar();
  await loadRun(wanted);
}
async function loadRun(id) {
  detail = await (await api("/api/runs/" + encodeURIComponent(id))).json();
  renderStats();
  renderGap();
  renderNotes();
  render();
  syncRaster();
  frameOnce();
  updateButtons();
}

// -------------------------------------------------------------- header strip

function renderStats() {
  const s = detail.summary;
  const items = [
    ["CFAR raw", s.cfarRawCount === null ? "—" : s.cfarRawCount],
    ["in AOI", s.detectionCount],
    ["with transponder", s.hasAis ? s.withTransponder : "—"],
    ["AIS gap", s.hasAis ? describeGap(s.aisGapMinutes) : "none"],
    ["newest run", describeAge(newestRunTime) + " ago"]
  ];

  let html = '<div class="stat lead"><span class="v">'
    + (s.hasAis ? s.withoutTransponder : "?")
    + '</span><span class="k">without transponder</span></div>'
    + '<div class="stat divider"></div>';

  html += items.map(([key, value]) =>
    '<div class="stat"><span class="v">' + value
    + '</span><span class="k">' + key + "</span></div>").join("");

  document.getElementById("stats").innerHTML = html;
}

function renderGap() {
  const s = detail.summary;
  const bar = document.getElementById("gap");

  if (!s.hasAis) {
    bar.textContent = "No AIS data for this run — transponder status unknown.";
    bar.className = "gap stale";
    return;
  }

  const stale = s.aisGapMinutes > GAP_MINUTES_THAT_STILL_MEAN_SOMETHING;
  bar.textContent = "AIS positions are " + describeGap(s.aisGapMinutes) + " from the pass"
    + (stale
      ? " — “without transponder” is unreliable at this gap."
      : ".");
  bar.className = stale ? "gap stale" : "gap";
}

/**
 * The provenance lines are true and occasionally decisive, but they are not what
 * anyone reads first, so they live behind a toggle instead of taking a band of
 * the screen on every run.
 */
function renderNotes() {
  const parts = [];
  if (detail.summary.hasAis) {
    parts.push("Pass " + text(detail.summary.acquisitionTime)
      + ". AIS window " + text(detail.aisEarliestTime)
      + " to " + text(detail.aisLatestTime) + ".");
  }

  const notes = document.getElementById("notes");
  const toggle = document.getElementById("notes-toggle");
  notes.textContent = parts.join("  ");
  toggle.hidden = parts.length === 0;
  if (parts.length === 0) notes.hidden = true;
}

// ------------------------------------------------------------- empty states

/**
 * The three situations that used to look alike. Returns null when the run has
 * detections, in which case nothing needs explaining.
 */
function emptyStateFor(summary) {
  if (summary.detectionCount > 0) return null;

  if (summary.cfarRawCount > 0) {
    return {
      title: "CFAR found " + summary.cfarRawCount + " targets, all outside Bulgarian waters.",
      detail: "Every target fell outside the AOI polygon and was dropped. The scene was "
        + "processed correctly; it simply covered other countries' water."
    };
  }
  if (summary.cfarRawCount === 0) {
    return {
      title: "No targets detected in this scene.",
      detail: "CFAR returned nothing at all. An empty sea looks exactly like a broken "
        + "land/sea mask that discarded the water, so check the mask before trusting this."
    };
  }
  return {
    title: "No detections in this run.",
    detail: "There is no run log, so whether CFAR found nothing or the AOI filter dropped "
      + "everything cannot be told apart from here."
  };
}

function showMapNotice(title, detail) {
  const box = document.getElementById("mapnotice");
  box.innerHTML = "<b>" + escapeHtml(title) + "</b><span>" + escapeHtml(detail) + "</span>";
  box.hidden = false;
}

function hideMapNotice() {
  document.getElementById("mapnotice").hidden = true;
}

// ------------------------------------------------------------------ layers

function render() {
  markerLayer.clearLayers();
  markers = [];
  chosenIndex = null;
  clearPairVisual();

  const byLayer = featuresByLayer();
  renderLayerPanel(byLayer);

  // Listed in importance order, which is also the order the panel shows, so a
  // row's position in the list matches where the eye expects it. With every box
  // cleared this is empty, which is a legitimate state: the basemap on its own.
  visible = [];
  for (const layer of layersShown()) visible = visible.concat(byLayer[layer.id]);

  visible.forEach((row, index) => {
    const layer = LAYERS.find(candidate => candidate.glyph === row.kind);
    const marker = L.marker([row.p.latitude, row.p.longitude], {
      pane: layer.pane,
      icon: L.divIcon({
        className: "mark",
        html: MARK[row.kind].svg,
        iconSize: [MARK[row.kind].box, MARK[row.kind].box],
        iconAnchor: [MARK[row.kind].box / 2, MARK[row.kind].box / 2]
      })
    });
    marker.bindTooltip(tooltipFor(row), { direction: "top", offset: [0, -8] });
    marker.on("click", () => choose(index, false));
    marker.addTo(markerLayer);
    markers.push(marker);
  });

  const empty = emptyStateFor(detail.summary);
  if (empty) {
    showMapNotice(empty.title, empty.detail);
  } else {
    hideMapNotice();
  }

  renderVesselList(empty);
  renderVesselCard(null);
}

function tooltipFor(row) {
  const p = row.p;
  if (row.kind === "ais") {
    return escapeHtml(p.vesselName || "MMSI " + p.mmsi) + "<br>" + escapeHtml(p.timestamp);
  }
  // The pairing used to be a layer of its own. It says more here, next to the
  // detection it belongs to, than it did as a duplicate mark 37 m away.
  const paired = p.matched ? aisPositionOf(p.matchedMmsi) : null;
  return escapeHtml(p.detectionId) + "<br>" + whole(p.lengthM) + " × " + whole(p.widthM)
    + " m<br>" + (p.matched
      ? "matched MMSI " + escapeHtml(p.matchedMmsi)
        + (paired && paired.vesselName ? " (" + escapeHtml(paired.vesselName) + ")" : "")
        + " at " + whole(p.distanceMeters) + " m"
      : "no transponder within range");
}

// -------------------------------------------------------------------- table

// ------------------------------------------------------------- side panel

/**
 * The vessel list, one row per mark on the map.
 *
 * <p>Two lines per row rather than seven columns: a 330 px column cannot hold a
 * table, and the fields that matter for scanning — which vessel, where, how big,
 * matched or not — read better stacked than squeezed.
 */
function renderVesselList(empty) {
  const list = document.getElementById("vessel-list");
  const notice = document.getElementById("vessel-notice");

  document.getElementById("vessel-count").textContent =
    visible.length + (visible.length === 1 ? " vessel" : " vessels");
  document.getElementById("vessel-kind").textContent = describeLayers();

  list.innerHTML = visible.map((row, index) => {
    const p = row.p;
    const isAis = row.kind === "ais";
    const name = isAis ? (p.vesselName || "MMSI " + p.mmsi) : p.detectionId;
    const sub = isAis
      ? "MMSI " + escapeHtml(p.mmsi) + " · " + escapeHtml(text(p.timestamp).slice(11, 19)) + " UTC"
      : whole(p.lengthM) + " × " + whole(p.widthM) + " m"
        + (p.matched
          ? " · MMSI " + escapeHtml(p.matchedMmsi) + " · " + whole(p.distanceMeters) + " m"
          : " · no transponder");

    return '<button class="vrow" data-index="' + index + '">'
      + '<span class="top">' + GLYPH[row.kind]
      + '<span class="id">' + escapeHtml(name) + "</span>"
      + '<span class="pos">' + fixed(p.latitude, 3) + ", " + fixed(p.longitude, 3) + "</span>"
      + "</span>"
      + '<span class="sub">' + sub + "</span></button>";
  }).join("");

  for (const row of list.querySelectorAll(".vrow")) {
    row.onclick = () => choose(Number(row.dataset.index), true);
  }

  if (visible.length > 0) {
    notice.hidden = true;
  } else {
    // Three different nothings, and they must not read the same. Every box
    // cleared is a choice and says so; a run that found nothing is explained on
    // the map already; what is left is a run with data whose switched-on layers
    // happen to hold none of it. An unexplained empty list reads as a bug.
    setVesselNotice(
      layersShown().length === 0
        ? "No layers are switched on. Tick one above to see the "
          + detail.summary.detectionCount + " detections in this run."
        : empty
          ? empty.title
          : hideNearShoreM > 0
            ? "Everything in these layers is within " + describeShore(hideNearShoreM)
              + " of shore. The run has " + detail.summary.detectionCount
              + " detections in all."
            : "Nothing in the layers you have on. "
              + "The run has " + detail.summary.detectionCount + " detections in all.");
  }
}

/** The one selected vessel, spelled out. Cleared whenever the layer changes. */
function renderVesselCard(index) {
  const card = document.getElementById("vessel-card");
  if (index === null || !visible[index]) {
    card.innerHTML = '<p class="vhint">Pick a mark on the map, or a row below.</p>';
    return;
  }

  const row = visible[index];
  const p = row.p;
  const isAis = row.kind === "ais";
  const name = isAis ? (p.vesselName || "MMSI " + p.mmsi) : p.detectionId;

  const fields = isAis
    ? [["MMSI", escapeHtml(p.mmsi)],
       ["reported", escapeHtml(text(p.timestamp).replace("T", " ").replace("Z", " UTC"))],
       ["speed", p.sog === null || p.sog === undefined ? "—" : p.sog + " kn"],
       ["course", p.cog === null || p.cog === undefined ? "—" : p.cog + "°"],
       ["latitude", fixed(p.latitude, 4)],
       ["longitude", fixed(p.longitude, 4)]]
    : [["length", whole(p.lengthM) + " m"],
       ["width", whole(p.widthM) + " m"],
       ["latitude", fixed(p.latitude, 4)],
       ["longitude", fixed(p.longitude, 4)],
       ["slice", escapeHtml(p.sourceSlice)],
       ["transponder", p.matched
         ? "MMSI " + escapeHtml(p.matchedMmsi)
         : '<span class="strong">none within range</span>'],
       ["distance", p.matched ? whole(p.distanceMeters) + " m" : "—"]];

  card.innerHTML = '<div class="vname">' + GLYPH[row.kind] + escapeHtml(name) + "</div><dl>"
    + fields.map(([key, value]) =>
      "<dt>" + key + "</dt><dd" + (key === "transponder" && !p.matched ? ' class="strong"' : "")
      + ">" + value + "</dd>").join("")
    + "</dl>";
}

function setVesselNotice(message) {
  const notice = document.getElementById("vessel-notice");
  notice.textContent = message;
  notice.hidden = false;
}

// ------------------------------------------------------ the pairing, on demand

// Shown for the selected detection only. A permanent layer of these was a
// duplicate beside a duplicate; as a detail on demand it answers the one
// question it is good for — is this pairing believable.
let pairVisual = null;

function clearPairVisual() {
  if (pairVisual) {
    map.removeLayer(pairVisual);
    pairVisual = null;
  }
}

function showPairVisual(row) {
  clearPairVisual();
  if (!row || row.kind !== "lit" || !row.p.matched) return;

  const ais = aisPositionOf(row.p.matchedMmsi);
  if (!ais) return;

  pairVisual = L.layerGroup([
    L.polyline([[row.p.latitude, row.p.longitude], [ais.latitude, ais.longitude]],
      { className: "pairline", interactive: false }),
    L.marker([ais.latitude, ais.longitude], {
      pane: "layerAisAlone",
      interactive: false,
      icon: L.divIcon({
        className: "mark",
        html: MARK.ais.svg,
        iconSize: [MARK.ais.box, MARK.ais.box],
        iconAnchor: [MARK.ais.box / 2, MARK.ais.box / 2]
      })
    })
  ]).addTo(map);
}

// ----------------------------------------------------------------- selection

function choose(index, cameFromList) {
  chosenIndex = index;

  for (const row of document.querySelectorAll("#vessel-list .vrow")) {
    row.classList.toggle("chosen", Number(row.dataset.index) === index);
  }
  markers.forEach((marker, at) => {
    const element = marker.getElement();
    if (element) element.classList.toggle("chosen", at === index);
  });

  renderVesselCard(index);
  showPairVisual(visible[index]);

  const row = document.querySelector('#vessel-list .vrow[data-index="' + index + '"]');
  if (row && !cameFromList) row.scrollIntoView({ block: "nearest" });

  if (cameFromList) {
    const p = visible[index].p;
    // Instant, not animated: Leaflet's animated pan gets cancelled if anything
    // else touches the map mid-flight, and a jump is the right feel here anyway.
    map.panTo([p.latitude, p.longitude], { animate: false });
  }
}

// ------------------------------------------------------- calendar open/close

function calendarIsOpen() {
  return !document.getElementById("calpop").hidden;
}

function openCalendar() {
  const pop = document.getElementById("calpop");
  const trigger = document.getElementById("cal-open");
  pop.hidden = false;
  document.getElementById("cal-open").setAttribute("aria-expanded", "true");

  const box = trigger.getBoundingClientRect();
  pop.style.left = Math.min(box.left, window.innerWidth - pop.offsetWidth - 12) + "px";
  pop.style.top = (box.bottom + 6) + "px";
}

function closeCalendar() {
  document.getElementById("calpop").hidden = true;
  document.getElementById("cal-open").setAttribute("aria-expanded", "false");
  hideCalendarTip();
}

function updateRunButton() {
  const run = runsById.get(selectedRunId);
  const label = document.getElementById("cal-open-label");
  if (!run) {
    label.textContent = "No run";
    return;
  }
  const day = new Date(run.acquisitionTime);
  label.textContent = day.getUTCDate() + " " + MONTH_NAMES[day.getUTCMonth()].slice(0, 3)
    + " " + day.getUTCFullYear() + " · " + utcHourMinute(run.acquisitionTime) + "Z";
}

document.getElementById("cal-open").onclick = event => {
  event.stopPropagation();
  if (calendarIsOpen()) closeCalendar(); else openCalendar();
};

document.addEventListener("click", event => {
  if (!calendarIsOpen()) return;
  if (document.getElementById("calpop").contains(event.target)) return;
  closeCalendar();
});

document.addEventListener("keydown", event => {
  if (event.key === "Escape" && calendarIsOpen()) closeCalendar();
});

// ------------------------------------------------------------------- filters

document.getElementById("notes-toggle").onclick = () => {
  const notes = document.getElementById("notes");
  notes.hidden = !notes.hidden;
};

// ---------------------------------------------------------------------- jobs

async function startJob(kind) {
  const response = await api("/api/jobs/" + kind, { method: "POST" });
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    showJobProblem(body.message || ("Could not start: HTTP " + response.status));
    return;
  }
  job = await response.json();
  renderJob();
  watchJob();
}

function watchJob() {
  if (jobTimer) return;
  jobTimer = setInterval(pollJob, 1200);
}

async function pollJob() {
  const wasRunning = job && job.state === "running";
  job = await (await api("/api/jobs")).json();
  renderJob();

  if (job.state !== "running") {
    clearInterval(jobTimer);
    jobTimer = null;
    // Processing stores its pass in the database the viewer reads, so the run
    // list is reloaded once the job lands rather than making anyone press refresh.
    if (wasRunning && job.kind !== "search") {
      await loadRuns(selectedRunId);
    }
    updateButtons();
  }
}

function showJobProblem(message) {
  job = {
    kind: job && job.kind, state: "failed", startedAt: null, finishedAt: null,
    exitCode: null, failure: message, log: [], partialLine: ""
  };
  renderJob();
}

function renderJob() {
  const card = document.getElementById("jobcard");
  if (!job || job.state === "idle") {
    card.hidden = true;
    updateButtons();
    return;
  }

  card.hidden = false;
  document.getElementById("job-dot").className = "dot " + job.state;
  document.getElementById("job-title").textContent =
    (JOB_TITLES[job.kind] || "Job") + (job.state === "running" ? "…" : "");
  document.getElementById("job-elapsed").textContent =
    job.state === "running" ? describeElapsed(job.startedAt, null)
      : job.startedAt ? "took " + describeElapsed(job.startedAt, job.finishedAt) : "";

  const lines = (job.log || []).slice();
  if (job.partialLine) lines.push(job.partialLine);
  if (job.failure) lines.push("");
  if (job.failure) lines.push(job.failure);
  else if (job.state === "failed" && job.exitCode !== null) {
    lines.push("", "Exited with code " + job.exitCode + ".");
  }

  const pre = document.getElementById("job-log");
  const wasAtBottom = pre.scrollTop + pre.clientHeight >= pre.scrollHeight - 24;
  pre.textContent = lines.join("\n");
  if (wasAtBottom) pre.scrollTop = pre.scrollHeight;

  updateButtons();
}

function updateButtons() {
  const busy = job && job.state === "running";
  document.getElementById("do-search").disabled = busy;
  document.getElementById("do-pipeline").disabled = busy;
}

document.getElementById("do-search").onclick = () => startJob("search");
document.getElementById("do-pipeline").onclick = () => startJob("pipeline");
document.getElementById("job-close").onclick = () => {
  document.getElementById("jobcard").hidden = true;
};

// ----------------------------------------------------------------- collector

// The collector runs for weeks and outlives this page and the viewer, so it is
// polled for as long as the page is open: slowly while it simply runs, quickly
// while it is compiling and connecting.
const COLLECTOR_POLL_MS = 15000;
const COLLECTOR_STARTING_POLL_MS = 2000;

const COLLECTOR_LABELS = {
  running: "collecting",
  starting: "starting…",
  stopped: "stopped"
};

let collector = null;     // last /api/collector status
let collectorTimer = null;
let isAdmin = false;

async function pollCollector() {
  clearTimeout(collectorTimer);
  try {
    collector = await (await api("/api/collector")).json();
  } catch (unreachable) {
    collector = null;
  }
  renderCollector();
  const starting = collector && collector.state === "starting";
  collectorTimer = setTimeout(pollCollector,
    starting ? COLLECTOR_STARTING_POLL_MS : COLLECTOR_POLL_MS);
}

async function startCollector() {
  const button = document.getElementById("collector-start");
  button.disabled = true;
  const response = await api("/api/collector/start", { method: "POST" });
  if (!response.ok) {
    const body = await response.json().catch(() => ({}));
    document.getElementById("collector-note").textContent =
      body.message || ("Could not start: HTTP " + response.status);
    button.disabled = false;
    return;
  }
  collector = await response.json();
  renderCollector();
  pollCollector();
}

function renderCollector() {
  const state = collector ? collector.state : "unknown";
  const label = collector ? COLLECTOR_LABELS[state] : "unknown";

  document.getElementById("collector-dot").className = "dot collector-" + state;
  document.getElementById("collector-card-dot").className = "dot collector-" + state;
  document.getElementById("collector-label").textContent = "AIS · " + label;
  document.getElementById("collector-state").textContent = label;
  if (!collector) return;

  const facts = [];
  if (collector.pid !== null) facts.push(["Process", collector.pid]);
  if (collector.startedAt) facts.push(["Running for", describeElapsed(collector.startedAt, null)]);
  facts.push(["Newest position",
    collector.lastPositionAt ? describeAge(collector.lastPositionAt) + " ago" : "unknown"]);
  facts.push(["Last hour",
    collector.positionsLastHour === null ? "unknown"
      : collector.positionsLastHour.toLocaleString("en-GB") + " positions"]);
  document.getElementById("collector-facts").innerHTML = facts
    .map(([name, value]) => "<dt>" + escapeHtml(name) + "</dt><dd>" + escapeHtml(value) + "</dd>")
    .join("");

  const button = document.getElementById("collector-start");
  button.hidden = !isAdmin || state !== "stopped";
  button.disabled = false;

  let note = "";
  if (state === "stopped") {
    note = "Passes processed while it is stopped will have no AIS."
      + (isAdmin ? "" : " An admin can start it.");
  } else if (state === "running" && collector.positionsLastHour === 0) {
    note = "Running, but nothing reached the database in the last hour. Check the output below.";
  } else if (state === "starting") {
    note = "Compiling and connecting. This can take a minute the first time.";
  }
  document.getElementById("collector-note").textContent = note;

  const pre = document.getElementById("collector-log");
  pre.hidden = collector.log.length === 0;
  pre.textContent = collector.log.join("\n");
  pre.scrollTop = pre.scrollHeight;
}

document.getElementById("collector-chip").onclick = () => {
  const card = document.getElementById("collectorcard");
  card.hidden = !card.hidden;
  document.getElementById("collector-chip").setAttribute("aria-expanded", String(!card.hidden));
  if (!card.hidden) pollCollector();
};
document.getElementById("collector-close").onclick = () => {
  document.getElementById("collectorcard").hidden = true;
  document.getElementById("collector-chip").setAttribute("aria-expanded", "false");
};
document.getElementById("collector-start").onclick = startCollector;

// ------------------------------------------------------------------- startup

loadLayerState();
loadShoreState();
loadRasterState();

// Independent of the map, so they do not wait in the chain below.
loadAccount();
pollCollector();

// Sequential on purpose: the framing decision needs the AOI as its fallback, so
// it has to be on the map before the first run is drawn.
loadAoi()
  .then(() => loadRuns())
  .then(pollJob)             // A job may already be running from an earlier visit.
  .then(() => { if (job && job.state === "running") watchJob(); });
