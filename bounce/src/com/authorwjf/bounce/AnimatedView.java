package com.authorwjf.bounce;

import static com.authorwjf.bounce.BluetoothChatService.KILOMETERS_IN_A_MILE;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;
import android.widget.Toast;

public class AnimatedView extends ImageView{

	private Context mContext;
	int x = -1;
	int y = -1;
	//private int xVelocity = 10;
	private int yVelocity = 5;
	private Handler h;
	private final int FRAME_RATE = 30;
	private int sashay = 0;
	private int flambe = 15;
	private double scoreNum = 0; 
	private double amount = scoreNum/100;
	private int scoreDec = 0;
	private String getURL = "http://omnidrive.herokuapp.com/getscore?fbid="; 

	private int timeSinceLastRec = 0;

	private long counter = 0;

	String rec = "";
	int recCountup = 0;
	int status = 0;
	boolean scrollIn = false;
	private double speed = 0;


	// Image data
	private int winWidth = -1;
	private int winHeight = -1;
	private int greenWidth = -1;
	private int greenHeight = -1;
	private int grayWidth = -1;
	private int grayHeight = -1;
	private int ballWidth = -1;
	private BitmapDrawable greenCircle = null;
	private BitmapDrawable grayCircle = null;
	private BitmapDrawable ball = null;
	private MediaPlayer mp1;
	private long last;
	private Bitmap grayMap;
	private int[] grayPixels;

	private double lastMPG = 0;

	private int coinCounter = 0;
	private double lastCoinCheck = 0;
	private ArrayList<Double> avgMpgThisDrive = new ArrayList<Double>();
	private HashMap<String, Integer> recTracker = new HashMap<String, Integer>();

	public static int totalCoinsWon = 0;
	public static int rep = 0;
	public static String recommendedTip = "";
	public static double avgMpg = 0;
	public static long totalDriveTime = 0;

	private long startDriveTime = 0;

	private double currentSpeed = -1;
	private double speedDeltaTolerance = 100;
	private final double TOLERANCE_SCALE_DECREASE = 0.9;
	private double engineLoadTolerance = 100;

	private int slowSpeed = 0;
	private long speedingWarning = 0;

	private final int MAX_TOLERANCE = 100;

	public AnimatedView(Context context, AttributeSet attrs)  {  
		super(context, attrs);  
		mContext = context;  
		h = new Handler();
		getURL += Main.fbid;
		updateCoins(false);
		startDriveTime = System.currentTimeMillis();
	} 

	private Runnable r = new Runnable() {
		@Override
		public void run() {
			invalidate(); 
		}
	};

	private String getRecommendation()	{
		speedingWarning++;
		String speed = BluetoothChatService.retrieveDatum("speed");
		if(speed != null)	{
			double now = Double.parseDouble(speed);
			if(now > 65 * KILOMETERS_IN_A_MILE && speedingWarning > 100){
				speedingWarning = 0;
				return "Try to avoid going too quickly.";
			}
			else if(now < 5 * KILOMETERS_IN_A_MILE && ++slowSpeed > 100){
				slowSpeed = 0;
				return "Avoid idling your engine.";
			}
			slowSpeed = 0;
			double delta = now - currentSpeed;
			if(currentSpeed < 0)	{
				delta = 0;
			}
			currentSpeed = now;
			if(delta >= speedDeltaTolerance)	{
				speedDeltaTolerance = MAX_TOLERANCE;
				return "Try to avoid flooring the accelerator.";
			}
			else if(delta <= -speedDeltaTolerance) {
				speedDeltaTolerance = MAX_TOLERANCE;
				return "Try to avoid flooring the brake pedal.";
			}
		}
		engineLoadTolerance *= TOLERANCE_SCALE_DECREASE;
		String engineLoad = BluetoothChatService.retrieveDatum("engine load");
		if(engineLoad != null && Double.parseDouble(engineLoad) >= engineLoadTolerance) {
			engineLoadTolerance = 100;
			return "Don't push the engine too hard - avoid sudden changes in speed.";
		}
		speedDeltaTolerance *= TOLERANCE_SCALE_DECREASE;
		return "No recommendation.";
	}

	/* Updates the player's total number of coins to the database once the drive
	 * has concluded
	 */

	private class updateCoinsTask extends AsyncTask<String, Integer, Integer> {

		@Override
		protected Integer doInBackground(String... params) {
			try {
				HttpClient client = new DefaultHttpClient();
				String url = "http://omnidrive.herokuapp.com/getcoins?coins=" + totalCoinsWon;
				HttpGet get = new HttpGet(url);
				HttpResponse responseGet = client.execute(get);
			} catch (ClientProtocolException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return 0; 
		}
	}

	/* Gets the initial number of coins from the database.  The total number of coins won
	 * is updated based on this initial number.
	 */
	private class getInitialCoinsTask extends AsyncTask<String, Integer, Integer> {

		@Override
		protected Integer doInBackground(String... params) {
			try {
				HttpClient client = new DefaultHttpClient();
				String url = "http://www.omnidrive.io/api/?fbid="+ Main.fbid;
				HttpGet get = new HttpGet(url);
				HttpResponse responseGet = client.execute(get);
				HttpEntity responseEntity = responseGet.getEntity();
				String response = EntityUtils.toString(responseEntity);
				if (!response.equals(null)) {
					JSONObject currUser;
					try {
						currUser = new JSONObject(response);
						String curCoinsString = currUser.optString("coins");
						if(curCoinsString != null) {
							int initialCoins = Integer.parseInt(curCoinsString);
							totalCoinsWon += initialCoins;
						}
					} catch (JSONException e) {
						e.printStackTrace();
					}
				}
			} catch (ClientProtocolException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return 0; 
		}
	}

	private class setScoreTask extends AsyncTask<String, Integer, Integer> {

		@Override
		protected Integer doInBackground(String... params) {
			double mpg = BluetoothChatService.currentMPG;
			if (mpg != scoreNum) {
				lastMPG = scoreNum;
				scoreNum = mpg;
			}
			scoreNum = mpg;
			avgMpgThisDrive.add(mpg);
			return 0;
		}

	}

	private void updateCoins(boolean update) {
		if (update) {
			(new updateCoinsTask()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, getURL);
		} else {
			(new getInitialCoinsTask()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, getURL);
		}
	}

	private void setScore() {

		(new setScoreTask()).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, getURL);
	}

	private void drawRecommendation(Canvas c, Paint scorePaint)	{
		Rect notification = new Rect(0, getHeight() - status, getWidth(), getHeight());

		scorePaint.setColor(Color.parseColor("#3498db"));
		scorePaint.setStyle(Style.FILL);
		c.drawRect(notification, scorePaint);
		scorePaint.setTextSize(40);
		scorePaint.setColor(Color.WHITE);
		Rect recBounds = new Rect();
		scorePaint.getTextBounds(rec, 0, rec.length(), recBounds);
		c.drawText(rec, this.getWidth()/2 - recBounds.width() / 2, this.getHeight() - status + recBounds.height() + 12, scorePaint);

	}

	private double calculateAverageMPG() {
		int sum = 0;
		for (int i=0; i< avgMpgThisDrive.size(); i++) {
			sum += i;
		}
		return sum / (avgMpgThisDrive.size() + 0.1);
	}

	private void drawCircles(BitmapDrawable greenCircle, BitmapDrawable grayCircle, Canvas c, double amt) {
		Paint scorePaint = new Paint();
		if (scoreNum <=25) {
			scorePaint.setColor(Color.RED);
		} else {
			scorePaint.setColor(Color.WHITE);
		}
		scorePaint.setTextSize(200);


		if (scoreDec == 4) {
			setScore();
			double difference = scoreNum - lastMPG;
			//amt = scoreNum/100;
			//amount = amt;
			if (difference < 0 ) {
				speed = -.0001 * (-1) * difference;
			} else {
				speed = 0.0001 * difference;
			}
			scoreDec = 0;
			if (scoreNum == 99) {
				last = System.currentTimeMillis();
			}
			else if (scoreNum % 10 == 0) {
				long curr = System.currentTimeMillis();
				last = curr;
			}
		} else {
			scoreDec++;
		}

		String score = String.format("%.2g%n", scoreNum);
		winWidth = this.getWidth();
		winHeight = this.getHeight();

		Rect bounds = new Rect();
		scorePaint.getTextBounds(score, 0, score.length(), bounds);

		int scoreX = winWidth/2  - bounds.width()/2 - 15;
		int scoreY = greenWidth/2 + bounds.height()/2 - 10;

		int endX = grayWidth;
		int endY = (int)(grayHeight * (1 - amount));
		int greenX = winWidth/2  - greenWidth/2;
		int grayX = winWidth/2  - grayWidth/2;

		c.drawBitmap(greenCircle.getBitmap(), greenX, 10, null);  
		c.drawBitmap(grayPixels, 0, grayMap.getWidth(), grayX, 10, endX, endY, false, null);

		//c.drawBitmap(transform, grayX, 10, null);  
		timeSinceLastRec++;

		c.drawText(score, scoreX, scoreY, scorePaint);
		if (lastMPG > 0 && (scoreNum - lastMPG < 0) && timeSinceLastRec > 600) {
			if (rec.equals("")) {
				rec = getRecommendation();
				if (recTracker.containsKey(rec)) {
					int curNumber = recTracker.get(rec);
					recTracker.put(rec, curNumber++);
				} else {
					recTracker.put(rec, 1);
				}
				timeSinceLastRec = 0;

				mp1.start();
				scrollIn = true;
			} else {
				if (scrollIn) {
					if (status < 80) {
						status += 10;
					} else {
						scrollIn = false;
					}
				} else {
					recCountup++;
					if (recCountup > 80) {
						status -= 10;
					}
				}

				if (status > -10) {
					drawRecommendation(c, scorePaint);
				}
			}
		} else {
			// reset recommendation variables 
			rec = "";
			recCountup = 0;
			scrollIn = false;
			status = 0;
		}

		if (coinCounter == 50) {
			double diff = scoreNum - lastCoinCheck;
			if (diff > 0) {
				makeToast(diff);
			}
			coinCounter = 0;
			lastCoinCheck = scoreNum;

		}

		coinCounter++;
	}

	private void makeToast(double diff) {
		Context context = mContext;
		CharSequence text = "Great job!! + " + (int)diff + " coins!";
		int duration = Toast.LENGTH_LONG;
		totalCoinsWon += (int)diff;
		rep += (int) diff;

		Toast toast = Toast.makeText(context, text, duration);
		toast.show();
	}

	/* Adjust flambe speed around a mean of 15.  Note: The speed of the droplet descent isn't changed.
	 * Only the amount of time between droplets is changed. The higher this amount of time, the "slower"
	 * the droplets, and the more your score is improving at the moment. */
	private void adjustFlambe() {
		if (scoreNum > 0 && lastMPG > 0) {
			int improvement = (int) (scoreNum - lastMPG);
			/* only change flambe if a reasonable number is found for scoreNum - lastMPG.  If
			 * an out-of-range improvement is found, it is likely because the car is just starting up. The
			 * droplets should not change their speed in this case.
			 */
			if (Math.abs(improvement) < 5) {
				flambe += improvement; 
			}
		}
		/* Last check to ensure flambe is within a reasonable range */
		if (flambe < 5) {
			flambe = 5;
		} else if (flambe > 25) {
			flambe = 25;
		}
	}

	private String updateBestTip() {
		String bestRec = "";
		int maxHits = 0;    
		for(String key : recTracker.keySet()) {
			int currentHits = recTracker.get(key);
			if (currentHits > maxHits) {
				bestRec = key;
				maxHits = currentHits;
			}
		}
		return bestRec;
	}

	protected void onDraw(Canvas c) {  

		if (BluetoothChatService.end_game) {
			totalDriveTime = System.currentTimeMillis() - startDriveTime;
			// convert to minutes
			totalDriveTime = totalDriveTime/(1000 * 60);
			recommendedTip = updateBestTip();
			updateCoins(true);
			avgMpg = calculateAverageMPG();
			Activity a = (Activity) mContext;
			Intent k = new Intent(a, SummaryActivity.class);
			a.startActivity(k);
		}

		if(++counter % 10 == 0)	{
			drawRecommendation(c, new Paint());
		}

		if (greenCircle == null) {
			greenCircle = (BitmapDrawable) mContext.getResources().getDrawable(R.drawable.darkcircle1);
			grayCircle = (BitmapDrawable) mContext.getResources().getDrawable(R.drawable.graycircle);
			grayMap = grayCircle.getBitmap();
			ball = (BitmapDrawable) mContext.getResources().getDrawable(R.drawable.waterdrop);  
			winWidth = this.getWidth();
			winHeight = this.getHeight();
			grayWidth = grayCircle.getBitmap().getWidth();
			grayHeight = grayCircle.getBitmap().getHeight();
			greenWidth = greenCircle.getBitmap().getWidth();
			greenHeight = greenCircle.getBitmap().getHeight();
			ballWidth = ball.getBitmap().getWidth();	
			grayPixels = new int[grayMap.getWidth() * grayMap.getHeight()];
			grayCircle.getBitmap().getPixels(grayPixels, 0, grayMap.getWidth(), 1, 1, grayMap.getWidth() - 1, grayMap.getHeight() - 1);

		}

		if (flambe >= 15) {

			if (x<0 && y <0) {
				x = winWidth/2  - ballWidth/2;
				y = winHeight/2;
				yVelocity *= 1.1;
			} else {
				y += yVelocity;
				if (sashay < 2) {
					yVelocity *= 1.1;
					sashay++;
				} else if (sashay < 18) {
					yVelocity *= 1.25;
					sashay++;
				} else {
					yVelocity *= 1.2;
				}
				if ((y > winHeight) || (y < 0)) {
					yVelocity = 5;
					x = winWidth/2 - ballWidth/2;
					y = winHeight/2;
					sashay = 0;
					flambe = 0;
				}
			}
			if (flambe > 0) {
				c.drawBitmap(ball.getBitmap(), x, y, null);  

			}
			amount += speed;
			if (amount < 0.01) {
				amount = 0.01;
			} else if (amount > 0.99) {
				amount = 0.99;
			}
			drawCircles(greenCircle, grayCircle, c, amount);

			h.postDelayed(r, FRAME_RATE);

		} else {
			flambe++;
			amount += speed;
			if (amount < 0.001) {
				amount = 0.001;
			} else if (amount > 0.99) {
				amount = 0.99;
			}
			drawCircles(greenCircle, grayCircle, c, amount);

			h.postDelayed(r, FRAME_RATE);

		}
		adjustFlambe();
	} 

}
