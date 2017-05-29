package com.hcm.luckydart;

import java.util.ArrayList;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ViewTreeObserver.OnGlobalLayoutListener;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.VerticalSeekBar;

import com.hcm.luckydart.util.AdmobUtil;
import com.hcm.luckydart.util.ConsoleLogger;

public class GameActivity extends Activity {

	private final int 			SELECT_SDCARD_REQUEST 	= 8888;
	private final int 			PIC_CROP 				= 2;
	private final int			IMAGE_SIZE				= 400;	//px
	
	private boolean				isStarted				= false;
	private RelativeLayout 		rlWall 					= null;
	private ImageView 			ivReady 				= null;
	private ImageView 			ivBoard 				= null;
	private ImageView 			ivArrow 				= null;

	private LinearLayout 		llPoint 				= null;
	private TextView 			tvPoint 				= null;

	private SeekBar 			sbHorizontal 			= null;
	private VerticalSeekBar 	sbVertical 				= null;

	private ImageView 			ivBottomBanner 			= null;
	
	private TextView			tvCurrentPoint			= null;
	private LinearLayout		llCurrentPoint			= null;

	private int 				iScreenWidth 			= 0;
	private int 				iScreenHeight 			= 0;

	private int 				iState 					= 0;
	private boolean 			bHorOrientation 		= true;
	private boolean 			bVerOrientation 		= true;

	private Position 			boardCenter 			= null;

	private int 				iBanner 				= 0;
	
	private Uri 				picUri					= null;
	
	private int					iHighestPoint			= 0;
	

	private static int iaBanners[] = { R.drawable.banner_1, R.drawable.banner_2, R.drawable.banner_3, R.drawable.banner_4, R.drawable.banner_5, R.drawable.banner_6, 
							   		   R.drawable.banner_7, R.drawable.banner_8, R.drawable.banner_9, R.drawable.banner_10, R.drawable.banner_11, R.drawable.banner_12};
	
	private ArrayList<Pirate> arPirates = new ArrayList<Pirate>();

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		ConsoleLogger.logEnterFunction();

		super.onCreate(savedInstanceState);
		setContentView(R.layout.layout_game);

		AdmobUtil.initAdView(this);

		rlWall 			= (RelativeLayout) findViewById(R.id.rlWall);
		
		ViewTreeObserver viewTreeObserver = rlWall.getViewTreeObserver();
		if (viewTreeObserver.isAlive()) {
			viewTreeObserver.addOnGlobalLayoutListener(new OnGlobalLayoutListener() {
				@Override
				public void onGlobalLayout() {
					rlWall.getViewTreeObserver().removeOnGlobalLayoutListener(this);
					
					iScreenWidth 	= rlWall.getWidth();
					iScreenHeight 	= rlWall.getHeight();
					ConsoleLogger.log("Screen Width: " + iScreenWidth + ", Screen Height: " + iScreenHeight);

					boardCenter = new Position(iScreenWidth / 2, iScreenHeight / 2);
					ConsoleLogger.log("boardCenter(" + boardCenter.x + ", " + boardCenter.y + ")");
					
					initPirates();
				}
			});
		}

		ivReady 		= (ImageView) findViewById(R.id.imgReady);

		ivBoard 		= (ImageView) findViewById(R.id.imgBoard);

		sbHorizontal 	= (SeekBar) findViewById(R.id.sbHorizontal);
		sbVertical 		= (VerticalSeekBar) findViewById(R.id.sbVertical);

		ivBottomBanner = (ImageView) findViewById(R.id.ivBottomBanner);

		updateBanner();

		llPoint 		= (LinearLayout) findViewById(R.id.llPoint);
		tvPoint 		= (TextView) findViewById(R.id.tvPoint);

		llCurrentPoint	= (LinearLayout) findViewById(R.id.llCurrentPoint);
		tvCurrentPoint 	= (TextView) findViewById(R.id.tvCurrentPoint);
		tvCurrentPoint.setText(String.format(getResources().getString(R.string.Point), iHighestPoint));
		
		ConsoleLogger.logLeaveFunction();
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		ConsoleLogger.logEnterFunction();
		
		super.onActivityResult(requestCode, resultCode, data);
		
		if (requestCode == SELECT_SDCARD_REQUEST) {
			if (resultCode == RESULT_OK) {
				picUri = data.getData();
				cropImage();
			}
		} else if (requestCode == PIC_CROP) {
			if (resultCode == RESULT_OK) {
				Bitmap bmIcon = (Bitmap) data.getExtras().get("data");
				
				for(int i = 0 ; i < arPirates.size(); i++) {
					if(arPirates.get(i).isSelecting) {
						arPirates.get(i).isSelecting 	= false;
						arPirates.get(i).bitmap			= getclip(bmIcon);
						arPirates.get(i).imageView.setImageBitmap(arPirates.get(i).bitmap);
					}
				}
			}
		}
		
		ConsoleLogger.logLeaveFunction();
	}
	
	public void onBoardClickListener(View v) {
		iState = ++iState % 4;
		ConsoleLogger.log("State: " + iState);

		switch (iState) {
		case 0:
			break;
			
		case 1: // Scroll board + select horizontal
			
			isStarted	= true;
			
			Thread timer = new Thread() { // new thread
				public void run() {

					try {
						do {

							sleep(100);

							runOnUiThread(new Runnable() {
								@Override
								public void run() {

									ivBoard.setRotation((ivBoard.getRotation() + 1) % 360);
									
									for(int i = 0 ; i < arPirates.size(); i++) {
										
										--arPirates.get(i).degree;	// -1
										
										if(arPirates.get(i).degree == -1) {
											arPirates.get(i).degree = 359;
										}
										
										if(!arPirates.get(i).isFired && arPirates.get(i).degree != 90 && arPirates.get(i).degree != 270) {
											
											int[] piratePosition = getPosition(i, arPirates.get(i).degree);
											
											RelativeLayout.LayoutParams layoutParam = new RelativeLayout.LayoutParams(iScreenWidth / 18, iScreenWidth / 18);
											layoutParam.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
											layoutParam.setMargins(piratePosition[0] - iScreenWidth / 18 / 2, 0, 0, piratePosition[1] - iScreenWidth / 18 / 2);
											arPirates.get(i).imageView.setLayoutParams(layoutParam);
											
										}
										
									}

								}
							});

						} while (iState == 1 || iState == 2);

						currentThread().interrupt();
					} catch (Exception e) {
						e.printStackTrace();
					} finally {
					}
				};
			};
			timer.start();

			Thread timerHor = new Thread() { // new thread
				public void run() {

					try {
						do {

							sleep(10);

							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									if (bHorOrientation) {
										sbHorizontal.setProgress(sbHorizontal.getProgress() + 1);
									} else {
										sbHorizontal.setProgress(sbHorizontal.getProgress() - 1);
									}

									if (sbHorizontal.getProgress() == 100) {
										bHorOrientation = false;
									} else if (sbHorizontal.getProgress() == 0) {
										bHorOrientation = true;
									}

								}
							});

						} while (iState == 1);

						currentThread().interrupt();
					} catch (Exception e) {
						e.printStackTrace();
					} finally {
					}
				};
			};
			timerHor.start();

			break;

		case 2: // Select vertical
			Thread timerVer = new Thread() { // new thread
				public void run() {

					try {
						do {

							sleep(10);

							runOnUiThread(new Runnable() {
								@Override
								public void run() {
									if (bVerOrientation) {
										sbVertical.setProgress(sbVertical.getProgress() + 1);
									} else {
										sbVertical.setProgress(sbVertical.getProgress() - 1);
									}

									if (sbVertical.getProgress() == 100) {
										bVerOrientation = false;
									} else if (sbVertical.getProgress() == 0) {
										bVerOrientation = true;
									}

								}
							});

						} while (iState == 2);

						currentThread().interrupt();
					} catch (Exception e) {
						e.printStackTrace();
					} finally {
					}
				};
			};
			timerVer.start();

			break;

		case 3: // Show result

			ivReady.setVisibility(View.GONE);

			double leftMargin 	= iScreenWidth / 10 * 9 * sbHorizontal.getProgress() / 100 + iScreenWidth / 20;
			double bottomMargin = iScreenHeight / 10 * 9 * sbVertical.getProgress() / 100;
			// Hack 100
//			double leftMargin = boardCenter.x;	mHorSeekbar.setProgress(50);
//			double bottomMargin = boardCenter.y;mVerSeekbar.setProgress(50);
			// Test pirate
//			double leftMargin = ((RelativeLayout.LayoutParams)arPirates.get(0).imageView.getLayoutParams()).leftMargin + mScreenWidth / 18 / 2;
//			double bottomMargin = ((RelativeLayout.LayoutParams)arPirates.get(0).imageView.getLayoutParams()).bottomMargin + mScreenWidth / 18 / 2;
			
			ivArrow = new ImageView(getApplicationContext());
			ivArrow.setImageResource(R.drawable.arrow);
			RelativeLayout.LayoutParams arrowParam = new RelativeLayout.LayoutParams(iScreenWidth / 10, iScreenWidth / 10); // Arrow's Size = mScreenWidth / 10
			arrowParam.setMargins((int) leftMargin, 0, 0, (int) bottomMargin - iScreenWidth / 10 / 2); // Arrow's size / 2
			arrowParam.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
			ivArrow.setLayoutParams(arrowParam);
			rlWall.addView(ivArrow);

			ConsoleLogger.log("Arrow(" + leftMargin + ", 0, 0 " + bottomMargin + ")");
			Position posArrow 	= new Position((int) leftMargin, (int) bottomMargin);
			
			showPoint(posArrow);

			break;

		default:
			break;
		}

	}
	
	public void onResultClickListener(View v) {
		isStarted	= false;
		
		llPoint.setVisibility(View.GONE);

		rlWall.removeView(ivArrow);

		ivReady.setVisibility(View.VISIBLE);
		ivArrow.setVisibility(View.GONE);
		
		initPirates();
		
		iState 			= 0;
		bHorOrientation 	= true;
		sbHorizontal.setProgress(0);
		bVerOrientation 	= true;
		sbVertical.setProgress(0);
		
		if(iHighestPoint == 100) {
			resetGame();
		}
	}

	private void initPirates() {
		
		ConsoleLogger.logEnterFunction();
		
		if(arPirates.size() == 0) {
			arPirates = new ArrayList<Pirate>();
			
			for(int alpha = 9; alpha < 369; alpha	+= 18){
				
				final Pirate pirate = new Pirate();
				
				int[] position = getPosition(alpha, alpha);
				
				ImageView ivPirate = new ImageView(getApplicationContext());
				ivPirate.setImageResource(R.drawable.icon_pirate);
				ivPirate.setScaleType(ScaleType.FIT_CENTER);
				RelativeLayout.LayoutParams layoutParam = new RelativeLayout.LayoutParams(iScreenWidth / 18, iScreenWidth / 18);
				layoutParam.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
				layoutParam.setMargins(position[0] - iScreenWidth / 18 / 2, 0, 0, position[1] - iScreenWidth / 18 / 2);
				ivPirate.setLayoutParams(layoutParam);
				
				ivPirate.setOnClickListener(new OnClickListener() {
					
					@Override
					public void onClick(View v) {
						if(isStarted) {
							return;
						}
						
						pirate.isSelecting = true;
						Intent mediaChooser = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI);
						mediaChooser.setType("image/*");
						startActivityForResult(mediaChooser, SELECT_SDCARD_REQUEST);					
					}
				});
				
				rlWall.addView(ivPirate);
				
				pirate.imageView 	= ivPirate;
				pirate.degree		= alpha;
				pirate.bitmap		= BitmapFactory.decodeResource(getResources(), R.drawable.icon_pirate);
				
				arPirates.add(pirate);
				
			}
			
		} else {
			for(int i = 0 ; i < arPirates.size(); i++) {
				
				if(arPirates.get(i).isFired) {
					arPirates.get(i).imageView.setVisibility(View.GONE);
				}
			}
		}
		
		ConsoleLogger.logLeaveFunction();
	}

	private int[] getPosition(int index, int alpha) {
		
		int[] result = new int[2];
		
		double tanAlpha = Math.tan(Math.toRadians(alpha));
		double a = 1 + tanAlpha * tanAlpha ; // A
		double b = 2 * tanAlpha * (boardCenter.y - boardCenter.x * tanAlpha) - 2 * boardCenter.x - 2 * boardCenter.y * tanAlpha;
		double c = Math.pow((boardCenter.y - boardCenter.x * tanAlpha), 2) - 2 * boardCenter.y * (boardCenter.y - boardCenter.x * tanAlpha) + boardCenter.x * boardCenter.x + boardCenter.y * boardCenter.y - iScreenWidth/4 * iScreenWidth/4; 
		
		double delta = b * b - 4 * a * c;
		
		double x = 0;
		if(alpha <= 90 || (alpha > 270 && alpha <= 360)) {
			x = (-b + Math.sqrt(delta)) / (2*a);
		} else {
			x = (-b - Math.sqrt(delta)) / (2*a);
		}
		
		double y = x * tanAlpha + boardCenter.y - boardCenter.x * tanAlpha;
		
		if(x <= 0 || y <= 0) {
			ConsoleLogger.logError("index: 	" + index + ", alpha: " + alpha + " -> 	{" + (int)x + ", " + (int)y + "}");
		} else {
			ConsoleLogger.log("index: 	" + index + ", alpha: " + alpha + " -> 	{" + (int)x + ", " + (int)y + "}");
		}
		
		result[0] = (int)x;
		result[1] = (int)y;
		
		return result;
	}
	
	private void updateBanner() {
		
		Thread timer = new Thread() { // new thread
			public void run() {

				try {
					do {

						sleep(1000);

						runOnUiThread(new Runnable() {
							@Override
							public void run() {

								if (iBanner == iaBanners.length) {
									iBanner = 0;
								}
								switch (iBanner % 3) {
								case 0:
									ivBottomBanner.setScaleType(ScaleType.FIT_START);
									break;
								case 1:
									ivBottomBanner.setScaleType(ScaleType.FIT_CENTER);
									break;
								case 2:
									ivBottomBanner.setScaleType(ScaleType.FIT_END);
									break;
								default:
									break;
								}
								ivBottomBanner.setImageResource(iaBanners[iBanner++]);

							}
						});

					} while (true);

				} catch (Exception e) {
					e.printStackTrace();
				} finally {
				}
			};
		};
		timer.start();
	}

	private void showPoint(Position arrow) {

		ConsoleLogger.logEnterFunction();
		if(arrow == null) {
			ConsoleLogger.logError("Arrow Null");
			return;
		}
		ConsoleLogger.log("Arrow(" + arrow.x + ", " + arrow.y + ")");
		ConsoleLogger.log("Degree: " + ivBoard.getRotation());

		double corner 		= getCorner(arrow);
		double distance 	= getDistance(boardCenter, arrow);
		int mRadius 		= iScreenWidth / 10 * 4;

		int point 			= 0;

		if (distance <= 20) {
			point = 100;
			tvPoint.setText(point + "");
		} else if (distance < mRadius) {

			if (arrow.x > boardCenter.x) {
				if (arrow.y > boardCenter.y) { // x > x && y > y
				} else { // x > x && y < y
					corner = 360 - corner;
				}
			} else { // x < x &&
				if (arrow.y > boardCenter.y) { // x < x && y > y
					corner = 180 - corner;
				} else { // x < x && y < y
					corner += 180;
				}
			}

			int tmp = (int) Math.floor(((corner + ivBoard.getRotation()) % 360) / 18);

			switch (tmp) {
			case 0:
				point = 20;
				break;
			case 1:
				point = 5;
				break;
			case 2:
				point = 12;
				break;
			case 3:
				point = 9;
				break;
			case 4:
				point = 14;
				break;
			case 5:
				point = 11;
				break;
			case 6:
				point = 8;
				break;
			case 7:
				point = 16;
				break;
			case 8:
				point = 7;
				break;
			case 9:
				point = 19;
				break;
			case 10:
				point = 3;
				break;
			case 11:
				point = 17;
				break;
			case 12:
				point = 2;
				break;
			case 13:
				point = 15;
				break;
			case 14:
				point = 10;
				break;
			case 15:
				point = 6;
				break;
			case 16:
				point = 13;
				break;
			case 17:
				point = 4;
				break;
			case 18:
				point = 18;
				break;
			case 19:
				point = 1;
				break;
			default:
				break;
			}
			
			tvPoint.setText(point + "");
			
			for(int i = 0 ; i < arPirates.size(); i++) {
				
				if(!arPirates.get(i).isFired) {
					RelativeLayout.LayoutParams layoutParams = (RelativeLayout.LayoutParams)arPirates.get(i).imageView.getLayoutParams();
					
					Position center = new Position(layoutParams.leftMargin + iScreenWidth / 18 / 2, layoutParams.bottomMargin + iScreenWidth / 18 / 2);
					
					double pirate_distance = getDistance(center, arrow);
					
					if(pirate_distance <= iScreenWidth / 18 / 2) {
						tvPoint.setText(tvPoint.getText() + " X 2");

						point*=2;
						
						ImageView ivPirate = new ImageView(getApplicationContext());
						ivPirate.setScaleType(ScaleType.FIT_CENTER);
						LinearLayout.LayoutParams layoutParam = new LinearLayout.LayoutParams(iScreenWidth / 30, iScreenWidth / 30);
						layoutParam.setMargins(5, 0, 0, 0);
						ivPirate.setLayoutParams(layoutParam);
						ivPirate.setImageBitmap(arPirates.get(i).bitmap);
						
						llCurrentPoint.addView(ivPirate);
						
						arPirates.get(i).isFired = true;
					}
				}
				
			}
			
		} else {
			point = 0;
			tvPoint.setText(point + "");
		}

		if(point > iHighestPoint) {
			iHighestPoint = point;
			tvCurrentPoint.setText(String.format(getResources().getString(R.string.Point), iHighestPoint));
		}
		
		llPoint.setVisibility(View.VISIBLE);
		
		ConsoleLogger.log("Corner: " + corner + ", Point: " + point);
		ConsoleLogger.logLeaveFunction();

	}

	private double getDistance(Position center, Position arrow) {
		ConsoleLogger.logEnterFunction();
		
		if(center == null || arrow == null) {
			ConsoleLogger.logError((center == null ? "Center " : "") + (arrow == null ? " Arrow " : "") + "NullException");
			return 0;
		}
		
		ConsoleLogger.log("Center(" + center.x + ", " + center.y + ") , Arrow(" + arrow.x + ", " + arrow.y + ")");

		double distance = Math.sqrt(Math.pow((arrow.x - center.x), 2) + Math.pow((arrow.y - center.y), 2));

		ConsoleLogger.log("Distance: " + distance);

		ConsoleLogger.logLeaveFunction();
		return distance;
	}
	

	private double getCorner(Position arrow) {
		ConsoleLogger.logEnterFunction();
		
		if(arrow == null) {
			ConsoleLogger.logError(" Arrow Null");
			return 0;
		}
		
		ConsoleLogger.log("Arrow(" + arrow.x + ", " + arrow.y + ")");

		int x 				= arrow.x;
		int y 				= arrow.y;

		double a 			= Math.abs(((boardCenter.x + 100) - boardCenter.x) * (x - boardCenter.x) + ((boardCenter.y + 0) - boardCenter.y) * (y - boardCenter.y));
		double b 			= Math.sqrt(Math.pow(((boardCenter.x + 100) - boardCenter.x), 2) + Math.pow(boardCenter.y - boardCenter.y, 2)) * Math.sqrt(Math.pow((x - boardCenter.x), 2) + Math.pow((y - boardCenter.y), 2));

		double cosCorner 	= a / b;

		ConsoleLogger.log("Cos: " + cosCorner + ", Corner: " + Math.round(Math.toDegrees(Math.acos(cosCorner))));
		ConsoleLogger.logLeaveFunction();
		
		return Math.round(Math.toDegrees(Math.acos(cosCorner)));
	}

	private void cropImage() {
		try {
			// call the standard crop action intent (the user device may not support it)
			Intent cropIntent = new Intent("com.android.camera.action.CROP");
			// indicate image type and Uri
			cropIntent.setDataAndType(picUri, "image/*");
			// set crop properties
			cropIntent.putExtra("crop", "true");
			// indicate aspect of desired crop
			cropIntent.putExtra("aspectX", 1);
			cropIntent.putExtra("aspectY", 1);
			// indicate output X and Y
			cropIntent.putExtra("outputX", IMAGE_SIZE);
			cropIntent.putExtra("outputY", IMAGE_SIZE);
			// retrieve data on return
			cropIntent.putExtra("return-data", true);
			// start the activity - we handle returning in onActivityResult
			startActivityForResult(cropIntent, PIC_CROP);
		} catch (ActivityNotFoundException anfe) {
			String errorMessage = getResources().getString(R.string.Camera_Doesnt_Exist);
			showToast(errorMessage);
		}
	}
	
	public static Bitmap getclip(Bitmap bitmap) {
		ConsoleLogger.logEnterFunction();
		
	    Bitmap output = Bitmap.createBitmap(bitmap.getWidth(), bitmap.getHeight(), Config.ARGB_8888);
	    Canvas canvas = new Canvas(output);

	    final Paint paint = new Paint();
	    final Rect rect = new Rect(0, 0, bitmap.getWidth(), bitmap.getHeight());

	    paint.setAntiAlias(true);
	    canvas.drawARGB(0, 0, 0, 0);
	    canvas.drawCircle(bitmap.getWidth() / 2, bitmap.getHeight() / 2, bitmap.getWidth() / 2, paint);
	    paint.setXfermode(new PorterDuffXfermode(android.graphics.PorterDuff.Mode.SRC_IN));
	    canvas.drawBitmap(bitmap, rect, rect, paint);
	    
	    ConsoleLogger.logLeaveFunction();
	    return output;
	}
	
	private void showToast(String strToast) {
		LayoutInflater inflater = getLayoutInflater();
        // Inflate the Layout
        View layout = inflater.inflate(R.layout.custom_toast,(ViewGroup) findViewById(R.id.custom_toast_layout));

        TextView text = (TextView) layout.findViewById(R.id.tvToast);
        // Set the Text to show in TextView
        text.setText(strToast);

        Toast toast = new Toast(getApplicationContext());
        toast.setGravity(Gravity.CENTER_VERTICAL, 0, 0);
        toast.setDuration(Toast.LENGTH_LONG);
        toast.setView(layout);
        toast.show();
	}
	
	private void resetGame() {
		ConsoleLogger.logEnterFunction();
		
		iHighestPoint = 0;
		tvCurrentPoint.setText(String.format(getResources().getString(R.string.Point), iHighestPoint));
		
		ivBoard.setRotation(0);

		for(int i = 0; i < arPirates.size(); i++) {
			rlWall.removeView(arPirates.get(i).imageView);
		}
		arPirates.removeAll(arPirates);
		initPirates();
		
		llCurrentPoint.removeAllViews();
		
		ConsoleLogger.logLeaveFunction();
	}
	
	private class Position {
		int x;
		int y;

		public Position(int x, int y) {
			super();
			this.x = x;
			this.y = y;
		}

	}
	
	
	private class Pirate {
		Bitmap 		bitmap;
		ImageView 	imageView;
		int 		degree;
		boolean		isSelecting = false;
		boolean		isFired		= false;
	}

}
