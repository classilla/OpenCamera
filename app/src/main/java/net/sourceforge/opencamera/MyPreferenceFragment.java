package net.sourceforge.opencamera;

import net.sourceforge.opencamera.cameracontroller.CameraController;
import net.sourceforge.opencamera.preview.Preview;
import net.sourceforge.opencamera.ui.ArraySeekBarPreference;
import net.sourceforge.opencamera.ui.FolderChooserDialog;
import net.sourceforge.opencamera.ui.MyEditTextPreference;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
//import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.Point;
//import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceGroup;
import android.preference.PreferenceManager;
import android.preference.TwoStatePreference;
import android.text.Html;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

/** Fragment to handle the Settings UI. Note that originally this was a
 *  PreferenceActivity rather than a PreferenceFragment which required all
 *  communication to be via the bundle (since this replaced the MainActivity,
 *  meaning we couldn't access data from that class. This no longer applies due
 *  to now using a PreferenceFragment, but I've still kept with transferring
 *  information via the bundle (for the most part, at least).
 *  Also note that passing via a bundle may be necessary to avoid accessing the
 *  preview, which can be null - see note about video resolutions below.
 *  Also see https://stackoverflow.com/questions/14093438/after-the-rotate-oncreate-fragment-is-called-before-oncreate-fragmentactivi .
 */
public class MyPreferenceFragment extends PreferenceFragment implements OnSharedPreferenceChangeListener {
    private static final String TAG = "MyPreferenceFragment";

    private int cameraId;

    /* Any AlertDialogs we create should be added to dialogs, and removed when dismissed. Any dialogs still
     * opened when onDestroy() is called are closed.
     * Normally this shouldn't be needed - the settings is usually only closed by the user pressing Back,
     * which can only be done once any opened dialogs are also closed. But this is required if we want to
     * programmatically close the settings - this is done in MainActivity.onNewIntent(), so that if Open Camera
     * is launched from the homescreen again when the settings was opened, we close the settings.
     * UPDATE: At the time of writing, we don't set android:launchMode="singleTask", so onNewIntent() is not called,
     * so this code isn't necessary - but there shouldn't be harm to leave it here for future use.
     */
    private final HashSet<AlertDialog> dialogs = new HashSet<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        if( MyDebug.LOG )
            Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.preferences);

        final Bundle bundle = getArguments();
        this.cameraId = bundle.getInt("cameraId");
        if( MyDebug.LOG )
            Log.d(TAG, "cameraId: " + cameraId);
        final int nCameras = bundle.getInt("nCameras");
        if( MyDebug.LOG )
            Log.d(TAG, "nCameras: " + nCameras);

        final String camera_api = bundle.getString("camera_api");

        final String photo_mode_string = bundle.getString("photo_mode_string");

        final boolean using_android_l = bundle.getBoolean("using_android_l");
        if( MyDebug.LOG )
            Log.d(TAG, "using_android_l: " + using_android_l);

        final int camera_orientation = bundle.getInt("camera_orientation");
        if( MyDebug.LOG )
            Log.d(TAG, "camera_orientation: " + camera_orientation);

        final SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getActivity());

        final boolean supports_auto_stabilise = bundle.getBoolean("supports_auto_stabilise");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_auto_stabilise: " + supports_auto_stabilise);

		/*if( !supports_auto_stabilise ) {
			Preference pref = findPreference("preference_auto_stabilise");
			PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_category_camera_effects");
        	pg.removePreference(pref);
		}*/

        final boolean supports_flash = bundle.getBoolean("supports_flash");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_flash: " + supports_flash);

        //readFromBundle(bundle, "color_effects", Preview.getColorEffectPreferenceKey(), Camera.Parameters.EFFECT_NONE, "preference_category_camera_effects");
        //readFromBundle(bundle, "scene_modes", Preview.getSceneModePreferenceKey(), Camera.Parameters.SCENE_MODE_AUTO, "preference_category_camera_effects");
        //readFromBundle(bundle, "white_balances", Preview.getWhiteBalancePreferenceKey(), Camera.Parameters.WHITE_BALANCE_AUTO, "preference_category_camera_effects");
        //readFromBundle(bundle, "isos", Preview.getISOPreferenceKey(), "auto", "preference_category_camera_effects");
        //readFromBundle(bundle, "exposures", "preference_exposure", "0", "preference_category_camera_effects");

        boolean has_antibanding = false;
        String [] antibanding_values = bundle.getStringArray("antibanding");
        if( antibanding_values != null && antibanding_values.length > 0 ) {
            String [] antibanding_entries = bundle.getStringArray("antibanding_entries");
            if( antibanding_entries != null && antibanding_entries.length == antibanding_values.length ) { // should always be true here, but just in case
                readFromBundle(antibanding_values, antibanding_entries, PreferenceKeys.AntiBandingPreferenceKey, CameraController.ANTIBANDING_DEFAULT, "preference_screen_processing_settings");
                has_antibanding = true;
            }
        }
        if( MyDebug.LOG )
            Log.d(TAG, "has_antibanding?: " + has_antibanding);
        if( !has_antibanding ) {
            Preference pref = findPreference(PreferenceKeys.AntiBandingPreferenceKey);
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_processing_settings");
            pg.removePreference(pref);
        }

        boolean has_edge_mode = false;
        String [] edge_mode_values = bundle.getStringArray("edge_modes");
        if( edge_mode_values != null && edge_mode_values.length > 0 ) {
            String [] edge_mode_entries = bundle.getStringArray("edge_modes_entries");
            if( edge_mode_entries != null && edge_mode_entries.length == edge_mode_values.length ) { // should always be true here, but just in case
                readFromBundle(edge_mode_values, edge_mode_entries, PreferenceKeys.EdgeModePreferenceKey, CameraController.EDGE_MODE_DEFAULT, "preference_screen_processing_settings");
                has_edge_mode = true;
            }
        }
        if( MyDebug.LOG )
            Log.d(TAG, "has_edge_mode?: " + has_edge_mode);
        if( !has_edge_mode ) {
            Preference pref = findPreference(PreferenceKeys.EdgeModePreferenceKey);
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_processing_settings");
            pg.removePreference(pref);
        }

        boolean has_noise_reduction_mode = false;
        String [] noise_reduction_mode_values = bundle.getStringArray("noise_reduction_modes");
        if( noise_reduction_mode_values != null && noise_reduction_mode_values.length > 0 ) {
            String [] noise_reduction_mode_entries = bundle.getStringArray("noise_reduction_modes_entries");
            if( noise_reduction_mode_entries != null && noise_reduction_mode_entries.length == noise_reduction_mode_values.length ) { // should always be true here, but just in case
                readFromBundle(noise_reduction_mode_values, noise_reduction_mode_entries, PreferenceKeys.CameraNoiseReductionModePreferenceKey, CameraController.NOISE_REDUCTION_MODE_DEFAULT, "preference_screen_processing_settings");
                has_noise_reduction_mode = true;
            }
        }
        if( MyDebug.LOG )
            Log.d(TAG, "has_noise_reduction_mode?: " + has_noise_reduction_mode);
        if( !has_noise_reduction_mode ) {
            Preference pref = findPreference(PreferenceKeys.CameraNoiseReductionModePreferenceKey);
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_processing_settings");
            pg.removePreference(pref);
        }

        final boolean supports_face_detection = bundle.getBoolean("supports_face_detection");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_face_detection: " + supports_face_detection);

        if( !supports_face_detection ) {
            Preference pref = findPreference("preference_face_detection");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_category_camera_controls");
            pg.removePreference(pref);

            pref = findPreference("preference_show_face_detection");
            pg = (PreferenceGroup)this.findPreference("preference_screen_gui");
            pg.removePreference(pref);
        }

        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR2 ) {
            // BluetoothLeService requires Android 4.3+
            Preference pref = findPreference("preference_screen_remote_control");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_camera_controls_more");
            pg.removePreference(pref);
        }

        final int preview_width = bundle.getInt("preview_width");
        final int preview_height = bundle.getInt("preview_height");
        final int [] preview_widths = bundle.getIntArray("preview_widths");
        final int [] preview_heights = bundle.getIntArray("preview_heights");
        final int [] video_widths = bundle.getIntArray("video_widths");
        final int [] video_heights = bundle.getIntArray("video_heights");
        final int [] video_fps = bundle.getIntArray("video_fps");
        final boolean [] video_fps_high_speed = bundle.getBooleanArray("video_fps_high_speed");

        final int resolution_width = bundle.getInt("resolution_width");
        final int resolution_height = bundle.getInt("resolution_height");
        final int [] widths = bundle.getIntArray("resolution_widths");
        final int [] heights = bundle.getIntArray("resolution_heights");
        final boolean [] supports_burst = bundle.getBooleanArray("resolution_supports_burst");
        if( widths != null && heights != null && supports_burst != null ) {
            CharSequence [] entries = new CharSequence[widths.length];
            CharSequence [] values = new CharSequence[widths.length];
            for(int i=0;i<widths.length;i++) {
                entries[i] = widths[i] + " x " + heights[i] + " " + Preview.getAspectRatioMPString(getResources(), widths[i], heights[i], supports_burst[i]);
                values[i] = widths[i] + " " + heights[i];
            }
            ListPreference lp = (ListPreference)findPreference("preference_resolution");
            lp.setEntries(entries);
            lp.setEntryValues(values);
            String resolution_preference_key = PreferenceKeys.getResolutionPreferenceKey(cameraId);
            String resolution_value = sharedPreferences.getString(resolution_preference_key, "");
            if( MyDebug.LOG )
                Log.d(TAG, "resolution_value: " + resolution_value);
            lp.setValue(resolution_value);
            // now set the key, so we save for the correct cameraId
            lp.setKey(resolution_preference_key);
        }
        else {
            Preference pref = findPreference("preference_resolution");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_photo_settings");
            pg.removePreference(pref);
        }

        String fps_preference_key = PreferenceKeys.getVideoFPSPreferenceKey(cameraId);
        if( MyDebug.LOG )
            Log.d(TAG, "fps_preference_key: " + fps_preference_key);
        String fps_value = sharedPreferences.getString(fps_preference_key, "default");
        if( MyDebug.LOG )
            Log.d(TAG, "fps_value: " + fps_value);
        if( video_fps != null ) {
            // build video fps settings
            CharSequence [] entries = new CharSequence[video_fps.length+1];
            CharSequence [] values = new CharSequence[video_fps.length+1];
            int i=0;
            // default:
            entries[i] = getResources().getString(R.string.preference_video_fps_default);
            values[i] = "default";
            i++;
            final String high_speed_append = " [" + getResources().getString(R.string.high_speed) + "]";
            for(int k=0;k<video_fps.length;k++) {
                int fps = video_fps[k];
                if( video_fps_high_speed != null && video_fps_high_speed[k] ) {
                    entries[i] = fps + high_speed_append;
                }
                else {
                    entries[i] = "" + fps;
                }
                values[i] = "" + fps;
                i++;
            }

            ListPreference lp = (ListPreference)findPreference("preference_video_fps");
            lp.setEntries(entries);
            lp.setEntryValues(values);
            lp.setValue(fps_value);
            // now set the key, so we save for the correct cameraId
            lp.setKey(fps_preference_key);
        }

        {
            final int n_quality = 100;
            CharSequence [] entries = new CharSequence[n_quality];
            CharSequence [] values = new CharSequence[n_quality];
            for(int i=0;i<n_quality;i++) {
                entries[i] = "" + (i+1) + "%";
                values[i] = "" + (i+1);
            }
            ArraySeekBarPreference sp = (ArraySeekBarPreference)findPreference("preference_quality");
            sp.setEntries(entries);
            sp.setEntryValues(values);
        }

        {
            final int max_ghost_image_alpha = 80; // limit max to 80% for privacy reasons, so it isn't possible to put in a state where camera is on, but no preview is shown
            final int ghost_image_alpha_step = 5; // should be exact divisor of max_ghost_image_alpha
            final int n_ghost_image_alpha = max_ghost_image_alpha/ghost_image_alpha_step;
            CharSequence [] entries = new CharSequence[n_ghost_image_alpha];
            CharSequence [] values = new CharSequence[n_ghost_image_alpha];
            for(int i=0;i<n_ghost_image_alpha;i++) {
                int alpha = ghost_image_alpha_step*(i+1);
                entries[i] = "" + alpha + "%";
                values[i] = "" + alpha;
            }
            ArraySeekBarPreference sp = (ArraySeekBarPreference)findPreference("ghost_image_alpha");
            sp.setEntries(entries);
            sp.setEntryValues(values);
        }

        final boolean supports_raw = bundle.getBoolean("supports_raw");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_raw: " + supports_raw);
        final boolean supports_burst_raw = bundle.getBoolean("supports_burst_raw");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_burst_raw: " + supports_burst_raw);

        if( !supports_raw ) {
            Preference pref = findPreference("preference_raw");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_photo_settings");
            pg.removePreference(pref);
        }
        else {
            ListPreference pref = (ListPreference)findPreference("preference_raw");

            if( Build.VERSION.SDK_INT < Build.VERSION_CODES.N ) {
                // RAW only mode requires at least Android 7; earlier versions seem to have poorer support for DNG files
                pref.setEntries(R.array.preference_raw_entries_preandroid7);
                pref.setEntryValues(R.array.preference_raw_values_preandroid7);
            }

            pref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "clicked raw: " + newValue);
                    if( newValue.equals("preference_raw_yes") || newValue.equals("preference_raw_only") ) {
                        // we check done_raw_info every time, so that this works if the user selects RAW again without leaving and returning to Settings
                        boolean done_raw_info = sharedPreferences.contains(PreferenceKeys.RawInfoPreferenceKey);
                        if( !done_raw_info ) {
                            AlertDialog.Builder alertDialog = new AlertDialog.Builder(MyPreferenceFragment.this.getActivity());
                            alertDialog.setTitle(R.string.preference_raw);
                            alertDialog.setMessage(R.string.raw_info);
                            alertDialog.setPositiveButton(android.R.string.ok, null);
                            alertDialog.setNegativeButton(R.string.dont_show_again, new OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    if( MyDebug.LOG )
                                        Log.d(TAG, "user clicked dont_show_again for raw info dialog");
                                    SharedPreferences.Editor editor = sharedPreferences.edit();
                                    editor.putBoolean(PreferenceKeys.RawInfoPreferenceKey, true);
                                    editor.apply();
                                }
                            });
                            final AlertDialog alert = alertDialog.create();
                            // AlertDialog.Builder.setOnDismissListener() requires API level 17, so do it this way instead
                            alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
                                @Override
                                public void onDismiss(DialogInterface arg0) {
                                    if( MyDebug.LOG )
                                        Log.d(TAG, "raw dialog dismissed");
                                    dialogs.remove(alert);
                                }
                            });
                            alert.show();
                            dialogs.add(alert);
                        }
                    }
                    return true;
                }
            });
        }

        if( !( supports_raw && supports_burst_raw ) ) {
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_photo_settings");
            Preference pref = findPreference("preference_raw_expo_bracketing");
            pg.removePreference(pref);
            pref = findPreference("preference_raw_focus_bracketing");
            pg.removePreference(pref);
        }

        final boolean supports_hdr = bundle.getBoolean("supports_hdr");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_hdr: " + supports_hdr);

        if( !supports_hdr ) {
            Preference pref = findPreference("preference_hdr_save_expo");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_photo_settings");
            pg.removePreference(pref);

            pref = findPreference("preference_hdr_contrast_enhancement");
            pg = (PreferenceGroup)this.findPreference("preference_screen_photo_settings");
            pg.removePreference(pref);
        }


        final boolean supports_panorama = bundle.getBoolean("supports_panorama");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_panorama: " + supports_panorama);

        if( !supports_panorama ) {
            Preference pref = findPreference("preference_panorama_crop");
            PreferenceGroup pg = (PreferenceGroup) this.findPreference("preference_screen_photo_settings");
            pg.removePreference(pref);

            pref = findPreference("preference_panorama_save");
            pg = (PreferenceGroup) this.findPreference("preference_screen_photo_settings");
            pg.removePreference(pref);
        }

        final boolean has_gyro_sensors = bundle.getBoolean("has_gyro_sensors");
        if( MyDebug.LOG )
            Log.d(TAG, "has_gyro_sensors: " + has_gyro_sensors);

        final boolean supports_expo_bracketing = bundle.getBoolean("supports_expo_bracketing");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_expo_bracketing: " + supports_expo_bracketing);

        final int max_expo_bracketing_n_images = bundle.getInt("max_expo_bracketing_n_images");
        if( MyDebug.LOG )
            Log.d(TAG, "max_expo_bracketing_n_images: " + max_expo_bracketing_n_images);

        final boolean supports_nr = bundle.getBoolean("supports_nr");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_nr: " + supports_nr);

        if( !supports_nr ) {
            Preference pref = findPreference("preference_nr_save");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_photo_settings");
            pg.removePreference(pref);
        }

        final boolean supports_exposure_compensation = bundle.getBoolean("supports_exposure_compensation");
        final int exposure_compensation_min = bundle.getInt("exposure_compensation_min");
        final int exposure_compensation_max = bundle.getInt("exposure_compensation_max");
        if( MyDebug.LOG ) {
            Log.d(TAG, "supports_exposure_compensation: " + supports_exposure_compensation);
            Log.d(TAG, "exposure_compensation_min: " + exposure_compensation_min);
            Log.d(TAG, "exposure_compensation_max: " + exposure_compensation_max);
        }

        final boolean supports_iso_range = bundle.getBoolean("supports_iso_range");
        final int iso_range_min = bundle.getInt("iso_range_min");
        final int iso_range_max = bundle.getInt("iso_range_max");
        if( MyDebug.LOG ) {
            Log.d(TAG, "supports_iso_range: " + supports_iso_range);
            Log.d(TAG, "iso_range_min: " + iso_range_min);
            Log.d(TAG, "iso_range_max: " + iso_range_max);
        }

        final boolean supports_exposure_time = bundle.getBoolean("supports_exposure_time");
        final long exposure_time_min = bundle.getLong("exposure_time_min");
        final long exposure_time_max = bundle.getLong("exposure_time_max");
        if( MyDebug.LOG ) {
            Log.d(TAG, "supports_exposure_time: " + supports_exposure_time);
            Log.d(TAG, "exposure_time_min: " + exposure_time_min);
            Log.d(TAG, "exposure_time_max: " + exposure_time_max);
        }

        final boolean supports_exposure_lock = bundle.getBoolean("supports_exposure_lock");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_exposure_lock: " + supports_exposure_lock);

        final boolean supports_white_balance_lock = bundle.getBoolean("supports_white_balance_lock");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_white_balance_lock: " + supports_white_balance_lock);

        final boolean supports_white_balance_temperature = bundle.getBoolean("supports_white_balance_temperature");
        final int white_balance_temperature_min = bundle.getInt("white_balance_temperature_min");
        final int white_balance_temperature_max = bundle.getInt("white_balance_temperature_max");
        if( MyDebug.LOG ) {
            Log.d(TAG, "supports_white_balance_temperature: " + supports_white_balance_temperature);
            Log.d(TAG, "white_balance_temperature_min: " + white_balance_temperature_min);
            Log.d(TAG, "white_balance_temperature_max: " + white_balance_temperature_max);
        }

        if( !supports_expo_bracketing || max_expo_bracketing_n_images <= 3 ) {
            Preference pref = findPreference("preference_expo_bracketing_n_images");
            PreferenceGroup pg = (PreferenceGroup) this.findPreference("preference_screen_photo_settings");
            pg.removePreference(pref);
        }
        if( !supports_expo_bracketing ) {
            Preference pref = findPreference("preference_expo_bracketing_stops");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_photo_settings");
            pg.removePreference(pref);
        }

        final boolean is_multi_cam = bundle.getBoolean("is_multi_cam");
        if( MyDebug.LOG )
            Log.d(TAG, "is_multi_cam: " + is_multi_cam);

		/* Set up video resolutions.
		   Note that this will be the resolutions for either standard or high speed frame rate (where
		   the latter may also include being in slow motion mode), depending on the current setting when
		   this settings fragment is launched. A limitation is that if the user changes the fps value
		   within the settings, this list won't update until the user exits and re-enters the settings.
		   This could be fixed by setting a setOnPreferenceChangeListener for the preference_video_fps
		   ListPreference and updating, but we must not assume that the preview will be non-null (since
		   if the application is being recreated, MyPreferenceFragment.onCreate() is called via
		   MainActivity.onCreate()->super.onCreate() before the preview is created! So we still need to
		   read the info via a bundle, and only update when fps changes if the preview is non-null.
		 */
        final String [] video_quality = bundle.getStringArray("video_quality");
        final String [] video_quality_string = bundle.getStringArray("video_quality_string");
        if( video_quality != null && video_quality_string != null ) {
            CharSequence [] entries = new CharSequence[video_quality.length];
            CharSequence [] values = new CharSequence[video_quality.length];
            for(int i=0;i<video_quality.length;i++) {
                entries[i] = video_quality_string[i];
                values[i] = video_quality[i];
            }
            ListPreference lp = (ListPreference)findPreference("preference_video_quality");
            lp.setEntries(entries);
            lp.setEntryValues(values);
            String video_quality_preference_key = bundle.getString("video_quality_preference_key");
            if( MyDebug.LOG )
                Log.d(TAG, "video_quality_preference_key: " + video_quality_preference_key);
            String video_quality_value = sharedPreferences.getString(video_quality_preference_key, "");
            if( MyDebug.LOG )
                Log.d(TAG, "video_quality_value: " + video_quality_value);
            // set the key, so we save for the correct cameraId and high-speed setting
            // this must be done before setting the value (otherwise the video resolutions preference won't be
            // updated correctly when this is called from the callback when the user switches between
            // normal and high speed frame rates
            lp.setKey(video_quality_preference_key);
            lp.setValue(video_quality_value);

            boolean is_high_speed = bundle.getBoolean("video_is_high_speed");
            String title = is_high_speed ? getResources().getString(R.string.video_quality) + " [" + getResources().getString(R.string.high_speed) + "]" : getResources().getString(R.string.video_quality);
            lp.setTitle(title);
            lp.setDialogTitle(title);
        }
        else {
            Preference pref = findPreference("preference_video_quality");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_video_settings");
            pg.removePreference(pref);
        }

        final String current_video_quality = bundle.getString("current_video_quality");
        final int video_frame_width = bundle.getInt("video_frame_width");
        final int video_frame_height = bundle.getInt("video_frame_height");
        final int video_bit_rate = bundle.getInt("video_bit_rate");
        final int video_frame_rate = bundle.getInt("video_frame_rate");
        final double video_capture_rate = bundle.getDouble("video_capture_rate");
        final boolean video_high_speed = bundle.getBoolean("video_high_speed");
        final float video_capture_rate_factor = bundle.getFloat("video_capture_rate_factor");

        final boolean supports_force_video_4k = bundle.getBoolean("supports_force_video_4k");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_force_video_4k: " + supports_force_video_4k);
        if( !supports_force_video_4k || video_quality == null ) {
            Preference pref = findPreference("preference_force_video_4k");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_category_video_debugging");
            pg.removePreference(pref);
        }

        final boolean supports_optical_stabilization = bundle.getBoolean("supports_optical_stabilization");
        final boolean optical_stabilization_enabled = bundle.getBoolean("optical_stabilization_enabled");

        final boolean supports_video_stabilization = bundle.getBoolean("supports_video_stabilization");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_video_stabilization: " + supports_video_stabilization);
        if( !supports_video_stabilization ) {
            Preference pref = findPreference("preference_video_stabilization");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_video_settings");
            pg.removePreference(pref);
        }
        final boolean video_stabilization_enabled = bundle.getBoolean("video_stabilization_enabled");

        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.N ) {
            filterArrayEntry("preference_video_output_format", "preference_video_output_format_mpeg4_hevc");
        }
        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ) {
            filterArrayEntry("preference_video_output_format", "preference_video_output_format_webm");
        }

        {
            ListPreference pref = (ListPreference)findPreference("preference_record_audio_src");

            if( Build.VERSION.SDK_INT < Build.VERSION_CODES.N ) {
                // some values require at least Android 7
                pref.setEntries(R.array.preference_record_audio_src_entries_preandroid7);
                pref.setEntryValues(R.array.preference_record_audio_src_values_preandroid7);
            }
        }

        final boolean can_disable_shutter_sound = bundle.getBoolean("can_disable_shutter_sound");
        if( MyDebug.LOG )
            Log.d(TAG, "can_disable_shutter_sound: " + can_disable_shutter_sound);
        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1 || !can_disable_shutter_sound ) {
            // Camera.enableShutterSound requires JELLY_BEAN_MR1 or greater
            Preference pref = findPreference("preference_shutter_sound");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_camera_controls_more");
            pg.removePreference(pref);
        }

        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT ) {
            // Some immersive modes require KITKAT - simpler to require Kitkat for any of the menu options
            Preference pref = findPreference("preference_immersive_mode");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_gui");
            pg.removePreference(pref);
        }

        if( !using_android_l ) {
            Preference pref = findPreference("preference_focus_assist");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_preview");
            pg.removePreference(pref);
        }

        if( !supports_flash ) {
            Preference pref = findPreference("preference_show_cycle_flash");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_gui");
            pg.removePreference(pref);
        }

        if( !supports_exposure_lock ) {
            Preference pref = findPreference("preference_show_exposure_lock");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_gui");
            pg.removePreference(pref);
        }

        if( !is_multi_cam ) {
            Preference pref = findPreference("preference_show_camera_id");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_preview");
            pg.removePreference(pref);

            pref = findPreference("preference_multi_cam_button");
            pg = (PreferenceGroup)this.findPreference("preference_screen_gui");
            pg.removePreference(pref);
        }

        if( !supports_raw ) {
            Preference pref = findPreference("preference_show_cycle_raw");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_gui");
            pg.removePreference(pref);
        }

        if( !supports_white_balance_lock ) {
            Preference pref = findPreference("preference_show_white_balance_lock");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_gui");
            pg.removePreference(pref);
        }

        if( !supports_auto_stabilise ) {
            Preference pref = findPreference("preference_show_auto_level");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_gui");
            pg.removePreference(pref);
        }

        setSummary("preference_exif_artist");
        setSummary("preference_exif_copyright");

        setSummary("preference_save_photo_prefix");
        setSummary("preference_save_video_prefix");
        setSummary("preference_textstamp");

        if( !using_android_l ) {
            Preference pref = findPreference("preference_show_iso");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_preview");
            pg.removePreference(pref);
        }

        final boolean supports_preview_bitmaps = bundle.getBoolean("supports_preview_bitmaps");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_preview_bitmaps: " + supports_preview_bitmaps);

        if( !supports_preview_bitmaps ) {
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_preview");

            Preference pref = findPreference("preference_histogram");
            pg.removePreference(pref);

            pref = findPreference("preference_zebra_stripes");
            pg.removePreference(pref);

            pref = findPreference("preference_zebra_stripes_foreground_color");
            pg.removePreference(pref);

            pref = findPreference("preference_zebra_stripes_background_color");
            pg.removePreference(pref);

            pref = findPreference("preference_focus_peaking");
            pg.removePreference(pref);

            pref = findPreference("preference_focus_peaking_color");
            pg.removePreference(pref);
        }

        final boolean supports_photo_video_recording = bundle.getBoolean("supports_photo_video_recording");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_photo_video_recording: " + supports_photo_video_recording);

        if( !using_android_l ) {
            Preference pref = findPreference("preference_camera2_fake_flash");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_category_photo_debugging");
            pg.removePreference(pref);

            pref = findPreference("preference_camera2_fast_burst");
            pg = (PreferenceGroup)this.findPreference("preference_category_photo_debugging");
            pg.removePreference(pref);

            pref = findPreference("preference_camera2_photo_video_recording");
            pg = (PreferenceGroup)this.findPreference("preference_category_photo_debugging");
            pg.removePreference(pref);
        }
        else {
            if( !supports_photo_video_recording ) {
                Preference pref = findPreference("preference_camera2_photo_video_recording");
                PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_category_photo_debugging");
                pg.removePreference(pref);
            }
        }

        final int tonemap_max_curve_points = bundle.getInt("tonemap_max_curve_points");
        final boolean supports_tonemap_curve = bundle.getBoolean("supports_tonemap_curve");
        if( MyDebug.LOG ) {
            Log.d(TAG, "tonemap_max_curve_points: " + tonemap_max_curve_points);
            Log.d(TAG, "supports_tonemap_curve: " + supports_tonemap_curve);
        }
        if( !supports_tonemap_curve ) {
            Preference pref = findPreference("preference_video_log");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_video_settings");
            pg.removePreference(pref);

            pref = findPreference("preference_video_profile_gamma");
            pg = (PreferenceGroup)this.findPreference("preference_screen_video_settings");
            pg.removePreference(pref);
        }

        final float camera_view_angle_x = bundle.getFloat("camera_view_angle_x");
        final float camera_view_angle_y = bundle.getFloat("camera_view_angle_y");
        if( MyDebug.LOG ) {
            Log.d(TAG, "camera_view_angle_x: " + camera_view_angle_x);
            Log.d(TAG, "camera_view_angle_y: " + camera_view_angle_y);
        }

        {
            // remove preference_category_photo_debugging category if empty (which will be the case for old api)
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_category_photo_debugging");
            if( MyDebug.LOG )
                Log.d(TAG, "preference_category_photo_debugging children: " + pg.getPreferenceCount());
            if( pg.getPreferenceCount() == 0 ) {
                // pg.getParent() requires API level 26
                PreferenceGroup parent = (PreferenceGroup)this.findPreference("preference_screen_photo_settings");
                parent.removePreference(pg);
            }
        }

        {
            List<String> camera_api_values = new ArrayList<>();
            List<String> camera_api_entries = new ArrayList<>();

            // all devices support old api
            camera_api_values.add("preference_camera_api_old");
            camera_api_entries.add(getActivity().getResources().getString(R.string.preference_camera_api_old));

            final boolean supports_camera2 = bundle.getBoolean("supports_camera2");
            if( MyDebug.LOG )
                Log.d(TAG, "supports_camera2: " + supports_camera2);
            if( supports_camera2 ) {
                camera_api_values.add("preference_camera_api_camera2");
                camera_api_entries.add(getActivity().getResources().getString(R.string.preference_camera_api_camera2));
            }

            if( camera_api_values.size() == 1 ) {
                // if only supports 1 API, no point showing the preference
                camera_api_values.clear();
                camera_api_entries.clear();
            }

            readFromBundle(camera_api_values.toArray(new String[0]), camera_api_entries.toArray(new String[0]), "preference_camera_api", PreferenceKeys.CameraAPIPreferenceDefault, "preference_category_online");

            if( camera_api_values.size() >= 2 ) {
                final Preference pref = findPreference("preference_camera_api");
                pref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                    @Override
                    public boolean onPreferenceChange(Preference arg0, Object newValue) {
                        if( pref.getKey().equals("preference_camera_api") ) {
                            ListPreference list_pref = (ListPreference)pref;
                            if( list_pref.getValue().equals(newValue) ) {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "user selected same camera API");
                            }
                            else {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "user changed camera API - need to restart");
                                MainActivity main_activity = (MainActivity)MyPreferenceFragment.this.getActivity();
                                main_activity.restartOpenCamera();
                            }
                        }
                        return true;
                    }
                });
            }
        }
        /*final boolean supports_camera2 = bundle.getBoolean("supports_camera2");
        if( MyDebug.LOG )
            Log.d(TAG, "supports_camera2: " + supports_camera2);
        if( supports_camera2 ) {
            final Preference pref = findPreference("preference_use_camera2");
            pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    if( pref.getKey().equals("preference_use_camera2") ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "user clicked camera2 API - need to restart");
                        MainActivity main_activity = (MainActivity)MyPreferenceFragment.this.getActivity();
                        main_activity.restartOpenCamera();
                        return false;
                    }
                    return false;
                }
            });
        }
        else {
            Preference pref = findPreference("preference_use_camera2");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_category_online");
            pg.removePreference(pref);
        }*/

        {
            final Preference pref = findPreference("preference_online_help");
            pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    if( pref.getKey().equals("preference_online_help") ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "user clicked online help");
                        MainActivity main_activity = (MainActivity)MyPreferenceFragment.this.getActivity();
                        main_activity.launchOnlineHelp();
                        return false;
                    }
                    return false;
                }
            });
        }

        {
            final Preference pref = findPreference("preference_privacy_policy");
            pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    if( pref.getKey().equals("preference_privacy_policy") ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "user clicked privacy policy");

                        clickedPrivacyPolicy();
                    }
                    return false;
                }
            });
        }

        // licences

        {
            final Preference pref = findPreference("preference_licence_open_camera");
            pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    if( pref.getKey().equals("preference_licence_open_camera") ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "user clicked open camera licence");
                        // display the GPL v3 text
                        displayTextDialog(R.string.preference_licence_open_camera, "gpl-3.0.txt");
                        return false;
                    }
                    return false;
                }
            });
        }

        {
            final Preference pref = findPreference("preference_licence_androidx");
            pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    if( pref.getKey().equals("preference_licence_androidx") ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "user clicked androidx licence");
                        // display the Apache licence 2.0 text
                        displayTextDialog(R.string.preference_licence_androidx, "androidx_LICENSE-2.0.txt");
                        return false;
                    }
                    return false;
                }
            });
        }

        {
            final Preference pref = findPreference("preference_licence_google_icons");
            pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    if( pref.getKey().equals("preference_licence_google_icons") ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "user clicked google material design icons licence");
                        // display the Apache licence 2.0 text
                        displayTextDialog(R.string.preference_licence_google_icons, "google_material_design_icons_LICENSE-2.0.txt");
                        return false;
                    }
                    return false;
                }
            });
        }

        {
            final Preference pref = findPreference("preference_licence_online");
            pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    if( pref.getKey().equals("preference_licence_online") ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "user clicked online licences");
                        MainActivity main_activity = (MainActivity)MyPreferenceFragment.this.getActivity();
                        main_activity.launchOnlineLicences();
                        return false;
                    }
                    return false;
                }
            });
        }

        // end licences

        {
            ListPreference pref = (ListPreference)findPreference("preference_ghost_image");

            if( Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ) {
                // require Storage Access Framework to select a ghost image
                pref.setEntries(R.array.preference_ghost_image_entries_preandroid5);
                pref.setEntryValues(R.array.preference_ghost_image_values_preandroid5);
            }

            pref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference arg0, Object newValue) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "clicked ghost image: " + newValue);
                    if( newValue.equals("preference_ghost_image_selected") ) {
                        MainActivity main_activity = (MainActivity) MyPreferenceFragment.this.getActivity();
                        main_activity.openGhostImageChooserDialogSAF(true);
                    }
                    return true;
                }
            });
        }

        /*{
        	EditTextPreference edit = (EditTextPreference)findPreference("preference_save_location");
        	InputFilter filter = new InputFilter() { 
        		// whilst Android seems to allow any characters on internal memory, SD cards are typically formatted with FAT32
        		String disallowed = "|\\?*<\":>";
                public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart, int dend) { 
                    for(int i=start;i<end;i++) { 
                    	if( disallowed.indexOf( source.charAt(i) ) != -1 ) {
                            return ""; 
                    	}
                    } 
                    return null; 
                }
        	}; 
        	edit.getEditText().setFilters(new InputFilter[]{filter});         	
        }*/
        {
            Preference pref = findPreference("preference_save_location");
            pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "clicked save location");
                    MainActivity main_activity = (MainActivity)MyPreferenceFragment.this.getActivity();
                    if( main_activity.getStorageUtils().isUsingSAF() ) {
                        main_activity.openFolderChooserDialogSAF(true);
                        return true;
                    }
                    else if( MainActivity.useScopedStorage() ) {
                        // we can't use an EditTextPreference (or MyEditTextPreference) due to having to support non-scoped-storage, or when SAF is enabled...
                        // anyhow, this means we can share code when called from gallery long-press anyway
                        AlertDialog.Builder alertDialog = main_activity.createSaveFolderDialog();
                        final AlertDialog alert = alertDialog.create();
                        // AlertDialog.Builder.setOnDismissListener() requires API level 17, so do it this way instead
                        alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface arg0) {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "save folder dialog dismissed");
                                dialogs.remove(alert);
                            }
                        });
                        alert.show();
                        dialogs.add(alert);
                        return true;
                    }
                    else {
                        File start_folder = main_activity.getStorageUtils().getImageFolder();

                        FolderChooserDialog fragment = new SaveFolderChooserDialog();
                        fragment.setStartFolder(start_folder);
                        fragment.show(getFragmentManager(), "FOLDER_FRAGMENT");
                        return true;
                    }
                }
            });
        }

        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP ) {
            Preference pref = findPreference("preference_using_saf");
            PreferenceGroup pg = (PreferenceGroup)this.findPreference("preference_screen_camera_controls_more");
            pg.removePreference(pref);
        }
        else {
            final Preference pref = findPreference("preference_using_saf");
            pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    if( pref.getKey().equals("preference_using_saf") ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "user clicked saf");
                        if( sharedPreferences.getBoolean(PreferenceKeys.UsingSAFPreferenceKey, false) ) {
                            if( MyDebug.LOG )
                                Log.d(TAG, "saf is now enabled");
                            // seems better to alway re-show the dialog when the user selects, to make it clear where files will be saved (as the SAF location in general will be different to the non-SAF one)
                            //String uri = sharedPreferences.getString(PreferenceKeys.getSaveLocationSAFPreferenceKey(), "");
                            //if( uri.length() == 0 )
                            {
                                MainActivity main_activity = (MainActivity)MyPreferenceFragment.this.getActivity();
                                Toast.makeText(main_activity, R.string.saf_select_save_location, Toast.LENGTH_SHORT).show();
                                main_activity.openFolderChooserDialogSAF(true);
                            }
                        }
                        else {
                            if( MyDebug.LOG )
                                Log.d(TAG, "saf is now disabled");
                        }
                    }
                    return false;
                }
            });
        }

        {
            final Preference pref = findPreference("preference_calibrate_level");
            pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    if( pref.getKey().equals("preference_calibrate_level") ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "user clicked calibrate level option");
                        AlertDialog.Builder alertDialog = new AlertDialog.Builder(MyPreferenceFragment.this.getActivity());
                        alertDialog.setTitle(getActivity().getResources().getString(R.string.preference_calibrate_level));
                        alertDialog.setMessage(R.string.preference_calibrate_level_dialog);
                        alertDialog.setPositiveButton(R.string.preference_calibrate_level_calibrate, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "user clicked calibrate level");
                                MainActivity main_activity = (MainActivity)MyPreferenceFragment.this.getActivity();
                                if( main_activity.getPreview().hasLevelAngleStable() ) {
                                    double current_level_angle = main_activity.getPreview().getLevelAngleUncalibrated();
                                    SharedPreferences.Editor editor = sharedPreferences.edit();
                                    editor.putFloat(PreferenceKeys.CalibratedLevelAnglePreferenceKey, (float)current_level_angle);
                                    editor.apply();
                                    main_activity.getPreview().updateLevelAngles();
                                    Toast.makeText(main_activity, R.string.preference_calibrate_level_calibrated, Toast.LENGTH_SHORT).show();
                                }
                            }
                        });
                        alertDialog.setNegativeButton(R.string.preference_calibrate_level_reset, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "user clicked reset calibration level");
                                MainActivity main_activity = (MainActivity)MyPreferenceFragment.this.getActivity();
                                SharedPreferences.Editor editor = sharedPreferences.edit();
                                editor.putFloat(PreferenceKeys.CalibratedLevelAnglePreferenceKey, 0.0f);
                                editor.apply();
                                main_activity.getPreview().updateLevelAngles();
                                Toast.makeText(main_activity, R.string.preference_calibrate_level_calibration_reset, Toast.LENGTH_SHORT).show();
                            }
                        });
                        final AlertDialog alert = alertDialog.create();
                        // AlertDialog.Builder.setOnDismissListener() requires API level 17, so do it this way instead
                        alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface arg0) {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "calibration dialog dismissed");
                                dialogs.remove(alert);
                            }
                        });
                        alert.show();
                        dialogs.add(alert);
                        return false;
                    }
                    return false;
                }
            });
        }

        /*{
            final Preference pref = findPreference("preference_donate");
            pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    if( pref.getKey().equals("preference_donate") ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "user clicked to donate");
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(MainActivity.DonateLink));
                        startActivity(browserIntent);
                        return false;
                    }
                    return false;
                }
            });
        }*/

        {
            final Preference pref = findPreference("preference_about");
            pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    if( pref.getKey().equals("preference_about") ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "user clicked about");
                        AlertDialog.Builder alertDialog = new AlertDialog.Builder(MyPreferenceFragment.this.getActivity());
                        alertDialog.setTitle(R.string.preference_about);
                        final StringBuilder about_string = new StringBuilder();
                        String version = "UNKNOWN_VERSION";
                        int version_code = -1;
                        try {
                            PackageInfo pInfo = MyPreferenceFragment.this.getActivity().getPackageManager().getPackageInfo(MyPreferenceFragment.this.getActivity().getPackageName(), 0);
                            version = pInfo.versionName;
                            version_code = pInfo.versionCode;
                        }
                        catch(NameNotFoundException e) {
                            if( MyDebug.LOG )
                                Log.d(TAG, "NameNotFoundException exception trying to get version number");
                            e.printStackTrace();
                        }
                        about_string.append("Open Camera v");
                        about_string.append(version);
                        about_string.append("\nCode: ");
                        about_string.append(version_code);
                        about_string.append("\nPackage: ");
                        about_string.append(MyPreferenceFragment.this.getActivity().getPackageName());
                        about_string.append("\nAndroid API version: ");
                        about_string.append(Build.VERSION.SDK_INT);
                        about_string.append("\nDevice manufacturer: ");
                        about_string.append(Build.MANUFACTURER);
                        about_string.append("\nDevice model: ");
                        about_string.append(Build.MODEL);
                        about_string.append("\nDevice code-name: ");
                        about_string.append(Build.HARDWARE);
                        about_string.append("\nDevice variant: ");
                        about_string.append(Build.DEVICE);
                        about_string.append("\nLanguage: ");
                        about_string.append(Locale.getDefault().getLanguage());
                        {
                            ActivityManager activityManager = (ActivityManager) getActivity().getSystemService(Activity.ACTIVITY_SERVICE);
                            about_string.append("\nStandard max heap?: ");
                            about_string.append(activityManager.getMemoryClass());
                            about_string.append("\nLarge max heap?: ");
                            about_string.append(activityManager.getLargeMemoryClass());
                        }
                        {
                            Point display_size = new Point();
                            Display display = MyPreferenceFragment.this.getActivity().getWindowManager().getDefaultDisplay();
                            display.getSize(display_size);
                            about_string.append("\nDisplay size: ");
                            about_string.append(display_size.x);
                            about_string.append("x");
                            about_string.append(display_size.y);
                            DisplayMetrics outMetrics = new DisplayMetrics();
                            display.getMetrics(outMetrics);
                            about_string.append("\nDisplay metrics: ");
                            about_string.append(outMetrics.widthPixels);
                            about_string.append("x");
                            about_string.append(outMetrics.heightPixels);
                        }
                        about_string.append("\nCurrent camera ID: ");
                        about_string.append(cameraId);
                        about_string.append("\nNo. of cameras: ");
                        about_string.append(nCameras);
                        about_string.append("\nMulti-camera?: ");
                        about_string.append(is_multi_cam);
                        about_string.append("\nCamera API: ");
                        about_string.append(camera_api);
                        about_string.append("\nCamera orientation: ");
                        about_string.append(camera_orientation);
                        about_string.append("\nPhoto mode: ");
                        about_string.append(photo_mode_string==null ? "UNKNOWN" : photo_mode_string);
                        {
                            String last_video_error = sharedPreferences.getString("last_video_error", "");
                            if( last_video_error.length() > 0 ) {
                                about_string.append("\nLast video error: ");
                                about_string.append(last_video_error);
                            }
                        }
                        if( preview_widths != null && preview_heights != null ) {
                            about_string.append("\nPreview resolutions: ");
                            for(int i=0;i<preview_widths.length;i++) {
                                if( i > 0 ) {
                                    about_string.append(", ");
                                }
                                about_string.append(preview_widths[i]);
                                about_string.append("x");
                                about_string.append(preview_heights[i]);
                            }
                        }
                        about_string.append("\nPreview resolution: ");
                        about_string.append(preview_width);
                        about_string.append("x");
                        about_string.append(preview_height);
                        if( widths != null && heights != null ) {
                            about_string.append("\nPhoto resolutions: ");
                            for(int i=0;i<widths.length;i++) {
                                if( i > 0 ) {
                                    about_string.append(", ");
                                }
                                about_string.append(widths[i]);
                                about_string.append("x");
                                about_string.append(heights[i]);
                                if( supports_burst != null && !supports_burst[i] ) {
                                    about_string.append("[no burst]");
                                }
                            }
                        }
                        about_string.append("\nPhoto resolution: ");
                        about_string.append(resolution_width);
                        about_string.append("x");
                        about_string.append(resolution_height);
                        if( video_quality != null ) {
                            about_string.append("\nVideo qualities: ");
                            for(int i=0;i<video_quality.length;i++) {
                                if( i > 0 ) {
                                    about_string.append(", ");
                                }
                                about_string.append(video_quality[i]);
                            }
                        }
                        if( video_widths != null && video_heights != null ) {
                            about_string.append("\nVideo resolutions: ");
                            for(int i=0;i<video_widths.length;i++) {
                                if( i > 0 ) {
                                    about_string.append(", ");
                                }
                                about_string.append(video_widths[i]);
                                about_string.append("x");
                                about_string.append(video_heights[i]);
                            }
                        }
                        about_string.append("\nVideo quality: ");
                        about_string.append(current_video_quality);
                        about_string.append("\nVideo frame width: ");
                        about_string.append(video_frame_width);
                        about_string.append("\nVideo frame height: ");
                        about_string.append(video_frame_height);
                        about_string.append("\nVideo bit rate: ");
                        about_string.append(video_bit_rate);
                        about_string.append("\nVideo frame rate: ");
                        about_string.append(video_frame_rate);
                        about_string.append("\nVideo capture rate: ");
                        about_string.append(video_capture_rate);
                        about_string.append("\nVideo high speed: ");
                        about_string.append(video_high_speed);
                        about_string.append("\nVideo capture rate factor: ");
                        about_string.append(video_capture_rate_factor);
                        about_string.append("\nAuto-level?: ");
                        about_string.append(getString(supports_auto_stabilise ? R.string.about_available : R.string.about_not_available));
                        about_string.append("\nAuto-level enabled?: ");
                        about_string.append(sharedPreferences.getBoolean(PreferenceKeys.AutoStabilisePreferenceKey, false));
                        about_string.append("\nFace detection?: ");
                        about_string.append(getString(supports_face_detection ? R.string.about_available : R.string.about_not_available));
                        about_string.append("\nRAW?: ");
                        about_string.append(getString(supports_raw ? R.string.about_available : R.string.about_not_available));
                        about_string.append("\nHDR?: ");
                        about_string.append(getString(supports_hdr ? R.string.about_available : R.string.about_not_available));
                        about_string.append("\nPanorama?: ");
                        about_string.append(getString(supports_panorama ? R.string.about_available : R.string.about_not_available));
                        about_string.append("\nGyro sensors?: ");
                        about_string.append(getString(has_gyro_sensors ? R.string.about_available : R.string.about_not_available));
                        about_string.append("\nExpo?: ");
                        about_string.append(getString(supports_expo_bracketing ? R.string.about_available : R.string.about_not_available));
                        about_string.append("\nExpo compensation?: ");
                        about_string.append(getString(supports_exposure_compensation ? R.string.about_available : R.string.about_not_available));
                        if( supports_exposure_compensation ) {
                            about_string.append("\nExposure compensation range: ");
                            about_string.append(exposure_compensation_min);
                            about_string.append(" to ");
                            about_string.append(exposure_compensation_max);
                        }
                        about_string.append("\nManual ISO?: ");
                        about_string.append(getString(supports_iso_range ? R.string.about_available : R.string.about_not_available));
                        if( supports_iso_range ) {
                            about_string.append("\nISO range: ");
                            about_string.append(iso_range_min);
                            about_string.append(" to ");
                            about_string.append(iso_range_max);
                        }
                        about_string.append("\nManual exposure?: ");
                        about_string.append(getString(supports_exposure_time ? R.string.about_available : R.string.about_not_available));
                        if( supports_exposure_time ) {
                            about_string.append("\nExposure range: ");
                            about_string.append(exposure_time_min);
                            about_string.append(" to ");
                            about_string.append(exposure_time_max);
                        }
                        about_string.append("\nManual WB?: ");
                        about_string.append(getString(supports_white_balance_temperature ? R.string.about_available : R.string.about_not_available));
                        if( supports_white_balance_temperature ) {
                            about_string.append("\nWB temperature: ");
                            about_string.append(white_balance_temperature_min);
                            about_string.append(" to ");
                            about_string.append(white_balance_temperature_max);
                        }
                        about_string.append("\nOptical stabilization?: ");
                        about_string.append(getString(supports_optical_stabilization ? R.string.about_available : R.string.about_not_available));
                        about_string.append("\nOptical stabilization enabled?: ");
                        about_string.append(optical_stabilization_enabled);
                        about_string.append("\nVideo stabilization?: ");
                        about_string.append(getString(supports_video_stabilization ? R.string.about_available : R.string.about_not_available));
                        about_string.append("\nVideo stabilization enabled?: ");
                        about_string.append(video_stabilization_enabled);
                        about_string.append("\nTonemap curve?: ");
                        about_string.append(getString(supports_tonemap_curve ? R.string.about_available : R.string.about_not_available));
                        about_string.append("\nTonemap max curve points: ");
                        about_string.append(tonemap_max_curve_points);
                        about_string.append("\nCan disable shutter sound?: ");
                        about_string.append(getString(can_disable_shutter_sound ? R.string.about_available : R.string.about_not_available));

                        about_string.append("\nCamera view angle: ").append(camera_view_angle_x).append(" , ").append(camera_view_angle_y);

                        about_string.append("\nFlash modes: ");
                        String [] flash_values = bundle.getStringArray("flash_values");
                        if( flash_values != null && flash_values.length > 0 ) {
                            for(int i=0;i<flash_values.length;i++) {
                                if( i > 0 ) {
                                    about_string.append(", ");
                                }
                                about_string.append(flash_values[i]);
                            }
                        }
                        else {
                            about_string.append("None");
                        }
                        about_string.append("\nFocus modes: ");
                        String [] focus_values = bundle.getStringArray("focus_values");
                        if( focus_values != null && focus_values.length > 0 ) {
                            for(int i=0;i<focus_values.length;i++) {
                                if( i > 0 ) {
                                    about_string.append(", ");
                                }
                                about_string.append(focus_values[i]);
                            }
                        }
                        else {
                            about_string.append("None");
                        }
                        about_string.append("\nColor effects: ");
                        String [] color_effects_values = bundle.getStringArray("color_effects");
                        if( color_effects_values != null && color_effects_values.length > 0 ) {
                            for(int i=0;i<color_effects_values.length;i++) {
                                if( i > 0 ) {
                                    about_string.append(", ");
                                }
                                about_string.append(color_effects_values[i]);
                            }
                        }
                        else {
                            about_string.append("None");
                        }
                        about_string.append("\nScene modes: ");
                        String [] scene_modes_values = bundle.getStringArray("scene_modes");
                        if( scene_modes_values != null && scene_modes_values.length > 0 ) {
                            for(int i=0;i<scene_modes_values.length;i++) {
                                if( i > 0 ) {
                                    about_string.append(", ");
                                }
                                about_string.append(scene_modes_values[i]);
                            }
                        }
                        else {
                            about_string.append("None");
                        }
                        about_string.append("\nWhite balances: ");
                        String [] white_balances_values = bundle.getStringArray("white_balances");
                        if( white_balances_values != null && white_balances_values.length > 0 ) {
                            for(int i=0;i<white_balances_values.length;i++) {
                                if( i > 0 ) {
                                    about_string.append(", ");
                                }
                                about_string.append(white_balances_values[i]);
                            }
                        }
                        else {
                            about_string.append("None");
                        }
                        if( !using_android_l ) {
                            about_string.append("\nISOs: ");
                            String[] isos = bundle.getStringArray("isos");
                            if (isos != null && isos.length > 0) {
                                for (int i = 0; i < isos.length; i++) {
                                    if (i > 0) {
                                        about_string.append(", ");
                                    }
                                    about_string.append(isos[i]);
                                }
                            } else {
                                about_string.append("None");
                            }
                            String iso_key = bundle.getString("iso_key");
                            if (iso_key != null) {
                                about_string.append("\nISO key: ");
                                about_string.append(iso_key);
                            }
                        }

                        int magnetic_accuracy = bundle.getInt("magnetic_accuracy");
                        about_string.append("\nMagnetic accuracy?: ");
                        about_string.append(magnetic_accuracy);

                        about_string.append("\nUsing SAF?: ");
                        about_string.append(sharedPreferences.getBoolean(PreferenceKeys.UsingSAFPreferenceKey, false));
                        String save_location = sharedPreferences.getString(PreferenceKeys.SaveLocationPreferenceKey, "OpenCamera");
                        about_string.append("\nSave Location: ");
                        about_string.append(save_location);
                        String save_location_saf = sharedPreferences.getString(PreferenceKeys.SaveLocationSAFPreferenceKey, "");
                        about_string.append("\nSave Location SAF: ");
                        about_string.append(save_location_saf);

                        about_string.append("\nParameters: ");
                        String parameters_string = bundle.getString("parameters_string");
                        if( parameters_string != null ) {
                            about_string.append(parameters_string);
                        }
                        else {
                            about_string.append("None");
                        }

                        SpannableString span = new SpannableString(about_string);

                        // clickable text is only supported if we call setMovementMethod on the TextView - which means we need to create
                        // our own for the AlertDialog!
                        @SuppressLint("InflateParams") // we add the view to the alert dialog in addTextViewForAlertDialog()
                        final View dialog_view = LayoutInflater.from(getActivity()).inflate(R.layout.alertdialog_textview, null);
                        final TextView textView = dialog_view.findViewById(R.id.text_view);

                        textView.setText(span);
                        textView.setMovementMethod(LinkMovementMethod.getInstance());
                        textView.setTextAppearance(getActivity(), android.R.style.TextAppearance_Medium);
                        addTextViewForAlertDialog(alertDialog, textView);
                        //alertDialog.setMessage(about_string);

                        alertDialog.setPositiveButton(android.R.string.ok, null);
                        alertDialog.setNegativeButton(R.string.about_copy_to_clipboard, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "user clicked copy to clipboard");
                                ClipboardManager clipboard = (ClipboardManager) getActivity().getSystemService(Activity.CLIPBOARD_SERVICE);
                                ClipData clip = ClipData.newPlainText("OpenCamera About", about_string);
                                clipboard.setPrimaryClip(clip);
                            }
                        });
                        final AlertDialog alert = alertDialog.create();
                        // AlertDialog.Builder.setOnDismissListener() requires API level 17, so do it this way instead
                        alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface arg0) {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "about dialog dismissed");
                                dialogs.remove(alert);
                            }
                        });
                        alert.show();
                        dialogs.add(alert);
                        return false;
                    }
                    return false;
                }
            });
        }

        {
            final Preference pref = findPreference("preference_restore_settings");
            pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    if( pref.getKey().equals("preference_restore_settings") ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "user clicked restore settings");

                        loadSettings();
                    }
                    return false;
                }
            });
        }
        {
            final Preference pref = findPreference("preference_save_settings");
            pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    if( pref.getKey().equals("preference_save_settings") ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "user clicked save settings");

                        AlertDialog.Builder alertDialog = new AlertDialog.Builder(MyPreferenceFragment.this.getActivity());
                        alertDialog.setTitle(R.string.preference_save_settings_filename);

                        final View dialog_view = LayoutInflater.from(getActivity()).inflate(R.layout.alertdialog_edittext, null);
                        final EditText editText = dialog_view.findViewById(R.id.edit_text);

                        editText.setSingleLine();
                        // set hint instead of content description for EditText, see https://support.google.com/accessibility/android/answer/6378120
                        editText.setHint(getResources().getString(R.string.preference_save_settings_filename));

                        alertDialog.setView(dialog_view);

                        final MainActivity main_activity = (MainActivity)MyPreferenceFragment.this.getActivity();
                        try {
                            // find a default name - although we're only interested in the name rather than full path, this still
                            // requires checking the folder, so that we don't reuse an existing filename
                            String mediaFilename = main_activity.getStorageUtils().createOutputMediaFile(
                                    main_activity.getStorageUtils().getSettingsFolder(),
                                    StorageUtils.MEDIA_TYPE_PREFS, "", "xml", new Date()
                            ).getName();
                            if( MyDebug.LOG )
                                Log.d(TAG, "mediaFilename: " + mediaFilename);
                            int index = mediaFilename.lastIndexOf('.');
                            if( index != -1 ) {
                                // remove extension
                                mediaFilename = mediaFilename.substring(0, index);
                            }
                            editText.setText(mediaFilename);
                            editText.setSelection(mediaFilename.length());
                        }
                        catch(IOException e) {
                            Log.e(TAG, "failed to obtain a filename");
                            e.printStackTrace();
                        }

                        alertDialog.setPositiveButton(android.R.string.ok, new OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "save settings clicked okay");

                                String filename = editText.getText().toString() + ".xml";
                                main_activity.getSettingsManager().saveSettings(filename);
                            }
                        });
                        alertDialog.setNegativeButton(android.R.string.cancel, null);
                        final AlertDialog alert = alertDialog.create();
                        // AlertDialog.Builder.setOnDismissListener() requires API level 17, so do it this way instead
                        alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface arg0) {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "save settings dialog dismissed");
                                dialogs.remove(alert);
                            }
                        });
                        alert.show();
                        dialogs.add(alert);
                        //MainActivity main_activity = (MainActivity)MyPreferenceFragment.this.getActivity();
                        //main_activity.getSettingsManager().saveSettings();
                    }
                    return false;
                }
            });
        }
        {
            final Preference pref = findPreference("preference_reset");
            pref.setOnPreferenceClickListener(new OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference arg0) {
                    if( pref.getKey().equals("preference_reset") ) {
                        if( MyDebug.LOG )
                            Log.d(TAG, "user clicked reset settings");
                        AlertDialog.Builder alertDialog = new AlertDialog.Builder(MyPreferenceFragment.this.getActivity());
                        alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
                        alertDialog.setTitle(R.string.preference_reset);
                        alertDialog.setMessage(R.string.preference_reset_question);
                        alertDialog.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "user confirmed reset");
                                SharedPreferences.Editor editor = sharedPreferences.edit();
                                editor.clear();
                                editor.putBoolean(PreferenceKeys.FirstTimePreferenceKey, true);
                                try {
                                    PackageInfo pInfo = MyPreferenceFragment.this.getActivity().getPackageManager().getPackageInfo(MyPreferenceFragment.this.getActivity().getPackageName(), 0);
                                    int version_code = pInfo.versionCode;
                                    editor.putInt(PreferenceKeys.LatestVersionPreferenceKey, version_code);
                                }
                                catch(NameNotFoundException e) {
                                    if (MyDebug.LOG)
                                        Log.d(TAG, "NameNotFoundException exception trying to get version number");
                                    e.printStackTrace();
                                }
                                editor.apply();
                                MainActivity main_activity = (MainActivity)MyPreferenceFragment.this.getActivity();
                                main_activity.setDeviceDefaults();
                                if( MyDebug.LOG )
                                    Log.d(TAG, "user clicked reset - need to restart");
                                main_activity.restartOpenCamera();
                            }
                        });
                        alertDialog.setNegativeButton(android.R.string.no, null);
                        final AlertDialog alert = alertDialog.create();
                        // AlertDialog.Builder.setOnDismissListener() requires API level 17, so do it this way instead
                        alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
                            @Override
                            public void onDismiss(DialogInterface arg0) {
                                if( MyDebug.LOG )
                                    Log.d(TAG, "reset dialog dismissed");
                                dialogs.remove(alert);
                            }
                        });
                        alert.show();
                        dialogs.add(alert);
                    }
                    return false;
                }
            });
        }

        setupDependencies();
    }

    /** Adds a TextView to an AlertDialog builder, placing it inside a scrollview and adding appropriate padding.
     */
    private void addTextViewForAlertDialog(AlertDialog.Builder alertDialog, TextView textView) {
        final float scale = getActivity().getResources().getDisplayMetrics().density;
        ScrollView scrollView = new ScrollView(getActivity());
        scrollView.addView(textView);
        // padding values from /sdk/platforms/android-18/data/res/layout/alert_dialog.xml
        textView.setPadding((int)(5*scale+0.5f), (int)(5*scale+0.5f), (int)(5*scale+0.5f), (int)(5*scale+0.5f));
        scrollView.setPadding((int)(14*scale+0.5f), (int)(2*scale+0.5f), (int)(10*scale+0.5f), (int)(12*scale+0.5f));
        alertDialog.setView(scrollView);
    }

    /** Programmatically set up dependencies for preference types (e.g., ListPreference) that don't
     *  support this in xml (such as SwitchPreference and CheckBoxPreference), or where this depends
     *  on the device (e.g., Android version).
     */
    private void setupDependencies() {
        // set up dependency for preference_audio_noise_control_sensitivity on preference_audio_control
        ListPreference pref = (ListPreference)findPreference("preference_audio_control");
        if( pref != null ) { // may be null if preference not supported
            pref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference arg0, Object newValue) {
                    String value = newValue.toString();
                    setAudioNoiseControlSensitivityDependency(value);
                    return true;
                }
            });
            setAudioNoiseControlSensitivityDependency(pref.getValue()); // ensure dependency is enabled/disabled as required for initial value
        }

        // set up dependency for preference_video_profile_gamma on preference_video_log
        pref = (ListPreference)findPreference("preference_video_log");
        if( pref != null ) { // may be null if preference not supported
            pref.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference arg0, Object newValue) {
                    String value = newValue.toString();
                    setVideoProfileGammaDependency(value);
                    return true;
                }
            });
            setVideoProfileGammaDependency(pref.getValue()); // ensure dependency is enabled/disabled as required for initial value
        }

        if( !MyApplicationInterface.mediastoreSupportsVideoSubtitles() ) {
            // video subtitles only supported with SAF on Android 11+
            pref = (ListPreference)findPreference("preference_video_subtitle");
            if( pref != null ) {
                pref.setDependency("preference_using_saf");
            }
        }
    }

    private void setAudioNoiseControlSensitivityDependency(String newValue) {
        Preference dependent = findPreference("preference_audio_noise_control_sensitivity");
        if( dependent != null ) { // just in case
            boolean enable_dependent = "noise".equals(newValue);
            if( MyDebug.LOG )
                Log.d(TAG, "clicked audio control: " + newValue + " enable_dependent: " + enable_dependent);
            dependent.setEnabled(enable_dependent);
        }
    }

    private void setVideoProfileGammaDependency(String newValue) {
        Preference dependent = findPreference("preference_video_profile_gamma");
        if( dependent != null ) { // just in case
            boolean enable_dependent = "gamma".equals(newValue);
            if( MyDebug.LOG )
                Log.d(TAG, "clicked video log: " + newValue + " enable_dependent: " + enable_dependent);
            dependent.setEnabled(enable_dependent);
        }
    }

    /* The user clicked the privacy policy preference.
     */
    public void clickedPrivacyPolicy() {
        if( MyDebug.LOG )
            Log.d(TAG, "clickedPrivacyPolicy()");
        /*MainActivity main_activity = (MainActivity)MyPreferenceFragment.this.getActivity();
        main_activity.launchOnlinePrivacyPolicy();*/

        AlertDialog.Builder alertDialog = new AlertDialog.Builder(MyPreferenceFragment.this.getActivity());
        alertDialog.setTitle(R.string.preference_privacy_policy);

        //SpannableString span = new SpannableString(getActivity().getResources().getString(R.string.preference_privacy_policy_text));
        //Linkify.addLinks(span, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES);
        Spanned span = Html.fromHtml(getActivity().getResources().getString(R.string.preference_privacy_policy_text));
        // clickable text is only supported if we call setMovementMethod on the TextView - which means we need to create
        // our own for the AlertDialog!
        @SuppressLint("InflateParams") // we add the view to the alert dialog in addTextViewForAlertDialog()
        final View dialog_view = LayoutInflater.from(getActivity()).inflate(R.layout.alertdialog_textview, null);
        final TextView textView = dialog_view.findViewById(R.id.text_view);
        textView.setText(span);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        textView.setTextAppearance(getActivity(), android.R.style.TextAppearance_Medium);
        addTextViewForAlertDialog(alertDialog, textView);
        //alertDialog.setMessage(R.string.preference_privacy_policy_text);

        alertDialog.setPositiveButton(android.R.string.ok, null);
        alertDialog.setNegativeButton(R.string.preference_privacy_policy_online, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if( MyDebug.LOG )
                    Log.d(TAG, "online privacy policy");
                MainActivity main_activity = (MainActivity)MyPreferenceFragment.this.getActivity();
                main_activity.launchOnlinePrivacyPolicy();
            }
        });
        final AlertDialog alert = alertDialog.create();
        // AlertDialog.Builder.setOnDismissListener() requires API level 17, so do it this way instead
        alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface arg0) {
                if( MyDebug.LOG )
                    Log.d(TAG, "reset dialog dismissed");
                dialogs.remove(alert);
            }
        });
        alert.show();
        dialogs.add(alert);
    }

    /* Displays a dialog with text loaded from a file in assets.
     */
    private void displayTextDialog(int title_id, String file) {
        try {
            InputStream inputStream = getActivity().getAssets().open(file);
            Scanner scanner = new Scanner(inputStream).useDelimiter("\\A");
            AlertDialog.Builder alertDialog = new AlertDialog.Builder(MyPreferenceFragment.this.getActivity());
            alertDialog.setTitle(getActivity().getResources().getString(title_id));
            alertDialog.setMessage(scanner.next());
            alertDialog.setPositiveButton(android.R.string.ok, null);
            final AlertDialog alert = alertDialog.create();
            // AlertDialog.Builder.setOnDismissListener() requires API level 17, so do it this way instead
            alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface arg0) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "text dialog dismissed");
                    dialogs.remove(alert);
                }
            });
            alert.show();
            dialogs.add(alert);
        }
        catch(IOException e) {
            e.printStackTrace();
        }
    }

    /** Removes an entry and value pair from a ListPreference, if it exists.
     * @param preference_key Key of the ListPreference to remove the supplied entry/value.
     * @param filter_value The value to remove from the list.
     */
    private void filterArrayEntry(String preference_key, String filter_value) {
        {
            ListPreference pref = (ListPreference)findPreference(preference_key);
            CharSequence [] orig_entries = pref.getEntries();
            CharSequence [] orig_values = pref.getEntryValues();
            List<CharSequence> new_entries = new ArrayList<>();
            List<CharSequence> new_values = new ArrayList<>();
            for(int i=0;i<orig_entries.length;i++) {
                CharSequence value = orig_values[i];
                if( !value.equals(filter_value) ) {
                    new_entries.add(orig_entries[i]);
                    new_values.add(value);
                }
            }
            CharSequence [] new_entries_arr = new CharSequence[new_entries.size()];
            new_entries.toArray(new_entries_arr);
            CharSequence [] new_values_arr = new CharSequence[new_values.size()];
            new_values.toArray(new_values_arr);
            pref.setEntries(new_entries_arr);
            pref.setEntryValues(new_values_arr);
        }
    }

    public static class SaveFolderChooserDialog extends FolderChooserDialog {
        @Override
        public void onDismiss(DialogInterface dialog) {
            if( MyDebug.LOG )
                Log.d(TAG, "FolderChooserDialog dismissed");
            // n.b., fragments have to be static (as they might be inserted into a new Activity - see http://stackoverflow.com/questions/15571010/fragment-inner-class-should-be-static),
            // so we access the MainActivity via the fragment's getActivity().
            MainActivity main_activity = (MainActivity)this.getActivity();
            if( main_activity != null ) { // main_activity may be null if this is being closed via MainActivity.onNewIntent()
                String new_save_location = this.getChosenFolder();
                main_activity.updateSaveFolder(new_save_location);
            }
            super.onDismiss(dialog);
        }
    }

    public static class LoadSettingsFileChooserDialog extends FolderChooserDialog {
        @Override
        public void onDismiss(DialogInterface dialog) {
            if( MyDebug.LOG )
                Log.d(TAG, "FolderChooserDialog dismissed");
            // n.b., fragments have to be static (as they might be inserted into a new Activity - see http://stackoverflow.com/questions/15571010/fragment-inner-class-should-be-static),
            // so we access the MainActivity via the fragment's getActivity().
            MainActivity main_activity = (MainActivity)this.getActivity();
            if( main_activity != null ) { // main_activity may be null if this is being closed via MainActivity.onNewIntent()
                String settings_file = this.getChosenFile();
                if( MyDebug.LOG )
                    Log.d(TAG, "settings_file: " + settings_file);
                if( settings_file != null ) {
                    main_activity.getSettingsManager().loadSettings(settings_file);
                }
            }
            super.onDismiss(dialog);
        }
    }

    private void readFromBundle(String [] values, String [] entries, String preference_key, String default_value, String preference_category_key) {
        if( MyDebug.LOG ) {
            Log.d(TAG, "readFromBundle");
        }
        if( values != null && values.length > 0 ) {
            if( MyDebug.LOG ) {
                Log.d(TAG, "values:");
                for(String value : values) {
                    Log.d(TAG, value);
                }
            }
            ListPreference lp = (ListPreference)findPreference(preference_key);
            lp.setEntries(entries);
            lp.setEntryValues(values);
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getActivity());
            String value = sharedPreferences.getString(preference_key, default_value);
            if( MyDebug.LOG )
                Log.d(TAG, "    value: " + Arrays.toString(values));
            lp.setValue(value);
        }
        else {
            if( MyDebug.LOG )
                Log.d(TAG, "remove preference " + preference_key + " from category " + preference_category_key);
            Preference pref = findPreference(preference_key);
            PreferenceGroup pg = (PreferenceGroup)this.findPreference(preference_category_key);
            pg.removePreference(pref);
        }
    }

    public void onResume() {
        super.onResume();
        // prevent fragment being transparent
        // note, setting color here only seems to affect the "main" preference fragment screen, and not sub-screens
        // note, on Galaxy Nexus Android 4.3 this sets to black rather than the dark grey that the background theme should be (and what the sub-screens use); works okay on Nexus 7 Android 5
        // we used to use a light theme for the PreferenceFragment, but mixing themes in same activity seems to cause problems (e.g., for EditTextPreference colors)
        TypedArray array = getActivity().getTheme().obtainStyledAttributes(new int[] {
                android.R.attr.colorBackground
        });
        int backgroundColor = array.getColor(0, Color.BLACK);
		/*if( MyDebug.LOG ) {
			int r = (backgroundColor >> 16) & 0xFF;
			int g = (backgroundColor >> 8) & 0xFF;
			int b = (backgroundColor >> 0) & 0xFF;
			Log.d(TAG, "backgroundColor: " + r + " , " + g + " , " + b);
		}*/
        getView().setBackgroundColor(backgroundColor);
        array.recycle();

        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this.getActivity());
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
    }

    public void onPause() {
        super.onPause();
    }

    @Override
    public void onDestroy() {
        if( MyDebug.LOG )
            Log.d(TAG, "onDestroy");
        super.onDestroy();

        // dismiss open dialogs - see comment for dialogs for why we do this
        for(AlertDialog dialog : dialogs) {
            if( MyDebug.LOG )
                Log.d(TAG, "dismiss dialog: " + dialog);
            dialog.dismiss();
        }
        // similarly dimiss any dialog fragments still opened
        Fragment folder_fragment = getFragmentManager().findFragmentByTag("FOLDER_FRAGMENT");
        if( folder_fragment != null ) {
            DialogFragment dialogFragment = (DialogFragment)folder_fragment;
            if( MyDebug.LOG )
                Log.d(TAG, "dismiss dialogFragment: " + dialogFragment);
            dialogFragment.dismissAllowingStateLoss();
        }
    }

    /* So that manual changes to the checkbox/switch preferences, while the preferences are showing, show up;
     * in particular, needed for preference_using_saf, when the user cancels the SAF dialog (see
     * MainActivity.onActivityResult).
     * Also programmatically sets summary (see setSummary).
     */
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if( MyDebug.LOG )
            Log.d(TAG, "onSharedPreferenceChanged: " + key);

        if( key == null ) {
            // On Android 11+, when targetting Android 11+, this method is called with key==null
            // if preferences are cleared. Unclear if this happens here in practice, but return
            // just in case.
            return;
        }

        Preference pref = findPreference(key);
        if( pref instanceof TwoStatePreference ) {
            TwoStatePreference twoStatePref = (TwoStatePreference)pref;
            twoStatePref.setChecked(prefs.getBoolean(key, true));
        }
        else if( pref instanceof  ListPreference ) {
            ListPreference listPref = (ListPreference)pref;
            listPref.setValue(prefs.getString(key, ""));
        }
        setSummary(key);
    }

    /** Programmatically sets summaries as required.
     *  Remember to call setSummary() from the constructor for any keys we set, to initialise the
     *  summary.
     */
    private void setSummary(String key) {
        Preference pref = findPreference(key);

        //noinspection DuplicateCondition
        if( pref instanceof EditTextPreference ) {
            /* We have a runtime check for using EditTextPreference - we don't want these due to importance of
             * supporting the Google Play emoji policy (see comment in MyEditTextPreference.java) - and this
             * helps guard against the risk of accidentally adding more EditTextPreferences in future.
             * Once we've switched to using Android X Preference library, and hence safe to use EditTextPreference
             * again, this code can be removed.
             */
            throw new RuntimeException("detected an EditTextPreference: " + key + " pref: " + pref);
        }

        //noinspection DuplicateCondition
        if( pref instanceof EditTextPreference || pref instanceof MyEditTextPreference) {
            // %s only supported for ListPreference
            // we also display the usual summary if no preference value is set
            if( pref.getKey().equals("preference_exif_artist") ||
                    pref.getKey().equals("preference_exif_copyright") ||
                    pref.getKey().equals("preference_save_photo_prefix") ||
                    pref.getKey().equals("preference_save_video_prefix") ||
                    pref.getKey().equals("preference_textstamp")
            ) {
                String default_value = "";
                if( pref.getKey().equals("preference_save_photo_prefix") )
                    default_value = "IMG_";
                else if( pref.getKey().equals("preference_save_video_prefix") )
                    default_value = "VID_";

                String current_value;
                if( pref instanceof EditTextPreference ) {
                    EditTextPreference editTextPref = (EditTextPreference)pref;
                    current_value = editTextPref.getText();
                }
                else {
                    MyEditTextPreference editTextPref = (MyEditTextPreference)pref;
                    current_value = editTextPref.getText();
                }

                if( current_value.equals(default_value) ) {
                    switch (pref.getKey()) {
                        case "preference_exif_artist":
                            pref.setSummary(R.string.preference_exif_artist_summary);
                            break;
                        case "preference_exif_copyright":
                            pref.setSummary(R.string.preference_exif_copyright_summary);
                            break;
                        case "preference_save_photo_prefix":
                            pref.setSummary(R.string.preference_save_photo_prefix_summary);
                            break;
                        case "preference_save_video_prefix":
                            pref.setSummary(R.string.preference_save_video_prefix_summary);
                            break;
                        case "preference_textstamp":
                            pref.setSummary(R.string.preference_textstamp_summary);
                            break;
                    }
                }
                else {
                    // non-default value, so display the current value
                    pref.setSummary(current_value);
                }
            }
        }
    }

    private void loadSettings() {
        if( MyDebug.LOG )
            Log.d(TAG, "loadSettings");
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(MyPreferenceFragment.this.getActivity());
        alertDialog.setIcon(android.R.drawable.ic_dialog_alert);
        alertDialog.setTitle(R.string.preference_restore_settings);
        alertDialog.setMessage(R.string.preference_restore_settings_question);
        alertDialog.setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if( MyDebug.LOG )
                    Log.d(TAG, "user confirmed to restore settings");
                MainActivity main_activity = (MainActivity)MyPreferenceFragment.this.getActivity();
				/*if( main_activity.getStorageUtils().isUsingSAF() ) {
					main_activity.openLoadSettingsChooserDialogSAF(true);
				}
				else*/ {
                    FolderChooserDialog fragment = new LoadSettingsFileChooserDialog();
                    fragment.setShowDCIMShortcut(false);
                    fragment.setShowNewFolderButton(false);
                    fragment.setModeFolder(false);
                    fragment.setExtension(".xml");
                    fragment.setStartFolder(main_activity.getStorageUtils().getSettingsFolder());
                    if( MainActivity.useScopedStorage() ) {
                        // since we use File API to load, don't allow going outside of the application's folder, as we won't be able to read those files!
                        fragment.setMaxParent(main_activity.getExternalFilesDir(null));
                    }
                    fragment.show(getFragmentManager(), "FOLDER_FRAGMENT");
                }
            }
        });
        alertDialog.setNegativeButton(android.R.string.no, null);
        final AlertDialog alert = alertDialog.create();
        // AlertDialog.Builder.setOnDismissListener() requires API level 17, so do it this way instead
        alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface arg0) {
                if( MyDebug.LOG )
                    Log.d(TAG, "reset dialog dismissed");
                dialogs.remove(alert);
            }
        });
        alert.show();
        dialogs.add(alert);
    }
}
