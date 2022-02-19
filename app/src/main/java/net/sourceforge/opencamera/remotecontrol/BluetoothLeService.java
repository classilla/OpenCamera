package net.sourceforge.opencamera.remotecontrol;

import net.sourceforge.opencamera.MyDebug;

import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import androidx.annotation.RequiresApi;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

@RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
public class BluetoothLeService extends Service {
    private final static String TAG = "BluetoothLeService";

    private boolean is_bound; // whether service is bound
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private String device_address;
    private BluetoothGatt bluetoothGatt;
    private String remote_device_type;
    private final Handler bluetoothHandler = new Handler();
    private final HashMap<String, BluetoothGattCharacteristic> subscribed_characteristics = new HashMap<>();
    private final List<BluetoothGattCharacteristic> charsToSubscribe = new ArrayList<>();

    private double currentTemp = -1;
    private double currentDepth = -1;

    /*private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;*/

    public final static String ACTION_GATT_CONNECTED =
            "net.sourceforge.opencamera.Remotecontrol.ACTION_GATT_CONNECTED";
    public final static String ACTION_GATT_DISCONNECTED =
            "net.sourceforge.opencamera.Remotecontrol.ACTION_GATT_DISCONNECTED";
    public final static String ACTION_GATT_SERVICES_DISCOVERED =
            "net.sourceforge.opencamera.Remotecontrol.ACTION_GATT_SERVICES_DISCOVERED";
    public final static String ACTION_DATA_AVAILABLE =
            "net.sourceforge.opencamera.Remotecontrol.ACTION_DATA_AVAILABLE";
    public final static String ACTION_REMOTE_COMMAND =
            "net.sourceforge.opencamera.Remotecontrol.COMMAND";
    public final static String ACTION_SENSOR_VALUE =
            "net.sourceforge.opencamera.Remotecontrol.SENSOR";
    public final static String SENSOR_TEMPERATURE =
            "net.sourceforge.opencamera.Remotecontrol.TEMPERATURE";
    public final static String SENSOR_DEPTH =
            "net.sourceforge.opencamera.Remotecontrol.DEPTH";
    public final static String EXTRA_DATA =
            "net.sourceforge.opencamera.Remotecontrol.EXTRA_DATA";
    public final static int COMMAND_SHUTTER = 32;
    public final static int COMMAND_MODE = 16;
    public final static int COMMAND_MENU = 48;
    public final static int COMMAND_AFMF = 97;
    public final static int COMMAND_UP = 64;
    public final static int COMMAND_DOWN = 80;

    /* This forces a gratuitous BLE scan to help the device
     * connect to the remote faster. This is due to limitations of the
     * Android BLE stack and API (just knowing the MAC is not enough on
     * many phones).*/
    private void triggerScan() {
        if( MyDebug.LOG )
            Log.d(TAG, "triggerScan");

        if( !is_bound ) {
            // Don't allow calls to startLeScan() (which requires location permission) when service
            // not bound, as application may be in background!
            // In theory this shouldn't be needed here, as we also check is_bound in connect(), but
            // have it here too just to be safe.
            Log.e(TAG, "triggerScan shouldn't be called when service not bound");
            return;
        }

        // Stops scanning after a pre-defined scan period.
        bluetoothHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                bluetoothAdapter.stopLeScan(null);
            }
        }, 10000);
        bluetoothAdapter.startLeScan(null);
    }

    public void setRemoteDeviceType(String remote_device_type) {
        if( MyDebug.LOG )
            Log.d(TAG, "Setting remote type: " + remote_device_type);
        this.remote_device_type = remote_device_type;
    }

    private final BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            String intentAction;
            if( newState == BluetoothProfile.STATE_CONNECTED ) {
                intentAction = ACTION_GATT_CONNECTED;
                broadcastUpdate(intentAction);
                if( MyDebug.LOG ) {
                    Log.d(TAG, "Connected to GATT server, call discoverServices()");
                }
                bluetoothGatt.discoverServices();
                currentDepth = -1;
                currentTemp = -1;

            }
            else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                intentAction = ACTION_GATT_DISCONNECTED;
                if( MyDebug.LOG )
                    Log.d(TAG, "Disconnected from GATT server, reattempting every 5 seconds.");
                broadcastUpdate(intentAction);
                attemptReconnect();
            }
        }

        void attemptReconnect() {
            if( !is_bound ) {
                // We check is_bound in connect() itself, but seems pointless to even try if we
                // know the service is unbound (and if it's later bound again, we'll try connecting
                // again anyway without needing this).
                Log.e(TAG, "don't attempt to reconnect when service not bound");
            }

            Timer timer = new Timer();
            timer.schedule(new TimerTask() {
                public void run() {
                    if( MyDebug.LOG )
                        Log.d(TAG, "Attempting to reconnect to remote.");
                    connect(device_address);
                }
            }, 5000);
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if( status == BluetoothGatt.GATT_SUCCESS ) {
                broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED);
                subscribeToServices();
            }
            else {
                if( MyDebug.LOG )
                    Log.d(TAG, "onServicesDiscovered received: " + status);
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            if( status == BluetoothGatt.GATT_SUCCESS ) {
                broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
            }
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {
            if( MyDebug.LOG )
                Log.d(TAG,"Got notification");
            broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            // We need to wait for this callback before enabling the next notification in case we
            // have several in our list
            if( !charsToSubscribe.isEmpty() ) {
                setCharacteristicNotification(charsToSubscribe.remove(0), true);
            }
        }
    };

    /**
     * Subscribe to the services/characteristics we need depending
     * on the remote device model
     *
     */
    private void subscribeToServices() {
        List<BluetoothGattService> gattServices = getSupportedGattServices();
        if (gattServices == null) return;
        List<UUID> mCharacteristicsWanted;

        //noinspection SwitchStatementWithTooFewBranches
        switch( remote_device_type ) {
            case "preference_remote_type_kraken":
                mCharacteristicsWanted = KrakenGattAttributes.getDesiredCharacteristics();
                break;
            default:
                mCharacteristicsWanted = Collections.singletonList(UUID.fromString("0000"));
                break;
        }

        for(BluetoothGattService gattService : gattServices) {
            List<BluetoothGattCharacteristic> gattCharacteristics =
                    gattService.getCharacteristics();
            for(BluetoothGattCharacteristic gattCharacteristic : gattCharacteristics) {
                UUID uuid = gattCharacteristic.getUuid();
                if( mCharacteristicsWanted.contains(uuid) ) {
                    if( MyDebug.LOG )
                        Log.d(TAG, "Found characteristic to subscribe to: " + uuid);
                    charsToSubscribe.add(gattCharacteristic);
                }
            }
        }
        setCharacteristicNotification(charsToSubscribe.remove(0), true);
    }

    private void broadcastUpdate(final String action) {
        final Intent intent = new Intent(action);
        sendBroadcast(intent);
    }

    private void broadcastUpdate(String action, final BluetoothGattCharacteristic characteristic) {
        UUID uuid = characteristic.getUuid();
        final int format_uint8 = BluetoothGattCharacteristic.FORMAT_UINT8;
        final int format_uint16 = BluetoothGattCharacteristic.FORMAT_UINT16;
        int remoteCommand = -1;

        if( KrakenGattAttributes.KRAKEN_BUTTONS_CHARACTERISTIC.equals(uuid) ) {
            if( MyDebug.LOG )
                Log.d(TAG,"Got Kraken button press");
            final int buttonCode= characteristic.getIntValue(format_uint8, 0);
            if( MyDebug.LOG )
                Log.d(TAG, String.format("Received Button press: %d", buttonCode));
            // Note: we stay at a fairly generic level here and will manage variants
            // on the various button actions in MainActivity, because those will change depending
            // on the current state of the app, and we don't want to know anything about that state
            // from the Bluetooth LE service
            // TODO: update to remove all those tests and just forward buttonCode since value is identical
            //       but this is more readable if we want to implement other drivers
            if( buttonCode == 32 ) {
                // Shutter press
                remoteCommand = COMMAND_SHUTTER;
            }
            else if( buttonCode == 16 ) {
                // "Mode" button: either "back" action or "Photo/Camera" switch
                remoteCommand = COMMAND_MODE;
            }
            else if( buttonCode == 48 ) {
                // "Menu" button
                remoteCommand = COMMAND_MENU;
            }
            else if( buttonCode == 97 ) {
                // AF/MF button
                remoteCommand = COMMAND_AFMF;
            }
            else if( buttonCode == 96 ) {
                // Long press on MF/AF button.
                // Note: the camera issues button code 97 first, then
                // 96 after one second of continuous press
            }
            else if( buttonCode == 64 ) {
                // Up button
                remoteCommand = COMMAND_UP;
            } else if (buttonCode == 80) {
                // Down button
                remoteCommand = COMMAND_DOWN;
            }
            // Only send forward if we have something to say
            if( remoteCommand > -1 ) {
                final Intent intent = new Intent(ACTION_REMOTE_COMMAND);
                intent.putExtra(EXTRA_DATA, remoteCommand);
                sendBroadcast(intent);
            }
        }
        else if( KrakenGattAttributes.KRAKEN_SENSORS_CHARACTERISTIC.equals(uuid) ) {
            // The housing returns four bytes.
            // Byte 0-1: depth = (Byte 0 + Byte 1 << 8) / 10 / density
            // Byte 2-3: temperature = (Byte 2 + Byte 3 << 8) / 10
            //
            // Depth is valid for fresh water by default ( makes you wonder whether the sensor
            // is really designed for saltwater at all), and the value has to be divided by the density
            // of saltwater. A commonly accepted value is 1030 kg/m3 (1.03 density)

            double temperature = characteristic.getIntValue(format_uint16, 2) / 10.0;
            double depth = characteristic.getIntValue(format_uint16, 0) / 10.0;

            if( temperature == currentTemp && depth == currentDepth )
                return;

            currentDepth = depth;
            currentTemp = temperature;

            if( MyDebug.LOG )
                Log.d(TAG, "Got new Kraken sensor reading. Temperature: " + temperature + " Depth:" + depth);

            final Intent intent = new Intent(ACTION_SENSOR_VALUE);
            intent.putExtra(SENSOR_TEMPERATURE, temperature);
            intent.putExtra(SENSOR_DEPTH, depth);
            sendBroadcast(intent);
        }

    }

    public class LocalBinder extends Binder {
        public BluetoothLeService getService() {
            return BluetoothLeService.this;
        }
    }

    private final IBinder mBinder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) {
        if( MyDebug.LOG )
            Log.d(TAG, "onBind");
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if( MyDebug.LOG )
            Log.d(TAG, "onUnbind");
        this.is_bound = false;
        close();
        return super.onUnbind(intent);
    }

    /** Only call this after service is bound (from ServiceConnection.onServiceConnected())!
     */
    public boolean initialize() {
        if( MyDebug.LOG )
            Log.d(TAG, "initialize");

        // in theory we'd put this in onBind(), to be more symmetric with onUnbind() where we
        // set to false - but unclear whether onBind() is always called before
        // ServiceConnection.onServiceConnected().
        this.is_bound = true;

        if( bluetoothManager == null ) {
            bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            if( bluetoothManager == null ) {
                Log.e(TAG, "Unable to initialize BluetoothManager.");
                return false;
            }
        }

        bluetoothAdapter = bluetoothManager.getAdapter();
        if( bluetoothAdapter == null ) {
            Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
            return false;
        }

        return true;
    }

	public boolean connect(final String address) {
        if( MyDebug.LOG )
            Log.d(TAG, "connect: " + address);
        if( bluetoothAdapter == null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "bluetoothAdapter is null");
            return false;
        }
        else if( address == null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "address is null");
            return false;
        }
        else if( !is_bound ) {
            // Don't allow calls to startLeScan() via triggerScan() (which requires location
            // permission) when service not bound, as application may be in background!
            // And it doesn't seem sensible to even allow connecting if service not bound.
            // Under normal operation this isn't needed, but there are calls to connect() that can
            // happen from postDelayed() or TimerTask in this class, so a risk that they call
            // connect() after the service is unbound!
            Log.e(TAG, "connect shouldn't be called when service not bound");
            return false;
        }


        // test code for infinite looping, seeing if this runs in background:
        /*if( address.equals("undefined") ) {
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                public void run() {
                    if( MyDebug.LOG )
                        Log.d(TAG, "trying connect again from postdelayed");
                    connect(address);
                }
            }, 1000);
        }

        if( address.equals("undefined") ) {
            // test - only needed if we've hacked BluetoothRemoteControl.remoteEnabled() to not check for being undefined
            if( MyDebug.LOG )
                Log.d(TAG, "address is undefined");
            return false;
        }*/

        if( address.equals(device_address) && bluetoothGatt != null ) {
            bluetoothGatt.disconnect();
            bluetoothGatt.close();
            bluetoothGatt = null;
        }

        final BluetoothDevice device = bluetoothAdapter.getRemoteDevice(address);
        if( device == null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "device not found");
            Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                public void run() {
                    if( MyDebug.LOG )
                        Log.d(TAG, "attempt to connect to remote");
                    connect(address);
                }
            }, 5000);
            return false;
        }

        // It looks like Android won't connect to BLE devices properly without scanning
        // for them first, even when connecting by explicit MAC address. Since we're using
        // BLE for underwater housings and we want rock solid connectivity, we trigger
        // a scan for 10 seconds
        triggerScan();

        bluetoothGatt = device.connectGatt(this, true, mGattCallback);
        device_address = address;
        return true;
	}

    private void close() {
        if( bluetoothGatt == null ) {
            return;
        }
        bluetoothGatt.close();
        bluetoothGatt = null;
    }

    private void setCharacteristicNotification(BluetoothGattCharacteristic characteristic, boolean enabled) {
        if( bluetoothAdapter == null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "bluetoothAdapter is null");
            return;
        }
        else if( bluetoothGatt == null ) {
            if( MyDebug.LOG )
                Log.d(TAG, "bluetoothGatt is null");
            return;
        }

        String uuid = characteristic.getUuid().toString();
        bluetoothGatt.setCharacteristicNotification(characteristic, enabled);
        if( enabled ) {
            subscribed_characteristics.put(uuid, characteristic);
        }
        else {
            subscribed_characteristics.remove(uuid);
        }

        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(KrakenGattAttributes.CLIENT_CHARACTERISTIC_CONFIG);
        descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
        bluetoothGatt.writeDescriptor(descriptor);
    }

    private List<BluetoothGattService> getSupportedGattServices() {
        if( bluetoothGatt == null )
            return null;

        return bluetoothGatt.getServices();
    }
}
