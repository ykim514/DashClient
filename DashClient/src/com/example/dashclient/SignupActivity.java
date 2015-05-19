package com.example.dashclient;

import com.example.dashclient.DashHttpClient.OnSignUpListener;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class SignupActivity extends Activity implements OnSignUpListener {
	EditText mNameTxt, mEmailTxt;
	EditText mPasswdTxt, mPasswdTxt2;
	Button mSignupBtn;
	String mName, mEmail, mPasswd, mPasswd2;
	DashHttpClient mDashHttpClient;
	
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_signup);
		
		init();
		
	}
	
	public void init(){
		mNameTxt = (EditText)findViewById(R.id.nametxt);
		mEmailTxt = (EditText)findViewById(R.id.emailtxt);
		mPasswdTxt = (EditText)findViewById(R.id.passwdtxt);
		mPasswdTxt2 = (EditText)findViewById(R.id.passwd2txt);
		mSignupBtn = (Button)findViewById(R.id.confirmBtn);
		mDashHttpClient = new DashHttpClient();
		mDashHttpClient.setOnSignUpListener(this);
	}
	
	public void mOnClick(View v){
		if(v.getId()==mSignupBtn.getId()){
			mName = mNameTxt.getText().toString();
			mEmail = mEmailTxt.getText().toString();
			mPasswd = mPasswdTxt.getText().toString();
			mPasswd2 = mPasswdTxt2.getText().toString();
			if(mName == null){
				Toast.makeText(this, "이름을 입력해주세요.", Toast.LENGTH_SHORT).show();
				return;
			}
			if(mEmail == null){
				Toast.makeText(this, "이메일을 입력해주세요.", Toast.LENGTH_SHORT).show();
				return;
			}
			if(mPasswd == null || mPasswd2 == null){
				Toast.makeText(this, "패스워드를 입력해주세요.", Toast.LENGTH_SHORT).show();
				return;
			}
			if(!mPasswd.equals(mPasswd2)){
				Toast.makeText(getApplicationContext(), "패스워드가 일치하지 않습니다.", Toast.LENGTH_SHORT).show();
				return;
			}else{
				mDashHttpClient.signUp(mEmail, mName, mPasswd);
				
			}
		}
	}

	@Override
	public void onFailure(int event, String message) {
		 Toast.makeText(this, String.format("event: %d, %s", event, message), Toast.LENGTH_SHORT).show();
	}

	@Override
	public void onSignUp() {
		 Toast.makeText(this, "회원가입이 완료되었습니다.", Toast.LENGTH_SHORT).show();
		finish();
	}

	
}
