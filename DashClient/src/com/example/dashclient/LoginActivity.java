package com.example.dashclient;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.example.dashclient.DashHttpClient.OnSignInListener;

public class LoginActivity extends Activity implements OnSignInListener {
	TextView mIdView, mPasswdView;
	Button mSignupBtn, mSigninBtn;
	DashHttpClient mDashHttpClient;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_login);

		init();

	}

	void init() {
		mIdView = (TextView) findViewById(R.id.idtxt);
		mPasswdView = (TextView) findViewById(R.id.passwdtxt);
		mSignupBtn = (Button) findViewById(R.id.upBtn);
		mSigninBtn = (Button) findViewById(R.id.inBtn);

		mDashHttpClient = new DashHttpClient();
		mDashHttpClient.setOnSignInListener(this);

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	public void mOnClick(View v) {
		if (v.getId() == mSignupBtn.getId()) {
			Intent intent = new Intent(LoginActivity.this, SignupActivity.class);
			startActivity(intent);
		} else if (v.getId() == mSigninBtn.getId()) {
			String email = mIdView.getText().toString();
			String password = mPasswdView.getText().toString();

			if (email.trim().length() == 0) {
				Toast.makeText(getApplicationContext(), "아이디를 입력하세요.",
						Toast.LENGTH_SHORT).show();
				mIdView.requestFocus();
				return;
			}

			if (password.trim().length() == 0) {
				Toast.makeText(getApplicationContext(), "비밀번호를 입력하세요.",
						Toast.LENGTH_SHORT).show();
				return;
			}

			mDashHttpClient.signIn(email, password);
			//Intent intent = new Intent(LoginActivity.this, MainActivity.class);
			//startActivity(intent);

		}
	}

	@Override
	public void onFailure(int event, String message) {
		Toast.makeText(this, String.format("event: %d, %s", event, message),	Toast.LENGTH_SHORT).show();
	}

	@Override
	public void onSignIn() {
		Toast.makeText(this, "로그인 되었습니다.", Toast.LENGTH_SHORT).show();
		Intent intent = new Intent(LoginActivity.this, MainActivity.class);
		startActivity(intent);
	}

}