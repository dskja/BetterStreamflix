package com.betterstreamflix.fragments.player.settings

import android.content.Context
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.betterstreamflix.R
import com.betterstreamflix.fragments.player.settings.PlayerSettingsView.Item
import com.betterstreamflix.fragments.player.settings.PlayerSettingsView.Settings

/**
 * Pure presentation helpers shared by [PlayerSettingsMobileView] and [PlayerSettingsTvView]
 * through [PlayerSettingsPanel]. Mirrors the label/subtitle/selection logic that used to live
 * inside the RecyclerView `SettingViewHolder`s so both mobile and TV render identically.
 */
object PlayerSettingsPresentation {

    /** Main label/title text for a row. */
    fun mainText(context: Context, item: Item): String = when (item) {
        is Settings -> when (item) {
            Settings.Quality -> context.getString(R.string.player_settings_quality_label)
            Settings.Audio -> context.getString(R.string.player_settings_audio_label)
            Settings.Subtitle -> context.getString(R.string.player_settings_subtitles_label)
            Settings.Speed -> context.getString(R.string.player_settings_speed_label)
            Settings.ExtraBuffering -> context.getString(R.string.player_settings_extra_buffer_server_label)
            Settings.SoftwareDecoder -> context.getString(R.string.player_settings_software_decoder_label)
            Settings.Server -> context.getString(R.string.player_settings_servers_label)
            Settings.Gestures -> context.getString(R.string.player_settings_gestures_title)
            Settings.KeepScreenOn -> context.getString(R.string.player_settings_keep_screen_on_title)
            Settings.Download -> context.getString(R.string.downloads_title)
            Settings.ManualZoom -> context.getString(R.string.player_settings_manual_zoom_label)
        }

        is Settings.Audio -> when (item) {
            is Settings.Audio.AudioTrackInformation -> item.name
        }

        is Settings.Quality -> when (item) {
            is Settings.Quality.Auto -> when {
                item.isSelected -> when (val track = item.currentTrack) {
                    null -> context.getString(R.string.player_settings_quality_auto)
                    else -> context.getString(R.string.player_settings_quality_auto_selected, track.height)
                }
                else -> context.getString(R.string.player_settings_quality_auto)
            }
            is Settings.Quality.VideoTrackInformation -> context.getString(R.string.player_settings_quality, item.height)
        }

        is Settings.Subtitle -> when (item) {
            Settings.Subtitle.Style -> context.getString(R.string.player_settings_caption_style_label)
            Settings.Subtitle.Offset -> context.getString(R.string.player_settings_subtitle_offset_label)
            is Settings.Subtitle.None -> context.getString(R.string.player_settings_subtitles_off)
            is Settings.Subtitle.TextTrackInformation -> item.label.ifEmpty { item.name }
            Settings.Subtitle.LocalSubtitles -> context.getString(R.string.player_settings_local_subtitles_label)
            Settings.Subtitle.OpenSubtitles -> context.getString(R.string.player_settings_open_subtitles_label)
            Settings.Subtitle.SubDLSubtitles -> context.getString(R.string.player_settings_subdl_label)
        }

        is Settings.Subtitle.Offset.Value -> context.getString(
            R.string.player_settings_subtitle_offset_seconds,
            item.milliseconds / 1_000.0,
        )

        is Settings.Subtitle.Style -> when (item) {
            Settings.Subtitle.Style.ResetStyle -> context.getString(R.string.player_settings_caption_style_reset_style_label)
            Settings.Subtitle.Style.FontColor -> context.getString(R.string.player_settings_caption_style_font_color_label)
            Settings.Subtitle.Style.TextSize -> context.getString(R.string.player_settings_caption_style_text_size_label)
            Settings.Subtitle.Style.FontOpacity -> context.getString(R.string.player_settings_caption_style_font_opacity_label)
            Settings.Subtitle.Style.EdgeStyle -> context.getString(R.string.player_settings_caption_style_edge_style_label)
            Settings.Subtitle.Style.BackgroundColor -> context.getString(R.string.player_settings_caption_style_background_color_label)
            Settings.Subtitle.Style.BackgroundOpacity -> context.getString(R.string.player_settings_caption_style_background_opacity_label)
            Settings.Subtitle.Style.WindowColor -> context.getString(R.string.player_settings_caption_style_window_color_label)
            Settings.Subtitle.Style.WindowOpacity -> context.getString(R.string.player_settings_caption_style_window_opacity_label)
            Settings.Subtitle.Style.Margin -> context.getString(R.string.player_settings_caption_style_margin_label)
        }

        is Settings.Subtitle.Style.FontColor -> context.getString(item.stringId)
        is Settings.Subtitle.Style.Margin -> item.value.toString()
        is Settings.Subtitle.Style.TextSize -> context.getString(item.stringId)
        is Settings.Subtitle.Style.FontOpacity -> context.getString(item.stringId)
        is Settings.Subtitle.Style.EdgeStyle -> context.getString(item.stringId)
        is Settings.Subtitle.Style.BackgroundColor -> context.getString(item.stringId)
        is Settings.Subtitle.Style.BackgroundOpacity -> context.getString(item.stringId)
        is Settings.Subtitle.Style.WindowColor -> context.getString(item.stringId)
        is Settings.Subtitle.Style.WindowOpacity -> context.getString(item.stringId)

        is Settings.Subtitle.OpenSubtitles.Subtitle -> item.openSubtitle.subFileName.orEmpty()
        is Settings.Subtitle.SubDLSubtitles.Subtitle ->
            (item.subDLSubtitle.releaseName ?: item.subDLSubtitle.name).orEmpty()

        is Settings.Speed -> context.getString(item.stringId)
        is Settings.ExtraBuffering -> context.getString(item.stringId)
        is Settings.SoftwareDecoder -> context.getString(item.stringId)
        is Settings.Gestures -> context.getString(item.stringId)
        is Settings.KeepScreenOn -> context.getString(item.stringId)
        is Settings.Server -> item.name

        else -> ""
    }

    /** Secondary/subtitle text for a row, or an empty string when it should not be shown. */
    fun subText(context: Context, item: Item, isTvLayout: Boolean): String = when (item) {
        is Settings -> when (item) {
            Settings.Quality -> when (val selected = Settings.Quality.selected) {
                is Settings.Quality.Auto -> when (val track = selected.currentTrack) {
                    null -> context.getString(R.string.player_settings_quality_auto)
                    else -> context.getString(R.string.player_settings_quality_auto_selected, track.height)
                }
                is Settings.Quality.VideoTrackInformation -> context.getString(R.string.player_settings_quality, selected.height)
            }
            Settings.Audio -> Settings.Audio.selected?.name ?: ""
            Settings.Subtitle -> when (val selected = Settings.Subtitle.selected) {
                is Settings.Subtitle.TextTrackInformation -> selected.label
                else -> context.getString(R.string.player_settings_subtitles_off)
            }
            Settings.Speed -> context.getString(Settings.Speed.selected.stringId)
            Settings.ExtraBuffering -> context.getString(Settings.ExtraBuffering.selected.stringId)
            Settings.SoftwareDecoder -> context.getString(Settings.SoftwareDecoder.selected.stringId)
            Settings.Gestures -> context.getString(Settings.Gestures.selected.stringId)
            Settings.KeepScreenOn -> context.getString(Settings.KeepScreenOn.selected.stringId)
            Settings.Server -> Settings.Server.selected?.name ?: ""
            Settings.ManualZoom -> ""
            Settings.Download -> ""
        }

        is Settings.Subtitle -> when (item) {
            Settings.Subtitle.Style -> context.getString(R.string.player_settings_caption_style_sub_label)
            Settings.Subtitle.Offset -> context.getString(
                R.string.player_settings_subtitle_offset_seconds,
                Settings.Subtitle.Offset.selected.milliseconds / 1_000.0,
            )
            is Settings.Subtitle.TextTrackInformation -> item.language ?: ""
            else -> ""
        }

        is Settings.Subtitle.Style -> when (item) {
            Settings.Subtitle.Style.ResetStyle -> ""
            Settings.Subtitle.Style.FontColor -> context.getString(Settings.Subtitle.Style.FontColor.selected.stringId)
            Settings.Subtitle.Style.TextSize -> context.getString(Settings.Subtitle.Style.TextSize.selected.stringId)
            Settings.Subtitle.Style.FontOpacity -> context.getString(Settings.Subtitle.Style.FontOpacity.selected.stringId)
            Settings.Subtitle.Style.EdgeStyle -> context.getString(Settings.Subtitle.Style.EdgeStyle.selected.stringId)
            Settings.Subtitle.Style.BackgroundColor -> context.getString(Settings.Subtitle.Style.BackgroundColor.selected.stringId)
            Settings.Subtitle.Style.BackgroundOpacity -> context.getString(Settings.Subtitle.Style.BackgroundOpacity.selected.stringId)
            Settings.Subtitle.Style.WindowColor -> context.getString(Settings.Subtitle.Style.WindowColor.selected.stringId)
            Settings.Subtitle.Style.WindowOpacity -> context.getString(Settings.Subtitle.Style.WindowOpacity.selected.stringId)
            Settings.Subtitle.Style.Margin -> Settings.Subtitle.Style.Margin.selected.value.toString()
        }

        is Settings.Subtitle.OpenSubtitles.Subtitle -> item.openSubtitle.languageName ?: ""
        is Settings.Subtitle.SubDLSubtitles.Subtitle -> item.subDLSubtitle.lang
            ?.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.getDefault()) else it.toString() }
            ?: ""

        else -> ""
    }

    /** Whether the trailing checkmark should be shown for [item]. */
    fun isSelected(item: Item): Boolean = when (item) {
        is Settings.Quality -> item.isSelected
        is Settings.Audio -> item.isSelected
        is Settings.Subtitle -> when (item) {
            is Settings.Subtitle.None -> item.isSelected
            is Settings.Subtitle.TextTrackInformation -> item.isSelected
            else -> false
        }
        is Settings.Subtitle.Style.FontColor -> item.isSelected
        is Settings.Subtitle.Style.TextSize -> item.isSelected
        is Settings.Subtitle.Style.FontOpacity -> item.isSelected
        is Settings.Subtitle.Style.EdgeStyle -> item.isSelected
        is Settings.Subtitle.Style.BackgroundColor -> item.isSelected
        is Settings.Subtitle.Style.BackgroundOpacity -> item.isSelected
        is Settings.Subtitle.Style.WindowColor -> item.isSelected
        is Settings.Subtitle.Style.WindowOpacity -> item.isSelected
        is Settings.Subtitle.Style.Margin -> item.isSelected
        is Settings.Subtitle.Offset.Value -> item.isSelected
        is Settings.Speed -> item.isSelected
        is Settings.ExtraBuffering -> item.isSelected
        is Settings.SoftwareDecoder -> item.isSelected
        is Settings.Gestures -> item.isSelected
        is Settings.KeepScreenOn -> item.isSelected
        is Settings.Server -> item.isSelected
        else -> false
    }

    /** Whether the trailing chevron ("enter this menu") affordance should be shown for [item]. */
    fun showsChevron(item: Item): Boolean = when (item) {
        is Settings -> when (item) {
            Settings.Quality,
            Settings.Audio,
            Settings.Subtitle,
            Settings.Speed,
            Settings.ExtraBuffering,
            Settings.SoftwareDecoder,
            Settings.Gestures,
            Settings.KeepScreenOn,
            Settings.Server -> true
            else -> false
        }

        is Settings.Subtitle -> when (item) {
            Settings.Subtitle.Style -> true
            Settings.Subtitle.Offset -> true
            is Settings.Subtitle.None -> false
            is Settings.Subtitle.TextTrackInformation -> false
            Settings.Subtitle.LocalSubtitles -> true
            Settings.Subtitle.OpenSubtitles -> true
            Settings.Subtitle.SubDLSubtitles -> true
        }

        is Settings.Subtitle.Style -> when (item) {
            Settings.Subtitle.Style.ResetStyle -> false
            else -> true
        }

        else -> false
    }

    /** The color swatch to render next to [item], or `null` if it has none. */
    fun colorSwatch(item: Item): Int? = when (item) {
        is Settings.Subtitle.Style.FontColor -> item.color
        is Settings.Subtitle.Style.BackgroundColor -> item.color
        is Settings.Subtitle.Style.WindowColor -> item.color
        else -> null
    }

    /** The leading icon for top-level [Settings] categories, or `null` for leaf items. */
    fun iconRes(item: Item): Int? = when (item) {
        is Settings -> when (item) {
            Settings.Quality -> R.drawable.ic_player_settings_quality
            Settings.Audio -> R.drawable.ic_player_settings_audio
            Settings.Subtitle -> if (Settings.Subtitle.selected is Settings.Subtitle.TextTrackInformation) {
                R.drawable.ic_player_settings_subtitle_on
            } else {
                R.drawable.ic_player_settings_subtitle_off
            }
            Settings.Speed -> R.drawable.ic_player_settings_playback_speed
            Settings.ExtraBuffering -> R.drawable.ic_player_settings_extra_buffer
            Settings.SoftwareDecoder -> R.drawable.ic_player_settings_extra_buffer
            Settings.Server -> R.drawable.ic_player_settings_servers
            Settings.Gestures -> R.drawable.ic_player_settings_gestures
            Settings.KeepScreenOn -> R.drawable.ic_brightness
            Settings.Download -> R.drawable.ic_player_settings_servers
            Settings.ManualZoom -> R.drawable.exo_styled_controls_aspect_ratio
        }
        else -> null
    }

    data class ItemSpacing(val top: Dp = 0.dp, val bottom: Dp = 0.dp)

    fun spacingFor(item: Item): ItemSpacing = when (item) {
        Settings.Subtitle.Style,
        Settings.Subtitle.Style.ResetStyle -> ItemSpacing(bottom = 6.dp)
        Settings.Subtitle.LocalSubtitles -> ItemSpacing(top = 6.dp)
        else -> ItemSpacing()
    }
}
