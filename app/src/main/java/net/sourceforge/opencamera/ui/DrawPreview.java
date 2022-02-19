package net.sourceforge.opencamera.ui;

import java.io.IOException;
import java.io.InputStream;
import java.text.DateFormat;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import net.sourceforge.opencamera.GyroSensor;
import net.sourceforge.opencamera.ImageSaver;
import net.sourceforge.opencamera.LocationSupplier;
import net.sourceforge.opencamera.MainActivity;
import net.sourceforge.opencamera.MyApplicationInterface;
import net.sourceforge.opencamera.MyDebug;
import net.sourceforge.opencamera.PreferenceKeys;
import net.sourceforge.opencamera.preview.ApplicationInterface;
import net.sourceforge.opencamera.R;
import net.sourceforge.opencamera.cameracontroller.CameraController;
import net.sourceforge.opencamera.preview.Preview;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.location.Location;
import android.net.Uri;
import android.os.BatteryManager;
import android.preference.PreferenceManager;
import android.util.Log;
import android.util.Pair;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.widget.RelativeLayout;

public class DrawPreview {
    private static final String TAG = "DrawPreview";

    private final MainActivity main_activity;
    private final MyApplicationInterface applicationInterface;

    // store to avoid calling PreferenceManager.getDefaultSharedPreferences() repeatedly
    private final SharedPreferences sharedPreferences;

    // cached preferences (need to call updateSettings() to refresh):
    private boolean has_settings;
    private MyApplicationInterface.PhotoMode photoMode;
    private boolean show_time_pref;
    private boolean show_camera_id_pref;
    private boolean show_free_memory_pref;
    private boolean show_iso_pref;
    private boolean show_video_max_amp_pref;
    private boolean show_zoom_pref;
    private boolean show_battery_pref;
    private boolean show_angle_pref;
    private int angle_highlight_color_pref;
    private boolean show_geo_direction_pref;
    private boolean take_photo_border_pref;
    private boolean preview_size_wysiwyg_pref;
    private boolean store_location_pref;
    private boolean show_angle_line_pref;
    private boolean show_pitch_lines_pref;
    private boolean show_geo_direction_lines_pref;
    private boolean immersive_mode_everything_pref;
    private boolean has_stamp_pref;
    private boolean is_raw_pref; // whether in RAW+JPEG or RAW only mode
    private boolean is_raw_only_pref; // whether in RAW only mode
    private boolean is_face_detection_pref;
    private boolean is_audio_enabled_pref;
    private boolean is_high_speed;
    private float capture_rate_factor;
    private boolean auto_stabilise_pref;
    private String preference_grid_pref;
    private String ghost_image_pref;
    private String ghost_selected_image_pref = "";
    private Bitmap ghost_selected_image_bitmap;
    private int ghost_image_alpha;
    private boolean want_histogram;
    private Preview.HistogramType histogram_type;
    private boolean want_zebra_stripes;
    private int zebra_stripes_threshold;
    private int zebra_stripes_color_foreground;
    private int zebra_stripes_color_background;
    private boolean want_focus_peaking;
    private int focus_peaking_color_pref;

    // avoid doing things that allocate memory every frame!
    private final Paint p = new Paint();
    private final RectF draw_rect = new RectF();
    private final int [] gui_location = new int[2];
    private final static DecimalFormat decimalFormat = new DecimalFormat("#0.0");
    private final float scale;
    private final float stroke_width; // stroke_width used for various UI elements
    private Calendar calendar;
    private DateFormat dateFormatTimeInstance;
    private final String ybounds_text;
    private final int [] temp_histogram_channel = new int[256];
    private final LocationSupplier.LocationInfo locationInfo = new LocationSupplier.LocationInfo();
    private final int [] auto_stabilise_crop = new int [2];
    //private final DecimalFormat decimal_format_1dp_force0 = new DecimalFormat("0.0");
    // cached Rects for drawTextWithBackground() calls
    private Rect text_bounds_time;
    private Rect text_bounds_camera_id;
    private Rect text_bounds_free_memory;
    private Rect text_bounds_angle_single;
    private Rect text_bounds_angle_double;

    private final static double close_level_angle = 1.0f;
    private String angle_string; // cached for UI performance
    private double cached_angle; // the angle that we used for the cached angle_string
    private long last_angle_string_time;

    private float free_memory_gb = -1.0f;
    private String free_memory_gb_string;
    private long last_free_memory_time;

    private String current_time_string;
    private long last_current_time_time;

    private String camera_id_string;
    private long last_camera_id_time;

    private String iso_exposure_string;
    private boolean is_scanning;
    private long last_iso_exposure_time;

    private boolean need_flash_indicator = false;
    private long last_need_flash_indicator_time;

    private final IntentFilter battery_ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    private boolean has_battery_frac;
    private float battery_frac;
    private long last_battery_time;

    private boolean has_video_max_amp;
    private int video_max_amp;
    private long last_video_max_amp_time;
    private int video_max_amp_prev2;
    private int video_max_amp_peak;

    private Bitmap location_bitmap;
    private Bitmap location_off_bitmap;
    private Bitmap raw_jpeg_bitmap;
    private Bitmap raw_only_bitmap;
    private Bitmap auto_stabilise_bitmap;
    private Bitmap dro_bitmap;
    private Bitmap hdr_bitmap;
    private Bitmap panorama_bitmap;
    private Bitmap expo_bitmap;
    //private Bitmap focus_bracket_bitmap;
        // no longer bother with a focus bracketing icon - hard to come up with a clear icon, and should be obvious from the two on-screen seekbars
    private Bitmap burst_bitmap;
    private Bitmap nr_bitmap;
    private Bitmap photostamp_bitmap;
    private Bitmap flash_bitmap;
    private Bitmap face_detection_bitmap;
    private Bitmap audio_disabled_bitmap;
    private Bitmap high_speed_fps_bitmap;
    private Bitmap slow_motion_bitmap;
    private Bitmap time_lapse_bitmap;
    private Bitmap rotate_left_bitmap;
    private Bitmap rotate_right_bitmap;

    private final Rect icon_dest = new Rect();
    private long needs_flash_time = -1; // time when flash symbol comes on (used for fade-in effect)
    private final Path path = new Path();

    private Bitmap last_thumbnail; // thumbnail of last picture taken
    private volatile boolean thumbnail_anim; // whether we are displaying the thumbnail animation; must be volatile for test project reading the state
    private long thumbnail_anim_start_ms = -1; // time that the thumbnail animation started
    public volatile int test_thumbnail_anim_count;
    private final RectF thumbnail_anim_src_rect = new RectF();
    private final RectF thumbnail_anim_dst_rect = new RectF();
    private final Matrix thumbnail_anim_matrix = new Matrix();
    private boolean last_thumbnail_is_video; // whether thumbnail is for video

    private boolean show_last_image; // whether to show the last image as part of "pause preview"
    private final RectF last_image_src_rect = new RectF();
    private final RectF last_image_dst_rect = new RectF();
    private final Matrix last_image_matrix = new Matrix();
    private boolean allow_ghost_last_image; // whether to allow ghosting the last image

    private long ae_started_scanning_ms = -1; // time when ae started scanning

    private boolean taking_picture; // true iff camera is in process of capturing a picture (including any necessary prior steps such as autofocus, flash/precapture)
    private boolean capture_started; // true iff the camera is capturing
    private boolean front_screen_flash; // true iff the front screen display should maximise to simulate flash
    private boolean image_queue_full; // whether we can no longer take new photos due to image queue being full (or rather, would become full if a new photo taken)

    private boolean continuous_focus_moving;
    private long continuous_focus_moving_ms;

    private boolean enable_gyro_target_spot;
    private final List<float []> gyro_directions = new ArrayList<>();
    private final float [] transformed_gyro_direction = new float[3];
    private final float [] gyro_direction_up = new float[3];
    private final float [] transformed_gyro_direction_up = new float[3];

    // call updateCachedViewAngles() before reading these values
    private float view_angle_x_preview;
    private float view_angle_y_preview;
    private long last_view_angles_time;

    private int take_photo_top; // coordinate (in canvas x coordinates, or y coords if system_orientation_portrait==true) of top of the take photo icon
    private long last_take_photo_top_time;

    private int top_icon_shift; // shift that may be needed for on-screen text to avoid clashing with icons (when arranged "along top")
    private long last_top_icon_shift_time;

    private int focus_seekbars_margin_left = -1; // margin left that's been set for the focus seekbars

    // OSD extra lines
    private String OSDLine1;
    private String OSDLine2;

    private final static int histogram_width_dp = 100;
    private final static int histogram_height_dp = 60;

    public DrawPreview(MainActivity main_activity, MyApplicationInterface applicationInterface) {
        if( MyDebug.LOG )
            Log.d(TAG, "DrawPreview");
        this.main_activity = main_activity;
        this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(main_activity);
        this.applicationInterface = applicationInterface;
        // n.b., don't call updateSettings() here, as it may rely on things that aren't yet initialise (e.g., the preview)
        // see testHDRRestart

        p.setAntiAlias(true);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setStrokeCap(Paint.Cap.ROUND);
        scale = getContext().getResources().getDisplayMetrics().density;
        //noinspection PointlessArithmeticExpression
        this.stroke_width = (1.0f * scale + 0.5f); // convert dps to pixels
        p.setStrokeWidth(this.stroke_width);

        location_bitmap = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.ic_gps_fixed_white_48dp);
        location_off_bitmap = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.ic_gps_off_white_48dp);
        raw_jpeg_bitmap = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.raw_icon);
        raw_only_bitmap = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.raw_only_icon);
        auto_stabilise_bitmap = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.auto_stabilise_icon);
        dro_bitmap = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.dro_icon);
        hdr_bitmap = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.ic_hdr_on_white_48dp);
        panorama_bitmap = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.baseline_panorama_horizontal_white_48);
        expo_bitmap = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.expo_icon);
        //focus_bracket_bitmap = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.focus_bracket_icon);
        burst_bitmap = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.ic_burst_mode_white_48dp);
        nr_bitmap = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.nr_icon);
        photostamp_bitmap = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.ic_text_format_white_48dp);
        flash_bitmap = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.flash_on);
        face_detection_bitmap = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.ic_face_white_48dp);
        audio_disabled_bitmap = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.ic_mic_off_white_48dp);
        high_speed_fps_bitmap = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.ic_fast_forward_white_48dp);
        slow_motion_bitmap = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.ic_slow_motion_video_white_48dp);
        time_lapse_bitmap = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.ic_timelapse_white_48dp);
        rotate_left_bitmap = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.baseline_rotate_left_white_48);
        rotate_right_bitmap = BitmapFactory.decodeResource(getContext().getResources(), R.drawable.baseline_rotate_right_white_48);

        ybounds_text = getContext().getResources().getString(R.string.zoom) + getContext().getResources().getString(R.string.angle) + getContext().getResources().getString(R.string.direction);
    }

    public void onDestroy() {
        if( MyDebug.LOG )
            Log.d(TAG, "onDestroy");
        // clean up just in case
        if( location_bitmap != null ) {
            location_bitmap.recycle();
            location_bitmap = null;
        }
        if( location_off_bitmap != null ) {
            location_off_bitmap.recycle();
            location_off_bitmap = null;
        }
        if( raw_jpeg_bitmap != null ) {
            raw_jpeg_bitmap.recycle();
            raw_jpeg_bitmap = null;
        }
        if( raw_only_bitmap != null ) {
            raw_only_bitmap.recycle();
            raw_only_bitmap = null;
        }
        if( auto_stabilise_bitmap != null ) {
            auto_stabilise_bitmap.recycle();
            auto_stabilise_bitmap = null;
        }
        if( dro_bitmap != null ) {
            dro_bitmap.recycle();
            dro_bitmap = null;
        }
        if( hdr_bitmap != null ) {
            hdr_bitmap.recycle();
            hdr_bitmap = null;
        }
        if( panorama_bitmap != null ) {
            panorama_bitmap.recycle();
            panorama_bitmap = null;
        }
        if( expo_bitmap != null ) {
            expo_bitmap.recycle();
            expo_bitmap = null;
        }
        /*if( focus_bracket_bitmap != null ) {
            focus_bracket_bitmap.recycle();
            focus_bracket_bitmap = null;
        }*/
        if( burst_bitmap != null ) {
            burst_bitmap.recycle();
            burst_bitmap = null;
        }
        if( nr_bitmap != null ) {
            nr_bitmap.recycle();
            nr_bitmap = null;
        }
        if( photostamp_bitmap != null ) {
            photostamp_bitmap.recycle();
            photostamp_bitmap = null;
        }
        if( flash_bitmap != null ) {
            flash_bitmap.recycle();
            flash_bitmap = null;
        }
        if( face_detection_bitmap != null ) {
            face_detection_bitmap.recycle();
            face_detection_bitmap = null;
        }
        if( audio_disabled_bitmap != null ) {
            audio_disabled_bitmap.recycle();
            audio_disabled_bitmap = null;
        }
        if( high_speed_fps_bitmap != null ) {
            high_speed_fps_bitmap.recycle();
            high_speed_fps_bitmap = null;
        }
        if( slow_motion_bitmap != null ) {
            slow_motion_bitmap.recycle();
            slow_motion_bitmap = null;
        }
        if( time_lapse_bitmap != null ) {
            time_lapse_bitmap.recycle();
            time_lapse_bitmap = null;
        }
        if( rotate_left_bitmap != null ) {
            rotate_left_bitmap.recycle();
            rotate_left_bitmap = null;
        }
        if( rotate_right_bitmap != null ) {
            rotate_right_bitmap.recycle();
            rotate_right_bitmap = null;
        }

        if( ghost_selected_image_bitmap != null ) {
            ghost_selected_image_bitmap.recycle();
            ghost_selected_image_bitmap = null;
        }
        ghost_selected_image_pref = "";
    }

    private Context getContext() {
        return main_activity;
    }

    /** Computes the x coordinate on screen of left side of the view, equivalent to
     *  view.getLocationOnScreen(), but we undo the effect of the view's rotation.
     *  This is because getLocationOnScreen() will return the coordinates of the view's top-left
     *  *after* applying the rotation, when we want the top left of the icon as shown on screen.
     *  This should not be called every frame but instead should be cached, due to cost of calling
     *  view.getLocationOnScreen().
     *  Update: For supporting landscape and portrait (if MainActivity.lock_to_landscape==false),
     *  instead this returns the top side if in portrait. Note though we still need to take rotation
     *  into account, as we still apply rotation to the icons when changing orienations (e.g., this
     *  is needed when rotating from reverse landscape to portrait, for on-screen text like level
     *  angle to be offset correctly above the shutter button (see take_photo_top) when the preview
     *  has a wide aspect ratio.
     */
    private int getViewOnScreenX(View view) {
        view.getLocationOnScreen(gui_location);

        MainActivity.SystemOrientation system_orientation = main_activity.getSystemOrientation();
        boolean system_orientation_portrait = system_orientation == MainActivity.SystemOrientation.PORTRAIT;
        int xpos = gui_location[system_orientation_portrait ? 1 : 0];
        int rotation = Math.round(view.getRotation());
        // rotation can be outside [0, 359] if the user repeatedly rotates in same direction!
        rotation = (rotation % 360 + 360) % 360; // version of (rotation % 360) that work if rotation is -ve
        /*if( MyDebug.LOG )
            Log.d(TAG, "    mod rotation: " + rotation);*/
        // undo annoying behaviour that getLocationOnScreen takes the rotation into account
        if( system_orientation_portrait ) {
            if( rotation == 180 || rotation == 270 ) {
                xpos -= view.getHeight();
            }
        }
        else {
            if( rotation == 90 || rotation == 180 ) {
                xpos -= view.getWidth();
            }
        }
        return xpos;
    }

    /** Sets a current thumbnail for a photo or video just taken. Used for thumbnail animation,
     *  and when ghosting the last image.
     */
    public void updateThumbnail(Bitmap thumbnail, boolean is_video, boolean want_thumbnail_animation) {
        if( MyDebug.LOG )
            Log.d(TAG, "updateThumbnail");
        if( want_thumbnail_animation && applicationInterface.getThumbnailAnimationPref() ) {
            if( MyDebug.LOG )
                Log.d(TAG, "thumbnail_anim started");
            thumbnail_anim = true;
            thumbnail_anim_start_ms = System.currentTimeMillis();
            test_thumbnail_anim_count++;
            if( MyDebug.LOG )
                Log.d(TAG, "test_thumbnail_anim_count is now: " + test_thumbnail_anim_count);
        }
        Bitmap old_thumbnail = this.last_thumbnail;
        this.last_thumbnail = thumbnail;
        this.last_thumbnail_is_video = is_video;
        this.allow_ghost_last_image = true;
        if( old_thumbnail != null ) {
            // only recycle after we've set the new thumbnail
            old_thumbnail.recycle();
        }
    }

    public boolean hasThumbnailAnimation() {
        return this.thumbnail_anim;
    }

    /** Displays the thumbnail as a fullscreen image (used for pause preview option).
     */
    public void showLastImage() {
        if( MyDebug.LOG )
            Log.d(TAG, "showLastImage");
        this.show_last_image = true;
    }

    public void clearLastImage() {
        if( MyDebug.LOG )
            Log.d(TAG, "clearLastImage");
        this.show_last_image = false;
    }

    public void clearGhostImage() {
        if( MyDebug.LOG )
            Log.d(TAG, "clearGhostImage");
        this.allow_ghost_last_image = false;
    }

    public void cameraInOperation(boolean in_operation) {
        if( in_operation && !main_activity.getPreview().isVideo() ) {
            taking_picture = true;
        }
        else {
            taking_picture = false;
            front_screen_flash = false;
            capture_started = false;
        }
    }

    public void setImageQueueFull(boolean image_queue_full) {
        this.image_queue_full = image_queue_full;
    }

    public void turnFrontScreenFlashOn() {
        if( MyDebug.LOG )
            Log.d(TAG, "turnFrontScreenFlashOn");
        front_screen_flash = true;
    }

    public void onCaptureStarted() {
        if( MyDebug.LOG )
            Log.d(TAG, "onCaptureStarted");
        capture_started = true;
    }

    public void onContinuousFocusMove(boolean start) {
        if( MyDebug.LOG )
            Log.d(TAG, "onContinuousFocusMove: " + start);
        if( start ) {
            if( !continuous_focus_moving ) { // don't restart the animation if already in motion
                continuous_focus_moving = true;
                continuous_focus_moving_ms = System.currentTimeMillis();
            }
        }
        // if we receive start==false, we don't stop the animation - let it continue
    }

    public void clearContinuousFocusMove() {
        if( MyDebug.LOG )
            Log.d(TAG, "clearContinuousFocusMove");
        if( continuous_focus_moving ) {
            continuous_focus_moving = false;
            continuous_focus_moving_ms = 0;
        }
    }

    public void setGyroDirectionMarker(float x, float y, float z) {
        enable_gyro_target_spot = true;
        this.gyro_directions.clear();
        addGyroDirectionMarker(x, y, z);
        gyro_direction_up[0] = 0.f;
        gyro_direction_up[1] = 1.f;
        gyro_direction_up[2] = 0.f;
    }

    public void addGyroDirectionMarker(float x, float y, float z) {
        float [] vector = new float[]{x, y, z};
        this.gyro_directions.add(vector);
    }

    public void clearGyroDirectionMarker() {
        enable_gyro_target_spot = false;
    }

    /** For performance reasons, some of the SharedPreferences settings are cached. This method
     *  should be used when the settings may have changed.
     */
    public void updateSettings() {
        if( MyDebug.LOG )
            Log.d(TAG, "updateSettings");

        photoMode = applicationInterface.getPhotoMode();
        if( MyDebug.LOG )
            Log.d(TAG, "photoMode: " + photoMode);

        show_time_pref = sharedPreferences.getBoolean(PreferenceKeys.ShowTimePreferenceKey, true);
        // reset in case user changes the preference:
        dateFormatTimeInstance = DateFormat.getTimeInstance();
        current_time_string = null;
        last_current_time_time = 0;
        text_bounds_time = null;

        show_camera_id_pref = main_activity.isMultiCam() && sharedPreferences.getBoolean(PreferenceKeys.ShowCameraIDPreferenceKey, true);
        //show_camera_id_pref = true; // test
        show_free_memory_pref = sharedPreferences.getBoolean(PreferenceKeys.ShowFreeMemoryPreferenceKey, true);
        show_iso_pref = sharedPreferences.getBoolean(PreferenceKeys.ShowISOPreferenceKey, true);
        show_video_max_amp_pref = sharedPreferences.getBoolean(PreferenceKeys.ShowVideoMaxAmpPreferenceKey, false);
        show_zoom_pref = sharedPreferences.getBoolean(PreferenceKeys.ShowZoomPreferenceKey, true);
        show_battery_pref = sharedPreferences.getBoolean(PreferenceKeys.ShowBatteryPreferenceKey, true);

        show_angle_pref = sharedPreferences.getBoolean(PreferenceKeys.ShowAnglePreferenceKey, false);
        String angle_highlight_color = sharedPreferences.getString(PreferenceKeys.ShowAngleHighlightColorPreferenceKey, "#14e715");
        angle_highlight_color_pref = Color.parseColor(angle_highlight_color);
        show_geo_direction_pref = sharedPreferences.getBoolean(PreferenceKeys.ShowGeoDirectionPreferenceKey, false);

        take_photo_border_pref = sharedPreferences.getBoolean(PreferenceKeys.TakePhotoBorderPreferenceKey, true);
        preview_size_wysiwyg_pref = sharedPreferences.getString(PreferenceKeys.PreviewSizePreferenceKey, "preference_preview_size_wysiwyg").equals("preference_preview_size_wysiwyg");
        store_location_pref = sharedPreferences.getBoolean(PreferenceKeys.LocationPreferenceKey, false);

        show_angle_line_pref = sharedPreferences.getBoolean(PreferenceKeys.ShowAngleLinePreferenceKey, false);
        show_pitch_lines_pref = sharedPreferences.getBoolean(PreferenceKeys.ShowPitchLinesPreferenceKey, false);
        show_geo_direction_lines_pref = sharedPreferences.getBoolean(PreferenceKeys.ShowGeoDirectionLinesPreferenceKey, false);

        String immersive_mode = sharedPreferences.getString(PreferenceKeys.ImmersiveModePreferenceKey, "immersive_mode_low_profile");
        immersive_mode_everything_pref = immersive_mode.equals("immersive_mode_everything");

        has_stamp_pref = applicationInterface.getStampPref().equals("preference_stamp_yes");
        is_raw_pref = applicationInterface.getRawPref() != ApplicationInterface.RawPref.RAWPREF_JPEG_ONLY;
        is_raw_only_pref = applicationInterface.isRawOnly();
        is_face_detection_pref = applicationInterface.getFaceDetectionPref();
        is_audio_enabled_pref = applicationInterface.getRecordAudioPref();

        is_high_speed = applicationInterface.fpsIsHighSpeed();
        capture_rate_factor = applicationInterface.getVideoCaptureRateFactor();

        auto_stabilise_pref = applicationInterface.getAutoStabilisePref();

        preference_grid_pref = sharedPreferences.getString(PreferenceKeys.ShowGridPreferenceKey, "preference_grid_none");

        ghost_image_pref = sharedPreferences.getString(PreferenceKeys.GhostImagePreferenceKey, "preference_ghost_image_off");
        if( ghost_image_pref.equals("preference_ghost_image_selected") ) {
            String new_ghost_selected_image_pref = sharedPreferences.getString(PreferenceKeys.GhostSelectedImageSAFPreferenceKey, "");
            if( MyDebug.LOG )
                Log.d(TAG, "new_ghost_selected_image_pref: " + new_ghost_selected_image_pref);

            KeyguardManager keyguard_manager = (KeyguardManager)main_activity.getSystemService(Context.KEYGUARD_SERVICE);
            boolean is_locked = keyguard_manager != null && keyguard_manager.inKeyguardRestrictedInputMode();
            if( MyDebug.LOG )
                Log.d(TAG, "is_locked?: " + is_locked);

            if( is_locked ) {
                // don't show selected image when device locked, as this could be a security flaw
                if( ghost_selected_image_bitmap != null ) {
                    ghost_selected_image_bitmap.recycle();
                    ghost_selected_image_bitmap = null;
                    ghost_selected_image_pref = ""; // so we'll load the bitmap again when unlocked
                }
            }
            else if( !new_ghost_selected_image_pref.equals(ghost_selected_image_pref) ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "ghost_selected_image_pref has changed");
                ghost_selected_image_pref = new_ghost_selected_image_pref;
                if( ghost_selected_image_bitmap != null ) {
                    ghost_selected_image_bitmap.recycle();
                    ghost_selected_image_bitmap = null;
                }
                Uri uri = Uri.parse(ghost_selected_image_pref);
                try {
                    ghost_selected_image_bitmap = loadBitmap(uri);
                }
                catch(IOException e) {
                    Log.e(TAG, "failed to load ghost_selected_image uri: " + uri);
                    e.printStackTrace();
                    ghost_selected_image_bitmap = null;
                    // don't set ghost_selected_image_pref to null, as we don't want to repeatedly try loading the invalid uri
                }
            }
        }
        else {
            if( ghost_selected_image_bitmap != null ) {
                ghost_selected_image_bitmap.recycle();
                ghost_selected_image_bitmap = null;
            }
            ghost_selected_image_pref = "";
        }
        ghost_image_alpha = applicationInterface.getGhostImageAlpha();

        String histogram_pref = sharedPreferences.getString(PreferenceKeys.HistogramPreferenceKey, "preference_histogram_off");
        want_histogram = !histogram_pref.equals("preference_histogram_off") && main_activity.supportsPreviewBitmaps();
        histogram_type = Preview.HistogramType.HISTOGRAM_TYPE_VALUE;
        if( want_histogram ) {
            switch( histogram_pref ) {
                case "preference_histogram_rgb":
                    histogram_type = Preview.HistogramType.HISTOGRAM_TYPE_RGB;
                    break;
                case "preference_histogram_luminance":
                    histogram_type = Preview.HistogramType.HISTOGRAM_TYPE_LUMINANCE;
                    break;
                case "preference_histogram_value":
                    histogram_type = Preview.HistogramType.HISTOGRAM_TYPE_VALUE;
                    break;
                case "preference_histogram_intensity":
                    histogram_type = Preview.HistogramType.HISTOGRAM_TYPE_INTENSITY;
                    break;
                case "preference_histogram_lightness":
                    histogram_type = Preview.HistogramType.HISTOGRAM_TYPE_LIGHTNESS;
                    break;
            }
        }

        String zebra_stripes_value = sharedPreferences.getString(PreferenceKeys.ZebraStripesPreferenceKey, "0");
        try {
            zebra_stripes_threshold = Integer.parseInt(zebra_stripes_value);
        }
        catch(NumberFormatException e) {
            if( MyDebug.LOG )
                Log.e(TAG, "failed to parse zebra_stripes_value: " + zebra_stripes_value);
            e.printStackTrace();
            zebra_stripes_threshold = 0;
        }
        want_zebra_stripes = zebra_stripes_threshold != 0 & main_activity.supportsPreviewBitmaps();

        String zebra_stripes_color_foreground_value = sharedPreferences.getString(PreferenceKeys.ZebraStripesForegroundColorPreferenceKey, "#ff000000");
        zebra_stripes_color_foreground = Color.parseColor(zebra_stripes_color_foreground_value);
        String zebra_stripes_color_background_value = sharedPreferences.getString(PreferenceKeys.ZebraStripesBackgroundColorPreferenceKey, "#ffffffff");
        zebra_stripes_color_background = Color.parseColor(zebra_stripes_color_background_value);

        String focus_peaking_pref = sharedPreferences.getString(PreferenceKeys.FocusPeakingPreferenceKey, "preference_focus_peaking_off");
        want_focus_peaking = !focus_peaking_pref.equals("preference_focus_peaking_off") && main_activity.supportsPreviewBitmaps();
        String focus_peaking_color = sharedPreferences.getString(PreferenceKeys.FocusPeakingColorPreferenceKey, "#ffffff");
        focus_peaking_color_pref = Color.parseColor(focus_peaking_color);

        last_camera_id_time = 0; // in case camera id changed
        last_view_angles_time = 0; // force view angles to be recomputed
        last_take_photo_top_time = 0;  // force take_photo_top to be recomputed
        last_top_icon_shift_time = 0; // for top_icon_shift to be recomputed

        focus_seekbars_margin_left = -1; // needed as the focus seekbars can only be updated when visible

        has_settings = true;
    }

    private void updateCachedViewAngles(long time_ms) {
        if( last_view_angles_time == 0 || time_ms > last_view_angles_time + 10000 ) {
            if( MyDebug.LOG )
                Log.d(TAG, "update cached view angles");
            // don't call this too often, for UI performance
            // note that updateSettings will force the time to reset anyway, but we check every so often
            // again just in case...
            Preview preview = main_activity.getPreview();
            view_angle_x_preview = preview.getViewAngleX(true);
            view_angle_y_preview = preview.getViewAngleY(true);
            last_view_angles_time = time_ms;
        }
    }

    /** Loads the bitmap from the uri.
     *  The image will be downscaled if required to be comparable to the preview width.
     */
    private Bitmap loadBitmap(Uri uri) throws IOException {
        if( MyDebug.LOG )
            Log.d(TAG, "loadBitmap: " + uri);
        Bitmap bitmap;
        try {
            //bitmap = MediaStore.Images.Media.getBitmap(main_activity.getContentResolver(), uri);

            int sample_size = 1;
            {
                // attempt to compute appropriate scaling
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                InputStream input = main_activity.getContentResolver().openInputStream(uri);
                BitmapFactory.decodeStream(input, null, bounds);
                if( input != null )
                    input.close();

                if( bounds.outWidth != -1 && bounds.outHeight != -1 ) {
                    // compute appropriate scaling
                    int image_size = Math.max(bounds.outWidth, bounds.outHeight);

                    Point point = new Point();
                    Display display = main_activity.getWindowManager().getDefaultDisplay();
                    display.getSize(point);
                    int display_size = Math.max(point.x, point.y);

                    int ratio = (int) Math.ceil((double) image_size / display_size);
                    sample_size = Integer.highestOneBit(ratio);
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "display_size: " + display_size);
                        Log.d(TAG, "image_size: " + image_size);
                        Log.d(TAG, "ratio: " + ratio);
                        Log.d(TAG, "sample_size: " + sample_size);
                    }
                }
                else {
                    if( MyDebug.LOG )
                        Log.e(TAG, "failed to obtain width/height of bitmap");
                }
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inMutable = false;
            options.inSampleSize = sample_size;
            InputStream input = main_activity.getContentResolver().openInputStream(uri);
            bitmap = BitmapFactory.decodeStream(input, null, options);
            if( input != null )
                input.close();
            if( MyDebug.LOG && bitmap != null ) {
                Log.d(TAG, "bitmap width: " + bitmap.getWidth());
                Log.d(TAG, "bitmap height: " + bitmap.getHeight());
            }
        }
        catch(Exception e) {
            // Although Media.getBitmap() is documented as only throwing FileNotFoundException, IOException
            // (with the former being a subset of IOException anyway), I've had SecurityException from
            // Google Play - best to catch everything just in case.
            Log.e(TAG, "MediaStore.Images.Media.getBitmap exception");
            e.printStackTrace();
            throw new IOException();
        }
        if( bitmap == null ) {
            // just in case!
            Log.e(TAG, "MediaStore.Images.Media.getBitmap returned null");
            throw new IOException();
        }

        // now need to take exif orientation into account, as some devices or camera apps store the orientation in the exif tag,
        // which getBitmap() doesn't account for
        bitmap = main_activity.rotateForExif(bitmap, uri);

        return bitmap;
    }

    private String getTimeStringFromSeconds(long time) {
        int secs = (int)(time % 60);
        time /= 60;
        int mins = (int)(time % 60);
        time /= 60;
        long hours = time;
        return hours + ":" + String.format(Locale.getDefault(), "%02d", mins) + ":" + String.format(Locale.getDefault(), "%02d", secs);
    }

    private void drawGrids(Canvas canvas) {
        Preview preview = main_activity.getPreview();
        CameraController camera_controller = preview.getCameraController();
        if( camera_controller == null ) {
            return;
        }

        p.setStrokeWidth(stroke_width);

        switch( preference_grid_pref ) {
            case "preference_grid_3x3":
                p.setColor(Color.WHITE);
                canvas.drawLine(canvas.getWidth() / 3.0f, 0.0f, canvas.getWidth() / 3.0f, canvas.getHeight() - 1.0f, p);
                canvas.drawLine(2.0f * canvas.getWidth() / 3.0f, 0.0f, 2.0f * canvas.getWidth() / 3.0f, canvas.getHeight() - 1.0f, p);
                canvas.drawLine(0.0f, canvas.getHeight() / 3.0f, canvas.getWidth() - 1.0f, canvas.getHeight() / 3.0f, p);
                canvas.drawLine(0.0f, 2.0f * canvas.getHeight() / 3.0f, canvas.getWidth() - 1.0f, 2.0f * canvas.getHeight() / 3.0f, p);
                break;
            case "preference_grid_phi_3x3":
                p.setColor(Color.WHITE);
                canvas.drawLine(canvas.getWidth() / 2.618f, 0.0f, canvas.getWidth() / 2.618f, canvas.getHeight() - 1.0f, p);
                canvas.drawLine(1.618f * canvas.getWidth() / 2.618f, 0.0f, 1.618f * canvas.getWidth() / 2.618f, canvas.getHeight() - 1.0f, p);
                canvas.drawLine(0.0f, canvas.getHeight() / 2.618f, canvas.getWidth() - 1.0f, canvas.getHeight() / 2.618f, p);
                canvas.drawLine(0.0f, 1.618f * canvas.getHeight() / 2.618f, canvas.getWidth() - 1.0f, 1.618f * canvas.getHeight() / 2.618f, p);
                break;
            case "preference_grid_4x2":
                p.setColor(Color.GRAY);
                canvas.drawLine(canvas.getWidth() / 4.0f, 0.0f, canvas.getWidth() / 4.0f, canvas.getHeight() - 1.0f, p);
                canvas.drawLine(canvas.getWidth() / 2.0f, 0.0f, canvas.getWidth() / 2.0f, canvas.getHeight() - 1.0f, p);
                canvas.drawLine(3.0f * canvas.getWidth() / 4.0f, 0.0f, 3.0f * canvas.getWidth() / 4.0f, canvas.getHeight() - 1.0f, p);
                canvas.drawLine(0.0f, canvas.getHeight() / 2.0f, canvas.getWidth() - 1.0f, canvas.getHeight() / 2.0f, p);
                p.setColor(Color.WHITE);
                int crosshairs_radius = (int) (20 * scale + 0.5f); // convert dps to pixels

                canvas.drawLine(canvas.getWidth() / 2.0f, canvas.getHeight() / 2.0f - crosshairs_radius, canvas.getWidth() / 2.0f, canvas.getHeight() / 2.0f + crosshairs_radius, p);
                canvas.drawLine(canvas.getWidth() / 2.0f - crosshairs_radius, canvas.getHeight() / 2.0f, canvas.getWidth() / 2.0f + crosshairs_radius, canvas.getHeight() / 2.0f, p);
                break;
            case "preference_grid_crosshair":
                p.setColor(Color.WHITE);
                canvas.drawLine(canvas.getWidth() / 2.0f, 0.0f, canvas.getWidth() / 2.0f, canvas.getHeight() - 1.0f, p);
                canvas.drawLine(0.0f, canvas.getHeight() / 2.0f, canvas.getWidth() - 1.0f, canvas.getHeight() / 2.0f, p);
                break;
            case "preference_grid_golden_spiral_right":
            case "preference_grid_golden_spiral_left":
            case "preference_grid_golden_spiral_upside_down_right":
            case "preference_grid_golden_spiral_upside_down_left":
                canvas.save();
                switch( preference_grid_pref ) {
                    case "preference_grid_golden_spiral_left":
                        canvas.scale(-1.0f, 1.0f, canvas.getWidth() * 0.5f, canvas.getHeight() * 0.5f);
                        break;
                    case "preference_grid_golden_spiral_right":
                        // no transformation needed
                        break;
                    case "preference_grid_golden_spiral_upside_down_left":
                        canvas.rotate(180.0f, canvas.getWidth() * 0.5f, canvas.getHeight() * 0.5f);
                        break;
                    case "preference_grid_golden_spiral_upside_down_right":
                        canvas.scale(1.0f, -1.0f, canvas.getWidth() * 0.5f, canvas.getHeight() * 0.5f);
                        break;
                }
                p.setColor(Color.WHITE);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(stroke_width);
                int fibb = 34;
                int fibb_n = 21;
                int left = 0, top = 0;
                int full_width = canvas.getWidth();
                int full_height = canvas.getHeight();
                int width = (int) (full_width * ((double) fibb_n) / (double) (fibb));
                int height = full_height;

                for (int count = 0; count < 2; count++) {
                    canvas.save();
                    draw_rect.set(left, top, left + width, top + height);
                    canvas.clipRect(draw_rect);
                    canvas.drawRect(draw_rect, p);
                    draw_rect.set(left, top, left + 2 * width, top + 2 * height);
                    canvas.drawOval(draw_rect, p);
                    canvas.restore();

                    int old_fibb = fibb;
                    fibb = fibb_n;
                    fibb_n = old_fibb - fibb;

                    left += width;
                    full_width = full_width - width;
                    width = full_width;
                    height = (int) (height * ((double) fibb_n) / (double) (fibb));

                    canvas.save();
                    draw_rect.set(left, top, left + width, top + height);
                    canvas.clipRect(draw_rect);
                    canvas.drawRect(draw_rect, p);
                    draw_rect.set(left - width, top, left + width, top + 2 * height);
                    canvas.drawOval(draw_rect, p);
                    canvas.restore();

                    old_fibb = fibb;
                    fibb = fibb_n;
                    fibb_n = old_fibb - fibb;

                    top += height;
                    full_height = full_height - height;
                    height = full_height;
                    width = (int) (width * ((double) fibb_n) / (double) (fibb));
                    left += full_width - width;

                    canvas.save();
                    draw_rect.set(left, top, left + width, top + height);
                    canvas.clipRect(draw_rect);
                    canvas.drawRect(draw_rect, p);
                    draw_rect.set(left - width, top - height, left + width, top + height);
                    canvas.drawOval(draw_rect, p);
                    canvas.restore();

                    old_fibb = fibb;
                    fibb = fibb_n;
                    fibb_n = old_fibb - fibb;

                    full_width = full_width - width;
                    width = full_width;
                    left -= width;
                    height = (int) (height * ((double) fibb_n) / (double) (fibb));
                    top += full_height - height;

                    canvas.save();
                    draw_rect.set(left, top, left + width, top + height);
                    canvas.clipRect(draw_rect);
                    canvas.drawRect(draw_rect, p);
                    draw_rect.set(left, top - height, left + 2 * width, top + height);
                    canvas.drawOval(draw_rect, p);
                    canvas.restore();

                    old_fibb = fibb;
                    fibb = fibb_n;
                    fibb_n = old_fibb - fibb;

                    full_height = full_height - height;
                    height = full_height;
                    top -= height;
                    width = (int) (width * ((double) fibb_n) / (double) (fibb));
                }

                canvas.restore();
                p.setStyle(Paint.Style.FILL); // reset

                break;
            case "preference_grid_golden_triangle_1":
            case "preference_grid_golden_triangle_2":
                p.setColor(Color.WHITE);
                double theta = Math.atan2(canvas.getWidth(), canvas.getHeight());
                double dist = canvas.getHeight() * Math.cos(theta);
                float dist_x = (float) (dist * Math.sin(theta));
                float dist_y = (float) (dist * Math.cos(theta));
                if( preference_grid_pref.equals("preference_grid_golden_triangle_1") ) {
                    canvas.drawLine(0.0f, canvas.getHeight() - 1.0f, canvas.getWidth() - 1.0f, 0.0f, p);
                    canvas.drawLine(0.0f, 0.0f, dist_x, canvas.getHeight() - dist_y, p);
                    canvas.drawLine(canvas.getWidth() - 1.0f - dist_x, dist_y - 1.0f, canvas.getWidth() - 1.0f, canvas.getHeight() - 1.0f, p);
                }
                else {
                    canvas.drawLine(0.0f, 0.0f, canvas.getWidth() - 1.0f, canvas.getHeight() - 1.0f, p);
                    canvas.drawLine(canvas.getWidth() - 1.0f, 0.0f, canvas.getWidth() - 1.0f - dist_x, canvas.getHeight() - dist_y, p);
                    canvas.drawLine(dist_x, dist_y - 1.0f, 0.0f, canvas.getHeight() - 1.0f, p);
                }
                break;
            case "preference_grid_diagonals":
                p.setColor(Color.WHITE);
                canvas.drawLine(0.0f, 0.0f, canvas.getHeight() - 1.0f, canvas.getHeight() - 1.0f, p);
                canvas.drawLine(canvas.getHeight() - 1.0f, 0.0f, 0.0f, canvas.getHeight() - 1.0f, p);
                int diff = canvas.getWidth() - canvas.getHeight();
                // n.b., diff is -ve in portrait orientation
                canvas.drawLine(diff, 0.0f, diff + canvas.getHeight() - 1.0f, canvas.getHeight() - 1.0f, p);
                canvas.drawLine(diff + canvas.getHeight() - 1.0f, 0.0f, diff, canvas.getHeight() - 1.0f, p);
                break;
        }
    }

    private void drawCropGuides(Canvas canvas) {
        Preview preview = main_activity.getPreview();
        CameraController camera_controller = preview.getCameraController();
        if( preview.isVideo() || preview_size_wysiwyg_pref ) {
            String preference_crop_guide = sharedPreferences.getString(PreferenceKeys.ShowCropGuidePreferenceKey, "crop_guide_none");
            if( camera_controller != null && preview.getTargetRatio() > 0.0 && !preference_crop_guide.equals("crop_guide_none") ) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(stroke_width);
                p.setColor(Color.rgb(255, 235, 59)); // Yellow 500
                double crop_ratio = -1.0;
                switch(preference_crop_guide) {
                    case "crop_guide_1":
                        crop_ratio = 1.0;
                        break;
                    case "crop_guide_1.25":
                        crop_ratio = 1.25;
                        break;
                    case "crop_guide_1.33":
                        crop_ratio = 1.33333333;
                        break;
                    case "crop_guide_1.4":
                        crop_ratio = 1.4;
                        break;
                    case "crop_guide_1.5":
                        crop_ratio = 1.5;
                        break;
                    case "crop_guide_1.78":
                        crop_ratio = 1.77777778;
                        break;
                    case "crop_guide_1.85":
                        crop_ratio = 1.85;
                        break;
                    case "crop_guide_2":
                        crop_ratio = 2.0;
                        break;
                    case "crop_guide_2.33":
                        crop_ratio = 2.33333333;
                        break;
                    case "crop_guide_2.35":
                        crop_ratio = 2.35006120; // actually 1920:817
                        break;
                    case "crop_guide_2.4":
                        crop_ratio = 2.4;
                        break;
                }
                if( crop_ratio > 0.0 ) {
                    // we should compare to getCurrentPreviewAspectRatio() not getTargetRatio(), as the actual preview
                    // aspect ratio may differ to the requested photo/video resolution's aspect ratio, in which case it's still useful
                    // to display the crop guide
                    double preview_aspect_ratio = preview.getCurrentPreviewAspectRatio();
                    MainActivity.SystemOrientation system_orientation = main_activity.getSystemOrientation();
                    boolean system_orientation_portrait = system_orientation == MainActivity.SystemOrientation.PORTRAIT;
                    if( system_orientation_portrait ) {
                        // crop ratios are always drawn as if in landscape
                        crop_ratio = 1.0/crop_ratio;
                        preview_aspect_ratio = 1.0/preview_aspect_ratio;
                    }
                    if( Math.abs(preview_aspect_ratio - crop_ratio) > 1.0e-5 ) {
                        /*if( MyDebug.LOG ) {
                            Log.d(TAG, "crop_ratio: " + crop_ratio);
                            Log.d(TAG, "preview_aspect_ratio: " + preview_aspect_ratio);
                            Log.d(TAG, "canvas width: " + canvas.getWidth());
                            Log.d(TAG, "canvas height: " + canvas.getHeight());
                        }*/
                        int left = 1, top = 1, right = canvas.getWidth()-1, bottom = canvas.getHeight()-1;
                        if( crop_ratio > preview_aspect_ratio ) {
                            // crop ratio is wider, so we have to crop top/bottom
                            double new_hheight = ((double)canvas.getWidth()) / (2.0f*crop_ratio);
                            top = (canvas.getHeight()/2 - (int)new_hheight);
                            bottom = (canvas.getHeight()/2 + (int)new_hheight);
                        }
                        else {
                            // crop ratio is taller, so we have to crop left/right
                            double new_hwidth = (((double)canvas.getHeight()) * crop_ratio) / 2.0f;
                            left = (canvas.getWidth()/2 - (int)new_hwidth);
                            right = (canvas.getWidth()/2 + (int)new_hwidth);
                        }
                        canvas.drawRect(left, top, right, bottom, p);
                    }
                }
                p.setStyle(Paint.Style.FILL); // reset
            }
        }
    }

    private void onDrawInfoLines(Canvas canvas, final int top_x, final int top_y, final int bottom_y, final int device_ui_rotation, long time_ms) {
        Preview preview = main_activity.getPreview();
        CameraController camera_controller = preview.getCameraController();
        int ui_rotation = preview.getUIRotation();

        // set up text etc for the multiple lines of "info" (time, free mem, etc)
        p.setTextSize(16 * scale + 0.5f); // convert dps to pixels
        p.setTextAlign(Paint.Align.LEFT);
        int location_x = top_x;
        int location_y = top_y;
        final int gap_x = (int) (8 * scale + 0.5f); // convert dps to pixels
        final int gap_y = (int) (0 * scale + 0.5f); // convert dps to pixels
        final int icon_gap_y = (int) (2 * scale + 0.5f); // convert dps to pixels
        if( ui_rotation == 90 || ui_rotation == 270 ) {
            // n.b., this is only for when lock_to_landscape==true, so we don't look at device_ui_rotation
            int diff = canvas.getWidth() - canvas.getHeight();
            location_x += diff/2;
            location_y -= diff/2;
        }
        if( device_ui_rotation == 90 ) {
            location_y = canvas.getHeight() - location_y - (int) (20 * scale + 0.5f);
        }
        boolean align_right = false;
        if( device_ui_rotation == 180 ) {
            location_x = canvas.getWidth() - location_x;
            p.setTextAlign(Paint.Align.RIGHT);
            align_right = true;
        }

        int first_line_height = 0;
        int first_line_xshift = 0;
        if( show_time_pref ) {
            if( current_time_string == null || time_ms/1000 > last_current_time_time/1000 ) {
                // avoid creating a new calendar object every time
                if( calendar == null )
                    calendar = Calendar.getInstance();
                else
                    calendar.setTimeInMillis(time_ms);

                current_time_string = dateFormatTimeInstance.format(calendar.getTime());
                //current_time_string = DateUtils.formatDateTime(getContext(), c.getTimeInMillis(), DateUtils.FORMAT_SHOW_TIME);
                last_current_time_time = time_ms;
            }
            // n.b., DateFormat.getTimeInstance() ignores user preferences such as 12/24 hour or date format, but this is an Android bug.
            // Whilst DateUtils.formatDateTime doesn't have that problem, it doesn't print out seconds! See:
            // http://stackoverflow.com/questions/15981516/simpledateformat-gettimeinstance-ignores-24-hour-format
            // http://daniel-codes.blogspot.co.uk/2013/06/how-to-correctly-format-datetime.html
            // http://code.google.com/p/android/issues/detail?id=42104
            // update: now seems to be fixed
            // also possibly related https://code.google.com/p/android/issues/detail?id=181201
            //int height = applicationInterface.drawTextWithBackground(canvas, p, current_time_string, Color.WHITE, Color.BLACK, location_x, location_y, MyApplicationInterface.Alignment.ALIGNMENT_TOP);
            if( text_bounds_time == null ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "compute text_bounds_time");
                text_bounds_time = new Rect();
                // better to not use a fixed string like "00:00:00" as don't want to make assumptions - e.g., in 12 hour format we'll have the appended am/pm to account for!
                Calendar calendar = Calendar.getInstance();
                calendar.set(100, 0, 1, 10, 59, 59);
                String bounds_time_string = dateFormatTimeInstance.format(calendar.getTime());
                if( MyDebug.LOG )
                    Log.d(TAG, "bounds_time_string:" + bounds_time_string);
                p.getTextBounds(bounds_time_string, 0, bounds_time_string.length(), text_bounds_time);
            }
            first_line_xshift += text_bounds_time.width() + gap_x;
            int height = applicationInterface.drawTextWithBackground(canvas, p, current_time_string, Color.WHITE, Color.BLACK, location_x, location_y, MyApplicationInterface.Alignment.ALIGNMENT_TOP, null, MyApplicationInterface.Shadow.SHADOW_OUTLINE, text_bounds_time);
            height += gap_y;
            // don't update location_y yet, as we have time and cameraid shown on the same line
            first_line_height = Math.max(first_line_height, height);
        }
        if( show_camera_id_pref && camera_controller != null ) {
            if( camera_id_string == null || time_ms > last_camera_id_time + 10000 ) {
                // cache string for performance

                camera_id_string = getContext().getResources().getString(R.string.camera_id) + ":" + preview.getCameraId(); // intentionally don't put a space
                last_camera_id_time = time_ms;
            }
            if( text_bounds_camera_id == null ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "compute text_bounds_camera_id");
                text_bounds_camera_id = new Rect();
                p.getTextBounds(camera_id_string, 0, camera_id_string.length(), text_bounds_camera_id);
            }
            int xpos = align_right ? location_x - first_line_xshift : location_x + first_line_xshift;
            int height = applicationInterface.drawTextWithBackground(canvas, p, camera_id_string, Color.WHITE, Color.BLACK, xpos, location_y, MyApplicationInterface.Alignment.ALIGNMENT_TOP, null, MyApplicationInterface.Shadow.SHADOW_OUTLINE, text_bounds_camera_id);
            height += gap_y;
            // don't update location_y yet, as we have time and cameraid shown on the same line
            first_line_height = Math.max(first_line_height, height);
        }
        // update location_y for first line (time and camera id)
        if( device_ui_rotation == 90 ) {
            // upside-down portrait
            location_y -= first_line_height;
        }
        else {
            location_y += first_line_height;
        }

        if( camera_controller != null && show_free_memory_pref ) {
            if( last_free_memory_time == 0 || time_ms > last_free_memory_time + 10000 ) {
                // don't call this too often, for UI performance
                long free_mb = main_activity.getStorageUtils().freeMemory();
                if( free_mb >= 0 ) {
                    float new_free_memory_gb = free_mb/1024.0f;
                    if( MyDebug.LOG ) {
                        Log.d(TAG, "free_memory_gb: " + free_memory_gb);
                        Log.d(TAG, "new_free_memory_gb: " + new_free_memory_gb);
                    }
                    if( Math.abs(new_free_memory_gb - free_memory_gb) > 0.001f ) {
                        free_memory_gb = new_free_memory_gb;
                        free_memory_gb_string = decimalFormat.format(free_memory_gb) + getContext().getResources().getString(R.string.gb_abbreviation);
                    }
                }
                last_free_memory_time = time_ms; // always set this, so that in case of free memory not being available, we aren't calling freeMemory() every frame
            }
            if( free_memory_gb >= 0.0f && free_memory_gb_string != null ) {
                //int height = applicationInterface.drawTextWithBackground(canvas, p, free_memory_gb_string, Color.WHITE, Color.BLACK, location_x, location_y, MyApplicationInterface.Alignment.ALIGNMENT_TOP);
                if( text_bounds_free_memory == null ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "compute text_bounds_free_memory");
                    text_bounds_free_memory = new Rect();
                    p.getTextBounds(free_memory_gb_string, 0, free_memory_gb_string.length(), text_bounds_free_memory);
                }
                int height = applicationInterface.drawTextWithBackground(canvas, p, free_memory_gb_string, Color.WHITE, Color.BLACK, location_x, location_y, MyApplicationInterface.Alignment.ALIGNMENT_TOP, null, MyApplicationInterface.Shadow.SHADOW_OUTLINE, text_bounds_free_memory);
                height += gap_y;
                if( device_ui_rotation == 90 ) {
                    location_y -= height;
                }
                else {
                    location_y += height;
                }
            }
        }

        // Now draw additional info on the lower left corner if needed
        int y_offset = (int) (27 * scale + 0.5f);
        p.setTextSize(24 * scale + 0.5f); // convert dps to pixels
        if (OSDLine1 != null && OSDLine1.length() > 0) {
            applicationInterface.drawTextWithBackground(canvas, p, OSDLine1,
                    Color.WHITE, Color.BLACK,  location_x, bottom_y - y_offset,
                    MyApplicationInterface.Alignment.ALIGNMENT_BOTTOM, null, MyApplicationInterface.Shadow.SHADOW_OUTLINE);
        }
        if (OSDLine2 != null && OSDLine2.length() > 0) {
            applicationInterface.drawTextWithBackground(canvas, p, OSDLine2,
                    Color.WHITE, Color.BLACK, location_x, bottom_y,
                    MyApplicationInterface.Alignment.ALIGNMENT_BOTTOM, null, MyApplicationInterface.Shadow.SHADOW_OUTLINE);
        }
        p.setTextSize(16 * scale + 0.5f); // Restore text size

        if( camera_controller != null && show_iso_pref ) {
            if( iso_exposure_string == null || time_ms > last_iso_exposure_time + 500 ) {
                iso_exposure_string = "";
                if( camera_controller.captureResultHasIso() ) {
                    int iso = camera_controller.captureResultIso();
                    if( iso_exposure_string.length() > 0 )
                        iso_exposure_string += " ";
                    iso_exposure_string += preview.getISOString(iso);
                }
                if( camera_controller.captureResultHasExposureTime() ) {
                    long exposure_time = camera_controller.captureResultExposureTime();
                    if( iso_exposure_string.length() > 0 )
                        iso_exposure_string += " ";
                    iso_exposure_string += preview.getExposureTimeString(exposure_time);
                }
                if( preview.isVideoRecording() && camera_controller.captureResultHasFrameDuration() ) {
                    long frame_duration = camera_controller.captureResultFrameDuration();
                    if( iso_exposure_string.length() > 0 )
                        iso_exposure_string += " ";
                    iso_exposure_string += preview.getFrameDurationString(frame_duration);
                }
                /*if( camera_controller.captureResultHasAperture() ) {
                    float aperture = camera_controller.captureResultAperture();
                    if( iso_exposure_string.length() > 0 )
                        iso_exposure_string += " F";
                    iso_exposure_string += decimal_format_1dp_force0.format(aperture);
                }*/

                is_scanning = false;
                if( camera_controller.captureResultIsAEScanning() ) {
                    // only show as scanning if in auto ISO mode (problem on Nexus 6 at least that if we're in manual ISO mode, after pausing and
                    // resuming, the camera driver continually reports CONTROL_AE_STATE_SEARCHING)
                    String value = sharedPreferences.getString(PreferenceKeys.ISOPreferenceKey, CameraController.ISO_DEFAULT);
                    if( value.equals("auto") ) {
                        is_scanning = true;
                    }
                }

                last_iso_exposure_time = time_ms;
            }

            if( iso_exposure_string.length() > 0 ) {
                int text_color = Color.rgb(255, 235, 59); // Yellow 500
                if( is_scanning ) {
                    // we only change the color if ae scanning is at least a certain time, otherwise we get a lot of flickering of the color
                    if( ae_started_scanning_ms == -1 ) {
                        ae_started_scanning_ms = time_ms;
                    }
                    else if( time_ms - ae_started_scanning_ms > 500 ) {
                        text_color = Color.rgb(244, 67, 54); // Red 500
                    }
                }
                else {
                    ae_started_scanning_ms = -1;
                }
                // can't cache the bounds rect, as the width may change significantly as the ISO or exposure values change
                int height = applicationInterface.drawTextWithBackground(canvas, p, iso_exposure_string, text_color, Color.BLACK, location_x, location_y, MyApplicationInterface.Alignment.ALIGNMENT_TOP, ybounds_text, MyApplicationInterface.Shadow.SHADOW_OUTLINE);
                height += gap_y;
                // only move location_y if we actually print something (because on old camera API, even if the ISO option has
                // been enabled, we'll never be able to display the on-screen ISO)
                if( device_ui_rotation == 90 ) {
                    location_y -= height;
                }
                else {
                    location_y += height;
                }
            }
        }

        // padding to align with earlier text
        final int flash_padding = (int) (1 * scale + 0.5f); // convert dps to pixels

        if( camera_controller != null ) {
            // draw info icons

            int location_x2 = location_x - flash_padding;
            final int icon_size = (int) (16 * scale + 0.5f); // convert dps to pixels
            if( device_ui_rotation == 180 ) {
                location_x2 = location_x - icon_size + flash_padding;
            }

            if( store_location_pref ) {
                icon_dest.set(location_x2, location_y, location_x2 + icon_size, location_y + icon_size);
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.BLACK);
                p.setAlpha(64);
                canvas.drawRect(icon_dest, p);
                p.setAlpha(255);

                Location location = applicationInterface.getLocation(locationInfo);
                if( location != null ) {
                    canvas.drawBitmap(location_bitmap, null, icon_dest, p);
                    int location_radius = icon_size / 10;
                    int indicator_x = location_x2 + icon_size - (int)(location_radius*1.5);
                    int indicator_y = location_y + (int)(location_radius*1.5);
                    p.setColor(locationInfo.LocationWasCached() ? Color.rgb(127, 127, 127) :
                            location.getAccuracy() < 25.01f ? Color.rgb(37, 155, 36) :
                                    Color.rgb(255, 235, 59)); // Green 500 or Yellow 500
                    canvas.drawCircle(indicator_x, indicator_y, location_radius, p);
                }
                else {
                    canvas.drawBitmap(location_off_bitmap, null, icon_dest, p);
                }

                if( device_ui_rotation == 180 ) {
                    location_x2 -= icon_size + flash_padding;
                }
                else {
                    location_x2 += icon_size + flash_padding;
                }
            }

            if(
                    is_raw_pref &&
                            preview.supportsRaw()
                // RAW can be enabled, even if it isn't available for this camera (e.g., user enables RAW for back camera, but then
                // switches to front camera which doesn't support it)
            ) {
                icon_dest.set(location_x2, location_y, location_x2 + icon_size, location_y + icon_size);
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.BLACK);
                p.setAlpha(64);
                canvas.drawRect(icon_dest, p);
                p.setAlpha(255);
                canvas.drawBitmap(is_raw_only_pref ? raw_only_bitmap : raw_jpeg_bitmap, null, icon_dest, p);

                if( device_ui_rotation == 180 ) {
                    location_x2 -= icon_size + flash_padding;
                }
                else {
                    location_x2 += icon_size + flash_padding;
                }
            }

            if( is_face_detection_pref && preview.supportsFaceDetection() ) {
                icon_dest.set(location_x2, location_y, location_x2 + icon_size, location_y + icon_size);
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.BLACK);
                p.setAlpha(64);
                canvas.drawRect(icon_dest, p);
                p.setAlpha(255);
                canvas.drawBitmap(face_detection_bitmap, null, icon_dest, p);

                if( device_ui_rotation == 180 ) {
                    location_x2 -= icon_size + flash_padding;
                }
                else {
                    location_x2 += icon_size + flash_padding;
                }
            }

            if( auto_stabilise_pref && preview.hasLevelAngleStable() ) { // auto-level is supported for photos taken in video mode
                icon_dest.set(location_x2, location_y, location_x2 + icon_size, location_y + icon_size);
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.BLACK);
                p.setAlpha(64);
                canvas.drawRect(icon_dest, p);
                p.setAlpha(255);
                canvas.drawBitmap(auto_stabilise_bitmap, null, icon_dest, p);

                if( device_ui_rotation == 180 ) {
                    location_x2 -= icon_size + flash_padding;
                }
                else {
                    location_x2 += icon_size + flash_padding;
                }
            }

            if( (
                    photoMode == MyApplicationInterface.PhotoMode.DRO ||
                            photoMode == MyApplicationInterface.PhotoMode.HDR ||
                            photoMode == MyApplicationInterface.PhotoMode.Panorama ||
                            photoMode == MyApplicationInterface.PhotoMode.ExpoBracketing ||
                            //photoMode == MyApplicationInterface.PhotoMode.FocusBracketing ||
                            photoMode == MyApplicationInterface.PhotoMode.FastBurst ||
                            photoMode == MyApplicationInterface.PhotoMode.NoiseReduction
            ) &&
                    !applicationInterface.isVideoPref() ) { // these photo modes not supported for video mode
                icon_dest.set(location_x2, location_y, location_x2 + icon_size, location_y + icon_size);
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.BLACK);
                p.setAlpha(64);
                canvas.drawRect(icon_dest, p);
                p.setAlpha(255);
                Bitmap bitmap = photoMode == MyApplicationInterface.PhotoMode.DRO ? dro_bitmap :
                        photoMode == MyApplicationInterface.PhotoMode.HDR ? hdr_bitmap :
                                photoMode == MyApplicationInterface.PhotoMode.Panorama ? panorama_bitmap :
                                    photoMode == MyApplicationInterface.PhotoMode.ExpoBracketing ? expo_bitmap :
                                            //photoMode == MyApplicationInterface.PhotoMode.FocusBracketing ? focus_bracket_bitmap :
                                                    photoMode == MyApplicationInterface.PhotoMode.FastBurst ? burst_bitmap :
                                                            photoMode == MyApplicationInterface.PhotoMode.NoiseReduction ? nr_bitmap :
                                                                    null;
                if( bitmap != null ) {
                    if( photoMode == MyApplicationInterface.PhotoMode.NoiseReduction && applicationInterface.getNRModePref() == ApplicationInterface.NRModePref.NRMODE_LOW_LIGHT ) {
                        p.setColorFilter(new PorterDuffColorFilter(Color.rgb(255, 235, 59), PorterDuff.Mode.SRC_IN)); // Yellow 500
                    }
                    canvas.drawBitmap(bitmap, null, icon_dest, p);
                    p.setColorFilter(null);

                    if( device_ui_rotation == 180 ) {
                        location_x2 -= icon_size + flash_padding;
                    }
                    else {
                        location_x2 += icon_size + flash_padding;
                    }
                }
            }


            // photo-stamp is supported for photos taken in video mode
            // but it isn't supported in RAW-only mode
            if( has_stamp_pref && !( is_raw_only_pref && preview.supportsRaw() ) ) {
                icon_dest.set(location_x2, location_y, location_x2 + icon_size, location_y + icon_size);
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.BLACK);
                p.setAlpha(64);
                canvas.drawRect(icon_dest, p);
                p.setAlpha(255);
                canvas.drawBitmap(photostamp_bitmap, null, icon_dest, p);

                if( device_ui_rotation == 180 ) {
                    location_x2 -= icon_size + flash_padding;
                }
                else {
                    location_x2 += icon_size + flash_padding;
                }
            }

            if( !is_audio_enabled_pref && applicationInterface.isVideoPref() ) {
                icon_dest.set(location_x2, location_y, location_x2 + icon_size, location_y + icon_size);
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.BLACK);
                p.setAlpha(64);
                canvas.drawRect(icon_dest, p);
                p.setAlpha(255);
                canvas.drawBitmap(audio_disabled_bitmap, null, icon_dest, p);

                if( device_ui_rotation == 180 ) {
                    location_x2 -= icon_size + flash_padding;
                }
                else {
                    location_x2 += icon_size + flash_padding;
                }
            }

            // icons for slow motion, time lapse or high speed video
            if( Math.abs(capture_rate_factor - 1.0f) > 1.0e-5 && applicationInterface.isVideoPref() ) {
                icon_dest.set(location_x2, location_y, location_x2 + icon_size, location_y + icon_size);
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.BLACK);
                p.setAlpha(64);
                canvas.drawRect(icon_dest, p);
                p.setAlpha(255);
                canvas.drawBitmap(capture_rate_factor < 1.0f ? slow_motion_bitmap : time_lapse_bitmap, null, icon_dest, p);

                if( device_ui_rotation == 180 ) {
                    location_x2 -= icon_size + flash_padding;
                }
                else {
                    location_x2 += icon_size + flash_padding;
                }
            }
            else if( is_high_speed && applicationInterface.isVideoPref() ) {
                icon_dest.set(location_x2, location_y, location_x2 + icon_size, location_y + icon_size);
                p.setStyle(Paint.Style.FILL);
                p.setColor(Color.BLACK);
                p.setAlpha(64);
                canvas.drawRect(icon_dest, p);
                p.setAlpha(255);
                canvas.drawBitmap(high_speed_fps_bitmap, null, icon_dest, p);

                if( device_ui_rotation == 180 ) {
                    location_x2 -= icon_size + flash_padding;
                }
                else {
                    location_x2 += icon_size + flash_padding;
                }
            }

            if( time_ms > last_need_flash_indicator_time + 100 ) {
                need_flash_indicator = false;
                String flash_value = preview.getCurrentFlashValue();
                // note, flash_frontscreen_auto not yet support for the flash symbol (as camera_controller.needsFlash() only returns info on the built-in actual flash, not frontscreen flash)
                if( flash_value != null &&
                        ( flash_value.equals("flash_on")
                                || ( (flash_value.equals("flash_auto") || flash_value.equals("flash_red_eye")) && camera_controller.needsFlash() )
                                || camera_controller.needsFrontScreenFlash() ) &&
                        !applicationInterface.isVideoPref() ) { // flash-indicator not supported for photos taken in video mode
                    need_flash_indicator = true;
                }

                last_need_flash_indicator_time = time_ms;
            }
            if( need_flash_indicator ) {
                if( needs_flash_time != -1 ) {
                    final long fade_ms = 500;
                    float alpha = (time_ms - needs_flash_time)/(float)fade_ms;
                    if( time_ms - needs_flash_time >= fade_ms )
                        alpha = 1.0f;
                    icon_dest.set(location_x2, location_y, location_x2 + icon_size, location_y + icon_size);

					/*if( MyDebug.LOG )
						Log.d(TAG, "alpha: " + alpha);*/
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(Color.BLACK);
                    p.setAlpha((int)(64*alpha));
                    canvas.drawRect(icon_dest, p);
                    p.setAlpha((int)(255*alpha));
                    canvas.drawBitmap(flash_bitmap, null, icon_dest, p);
                    p.setAlpha(255);
                }
                else {
                    needs_flash_time = time_ms;
                }
            }
            else {
                needs_flash_time = -1;
            }

            if( device_ui_rotation == 90 ) {
                location_y -= icon_gap_y;
            }
            else {
                location_y += (icon_size+icon_gap_y);
            }
        }

        if( camera_controller != null ) {
            // draw histogram
            if( preview.isPreviewBitmapEnabled() ) {
                int [] histogram = preview.getHistogram();
                if( histogram != null ) {
					/*if( MyDebug.LOG )
						Log.d(TAG, "histogram length: " + histogram.length);*/
                    final int histogram_width = (int) (histogram_width_dp * scale + 0.5f); // convert dps to pixels
                    final int histogram_height = (int) (histogram_height_dp * scale + 0.5f); // convert dps to pixels
                    // n.b., if changing the histogram_height, remember to update focus_seekbar and
                    // focus_bracketing_target_seekbar margins in activity_main.xml
                    int location_x2 = location_x - flash_padding;
                    if( device_ui_rotation == 180 ) {
                        location_x2 = location_x - histogram_width + flash_padding;
                    }
                    icon_dest.set(location_x2 - flash_padding, location_y, location_x2 - flash_padding + histogram_width, location_y + histogram_height);
                    if( device_ui_rotation == 90 ) {
                        icon_dest.top -= histogram_height;
                        icon_dest.bottom -= histogram_height;
                    }

                    p.setStyle(Paint.Style.FILL);
                    p.setColor(Color.argb(64, 0, 0, 0));
                    canvas.drawRect(icon_dest, p);

                    int max = 0;
                    for(int value : histogram) {
                        max = Math.max(max, value);
                    }

                    if( histogram.length == 256*3 ) {
                        int c=0;

						/* For overlapping rgb, we'll have:
							(1, (1-a2).(1-a1).a0.r, (1-a2).a1.g, a2.b)
						   If we wanted to have the alpha scaling the same (i.e., same r, g, b values
						   if r=g=b, then this gives:
						       a2 = 1/[2+1/a0]
                               a1 = 1 - a2/[a0.(1-a2)]
                           However this then means that for non-overlapping colours, red is too
                           strong whilst blue is too weak, so we instead adjust to:
                               a0' = (a0+a1)/2
                               a1' = a1
                               a2' = (a1+a2)/2
						 */
						/*final int a0 = 255;
						final int a1 = 128;
						final int a2 = 85;*/
                        //final int a0 = 191;
                        final int a0 = 151;
                        final int a1 = 110;
                        //final int a2 = 77;
                        final int a2 = 94;
						/*final int a0 = 128;
						final int a1 = 85;
						final int a2 = 64;*/
                        final int r = 255;
                        final int g = 255;
                        final int b = 255;

                        for(int i=0;i<256;i++)
                            temp_histogram_channel[i] = histogram[c++];
                        p.setColor(Color.argb(a0, r, 0, 0));
                        drawHistogramChannel(canvas, temp_histogram_channel, max);

                        for(int i=0;i<256;i++)
                            temp_histogram_channel[i] = histogram[c++];
                        p.setColor(Color.argb(a1, 0, g, 0));
                        drawHistogramChannel(canvas, temp_histogram_channel, max);

                        for(int i=0;i<256;i++)
                            temp_histogram_channel[i] = histogram[c++];
                        p.setColor(Color.argb(a2, 0, 0, b));
                        drawHistogramChannel(canvas, temp_histogram_channel, max);
                    }
                    else {
                        p.setColor(Color.argb(192, 255, 255, 255));
                        drawHistogramChannel(canvas, histogram, max);
                    }
                }
            }
        }
    }

    /** Draws histogram for a single color channel.
     * @param canvas Canvas to draw onto.
     * @param histogram_channel The histogram for this color.
     * @param max The maximum value of histogram_channel, or if drawing multiple channels, this
     *            should be the maximum value of all histogram channels.
     */
    private void drawHistogramChannel(Canvas canvas, int [] histogram_channel, int max) {
        /*long debug_time = 0;
        if( MyDebug.LOG ) {
            debug_time = System.currentTimeMillis();
        }*/

		/*if( MyDebug.LOG )
			Log.d(TAG, "drawHistogramChannel, time before creating path: " + (System.currentTimeMillis() - debug_time));*/
        path.reset();
        path.moveTo(icon_dest.left, icon_dest.bottom);
        for(int c=0;c<histogram_channel.length;c++) {
            double c_alpha = c / (double)histogram_channel.length;
            int x = (int)(c_alpha * icon_dest.width());
            int h = (histogram_channel[c] * icon_dest.height()) / max;
            path.lineTo(icon_dest.left + x, icon_dest.bottom - h);
        }
        path.lineTo(icon_dest.right, icon_dest.bottom);
        path.close();
		/*if( MyDebug.LOG )
			Log.d(TAG, "drawHistogramChannel, time after creating path: " + (System.currentTimeMillis() - debug_time));*/
        canvas.drawPath(path, p);
		/*if( MyDebug.LOG )
			Log.d(TAG, "drawHistogramChannel, time before drawing path: " + (System.currentTimeMillis() - debug_time));*/
    }

    /** Formats the level_angle double into a string.
     *  Beware of calling this too often - shouldn't be every frame due to performance of DecimalFormat
     *  (see http://stackoverflow.com/questions/8553672/a-faster-alternative-to-decimalformat-format ).
     */
    public static String formatLevelAngle(double level_angle) {
        String number_string = decimalFormat.format(level_angle);
        if( Math.abs(level_angle) < 0.1 ) {
            // avoids displaying "-0.0", see http://stackoverflow.com/questions/11929096/negative-sign-in-case-of-zero-in-java
            // only do this when level_angle is small, to help performance
            number_string = number_string.replaceAll("^-(?=0(.0*)?$)", "");
        }
        return number_string;
    }

    /** This includes drawing of the UI that requires the canvas to be rotated according to the preview's
     *  current UI rotation.
     */
    private void drawUI(Canvas canvas, int device_ui_rotation, long time_ms) {
        Preview preview = main_activity.getPreview();
        CameraController camera_controller = preview.getCameraController();
        int ui_rotation = preview.getUIRotation();
        MainUI.UIPlacement ui_placement = main_activity.getMainUI().getUIPlacement();
        boolean has_level_angle = preview.hasLevelAngle();
        double level_angle = preview.getLevelAngle();
        boolean has_geo_direction = preview.hasGeoDirection();
        double geo_direction = preview.getGeoDirection();
        MainActivity.SystemOrientation system_orientation = main_activity.getSystemOrientation();
        boolean system_orientation_portrait = system_orientation == MainActivity.SystemOrientation.PORTRAIT;
        int text_base_y = 0;

        canvas.save();
        canvas.rotate(ui_rotation, canvas.getWidth()/2.0f, canvas.getHeight()/2.0f);

        if( camera_controller != null && !preview.isPreviewPaused() ) {
			/*canvas.drawText("PREVIEW", canvas.getWidth() / 2,
					canvas.getHeight() / 2, p);*/

            int gap_y = (int) (20 * scale + 0.5f); // convert dps to pixels
            int text_y = (int) (16 * scale + 0.5f); // convert dps to pixels
            boolean avoid_ui = false;
            // fine tuning to adjust placement of text with respect to the GUI, depending on orientation
            if( ui_placement == MainUI.UIPlacement.UIPLACEMENT_TOP && ( device_ui_rotation == 0 || device_ui_rotation == 180 ) ) {
                text_base_y = canvas.getHeight() - (int)(0.1*gap_y);
                avoid_ui = true;
            }
            else if( device_ui_rotation == ( ui_placement == MainUI.UIPlacement.UIPLACEMENT_RIGHT ? 0 : 180 ) ) {
                text_base_y = canvas.getHeight() - (int)(0.1*gap_y);
                avoid_ui = true;
            }
            else if( device_ui_rotation == ( ui_placement == MainUI.UIPlacement.UIPLACEMENT_RIGHT ? 180 : 0 ) ) {
                text_base_y = canvas.getHeight() - (int)(2.5*gap_y); // leave room for GUI icons
            }
            else if( device_ui_rotation == 90 || device_ui_rotation == 270 ) {
                // 90 is upside down portrait
                // 270 is portrait

                if( last_take_photo_top_time == 0 || time_ms > last_take_photo_top_time + 1000 ) {
                    /*if( MyDebug.LOG )
                        Log.d(TAG, "update cached take_photo_top");*/
                    // don't call this too often, for UI performance (due to calling View.getLocationOnScreen())
                    View view = main_activity.findViewById(R.id.take_photo);
                    // align with "top" of the take_photo button, but remember to take the rotation into account!
                    int view_left = getViewOnScreenX(view);
                    preview.getView().getLocationOnScreen(gui_location);
                    int this_left = gui_location[system_orientation_portrait ? 1 : 0];
                    take_photo_top = view_left - this_left;

                    last_take_photo_top_time = time_ms;
                    /*if( MyDebug.LOG ) {
                        Log.d(TAG, "device_ui_rotation: " + device_ui_rotation);
                        Log.d(TAG, "view_left: " + view_left);
                        Log.d(TAG, "this_left: " + this_left);
                        Log.d(TAG, "take_photo_top: " + take_photo_top);
                    }*/
                }

				// diff_x is the difference from the centre of the canvas to the position we want
                int max_x = system_orientation_portrait ? canvas.getHeight() : canvas.getWidth();
                int mid_x = max_x/2;
                int diff_x = take_photo_top - mid_x;

				/*if( MyDebug.LOG ) {
					Log.d(TAG, "view left: " + view_left);
					Log.d(TAG, "this left: " + this_left);
					Log.d(TAG, "canvas is " + canvas.getWidth() + " x " + canvas.getHeight());
                    Log.d(TAG, "compare offset_x: " + (preview.getView().getRootView().getRight()/2 - diff_x)/scale);
				}*/

                // diff_x is the difference from the centre of the canvas to the position we want
                // assumes canvas is centered
                // avoids calling getLocationOnScreen for performance
                /*int offset_x = (int) (124 * scale + 0.5f); // convert dps to pixels
                // offset_x should be enough such that on-screen level angle (this is the lowest display on-screen text) does not
                // interfere with take photo icon when using at least a 16:9 preview aspect ratio
                // should correspond to the logged "compare offset_x" above
                int diff_x = preview.getView().getRootView().getRight()/2 - offset_x;
                */

                if( device_ui_rotation == 90 ) {
                    // so we don't interfere with the top bar info (datetime, free memory, ISO) when upside down
                    max_x -= (int)(2.5*gap_y);
                }
				/*if( MyDebug.LOG ) {
					Log.d(TAG, "root view right: " + preview.getView().getRootView().getRight());
					Log.d(TAG, "diff_x: " + diff_x);
					Log.d(TAG, "canvas.getWidth()/2 + diff_x: " + (canvas.getWidth()/2+diff_x));
					Log.d(TAG, "max_x: " + max_x);
				}*/
                if( mid_x + diff_x > max_x ) {
                    // in case goes off the size of the canvas, for "black bar" cases (when preview aspect ratio < screen aspect ratio)
                    diff_x = max_x - mid_x;
                }
                text_base_y = canvas.getHeight()/2 + diff_x - (int)(0.5*gap_y);
            }

            if( avoid_ui ) {
                // avoid parts of the UI
                View view = main_activity.findViewById(R.id.focus_seekbar);
                if(view.getVisibility() == View.VISIBLE ) {
                    text_base_y -= view.getHeight();
                }
                view = main_activity.findViewById(R.id.focus_bracketing_target_seekbar);
                if(view.getVisibility() == View.VISIBLE ) {
                    text_base_y -= view.getHeight();
                }
                /*view = main_activity.findViewById(R.id.sliders_container);
                if(view.getVisibility() == View.VISIBLE ) {
                    text_base_y -= view.getHeight();
                }*/
            }

            boolean draw_angle = has_level_angle && show_angle_pref;
            boolean draw_geo_direction = has_geo_direction && show_geo_direction_pref;
            if( draw_angle ) {
                int color = Color.WHITE;
                p.setTextSize(14 * scale + 0.5f); // convert dps to pixels
                int pixels_offset_x;
                if( draw_geo_direction ) {
                    pixels_offset_x = - (int) (35 * scale + 0.5f); // convert dps to pixels
                    p.setTextAlign(Paint.Align.LEFT);
                }
                else {
                    //p.setTextAlign(Paint.Align.CENTER);
                    // slightly better for performance to use Align.LEFT, due to avoid measureText() call in drawTextWithBackground()
                    pixels_offset_x = - (int) ((level_angle<0 ? 16 : 14) * scale + 0.5f); // convert dps to pixels
                    p.setTextAlign(Paint.Align.LEFT);
                }
                if( Math.abs(level_angle) <= close_level_angle ) {
                    color = angle_highlight_color_pref;
                    p.setUnderlineText(true);
                }
                if( angle_string == null || time_ms > this.last_angle_string_time + 500 ) {
                    // update cached string
					/*if( MyDebug.LOG )
						Log.d(TAG, "update angle_string: " + angle_string);*/
                    last_angle_string_time = time_ms;
                    String number_string = formatLevelAngle(level_angle);
                    //String number_string = "" + level_angle;
                    angle_string = number_string + (char)0x00B0;
                    cached_angle = level_angle;
                    //String angle_string = "" + level_angle;
                }
                //applicationInterface.drawTextWithBackground(canvas, p, angle_string, color, Color.BLACK, canvas.getWidth() / 2 + pixels_offset_x, text_base_y, MyApplicationInterface.Alignment.ALIGNMENT_BOTTOM, ybounds_text, true);
                if( text_bounds_angle_single == null ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "compute text_bounds_angle_single");
                    text_bounds_angle_single = new Rect();
                    String bounds_angle_string = "-9.0" + (char)0x00B0;
                    p.getTextBounds(bounds_angle_string, 0, bounds_angle_string.length(), text_bounds_angle_single);
                }
                if( text_bounds_angle_double == null ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "compute text_bounds_angle_double");
                    text_bounds_angle_double = new Rect();
                    String bounds_angle_string = "-45.0" + (char)0x00B0;
                    p.getTextBounds(bounds_angle_string, 0, bounds_angle_string.length(), text_bounds_angle_double);
                }
                applicationInterface.drawTextWithBackground(canvas, p, angle_string, color, Color.BLACK, canvas.getWidth() / 2 + pixels_offset_x, text_base_y, MyApplicationInterface.Alignment.ALIGNMENT_BOTTOM, null, MyApplicationInterface.Shadow.SHADOW_OUTLINE, Math.abs(cached_angle) < 10.0 ? text_bounds_angle_single : text_bounds_angle_double);
                p.setUnderlineText(false);
            }
            if( draw_geo_direction ) {
                int color = Color.WHITE;
                p.setTextSize(14 * scale + 0.5f); // convert dps to pixels
                int pixels_offset_x;
                if( draw_angle ) {
                    pixels_offset_x = (int) (10 * scale + 0.5f); // convert dps to pixels
                    p.setTextAlign(Paint.Align.LEFT);
                }
                else {
                    //p.setTextAlign(Paint.Align.CENTER);
                    // slightly better for performance to use Align.LEFT, due to avoid measureText() call in drawTextWithBackground()
                    pixels_offset_x = - (int) (14 * scale + 0.5f); // convert dps to pixels
                    p.setTextAlign(Paint.Align.LEFT);
                }
                float geo_angle = (float)Math.toDegrees(geo_direction);
                if( geo_angle < 0.0f ) {
                    geo_angle += 360.0f;
                }
                String string = "" + Math.round(geo_angle) + (char)0x00B0;
                applicationInterface.drawTextWithBackground(canvas, p, string, color, Color.BLACK, canvas.getWidth() / 2 + pixels_offset_x, text_base_y, MyApplicationInterface.Alignment.ALIGNMENT_BOTTOM, ybounds_text, MyApplicationInterface.Shadow.SHADOW_OUTLINE);
            }
            if( preview.isOnTimer() ) {
                long remaining_time = (preview.getTimerEndTime() - time_ms + 999)/1000;
                if( MyDebug.LOG )
                    Log.d(TAG, "remaining_time: " + remaining_time);
                if( remaining_time > 0 ) {
                    p.setTextSize(42 * scale + 0.5f); // convert dps to pixels
                    p.setTextAlign(Paint.Align.CENTER);
                    String time_s;
                    if( remaining_time < 60 ) {
                        // simpler to just show seconds when less than a minute
                        time_s = "" + remaining_time;
                    }
                    else {
                        time_s = getTimeStringFromSeconds(remaining_time);
                    }
                    applicationInterface.drawTextWithBackground(canvas, p, time_s, Color.rgb(244, 67, 54), Color.BLACK, canvas.getWidth() / 2, canvas.getHeight() / 2); // Red 500
                }
            }
            else if( preview.isVideoRecording() ) {
                long video_time = preview.getVideoTime(false);
                String time_s = getTimeStringFromSeconds(video_time/1000);
            	/*if( MyDebug.LOG )
					Log.d(TAG, "video_time: " + video_time + " " + time_s);*/
                p.setTextSize(14 * scale + 0.5f); // convert dps to pixels
                p.setTextAlign(Paint.Align.CENTER);
                int pixels_offset_y = 2*text_y; // avoid overwriting the zoom
                int color = Color.rgb(244, 67, 54); // Red 500
                if( main_activity.isScreenLocked() ) {
                    // writing in reverse order, bottom to top
                    applicationInterface.drawTextWithBackground(canvas, p, getContext().getResources().getString(R.string.screen_lock_message_2), color, Color.BLACK, canvas.getWidth() / 2, text_base_y - pixels_offset_y);
                    pixels_offset_y += text_y;
                    applicationInterface.drawTextWithBackground(canvas, p, getContext().getResources().getString(R.string.screen_lock_message_1), color, Color.BLACK, canvas.getWidth() / 2, text_base_y - pixels_offset_y);
                    pixels_offset_y += text_y;
                }
                if( !preview.isVideoRecordingPaused() || ((int)(time_ms / 500)) % 2 == 0 ) { // if video is paused, then flash the video time
                    applicationInterface.drawTextWithBackground(canvas, p, time_s, color, Color.BLACK, canvas.getWidth() / 2, text_base_y - pixels_offset_y);
                    pixels_offset_y += text_y;
                }
                if( show_video_max_amp_pref && !preview.isVideoRecordingPaused() ) {
                    // audio amplitude
                    if( !this.has_video_max_amp || time_ms > this.last_video_max_amp_time + 50 ) {
                        has_video_max_amp = true;
                        int video_max_amp_prev1 = video_max_amp_prev2;
                        video_max_amp_prev2 = video_max_amp;
                        video_max_amp = preview.getMaxAmplitude();
                        last_video_max_amp_time = time_ms;
                        if( MyDebug.LOG ) {
                            if( video_max_amp > 30000 ) {
                                Log.d(TAG, "max_amp: " + video_max_amp);
                            }
                            if( video_max_amp > 32767 ) {
                                Log.e(TAG, "video_max_amp greater than max: " + video_max_amp);
                            }
                        }
                        if( video_max_amp_prev2 > video_max_amp_prev1 && video_max_amp_prev2 > video_max_amp ) {
                            // new peak
                            video_max_amp_peak = video_max_amp_prev2;
                        }
                        //video_max_amp_peak = Math.max(video_max_amp_peak, video_max_amp);
                    }
                    float amp_frac = video_max_amp/32767.0f;
                    amp_frac = Math.max(amp_frac, 0.0f);
                    amp_frac = Math.min(amp_frac, 1.0f);
                    //applicationInterface.drawTextWithBackground(canvas, p, "" + max_amp, color, Color.BLACK, canvas.getWidth() / 2, text_base_y - pixels_offset_y);

                    pixels_offset_y += text_y; // allow extra space
                    int amp_width = (int) (160 * scale + 0.5f); // convert dps to pixels
                    int amp_height = (int) (10 * scale + 0.5f); // convert dps to pixels
                    int amp_x = (canvas.getWidth() - amp_width)/2;
                    p.setColor(Color.WHITE);
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(stroke_width);
                    canvas.drawRect(amp_x, text_base_y - pixels_offset_y, amp_x+amp_width, text_base_y - pixels_offset_y+amp_height, p);
                    p.setStyle(Paint.Style.FILL);
                    canvas.drawRect(amp_x, text_base_y - pixels_offset_y, amp_x+amp_frac*amp_width, text_base_y - pixels_offset_y+amp_height, p);
                    if( amp_frac < 1.0f ) {
                        p.setColor(Color.BLACK);
                        p.setAlpha(64);
                        canvas.drawRect(amp_x+amp_frac*amp_width+1, text_base_y - pixels_offset_y, amp_x+amp_width, text_base_y - pixels_offset_y+amp_height, p);
                        p.setAlpha(255);
                    }
                    if( video_max_amp_peak > video_max_amp ) {
                        float peak_frac = video_max_amp_peak/32767.0f;
                        peak_frac = Math.max(peak_frac, 0.0f);
                        peak_frac = Math.min(peak_frac, 1.0f);
                        p.setColor(Color.YELLOW);
                        p.setStyle(Paint.Style.STROKE);
                        p.setStrokeWidth(stroke_width);
                        canvas.drawLine(amp_x+peak_frac*amp_width, text_base_y - pixels_offset_y, amp_x+peak_frac*amp_width, text_base_y - pixels_offset_y+amp_height, p);
                        p.setColor(Color.WHITE);
                    }
                }
            }
            else if( taking_picture && capture_started ) {
                if( camera_controller.isCapturingBurst() ) {
                    int n_burst_taken = camera_controller.getNBurstTaken() + 1;
                    int n_burst_total = camera_controller.getBurstTotal();
                    p.setTextSize(14 * scale + 0.5f); // convert dps to pixels
                    p.setTextAlign(Paint.Align.CENTER);
                    int pixels_offset_y = 2*text_y; // avoid overwriting the zoom
                    if( device_ui_rotation == 0 && applicationInterface.getPhotoMode() == MyApplicationInterface.PhotoMode.FocusBracketing ) {
                        // avoid clashing with the target focus bracketing seekbar in landscape orientation
                        pixels_offset_y = 5*gap_y;
                    }
                    String text = getContext().getResources().getString(R.string.capturing) + " " + n_burst_taken;
                    if( n_burst_total > 0 ) {
                        text += " / " + n_burst_total;
                    }
                    applicationInterface.drawTextWithBackground(canvas, p, text, Color.WHITE, Color.BLACK, canvas.getWidth() / 2, text_base_y - pixels_offset_y);
                }
                else if( camera_controller.isManualISO() ) {
                    // only show "capturing" text with time for manual exposure time >= 0.5s
                    long exposure_time = camera_controller.getExposureTime();
                    if( exposure_time >= 500000000L ) {
                        if( ((int)(time_ms / 500)) % 2 == 0 ) {
                            p.setTextSize(14 * scale + 0.5f); // convert dps to pixels
                            p.setTextAlign(Paint.Align.CENTER);
                            int pixels_offset_y = 2*text_y; // avoid overwriting the zoom
                            int color = Color.rgb(244, 67, 54); // Red 500
                            applicationInterface.drawTextWithBackground(canvas, p, getContext().getResources().getString(R.string.capturing), color, Color.BLACK, canvas.getWidth() / 2, text_base_y - pixels_offset_y);
                        }
                    }
                }
            }
            else if( image_queue_full ) {
                if( ((int)(time_ms / 500)) % 2 == 0 ) {
                    p.setTextSize(14 * scale + 0.5f); // convert dps to pixels
                    p.setTextAlign(Paint.Align.CENTER);
                    int pixels_offset_y = 2 * text_y; // avoid overwriting the zoom
                    int n_images_to_save = applicationInterface.getImageSaver().getNRealImagesToSave();
                    String string = getContext().getResources().getString(R.string.processing) + " (" + n_images_to_save + " " + getContext().getResources().getString(R.string.remaining) + ")";
                    applicationInterface.drawTextWithBackground(canvas, p, string, Color.LTGRAY, Color.BLACK, canvas.getWidth() / 2, text_base_y - pixels_offset_y);
                }
            }

            if( preview.supportsZoom() && show_zoom_pref ) {
                float zoom_ratio = preview.getZoomRatio();
                // only show when actually zoomed in
                if( zoom_ratio > 1.0f + 1.0e-5f ) {
                    // Convert the dps to pixels, based on density scale
                    p.setTextSize(14 * scale + 0.5f); // convert dps to pixels
                    p.setTextAlign(Paint.Align.CENTER);
                    applicationInterface.drawTextWithBackground(canvas, p, getContext().getResources().getString(R.string.zoom) + ": " + zoom_ratio +"x", Color.WHITE, Color.BLACK, canvas.getWidth() / 2, text_base_y - text_y, MyApplicationInterface.Alignment.ALIGNMENT_BOTTOM, ybounds_text, MyApplicationInterface.Shadow.SHADOW_OUTLINE);
                }
            }

        }
        else if( camera_controller == null ) {
			/*if( MyDebug.LOG ) {
				Log.d(TAG, "no camera!");
				Log.d(TAG, "width " + canvas.getWidth() + " height " + canvas.getHeight());
			}*/
            p.setColor(Color.WHITE);
            p.setTextSize(14 * scale + 0.5f); // convert dps to pixels
            p.setTextAlign(Paint.Align.CENTER);
            int pixels_offset = (int) (20 * scale + 0.5f); // convert dps to pixels
            if( preview.hasPermissions() ) {
                if( preview.openCameraFailed() ) {
                    canvas.drawText(getContext().getResources().getString(R.string.failed_to_open_camera_1), canvas.getWidth() / 2.0f, canvas.getHeight() / 2.0f, p);
                    canvas.drawText(getContext().getResources().getString(R.string.failed_to_open_camera_2), canvas.getWidth() / 2.0f, canvas.getHeight() / 2.0f + pixels_offset, p);
                    canvas.drawText(getContext().getResources().getString(R.string.failed_to_open_camera_3), canvas.getWidth() / 2.0f, canvas.getHeight() / 2.0f + 2 * pixels_offset, p);
                    // n.b., use applicationInterface.getCameraIdPref(), as preview.getCameraId() returns 0 if camera_controller==null
                    canvas.drawText(getContext().getResources().getString(R.string.camera_id) + ":" + applicationInterface.getCameraIdPref(), canvas.getWidth() / 2.0f, canvas.getHeight() / 2.0f + 3 * pixels_offset, p);
                }
            }
            else {
                canvas.drawText(getContext().getResources().getString(R.string.no_permission), canvas.getWidth() / 2.0f, canvas.getHeight() / 2.0f, p);
            }
            //canvas.drawRect(0.0f, 0.0f, 100.0f, 100.0f, p);
            //canvas.drawRGB(255, 0, 0);
            //canvas.drawRect(0.0f, 0.0f, canvas.getWidth(), canvas.getHeight(), p);
        }

        int top_x = (int) (5 * scale + 0.5f); // convert dps to pixels
        int top_y = (int) (5 * scale + 0.5f); // convert dps to pixels
        View top_icon = main_activity.getMainUI().getTopIcon();
        if( top_icon != null ) {
            if( last_top_icon_shift_time == 0 || time_ms > last_top_icon_shift_time + 1000 ) {
                // avoid computing every time, due to cost of calling View.getLocationOnScreen()
                /*if( MyDebug.LOG )
                    Log.d(TAG, "update cached top_icon_shift");*/
                int top_margin = getViewOnScreenX(top_icon);
                if( system_orientation == MainActivity.SystemOrientation.LANDSCAPE )
                    top_margin += top_icon.getWidth();
                else if( system_orientation == MainActivity.SystemOrientation.PORTRAIT )
                    top_margin += top_icon.getHeight();
                // n.b., don't adjust top_margin for icon width/height for an reverse orientation
                preview.getView().getLocationOnScreen(gui_location);
                int preview_left = gui_location[system_orientation_portrait ? 1 : 0];
                if( system_orientation == MainActivity.SystemOrientation.REVERSE_LANDSCAPE )
                    preview_left += preview.getView().getWidth(); // actually want preview-right for reverse landscape
                this.top_icon_shift = top_margin - preview_left;
                if( system_orientation == MainActivity.SystemOrientation.REVERSE_LANDSCAPE )
                    this.top_icon_shift = -this.top_icon_shift;
                /*if( MyDebug.LOG ) {
                    Log.d(TAG, "top_icon.getRotation(): " + top_icon.getRotation());
                    Log.d(TAG, "preview_left: " + preview_left);
                    Log.d(TAG, "top_margin: " + top_margin);
                    Log.d(TAG, "top_icon_shift: " + top_icon_shift);
                }*/

                last_top_icon_shift_time = time_ms;
            }

            if( this.top_icon_shift > 0 ) {
                if( device_ui_rotation == 90 || device_ui_rotation == 270 ) {
                    // portrait
                    top_y += top_icon_shift;
                }
                else {
                    // landscape
                    top_x += top_icon_shift;
                }
            }
        }

        {
            /*int focus_seekbars_margin_left_dp = 85;
            if( want_histogram )
                focus_seekbars_margin_left_dp += DrawPreview.histogram_height_dp;*/
            // 135 needed to make room for on-screen info lines in DrawPreview.onDrawInfoLines(), including the histogram
            // but we also need to take the top_icon_shift into account, for widescreen aspect ratios and "icons along top" UI placement
            int focus_seekbars_margin_left_dp = 135;
            int new_focus_seekbars_margin_left = (int) (focus_seekbars_margin_left_dp * scale + 0.5f); // convert dps to pixels
            if( top_icon_shift > 0 ) {
                //noinspection SuspiciousNameCombination
                new_focus_seekbars_margin_left += top_icon_shift;
            }

            if( focus_seekbars_margin_left == -1 || new_focus_seekbars_margin_left != focus_seekbars_margin_left ) {
                // we check whether focus_seekbars_margin_left has changed, in case there is a performance cost for setting layoutparams
                this.focus_seekbars_margin_left = new_focus_seekbars_margin_left;
                if( MyDebug.LOG )
                    Log.d(TAG, "set focus_seekbars_margin_left to " + focus_seekbars_margin_left);

                // "left" and "right" here are written assuming we're in landscape system orientation

                View view = main_activity.findViewById(R.id.focus_seekbar);
                RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
                preview.getView().getLocationOnScreen(gui_location);
                int preview_left = gui_location[system_orientation_portrait ? 1 : 0];
                if( system_orientation == MainActivity.SystemOrientation.REVERSE_LANDSCAPE )
                    preview_left += preview.getView().getWidth(); // actually want preview-right for reverse landscape

                view.getLocationOnScreen(gui_location);
                int seekbar_right = gui_location[system_orientation_portrait ? 1 : 0];
                if( system_orientation == MainActivity.SystemOrientation.LANDSCAPE || system_orientation == MainActivity.SystemOrientation.PORTRAIT ) {
                    // n.b., we read view.getWidth() even if system_orientation is portrait, because the seekbar is rotated in portrait orientation
                    seekbar_right += view.getWidth();
                }
                else {
                    // and for reversed landscape, the seekbar is rotated 180 degrees, and getLocationOnScreen() returns the location after the rotation
                    seekbar_right -= view.getWidth();
                }

                int min_seekbar_width = (int) (150 * scale + 0.5f); // convert dps to pixels
                int new_seekbar_width;
                if( system_orientation == MainActivity.SystemOrientation.LANDSCAPE || system_orientation == MainActivity.SystemOrientation.PORTRAIT ) {
                    new_seekbar_width = seekbar_right - (preview_left+focus_seekbars_margin_left);
                }
                else {
                    // reversed landscape
                    new_seekbar_width = preview_left - focus_seekbars_margin_left - seekbar_right;
                }
                new_seekbar_width = Math.max(new_seekbar_width, min_seekbar_width);
                /*if( MyDebug.LOG ) {
                    Log.d(TAG, "preview_left: " + preview_left);
                    Log.d(TAG, "seekbar_right: " + seekbar_right);
                    Log.d(TAG, "new_seekbar_width: " + new_seekbar_width);
                }*/
                layoutParams.width = new_seekbar_width;
                view.setLayoutParams(layoutParams);

                view = main_activity.findViewById(R.id.focus_bracketing_target_seekbar);
                layoutParams = (RelativeLayout.LayoutParams)view.getLayoutParams();
                layoutParams.width = new_seekbar_width;
                view.setLayoutParams(layoutParams);

                // need to update due to changing width of focus seekbars
                main_activity.getMainUI().setFocusSeekbarsRotation();
            }
        }

        int battery_x = top_x;
        int battery_y = top_y + (int) (5 * scale + 0.5f);
        int battery_width = (int) (5 * scale + 0.5f); // convert dps to pixels
        int battery_height = 4*battery_width;
        if( ui_rotation == 90 || ui_rotation == 270 ) {
            // n.b., this is only for when lock_to_landscape==true, so we don't look at device_ui_rotation
            int diff = canvas.getWidth() - canvas.getHeight();
            battery_x += diff/2;
            battery_y -= diff/2;
        }
        if( device_ui_rotation == 90 ) {
            battery_y = canvas.getHeight() - battery_y - battery_height;
        }
        if( device_ui_rotation == 180 ) {
            battery_x = canvas.getWidth() - battery_x - battery_width;
        }
        if( show_battery_pref ) {
            if( !this.has_battery_frac || time_ms > this.last_battery_time + 60000 ) {
                // only check periodically - unclear if checking is costly in any way
                // note that it's fine to call registerReceiver repeatedly - we pass a null receiver, so this is fine as a "one shot" use
                Intent batteryStatus = main_activity.registerReceiver(null, battery_ifilter);
                int battery_level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int battery_scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                has_battery_frac = true;
                battery_frac = battery_level/(float)battery_scale;
                last_battery_time = time_ms;
                if( MyDebug.LOG )
                    Log.d(TAG, "Battery status is " + battery_level + " / " + battery_scale + " : " + battery_frac);
            }
            //battery_frac = 0.2999f; // test
            boolean draw_battery = true;
            if( battery_frac <= 0.05f ) {
                // flash icon at this low level
                draw_battery = ((( time_ms / 1000 )) % 2) == 0;
            }
            if( draw_battery ) {
                p.setColor(battery_frac > 0.15f ? Color.rgb(37, 155, 36) : Color.rgb(244, 67, 54)); // Green 500 or Red 500
                p.setStyle(Paint.Style.FILL);
                canvas.drawRect(battery_x, battery_y+(1.0f-battery_frac)*(battery_height-2), battery_x+battery_width, battery_y+battery_height, p);
                if( battery_frac < 1.0f ) {
                    p.setColor(Color.BLACK);
                    p.setAlpha(64);
                    canvas.drawRect(battery_x, battery_y, battery_x + battery_width, battery_y + (1.0f - battery_frac) * (battery_height - 2), p);
                    p.setAlpha(255);
                }
            }
            top_x += (int) (10 * scale + 0.5f); // convert dps to pixels
        }

        onDrawInfoLines(canvas, top_x, top_y, text_base_y, device_ui_rotation, time_ms);

        canvas.restore();
    }

    private void drawAngleLines(Canvas canvas, int device_ui_rotation, long time_ms) {
        Preview preview = main_activity.getPreview();
        CameraController camera_controller = preview.getCameraController();
        MainActivity.SystemOrientation system_orientation = main_activity.getSystemOrientation();
        boolean system_orientation_portrait = system_orientation == MainActivity.SystemOrientation.PORTRAIT;
        boolean has_level_angle = preview.hasLevelAngle();
        boolean actual_show_angle_line_pref;
        if( photoMode == MyApplicationInterface.PhotoMode.Panorama ) {
            // in panorama mode, we should the level iff we aren't taking the panorama photos
            actual_show_angle_line_pref = !main_activity.getApplicationInterface().getGyroSensor().isRecording();
        }
        else
            actual_show_angle_line_pref = show_angle_line_pref;

        boolean allow_angle_lines = camera_controller != null && !preview.isPreviewPaused();

        if( allow_angle_lines && has_level_angle && ( actual_show_angle_line_pref || show_pitch_lines_pref || show_geo_direction_lines_pref ) ) {
            double level_angle = preview.getLevelAngle();
            boolean has_pitch_angle = preview.hasPitchAngle();
            double pitch_angle = preview.getPitchAngle();
            boolean has_geo_direction = preview.hasGeoDirection();
            double geo_direction = preview.getGeoDirection();
            // n.b., must draw this without the standard canvas rotation
            // lines should be shorter in portrait
            int radius_dps = (device_ui_rotation == 90 || device_ui_rotation == 270) ? 60 : 80;
            int radius = (int) (radius_dps * scale + 0.5f); // convert dps to pixels
            double angle = - preview.getOrigLevelAngle();
            // see http://android-developers.blogspot.co.uk/2010/09/one-screen-turn-deserves-another.html
            int rotation = main_activity.getDisplayRotation();
            switch (rotation) {
                case Surface.ROTATION_90:
                    angle -= 90.0;
                    break;
                case Surface.ROTATION_270:
                    angle += 90.0;
                    break;
                case Surface.ROTATION_180:
                    angle += 180.0;
                    break;
                case Surface.ROTATION_0:
                default:
                    break;
            }
			/*if( MyDebug.LOG ) {
                Log.d(TAG, "system_orientation: " + system_orientation);
                Log.d(TAG, "rotation: " + rotation);
            }*/
			/*if( MyDebug.LOG ) {
				Log.d(TAG, "orig_level_angle: " + preview.getOrigLevelAngle());
				Log.d(TAG, "angle: " + angle);
			}*/
            int cx = canvas.getWidth()/2;
            int cy = canvas.getHeight()/2;

            boolean is_level = false;
            if( has_level_angle && Math.abs(level_angle) <= close_level_angle ) { // n.b., use level_angle, not angle or orig_level_angle
                is_level = true;
            }

            if( is_level ) {
                radius = (int)(radius * 1.2);
            }

            canvas.save();
            canvas.rotate((float)angle, cx, cy);

            final int line_alpha = 160;
            float hthickness = (0.5f * scale + 0.5f); // convert dps to pixels
            p.setStyle(Paint.Style.FILL);
            if( actual_show_angle_line_pref && preview.hasLevelAngleStable() ) {
                // only show the angle line if level angle "stable" (i.e., not pointing near vertically up or down)
                // draw outline
                p.setColor(Color.BLACK);
                p.setAlpha(64);
                // can't use drawRoundRect(left, top, right, bottom, ...) as that requires API 21
                draw_rect.set(cx - radius - hthickness, cy - 2 * hthickness, cx + radius + hthickness, cy + 2 * hthickness);
                canvas.drawRoundRect(draw_rect, 2 * hthickness, 2 * hthickness, p);
                // draw the vertical crossbar
                draw_rect.set(cx - 2 * hthickness, cy - radius / 2.0f - hthickness, cx + 2 * hthickness, cy + radius / 2.0f + hthickness);
                canvas.drawRoundRect(draw_rect, hthickness, hthickness, p);
                // draw inner portion
                if( is_level ) {
                    p.setColor(angle_highlight_color_pref);
                }
                else {
                    p.setColor(Color.WHITE);
                }
                p.setAlpha(line_alpha);
                draw_rect.set(cx - radius, cy - hthickness, cx + radius, cy + hthickness);
                canvas.drawRoundRect(draw_rect, hthickness, hthickness, p);

                // draw the vertical crossbar
                draw_rect.set(cx - hthickness, cy - radius / 2.0f, cx + hthickness, cy + radius / 2.0f);
                canvas.drawRoundRect(draw_rect, hthickness, hthickness, p);

                if( is_level ) {
                    // draw a second line

                    p.setColor(Color.BLACK);
                    p.setAlpha(64);
                    draw_rect.set(cx - radius - hthickness, cy - 7 * hthickness, cx + radius + hthickness, cy - 3 * hthickness);
                    canvas.drawRoundRect(draw_rect, 2 * hthickness, 2 * hthickness, p);

                    p.setColor(angle_highlight_color_pref);
                    p.setAlpha(line_alpha);
                    draw_rect.set(cx - radius, cy - 6 * hthickness, cx + radius, cy - 4 * hthickness);
                    canvas.drawRoundRect(draw_rect, hthickness, hthickness, p);
                }
            }
            updateCachedViewAngles(time_ms); // ensure view_angle_x_preview, view_angle_y_preview are computed and up to date
            float camera_angle_x, camera_angle_y;
            if( system_orientation_portrait ) {
                camera_angle_x = this.view_angle_y_preview;
                camera_angle_y = this.view_angle_x_preview;
            }
            else {
                camera_angle_x = this.view_angle_x_preview;
                camera_angle_y = this.view_angle_y_preview;
            }
            float angle_scale_x = (float)( canvas.getWidth() / (2.0 * Math.tan( Math.toRadians((camera_angle_x/2.0)) )) );
            float angle_scale_y = (float)( canvas.getHeight() / (2.0 * Math.tan( Math.toRadians((camera_angle_y/2.0)) )) );
			/*if( MyDebug.LOG ) {
				Log.d(TAG, "camera_angle_x: " + camera_angle_x);
				Log.d(TAG, "camera_angle_y: " + camera_angle_y);
				Log.d(TAG, "angle_scale_x: " + angle_scale_x);
				Log.d(TAG, "angle_scale_y: " + angle_scale_y);
				Log.d(TAG, "angle_scale_x/scale: " + angle_scale_x/scale);
				Log.d(TAG, "angle_scale_y/scale: " + angle_scale_y/scale);
			}*/
			/*if( MyDebug.LOG ) {
				Log.d(TAG, "has_pitch_angle?: " + has_pitch_angle);
				Log.d(TAG, "show_pitch_lines?: " + show_pitch_lines);
			}*/
            float angle_scale = (float)Math.sqrt( angle_scale_x*angle_scale_x + angle_scale_y*angle_scale_y );
            angle_scale *= preview.getZoomRatio();
            if( has_pitch_angle && show_pitch_lines_pref ) {
                // lines should be shorter in portrait
                int pitch_radius_dps = (device_ui_rotation == 90 || device_ui_rotation == 270) ? 80 : 100;
                int pitch_radius = (int) (pitch_radius_dps * scale + 0.5f); // convert dps to pixels
                int angle_step = 10;
                if( preview.getZoomRatio() >= 2.0f )
                    angle_step = 5;
                for(int latitude_angle=-90;latitude_angle<=90;latitude_angle+=angle_step) {
                    double this_angle = pitch_angle - latitude_angle;
                    if( Math.abs(this_angle) < 90.0 ) {
                        float pitch_distance = angle_scale * (float)Math.tan( Math.toRadians(this_angle) ); // angle_scale is already in pixels rather than dps
						/*if( MyDebug.LOG ) {
							Log.d(TAG, "pitch_angle: " + pitch_angle);
							Log.d(TAG, "pitch_distance_dp: " + pitch_distance_dp);
						}*/
                        // draw outline
                        p.setColor(Color.BLACK);
                        p.setAlpha(64);
                        // can't use drawRoundRect(left, top, right, bottom, ...) as that requires API 21
                        draw_rect.set(cx - pitch_radius - hthickness, cy + pitch_distance - 2*hthickness, cx + pitch_radius + hthickness, cy + pitch_distance + 2*hthickness);
                        canvas.drawRoundRect(draw_rect, 2*hthickness, 2*hthickness, p);
                        // draw inner portion
                        p.setColor(Color.WHITE);
                        p.setTextAlign(Paint.Align.LEFT);
                        if( latitude_angle == 0 && Math.abs(pitch_angle) < 1.0 ) {
                            p.setAlpha(255);
                        }
                        else if( latitude_angle == 90 && Math.abs(pitch_angle - 90) < 3.0 ) {
                            p.setAlpha(255);
                        }
                        else if( latitude_angle == -90 && Math.abs(pitch_angle + 90) < 3.0 ) {
                            p.setAlpha(255);
                        }
                        else {
                            p.setAlpha(line_alpha);
                        }
                        draw_rect.set(cx - pitch_radius, cy + pitch_distance - hthickness, cx + pitch_radius, cy + pitch_distance + hthickness);
                        canvas.drawRoundRect(draw_rect, hthickness, hthickness, p);
                        // draw pitch angle indicator
                        applicationInterface.drawTextWithBackground(canvas, p, "" + latitude_angle + "\u00B0", p.getColor(), Color.BLACK, (int)(cx + pitch_radius + 4*hthickness), (int)(cy + pitch_distance - 2*hthickness), MyApplicationInterface.Alignment.ALIGNMENT_CENTRE);
                    }
                }
            }
            if( has_geo_direction && has_pitch_angle && show_geo_direction_lines_pref ) {
                // lines should be longer in portrait - n.b., this is opposite to behaviour of pitch lines, as
                // geo lines are drawn perpendicularly
                int geo_radius_dps = (device_ui_rotation == 90 || device_ui_rotation == 270) ? 100 : 80;
                int geo_radius = (int) (geo_radius_dps * scale + 0.5f); // convert dps to pixels
                float geo_angle = (float)Math.toDegrees(geo_direction);
                int angle_step = 10;
                if( preview.getZoomRatio() >= 2.0f )
                    angle_step = 5;
                for(int longitude_angle=0;longitude_angle<360;longitude_angle+=angle_step) {
                    double this_angle = longitude_angle - geo_angle;
					/*if( MyDebug.LOG ) {
						Log.d(TAG, "longitude_angle: " + longitude_angle);
						Log.d(TAG, "geo_angle: " + geo_angle);
						Log.d(TAG, "this_angle: " + this_angle);
					}*/
                    // normalise to be in interval [0, 360)
                    while( this_angle >= 360.0 )
                        this_angle -= 360.0;
                    while( this_angle < -360.0 )
                        this_angle += 360.0;
                    // pick shortest angle
                    if( this_angle > 180.0 )
                        this_angle = - (360.0 - this_angle);
                    if( Math.abs(this_angle) < 90.0 ) {
						/*if( MyDebug.LOG ) {
							Log.d(TAG, "this_angle is now: " + this_angle);
						}*/
                        float geo_distance = angle_scale * (float)Math.tan( Math.toRadians(this_angle) ); // angle_scale is already in pixels rather than dps
                        // draw outline
                        p.setColor(Color.BLACK);
                        p.setAlpha(64);
                        // can't use drawRoundRect(left, top, right, bottom, ...) as that requires API 21
                        draw_rect.set(cx + geo_distance - 2*hthickness, cy - geo_radius - hthickness, cx + geo_distance + 2*hthickness, cy + geo_radius + hthickness);
                        canvas.drawRoundRect(draw_rect, 2*hthickness, 2*hthickness, p);
                        // draw inner portion
                        p.setColor(Color.WHITE);
                        p.setTextAlign(Paint.Align.CENTER);
                        p.setAlpha(line_alpha);
                        draw_rect.set(cx + geo_distance - hthickness, cy - geo_radius, cx + geo_distance + hthickness, cy + geo_radius);
                        canvas.drawRoundRect(draw_rect, hthickness, hthickness, p);
                        // draw geo direction angle indicator
                        applicationInterface.drawTextWithBackground(canvas, p, "" + longitude_angle + "\u00B0", p.getColor(), Color.BLACK, (int)(cx + geo_distance), (int)(cy - geo_radius - 4*hthickness), MyApplicationInterface.Alignment.ALIGNMENT_BOTTOM);
                    }
                }
            }

            p.setAlpha(255);
            p.setStyle(Paint.Style.FILL); // reset

            canvas.restore();
        }

        if( allow_angle_lines && auto_stabilise_pref && preview.hasLevelAngleStable() && !preview.isVideo() ) {
            // although auto-level is supported for photos taken in video mode, there's the risk that it's misleading to display
            // the guide when in video mode!
            double level_angle = preview.getLevelAngle();
            double auto_stabilise_level_angle = level_angle;
            //double auto_stabilise_level_angle = angle;
            while( auto_stabilise_level_angle < -90 )
                auto_stabilise_level_angle += 180;
            while( auto_stabilise_level_angle > 90 )
                auto_stabilise_level_angle -= 180;
            double level_angle_rad_abs = Math.abs( Math.toRadians(auto_stabilise_level_angle) );

            int w1 = canvas.getWidth();
            int h1 = canvas.getHeight();
            double w0 = (w1 * Math.cos(level_angle_rad_abs) + h1 * Math.sin(level_angle_rad_abs));
            double h0 = (w1 * Math.sin(level_angle_rad_abs) + h1 * Math.cos(level_angle_rad_abs));

            if( ImageSaver.autoStabiliseCrop(auto_stabilise_crop, level_angle_rad_abs, w0, h0, w1, h1, canvas.getWidth(), canvas.getHeight()) ) {
                int w2 = auto_stabilise_crop[0];
                int h2 = auto_stabilise_crop[1];
                int cx = canvas.getWidth()/2;
                int cy = canvas.getHeight()/2;

                canvas.save();
                canvas.rotate((float)-level_angle, cx, cy);

                if( has_level_angle && Math.abs(level_angle) <= close_level_angle ) { // n.b., use level_angle, not angle or orig_level_angle
                    p.setColor(angle_highlight_color_pref);
                }
                else {
                    p.setColor(Color.WHITE);
                }
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(stroke_width);

                canvas.drawRect((canvas.getWidth() - w2)/2.0f, (canvas.getHeight() - h2)/2.0f, (canvas.getWidth() + w2)/2.0f, (canvas.getHeight() + h2)/2.0f, p);

                canvas.restore();

                p.setStyle(Paint.Style.FILL); // reset
            }
        }
    }

    private void doThumbnailAnimation(Canvas canvas, long time_ms) {
        Preview preview  = main_activity.getPreview();
        CameraController camera_controller = preview.getCameraController();
        // note, no need to check preferences here, as we do that when setting thumbnail_anim
        if( camera_controller != null && this.thumbnail_anim && last_thumbnail != null ) {
            int ui_rotation = preview.getUIRotation();
            long time = time_ms - this.thumbnail_anim_start_ms;
            final long duration = 500;
            if( time > duration ) {
                if( MyDebug.LOG )
                    Log.d(TAG, "thumbnail_anim finished");
                this.thumbnail_anim = false;
            }
            else {
                thumbnail_anim_src_rect.left = 0;
                thumbnail_anim_src_rect.top = 0;
                thumbnail_anim_src_rect.right = last_thumbnail.getWidth();
                thumbnail_anim_src_rect.bottom = last_thumbnail.getHeight();
                View galleryButton = main_activity.findViewById(R.id.gallery);
                float alpha = ((float)time)/(float)duration;

                int st_x = canvas.getWidth()/2;
                int st_y = canvas.getHeight()/2;
                int nd_x = galleryButton.getLeft() + galleryButton.getWidth()/2;
                int nd_y = galleryButton.getTop() + galleryButton.getHeight()/2;
                int thumbnail_x = (int)( (1.0f-alpha)*st_x + alpha*nd_x );
                int thumbnail_y = (int)( (1.0f-alpha)*st_y + alpha*nd_y );

                float st_w = canvas.getWidth();
                float st_h = canvas.getHeight();
                float nd_w = galleryButton.getWidth();
                float nd_h = galleryButton.getHeight();
                //int thumbnail_w = (int)( (1.0f-alpha)*st_w + alpha*nd_w );
                //int thumbnail_h = (int)( (1.0f-alpha)*st_h + alpha*nd_h );
                float correction_w = st_w/nd_w - 1.0f;
                float correction_h = st_h/nd_h - 1.0f;
                int thumbnail_w = (int)(st_w/(1.0f+alpha*correction_w));
                int thumbnail_h = (int)(st_h/(1.0f+alpha*correction_h));
                thumbnail_anim_dst_rect.left = thumbnail_x - thumbnail_w/2.0f;
                thumbnail_anim_dst_rect.top = thumbnail_y - thumbnail_h/2.0f;
                thumbnail_anim_dst_rect.right = thumbnail_x + thumbnail_w/2.0f;
                thumbnail_anim_dst_rect.bottom = thumbnail_y + thumbnail_h/2.0f;
                //canvas.drawBitmap(this.thumbnail, thumbnail_anim_src_rect, thumbnail_anim_dst_rect, p);
                thumbnail_anim_matrix.setRectToRect(thumbnail_anim_src_rect, thumbnail_anim_dst_rect, Matrix.ScaleToFit.FILL);
                //thumbnail_anim_matrix.reset();
                if( ui_rotation == 90 || ui_rotation == 270 ) {
                    float ratio = ((float)last_thumbnail.getWidth())/(float)last_thumbnail.getHeight();
                    thumbnail_anim_matrix.preScale(ratio, 1.0f/ratio, last_thumbnail.getWidth()/2.0f, last_thumbnail.getHeight()/2.0f);
                }
                thumbnail_anim_matrix.preRotate(ui_rotation, last_thumbnail.getWidth()/2.0f, last_thumbnail.getHeight()/2.0f);
                canvas.drawBitmap(last_thumbnail, thumbnail_anim_matrix, p);
            }
        }
    }

    private void doFocusAnimation(Canvas canvas, long time_ms) {
        Preview preview = main_activity.getPreview();
        CameraController camera_controller = preview.getCameraController();
        if( camera_controller != null && continuous_focus_moving && !taking_picture ) {
            // we don't display the continuous focusing animation when taking a photo - and can also give the impression of having
            // frozen if we pause because the image saver queue is full
            long dt = time_ms - continuous_focus_moving_ms;
            final long length = 1000;
			/*if( MyDebug.LOG )
				Log.d(TAG, "continuous focus moving, dt: " + dt);*/
            if( dt <= length ) {
                float frac = ((float)dt) / (float)length;
                float pos_x = canvas.getWidth()/2.0f;
                float pos_y = canvas.getHeight()/2.0f;
                float min_radius = (40 * scale + 0.5f); // convert dps to pixels
                float max_radius = (60 * scale + 0.5f); // convert dps to pixels
                float radius;
                if( frac < 0.5f ) {
                    float alpha = frac*2.0f;
                    radius = (1.0f-alpha) * min_radius + alpha * max_radius;
                }
                else {
                    float alpha = (frac-0.5f)*2.0f;
                    radius = (1.0f-alpha) * max_radius + alpha * min_radius;
                }
				/*if( MyDebug.LOG ) {
					Log.d(TAG, "dt: " + dt);
					Log.d(TAG, "radius: " + radius);
				}*/
                p.setColor(Color.WHITE);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(stroke_width);
                canvas.drawCircle(pos_x, pos_y, radius, p);
                p.setStyle(Paint.Style.FILL); // reset
            }
            else {
                clearContinuousFocusMove();
            }
        }

        if( preview.isFocusWaiting() || preview.isFocusRecentSuccess() || preview.isFocusRecentFailure() ) {
            long time_since_focus_started = preview.timeSinceStartedAutoFocus();
            float min_radius = (40 * scale + 0.5f); // convert dps to pixels
            float max_radius = (45 * scale + 0.5f); // convert dps to pixels
            float radius = min_radius;
            if( time_since_focus_started > 0 ) {
                final long length = 500;
                float frac = ((float)time_since_focus_started) / (float)length;
                if( frac > 1.0f )
                    frac = 1.0f;
                if( frac < 0.5f ) {
                    float alpha = frac*2.0f;
                    radius = (1.0f-alpha) * min_radius + alpha * max_radius;
                }
                else {
                    float alpha = (frac-0.5f)*2.0f;
                    radius = (1.0f-alpha) * max_radius + alpha * min_radius;
                }
            }
            int size = (int)radius;

            if( preview.isFocusRecentSuccess() )
                p.setColor(Color.rgb(20, 231, 21)); // Green A400
            else if( preview.isFocusRecentFailure() )
                p.setColor(Color.rgb(244, 67, 54)); // Red 500
            else
                p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(stroke_width);
            int pos_x;
            int pos_y;
            if( preview.hasFocusArea() ) {
                Pair<Integer, Integer> focus_pos = preview.getFocusPos();
                pos_x = focus_pos.first;
                pos_y = focus_pos.second;
            }
            else {
                pos_x = canvas.getWidth() / 2;
                pos_y = canvas.getHeight() / 2;
            }
            float frac = 0.5f;
            // horizontal strokes
            canvas.drawLine(pos_x - size, pos_y - size, pos_x - frac*size, pos_y - size, p);
            canvas.drawLine(pos_x + frac*size, pos_y - size, pos_x + size, pos_y - size, p);
            canvas.drawLine(pos_x - size, pos_y + size, pos_x - frac*size, pos_y + size, p);
            canvas.drawLine(pos_x + frac*size, pos_y + size, pos_x + size, pos_y + size, p);
            // vertical strokes
            canvas.drawLine(pos_x - size, pos_y - size, pos_x - size, pos_y - frac*size, p);
            canvas.drawLine(pos_x - size, pos_y + frac*size, pos_x - size, pos_y + size, p);
            canvas.drawLine(pos_x + size, pos_y - size, pos_x + size, pos_y - frac*size, p);
            canvas.drawLine(pos_x + size, pos_y + frac*size, pos_x + size, pos_y + size, p);
            p.setStyle(Paint.Style.FILL); // reset
        }
    }

    public void onDrawPreview(Canvas canvas) {
		/*if( MyDebug.LOG )
			Log.d(TAG, "onDrawPreview");*/
        if( !has_settings ) {
            if( MyDebug.LOG )
                Log.d(TAG, "onDrawPreview: need to update settings");
            updateSettings();
        }
        Preview preview = main_activity.getPreview();
        CameraController camera_controller = preview.getCameraController();
        int ui_rotation = preview.getUIRotation();

        final long time_ms = System.currentTimeMillis();

        // set up preview bitmaps (histogram etc)
        boolean want_preview_bitmap = want_histogram || want_zebra_stripes || want_focus_peaking;
        if( want_preview_bitmap != preview.isPreviewBitmapEnabled() ) {
            if( want_preview_bitmap ) {
                preview.enablePreviewBitmap();
            }
            else
                preview.disablePreviewBitmap();
        }
        if( want_preview_bitmap ) {
            if( want_histogram )
                preview.enableHistogram(histogram_type);
            else
                preview.disableHistogram();

            if( want_zebra_stripes )
                preview.enableZebraStripes(zebra_stripes_threshold, zebra_stripes_color_foreground, zebra_stripes_color_background);
            else
                preview.disableZebraStripes();

            if( want_focus_peaking )
                preview.enableFocusPeaking();
            else
                preview.disableFocusPeaking();
        }

        // see documentation for CameraController.shouldCoverPreview()
        if( preview.usingCamera2API() && ( camera_controller == null || camera_controller.shouldCoverPreview() ) ) {
            p.setColor(Color.BLACK);
            canvas.drawRect(0.0f, 0.0f, canvas.getWidth(), canvas.getHeight(), p);
        }

        if( camera_controller!= null && front_screen_flash ) {
            p.setColor(Color.WHITE);
            canvas.drawRect(0.0f, 0.0f, canvas.getWidth(), canvas.getHeight(), p);
        }
        else if( "flash_frontscreen_torch".equals(preview.getCurrentFlashValue()) ) { // getCurrentFlashValue() may return null
            p.setColor(Color.WHITE);
            p.setAlpha(200); // set alpha so user can still see some of the preview
            canvas.drawRect(0.0f, 0.0f, canvas.getWidth(), canvas.getHeight(), p);
            p.setAlpha(255);
        }

        if( main_activity.getMainUI().inImmersiveMode() ) {
            if( immersive_mode_everything_pref ) {
                // exit, to ensure we don't display anything!
                // though note we still should do the front screen flash (since the user can take photos via volume keys when
                // in immersive_mode_everything mode)
                return;
            }
        }

        // If MainActivity.lock_to_landscape==true, then the ui_rotation represents the orientation of the
        // device; if MainActivity.lock_to_landscape==false then ui_rotation is always 0 as we don't need to
        // apply any orientation ourselves. However, we're we do want to know the true rotation of the
        // device, as it affects how certain elements of the UI are layed out.
        int device_ui_rotation;
        if( MainActivity.lock_to_landscape ) {
            device_ui_rotation = ui_rotation;
        }
        else {
            MainActivity.SystemOrientation system_orientation = main_activity.getSystemOrientation();
            device_ui_rotation = MainActivity.getRotationFromSystemOrientation(system_orientation);
        }

        if( camera_controller != null && taking_picture && !front_screen_flash && take_photo_border_pref ) {
            p.setColor(Color.WHITE);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(stroke_width);
            float this_stroke_width = (5.0f * scale + 0.5f); // convert dps to pixels
            p.setStrokeWidth(this_stroke_width);
            canvas.drawRect(0.0f, 0.0f, canvas.getWidth(), canvas.getHeight(), p);
            p.setStyle(Paint.Style.FILL); // reset
            p.setStrokeWidth(stroke_width); // reset
        }
        drawGrids(canvas);

        drawCropGuides(canvas);

        // n.b., don't display ghost image if front_screen_flash==true (i.e., frontscreen flash is in operation), otherwise
        // the effectiveness of the "flash" is reduced
        if( last_thumbnail != null && !last_thumbnail_is_video && camera_controller != null && ( show_last_image || ( allow_ghost_last_image && !front_screen_flash && ghost_image_pref.equals("preference_ghost_image_last") ) ) ) {
            // If changing this code, ensure that pause preview still works when:
            // - Taking a photo in portrait or landscape - and check rotating the device while preview paused
            // - Taking a photo with lock to portrait/landscape options still shows the thumbnail with aspect ratio preserved
            // Also check ghost last image works okay!
            if( show_last_image ) {
                p.setColor(Color.rgb(0, 0, 0)); // in case image doesn't cover the canvas (due to different aspect ratios)
                canvas.drawRect(0.0f, 0.0f, canvas.getWidth(), canvas.getHeight(), p); // in case
            }
            setLastImageMatrix(canvas, last_thumbnail, ui_rotation, !show_last_image);
            if( !show_last_image )
                p.setAlpha(ghost_image_alpha);
            canvas.drawBitmap(last_thumbnail, last_image_matrix, p);
            if( !show_last_image )
                p.setAlpha(255);
        }
        else if( camera_controller != null && !front_screen_flash && ghost_selected_image_bitmap != null ) {
            setLastImageMatrix(canvas, ghost_selected_image_bitmap, ui_rotation, true);
            p.setAlpha(ghost_image_alpha);
            canvas.drawBitmap(ghost_selected_image_bitmap, last_image_matrix, p);
            p.setAlpha(255);
        }

        if( preview.isPreviewBitmapEnabled() ) {
            // draw additional real-time effects

            // draw zebra stripes
            Bitmap zebra_stripes_bitmap = preview.getZebraStripesBitmap();
            if( zebra_stripes_bitmap != null ) {
                setLastImageMatrix(canvas, zebra_stripes_bitmap, 0, false);
                p.setAlpha(255);
                canvas.drawBitmap(zebra_stripes_bitmap, last_image_matrix, p);
            }

            // draw focus peaking
            Bitmap focus_peaking_bitmap = preview.getFocusPeakingBitmap();
            if( focus_peaking_bitmap != null ) {
                setLastImageMatrix(canvas, focus_peaking_bitmap, 0, false);
                p.setAlpha(127);
                if( focus_peaking_color_pref != Color.WHITE ) {
                    p.setColorFilter(new PorterDuffColorFilter(focus_peaking_color_pref, PorterDuff.Mode.SRC_IN));
                }
                canvas.drawBitmap(focus_peaking_bitmap, last_image_matrix, p);
                if( focus_peaking_color_pref != Color.WHITE ) {
                    p.setColorFilter(null);
                }
                p.setAlpha(255);
            }
        }

        doThumbnailAnimation(canvas, time_ms);

        drawUI(canvas, device_ui_rotation, time_ms);

        drawAngleLines(canvas, device_ui_rotation, time_ms);

        doFocusAnimation(canvas, time_ms);

        CameraController.Face [] faces_detected = preview.getFacesDetected();
        if( faces_detected != null ) {
            p.setColor(Color.rgb(255, 235, 59)); // Yellow 500
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(stroke_width);
            for(CameraController.Face face : faces_detected) {
                // Android doc recommends filtering out faces with score less than 50 (same for both Camera and Camera2 APIs)
                if( face.score >= 50 ) {
                    canvas.drawRect(face.temp, p);
                }
            }
            p.setStyle(Paint.Style.FILL); // reset
        }

        if( enable_gyro_target_spot && camera_controller != null ) {
            GyroSensor gyroSensor = main_activity.getApplicationInterface().getGyroSensor();
            if( gyroSensor.isRecording() ) {
                MainActivity.SystemOrientation system_orientation = main_activity.getSystemOrientation();
                boolean system_orientation_portrait = system_orientation == MainActivity.SystemOrientation.PORTRAIT;
                for(float [] gyro_direction : gyro_directions) {
                    gyroSensor.getRelativeInverseVector(transformed_gyro_direction, gyro_direction);
                    gyroSensor.getRelativeInverseVector(transformed_gyro_direction_up, gyro_direction_up);
                    // note that although X of gyro_direction represents left to right on the device, because we're in landscape mode,
                    // this is y coordinates on the screen
                    float angle_x, angle_y;
                    if( system_orientation_portrait ) {
                        angle_x = (float)Math.asin(transformed_gyro_direction[0]);
                        angle_y = - (float)Math.asin(transformed_gyro_direction[1]);
                    }
                    else {
                        angle_x = - (float)Math.asin(transformed_gyro_direction[1]);
                        angle_y = - (float)Math.asin(transformed_gyro_direction[0]);
                    }
                    if( Math.abs(angle_x) < 0.5f*Math.PI && Math.abs(angle_y) < 0.5f*Math.PI ) {
                        updateCachedViewAngles(time_ms); // ensure view_angle_x_preview, view_angle_y_preview are computed and up to date
                        float camera_angle_x, camera_angle_y;
                        if( system_orientation_portrait ) {
                            camera_angle_x = this.view_angle_y_preview;
                            camera_angle_y = this.view_angle_x_preview;
                        }
                        else {
                            camera_angle_x = this.view_angle_x_preview;
                            camera_angle_y = this.view_angle_y_preview;
                        }
                        float angle_scale_x = (float) (canvas.getWidth() / (2.0 * Math.tan(Math.toRadians((camera_angle_x / 2.0)))));
                        float angle_scale_y = (float) (canvas.getHeight() / (2.0 * Math.tan(Math.toRadians((camera_angle_y / 2.0)))));
                        angle_scale_x *= preview.getZoomRatio();
                        angle_scale_y *= preview.getZoomRatio();
                        float distance_x = angle_scale_x * (float) Math.tan(angle_x); // angle_scale is already in pixels rather than dps
                        float distance_y = angle_scale_y * (float) Math.tan(angle_y); // angle_scale is already in pixels rather than dps
                        p.setColor(Color.WHITE);
                        drawGyroSpot(canvas, 0.0f, 0.0f, -1.0f, 0.0f, 48, true); // draw spot for the centre of the screen, to help the user orient the device
                        p.setColor(Color.BLUE);
                        float dir_x = -transformed_gyro_direction_up[1];
                        float dir_y = -transformed_gyro_direction_up[0];
                        drawGyroSpot(canvas, distance_x, distance_y, dir_x, dir_y, 45, false);
					/*{
						// for debug only, draw the gyro spot that isn't calibrated with the accelerometer
						gyroSensor.getRelativeInverseVectorGyroOnly(transformed_gyro_direction, gyro_direction);
						gyroSensor.getRelativeInverseVectorGyroOnly(transformed_gyro_direction_up, gyro_direction_up);
						p.setColor(Color.YELLOW);
						angle_x = - (float)Math.asin(transformed_gyro_direction[1]);
						angle_y = - (float)Math.asin(transformed_gyro_direction[0]);
						distance_x = angle_scale_x * (float) Math.tan(angle_x); // angle_scale is already in pixels rather than dps
						distance_y = angle_scale_y * (float) Math.tan(angle_y); // angle_scale is already in pixels rather than dps
						dir_x = -transformed_gyro_direction_up[1];
						dir_y = -transformed_gyro_direction_up[0];
						drawGyroSpot(canvas, distance_x, distance_y, dir_x, dir_y, 45);
					}*/
                    }

                    // show indicator for not being "upright", but only if tilt angle is within 20 degrees
                    if( gyroSensor.isUpright() != 0 && Math.abs(angle_x) <= 20.0f*0.0174532925199f ) {
                        //applicationInterface.drawTextWithBackground(canvas, p, "not upright", Color.WHITE, Color.BLACK, canvas.getWidth()/2, canvas.getHeight()/2, MyApplicationInterface.Alignment.ALIGNMENT_CENTRE, null, true);
                        canvas.save();
                        canvas.rotate(ui_rotation, canvas.getWidth()/2.0f, canvas.getHeight()/2.0f);
                        final int icon_size = (int) (64 * scale + 0.5f); // convert dps to pixels
                        final int cy_offset = (int) (80 * scale + 0.5f); // convert dps to pixels
                        int cx = canvas.getWidth()/2, cy = canvas.getHeight()/2 - cy_offset;
                        icon_dest.set(cx - icon_size/2, cy - icon_size/2, cx + icon_size/2, cy + icon_size/2);
					/*p.setStyle(Paint.Style.FILL);
					p.setColor(Color.BLACK);
					p.setAlpha(64);
					canvas.drawRect(icon_dest, p);
					p.setAlpha(255);*/
                        canvas.drawBitmap(gyroSensor.isUpright() > 0 ? rotate_left_bitmap : rotate_right_bitmap, null, icon_dest, p);
                        canvas.restore();
                    }
                }
            }
        }
    }

    private void setLastImageMatrix(Canvas canvas, Bitmap bitmap, int this_ui_rotation, boolean flip_front) {
        Preview preview = main_activity.getPreview();
        CameraController camera_controller = preview.getCameraController();
        last_image_src_rect.left = 0;
        last_image_src_rect.top = 0;
        last_image_src_rect.right = bitmap.getWidth();
        last_image_src_rect.bottom = bitmap.getHeight();
        if( this_ui_rotation == 90 || this_ui_rotation == 270 ) {
            last_image_src_rect.right = bitmap.getHeight();
            last_image_src_rect.bottom = bitmap.getWidth();
        }
        last_image_dst_rect.left = 0;
        last_image_dst_rect.top = 0;
        last_image_dst_rect.right = canvas.getWidth();
        last_image_dst_rect.bottom = canvas.getHeight();
		/*if( MyDebug.LOG ) {
			Log.d(TAG, "thumbnail: " + bitmap.getWidth() + " x " + bitmap.getHeight());
			Log.d(TAG, "canvas: " + canvas.getWidth() + " x " + canvas.getHeight());
		}*/
        last_image_matrix.setRectToRect(last_image_src_rect, last_image_dst_rect, Matrix.ScaleToFit.CENTER); // use CENTER to preserve aspect ratio
        if( this_ui_rotation == 90 || this_ui_rotation == 270 ) {
            // the rotation maps (0, 0) to (tw/2 - th/2, th/2 - tw/2), so we translate to undo this
            float diff = bitmap.getHeight() - bitmap.getWidth();
            last_image_matrix.preTranslate(diff/2.0f, -diff/2.0f);
        }
        last_image_matrix.preRotate(this_ui_rotation, bitmap.getWidth()/2.0f, bitmap.getHeight()/2.0f);
        if( flip_front ) {
            boolean is_front_facing = camera_controller != null && (camera_controller.getFacing() == CameraController.Facing.FACING_FRONT);
            if( is_front_facing && !sharedPreferences.getString(PreferenceKeys.FrontCameraMirrorKey, "preference_front_camera_mirror_no").equals("preference_front_camera_mirror_photo") ) {
                last_image_matrix.preScale(-1.0f, 1.0f, bitmap.getWidth()/2.0f, 0.0f);
            }
        }
    }

    private void drawGyroSpot(Canvas canvas, float distance_x, float distance_y, float dir_x, float dir_y, int radius_dp, boolean outline) {
        if( outline ) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(stroke_width);
            p.setAlpha(255);
        }
        else {
            p.setAlpha(127);
        }
        float radius = (radius_dp * scale + 0.5f); // convert dps to pixels
        float cx = canvas.getWidth()/2.0f + distance_x;
        float cy = canvas.getHeight()/2.0f + distance_y;
        canvas.drawCircle(cx, cy, radius, p);
        p.setAlpha(255);
        p.setStyle(Paint.Style.FILL); // reset

        // draw crosshairs
        //p.setColor(Color.WHITE);
        /*p.setStrokeWidth(stroke_width);
        canvas.drawLine(cx - radius*dir_x, cy - radius*dir_y, cx + radius*dir_x, cy + radius*dir_y, p);
        canvas.drawLine(cx - radius*dir_y, cy + radius*dir_x, cx + radius*dir_y, cy - radius*dir_x, p);*/
    }

    /**
     * A generic method to display up to two lines on the preview.
     * Currently used by the Kraken underwater housing sensor to display
     * temperature and depth.
     *
     * The two lines are displayed in the lower left corner of the screen.
     *
     * @param line1 First line to display
     * @param line2 Second line to display
     */
    public void onExtraOSDValuesChanged(String line1, String line2) {
        OSDLine1 = line1;
        OSDLine2 = line2;
    }

    // for testing:

    public boolean getStoredHasStampPref() {
        return this.has_stamp_pref;
    }

    public boolean getStoredAutoStabilisePref() {
        return this.auto_stabilise_pref;
    }
}
