package com.example.dashclient;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

public class SignupActivity extends Activity implements SocketHandler {
	EditText mNameTxt, mEmailTxt;
	EditText mPasswdTxt, mPasswdTxt2;
	Button mSignupBtn;
	String mName, mEmail, mPasswd, mPasswd2;
	
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_signup);
		
		init();
		
		SocketService.addActivity(getName(), this);
		SocketService.addHandler(this);
	}
	
	public void init(){
		mNameTxt = (EditText)findViewById(R.id.nametxt);
		mEmailTxt = (EditText)findViewById(R.id.emailtxt);
		mPasswdTxt = (EditText)findViewById(R.id.passwdtxt);
		mPasswdTxt2 = (EditText)findViewById(R.id.passwd2txt);
		mSignupBtn = (Button)findViewById(R.id.confirmBtn);
	}
	
	public void mOnClick(View v){
		if(v.getId()==mSignupBtn.getId()){
			mName = mNameTxt.getText().toString();
			mEmail = mEmailTxt.getText().toString();
			mPasswd = mPasswdTxt.getText().toString();
			mPasswd2 = mPasswdTxt2.getText().toString();
			if(!mPasswd.equals(mPasswd2)){
				Toast.makeText(getApplicationContext(), "Confirm Password", Toast.LENGTH_SHORT).show();
			}else{
				// TODO 나중에 밸리데이션 체크
				
				SocketService.getInstance().signUp(mEmail, mPasswd, mName);
				
			}
		}
	}

	@Override
	public String getName() {
		return "signUp";
	}

	@Override
	public void onSuccess() {
		Toast.makeText(getApplicationContext(), "회원 가입 되었습니다.", Toast.LENGTH_SHORT).show();
		finish();
	}

	@Override
	public void onSuccess(Object... args) {
		
	}

	@Override
	public void onFailure(String message) {

		Toast.makeText(getApplicationContext(), "회원 가입을 하는 도중 오류가 발생하였습니다.", Toast.LENGTH_SHORT).show();
	}
}
