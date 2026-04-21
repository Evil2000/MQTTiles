package de.evil2000.mqttiles;

import android.content.Intent;
import android.os.Bundle;
import android.widget.GridView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

/**
 * Grid picker that returns a drawable resource id via {@link #ICON_RES_ID}. The caller passes
 * the metric type in {@link MetricSwitchSettingsActivity#METRIC_TYPE_KEY}; only Switch (2) and
 * Color (6) metrics show icons — other types see an empty grid by design.
 */
public class IconSelectorActivity extends AppCompatActivity {

    /** Key for the selected drawable resource id in the result intent. */
    public static final String ICON_RES_ID = "ICON_RES_ID";

    /** Full catalogue of icons offered to Switch/Color tiles (MDI-style on/off pairs, then material icons). */
    private static final int[] ICON_RES_IDS = {
            R.drawable.ic_bell_on, R.drawable.ic_bell_off,
            R.drawable.ic_check_box_checked, R.drawable.ic_check_box_unchecked,
            R.drawable.ic_cloud_on, R.drawable.ic_cloud_off,
            R.drawable.ic_cloud_download, R.drawable.ic_cloud_upload,
            R.drawable.ic_flash_on, R.drawable.ic_flash_off,
            R.drawable.ic_layers_on, R.drawable.ic_layers_off,
            R.drawable.ic_lock_opened_outline, R.drawable.ic_lock_closed_outline,
            R.drawable.ic_mic_on, R.drawable.ic_mic_off,
            R.drawable.ic_radio_button_checked, R.drawable.ic_radio_button_unchecked,
            R.drawable.ic_thumb_up, R.drawable.ic_thumb_down,
            R.drawable.ic_trending_up, R.drawable.ic_trending_down,
            R.drawable.ic_visibility_on, R.drawable.ic_visibility_off,
            R.drawable.ic_volume_on, R.drawable.ic_volume_off,
            R.drawable.ic_wifi_on, R.drawable.ic_wifi_off,
            R.drawable.ic_biohazard_on, R.drawable.ic_biohazard_off,
            R.drawable.ic_cam1_on, R.drawable.ic_cam1_off,
            R.drawable.ic_cam2_on, R.drawable.ic_cam2_off,
            R.drawable.ic_device_on, R.drawable.ic_device_off,
            R.drawable.ic_firewall_on, R.drawable.ic_firewall_off,
            R.drawable.ic_light1_on, R.drawable.ic_light1_off,
            R.drawable.ic_light2_on, R.drawable.ic_light2_off,
            R.drawable.ic_light3_on, R.drawable.ic_light3_off,
            R.drawable.ic_propeller_on, R.drawable.ic_propeller_off,
            R.drawable.ic_radiation_on, R.drawable.ic_radiation_off,
            R.drawable.ic_shower1_on, R.drawable.ic_shower1_off,
            R.drawable.ic_shower2_on, R.drawable.ic_shower2_off,
            R.drawable.ic_skull1_on, R.drawable.ic_skull1_off,
            R.drawable.ic_skull2_on, R.drawable.ic_skull2_off,
            R.drawable.ic_speaker_on, R.drawable.ic_speaker_off,
            R.drawable.ic_tv_on, R.drawable.ic_tv_off,
            R.drawable.ic_aircon, R.drawable.ic_ampermeter,
            R.drawable.ic_battery_01, R.drawable.ic_battery_02, R.drawable.ic_battery_03,
            R.drawable.ic_battery_04, R.drawable.ic_battery_05, R.drawable.ic_battery_06,
            R.drawable.ic_battery_07, R.drawable.ic_battery_charging,
            R.drawable.ic_boiler, R.drawable.ic_bomb, R.drawable.ic_candle, R.drawable.ic_car,
            R.drawable.ic_cctv_camera, R.drawable.ic_ceiling_light,
            R.drawable.ic_coffee_cup_01, R.drawable.ic_coffee_cup_02,
            R.drawable.ic_coffee_machine_01, R.drawable.ic_coffee_machine_02,
            R.drawable.ic_cooker_01, R.drawable.ic_cooker_02, R.drawable.ic_cooker_03,
            R.drawable.ic_fan_01, R.drawable.ic_fan_02, R.drawable.ic_fan_03,
            R.drawable.ic_fan_04, R.drawable.ic_fan_05,
            R.drawable.ic_fence, R.drawable.ic_fire_01, R.drawable.ic_fire_02,
            R.drawable.ic_fire_containment, R.drawable.ic_flame_vacuum, R.drawable.ic_flower,
            R.drawable.ic_fridge_01, R.drawable.ic_fridge_02,
            R.drawable.ic_heater_01, R.drawable.ic_heater_02,
            R.drawable.ic_heating, R.drawable.ic_heating_fire, R.drawable.ic_ignition,
            R.drawable.ic_kettle_01, R.drawable.ic_kettle_02,
            R.drawable.ic_kitchen_vacuum_01, R.drawable.ic_kitchen_vacuum_02,
            R.drawable.ic_kitchen_vessel, R.drawable.ic_led,
            R.drawable.ic_light_bulb_01, R.drawable.ic_light_bulb_02, R.drawable.ic_light_bulb_03,
            R.drawable.ic_light_bulb_04, R.drawable.ic_light_bulb_05, R.drawable.ic_light_bulb_06,
            R.drawable.ic_light_bulb_07,
            R.drawable.ic_light_plug, R.drawable.ic_lightning, R.drawable.ic_meat_chopper,
            R.drawable.ic_mic, R.drawable.ic_microwave, R.drawable.ic_mixer, R.drawable.ic_music,
            R.drawable.ic_park_light, R.drawable.ic_pc, R.drawable.ic_portable_radio,
            R.drawable.ic_power_fork, R.drawable.ic_power_plug_01, R.drawable.ic_power_plug_02,
            R.drawable.ic_power_station, R.drawable.ic_power_switch, R.drawable.ic_protection,
            R.drawable.ic_radiator_01, R.drawable.ic_radiator_02,
            R.drawable.ic_radiator_03, R.drawable.ic_radiator_04,
            R.drawable.ic_satellite, R.drawable.ic_satellite_dish,
            R.drawable.ic_solar_panel_01, R.drawable.ic_solar_panel_02,
            R.drawable.ic_star_01,
            R.drawable.ic_stereo_01, R.drawable.ic_stereo_02, R.drawable.ic_stereo_03,
            R.drawable.ic_stove_01, R.drawable.ic_stove_02, R.drawable.ic_sun,
            R.drawable.ic_table_light_01, R.drawable.ic_table_light_02, R.drawable.ic_table_light_03,
            R.drawable.ic_table_light_04, R.drawable.ic_table_light_05,
            R.drawable.ic_thermometer_01, R.drawable.ic_torch, R.drawable.ic_tri_pipe,
            R.drawable.ic_wall, R.drawable.ic_wall_light, R.drawable.ic_wall_switch,
            R.drawable.ic_washing_mashine_01, R.drawable.ic_washing_mashine_02,
            R.drawable.ic_washsink, R.drawable.ic_watch, R.drawable.ic_water, R.drawable.ic_water_pump,
            R.drawable.ic_water_tap_01, R.drawable.ic_water_tap_02,
            R.drawable.ic_water_tap_03, R.drawable.ic_water_tap_04,
            R.drawable.ic_water_vessel, R.drawable.ic_weight, R.drawable.ic_wireless_mouse,
            R.drawable.ic_3d_rotation, R.drawable.ic_accessibility, R.drawable.ic_accessible,
            R.drawable.ic_account_balance, R.drawable.ic_account_balance_wallet,
            R.drawable.ic_account_box, R.drawable.ic_account_circle,
            R.drawable.ic_add_shopping_cart,
            R.drawable.ic_alarm, R.drawable.ic_alarm_add, R.drawable.ic_alarm_off, R.drawable.ic_alarm_on,
            R.drawable.ic_all_out, R.drawable.ic_android, R.drawable.ic_announcement,
            R.drawable.ic_aspect_ratio, R.drawable.ic_assessment,
            R.drawable.ic_assignment, R.drawable.ic_assignment_ind, R.drawable.ic_assignment_late,
            R.drawable.ic_assignment_return, R.drawable.ic_assignment_returned,
            R.drawable.ic_assignment_turned,
            R.drawable.ic_autorenew, R.drawable.ic_book, R.drawable.ic_bookmark, R.drawable.ic_bookmark_border,
            R.drawable.ic_bug_report, R.drawable.ic_build,
            R.drawable.ic_camera_enhance, R.drawable.ic_card_giftcard,
            R.drawable.ic_card_membership, R.drawable.ic_card_travel,
            R.drawable.ic_change_history, R.drawable.ic_check_circle, R.drawable.ic_chrome_reader_mode,
            R.drawable.ic_class, R.drawable.ic_code, R.drawable.ic_compare_arrows,
            R.drawable.ic_copyright, R.drawable.ic_credit_card,
            R.drawable.ic_dashboard, R.drawable.ic_date_range,
            R.drawable.ic_delete, R.drawable.ic_description, R.drawable.ic_dns,
            R.drawable.ic_done, R.drawable.ic_done_all,
            R.drawable.ic_donut_large, R.drawable.ic_donut_small,
            R.drawable.ic_eject, R.drawable.ic_event_seat, R.drawable.ic_exit_to_app,
            R.drawable.ic_explore, R.drawable.ic_extension,
            R.drawable.ic_face, R.drawable.ic_favorite, R.drawable.ic_favorite_border,
            R.drawable.ic_feedback, R.drawable.ic_find_in_page, R.drawable.ic_find_replace,
            R.drawable.ic_fingerprint, R.drawable.ic_flight_land, R.drawable.ic_flight_takeoff,
            R.drawable.ic_flip_to_back, R.drawable.ic_flip_to_front,
            R.drawable.ic_gavel, R.drawable.ic_get_app, R.drawable.ic_grade, R.drawable.ic_group_work,
            R.drawable.ic_help, R.drawable.ic_help_outline, R.drawable.ic_highlight_off,
            R.drawable.ic_history, R.drawable.ic_home,
            R.drawable.ic_hourglass_empty, R.drawable.ic_hourglass_full,
            R.drawable.ic_https, R.drawable.ic_important_devices,
            R.drawable.ic_info, R.drawable.ic_info_outline, R.drawable.ic_input, R.drawable.ic_invert_colors,
            R.drawable.ic_label, R.drawable.ic_label_outline, R.drawable.ic_language, R.drawable.ic_launch,
            R.drawable.ic_lightbulb_outline, R.drawable.ic_loyalty,
            R.drawable.ic_markunread_mailbox, R.drawable.ic_motorcycle, R.drawable.ic_note_add,
            R.drawable.ic_offline_pin, R.drawable.ic_opacity,
            R.drawable.ic_open_in_browser, R.drawable.ic_open_with, R.drawable.ic_pan_tool,
            R.drawable.ic_perm_camera_mic, R.drawable.ic_perm_contact_calendar,
            R.drawable.ic_perm_data_setting, R.drawable.ic_perm_device_information,
            R.drawable.ic_perm_media, R.drawable.ic_perm_phone_msg, R.drawable.ic_perm_scan_wifi,
            R.drawable.ic_pets, R.drawable.ic_picture_in_picture, R.drawable.ic_picture_in_picture_alt,
            R.drawable.ic_play_for_work, R.drawable.ic_polymer,
            R.drawable.ic_power_settings_new, R.drawable.ic_print,
            R.drawable.ic_query_builder, R.drawable.ic_question_answer,
            R.drawable.ic_receipt, R.drawable.ic_record_voice_over, R.drawable.ic_reorder,
            R.drawable.ic_report_problem, R.drawable.ic_restore,
            R.drawable.ic_room, R.drawable.ic_rounded_corner, R.drawable.ic_rowing,
            R.drawable.ic_settings, R.drawable.ic_settings_applications,
            R.drawable.ic_settings_backup_restore, R.drawable.ic_settings_bluetooth,
            R.drawable.ic_settings_brightness, R.drawable.ic_settings_cell,
            R.drawable.ic_settings_ethernet, R.drawable.ic_settings_input_antenna,
            R.drawable.ic_settings_input_component, R.drawable.ic_settings_input_hdmi,
            R.drawable.ic_settings_input_svideo, R.drawable.ic_settings_overscan,
            R.drawable.ic_settings_phone, R.drawable.ic_settings_power,
            R.drawable.ic_settings_remote, R.drawable.ic_settings_voice,
            R.drawable.ic_shop, R.drawable.ic_shop_two,
            R.drawable.ic_shopping_basket, R.drawable.ic_shopping_cart,
            R.drawable.ic_speaker_notes, R.drawable.ic_spellcheck,
            R.drawable.ic_stars, R.drawable.ic_store, R.drawable.ic_supervisor_account,
            R.drawable.ic_swap_horiz, R.drawable.ic_swap_vert, R.drawable.ic_swap_vertical_circle,
            R.drawable.ic_system_update_alt, R.drawable.ic_theaters,
            R.drawable.ic_thumbs_up_down, R.drawable.ic_timeline, R.drawable.ic_toll,
            R.drawable.ic_touch_app, R.drawable.ic_track_changes,
            R.drawable.ic_translate, R.drawable.ic_trending_flat, R.drawable.ic_verified_user,
            R.drawable.ic_view_agenda, R.drawable.ic_zoom_in, R.drawable.ic_zoom_out,
            R.drawable.ic_add_alert, R.drawable.ic_add_to_queue,
            R.drawable.ic_airplanemode_active, R.drawable.ic_airplanemode_inactive,
            R.drawable.ic_airplay_black, R.drawable.ic_album, R.drawable.ic_art_track,
            R.drawable.ic_av_timer,
            R.drawable.ic_battery_20, R.drawable.ic_battery_30, R.drawable.ic_battery_50,
            R.drawable.ic_battery_60, R.drawable.ic_battery_80, R.drawable.ic_battery_90,
            R.drawable.ic_battery_alert,
            R.drawable.ic_battery_charging_20, R.drawable.ic_battery_charging_30,
            R.drawable.ic_battery_charging_50, R.drawable.ic_battery_charging_60,
            R.drawable.ic_battery_charging_80, R.drawable.ic_battery_charging_90,
            R.drawable.ic_battery_charging_full, R.drawable.ic_battery_full,
            R.drawable.ic_battery_unknown,
            R.drawable.ic_bluetooth, R.drawable.ic_bluetooth_connected,
            R.drawable.ic_bluetooth_disabled, R.drawable.ic_bluetooth_searching,
            R.drawable.ic_call, R.drawable.ic_call_end,
            R.drawable.ic_cast, R.drawable.ic_cast_connected,
            R.drawable.ic_center_focus_strong, R.drawable.ic_center_focus_weak,
            R.drawable.ic_closed_caption,
            R.drawable.ic_cloud, R.drawable.ic_cloud_circle,
            R.drawable.ic_cloud_done, R.drawable.ic_cloud_queue,
            R.drawable.ic_equalizer, R.drawable.ic_error, R.drawable.ic_error_outline,
            R.drawable.ic_explicit,
            R.drawable.ic_fast_forward, R.drawable.ic_fast_rewind,
            R.drawable.ic_fiber_dvr, R.drawable.ic_fiber_manual_record,
            R.drawable.ic_fiber_new, R.drawable.ic_fiber_pin,
            R.drawable.ic_flash_auto,
            R.drawable.ic_forward_10, R.drawable.ic_forward_30, R.drawable.ic_forward_5,
            R.drawable.ic_games,
            R.drawable.ic_gps_fixed, R.drawable.ic_gps_not_fixed, R.drawable.ic_gps_off,
            R.drawable.ic_hd, R.drawable.ic_headset, R.drawable.ic_hearing, R.drawable.ic_high_quality,
            R.drawable.ic_keyboard,
            R.drawable.ic_library_add, R.drawable.ic_library_books, R.drawable.ic_library_music,
            R.drawable.ic_loop_black, R.drawable.ic_movie, R.drawable.ic_music_video,
            R.drawable.ic_new_releases, R.drawable.ic_not_interested,
            R.drawable.ic_palette,
            R.drawable.ic_pause, R.drawable.ic_pause_circle_filled, R.drawable.ic_pause_circle_outline,
            R.drawable.ic_play_arrow, R.drawable.ic_play_circle_filled, R.drawable.ic_play_circle_outline,
            R.drawable.ic_playlist_add, R.drawable.ic_playlist_add_check, R.drawable.ic_playlist_play,
            R.drawable.ic_ring_volume, R.drawable.ic_screen_share, R.drawable.ic_sd_storage,
            R.drawable.ic_shuffle,
            R.drawable.ic_signal_cellular_0_bar, R.drawable.ic_signal_cellular_1_bar,
            R.drawable.ic_signal_cellular_2_bar, R.drawable.ic_signal_cellular_3_bar,
            R.drawable.ic_signal_cellular_4_bar, R.drawable.ic_signal_cellular_null,
            R.drawable.ic_signal_cellular_off,
            R.drawable.ic_signal_wifi_0_bar, R.drawable.ic_signal_wifi_1_bar,
            R.drawable.ic_signal_wifi_2_bar, R.drawable.ic_signal_wifi_3_bar,
            R.drawable.ic_signal_wifi_4_bar, R.drawable.ic_signal_wifi_off,
            R.drawable.ic_skip_next, R.drawable.ic_skip_previous,
            R.drawable.ic_stay_primary_landscape, R.drawable.ic_stay_primary_portrait,
            R.drawable.ic_stop, R.drawable.ic_stop_screen_share,
            R.drawable.ic_usb, R.drawable.ic_voicemail, R.drawable.ic_vpn_key, R.drawable.ic_warning,
    };

    private GridView mGridView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_icon_selector);
        mGridView = findViewById(R.id.gridView);
    }

    @Override
    protected void onStart() {
        super.onStart();
        int metricType = getIntent().getIntExtra(MetricSwitchSettingsActivity.METRIC_TYPE_KEY, 1);

        ArrayList<Integer> icons = new ArrayList<>();
        if (metricType == 2 || metricType == 6) {
            for (int resId : ICON_RES_IDS) icons.add(resId);
        }

        mGridView.setAdapter(new ImageAdapter(this, icons, v -> {
            Integer resId = (Integer) v.getTag();
            Intent out = new Intent();
            out.putExtra(ICON_RES_ID, resId);
            setResult(RESULT_OK, out);
            finish();
        }));
    }
}
