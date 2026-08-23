package com.betterstreamflix.utils

/**
 * Centralized application constants.
 * Replaces magic numbers and hardcoded strings throughout the codebase.
 */
object Constants {
    // === Network ===
    const val DEFAULT_TIMEOUT_MS = 30_000L
    const val CONNECT_TIMEOUT_MS = 15_000L
    const val READ_TIMEOUT_MS = 30_000L
    const val WRITE_TIMEOUT_MS = 30_000L
    const val NETWORK_TIMEOUT_MS = 30_000
    const val MAX_RETRIES = 3
    const val RETRY_DELAY_MS = 1_000L
    const val USER_AGENT = "Mozilla/5.0 (Linux; Android) BetterStreamflix/1.0"

    // === Player ===
    const val SKIP_INTRO_START_MS = 3_000L
    const val SKIP_INTRO_END_MS = 120_000L
    const val NEXT_EPISODE_PREFETCH_THRESHOLD_MS = 60_000L
    const val NEXT_EPISODE_OVERLAY_MIN_THRESHOLD_MS = 30_000L
    const val PROGRESS_UPDATE_INTERVAL_MS = 1_000L
    const val WATCHED_THRESHOLD_PERCENT = 0.90
    const val STARTED_THRESHOLD_PERCENT = 0.005
    const val STARTED_THRESHOLD_MS = 20_000L

    // === Cache ===
    const val CACHE_THRESHOLD_MOBILE_MB = 50L
    const val CACHE_THRESHOLD_TV_MB = 10L
    const val CACHE_MAX_AGE_HOURS = 24

    // === Database ===
    const val DB_NAME = "betterstreamflix.db"
    const val DB_VERSION = 1

    // === Pagination ===
    const val DEFAULT_PAGE_SIZE = 20
    const val MAX_PAGE_SIZE = 100
    const val PREFETCH_DISTANCE = 5

    // === UI ===
    const val DEBOUNCE_SEARCH_MS = 500L
    const val DEBOUNCE_SCROLL_MS = 200L
    const val ANIMATION_DURATION_MS = 300L
    const val TOAST_DURATION_SHORT = 0
    const val TOAST_DURATION_LONG = 1

    // === GitHub ===
    const val GITHUB_OWNER = "BetterStreamflix"
    const val GITHUB_REPO = "BetterStreamflix"
    const val GITHUB_REPO_OWNER = GITHUB_OWNER
    const val GITHUB_REPO_NAME = GITHUB_REPO
    const val GITHUB_API_BASE = "https://api.github.com"

    // === Supabase ===
    const val SUPABASE_URL = "https://your-project.supabase.co"
    const val SUPABASE_REALTIME_CHANNEL = "public:watch_progress"

    // === Intent Extras ===
    const val EXTRA_VIDEO_TYPE = "video_type"
    const val EXTRA_VIDEO_ID = "video_id"
    const val EXTRA_PROVIDER_NAME = "provider_name"
    const val EXTRA_URL = "extra_url"
    const val EXTRA_COOKIE_HEADER = "extra_cookie_header"

    // === Notification Channels ===
    const val CHANNEL_DOWNLOAD = "downloads"
    const val CHANNEL_UPDATE = "updates"
    const val CHANNEL_GENERAL = "general"

    // === File Extensions ===
    const val EXT_M3U8 = ".m3u8"
    const val EXT_VTT = ".vtt"
    const val EXT_SRT = ".srt"
    const val EXT_ASS = ".ass"
    const val EXT_MP4 = ".mp4"
    const val EXT_MKV = ".mkv"

    // === MIME Types ===
    const val MIME_M3U8 = "application/x-mpegURL"
    const val MIME_VTT = "text/vtt"
    const val MIME_SRT = "application/x-subrip"
    const val MIME_MP4 = "video/mp4"
    const val MIME_MKV = "video/x-matroska"

    // === User Preferences Keys ===
    const val PREF_PROVIDER_URL = "provider_url"
    const val PREF_SELECTED_PROVIDER = "selected_provider"
    const val PREF_APP_LAYOUT = "app_layout"
    const val PREF_QUALITY_HEIGHT = "quality_height"
    const val PREF_AUTOPLAY = "autoplay"
    const val PREF_AUTOPLAY_BUFFER = "autoplay_buffer"
    const val PREF_DOH_PROVIDER_URL = "doh_provider_url"
    const val PREF_APP_LANGUAGE = "app_language"
    const val PREF_SUBTITLE_LANGUAGE = "subtitle_language"
    const val PREF_SUBTITLE_STYLE = "subtitle_style"
}
