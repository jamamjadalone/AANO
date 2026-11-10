// AANO Windows Desktop App - Global Reactive State & Palettes
// Copyright (C) 2026 Jam Amjad Rasheed | Licensed under GPL-3.0-or-later.
// Modularized under Rule 21 (< 300 lines).

var activePairedPeer = null;

const PALETTE_LIGHT = [
  "#FFFFFF", // 0: Pure Studio White
  "#F0FDF4", // 1: Crisp Sage Mint
  "#FEF9C3", // 2: Soft Lemon Chiffon
  "#F0F9FF", // 3: Nordic Glacier Blue
  "#EEF2FF", // 4: Royal Soft Iris
  "#FAF5FF", // 5: Lavender Mist
  "#FFF1F2", // 6: Velvet Peony
  "#FFF7ED", // 7: Peach Sorbet
  "#FEF3C7", // 8: Warm Amber Honey
  "#F8FAFC", // 9: Slate Porcelain
  "#F3F4F6", // 10: Urban Sandstone
  "#1E293B"  // 11: Deep Obsidian Accent
];

const PALETTE_DARK = [
  "#161B22", // 0: Deep Obsidian Canvas
  "#0F291E", // 1: Emerald Forest
  "#2E2510", // 2: Golden Topaz Glow
  "#0E2338", // 3: Arctic Midnight Blue
  "#171C3D", // 4: Deep Royal Indigo
  "#241738", // 5: Twilight Amethyst
  "#331422", // 6: Crimson Velvet
  "#311B11", // 7: Burnt Terracotta
  "#2D2110", // 8: Sahara Espresso
  "#1C2128", // 9: Graphite Slate
  "#21262D", // 10: Charcoal Ash
  "#0B0E14"  // 11: Pure Onyx
];

const PASTEL_COLORS = PALETTE_LIGHT;

const PALETTE_LABELS = [
  "Default",   // 0
  "Teal",      // 1
  "Yellow",    // 2
  "Green",     // 3
  "Blue",      // 4
  "Purple",    // 5
  "Pink",      // 6
  "Orange",    // 7
  "Amber",     // 8
  "Slate",     // 9
  "Sand",      // 10
  "Charcoal"   // 11
];

var customColorLabels = {};

var allNotes = [];
var archivedNotes = [];
var trashNotes = [];
var currentFilter = "all"; // "all" | "pinned" | "archived" | "trash" | "folder:<name>"
var currentColorFilter = null; // null | number
var currentSearch = "";
var ftsMatchIds = null;
var isListView = false;

var isSelectionMode = false;
var selectedNoteIds = new Set();
var lastFilteredNotes = [];

var currentAttachedImages = []; // Array of { filename, dataUrl }
var isHdAttachmentQuality = false;
var currentPreviewIndex = -1;
var cropImageObj = null;
var cropRotation = 0;
var cropAspectRatio = "free";
var cropBox = null;
var isDraggingCrop = false;
var cropDragStart = { x: 0, y: 0 };
var cropDragMode = "move";

var editingNote = null; // null if creating new, or note object
var editingColor = 0;
var editingPinned = false;
var editingIsLocal = false;
var isEditorViewMode = false;

var lastUrlOpenTime = 0;
var lastUrlOpened = "";
var isAppInitialized = false;

const URL_REGEX = /(https?:\/\/[^\s<>"'`]+|www\.[a-zA-Z0-9-]+\.[a-zA-Z0-9-._~:/?#[\]@!$&'()*+,;=]+)/gi;

function getApi() {
  return window.pywebview ? window.pywebview.api : null;
}
