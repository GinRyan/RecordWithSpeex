package com.ryong21;

import com.ryong21.manager.ClientManager;
import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MyRecorder extends Activity implements OnClickListener {
	ClientManager clientManager = new ClientManager();
	Button startButton = null;
	Button playButon = null;
	Button stopButton = null;
	Button exitButon = null;
	TextView textView = null;

	public void onClick(View v) {
		if (v == startButton) {
			this.setTitle("Started!");
				clientManager.setClientMode(ClientManager.SING);
				clientManager.setRecordMode(ClientManager.NETONLY);
				clientManager.setRunning(true);
		}
		if (v == playButon) {
			this.setTitle("Playing!");
			clientManager.setRunning(false);
			try {
				Thread.sleep(200);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			clientManager.setClientMode(ClientManager.PLAY);
			clientManager.setRunning(true);
		}
		if (v == stopButton) {
			this.setTitle("Stoped!");
			clientManager.setRunning(false);
		}
		if (v == exitButon) {
			clientManager.setRunning(false);
			clientManager.setNeedExit(true);
			System.exit(0);
		}		
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		startButton = new Button(this);
		playButon = new Button(this);
		stopButton = new Button(this);
		exitButon = new Button(this);
		textView = new TextView(this);

		startButton.setText("Start Record");
		playButon.setText("Playback");
		stopButton.setText("Stop");
		exitButon.setText("Exit");
		textView.setText("Android Recorder ChangeLog£º" +
				"\n(1)Get PCM data." +
				"\n(2)Encode with speex." +
				"\n(3)Package in flv format" +
				"\n(4)Publish audio to server." +
				"\n(5)Record both local and server side." +
				"\n(6)Play what you published");

		startButton.setOnClickListener(this);
		stopButton.setOnClickListener(this);
		exitButon.setOnClickListener(this);
		playButon.setOnClickListener(this);

		LinearLayout layout = new LinearLayout(this);
		layout.setOrientation(LinearLayout.VERTICAL);
		layout.addView(textView);
		layout.addView(startButton);
		layout.addView(playButon);
		layout.addView(stopButton);
		layout.addView(exitButon);
		this.setContentView(layout);
		this.setTitle("Android Recorder");

		clientManager.setNeedExit(false);
		Thread cmThread = new Thread(clientManager);
		cmThread.start();
	}
}