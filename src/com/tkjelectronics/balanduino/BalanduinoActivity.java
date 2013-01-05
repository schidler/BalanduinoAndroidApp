/*************************************************************************************
 * Copyright (C) 2012 Kristian Lauszus, TKJ Electronics. All rights reserved.
 *
 * This software may be distributed and modified under the terms of the GNU
 * General Public License version 2 (GPL2) as published by the Free Software
 * Foundation and appearing in the file GPL2.TXT included in the packaging of
 * this file. Please note that GPL2 Section 2[b] requires that all works based
 * on this software must also be made publicly available under the terms of
 * the GPL2 ("Copyleft").
 *
 * Contact information
 * -------------------
 *
 * Kristian Lauszus, TKJ Electronics
 * Web      :  http://www.tkjelectronics.com
 * e-mail   :  kristianl@tkjelectronics.com
 * 
 * The fusion algorythme is from this guide: http://www.thousand-thoughts.com/2012/03/android-sensor-fusion-tutorial/
 * By Paul Lawitzki
 * The Bluetooth communication is based on the BluetoothChat sample included in the Android SDK, but with some improvements
 * 
 ************************************************************************************/

package com.tkjelectronics.balanduino;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences.Editor;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.app.SherlockFragmentActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.viewpagerindicator.UnderlinePageIndicator;

public class BalanduinoActivity extends SherlockFragmentActivity implements
		ActionBar.TabListener, VoiceRecognitionFragment.signalListener {

	private static final String TAG = "Balanduino";
	public static final boolean D = false;

	// Message types sent from the BluetoothChatService Handler
	public static final int MESSAGE_STATE_CHANGE = 1;
	public static final int MESSAGE_READ = 2;
	// public static final int MESSAGE_WRITE = 3;
	public static final int MESSAGE_DEVICE_NAME = 4;
	public static final int MESSAGE_TOAST = 5;

	// Key names received from the BluetoothChatService Handler
	public static final String DEVICE_NAME = "device_name";
	public static final String TOAST = "toast";

	// Intent request codes
	// private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
	private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
	private static final int REQUEST_ENABLE_BT = 3;

	// Name of the connected device
	private String mConnectedDeviceName = null;
	// Local Bluetooth adapter
	private BluetoothAdapter mBluetoothAdapter = null;
	// Member object for the chat services
	public static BluetoothChatService mChatService = null;
	public static SensorFusion mSensorFusion = null;
	private SensorManager mSensorManager = null;

	ViewPagerAdapter mViewPagerAdapter;
	ViewPager mViewPager;
	
	public static final int IMU_TAB = 0;
	public static final int JOYSTICK_TAB = 1;
	public static final int PID_TAB = 2;
	public static final int VOICERECOGNITION_TAB = 3;
	
	public static int currentTabSelected;
	
	public static String pValue = "";
	public static String iValue = "";
	public static String dValue = "";
	public static String targetAngleValue = "";
	public static boolean newPValue;
	public static boolean newIValue;
	public static boolean newDValue;
	public static boolean newTargetAngleValue;
	
	private static SpeechRecognizer mSpeechRecognizer;
	public static boolean toggleButtonState;
	
	private static Context context;
	
	public static MenuItem settings;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		context = getApplicationContext();
		
		setContentView(R.layout.activity_main);

		// Get local Bluetooth adapter
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		// If the adapter is null, then Bluetooth is not supported
		if (mBluetoothAdapter == null) {
			Toast.makeText(getApplicationContext(),
					"Bluetooth is not available", Toast.LENGTH_LONG).show();
			finish();
			return;
		}
		initSpeechRecognizer();

		// get sensorManager and initialize sensor listeners
		mSensorManager = (SensorManager) this.getSystemService(SENSOR_SERVICE);
		mSensorFusion = new SensorFusion(mSensorManager);

		// Set up the action bar.
		final ActionBar actionBar = getSupportActionBar();
		actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
		actionBar.setDisplayHomeAsUpEnabled(true);

		// Create the adapter that will return a fragment for each of the three
		// primary sections of the app.
		mViewPagerAdapter = new ViewPagerAdapter(
				getSupportFragmentManager());

		// Set up the ViewPager with the adapter.
		mViewPager = (ViewPager) findViewById(R.id.pager);
		mViewPager.setAdapter(mViewPagerAdapter);
		
		// Bind the underline indicator to the adapter
		UnderlinePageIndicator mUnderlinePageIndicator = (UnderlinePageIndicator)findViewById(R.id.indicator);
		mUnderlinePageIndicator.setViewPager(mViewPager);
		mUnderlinePageIndicator.setFades(false);

		// When swiping between different sections, select the corresponding
		// tab. We can also use ActionBar.Tab#select() to do this if we have
		// a reference to the Tab.
		mUnderlinePageIndicator
				.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
					@Override
					public void onPageSelected(int position) {
						actionBar.setSelectedNavigationItem(position);
					}
				});
		// For each of the sections in the app, add a tab to the action bar.
		for (int i = 0; i < mViewPagerAdapter.getCount(); i++) {
			// Create a tab with text corresponding to the page title defined by
			// the adapter. Also specify this Activity object, which implements
			// the TabListener interface, as the callback (listener) for when
			// this tab is selected.
			actionBar.addTab(actionBar.newTab()
					.setText(mViewPagerAdapter.getPageTitle(i))
					.setTabListener(this));
		}
	}
	
	private void initSpeechRecognizer() {
    	if(mSpeechRecognizer == null) {
    		mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
    		if (!SpeechRecognizer.isRecognitionAvailable(getApplicationContext())) {
    			Toast.makeText(getApplicationContext(),"Speech Recognition is not available",Toast.LENGTH_LONG).show();
    			//finish();
    		}
    		mSpeechRecognizer.setRecognitionListener(new VoiceRecognitionListener());
    	}
    }
	
	static void startSpeechRecognizer() {
		if(D)
			Log.d(TAG, "startSpeechRecognizer");
		if(mSpeechRecognizer == null) {
			if(D)
				Log.d(TAG, "mSpeechRecognizer == null");
			return;
		}
		Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
		// Intent intent = new Intent(RecognizerIntent.ACTION_WEB_SEARCH);
		intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
		//intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,"com.tkjelectronics.balanduino");
		//intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,5);
		mSpeechRecognizer.startListening(intent);	
	}	
	
	static void restartSpeechRecognizer() {
		if(D)
			Log.d(TAG, "restartSpeechRecognizer");
		if(mSpeechRecognizer != null) {
			mSpeechRecognizer.stopListening();
			mSpeechRecognizer.cancel();
			mSpeechRecognizer.destroy();
		}
		mSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(getAppContext());
		mSpeechRecognizer.setRecognitionListener(new VoiceRecognitionListener());
		
		if(toggleButtonState) // Start it again if the button is on
			startSpeechRecognizer();
	}
	
	public static Context getAppContext() {
        return context;
    }
	
	public void toggleButtonChanged(boolean toggleButton) {
		toggleButtonState = toggleButton;
		if(D)
			Log.d(TAG, "Toggle Button Pressed: " + toggleButtonState);		
		if(toggleButtonState)
			startSpeechRecognizer();		
	};

	@Override
	public void onStart() {
		super.onStart();
		if (D)
			Log.e(TAG, "++ ON START ++");
		// If BT is not on, request that it be enabled.
		// setupChat() will then be called during onActivityResult
		if (!mBluetoothAdapter.isEnabled()) {
			if (D)
				Log.d(TAG, "Request enable BT");
			Intent enableIntent = new Intent(
					BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
			// Otherwise, setup the chat session
		} else {
			if (mChatService == null)
				setupBTService();
		}
		// Read the stored value for FILTER_COEFFICIENT
		String filterCoefficient = PreferenceManager
				.getDefaultSharedPreferences(this).getString(
						"filterCoefficient", null);
		if (filterCoefficient != null) {
			mSensorFusion.filter_coefficient = Float
					.parseFloat(filterCoefficient);
			mSensorFusion.tempFilter_coefficient = mSensorFusion.filter_coefficient;
		}
	}

	@Override
	public void onStop() {
		super.onStop();
		if (D)
			Log.e(TAG, "-- ON STOP --");
		// unregister sensor listeners to prevent the activity from draining the
		// device's battery.
		mSensorFusion.unregisterListeners();

		// Store the value for FILTER_COEFFICIENT at shutdown
		Editor edit = PreferenceManager.getDefaultSharedPreferences(this)
				.edit();
		edit.putString("filterCoefficient",
				Float.toString(mSensorFusion.filter_coefficient));
		edit.commit();
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (D)
			Log.e(TAG, "--- ON DESTROY ---");		
		// Stop the Bluetooth chat services
		if (mChatService != null)
			mChatService.stop();
		mSensorFusion.unregisterListeners();
		if (mSpeechRecognizer != null) {
			mSpeechRecognizer.stopListening();
			mSpeechRecognizer.cancel();
			mSpeechRecognizer.destroy();
        }
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (D)
			Log.e(TAG, "- ON PAUSE -");
		// unregister sensor listeners to prevent the activity from draining the
		// device's battery.
		mSensorFusion.unregisterListeners();
		if(mChatService != null) { // Send stop command
			if(mChatService.getState() == BluetoothChatService.STATE_CONNECTED) {
				byte[] send = "S;".getBytes();
				mChatService.write(send, false);
			}
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		if (D)
			Log.e(TAG, "+ ON RESUME +");
		// restore the sensor listeners when user resumes the application.
		mSensorFusion.initListeners();
		initSpeechRecognizer();
	}

	private void setupBTService() {
		if (D)
			Log.d(TAG, "setupBTService()");
		// Initialize the BluetoothChatService to perform bluetooth connections
		mChatService = new BluetoothChatService(this, mHandlerBluetooth);
	}

	@Override
	public void onTabSelected(ActionBar.Tab tab,
			FragmentTransaction fragmentTransaction) {
		if(D)
			Log.d(TAG,"onTabSelected: " + tab.getPosition());
		// When the given tab is selected, switch to the corresponding page in
		// the ViewPager.
		currentTabSelected = tab.getPosition();
		mViewPager.setCurrentItem(currentTabSelected);
		CustomViewPager.setPagingEnabled(true);
		if(currentTabSelected == VOICERECOGNITION_TAB)
			restartSpeechRecognizer(); // Restart service
	}

	@Override
	public void onTabUnselected(ActionBar.Tab tab,
			FragmentTransaction fragmentTransaction) {
		if(D)
			Log.d(TAG,"onTabUnselected: " + tab.getPosition());
		if((tab.getPosition() == IMU_TAB || tab.getPosition() == JOYSTICK_TAB) && mChatService != null) { // Send stop command if the user selects another tab
			if(mChatService.getState() == BluetoothChatService.STATE_CONNECTED) {
				byte[] send = "S;".getBytes();
				mChatService.write(send, false);				
			}
		}
	}

	@Override
	public void onTabReselected(ActionBar.Tab tab,
			FragmentTransaction fragmentTransaction) {
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		if(D)
			Log.e(TAG,"onPrepareOptionsMenu");
		super.onPrepareOptionsMenu(menu);
		MenuItem menuItem = menu.findItem(R.id.menu_connect);
		if (mChatService == null)
			menuItem.setIcon(R.drawable.device_access_bluetooth);
		else {
			if (mChatService.getState() == BluetoothChatService.STATE_CONNECTED)
				menuItem.setIcon(R.drawable.device_access_bluetooth_connected);
			else
				menuItem.setIcon(R.drawable.device_access_bluetooth);
		}
		return true;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		if(D)
			Log.e(TAG,"onCreateOptionsMenu");
		super.onCreateOptionsMenu(menu);
		// Inflate the menu; this adds items to the action bar if it is present.
		MenuInflater inflater = getSupportMenuInflater();	  	
		inflater.inflate(R.menu.menu, menu);
		settings = menu.findItem(R.id.settings);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Intent serverIntent = null;
		switch (item.getItemId()) {
		case R.id.menu_connect:
			// Launch the DeviceListActivity to see devices and do scan
			serverIntent = new Intent(this, DeviceListActivity.class);
			startActivityForResult(serverIntent,
					REQUEST_CONNECT_DEVICE_INSECURE);
			return true;
		case R.id.settings:
			mViewPager.setCurrentItem(IMU_TAB); // Change to the IMU tab
			// This is used to add a custom layout to an AlertDialog
			final View setCoefficient = LayoutInflater.from(this).inflate(
					R.layout.dialog,
					(ViewGroup) findViewById(R.id.layout_dialog));
			final TextView value = (TextView) setCoefficient
					.findViewById(R.id.alertText);
			value.setText(mSensorFusion.d
					.format(mSensorFusion.filter_coefficient));
			final SeekBar mSeekbar = (SeekBar) setCoefficient
					.findViewById(R.id.seek);
			mSeekbar.setProgress((int) (mSensorFusion.filter_coefficient * 100));
			mSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
				public void onProgressChanged(SeekBar seekBar, int progress,
						boolean fromTouch) {
					if (D)
						Log.d(TAG, Integer.toString(progress));
					mSensorFusion.tempFilter_coefficient = ((float) progress) / 100;
					value.setText(mSensorFusion.d
							.format(mSensorFusion.tempFilter_coefficient));
				}

				public void onStartTrackingTouch(SeekBar seekBar) {
				}

				public void onStopTrackingTouch(SeekBar seekBar) {
				}
			});
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle(R.string.dialog_title)
					// Set title
					// Add the buttons
					.setPositiveButton(R.string.ok,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int id) {
									mSensorFusion.filter_coefficient = mSensorFusion.tempFilter_coefficient;
									if (D)
										Log.d(TAG,
												"Filter Coefficient was set to: "
														+ mSensorFusion.filter_coefficient);
									// User clicked OK button
								}
							})
					.setNegativeButton(R.string.cancel,
							new DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int id) {
									mSensorFusion.tempFilter_coefficient = mSensorFusion.filter_coefficient;
									// User cancelled the dialog
								}
							})
					.setOnCancelListener(
							new DialogInterface.OnCancelListener() {
								@Override
								public void onCancel(DialogInterface dialog) {
									mSensorFusion.tempFilter_coefficient = mSensorFusion.filter_coefficient;
									// User pressed back, home etc.
								}
							})
					// Set custom view with Seekbar
					.setView(setCoefficient)
					// Create and show the AlertDialog
					.create().show();
			return true;
		case android.R.id.home:
			Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://balanduino.tkjelectronics.com/"));
			startActivity(browserIntent);
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	// The Handler that gets information back from the BluetoothChatService
	@SuppressLint("HandlerLeak")
	private final Handler mHandlerBluetooth = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			switch (msg.what) {
			case MESSAGE_STATE_CHANGE:
				supportInvalidateOptionsMenu();
				if (D)
					Log.i(TAG, "MESSAGE_STATE_CHANGE: " + msg.arg1);
				switch (msg.arg1) {
				case BluetoothChatService.STATE_CONNECTED:
					Toast.makeText(
							getApplicationContext(),
							getString(R.string.connected_to) + " "
									+ mConnectedDeviceName, Toast.LENGTH_SHORT)
							.show();
					if (mChatService == null) {
						return;
					}		
					Handler myHandler = new Handler();
					myHandler.postDelayed(new Runnable(){
				        public void run() {
				        	byte[] send = "G;".getBytes();
							mChatService.write(send, false);
				        }}, 1000); // Wait 1 second before sending the message
					break;
				case BluetoothChatService.STATE_CONNECTING:
					Toast.makeText(getApplicationContext(),
							R.string.connecting, Toast.LENGTH_SHORT).show();
					break;
				/*
				 * case BluetoothChatService.STATE_NONE:
				 * Toast.makeText(getApplicationContext
				 * (),R.string.title_not_connected,Toast.LENGTH_LONG).show();
				 * break;
				 */
				}
				PIDFragment.updateButton();
				break;
			case MESSAGE_READ: 
				PIDFragment.updateView();
				break;		
			case MESSAGE_DEVICE_NAME:
				// save the connected device's name
				mConnectedDeviceName = msg.getData().getString(DEVICE_NAME);
				break;
			case MESSAGE_TOAST:
				supportInvalidateOptionsMenu();
				Toast.makeText(getApplicationContext(),
						msg.getData().getString(TOAST), Toast.LENGTH_SHORT)
						.show();
				break;
			}
		}
	};

	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (D)
			Log.d(TAG, "onActivityResult " + resultCode);
		switch (requestCode) {
		/*
		 * case REQUEST_CONNECT_DEVICE_SECURE: // When DeviceListActivity
		 * returns with a device to connect if (resultCode ==
		 * Activity.RESULT_OK) { connectDevice(data, true); } break;
		 */
		case REQUEST_CONNECT_DEVICE_INSECURE:
			// When DeviceListActivity returns with a device to connect
			if (resultCode == Activity.RESULT_OK) {
				connectDevice(data, false);
			}
			break;
		case REQUEST_ENABLE_BT:
			// When the request to enable Bluetooth returns
			if (resultCode == Activity.RESULT_OK) {
				// Bluetooth is now enabled, so set up a chat session
				setupBTService();
			} else {
				// User did not enable Bluetooth or an error occured
				if (D)
					Log.d(TAG, "BT not enabled");
				Toast.makeText(getApplicationContext(),
						R.string.bt_not_enabled_leaving, Toast.LENGTH_SHORT)
						.show();
				finish();
			}
		}
	}

	private void connectDevice(Intent data, boolean secure) {
		// Get the device MAC address
		String address = data.getExtras().getString(
				DeviceListActivity.EXTRA_DEVICE_ADDRESS);
		// Get the BLuetoothDevice object
		BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
		// Attempt to connect to the device
		mChatService.connect(device, secure);
	}
}