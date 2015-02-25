package com.reicast.emulator;

import java.io.File;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.List;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnSystemUiVisibilityChangeListener;
import android.view.View.OnTouchListener;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.jeremyfeinstein.slidingmenu.lib.SlidingMenu;
import com.jeremyfeinstein.slidingmenu.lib.SlidingMenu.OnOpenListener;
import com.jeremyfeinstein.slidingmenu.lib.app.SlidingFragmentActivity;
import com.reicast.emulator.config.Config;
import com.reicast.emulator.config.InputFragment;
import com.reicast.emulator.config.OptionsFragment;
import com.reicast.emulator.debug.GenerateLogs;
import com.reicast.emulator.emu.JNIdc;
import com.reicast.emulator.periph.Gamepad;

public class MainActivity extends SlidingFragmentActivity implements
		FileBrowser.OnItemSelectedListener, OptionsFragment.OnClickListener {

	private SharedPreferences mPrefs;
	private static File sdcard = Environment.getExternalStorageDirectory();
	public static String home_directory = sdcard.getAbsolutePath();

	private TextView menuHeading;
	private boolean hasAndroidMarket = false;
	
	private SlidingMenu sm;
	
	private UncaughtExceptionHandler mUEHandler;

	private Intent debugger;
	public static boolean debugUser;

	Gamepad pad = new Gamepad();

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.mainuilayout_fragment);
		setBehindContentView(R.layout.drawer_menu);
		
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			getWindow().getDecorView().setOnSystemUiVisibilityChangeListener (new OnSystemUiVisibilityChangeListener() {
				public void onSystemUiVisibilityChange(int visibility) {
					if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
						getWindow().getDecorView().setSystemUiVisibility(
//                            View.SYSTEM_UI_FLAG_LAYOUT_STABLE | 
								View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                                | View.SYSTEM_UI_FLAG_FULLSCREEN
                                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
					}
				}
			});
		} else {
			getWindow().setFlags (WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
		}

		mPrefs = PreferenceManager.getDefaultSharedPreferences(this);

		debugger = new Intent("com.reicast.emulator.debug.Debug");
		debugger.setAction("reicast.emulator.DEBUG");
		if (isCallable(debugger)) {
			MainActivity.debugUser = true;
		}

		String prior_error = mPrefs.getString("prior_error", null);
		if (prior_error != null) {
			displayLogOutput(prior_error);
			mPrefs.edit().remove("prior_error").commit();
		} else {
			mUEHandler = new Thread.UncaughtExceptionHandler() {
				public void uncaughtException(Thread t, Throwable error) {
					if (error != null) {
						StringBuilder output = new StringBuilder();
						output.append("UncaughtException:\n");
						for (StackTraceElement trace : error.getStackTrace()) {
							output.append(trace.toString() + "\n");
						}
						String log = output.toString();
						mPrefs.edit().putString("prior_error", log).commit();
						error.printStackTrace();
						android.os.Process.killProcess(android.os.Process.myPid());
						System.exit(0);
					}
				}
			};
			Thread.setDefaultUncaughtExceptionHandler(mUEHandler);
		}

		home_directory = mPrefs.getString("home_directory", home_directory);

		Intent market = new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=dummy"));
		if (isCallable(market)) {
			hasAndroidMarket = true;
		}
		
		if (!getFilesDir().exists()) {
			getFilesDir().mkdir();
		}
		JNIdc.config(home_directory);
		
		// When viewing a resource, pass its URI to the native code for opening
		Intent intent = getIntent();
		if (intent.getAction() != null) {
			if (intent.getAction().equals(Intent.ACTION_VIEW))
				onGameSelected(Uri.parse(intent.getData().toString()));
		}

		// Check that the activity is using the layout version with
		// the fragment_container FrameLayout
		if (findViewById(R.id.fragment_container) != null) {

			// However, if we're being restored from a previous state,
			// then we don't need to do anything and should return or else
			// we could end up with overlapping fragments.
			if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB_MR1) {
				if (savedInstanceState != null) {
					return;
				}
			}

			// Create a new Fragment to be placed in the activity layout
			FileBrowser firstFragment = new FileBrowser();
			Bundle args = new Bundle();
			args.putBoolean("ImgBrowse", true);
			args.putString("browse_entry", null);
			// specify a path for selecting folder options
			args.putBoolean("games_entry", false);
			// specify if the desired path is for games or data
			firstFragment.setArguments(args);
			// In case this activity was started with special instructions from
			// an
			// Intent, pass the Intent's extras to the fragment as arguments
			// firstFragment.setArguments(getIntent().getExtras());

			// Add the fragment to the 'fragment_container' FrameLayout
			getSupportFragmentManager()
			.beginTransaction()
			.replace(R.id.fragment_container, firstFragment,
					"MAIN_BROWSER").commit();
		}

		menuHeading = (TextView) findViewById(R.id.menu_heading);

		sm = getSlidingMenu();
		sm.setShadowWidthRes(R.dimen.shadow_width);
		sm.setShadowDrawable(R.drawable.shadow);
		sm.setBehindOffsetRes(R.dimen.slidingmenu_offset);
		sm.setFadeDegree(0.35f);
		sm.setTouchModeAbove(SlidingMenu.TOUCHMODE_MARGIN);
		sm.setOnOpenListener(new OnOpenListener() {
			public void onOpen() {
				findViewById(R.id.browser_menu).setOnClickListener(new OnClickListener() {
					public void onClick(View view) {
						FileBrowser browseFrag = (FileBrowser) getSupportFragmentManager()
								.findFragmentByTag("MAIN_BROWSER");
						if (browseFrag != null) {
							if (browseFrag.isVisible()) {
								return;
							}
						}
						browseFrag = new FileBrowser();
						Bundle args = new Bundle();
						args.putBoolean("ImgBrowse", true);
						args.putString("browse_entry", null);
						// specify a path for selecting folder options
						args.putBoolean("games_entry", false);
						// specify if the desired path is for games or data
						browseFrag.setArguments(args);
						getSupportFragmentManager()
						.beginTransaction()
						.replace(R.id.fragment_container, browseFrag,
								"MAIN_BROWSER").addToBackStack(null)
								.commit();
						setTitle(R.string.browser);
						sm.toggle(true);
					}

				});

				findViewById(R.id.settings_menu).setOnClickListener(
						new OnClickListener() {
							public void onClick(View view) {
								OptionsFragment optionsFrag = (OptionsFragment) getSupportFragmentManager()
										.findFragmentByTag("OPTIONS_FRAG");
								if (optionsFrag != null) {
									if (optionsFrag.isVisible()) {
										return;
									}
								}
								optionsFrag = new OptionsFragment();
								getSupportFragmentManager()
								.beginTransaction()
								.replace(R.id.fragment_container,
										optionsFrag, "OPTIONS_FRAG")
										.addToBackStack(null).commit();
								setTitle(R.string.settings);
								sm.toggle(true);
							}

						});

				findViewById(R.id.input_menu).setOnClickListener(new OnClickListener() {
					public void onClick(View view) {
						InputFragment inputFrag = (InputFragment) getSupportFragmentManager()
								.findFragmentByTag("INPUT_FRAG");
						if (inputFrag != null) {
							if (inputFrag.isVisible()) {
								return;
							}
						}
						inputFrag = new InputFragment();
						getSupportFragmentManager()
						.beginTransaction()
						.replace(R.id.fragment_container, inputFrag,
								"INPUT_FRAG").addToBackStack(null).commit();
						setTitle(R.string.input);
						sm.toggle(true);
					}

				});

				findViewById(R.id.about_menu).setOnClickListener(new OnClickListener() {
					public void onClick(View view) {
						AboutFragment aboutFrag = (AboutFragment) getSupportFragmentManager()
								.findFragmentByTag("ABOUT_FRAG");
						if (aboutFrag != null) {
							if (aboutFrag.isVisible()) {
								return;
							}
						}
						aboutFrag = new AboutFragment();
						getSupportFragmentManager()
						.beginTransaction()
						.replace(R.id.fragment_container, aboutFrag,
								"ABOUT_FRAG").addToBackStack(null).commit();
						setTitle(R.string.about);
						sm.toggle(true);
					}

				});
				
				findViewById(R.id.cloud_menu).setOnClickListener(new OnClickListener() {
					public void onClick(View view) {
						CloudFragment cloudFrag = (CloudFragment) getSupportFragmentManager()
								.findFragmentByTag("CLOUD_FRAG");
						if (cloudFrag != null) {
							if (cloudFrag.isVisible()) {
								return;
							}
						}
						cloudFrag = new CloudFragment();
						getSupportFragmentManager()
						.beginTransaction()
						.replace(R.id.fragment_container,
						cloudFrag, "CLOUD_FRAG")
							.addToBackStack(null).commit();
						setTitle(R.string.cloud);
						sm.toggle(true);
					}

				});

				View rateMe = findViewById(R.id.rateme_menu);
				if (!hasAndroidMarket) {
					rateMe.setVisibility(View.GONE);
				} else {
					rateMe.setOnTouchListener(new OnTouchListener() {
						public boolean onTouch(View v, MotionEvent event) {
							if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
								// vib.vibrate(50);
								startActivity(new Intent(Intent.ACTION_VIEW, Uri
										.parse("market://details?id="
												+ getPackageName())));
								//setTitle(R.string.rateme);
								sm.toggle(true);
								return true;
							} else
								return false;
						}
					});
				}
				


				View messages = findViewById(R.id.message_menu);
				if (MainActivity.debugUser) {
					messages.setOnClickListener(new OnClickListener() {
						public void onClick(View view) {
							startActivity(debugger);
						}
					});
				} else {
					messages.setOnClickListener(new OnClickListener() {
						public void onClick(View view) {
							generateErrorLog();
						}
					});
				}
			}
		});
		findViewById(R.id.header_list).setOnTouchListener(new OnTouchListener() {
			public boolean onTouch(View v, MotionEvent event) {
				if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
					sm.toggle(true);
					return true;
				} else
					return false;
			}
		});
	}
	
	public void generateErrorLog() {
		new GenerateLogs(MainActivity.this).execute(getFilesDir().getAbsolutePath());
	}

	/**
	 * Display a dialog to notify the user of prior crash
	 * 
	 * @param string
	 *            A generalized summary of the crash cause
	 * @param bundle
	 *            The savedInstanceState passed from onCreate
	 */
	private void displayLogOutput(final String error) {
		AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
		builder.setTitle(R.string.report_issue);
		builder.setMessage(error);
		builder.setNegativeButton(R.string.dismiss,
				new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						dialog.dismiss();
					}
				});
		builder.create();
		builder.show();
	}

    public static boolean isBiosExisting(Context context) {
        SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        home_directory = mPrefs.getString("home_directory", home_directory);
        File bios = new File(home_directory, "data/dc_boot.bin");
        return bios.exists();
    }

    public static boolean isFlashExisting(Context context) {
        SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(context);
        home_directory = mPrefs.getString("home_directory", home_directory);
        File flash = new File(home_directory, "data/dc_flash.bin");
        return flash.exists();
    }

	public void onGameSelected(Uri uri) {
		if (Config.readOutput("uname -a").equals(getString(R.string.error_kernel))) {
			MainActivity.showToastMessage(MainActivity.this, getString(R.string.unsupported), Toast.LENGTH_SHORT);
		}
		String msg = null;
		if (!isBiosExisting(MainActivity.this))
			msg = getString(R.string.missing_bios, home_directory);
		else if (!isFlashExisting(MainActivity.this))
			msg = getString(R.string.missing_flash, home_directory);

		if (msg != null) {
			AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(
					this);

			// set title
			if (!isBiosExisting(MainActivity.this))
				alertDialogBuilder.setTitle(R.string.missing_bios_title);
			else if (!isFlashExisting(MainActivity.this))
				alertDialogBuilder.setTitle(R.string.missing_flash_title);

			// set dialog message
			alertDialogBuilder
			.setMessage(msg)
			.setCancelable(false)
			.setPositiveButton(R.string.dismiss,
					new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					// if this button is clicked, close
					// current activity
					// MainActivity.this.finish();
				}
			})
			.setNegativeButton(R.string.options,
					new DialogInterface.OnClickListener() {
				public void onClick(DialogInterface dialog, int id) {
					FileBrowser firstFragment = new FileBrowser();
					Bundle args = new Bundle();
					// args.putBoolean("ImgBrowse", false);
					// specify ImgBrowse option. true = images,
					// false = folders only
					args.putString("browse_entry", sdcard.toString());
					// specify a path for selecting folder
					// options
					args.putBoolean("games_entry", false);
					// selecting a BIOS folder, so this is not
					// games

					firstFragment.setArguments(args);
					// In case this activity was started with
					// special instructions from
					// an Intent, pass the Intent's extras to
					// the fragment as arguments
					// firstFragment.setArguments(getIntent().getExtras());

					// Add the fragment to the
					// 'fragment_container' FrameLayout
					getSupportFragmentManager()
					.beginTransaction()
					.replace(R.id.fragment_container,
							firstFragment,
							"MAIN_BROWSER")
							.addToBackStack(null).commit();
				}
			});

			// create alert dialog
			AlertDialog alertDialog = alertDialogBuilder.create();

			// show it
			alertDialog.show();
		} else {
			Config.nativeact = PreferenceManager.getDefaultSharedPreferences(getApplicationContext()).getBoolean(Config.pref_nativeact, Config.nativeact);
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD && Config.nativeact) {
				startActivity(new Intent("com.reicast.EMULATOR", uri, getApplicationContext(),
						GL2JNINative.class));
			} else {
				startActivity(new Intent("com.reicast.EMULATOR", uri, getApplicationContext(),
						GL2JNIActivity.class));
			}
		}
	}

	public void onFolderSelected(Uri uri) {
		FileBrowser browserFrag = (FileBrowser) getSupportFragmentManager()
				.findFragmentByTag("MAIN_BROWSER");
		if (browserFrag != null) {
			if (browserFrag.isVisible()) {

				Log.d("reicast", "Main folder: " + uri.toString());
				// return;
			}
		}

		OptionsFragment optsFrag = new OptionsFragment();
		getSupportFragmentManager().beginTransaction()
				.replace(R.id.fragment_container, optsFrag, "OPTIONS_FRAG")
				.commit();
		return;
	}

	public void onMainBrowseSelected(String path_entry, boolean games) {
		FileBrowser firstFragment = new FileBrowser();
		Bundle args = new Bundle();
		args.putBoolean("ImgBrowse", false);
		// specify ImgBrowse option. true = images, false = folders only
		args.putString("browse_entry", path_entry);
		// specify a path for selecting folder options
		args.putBoolean("games_entry", games);
		// specify if the desired path is for games or data

		firstFragment.setArguments(args);
		// In case this activity was started with special instructions from
		// an Intent, pass the Intent's extras to the fragment as arguments
		// firstFragment.setArguments(getIntent().getExtras());

		// Add the fragment to the 'fragment_container' FrameLayout
		getSupportFragmentManager()
				.beginTransaction()
				.replace(R.id.fragment_container, firstFragment, "MAIN_BROWSER")
				.addToBackStack(null).commit();
	}

	@SuppressLint("NewApi")
	@Override
	public void setTitle(CharSequence title) {
		menuHeading.setText(title);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			Fragment fragment = (FileBrowser) getSupportFragmentManager()
					.findFragmentByTag("MAIN_BROWSER");
			if (fragment != null && fragment.isVisible()) {
				boolean readyToQuit = true;
				if (fragment.getArguments() != null) {
					readyToQuit = fragment.getArguments().getBoolean(
							"ImgBrowse", true);
				}
				if (readyToQuit) {
					MainActivity.this.finish();
				} else {
					launchMainFragment(fragment);
				}
				return true;
			} else {
				launchMainFragment(fragment);
				return true;
			}

		}
		if (keyCode == KeyEvent.KEYCODE_MENU) {
			sm.toggle(true);
		}

		return super.onKeyDown(keyCode, event);
	}
	
	private void launchMainFragment(Fragment fragment) {
		fragment = new FileBrowser();
		Bundle args = new Bundle();
		args.putBoolean("ImgBrowse", true);
		args.putString("browse_entry", null);
		args.putBoolean("games_entry", false);
		fragment.setArguments(args);
		getSupportFragmentManager().beginTransaction()
				.replace(R.id.fragment_container, fragment, "MAIN_BROWSER")
				.addToBackStack(null).commit();
		setTitle(R.string.browser);
	}

	@Override
	protected void onPause() {
		super.onPause();
		InputFragment fragment = (InputFragment) getSupportFragmentManager()
				.findFragmentByTag("INPUT_FRAG");
		if (fragment != null && fragment.isVisible()) {
			if (fragment.moga != null) {
				fragment.moga.onPause();
			}
		}
	}

	@Override
	protected void onDestroy() {
		InputFragment fragment = (InputFragment) getSupportFragmentManager()
				.findFragmentByTag("INPUT_FRAG");
		if (fragment != null && fragment.isVisible()) {
			if (fragment.moga != null) {
				fragment.moga.onDestroy();
			}
		}
		super.onDestroy();
	}

	@Override
	protected void onResume() {
		super.onResume();
		InputFragment fragment = (InputFragment) getSupportFragmentManager()
				.findFragmentByTag("INPUT_FRAG");
		if (fragment != null && fragment.isVisible()) {
			if (fragment.moga != null) {
				fragment.moga.onResume();
			}
		}
		
		CloudFragment cloudfragment = (CloudFragment) getSupportFragmentManager()
				.findFragmentByTag("CLOUD_FRAG");
		if (cloudfragment != null && cloudfragment.isVisible()) {
			if (cloudfragment != null) {
				cloudfragment.onResume();
			}
		}
	}
	
	@Override
	public void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
	}
	
	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
			getWindow().getDecorView().setSystemUiVisibility(
					View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
	                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
	                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
	                | View.SYSTEM_UI_FLAG_FULLSCREEN
	                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
	}

	public boolean isCallable(Intent intent) {
		List<ResolveInfo> list = getPackageManager().queryIntentActivities(
				intent, PackageManager.MATCH_DEFAULT_ONLY);
		return list.size() > 0;
	}
	
	public static void showToastMessage(Context context, String message,
			int duration) {
		LayoutInflater inflater = (LayoutInflater) context
				.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		View layout = inflater.inflate(R.layout.toast_layout, null);

		ImageView image = (ImageView) layout.findViewById(R.id.image);
		image.setImageResource(R.drawable.ic_notification);
		TextView text = (TextView) layout.findViewById(R.id.text);
		text.setText(message);

		DisplayMetrics metrics = new DisplayMetrics();
		WindowManager winman = (WindowManager) context
				.getSystemService(Context.WINDOW_SERVICE);
		winman.getDefaultDisplay().getMetrics(metrics);
		final float scale = context.getResources().getDisplayMetrics().density;
		int toastPixels = (int) ((metrics.widthPixels * scale + 0.5f) / 18);

		Toast toast = new Toast(context);
		toast.setGravity(Gravity.BOTTOM, 0, toastPixels);
		toast.setDuration(duration);
		toast.setView(layout);
		toast.show();
	}
}
