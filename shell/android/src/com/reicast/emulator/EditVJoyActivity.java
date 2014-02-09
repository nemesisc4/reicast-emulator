package com.reicast.emulator;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.widget.ImageButton;
import android.widget.ImageView.ScaleType;
import android.widget.PopupWindow;
import android.widget.Toast;

@TargetApi(Build.VERSION_CODES.HONEYCOMB_MR1)
public class EditVJoyActivity extends Activity {
	GL2JNIView mView;
	GL2JNIViewV6 mView6;
	OnScreenMenu menu;
	PopupWindow popUp;
	LayoutParams params;
	
	public static float[][] vjoy_d_cached;

	View addbut(int x, OnClickListener ocl) {
		ImageButton but = new ImageButton(this);

		but.setImageResource(x);
		but.setScaleType(ScaleType.FIT_CENTER);
		but.setOnClickListener(ocl);

		return but;
	}

	@Override
	protected void onCreate(Bundle icicle) {
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		
		menu = new OnScreenMenu(this);
		menu.setGLView(mView, mView6);
		popUp = menu.createVjoyPopup();

		// Call parent onCreate()
		super.onCreate(icicle);

		// Create the actual GLES view
		if (MainActivity.force_gpu) {
			mView6 = new GL2JNIViewV6(getApplication(), null, false, 24, 0, true);
			setContentView(mView6);
			
			vjoy_d_cached = GL2JNIView.readCustomVjoyValues(getApplicationContext());
		} else {
			mView = new GL2JNIView(getApplication(), null, false, 24, 0, true);
			setContentView(mView);
			
			vjoy_d_cached = GL2JNIView.readCustomVjoyValues(getApplicationContext());
		}

        JNIdc.show_osd();

		Toast.makeText(getApplicationContext(),
				"Press the back button for a menu", Toast.LENGTH_SHORT).show();
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (MainActivity.force_gpu) {
			mView6.onPause();
		} else {
			mView.onPause();
		}
	}

	@Override
	protected void onStop() {
		if (MainActivity.force_gpu) {
			mView6.onStop();
		} else {
			mView.onStop();
		}
		super.onStop();
	}


	@Override
	protected void onResume() {
		super.onResume();
		if (MainActivity.force_gpu) {
			mView6.onResume();
		} else {
			mView.onResume();
		}
	}

	public boolean onKeyDown(int keyCode, KeyEvent event) {
		if (keyCode == KeyEvent.KEYCODE_MENU
				|| keyCode == KeyEvent.KEYCODE_BACK) {
			if (!popUp.isShowing()) {
				if (MainActivity.force_gpu) {
					popUp.showAtLocation(mView6, Gravity.BOTTOM, 0, 0);
				} else {
					popUp.showAtLocation(mView, Gravity.BOTTOM, 0, 0);
				}
				popUp.update(LayoutParams.WRAP_CONTENT,
						LayoutParams.WRAP_CONTENT);

			} else {
				popUp.dismiss();
			}

			return true;
		} else
			return super.onKeyDown(keyCode, event);
	}
	
	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
	}
}
